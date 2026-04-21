# Spring Security

## 1. Spring Security 아키텍처

### SecurityFilterChain 구조

Spring Security는 서블릿 필터 기반으로 동작하며, 다음과 같은 위임 구조를 가진다.

```
HTTP 요청
  → Servlet Container (Tomcat)
    → DelegatingFilterProxy          ← 서블릿 필터, Spring Bean에 위임
      → FilterChainProxy             ← Spring Bean, SecurityFilterChain 목록 관리
        → SecurityFilterChain #0     ← /api/** 패턴 매칭
        → SecurityFilterChain #1     ← /** 패턴 매칭
```

| 컴포넌트 | 역할 |
|---|---|
| **DelegatingFilterProxy** | 서블릿 컨테이너에 등록된 필터. 실제 처리를 Spring Bean(`FilterChainProxy`)에 위임 |
| **FilterChainProxy** | `springSecurityFilterChain`이라는 이름의 Spring Bean. 다수의 `SecurityFilterChain`을 순서대로 관리 |
| **SecurityFilterChain** | URL 패턴별로 적용할 보안 필터 목록을 정의. 첫 번째 매칭된 체인만 실행 |

### 주요 필터 순서

Spring Security는 내부적으로 약 30개 이상의 필터를 정해진 순서로 실행한다. 주요 필터는 다음과 같다.

| 순서 | 필터 | 역할 |
|---|---|---|
| 1 | `DisableEncodeUrlFilter` | URL에 세션 ID가 포함되지 않도록 방지 |
| 2 | `SecurityContextPersistenceFilter` | 요청 시작 시 SecurityContext 복원, 응답 시 저장 (Spring Security 6에서는 `SecurityContextHolderFilter`로 대체) |
| 3 | `CorsFilter` | CORS 사전 요청(Preflight) 처리 |
| 4 | `CsrfFilter` | CSRF 토큰 검증 |
| 5 | `LogoutFilter` | 로그아웃 요청 처리 |
| 6 | `UsernamePasswordAuthenticationFilter` | 폼 로그인 기반 인증 처리 (`/login` POST) |
| 7 | `BasicAuthenticationFilter` | HTTP Basic 인증 헤더 처리 |
| 8 | `BearerTokenAuthenticationFilter` | OAuth2/JWT Bearer 토큰 처리 |
| 9 | `ExceptionTranslationFilter` | 인증/인가 예외를 HTTP 응답으로 변환 |
| 10 | `AuthorizationFilter` | 최종 인가 결정 (기존 `FilterSecurityInterceptor` 대체) |

> Spring Security 6.x 기준. 버전에 따라 필터 이름과 순서가 다를 수 있다.

### Authentication 객체와 SecurityContext

```
SecurityContextHolder
  └── SecurityContext
        └── Authentication
              ├── Principal      ← 인증된 사용자 정보 (UserDetails 등)
              ├── Credentials    ← 비밀번호 (인증 후 보통 null로 초기화)
              └── Authorities    ← 권한 목록 (Collection<GrantedAuthority>)
```

| 컴포넌트 | 역할 |
|---|---|
| **SecurityContextHolder** | `SecurityContext`를 보관하는 정적 유틸리티. 기본 전략은 `ThreadLocal` (MODE_THREADLOCAL) |
| **SecurityContext** | 현재 인증 정보(`Authentication`)를 감싸는 컨테이너 |
| **Authentication** | 인증 전에는 사용자가 입력한 정보, 인증 후에는 인증된 사용자 정보를 담는 토큰 |

```java
// 현재 인증 정보 조회
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
String username = authentication.getName();
Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
```

**SecurityContextHolder 전략**

| 전략 | 설명 | 용도 |
|---|---|---|
| `MODE_THREADLOCAL` | 각 스레드별 SecurityContext 보관 (기본값) | 일반적인 서블릿 환경 |
| `MODE_INHERITABLETHREADLOCAL` | 자식 스레드에도 SecurityContext 전파 | `@Async` 사용 시 |
| `MODE_GLOBAL` | 애플리케이션 전역 공유 | 독립 실행형(데스크톱) 앱 |

---

## 2. 인증(Authentication) 흐름

### AuthenticationManager → ProviderManager → AuthenticationProvider

```
UsernamePasswordAuthenticationFilter
  → AuthenticationManager (인터페이스)
    → ProviderManager (구현체)
      → AuthenticationProvider #1 (DaoAuthenticationProvider 등)
      → AuthenticationProvider #2
      → ...
      → Parent ProviderManager (선택적)
```

**동작 흐름**
1. 필터가 사용자 요청에서 인증 정보를 추출하여 `Authentication` 객체(미인증 토큰) 생성
2. `AuthenticationManager.authenticate()` 호출
3. `ProviderManager`가 등록된 `AuthenticationProvider` 목록을 순회
4. 각 Provider가 `supports()` 메서드로 해당 인증 타입 지원 여부 확인
5. 지원하는 Provider가 `authenticate()` 실행
6. 인증 성공 시 `Authentication` 객체(인증 완료 토큰) 반환
7. 실패 시 `AuthenticationException` 던짐

```java
// AuthenticationManager 인터페이스
public interface AuthenticationManager {
    Authentication authenticate(Authentication authentication) throws AuthenticationException;
}

// ProviderManager: 여러 AuthenticationProvider에 위임
public class ProviderManager implements AuthenticationManager {
    private List<AuthenticationProvider> providers;
    private AuthenticationManager parent; // 부모 ProviderManager (선택적)
}

// AuthenticationProvider 인터페이스
public interface AuthenticationProvider {
    Authentication authenticate(Authentication authentication) throws AuthenticationException;
    boolean supports(Class<?> authentication);
}
```

### UserDetailsService와 UserDetails

`DaoAuthenticationProvider`는 `UserDetailsService`를 사용하여 DB에서 사용자 정보를 조회한다.

```java
// UserDetailsService 인터페이스
public interface UserDetailsService {
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException;
}

// UserDetails 인터페이스 - 인증에 필요한 사용자 정보 제공
public interface UserDetails extends Serializable {
    Collection<? extends GrantedAuthority> getAuthorities();
    String getPassword();
    String getUsername();
    boolean isAccountNonExpired();
    boolean isAccountNonLocked();
    boolean isCredentialsNonExpired();
    boolean isEnabled();
}
```

**커스텀 구현 예시**

```java
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member member = memberRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        return User.builder()
                .username(member.getEmail())
                .password(member.getPassword())
                .roles(member.getRole().name())
                .build();
    }
}
```

### 인증 성공/실패 핸들러

| 핸들러 | 호출 시점 | 주요 용도 |
|---|---|---|
| `AuthenticationSuccessHandler` | 인증 성공 시 | JWT 토큰 발급, 로그인 이력 저장, 리다이렉트 |
| `AuthenticationFailureHandler` | 인증 실패 시 | 실패 횟수 기록, 계정 잠금, 에러 응답 반환 |

```java
// REST API 환경의 성공 핸들러 예시
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\": \"로그인 성공\"}");
    }
}

// REST API 환경의 실패 핸들러 예시
@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"인증 실패: " + exception.getMessage() + "\"}");
    }
}
```

---

## 3. 인가(Authorization)

### GrantedAuthority, Role vs Authority

`GrantedAuthority`는 인증된 사용자에게 부여된 권한을 나타내는 인터페이스이다.

```java
public interface GrantedAuthority extends Serializable {
    String getAuthority();
}
```

| 구분 | 형식 | 예시 | 설명 |
|---|---|---|---|
| **Role** | `ROLE_` 접두사 | `ROLE_ADMIN`, `ROLE_USER` | 사용자의 역할(그룹) 단위. `hasRole("ADMIN")`은 내부적으로 `ROLE_ADMIN`을 검사 |
| **Authority** | 접두사 없음 | `READ_PRIVILEGE`, `WRITE_PRIVILEGE` | 세밀한 개별 권한 단위. `hasAuthority("READ_PRIVILEGE")`로 검사 |

> `hasRole("ADMIN")`과 `hasAuthority("ROLE_ADMIN")`은 동일하게 동작한다. `hasRole()`은 자동으로 `ROLE_` 접두사를 붙여서 검사한다.

### Method Security

메서드 수준에서 인가를 적용하는 방식. `@EnableMethodSecurity`로 활성화한다.

```java
@Configuration
@EnableMethodSecurity  // Spring Security 6.x
public class SecurityConfig { }
```

| 어노테이션 | 평가 시점 | SpEL 지원 | 설명 |
|---|---|---|---|
| `@PreAuthorize` | 메서드 실행 전 | O | 가장 유연하고 권장되는 방식 |
| `@PostAuthorize` | 메서드 실행 후 | O | 반환값 기반 인가 검사 |
| `@Secured` | 메서드 실행 전 | X | 단순 역할 기반 검사만 가능 |

```java
@Service
public class MemberService {

    // SpEL 표현식으로 복합 조건 가능
    @PreAuthorize("hasRole('ADMIN') or #memberId == authentication.principal.id")
    public MemberDto getMember(Long memberId) { ... }

    // 반환값(returnObject)을 기반으로 인가 검사
    @PostAuthorize("returnObject.createdBy == authentication.name")
    public Document getDocument(Long documentId) { ... }

    // 단순 역할 기반
    @Secured("ROLE_ADMIN")
    public void deleteAllMembers() { ... }
}
```

### URL 기반 인가

Spring Security 6.x에서는 `authorizeHttpRequests`와 `requestMatchers`를 사용한다.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/public/**").permitAll()          // 인증 불필요
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")     // ADMIN 역할 필요
                .requestMatchers(HttpMethod.POST, "/api/v1/members").hasAuthority("WRITE_MEMBER")
                .anyRequest().authenticated()                              // 나머지는 인증 필요
            );

        return http.build();
    }
}
```

**주요 인가 표현식**

| 표현식 | 설명 |
|---|---|
| `permitAll()` | 모든 요청 허용 (인증 불필요) |
| `denyAll()` | 모든 요청 거부 |
| `authenticated()` | 인증된 사용자만 허용 |
| `hasRole("ADMIN")` | 특정 역할 보유 시 허용 (`ROLE_` 접두사 자동 부여) |
| `hasAuthority("WRITE")` | 특정 권한 보유 시 허용 |
| `hasAnyRole("ADMIN", "MANAGER")` | 여러 역할 중 하나 이상 보유 시 허용 |
| `hasIpAddress("192.168.1.0/24")` | 특정 IP 대역 허용 |

> **주의**: `requestMatchers`의 순서가 중요하다. 구체적인 패턴을 먼저, 포괄적인 패턴(`anyRequest()`)을 마지막에 배치해야 한다.

---

## 4. JWT 기반 인증 구현

### JWT 토큰 구조

JWT(JSON Web Token)는 `Header.Payload.Signature` 세 부분을 `.`으로 연결한 문자열이다.

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyMSIsImlhdCI6MTcwMH0.abc123signature
├── Header ──────────┤├── Payload ────────────────────────┤├── Signature ──┤
```

| 구성 요소 | 내용 | 인코딩 |
|---|---|---|
| **Header** | 알고리즘(`alg`), 토큰 타입(`typ`) | Base64Url |
| **Payload** | 클레임(Claims): `sub`, `iat`, `exp`, 커스텀 클레임 등 | Base64Url |
| **Signature** | `HMACSHA256(base64UrlEncode(header) + "." + base64UrlEncode(payload), secret)` | - |

> Payload는 암호화되지 않고 인코딩만 된다. **민감 정보(비밀번호 등)를 Payload에 넣으면 안 된다.**

### Access Token + Refresh Token 전략

| 구분 | Access Token | Refresh Token |
|---|---|---|
| **용도** | API 요청 시 인증 수단 | Access Token 재발급 수단 |
| **유효 기간** | 짧음 (15분 ~ 1시간) | 길음 (7일 ~ 30일) |
| **저장 위치** | 메모리 또는 httpOnly Cookie | httpOnly Cookie 또는 서버 DB |
| **탈취 시 위험** | 유효 기간이 짧아 피해 제한적 | 새 Access Token을 발급할 수 있어 위험도 높음 |

**토큰 재발급 흐름**
1. 클라이언트가 Access Token으로 API 요청
2. Access Token 만료 시 서버가 `401 Unauthorized` 응답
3. 클라이언트가 Refresh Token으로 재발급 요청 (`/api/v1/auth/refresh`)
4. 서버가 Refresh Token 검증 후 새 Access Token (+ 새 Refresh Token) 발급
5. 클라이언트가 새 Access Token으로 원래 API 재요청

### Refresh Token Rotation (RTR)

Refresh Token Rotation은 Refresh Token 사용 시 **새로운 Refresh Token도 함께 발급**하고, 이전 Refresh Token을 즉시 무효화하는 전략이다.

**동작 원리**
1. 클라이언트가 Refresh Token A로 재발급 요청
2. 서버가 검증 후 **새 Access Token + 새 Refresh Token B** 발급
3. Refresh Token A는 즉시 폐기(DB에서 삭제 또는 무효화 처리)
4. 이후 누군가 Refresh Token A를 사용하면 → **토큰 탈취로 간주**, 해당 사용자의 모든 Refresh Token 무효화

**RTR의 장점**
- Refresh Token 탈취를 탐지할 수 있다: 이미 사용된(폐기된) 토큰이 재사용되면 공격으로 판단
- 토큰의 유효 기간을 실질적으로 단축하는 효과 (매번 새로 발급)
- 탈취 탐지 시 해당 사용자의 모든 세션을 강제 로그아웃 가능

```java
@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        // 1. Refresh Token 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidTokenException("유효하지 않은 리프레시 토큰");
        }

        // 2. DB에서 Refresh Token 조회
        RefreshToken savedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> {
                    // 이미 사용된 토큰 → 탈취 의심 → 해당 사용자 모든 토큰 무효화
                    String username = jwtTokenProvider.getUsername(refreshToken);
                    refreshTokenRepository.deleteAllByUsername(username);
                    return new TokenTheftException("토큰 재사용 탐지. 모든 세션 무효화.");
                });

        // 3. 이전 Refresh Token 폐기
        refreshTokenRepository.delete(savedToken);

        // 4. 새 토큰 쌍 발급
        String newAccessToken = jwtTokenProvider.createAccessToken(savedToken.getUsername());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(savedToken.getUsername());

        // 5. 새 Refresh Token 저장
        refreshTokenRepository.save(new RefreshToken(savedToken.getUsername(), newRefreshToken));

        return new TokenResponse(newAccessToken, newRefreshToken);
    }
}
```

### JWT Filter 구현 패턴

JWT 인증 필터는 `OncePerRequestFilter`를 상속하여 구현하며, `UsernamePasswordAuthenticationFilter` **앞에** 배치한다.

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. 요청에서 토큰 추출
        String token = resolveToken(request);

        // 2. 토큰 유효성 검증 및 SecurityContext 설정
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 3. 다음 필터로 전달
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

**필터 등록**

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        // ...
    ;
    return http.build();
}
```

> `OncePerRequestFilter`를 사용하는 이유: 서블릿 컨테이너에서 `forward`나 `include`로 인해 같은 요청이 필터를 여러 번 통과할 수 있는데, `OncePerRequestFilter`는 요청당 **한 번만 실행**을 보장한다.

### 토큰 저장 위치 비교

| 저장 위치 | XSS 방어 | CSRF 방어 | 구현 난이도 | 설명 |
|---|---|---|---|---|
| **httpOnly Cookie** | O (JS 접근 불가) | X (별도 CSRF 방어 필요) | 중간 | 가장 권장되는 방식. `SameSite=Strict` + CSRF 토큰으로 보완 |
| **LocalStorage** | X (JS로 접근 가능) | O (자동 전송 안 됨) | 쉬움 | XSS 취약. 보안이 중요한 서비스에서는 비권장 |
| **SessionStorage** | X (JS로 접근 가능) | O (자동 전송 안 됨) | 쉬움 | 탭 간 공유 불가, 탭 닫으면 삭제 |
| **Memory (변수)** | O (JS 접근 범위 제한) | O (자동 전송 안 됨) | 어려움 | 새로고침 시 토큰 소실 → Refresh Token과 조합 필요 |

**권장 조합**: Access Token은 **메모리(JavaScript 변수)**에, Refresh Token은 **httpOnly + Secure + SameSite Cookie**에 저장

---

## 5. CSRF 보호와 JWT의 관계

### Stateless REST API에서 CSRF 보호를 비활성화하는 이유

**CSRF(Cross-Site Request Forgery)** 공격은 브라우저가 **쿠키를 자동으로 전송**하는 특성을 악용한다.

| 인증 방식 | 쿠키 자동 전송 | CSRF 취약 여부 |
|---|---|---|
| 세션 기반 (JSESSIONID 쿠키) | O | **취약** → CSRF 보호 필수 |
| JWT (Authorization 헤더) | X | **안전** → CSRF 보호 불필요 |
| JWT (httpOnly Cookie) | O | **취약** → CSRF 보호 필요 |

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // Stateless + JWT(헤더 전송) 환경에서는 CSRF 비활성화
        .csrf(csrf -> csrf.disable())
        // 세션을 사용하지 않음
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // ...
    ;
    return http.build();
}
```

> JWT를 **httpOnly Cookie**로 전송하는 경우에는 CSRF 보호를 비활성화하면 안 된다. 이 경우 `SameSite` 속성 설정 + CSRF 토큰 사용을 병행해야 한다.

### CORS 설정과 Spring Security

**CORS(Cross-Origin Resource Sharing)**는 브라우저가 다른 출처(Origin)의 리소스에 접근할 때 서버가 이를 허용하는지 확인하는 메커니즘이다.

Spring Security에서 CORS를 설정하지 않으면, 보안 필터가 CORS Preflight 요청(`OPTIONS`)을 차단할 수 있다.

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        // ...
    ;
    return http.build();
}

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("https://frontend.example.com"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(true);  // 쿠키 포함 요청 허용
    configuration.setMaxAge(3600L);           // Preflight 캐시 1시간

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
}
```

**CORS + Spring Security 주의사항**
- `CorsFilter`는 `SecurityFilterChain` 내에서 가장 먼저 실행되어야 한다 (Spring Security가 자동 처리)
- `allowCredentials(true)` 설정 시 `allowedOrigins`에 `*`(와일드카드)를 사용할 수 없다 → 구체적인 Origin 명시 필요
- `allowedHeaders`에 `Authorization`을 반드시 포함해야 JWT 토큰 전달이 가능하다

---

## 6. 비밀번호 인코딩

### PasswordEncoder 인터페이스

```java
public interface PasswordEncoder {
    String encode(CharSequence rawPassword);                          // 비밀번호 → 해시
    boolean matches(CharSequence rawPassword, String encodedPassword); // 비밀번호 검증
    default boolean upgradeEncoding(String encodedPassword) {          // 재인코딩 필요 여부
        return false;
    }
}
```

**주요 구현체**

| 구현체 | 알고리즘 | 설명 |
|---|---|---|
| `BCryptPasswordEncoder` | bcrypt | **가장 널리 사용**. 내부적으로 Salt 자동 생성, 비용 인자(strength) 조절 가능 |
| `Argon2PasswordEncoder` | Argon2 | 메모리 집약적 해시. GPU 공격에 강함 |
| `SCryptPasswordEncoder` | scrypt | CPU + 메모리 집약적 해시 |
| `Pbkdf2PasswordEncoder` | PBKDF2 | NIST 권장 알고리즘 |
| `NoOpPasswordEncoder` | 평문 저장 | **테스트 전용. 절대 운영 환경에서 사용 금지** |
| `DelegatingPasswordEncoder` | 다중 알고리즘 | `{bcrypt}`, `{argon2}` 등 접두사로 알고리즘 판별. Spring Security 기본값 |

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(); // 기본 strength: 10
}

// DelegatingPasswordEncoder 사용 (Spring Security 기본)
@Bean
public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    // 인코딩 결과: {bcrypt}$2a$10$... 형태
}
```

### 단방향 해시 + Salt의 원리

**단방향 해시(One-way Hash)**
- 입력(비밀번호) → 해시값 변환은 가능하지만, 해시값 → 원본 복원은 **불가능**
- 동일한 입력은 항상 동일한 해시값 생성 → **레인보우 테이블** 공격에 취약

**Salt**
- 해시 생성 시 추가되는 **랜덤 문자열**
- 동일한 비밀번호라도 Salt가 다르면 완전히 다른 해시값 생성
- 레인보우 테이블 공격 무력화

```
// Salt 없이 해시
password: "1234" → hash: "03ac674216f3e15c..."   (모든 "1234"가 동일한 해시)

// Salt 포함 해시
password: "1234" + salt: "x8kQ" → hash: "7f2b1a9e..."
password: "1234" + salt: "p3Lm" → hash: "c4d8e2f1..."   (같은 비밀번호, 다른 해시)
```

**bcrypt의 동작 방식**
```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 │  │  │                                    │
 │  │  └── Salt (22자, Base64)               └── 해시값 (31자)
 │  └── Cost Factor (2^10 = 1024 라운드)
 └── 알고리즘 버전 (2a)
```

- **Cost Factor(비용 인자)**: 해시 계산 반복 횟수를 결정. 값이 클수록 해시 생성이 느려져 무차별 대입 공격에 강함
- bcrypt는 Salt를 해시 결과에 포함하여 저장하므로 별도 Salt 컬럼이 불필요
- 검증 시 저장된 해시에서 Salt를 추출하여 입력된 비밀번호를 같은 방식으로 해시한 후 비교

---

## 면접 빈출 질문 정리

**Q1. Spring Security의 인증 처리 흐름을 설명해 주세요.**
> 요청이 `SecurityFilterChain`의 필터를 순서대로 통과하며, 인증 필터(예: `UsernamePasswordAuthenticationFilter`)가 `AuthenticationManager`에 인증을 위임합니다. `ProviderManager`가 적절한 `AuthenticationProvider`를 찾아 인증을 수행하고, 성공 시 `Authentication` 객체를 `SecurityContextHolder`에 저장합니다.

**Q2. JWT를 사용할 때 CSRF를 비활성화해도 되는 이유는?**
> JWT를 `Authorization` 헤더로 전송하는 경우, 브라우저가 자동으로 토큰을 전송하지 않으므로 CSRF 공격이 불가능합니다. 단, JWT를 쿠키로 전송하는 경우에는 CSRF 보호가 여전히 필요합니다.

**Q3. Access Token과 Refresh Token을 분리하는 이유는?**
> Access Token의 유효 기간을 짧게 설정하여 탈취 시 피해를 최소화하면서, Refresh Token으로 사용자 경험(재로그인 빈도)을 유지합니다. Refresh Token Rotation을 적용하면 토큰 탈취도 탐지할 수 있습니다.

**Q4. 비밀번호를 단방향 해시로 저장하는 이유는?**
> DB가 유출되더라도 원본 비밀번호를 복원할 수 없게 하기 위해서입니다. Salt를 추가하면 동일한 비밀번호라도 서로 다른 해시값을 가지므로 레인보우 테이블 공격을 방어할 수 있습니다.

**Q5. `@PreAuthorize`와 `@Secured`의 차이는?**
> `@PreAuthorize`는 SpEL 표현식을 지원하여 복합 조건(역할, 파라미터, 인증 정보 등)을 유연하게 표현할 수 있고, `@Secured`는 단순 역할 기반 검사만 가능합니다.
