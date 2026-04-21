# 금융 시스템에서의 금액 정밀도 처리

> 최종 수정: 2026-04-21

---

## 1. 부동소수점 문제: double 절대 금지

### 왜 double이 금융에서 금지인가?

```java
// 충격적인 사실
System.out.println(0.1 + 0.2);          // 0.30000000000000004
System.out.println(1.03 - 0.42);         // 0.6100000000000001
System.out.println(10.00 * 0.1);         // 1.0000000000000002

// 실무에서 발생한 사고 시나리오
double unitPrice = 9.99;
double quantity = 3;
double total = unitPrice * quantity;     // 29.969999999999996 ≠ 29.97
// 이 값을 DB에 저장 → 계산서 불일치 → 감사 지적
```

IEEE 754 배정밀도 부동소수점은 10진수 0.1을 이진수로 정확하게 표현할 수 없다. 금융 연산에서 1원의 오차도 허용되지 않으므로 **`double`, `float` 사용은 절대 금지**.

---

## 2. BigDecimal 심층 분석

### 2-1. 생성 방법: new vs 문자열

```java
// 위험: double → BigDecimal 변환 시 부동소수점 오차 그대로 보존
BigDecimal bad = new BigDecimal(0.1);
System.out.println(bad);
// 0.1000000000000000055511151231257827021181583404541015625

// 올바른 방법 1: 문자열로 생성
BigDecimal good1 = new BigDecimal("0.1");   // 정확히 0.1

// 올바른 방법 2: valueOf (내부적으로 Double.toString() 사용)
BigDecimal good2 = BigDecimal.valueOf(0.1); // 정확히 0.1

// Kotlin 확장
val amount = "9.99".toBigDecimal()
val amount2 = 9.99.toBigDecimal()  // 주의: double 기반이므로 오차 가능
```

### 2-2. scale과 precision

```java
BigDecimal value = new BigDecimal("123.4500");
value.precision();  // 7 (유효 자릿수 전체)
value.scale();      // 4 (소수점 이하 자릿수)
value.unscaledValue(); // 1234500

// scale 조정
BigDecimal a = new BigDecimal("10.5");
BigDecimal b = new BigDecimal("10.50");
a.equals(b);             // false (scale이 다름: 1 vs 2)
a.compareTo(b) == 0;     // true  (값은 동일)

// scale 명시적 설정
BigDecimal result = value.setScale(2, RoundingMode.HALF_UP); // "123.45"
```

### 2-3. RoundingMode: HALF_UP vs HALF_EVEN

```java
// HALF_UP: 0.5는 올림 (일반적 반올림, 한국 금융 관행)
new BigDecimal("2.5").setScale(0, RoundingMode.HALF_UP);  // 3
new BigDecimal("3.5").setScale(0, RoundingMode.HALF_UP);  // 4
new BigDecimal("-2.5").setScale(0, RoundingMode.HALF_UP); // -2 (양수 방향으로)

// HALF_EVEN (Banker's Rounding): 0.5는 가장 가까운 짝수로
// 통계적 편향을 줄임, 미국 금융, 회계에서 사용
new BigDecimal("2.5").setScale(0, RoundingMode.HALF_EVEN); // 2 (짝수)
new BigDecimal("3.5").setScale(0, RoundingMode.HALF_EVEN); // 4 (짝수)
new BigDecimal("4.5").setScale(0, RoundingMode.HALF_EVEN); // 4 (짝수)

// DOWN: 0 방향으로 버림 (절삭, 수수료 계산 등 고객에게 유리하게)
new BigDecimal("2.9").setScale(0, RoundingMode.DOWN); // 2

// UNNECESSARY: 반올림 불필요, 정확한 결과가 아니면 예외
new BigDecimal("2.5").setScale(0, RoundingMode.UNNECESSARY); // ArithmeticException
```

**실무 선택 기준**
- 한국 소비자 대상 반올림 → `HALF_UP`
- 대량 거래 통계 집계 → `HALF_EVEN` (누적 오차 최소화)
- 수수료 절삭 (고객 유리) → `DOWN`
- 세금 계산 (법정 기준) → 담당 세무 담당자 확인 필수

### 2-4. compareTo vs equals

```java
BigDecimal a = new BigDecimal("10.50");
BigDecimal b = new BigDecimal("10.5");

// equals: scale까지 비교 → false
a.equals(b); // false!

// compareTo: 값만 비교 → 0 (동일)
a.compareTo(b) == 0; // true

// 실무 적용
// Map/Set의 키로 쓸 때는 scale 통일 필요
// 비교 로직은 항상 compareTo 사용

// Kotlin에서
a == b           // false (equals 호출)
a.compareTo(b) == 0  // true
```

---

## 3. Long 사용 기준

### Long이 가능한 경우: 원화 정수 단위

```kotlin
// 원화(KRW)는 소수점 없음 → Long으로 관리 가능
data class KrwAmount(val value: Long) {
    init {
        require(value >= 0) { "금액은 0 이상이어야 합니다" }
    }

    operator fun plus(other: KrwAmount) = KrwAmount(Math.addExact(value, other.value))
    operator fun minus(other: KrwAmount) = KrwAmount(Math.subtractExact(value, other.value))
}

// 원화 1조원도 Long으로 표현 가능
// Long.MAX_VALUE = 9,223,372,036,854,775,807 (약 9경 2천조)
// 원화 최대 송금액(수억 원)은 Long으로 충분
```

### overflow 방지

```java
// 위험: 조용한 overflow
long a = Long.MAX_VALUE;
long b = 1L;
long result = a + b; // -9223372036854775808 (오버플로우, 예외 없음)

// 안전: Math.addExact 사용
long result = Math.addExact(a, b); // ArithmeticException: long overflow

// 또는 범위 검증
private static final long MAX_TRANSFER_AMOUNT = 1_000_000_000L; // 10억

void validateAmount(long amount) {
    if (amount <= 0 || amount > MAX_TRANSFER_AMOUNT) {
        throw new InvalidAmountException(amount);
    }
}
```

### Long 사용이 부적합한 경우

```kotlin
// 소수점 있는 외화(USD, EUR) → BigDecimal 필수
data class UsdAmount(val value: BigDecimal) {
    init {
        require(value.scale() <= 4) { "USD는 소수점 4자리까지" }
        require(value >= BigDecimal.ZERO) { "금액은 0 이상" }
    }
}

// 환율 계산 → BigDecimal 필수
fun convertKrwToUsd(krwAmount: Long, exchangeRate: BigDecimal): BigDecimal {
    return krwAmount.toBigDecimal()
        .divide(exchangeRate, 4, RoundingMode.HALF_UP)
}
```

---

## 4. JPA 매핑

```kotlin
// 엔티티 정의
@Entity
@Table(name = "remittance")
class Remittance(
    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    val amount: BigDecimal,

    @Column(name = "exchange_rate", precision = 19, scale = 6, nullable = false)
    val exchangeRate: BigDecimal,

    @Column(name = "fee", precision = 19, scale = 4, nullable = false)
    val fee: BigDecimal,
)

// precision = 19: 정수 15자리 + 소수 4자리 = 총 19자리
// Long.MAX_VALUE는 19자리이므로 precision=19면 모든 Long 값 수용 가능
// scale = 4: USD, EUR 등 소수점 4자리까지 (환율은 6자리)
```

**JPA 컨버터로 일관성 보장**

```kotlin
@Converter(autoApply = true)
class MoneyConverter : AttributeConverter<BigDecimal, BigDecimal> {
    override fun convertToDatabaseColumn(attribute: BigDecimal?): BigDecimal? {
        // DB 저장 전 scale 정규화
        return attribute?.setScale(4, RoundingMode.HALF_UP)
    }

    override fun convertToEntityAttribute(dbData: BigDecimal?): BigDecimal? = dbData
}
```

---

## 5. DB 스키마: DECIMAL(19, 4)

```sql
-- 표준 금액 컬럼 정의
CREATE TABLE remittance (
    -- 금액: DECIMAL(19,4)
    amount          DECIMAL(19, 4)  NOT NULL CHECK (amount > 0),
    fee_amount      DECIMAL(19, 4)  NOT NULL CHECK (fee_amount >= 0),

    -- 환율: DECIMAL(19,6) - 소수점 6자리 필요
    exchange_rate   DECIMAL(19, 6)  NOT NULL CHECK (exchange_rate > 0),

    -- 원화(정수 단위): BIGINT 가능
    krw_amount      BIGINT          NOT NULL CHECK (krw_amount > 0),

    -- 통화 코드
    currency_code   CHAR(3)         NOT NULL  -- ISO 4217: USD, KRW, EUR
);

-- PostgreSQL에서 DECIMAL = NUMERIC (동일)
-- MySQL에서도 DECIMAL(19,4) 동일하게 작동
```

**왜 19, 4인가?**
- `DECIMAL(19, 4)`: 최대 999,999,999,999,999.9999 표현 가능
- 전 세계 통화 중 소수점이 가장 많은 것은 쿠웨이트 디나르(KWD) = 3자리
- 4자리면 모든 법정 통화 + 계산 중간 값 처리에 충분
- 19 = Java Long 최대값(19자리)과 호환

---

## 6. 국제화 / 환율 고려

```kotlin
// ISO 4217 통화 코드와 소수 자릿수
enum class Currency(val code: String, val defaultScale: Int) {
    KRW("KRW", 0),   // 원화: 소수점 없음
    USD("USD", 2),   // 달러: 2자리 (센트)
    JPY("JPY", 0),   // 엔화: 소수점 없음
    KWD("KWD", 3),   // 쿠웨이트 디나르: 3자리
    BHD("BHD", 3),   // 바레인 디나르: 3자리
}

// 환율 적용 시 중간 계산 정밀도
fun applyExchangeRate(
    amount: BigDecimal,
    rate: BigDecimal,
    targetCurrency: Currency
): BigDecimal {
    // 중간 계산은 높은 정밀도로
    val rawResult = amount.multiply(rate)
        .setScale(10, RoundingMode.HALF_UP)  // 중간값: 10자리

    // 최종 결과는 목표 통화 자릿수로
    return rawResult.setScale(
        targetCurrency.defaultScale,
        RoundingMode.HALF_UP
    )
}

// 스프레드(수수료 마진) 계산
fun calculateSpread(midRate: BigDecimal, spreadPercent: BigDecimal): BigDecimal {
    val spreadFactor = BigDecimal.ONE.subtract(
        spreadPercent.divide(BigDecimal("100"), 10, RoundingMode.HALF_UP)
    )
    return midRate.multiply(spreadFactor)
        .setScale(6, RoundingMode.HALF_UP)
}
```

---

## 7. 실무 결정 트리

```
금액을 어떤 타입으로 저장할까?
│
├─ 소수점이 있는가?
│   ├─ YES → BigDecimal 필수
│   └─ NO → Long 가능 (원화, 엔화 등)
│
├─ 연산 후 반올림이 필요한가?
│   ├─ YES → BigDecimal.setScale(n, RoundingMode.HALF_UP)
│   └─ NO → 정수 연산 유지
│
├─ DB 저장 타입은?
│   ├─ BigDecimal → DECIMAL(19,4) 또는 DECIMAL(19,6) for 환율
│   └─ Long(원화) → BIGINT
│
└─ 비교 로직에서
    ├─ 동등 비교 → compareTo() == 0  (equals() 사용 금지)
    └─ 정렬/집계 → compareTo()
```

---

## 8. 흔한 실수 모음

```kotlin
// 실수 1: double을 경유하는 BigDecimal 생성
val bad = BigDecimal(0.1)              // 오차 있음
val good = BigDecimal("0.1")           // 정확

// 실수 2: equals로 금액 비교
val a = BigDecimal("10.5")
val b = BigDecimal("10.50")
if (a == b) { /* 절대 여기 안 옴 */ }      // equals: scale 포함 비교
if (a.compareTo(b) == 0) { /* 올바름 */ }

// 실수 3: scale 없이 나눗셈
val result = BigDecimal("1").divide(BigDecimal("3"))
// ArithmeticException: Non-terminating decimal expansion
val safeResult = BigDecimal("1").divide(BigDecimal("3"), 4, RoundingMode.HALF_UP)
// 0.3333

// 실수 4: Long overflow 무시
val total = amount1 + amount2          // overflow 시 음수로 변환
val safeTotal = Math.addExact(amount1, amount2)  // overflow 시 예외

// 실수 5: 환율 계산 후 즉시 scale 축소
val krwAmount = usdAmount.multiply(rate)
    .setScale(0, RoundingMode.HALF_UP)  // 잘못: 중간 정밀도 손실
val krwAmount = usdAmount.multiply(rate)
    .setScale(10, RoundingMode.HALF_UP)  // 중간 정밀도 유지
    .setScale(0, RoundingMode.HALF_UP)   // 최종 반올림
```

---

## 9. 면접 포인트

**Q1. 금융 시스템에서 double 대신 BigDecimal을 쓰는 이유는?**
> `double`은 IEEE 754 이진 부동소수점 방식이라 0.1 같은 10진수 소수를 정확하게 표현할 수 없습니다. `0.1 + 0.2 = 0.30000000000000004`처럼 미세한 오차가 발생하고, 이 오차가 누적되면 1원 이상의 차이로 이어져 결산 불일치가 생깁니다. `BigDecimal`은 10진수 기반으로 임의 정밀도를 지원하므로 금융 연산에 필수입니다.

**Q2. BigDecimal의 equals와 compareTo 차이는?**
> `equals`는 값과 scale을 모두 비교해서 `10.5`와 `10.50`을 다른 값으로 봅니다. `compareTo`는 수학적 값만 비교해서 동일하게 봅니다. 금액 비교에는 반드시 `compareTo() == 0`을 써야 하며, `equals`로 비교하면 scale이 다를 때 잘못된 결과가 나옵니다.

**Q3. 원화는 Long으로 관리해도 되나요?**
> 원화(KRW)는 법정 최소 단위가 1원이라 소수점이 없으므로 Long 사용이 가능합니다. 다만 `Long.MAX_VALUE`(약 9.2경)를 넘을 일은 없지만, 덧셈/뺄셈 연산 시 `Math.addExact` 같은 overflow 안전 메서드를 써야 합니다. USD, EUR처럼 소수점이 있는 외화는 반드시 BigDecimal을 사용해야 합니다.

**Q4. DB에서 DECIMAL(19,4)를 쓰는 이유는?**
> precision 19는 Java `Long.MAX_VALUE`(19자리)와 호환되어 모든 Long 값을 수용합니다. scale 4는 쿠웨이트 디나르(KWD, 3자리)를 포함한 모든 ISO 4217 법정 통화의 최소 단위를 수용하고 계산 중간값 처리에도 여유가 있습니다. 환율처럼 더 높은 정밀도가 필요한 경우는 DECIMAL(19,6)을 씁니다.

**Q5. 반올림 모드를 어떻게 선택하나요?**
> 한국 금융 관행에서는 `HALF_UP`(사사오입)이 기본입니다. 대량 거래 통계 집계처럼 누적 오차를 최소화해야 할 때는 `HALF_EVEN`(은행가 반올림)을 씁니다. 수수료를 절삭해 고객에게 유리하게 처리할 때는 `DOWN`을 씁니다. 법정 세율이 적용되는 세금 계산은 반드시 법령 기준을 확인해야 합니다.
