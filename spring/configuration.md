# Spring Configuration

## @Value 어노테이션 주의점

`application.yaml` 등 설정 파일의 값을 Spring Bean에 주입하는 어노테이션.

### 주의사항

**1. 주입 시점**: `@Value`는 스프링 빈으로 등록되고 의존 관계가 주입될 때 동작. 대상 클래스에 `@Component` 등이 없으면 빈으로 등록되지 않아 `@Value`도 동작하지 않음.

**2. 적절한 주입 방식 선택**: 필드 주입, 생성자 주입, setter 주입 모두 사용 가능. 각 방식의 장단점을 비교해 상황에 맞는 방식 선택.

**3. 프로퍼티 파일의 경로와 스코프**: `application.yaml`이 클래스 패스에 존재해야 함. 프로퍼티 파일이 여러 개일 경우 우선순위를 고려해야 함.

### `@Value` vs `@ConfigurationProperties` 비교

| 항목 | `@Value` | `@ConfigurationProperties` |
|---|---|---|
| 바인딩 방식 | 단일 값 주입 | 클래스로 여러 값을 한 번에 바인딩 |
| RelaxedBinding | 미적용 | 적용 (이름이 조금 달라도 유연하게 바인딩) |
| 사용 목적 | 간단한 단일 설정값 | 관련 설정값 그룹화 |

---

## AutoConfiguration 동작 원리

### 진입점
- `@SpringBootApplication` 내부의 **`@EnableAutoConfiguration`** 이 자동 구성의 시작점
- `@Import(AutoConfigurationImportSelector.class)`를 통해 자동 구성 클래스를 가져옴

### 동작 흐름
`AutoConfigurationImportSelector.selectImports()` 호출 → `getAutoConfigurationEntry()` 실행:

1. `getCandidateConfigurations()` — AutoConfiguration 후보 클래스 목록 로드
2. `removeDuplicates()` — 중복 제거
3. `getExclusions()` — 제외 설정 정보 수집
4. `configurations.removeAll(exclusions)` — 제외 설정 적용
5. `getConfigurationClassFilter().filter()` — `@ConditionalOnClass` 등 조건 필터 적용

조건(`@ConditionalOn...`)을 만족하는 자동 구성 클래스만 실제로 등록됨. `spring.factories` 또는 `AutoConfiguration.imports` 파일에 후보 클래스 목록이 정의됨.

---

## Spring vs Spring Boot

### Spring Framework
- 엔터프라이즈 애플리케이션 개발을 지원하는 대규모 오픈소스 프레임워크
- 개발자가 직접 수동으로 구성: 스프링 컨테이너 설정, 빈 등록, 의존성 설정, 데이터베이스 연결, 트랜잭션 관리, WAS 설치 및 설정

### Spring Boot
Spring의 복잡한 설정 문제를 해결해 **더 쉽고 빠르게 개발**할 수 있도록 만든 도구.

**주요 특징 3가지:**

1. **자동 설정(Auto Configuration)**: 클래스패스, 빈 등록 상태 등을 분석해 적합한 설정을 자동 적용
2. **의존성 관리 간소화 (Starter)**: `spring-boot-starter-web` 등 관련 라이브러리를 하나의 패키지로 묶어 버전 충돌 없이 쉽게 추가
3. **내장 서버(Embedded Server)**: Tomcat, Jetty, Undertow 내장. 독립 실행형 JAR 파일로 배포 가능 (`java -jar`)

---

## Gradle

Java, Kotlin, Scala 등 JVM 언어에서 사용하는 **빌드 자동화 도구**. Ant/Maven의 단점을 보완해 **증분 빌드, 빌드 캐시, 데몬 프로세스**로 빌드 속도 최적화.

### Maven vs Gradle 비교

| 항목 | Maven | Gradle |
|---|---|---|
| 빌드 스크립트 | XML (pom.xml) | Groovy / Kotlin DSL |
| 빌드 속도 | 느림 (항상 전체 빌드) | 빠름 (증분 빌드, 캐시) |
| 확장성 | 한정적 | 다양한 플러그인, 커스텀 태스크 |
| Android 지원 | 공식 미지원 | Android 공식 빌드 도구 |
| 멀티 프로젝트 | 상속 방식, 복잡 | 설정 주입 방식, 최적화 |

### Dependency Configuration 종류

| 설정 | 설명 | 예시 |
|---|---|---|
| `implementation` | 컴파일 + 런타임 (현재 모듈에서만) | 일반 라이브러리 |
| `api` | 컴파일 + 런타임 (다른 모듈에도 노출) | 멀티모듈에서 공유 라이브러리 |
| `compileOnly` | 컴파일 시점만 | Lombok |
| `annotationProcessor` | 컴파일 시점 어노테이션 처리 | Lombok, MapStruct |
| `runtimeOnly` | 런타임 시점만 | DB 드라이버 |
| `testImplementation` 등 | 테스트 코드 전용 | JUnit, Mockito |

참고: [Gradle Docs - The Java Plugin](https://docs.gradle.org/8.5/userguide/java_plugin.html)
