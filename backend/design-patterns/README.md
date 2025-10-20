# Design Patterns

GoF(Gang of Four) 디자인 패턴을 정리한 디렉토리입니다.

## 📚 Contents

### [design-pattern.md](./design-pattern.md)

## 패턴 분류

### 생성 패턴 (Creational Patterns)
객체 생성 메커니즘을 다루는 패턴
- **Builder**: 복잡한 객체의 생성 과정과 표현 방법을 분리
- **Factory**: 객체 생성 로직을 캡슐화
- **Singleton**: 클래스의 인스턴스가 하나만 존재하도록 보장

### 구조 패턴 (Structural Patterns)
클래스나 객체를 조합해 더 큰 구조를 만드는 패턴
- **Decorator**: 객체에 동적으로 새로운 책임을 추가
- **Proxy**: 다른 객체에 대한 접근을 제어하기 위한 대리자
- **Adapter**: 호환되지 않는 인터페이스를 연결
- **Facade**: 복잡한 서브시스템에 대한 단순한 인터페이스 제공

### 행동 패턴 (Behavioral Patterns)
객체 간의 알고리즘이나 책임 분배에 관한 패턴
- **Observer**: 객체 상태 변화를 관찰자들에게 자동 통지
- **Strategy**: 알고리즘군을 정의하고 캡슐화하여 상호 교환 가능하게 함
- **Template**: 알고리즘의 구조를 정의하고 일부 단계를 서브클래스에 위임

## Spring에서 사용되는 패턴
Spring Framework에서도 다양한 디자인 패턴이 활용됩니다:
- **Singleton**: Bean 관리
- **Factory**: BeanFactory, ApplicationContext
- **Proxy**: AOP
- **Template Method**: JdbcTemplate, RestTemplate
- **Observer**: Spring Event Listener
