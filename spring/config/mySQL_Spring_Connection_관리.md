## Spring ConnectionPool (HikariCP) 
```
 master:
    datasource:
      jdbc-url: "jdbc:mysql://127.0.0.1:13001/doje?useUnicode=true&characterEncoding=......
      type: com.zaxxer.hikari.HikariDataSource
      username: ....
      password: ....
      validation-query: "SELECT 1 FROM DUAL"
      maximum-pool-size: 10 # default=10
      minimum-idle: 5 # default=10
      pool-name: "hikari-bakery-pool" 
```
참고 : https://bamdule.tistory.com/166

**hikari log 설정**
``` 
logging:
  #  config: classpath:config/logback.xml
  charset:
    console: utf-8
    file: UTF-8
  file:
    name: "./logs/local-a2d-admin.log"
  level:
    com.atd : debug
    org.springframework.web : debug
    jdbc.sqlonly: off
    jdbc.resultsettable: info
    jdbc.audit: off
    jdbc.sqltiming: info
    jdbc.resultset: off
    jdbc.connection: off
    org.hibernate.type: trace # 파라미터와 결과 로그
    org.hibernate.SQL: debug # SQL 로그
    com.zaxxer.hikari.HikariConfig: debug
```
