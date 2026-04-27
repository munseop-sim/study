# 이중부기 원장 설계 — Double-Entry Ledger

> **관련 문서**
> - [금액 정밀도 처리](./money_precision.md)
> - [멱등성 키 설계](./idempotency_keys.md)
> - [SAGA & Outbox 패턴](../msa/saga_outbox_patterns.md)

---

## 1. 왜 원장이 필요한가

금융 시스템에서 잔액 컬럼만 업데이트하면 현재 상태는 알 수 있지만, 왜 그런 잔액이 되었는지 추적하기 어렵다.

```sql
UPDATE wallet
SET balance = balance - 10000
WHERE id = 1;
```

이 방식의 문제:
- 변경 이력 추적이 어렵다.
- 장애 발생 시 어떤 거래가 반영됐는지 복구하기 어렵다.
- 외부 PG/은행과 불일치가 발생했을 때 조정 근거가 부족하다.
- 감사 대응이 어렵다.

원장은 모든 금전 이동을 불변 이벤트처럼 기록한다.

---

## 2. 이중부기 기본 개념

이중부기는 하나의 거래를 차변(Debit)과 대변(Credit) 양쪽에 기록한다.
항상 다음 식이 성립해야 한다.

```
차변 합계 = 대변 합계
```

예: 사용자 A가 사용자 B에게 10,000원을 송금

| 계정 | Debit | Credit |
|---|---:|---:|
| A 지갑 | 0 | 10,000 |
| B 지갑 | 10,000 | 0 |

시스템 전체 관점에서 돈은 사라지거나 생기지 않는다.

---

## 3. 테이블 모델

### 거래 단위

```sql
CREATE TABLE ledger_transaction (
    id BIGINT PRIMARY KEY,
    transaction_key VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
```

`transaction_key`는 멱등성 키 또는 외부 거래 ID와 연결한다.

### 원장 엔트리

```sql
CREATE TABLE ledger_entry (
    id BIGINT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    direction VARCHAR(10) NOT NULL, -- DEBIT / CREDIT
    amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_entry_tx
        FOREIGN KEY (transaction_id) REFERENCES ledger_transaction(id)
);
```

원장 엔트리는 수정하지 않는다.
잘못된 거래는 삭제/수정이 아니라 반대 방향의 보정 거래로 처리한다.

---

## 4. 잔액 스냅샷

원장만으로 잔액을 매번 계산하면 비용이 크다.
따라서 조회 성능을 위해 잔액 스냅샷을 둔다.

```sql
CREATE TABLE wallet_balance (
    account_id BIGINT PRIMARY KEY,
    available_balance DECIMAL(19, 4) NOT NULL,
    pending_balance DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    version BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

원장은 truth source, 잔액 테이블은 projection 또는 snapshot으로 본다.

장애 복구 시:

```
원장 엔트리 재집계 -> 잔액 스냅샷과 비교 -> 불일치 조정
```

---

## 5. 송금 처리 예시

```java
@Transactional
public TransferResult transfer(TransferCommand command) {
    Optional<LedgerTransaction> existing =
        ledgerTransactionRepository.findByTransactionKey(command.idempotencyKey());
    if (existing.isPresent()) {
        return TransferResult.from(existing.get()); // 이전 처리 결과 반환
    }

    WalletBalance from = walletRepository.findByAccountIdForUpdate(command.fromAccountId());
    WalletBalance to = walletRepository.findByAccountIdForUpdate(command.toAccountId());

    from.withdraw(command.amount());
    to.deposit(command.amount());

    LedgerTransaction tx = LedgerTransaction.transfer(command.idempotencyKey());
    ledgerTransactionRepository.save(tx);

    ledgerEntryRepository.save(LedgerEntry.credit(tx, from.accountId(), command.amount()));
    ledgerEntryRepository.save(LedgerEntry.debit(tx, to.accountId(), command.amount()));

    return TransferResult.from(tx);
}
```

핵심:
- 멱등성 키로 중복 거래를 막고, 재요청에는 이전 처리 결과를 조회해 동일하게 반환한다.
- 잔액 row는 비관적 락 또는 원자적 UPDATE로 보호한다.
- 같은 거래의 DEBIT 합계와 CREDIT 합계는 항상 같아야 한다.
- 원장 저장과 잔액 변경은 같은 DB 트랜잭션으로 묶는다.

---

## 6. 보류 잔액과 사용 가능 잔액

카드 승인, 예약 결제, 출금 대기처럼 즉시 확정되지 않는 거래는 pending 상태가 필요하다.

| 잔액 | 의미 |
|---|---|
| `available_balance` | 사용자가 즉시 사용할 수 있는 금액 |
| `pending_balance` | 승인/정산 대기 중인 금액 |
| `ledger balance` | 원장 기준 총액 |

예:

```
1. 결제 승인 요청: available -10,000 / pending +10,000

분기 A - 매입 확정:
2A. pending -10,000 / merchant 정산 계정 +10,000

분기 B - 승인 취소:
2B. pending -10,000 / available +10,000
```

상태 전이는 명시적으로 관리해야 한다.

---

## 7. Reconciliation

외부 PG/은행과 내부 원장은 언제든 불일치할 수 있다.

불일치 원인:
- 외부 API 성공 후 내부 DB 커밋 실패
- 내부 처리 성공 후 webhook 유실
- 중복 webhook
- 수동 운영 처리
- 정산 파일 지연

조정 배치:

```
1. 외부 거래 내역 수집
2. 내부 ledger_transaction과 매칭
3. 금액/상태/시각 비교
4. 불일치 건 reconciliation_task 생성
5. 자동 보정 또는 수동 처리
```

원장 설계에는 조정 작업의 근거가 되는 외부 거래 ID, 요청 ID, trace ID를 반드시 남긴다.

---

## 8. 실무 체크리스트

- 잔액 변경은 반드시 원장 엔트리와 함께 기록한다.
- 원장 엔트리는 수정/삭제하지 않는다.
- 보정은 반대 방향 거래로 남긴다.
- 모든 거래에는 idempotency key 또는 외부 거래 ID를 둔다.
- 차변 합계와 대변 합계가 같은지 DB/애플리케이션에서 검증한다.
- 잔액 스냅샷은 원장으로 재생성 가능해야 한다.
- reconciliation 배치를 운영 전부터 설계한다.
- 금액 타입은 통화별 scale과 rounding 정책을 명시한다.

### 면접 포인트

- Q: 잔액 컬럼만 관리하면 금융 시스템에서 어떤 문제가 생기는가?
- Q: 이중부기에서 차변 합계와 대변 합계가 항상 같아야 하는 이유는?
- Q: 원장과 잔액 스냅샷의 역할 차이는 무엇인가?
- Q: 잘못 기록된 원장 엔트리는 왜 수정하지 않고 보정 거래로 처리하는가?
