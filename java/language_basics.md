# Java Language Basics

## `main` 메서드가 `static`인 이유
- 객체 생성 없이 JVM이 진입점을 바로 호출해야 하기 때문
- 클래스 기준 호출 방식(`ClassName.main(args)`)과 맞음

## 오버로딩 vs 오버라이딩
- 오버로딩: 같은 이름, 다른 시그니처
- 오버라이딩: 상위 타입 메서드 재정의

## equals/hashCode
- 컬렉션(`HashMap`, `HashSet`) 일관성을 위해 함께 재정의 필요

## 동등성 vs 동일성
- 동등성: `equals` (값 비교)
- 동일성: `==` (참조 비교)

## 참고
- [일급 컬렉션이 무엇인가요?](https://www.maeil-mail.kr/question/53)
