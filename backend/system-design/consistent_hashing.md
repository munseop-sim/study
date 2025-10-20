## Consistent Hashing 이란?
- Hashing을 일관되게 유지하는 방법
- 일반적으로 요청 또는 데이터를 서버에 균등하게 나누기 위해 사용되는 기술
- 일관된 해싱(안정해쉬)를 적용하지 않으면 scale-out이나 scale-in 시에 일부 서버로 요청이 몰리게되는 현상이 발생됨

## Virtual Node
- Virtual Node를 사용하여 Consistent Hashing을 구현
- Virtual Node는 실제 노드의 복제본으로, 실제 노드의 수만큼 Virtual Node를 생성하여 요청을 처리한다.
- 
### 사용사례
- Amazone Dynamo DB
- Apache Cassandra
- Discord Chat
- Akamai CDN : https://www.akamai.com/ko/glossary/what-is-a-cdn

### 참고 URL
- [Nginx-로드밸런싱-적용을-통한-채팅-서비스-개선](https://velog.io/@mw310/Nginx-%EB%A1%9C%EB%93%9C%EB%B0%B8%EB%9F%B0%EC%8B%B1-%EC%A0%81%EC%9A%A9%EC%9D%84-%ED%86%B5%ED%95%9C-%EC%B1%84%ED%8C%85-%EC%84%9C%EB%B9%84%EC%8A%A4-%EA%B0%9C%EC%84%A0-Consistent-Hashing)