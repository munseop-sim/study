# 자료구조 핵심 정리

> 기존 파일: `HashTable.md`, `Tree.md` (해시 테이블, BST, B-Tree, B+Tree 내용 참고)

---

## 시간 복잡도와 공간 복잡도

> 출처: https://www.maeil-mail.kr/question/104

### 개념

하나의 문제를 해결하는 여러 알고리즘이 존재할 수 있으며, 개발자는 성능을 평가하여 하나를 선택해야 합니다. 코드의 정확한 실행 시간은 기계에 의존적이기 때문에, 대신 **컴퓨터가 처리해야 하는 연산의 수**를 세는 방법이 더 객관적입니다.

- **시간 복잡도(Time Complexity)**: 특정 입력을 기준으로 알고리즘이 수행하는 개략적인 연산의 수. 알고리즘의 속도를 평가하는 척도.
- **공간 복잡도(Space Complexity)**: 특정 입력을 기준으로 알고리즘이 얼마나 많은 메모리 공간을 차지하는지를 평가하는 척도.

### 빅오(Big-O) 표기법

빅오 표기법은 복잡도를 표현하는 표기법 중 하나로, 불필요한 상세를 무시하고 필수적인 부분에 집중하는 **점근적 표기법**을 따릅니다. `O(n)` 형식으로 입력값에 따라 실행 시간 및 공간 사용량이 어떻게 변하는지 설명합니다.

```java
// n번 루프 → O(n)
for (int i = 0; i < n; i++) { ... }

// 상수항, 계수 무시 → O(n)
int k = 5;
for (int i = 0; i < n * k; i++) { ... }

// 독립적인 두 루프 → O(n + m)
for (int i = 0; i < n; i++) { ... }
for (int i = 0; i < m; i++) { ... }

// 중첩 루프 → O(n^2)
for (int i = 0; i < n; i++) {
  for (int j = 0; j < n * 5; j++) {}
}

// 가장 큰 항만 남김 → O(n^2)
for (int i = 0; i < n; i++) {}
for (int i = 0; i < n; i++) {
  for (int j = 0; j < n; j++) {}
}
```

### 일반적인 복잡도 종류 (빠른 순)

| 복잡도 | 이름 | 예시 |
|---|---|---|
| O(1) | 상수 | 배열 인덱스 접근, 해시맵 조회 |
| O(log n) | 로그 | 이진 탐색, BST 탐색 |
| O(n) | 선형 | 배열 순회, 연결 리스트 탐색 |
| O(n log n) | 선형 로그 | 병합 정렬, 힙 정렬 |
| O(n^2) | 이차 | 버블 정렬, 삽입 정렬 |
| O(2^n) | 지수 | 피보나치 재귀, 부분집합 탐색 |

---

## 스택 (Stack)

> 출처: https://www.maeil-mail.kr/question/147

### 개념

**스택(Stack)** 은 **후입선출(LIFO, Last In First Out)** 개념을 가진 선형 자료구조입니다.

- 삭제(pop)와 삽입(push)은 가장 최상단(top)에서만 이루어짐
- **스택 언더플로우**: 비어있는 스택에서 값을 추출하려고 시도하는 경우
- **스택 오버플로우**: 스택의 용량이 넘치는 경우

### 활용 사례

- 스택 메모리 (함수 호출 스택)
- 브라우저 뒤로가기 기능
- 언두(Undo) 기능
- 수식 괄호 검사
- DFS(깊이 우선 탐색) 구현

### Java에서의 스택 사용

Java에서는 `Stack` 클래스를 사용할 수 있지만, **`Deque` 인터페이스 구현체를 사용하는 것이 권장**됩니다.

`Stack` 클래스를 사용하지 않는 이유:
- `Stack`은 내부적으로 `Vector`를 상속받아 인덱스를 통한 접근, 삽입, 제거 등이 가능 → 후입선출 특징에 맞지 않아 개발자가 실수할 여지 존재
- `Vector`의 메소드들은 `synchronized`로 구현되어 단일 스레드 환경에서는 불필요한 동기화 오버헤드 발생

`Deque` 인터페이스를 사용하는 이유:
- 후입선출의 특성을 완전히 유지
- 동기화 구현체(`LinkedBlockingDeque`)와 비동기화 구현체(`ArrayDeque`) 중 선택 가능
- 필요에 따라 동기화 오버헤드를 회피하고 성능 최적화 가능

```java
// 권장 방식
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1);
stack.push(2);
int top = stack.pop(); // 2
```

### 참고 자료

- [Java 의 Stack 대신 Deque](https://tecoble.techcourse.co.kr/post/2021-05-10-stack-vs-deque/)

---

## 연결 리스트 (Linked List)

> 출처: https://www.maeil-mail.kr/question/163

### 개념

**연결 리스트(Linked List)** 는 리스트 내의 요소(노드)들을 포인터로 연결하여 관리하는 선형 자료구조입니다.

- 각 노드는 **데이터**와 **다음 요소에 대한 포인터**를 가짐
- 첫 번째 노드: **HEAD**, 마지막 노드: **TAIL**
- 메모리가 허용하는 한 요소를 계속 삽입 가능
- 메모리 공간에 흩어져서 존재 (배열은 연속적인 메모리 사용)

### 시간 복잡도

| 연산 | 복잡도 | 설명 |
|---|---|---|
| 탐색 | O(n) | HEAD 포인터부터 순차 탐색 |
| 삽입 | O(1) | 포인터 조작만으로 삽입 (위치를 이미 알고 있는 경우) |
| 삭제 | O(1) | 포인터 조작만으로 삭제 (위치를 이미 알고 있는 경우) |

※ 삽입/삭제 위치 탐색이 필요한 경우 O(n)

### 배열 vs 연결 리스트 비교

| 특징 | 배열 | 연결 리스트 |
|---|---|---|
| 메모리 구조 | 연속적 | 비연속적(흩어져 있음) |
| 인덱스 접근 | O(1) | O(n) |
| 삽입/삭제 | O(n) — 요소 이동 필요 | O(1) — 포인터 조작 |
| 크기 | 고정 또는 재할당 필요 | 동적으로 확장 가능 |

### 종류

- **단일 연결 리스트(Singly Linked List)**: 각 노드가 다음 노드만 가리킴
- **이중 연결 리스트(Doubly Linked List)**: 각 노드가 이전/다음 노드 모두 가리킴
- **원형 연결 리스트(Circular Linked List)**: 마지막 노드가 첫 번째 노드를 가리킴

### 단일 연결 리스트 구현 예시 (Java)

```java
class SinglyLinkedList {

    public Node head;
    public Node tail;

    public Node insert(int newValue) {
        Node newNode = new Node(null, newValue);
        if (head == null) {
            head = newNode;
        } else {
            tail.next = newNode;
        }
        tail = newNode;
        return newNode;
    }

    public Node find(int findValue) {
        Node currentNode = head;
        while (currentNode.value != findValue) {
            currentNode = currentNode.next;
        }
        return currentNode;
    }

    public void appendNext(Node prevNode, int value) {
        prevNode.next = new Node(prevNode.next, value);
    }

    public void deleteNext(Node prevNode) {
        if (prevNode.next != null) {
            prevNode.next = prevNode.next.next;
        }
    }
}
```

---

## 트라이 (Trie)

> 출처: https://www.maeil-mail.kr/question/227

### 개념

**트라이(Trie)** 는 문자열을 저장하고 효율적으로 탐색하기 위한 **트리 형태의 자료구조**입니다.

- 루트는 항상 비어있으며, 각 간선은 추가될 문자를 키로 가짐
- 각 정점은 이전 정점의 값과 간선의 키를 더한 결과를 값으로 가짐
- 문자열을 단순 비교하는 것보다 효율적으로 탐색 가능
- 각 정점이 자식에 대한 링크를 모두 가지고 있기 때문에 **저장 공간을 더 많이 사용**

### 시간 복잡도

- 탐색/삽입: O(L) — L은 문자열의 길이

### 활용 사례

- 검색어 **자동완성** 기능
- **사전 찾기** 기능
- 텍스트 분석 및 압축
- 네트워크 라우팅 테이블

### 구현 예시 (Java)

```java
class Trie {

    private final Node root = new Node("");

    public void insert(String str) {
        Node current = root;
        for (String ch : str.split("")) {
            if (!current.children.containsKey(ch)) {
                current.children.put(ch, new Node(current.value + ch));
            }
            current = current.children.get(ch);
        }
    }

    public boolean has(String str) {
        Node current = root;
        for (String ch : str.split("")) {
            if (!current.children.containsKey(ch)) {
                return false;
            }
            current = current.children.get(ch);
        }
        return true;
    }
}

class Node {

    public String value;
    public Map<String, Node> children;

    public Node(String value) {
        this.value = value;
        this.children = new HashMap<>();
    }
}
```

### 참고 자료

- [System Design School - Design Typeahead (Autocomplete) System](https://systemdesignschool.io/problems/typeahead/solution)

---

## 이진 트리 (Binary Tree)

> 출처: https://www.maeil-mail.kr/question/282

### 개념

**트리(Tree)** 는 방향이 존재하는 그래프의 일종으로, 부모 정점 밑에 여러 자식 정점이 연결되는 재귀적 형태의 자료구조입니다. 그 중에서 **각 정점이 최대 2개의 자식 정점을 가지는 트리**를 **이진 트리(Binary Tree)** 라고 합니다.

### 이진 트리의 종류

**포화 이진 트리(Perfect Binary Tree)**: 마지막 레벨까지 모든 정점이 채워져 있는 경우
```
        1
      /   \
    2       3
   / \     / \
  4   5   6   7
```

**완전 이진 트리(Complete Binary Tree)**: 마지막 레벨을 제외하고 모든 정점이 채워져 있는 경우
```
        1
      /   \
    2       3
   / \     /
  4   5   6
```

**편향 이진 트리(Skewed Binary Tree)**: 한 방향으로만 정점이 이어지는 경우
```
    1
     \
      2
       \
        3
```

### 주요 특징

- 이진 트리의 정점이 N개인 경우, 최악의 경우 높이가 (N - 1)이 될 수 있음
- 포화/완전 이진 트리의 높이는 log N
- 높이가 h인 포화 이진 트리는 2^(h+1) - 1개의 정점을 가짐

### 활용 사례

이진 트리는 다른 자료구조를 만드는 데 주로 활용됩니다.
- **힙(Heap)**: 우선순위 큐 구현
- **이진 탐색 트리(BST)**: 검색/정렬 (→ `Tree.md` 참고)

### 순회 방법

| 순회 방법 | 방문 순서 | 활용 |
|---|---|---|
| **전위 순회(Pre-order)** | 부모 → 왼쪽 → 오른쪽 | 트리 복사, 직렬화 |
| **중위 순회(In-order)** | 왼쪽 → 부모 → 오른쪽 | BST에서 정렬된 순서로 출력 |
| **후위 순회(Post-order)** | 왼쪽 → 오른쪽 → 부모 | 트리 삭제, 폴더 크기 계산 |
| **층별 순회(Level-order)** | 레벨 단위로 방문 | BFS 탐색 |

### 이진 탐색 트리 (BST, Binary Search Tree)

- 왼쪽 서브트리의 값 < 루트 < 오른쪽 서브트리의 값
- 탐색/삽입/삭제: O(h) — h는 트리의 높이
- 편향될 경우 최악 O(n) → AVL 트리, Red-Black 트리 등 균형 트리 사용

### 참고 자료

- [코드라떼 - AVL 트리 개념](https://www.codelatte.io/courses/java_data_structure/7NPGIERM1X8R4IEW)
- [코드라떼 - Red-Black 트리 개념](https://www.codelatte.io/courses/java_data_structure/UN9UCFI8OJ7QGRCB)
