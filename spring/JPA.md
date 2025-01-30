### 복합키 처리 
- IdClass
  - 개별 필드에 직접 접근할 수 있다는 이점
  - 직관적인 구현
  - JPQL, QueryDSL 사용시에 복잡도가 낮아짐
    ```java
    @Entity
    @IdClass(StudentId.class)
    public class Student {
        @Id
        private Long id;
        @Id
        private Long classId;
    
        //.....
    }
    
    public class StudentId implements Serializable {
        private Long id;
        private Long classId;
    
        //equals(), hashCode()
    }
    ```
- EmbeddedId
  - 단일 객체로 복합 키를 쉽게 캡슐화할 수 있다는 이점
    ```java
    @Entity
    public class Student {
        @EmbeddedId
        private StudentId id;
    
        //.....
    }
    
    @Embeddable
    public class StudentId implements Serializable {
        private Long id;
        private Long classId;
    
        //equals(), hashCode()
    }
    ```
    
### Spring JPA 영속성 컨텍스트
- 속성 컨텍스트(Persistence Context)는 엔티티를 영구 저장하는 환경으로, JPA가 관리하는 엔티티 객체의 집합입니다.
  - **1차 캐시**
      - 영속성 컨텍스트 내부에 엔티티를 보관
      - 동일 트랜잭션 내에서 반복 조회 시 DB 접근 없이 캐시에서 조회
  - **동일성 보장**
      - 같은 엔티티를 반복 조회해도 동일한 객체 참조 반환
      - 트랜잭션 범위의 repeatable read 보장
  - **변경 감지(Dirty Checking)**
      - 엔티티의 변경사항을 자동으로 감지
      - 트랜잭션 커밋 시점에 변경된 엔티티를 DB에 자동 반영
  - **지연 로딩(Lazy Loading)**
      - 연관된 엔티티를 실제 사용하는 시점에 로딩
      - 프록시 객체를 통한 성능 최적화
  - **엔티티의 생명주기**
      - 비영속(new/transient): 영속성 컨텍스트와 관련 없는 상태
      - 영속(managed): 영속성 컨텍스트에 저장된 상태
      - 준영속(detached): 영속성 컨텍스트에서 분리된 상태
      - 삭제(removed): 삭제된 상태
  

### OSIV
[OSIV(Open Session In View) 옵션에 대해서 설명](https://www.maeil-mail.kr/question/1)
- OSIV(open session in view) 는 영속성 컨텍스트를 뷰까지 열어둔다는 의미입
- 뷰에서도 지연로딩이 가능함.
- 트랜잭션 범위 밖에서 지연로딩을 반드시 수행해야하는 경우에는 비활성화하기 어려울 수도 있음.

- [JPA를 사용하는 이유](https://www.maeil-mail.kr/question/25)
- [JPA, Hibernate, Spring Data JPA 의 차이](https://www.maeil-mail.kr/question/26)
- [Spring Data JPA에서 새로운 Entity인지 판단하는 방법은 무엇일까](https://www.maeil-mail.kr/question/27)
- [JPA ddl-auto](https://www.maeil-mail.kr/question/28)
- [엔티티매니저](https://www.maeil-mail.kr/question/29)
- [JPA의 N + 1 문제에 대해서 설명](https://www.maeil-mail.kr/question/49)
- [JPA에서 ID 생성 전략에 대해 설명](https://www.maeil-mail.kr/question/69)