- 기본 자료구조: 배열, 스택, 큐.
### Stack
- 후입 선출의 자료구조
- 삭제(pop), 추가(push)는 최상단(top)에서만 이루어짐
- 언더플로우: 비어있는 스택에서 `pop`시도
- 오버플로우: 스택의 용량이 넘치는 경우
- 활용사례: 스택 메모리, 브라우저 뒤로가기 기능, 언두 기능, 수식 괄호 검사 ...
- Java에서의 Stack
  - Stack이라는 클래스를 사용할 수 있지만, [Deque](https://docs.oracle.com/javase/8/docs/api/java/util/Deque.html) 인터페이스 구현체를 사용하는 것이 권장
    - Stack은 내부적으로 Vector를 상속받아서 사용. 
    - Vector는 인덱스를 통하여 접근, 삽입, 제거 등이 가능하여 스택의 후입선출 특징에는 맞지 않고, 개발자가 실수할 가능성이 존재
    - synchronized로 구현되어 있어 멀티 스레드 환경에서는 동기화의 이점이 있으나, 불필요한 동기화작업으로 성능측면에서 좋지 않을 수 있음
    - [Deque](https://docs.oracle.com/javase/8/docs/api/java/util/Deque.html) 인터페이스는 후입선출의 특성을 완전히 유지하면서도 동기화 작업을 가지는 구현체와 그렇지 않은 구현체를 선택할 수 있음. 이는 개발자가 필요에 따라 동기화 작업의 오버헤드를 회피하고 성능을 최적화할 수 있도록 한다.
    - [Java에서의 Deque](https://www.google.com/search?q=java+deque&oq=java+deque&gs_lcrp=EgZjaHJvbWUyBggAEEUYOTIHCAEQABiABDIHCAIQABiABDIHCAMQABiABDIHCAQQABiABDIHCAUQABiABDIHCAYQABiABDIHCAcQABiABDIHCAgQABiABDIHCAkQABiABNIBCDQyMzdqMGo3qAIAsAIA&sourceid=chrome&ie=UTF-8)
---
- 고급 자료구조: 그래프, 트리, Hash.
- 추가 학습 추천:
    - **Linked List**, **Priority Queue(Heap)**, **Trie**, **B-Tree**, **Fenwick Tree**.
    - **Disjoint Set(Union-Find)**, **Consistent Hashing**, **Bloom Filter**.
    - **Sliding Window**와 같은 실시간 처리용 자료구조.



### 1. **링크드 리스트(Linked List)**
- **개념**: 연결된 노드의 집합으로 구성된 자료구조. 각 노드는 데이터와 다음 노드에 대한 포인터를 포함합니다.
- **종류**:
    - 단일 연결 리스트(Singly Linked List) 
    - 이중 연결 리스트(Doubly Linked List)
    - 원형 연결 리스트(Circular Linked List)

- **백엔드에서의 활용**:
    - 메모리 효율이 중요한 경우 데이터의 동적 추가/삭제가 빈번한 작업에서 활용.
    - 데이터의 순차적 접근이나 연결 기반 데이터 처리(예: 캐싱 시스템의 LRU 구현).

### 2. **우선순위 큐(Priority Queue) 및 힙(Heap)**
- **개념**:
    - 우선순위 큐: 각 요소에 우선순위가 부여되고, 높은 우선순위를 가진 요소가 먼저 처리되는 자료구조.
    - 최소 힙: 부모 노드가 자식 노드보다 작은 값.
    - 최대 힙: 부모 노드가 자식 노드보다 큰 값.

- **백엔드에서의 활용**:
    - 작업 스케줄링, 프로세싱 우선순위 처리 (예: 메시지 큐).
    - 검색 엔진의 PageRank 알고리즘 구현.
    - 실시간 작업(예: 백그라운드 작업 우선순위 처리).

### 3. **집합(Set) 및 맵(Map)**
- **개념**:
    - **집합(Set)**: 중복되지 않는 데이터 저장.
        - 구현 예: `HashSet`, `TreeSet`(Java), `Set`(Python).

    - **맵(Map)**: 키-값 쌍으로 데이터를 저장, 키로 값을 빠르게 검색 가능.
        - 구현 예: `HashMap`, `TreeMap`(Java), `Dictionary`(Python).

- **백엔드에서의 활용**:
    - 중복 없는 데이터 저장(예: 유저 ID, 태그 관리).
    - 관계형 데이터 관리 및 빠른 조회(예: 캐시 시스템).

### 4. **덱(Deque, Double-Ended Queue)**
- **개념**:
    - 양쪽 끝에서 삽입 및 삭제가 가능한 큐.
    - **종류**: 입력-제한 덱, 출력-제한 덱.

- **백엔드에서의 활용**:
    - LRU 캐시 구현.
    - 실시간 데이터 스트림 처리(양방향 큐 관리).

### 5. **배열 리스트 및 동적 배열(ArrayList, Dynamic Array)**
- **개념**:
    - 고정 크기를 가진 배열 대신 크기를 동적으로 변경할 수 있는 배열.
    - 일반 배열보다 삽입, 삭제가 빈번한 작업에서 효율적.

- **백엔드에서의 활용**:
    - 데이터의 동적 집합 관리(예: 요청 로그, 임시 데이터 캐싱).

### 6. **트라이(Trie)**
- **개념**: 문자열이나 문자 기반 키를 저장하고 검색하는 트리 구조.
- **백엔드에서의 활용**:
    - 자동 완성 구현(예: 검색어 추천 기능).
    - 텍스트 분석 및 압축.
    - 라우팅 테이블(예: 네트워크 라우팅).

### 7. **분할-정복형 자료구조(Segment Tree, Fenwick Tree)**
- **Segment Tree**:
    - 배열 내 구간 질의(예: 특정 범위 합, 최소값/최대값 등)를 빠르게 처리하기 위한 트리 기반 자료구조.

- **Fenwick Tree (Binary Indexed Tree)**:
    - 주로 누적 합 계산에 사용.

- **백엔드에서의 활용**:
    - 범위 연산이 빈번한 앱(예: 일정 관리, 미디어 재생 길이 분석 등).

### 8. **블룸 필터(Bloom Filter)**
- **개념**:
    - 데이터가 집합에 포함되어 있을 가능성을 빠르게 확인하는 확률적 자료구조.
    - 오탐(false positive)이 발생할 수 있지만, 누락(false negative)은 없다.

- **백엔드에서의 활용**:
    - 캐싱 시스템에서 "존재하지 않을 가능성" 탐지(예: 메모리 절약).
    - 데이터베이스의 비싼 검색 작업 최소화.

### 9. _B-트리 및 변형(B+-트리, B-트리)_*
- **개념**:
    - 계층적이고 정렬된 트리 자료구조로 검색, 삽입, 삭제 작업이 log 시간에 동작.
    - B+트리는 리프 노드에 데이터 저장.

- **백엔드에서의 활용**:
    - 데이터베이스 인덱스.
    - 파일 시스템 내 데이터 블록 관리.

### 10. **해시 기반의 확장 자료구조 (Consistent Hashing)**
- **개념**:
    - 데이터 분산을 위한 해시링 기술의 확장. 분산 시스템에서 데이터 위치를 효율적으로 관리.

- **백엔드에서의 활용**:
    - 분산 환경에서 데이터 배치(예: 캐시 서버, 파일 저장소).
    - 웹 로드 밸런싱.

### 11. **이진 검색 트리(Binary Search Tree)와 Self-Balancing Trees**
- **Self-Balancing Trees**:
    - AVL 트리, Red-Black 트리, 스플레이 트리(Splay Tree).
    - 트리가 한쪽으로 치우치지 않도록 스스로 균형을 유지.

- **백엔드에서의 활용**:
    - 검색 및 데이터 정렬에 활용(데이터베이스나 트랜잭션 정렬).

### 12. **데이터 스트림 처리용 자료구조 (Sliding Window, Interval Tree)**
- **Sliding Window**: 실시간으로 고정 크기 데이터만 처리.
- **Interval Tree**: 구간 범위 쿼리를 처리.
- **백엔드에서의 활용**:
    - 실시간 데이터 처리(예: 네트워크 트래픽, 시간 범위 쿼리).

### 13. **합집합-찾기(Disjoint Set, Union-Find)**
- **개념**:
    - 데이터 집합을 빠르게 병합 및 찾는 자료구조.
    - 경로 압축(Path Compression)과 랭크(Rank)에 기반한 최적화를 활용.

- **백엔드에서의 활용**:
    - 네트워크 연결성 관리.
    - 클러스터링, 그래프 기반 알고리즘(예: 최소 신장 트리).
