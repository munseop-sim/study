# AutoConfiguration 동작 원리
- _`@EnableAutoConfiguration` : AutoConfiguration 활성화 시키는 애노테이션_

## 순서
1. @SpringBootApplication 안에 있는 @EnableAutoConfiguration
2. @ComponentScan 어노테이션을 통해 개발자가 정의한 Component 들이 Bean 으로 먼저 등록
3. @EnableAutoConfiguration은 @Import(AutoConfigurationImportSelector.class)를 통해 자동 구성 클래스를 가져온다.
   1. `AutoConfigurationImportSelector`의 selectImport 메소드를 통해 Import할 대상을 정한다.
   2. `AutoConfigurationImportSelector`는 자동 구성할 후보 빈들을 불러와 제외되거나 중복된 빈들을 제거하는 작업을 거친 후 자동 구성할 빈들을 반환
4. spring-boot-autoconfigure 라이브러리 내부에 위치한 `META-INF/spring/org.springframework.autoconfigure.AutoConfiguration.imports`파일에 정의된 자동구성 클래스들을 등록한다.


## 간단한 메서드 동작 과정 설명
1. getCandidateConfigurations(annotationMetadata, attributes); - AutoConfiguration의 후보들을 가져온다.
2. removeDuplicates(configurations); - 중복을 제거한다.
3. getExclusions(annotationMetadata, attributes); - 자동 설정에서 제외되는 설정에 대한 정보를 가져온다.
4. configurations.removeAll(exclusions); - 제외되는 설정을 제거한다.
5. getConfigurationClassFilter().filter(configurations); - 필터를 적용한다.

## 참조링크
- [AutoConfiguration 동작 원리](https://www.maeil-mail.kr/question/23)