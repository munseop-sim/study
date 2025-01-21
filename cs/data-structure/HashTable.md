HashTable  : `Key` + `Value`의 자료구조

- 해시 테이블 = 키를 입력받아서 `HashFunction`을 통해서 HashCode(정수)를 생성하고 생성된 HashCode를 기반으로 Index를 생성한다. 생성된 Index를 기반으로 해당 Index에 Value를 저장한다.
- 생성된 해시코드를 `Capacity`만큼 나머지 연사자를 통해서 인덱스를 계산하고, 해당 인덱스에 데이터를 저장
- HashFunction
  - 임의의 크기를 가지는 type의 데이터를 고정된 크기를 가지는 type의 데이터로 변환하는 함수
  - (hash table 에서) 임의의 데이터를 정수로 변환하는 함수 
  - 해시함수 충돌 
    - 서로 다른 키값 &rarr; 같은 해시코드 : 최대한 균등하게 나오게 해야함
    - 서로다른 코드 &rarr; 같은 인덱스(hash % map_capacity)
  - 해시함수 충돌 해결방법
    - open addressing : 하나의 해시 테이블만 사용함 (별도의 연결 리스트나 추가 구조 필요 없음).
      - linear probing : 특정 슬롯이 이미 점유된 경우 가까운 다음 슬롯부터 순차적으로 탐색하여 비어있는 슬롯에 데이터 저장 (저장인덱스를 1씩증가 +1, +2, +3...)
        - 충돌이 반복되면 연속된 슬롯에 데이터가 몰리는 "1차 클러스터링" 문제가 발생할 수 있다.
      - Quadratic Probing: 슬롯 탐색을 조금 더 분산시키기 위해 `i`의 제곱을 사용하여 탐색 경로를 변경. `index = (hash(key) + i^2) % capacity`  (i는 충돌 횟수를 나타냄)
        - 테이블 크기가 소수(prime number)가 아니면 탐색 실패(무한 루프)가 발생할 수 있다.
      - Double Hashing (이중 해싱) = ` index = (hash1(key) + i * hash2(key)) % capacity  (i = 0, 1, 2, ...)`
        - 두 번째 해시 함수(hash2)는 테이블 크기와 독립적이어야 하며, **0이 되지 않도록 설계**해야 한다.
    - Separate Chaining : 각 버킷을 **Linked List**, **Dynamic Array**, 혹은 **Tree 구조**로 관리하며, 여러 데이터가 해시 충돌로 동일한 인덱스에 매핑될 경우 이를 통해 항목을 관리한다.

- Java : `HashMap` = Separate Chaining 방식을 사용하며, Java 8 이후부터 체인의 항목 개수가 일정 임계치(기본값: 8)를 초과할 경우, Linked List 대신 Red-Black Tree를 사용해 탐색 시간을 O(log n)으로 줄인다. 
  - 해시충돌 해결 방법 : Separate Chaining
  - default capacity : 16
  - resize : capacity의 75% 이상 데이터가 존재시에 2배로 늘림
  - shrink : 없음
- Python : `Dictionary` = HashTable 구현
  - 해시충돌 해결 방법 : Open Addressing의 변형 방식. Dummy 데이터를 활용하여 삭제 이후에도 탐색 연속성을 유지하며, 충돌 발생 시 새로운 슬롯을 탐색해 데이터를 관리한다.
  - default capacity : 8
  - resize : capacity의 2/3 이상 데이터가 존재시에 2배로 늘림
  - shrink : dummy 데이터 > 유효데이터

  - Load Factor:
    - 해시 테이블의 부하 상태를 나타내는 지표로, `(저장된 데이터 개수 / 전체 슬롯 개수)`로 계산된다.
    - 임계치를 넘으면 테이블 크기를 동적으로 증가시키는 "resizing"이 발생하며, 기존 데이터를 새로운 테이블로 재배치(Rehashing)한다.
    - 지나치게 높은 Load Factor는 충돌 가능성을 높이고 낮은 Load Factor는 메모리 낭비를 초래한다.

