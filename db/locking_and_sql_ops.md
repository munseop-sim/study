# DB Locking And SQL Operations

## DB Lock
- Shared Lock: 읽기 중심 락, Shared Lock 간 공존 가능
- Exclusive Lock: 쓰기 중심 락, 다른 락과 충돌
- 락과 격리 수준의 차이
  - 격리 수준: 다른 트랜잭션 변경 내용을 어떻게 볼지
  - 락: 특정 데이터 접근 자체를 제어

## JPA 락 예시
```java
@Transactional
@Lock(value = LockModeType.PESSIMISTIC_READ)
public int order(String name, int price);

@Transactional
@Lock(value = LockModeType.PESSIMISTIC_WRITE)
public int order(String name, int price);

@Transactional
@Lock(value = LockModeType.PESSIMISTIC_FORCE_INCREMENT)
public int order(String name, int price);
```

## drop / truncate / delete
- `drop`: 테이블 자체 제거
- `truncate`: 데이터 및 일부 메타 초기화, 롤백 불가(일반적으로)
- `delete`: 데이터만 삭제, 조건 기반 처리 가능

## MySQL(InnoDB) 갭락/넥스트키락
- 갭락: 인덱스 사이 공간을 잠궈 삽입을 제어
- 넥스트키락: 레코드 락 + 갭락 결합으로 팬텀 리드 방지

## 참고
- [DB인덱스](https://www.maeil-mail.kr/question/60)
- [데이터베이스 시스템에서 동시성을 제어하는 방법](https://www.maeil-mail.kr/question/92)
