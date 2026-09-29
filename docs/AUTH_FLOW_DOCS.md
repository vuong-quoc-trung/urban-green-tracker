# 🔐 Urban Green Tracker — Tài Liệu Chi Tiết Luồng Login & Register

> **Cập nhật:** 2026-09-20  
> **Phạm vi:** Toàn bộ luồng xác thực người dùng (Register → Login → Token → API Request → Logout)  
> **File này dùng nội bộ, không commit lên repository.**

---

## Mục lục

1. [Tổng quan kiến trúc Auth](#1-tổng-quan-kiến-trúc-auth)
2. [Cấu trúc thư mục & liên kết giữa các file](#2-cấu-trúc-thư-mục--liên-kết-giữa-các-file)
3. [Data Transfer Objects (DTOs)](#3-data-transfer-objects-dtos)
4. [Luồng REGISTER chi tiết](#4-luồng-register-chi-tiết)
5. [Luồng LOGIN chi tiết](#5-luồng-login-chi-tiết)
6. [Cơ chế JWT — Access Token](#6-cơ-chế-jwt--access-token)
7. [Cơ chế Refresh Token (Redis)](#7-cơ-chế-refresh-token-redis)
8. [Bảo vệ Brute-Force (LoginAttemptService)](#8-bảo-vệ-brute-force-loginattemptservice)
9. [Luồng mỗi API Request có xác thực](#9-luồng-mỗi-api-request-có-xác-thực)
10. [Luồng LOGOUT & LOGOUT ALL DEVICES](#10-luồng-logout--logout-all-devices)
11. [Xử lý lỗi toàn cục (Exception Handling)](#11-xử-lý-lỗi-toàn-cục-exception-handling)
12. [Bảo mật Cookie — Tại sao Refresh Token lại để trong Cookie?](#12-bảo-mật-cookie--tại-sao-refresh-token-lại-để-trong-cookie)
13. [Sơ đồ dữ liệu trong Redis](#13-sơ-đồ-dữ-liệu-trong-redis)
14. [Cấu hình môi trường liên quan](#14-cấu-hình-môi-trường-liên-quan)

---

## 1. Tổng quan kiến trúc Auth

Hệ thống auth của dự án theo mô hình **Stateless JWT** với hai loại token:

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENT (Browser)                      │
│  Lưu: Access Token (memory/localStorage)                    │
│  Lưu: Refresh Token (HttpOnly Cookie — JS không đọc được)   │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTP Request
                             ▼
┌─────────────────────────────────────────────────────────────┐
│              JwtAuthenticationFilter (Spring Filter)         │
│  Đọc Authorization header → Xác minh Access Token           │
│  → Ghi userId + role vào SecurityContext                    │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│              SecurityConfig (Route-level Auth)               │
│  - Public: /login, /register, /refresh, /logout             │
│  - ADMIN/MANAGER: /api/v1/admin/**                          │
│  - Còn lại: cần xác thực                                    │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│              AuthenticationController                        │
│  POST /api/v1/auth/login                                    │
│  POST /api/v1/auth/register                                 │
│  POST /api/v1/auth/refresh                                  │
│  POST /api/v1/auth/logout                                   │
│  POST /api/v1/auth/logout-all                               │
└────────────────────────────┬────────────────────────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
       AuthService    TokenService    RefreshTokenService
              │              │              │
              ▼              ▼              ▼
       PostgreSQL          Redis           Redis
       (users table)  (blacklist)     (refresh tokens)
```

**Hai loại token và vai trò:**

| Token | Thời hạn | Lưu ở đâu | Mục đích |
|-------|----------|-----------|---------|
| Access Token (JWT) | 15 phút (900s) | Frontend memory | Gửi kèm mỗi API request |
| Refresh Token (random bytes) | 7 ngày (604800s) | HttpOnly Cookie | Xin cấp lại Access Token mới |

---

## 2. Cấu trúc thư mục & liên kết giữa các file

```
auth/
├── controller/
│   └── AuthenticationController.java   ← Nhận HTTP request, gọi AuthService
├── service/
│   ├── AuthService.java                ← Logic nghiệp vụ chính (login, register, logout)
│   ├── TokenService.java               ← Tạo & xác minh Access Token (JWT)
│   ├── RefreshTokenService.java        ← Tạo, xoay vòng & thu hồi Refresh Token
│   └── LoginAttemptService.java        ← Chống brute-force bằng Redis counter
├── security/
│   ├── JwtAuthenticationFilter.java    ← Filter chặn mọi request, check Access Token
│   └── SecurityConfig.java             ← Cấu hình Spring Security, phân quyền route
└── dto/
    ├── LoginRequest.java               ← Input: identifier (email/username) + password
    ├── LoginResponse.java              ← Output: accessToken (refreshToken null ở body)
    └── RegisterRequest.java            ← Input: username + email + password

exception/
├── AppException.java                   ← Custom exception mang ErrorCode
├── ErrorCode.java                      ← Enum toàn bộ mã lỗi của hệ thống
├── ApiResponse.java                    ← Wrapper chuẩn cho mọi HTTP response
└── GlobalExceptionHandler.java         ← @ControllerAdvice bắt exception toàn cục
```

**Luồng phụ thuộc (dependency):**
```
AuthenticationController
    └─ inject ──▶ AuthService
                      ├─ inject ──▶ UserRepository     (Spring Data JPA → PostgreSQL)
                      ├─ inject ──▶ PasswordEncoder     (BCrypt, khai báo trong SecurityConfig)
                      ├─ inject ──▶ LoginAttemptService (Redis counter)
                      ├─ inject ──▶ TokenService        (JWT + Redis blacklist)
                      └─ inject ──▶ RefreshTokenService (Redis refresh tokens)
```

---

## 3. Data Transfer Objects (DTOs)

### 3.1 LoginRequest.java
```java
@Data
public class LoginRequest {
    @NotBlank(message = "EMAIL_OR_USERNAME_NOT_BLANK")
    private String identifier;   // Nhận cả email hoặc username

    @NotBlank(message = "PASSWORD_NOT_BLANK")
    private String password;
}
```
**Giải thích:**
- `identifier` có thể là **email** hoặc **username** — người dùng không cần nhớ loại nào.
- `@NotBlank` kết hợp với `@Valid` trong controller để Spring tự động validate trước khi vào service.
- Nếu trống → Spring throw `MethodArgumentNotValidException` → `GlobalExceptionHandler` bắt → trả `400 Bad Request`.

### 3.2 RegisterRequest.java
```java
@Data
public class RegisterRequest {
    @NotBlank(message = "USERNAME_NOT_BLANK")
    @Size(min = 3, max = 50, message = "USERNAME_INVALID_LENGTH")
    private String username;

    @NotBlank(message = "EMAIL_NOT_BLANK")
    @Email(message = "EMAIL_INVALID_FORMAT")
    private String email;

    @NotBlank(message = "PASSWORD_NOT_BLANK")
    @Size(min = 8, message = "PASSWORD_TOO_SHORT")
    private String password;
}
```
**Giải thích:**
- `@Email` validate định dạng email hợp lệ theo RFC.
- `@Size(min = 8)` đảm bảo mật khẩu tối thiểu 8 ký tự — validation ở tầng API trước khi vào DB.
- Message string dạng KEY (vd: `"USERNAME_NOT_BLANK"`) thay vì text → dễ i18n sau này.

### 3.3 LoginResponse.java
```java
@Data
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;  // Chỉ dùng tạm trong nội bộ, bị set null trước khi trả về client
    private String tokenType;     // Hiện chưa set, sẽ là "Bearer"
}
```
**Kỹ thuật quan trọng:** `refreshToken` được set vào cookie trước rồi `response.setRefreshToken(null)` — client nhận response body **không thấy** refresh token.

---

## 4. Luồng REGISTER chi tiết

```
Client ──POST /api/v1/auth/register──▶ AuthenticationController.register()
                                               │
                                    @Valid kiểm tra RegisterRequest
                                    (username, email, password)
                                               │ Nếu lỗi → 400 Bad Request
                                               │ Nếu OK ↓
                                        AuthService.register()
                                               │
                               ┌───────────────┼───────────────┐
                               ▼               ▼               ▼
                    existsByEmail()   existsByUsername()   (cả hai query DB)
                               │
                    Email trùng → throw AppException(EMAIL_EXISTED) → 409
                    Username trùng → throw AppException(USERNAME_EXISTED) → 409
                               │ Nếu không trùng ↓
                    passwordEncoder.encode(password)     ← BCrypt hash
                               │
                    User.builder()
                      .username(username)
                      .email(email.toLowerCase())         ← normalize email
                      .password(BCrypt hash)
                      .provider("local")                  ← đăng ký thường
                      .role(Role.USER)                    ← mặc định USER
                      .status(UserStatus.ACTIVE)          ← active ngay
                      .totalPoints(0)
                      .monthlyPoints(0)
                      .build()
                               │
                    userRepository.save(newUser)          ← INSERT vào PostgreSQL
                               │
                    ┌──────────┴──────────┐
                    ▼                     ▼
          generateAccessToken()   generateRefreshToken()
          (TokenService)          (RefreshTokenService)
                    │                     │
                    └──────────┬──────────┘
                               │
                    LoginResponse{accessToken, refreshToken}
                               │
                    Controller nhận response
                    buildCookie(refreshToken, 7 ngày)    ← HttpOnly Cookie
                    response.setRefreshToken(null)        ← xóa khỏi body
                               │
                    Trả về client:
                    Header: Set-Cookie: refreshToken=...; HttpOnly; Path=/api/v1/auth/refresh
                    Body: { "accessToken": "eyJ..." }
```

**Code tương ứng — AuthService.register():**
```java
public LoginResponse register(RegisterRequest request) {
    String email = request.getEmail().trim().toLowerCase();
    String username = request.getUsername().trim();

    if (userRepository.existsByEmail(email)) {
        throw new AppException(ErrorCode.EMAIL_EXISTED);       // 409
    }
    if (userRepository.existsByUsername(username)) {
        throw new AppException(ErrorCode.USERNAME_EXISTED);    // 409
    }

    User newUser = User.builder()
            .username(username)
            .email(email)
            .password(passwordEncoder.encode(request.getPassword()))
            .provider("local")
            .role(Role.USER)
            .status(UserStatus.ACTIVE)
            .totalPoints(0)
            .monthlyPoints(0)
            .build();

    userRepository.save(newUser);

    // Auto-login sau register
    String accessToken = tokenService.generateAccessToken(newUser);
    String refreshToken = refreshTokenService.generateRefreshToken(newUser.getId());

    return LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .build();
}
```

---

## 5. Luồng LOGIN chi tiết

```
Client ──POST /api/v1/auth/login──▶ AuthenticationController.login()
                                           │
                              @Valid kiểm tra LoginRequest
                                           │
                              getClientIp(httpRequest)
                              // Ưu tiên: X-Forwarded-For → X-Real-IP → remoteAddr
                              // Xử lý cả trường hợp qua proxy (split ",")
                                           │
                              AuthService.authenticate(request, clientIp)
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    ▼                                             ▼
       loginAttemptService.isBlocked(ip, identifier)
       // Kiểm tra Redis: ip >= 10 lần HOẶC account >= 5 lần?
                    │
            Đang bị block → throw AppException(ACCOUNT_LOCKED) → 401
                    │ Chưa bị block ↓
       userRepository.findByEmailOrUsername(identifier, identifier)
       // Query: WHERE email = ? OR username = ?
                    │
            Tìm thấy ──▶ passwordEncoder.matches(raw, hash)    ← BCrypt compare
            Không tìm thấy ──▶ passwordEncoder.matches(raw, DUMMY_HASH)
                               // Phòng timing attack: luôn chạy BCrypt dù không tìm thấy user
                    │
            User null HOẶC password sai:
            loginAttemptService.loginFailed(ip, identifier)    ← tăng counter Redis
            throw AppException(UNAUTHENTICATED) → 401
                    │ User đúng & password đúng ↓
            user.getStatus() == LOCKED  → throw ACCOUNT_LOCKED → 401
            user.getStatus() == DISABLED → throw ACCOUNT_DISABLED → 401
                    │ Status ACTIVE ↓
            loginAttemptService.loginSucceeded(ip, identifier) ← reset counter Redis
                    │
            tokenService.generateAccessToken(user)
            refreshTokenService.generateRefreshToken(user.getId())
                    │
            Controller: buildCookie(refreshToken, 7 ngày)
            response.setRefreshToken(null)
                    │
            Trả về client:
            Header: Set-Cookie: refreshToken=...; HttpOnly; SameSite=Strict; Path=/api/v1/auth/refresh
            Body: { "accessToken": "eyJ..." }
```

### Kỹ thuật chống Timing Attack (quan trọng)
```java
static String DUMMY_HASH = "$2a$10$wK/p4z2u.89p4z2u.89p4ezwG.wK/p4z2u.89p4z2u.89p4ezwG.";

if (user != null) {
    isPasswordMatch = passwordEncoder.matches(request.getPassword(), user.getPassword());
} else {
    passwordEncoder.matches(request.getPassword(), DUMMY_HASH);  // Chạy BCrypt giả
}
```
**Tại sao cần điều này?** Nếu không tìm thấy user mà trả về lỗi ngay lập tức (không chạy BCrypt), attacker có thể đo thời gian phản hồi để biết email/username có tồn tại hay không. Giải pháp: **luôn chạy BCrypt dù không tìm thấy user**, response time sẽ tương đương nhau.

---

## 6. Cơ chế JWT — Access Token

### 6.1 Tạo Access Token (TokenService.generateAccessToken)
```java
JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
    .issuer("urbangreentracker.com")         // iss: ai phát hành token
    .subject(user.getId().toString())         // sub: định danh user (UUID)
    .issueTime(new Date())                    // iat: thời điểm tạo
    .expirationTime(Date.from(Instant.now()
        .plus(900, ChronoUnit.SECONDS)))      // exp: hết hạn sau 900s (15 phút)
    .jwtID(UUID.randomUUID().toString())      // jti: ID duy nhất của token (dùng để blacklist)
    .claim("token_type", "ACCESS")           // custom claim: phân biệt access/refresh
    .claim("scope", "ROLE_" + user.getRole()) // custom claim: role người dùng
    .build();

// Ký bằng thuật toán HS512 với SECRET_KEY
jwsObject.sign(new MACSigner(SECRET_KEY.getBytes(UTF_8)));
```

**Payload của một Access Token sau khi decode:**
```json
{
  "iss": "urbangreentracker.com",
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "iat": 1726789200,
  "exp": 1726790100,
  "jti": "a3f5c8d2-1234-...",
  "token_type": "ACCESS",
  "scope": "ROLE_USER"
}
```

### 6.2 Xác minh Access Token (TokenService.verifyAccessToken)

Có **7 bước kiểm tra tuần tự**, thiếu bất kỳ bước nào → throw `INVALID_TOKEN`:

```
Token string
    ↓ 1. requireReadableToken()   — không null, không blank
    ↓ 2. parseSignedJWT()         — parse string thành JWT object
    ↓ 3. verifyAlgorithm()        — đúng thuật toán HS512
    ↓ 4. verifySignature()        — chữ ký đúng với SECRET_KEY
    ↓ 5. verifyExpiration()       — chưa hết hạn (exp > now)
    ↓ 6. verifyIssuer()           — đúng issuer "urbangreentracker.com"
    ↓ 7. verifyAccessTokenType()  — token_type == "ACCESS"
    ↓ 8. verifyRequiredTokenIdentity() — có sub và jti
    ↓ 9. verifyTokenIsNotRevoked() — jti không có trong Redis blacklist
    ↓
  Return JWTClaimsSet (chứa userId, role)
```

**Blacklist Access Token khi logout:**
```java
public void blacklistAccessToken(String token) {
    SignedJWT signedJWT = SignedJWT.parse(token);
    String jti = signedJWT.getJWTClaimsSet().getJWTID();
    Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();

    long ttlSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;
    if (ttlSeconds > 0) {
        // Chỉ lưu đến khi token tự hết hạn → Redis tự xóa sau TTL
        redisTemplate.opsForValue().set(
            "auth:token:blacklist:access:" + jti,
            "revoked",
            ttlSeconds,
            TimeUnit.SECONDS
        );
    }
}
```
**Tại sao TTL = thời gian còn lại của token?** Không lưu vĩnh viễn token đã logout vào Redis — sau khi token tự hết hạn thì dù có trong blacklist hay không cũng không dùng được. Dùng TTL để tự động dọn dẹp, tránh Redis phình to.

---

## 7. Cơ chế Refresh Token (Redis)

### 7.1 Thiết kế: Không lưu plain token trong Redis

```java
public String generateRefreshToken(UUID userId) {
    byte[] randomBytes = new byte[32];
    new SecureRandom().nextBytes(randomBytes);
    String plainToken = Base64.getUrlEncoder().withoutPadding()
                              .encodeToString(randomBytes);  // Token gửi cho client
    String hashedToken = hashToken(plainToken);              // SHA-256 hash → lưu Redis
    ...
    return plainToken;   // Gửi ra ngoài: plain
}
```

**Tại sao không lưu plain token?** Nếu Redis bị tấn công/dump data, attacker chỉ lấy được hash SHA-256 — không thể reverse lại token gốc. Đây là nguyên tắc tương tự lưu password.

### 7.2 Cấu trúc dữ liệu trong Redis

```
Redis Key                                   Value           TTL
─────────────────────────────────────────────────────────────────
auth:refresh:valid:{SHA256(plainToken)}     userId (UUID)   7 ngày
auth:refresh:used:{SHA256(oldToken)}        userId (UUID)   7 ngày   ← sau khi rotate
auth:user:{userId}:sessions                 Set{hash1, hash2, ...}   7 ngày
auth:token:blacklist:access:{jti}           "revoked"       còn lại của access token
auth:login:ip:{ip}                          count           15 phút
auth:login:account:{identifier}             count           15 phút
```

### 7.3 Cơ chế Token Rotation + Reuse Detection

```java
public UUID validateAndRotate(String plainToken) {
    String hashedToken = hashToken(plainToken);
    String validKey = "auth:refresh:valid:" + hashedToken;
    String usedKey  = "auth:refresh:used:"  + hashedToken;

    // BƯỚC 1: Kiểm tra token đã dùng rồi chưa (reuse detection)
    String compromisedUserId = (String) redisTemplate.opsForValue().get(usedKey);
    if (compromisedUserId != null) {
        // Token này đã bị dùng 1 lần, đang được dùng lại
        // → KẾT LUẬN: token bị đánh cắp → thu hồi TOÀN BỘ session
        log.error("Refresh token reuse detected for user: {}", compromisedUserId);
        revokeAllUserSessions(UUID.fromString(compromisedUserId));
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }

    // BƯỚC 2: Kiểm tra token hợp lệ
    String userIdStr = (String) redisTemplate.opsForValue().get(validKey);
    if (userIdStr == null) {
        throw new AppException(ErrorCode.UNAUTHENTICATED);  // Token không tồn tại/hết hạn
    }

    // BƯỚC 3: Rotate — xóa token cũ, đánh dấu là "đã dùng"
    redisTemplate.delete(validKey);
    redisTemplate.opsForValue().set(usedKey, userIdStr, refreshTokenExpiration, SECONDS);
    redisTemplate.opsForSet().remove("auth:user:" + userId + ":sessions", hashedToken);

    return UUID.fromString(userIdStr);
    // AuthService sẽ gọi generateRefreshToken() để tạo token MỚI
}
```

**Vòng đời của Refresh Token:**
```
Login/Register
    │
    ▼
Tạo plainToken (random 32 bytes) → hash SHA-256 → lưu Redis
Client nhận plainToken qua HttpOnly Cookie
    │
    ▼ (sau 15 phút, Access Token hết hạn)
Client gọi POST /api/v1/auth/refresh
Cookie tự động gửi kèm refreshToken
    │
    ▼
Server: hash plainToken → tìm trong valid:
  ├─ Có trong "used" → TOKEN BỊ TÁI SỬ DỤNG → thu hồi tất cả session
  ├─ Không có trong "valid" → hết hạn hoặc đã revoke → lỗi
  └─ Có trong "valid" → hợp lệ:
       - Xóa khỏi "valid"
       - Đánh dấu vào "used"
       - Tạo TOKEN MỚI → gửi Cookie mới
    │
    ▼
Client có Access Token mới (15 phút) + Refresh Token mới trong Cookie
```

**Multi-device support:**
```java
// auth:user:{userId}:sessions = Set{hash1, hash2, hash3, ...}
// Mỗi device đăng nhập → thêm 1 hash vào Set
redisTemplate.opsForSet().add(userSessionsKey, hashedToken);

// logout-all: xóa hết
public void revokeAllUserSessions(UUID userId) {
    Set<Object> activeHashes = redisTemplate.opsForSet().members(userSessionsKey);
    for (Object hash : activeHashes) {
        redisTemplate.delete("auth:refresh:valid:" + hash);  // Thu hồi mọi session
    }
    redisTemplate.delete(userSessionsKey);
}
```

---

## 8. Bảo vệ Brute-Force (LoginAttemptService)

```java
private static final int MAX_ATTEMPTS_PER_ACCOUNT = 5;    // Sai 5 lần → khóa account
private static final int MAX_ATTEMPTS_PER_IP = 10;         // Sai 10 lần → khóa IP
private static final long LOCK_DURATION_MINUTES = 15;      // Khóa 15 phút
```

**Cơ chế hoạt động:**

```
Login attempt
    │
    ├─ Đọc Redis: auth:login:ip:{ip}         (counter IP)
    ├─ Đọc Redis: auth:login:account:{id}    (counter account)
    │
    ├─ ip >= 10 HOẶC account >= 5 → BLOCK → 401 ACCOUNT_LOCKED
    │
    └─ Chưa block → thử login
          ├─ Thất bại → increment cả 2 counter
          │              (Lần đầu tiên increment → set TTL = 15 phút)
          └─ Thành công → XÓA cả 2 counter (Redis delete)
```

**Điểm hay:** Đặt TTL ngay tại lần `increment` đầu tiên (`attempts == 1`):
```java
private void incrementAttempt(String key) {
    Long attempts = redisTemplate.opsForValue().increment(key);
    if (attempts != null && attempts == 1) {
        redisTemplate.expire(key, Duration.ofMinutes(15));
        // Chỉ set TTL một lần — lần increment tiếp theo không reset TTL
    }
}
```
Điều này đảm bảo: Nếu sai 4 lần trong 14 phút, sau 15 phút từ lần sai đầu tiên → tự mở khóa.

---

## 9. Luồng mỗi API Request có xác thực

```
Client ──GET /api/v1/users/me──▶
    Header: Authorization: Bearer eyJ...
                │
                ▼
    JwtAuthenticationFilter.doFilterInternal()
                │
    Đọc Authorization header
    Không có / không bắt đầu bằng "Bearer " → cho qua (SecurityConfig quyết định)
    Có "Bearer " → cắt lấy token (substring(7))
                │
                ▼
    tokenService.verifyAccessToken(token)
                │ Lỗi (hết hạn, sai chữ ký, blacklisted) → trả JSON lỗi luôn (không qua Handler)
                │ OK ↓
    claims.getSubject()        → userId (UUID string)
    claims.getStringClaim("scope") → "ROLE_USER" / "ROLE_MANAGER" / "ROLE_ADMIN"
                │
                ▼
    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        userId,    // principal — ai đang request (dùng để lấy ở Controller)
        null,      // credentials — không cần
        [SimpleGrantedAuthority("ROLE_USER")]   // authorities — role
    );
    SecurityContextHolder.getContext().setAuthentication(auth);
                │
                ▼
    filterChain.doFilter()  → tiếp tục đến SecurityConfig → Controller
                │
    Ở Controller: SecurityContextHolder.getContext().getAuthentication().getName()
                  → lấy được userId
```

**Tại sao không query DB trong filter?** Filter này chạy với **mọi request** — nếu query DB mỗi lần sẽ rất chậm. JWT là **self-contained**: mọi thông tin cần thiết (userId, role) đã nằm trong token, server chỉ cần verify chữ ký là đủ tin tưởng.

---

## 10. Luồng LOGOUT & LOGOUT ALL DEVICES

### Logout (thiết bị hiện tại)
```java
// Client gửi:
// Header: Authorization: Bearer {accessToken}
// Cookie: refreshToken={refreshToken}  (tự động theo cookie path)

public void logout(String accessToken, String refreshToken) {
    // 1. Blacklist access token (còn hạn nhưng đánh dấu revoked)
    if (accessToken != null && accessToken.startsWith("Bearer ")) {
        tokenService.blacklistAccessToken(accessToken.substring(7));
    }
    // 2. Xóa refresh token khỏi Redis
    if (refreshToken != null && !refreshToken.isBlank()) {
        refreshTokenService.revokeRefreshToken(refreshToken);
    }
}
```
Controller sau đó set cookie rỗng MaxAge=0 để browser xóa cookie:
```java
ResponseCookie deleteCookie = buildCookie("", 0);  // maxAge = 0 → browser xóa
```

### Logout All Devices
```java
public void logoutAllDevices(String accessToken, UUID currentUserId) {
    // 1. Blacklist access token thiết bị hiện tại
    tokenService.blacklistAccessToken(accessToken.substring(7));
    // 2. Thu hồi TẤT CẢ refresh token của user (mọi thiết bị)
    refreshTokenService.revokeAllUserSessions(currentUserId);
}
```
Lấy `currentUserId` từ SecurityContext (không cần truyền từ client):
```java
String userIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
UUID currentUserId = UUID.fromString(userIdStr);
```

---

## 11. Xử lý lỗi toàn cục (Exception Handling)

### ErrorCode enum — Mã lỗi chuẩn
```java
public enum ErrorCode {
    // System
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", 500),

    // 400
    INVALID_REQUEST(1001, "Invalid request data", 400),

    // 401
    UNAUTHENTICATED(1003, "Username or password is incorrect", 401),
    ACCOUNT_LOCKED(1004, "Account or IP is locked...", 401),
    ACCOUNT_DISABLED(1005, "Your account has been disabled", 401),
    INVALID_TOKEN(1009, "Invalid or expired token", 401),
    INVALID_REFRESH_TOKEN(1011, "Refresh token is invalid or expired", 401),

    // 403
    UNAUTHORIZED_ACCESS(1006, "You do not have permission...", 403),

    // 404
    USER_NOT_EXISTED(1007, "User does not exist", 404),

    // 409
    EMAIL_EXISTED(1012, "Email already in use", 409),
    USERNAME_EXISTED(1013, "Username already in use", 409),

    // 500
    TOKEN_GENERATION_FAILED(1010, "Cannot generate token", 500),
}
```

### GlobalExceptionHandler — 5 handler theo thứ tự ưu tiên
```
1. Exception.class          → UNCATEGORIZED (9999, 500) — fallback cuối cùng
2. AppException.class       → Lấy ErrorCode từ exception, trả đúng HTTP status
3. AuthenticationException  → UNAUTHENTICATED (1003, 401)
4. AccessDeniedException    → UNAUTHORIZED_ACCESS (1006, 403)
5. MethodArgumentNotValidException → INVALID_REQUEST (1001, 400) + message từ @constraint
```

**Response format thống nhất (ApiResponse):**
```json
{
    "code": 1003,
    "message": "Username or password is incorrect"
}
```
Hoặc khi thành công (hiện chưa wrap, sẽ thêm sau):
```json
{
    "code": 200,
    "message": "Success",
    "data": { "accessToken": "eyJ..." }
}
```

---

## 12. Bảo mật Cookie — Tại sao Refresh Token lại để trong Cookie?

```java
private ResponseCookie buildCookie(String token, long maxAgeInSeconds) {
    return ResponseCookie.from("refreshToken", token)
        .httpOnly(true)                        // ① JS không đọc được (chống XSS)
        .secure(false)                         // ② false = HTTP cho localhost
                                               //    PHẢI true ở production (HTTPS)
        .path("/api/v1/auth/refresh")          // ③ Chỉ gửi kèm khi request đến /refresh
                                               //    Các API khác KHÔNG có cookie này
        .maxAge(maxAgeInSeconds)               // ④ 7 ngày hoặc 0 (để xóa)
        .sameSite("Strict")                    // ⑤ Chỉ gửi từ cùng origin (chống CSRF)
        .build();
}
```

**Tại sao không để Refresh Token trong localStorage?**
- localStorage dễ bị đọc bởi XSS (Cross-Site Scripting).
- HttpOnly Cookie: JavaScript **hoàn toàn không đọc được** giá trị cookie này.
- Kết hợp `SameSite=Strict`: Ngăn trình duyệt gửi cookie tới site khác → chống CSRF.
- `path=/api/v1/auth/refresh`: Cookie chỉ tự động đính kèm khi call đến đúng endpoint đó.

**Tại sao Access Token lại không để trong Cookie?**
- Access Token được gửi với mọi API request qua `Authorization: Bearer` header.
- Nếu dùng cookie thì phải dùng `path=/` → gửi kèm mọi request → attack surface rộng hơn.
- Access Token ngắn hạn (15 phút) → rủi ro thấp hơn.

---

## 13. Sơ đồ dữ liệu trong Redis

```
Redis (port 15673) — Toàn bộ key patterns trong auth:

LOẠI 1: Brute-force protection
  auth:login:ip:{ipAddress}                    → String (count)   TTL: 15 phút
  auth:login:account:{email_hoặc_username}     → String (count)   TTL: 15 phút

LOẠI 2: Refresh Token storage (per session)
  auth:refresh:valid:{sha256Hash}              → String (userId)  TTL: 7 ngày
  auth:refresh:used:{sha256Hash}               → String (userId)  TTL: 7 ngày

LOẠI 3: Session tracking (per user, all devices)
  auth:user:{userId}:sessions                  → Set{hash1, hash2, ...}  TTL: 7 ngày

LOẠI 4: Access Token blacklist
  auth:token:blacklist:access:{jti}            → String ("revoked")  TTL: thời gian còn lại của token
```

---

## 14. Cấu hình môi trường liên quan

```yaml
# application.yaml
jwt:
  secret: ${JWT_SECRET_KEY}           # Từ biến môi trường .env
  access-token-expiration: 900        # 15 phút (seconds)
  # refresh-token-expiration: 604800  # 7 ngày (default trong RefreshTokenService)

spring:
  data:
    redis:
      host: localhost
      port: 15673                     # Mapped port từ Docker (container nội bộ là 6379)
```

**Điểm chú ý:** `refresh-token-expiration` chưa khai báo trong `application.yaml` — hiện đang dùng giá trị default `604800` trong `@Value("${jwt.refresh-token-expiration:604800}")`. Nên thêm vào yaml để dễ quản lý.

---

## Tóm tắt bảo mật

| Tấn công | Cơ chế phòng chống |
|---------|-------------------|
| Brute-force login | Redis counter: 5 lần/account, 10 lần/IP, khóa 15 phút |
| Timing attack | Luôn chạy BCrypt dù user không tồn tại (DUMMY_HASH) |
| Token giả mạo | HS512 signature verify + issuer check |
| Token replay sau logout | Access Token blacklist trong Redis (TTL tự dọn) |
| Refresh Token đánh cắp | Rotate + Reuse detection → thu hồi toàn bộ session |
| XSS đánh cắp token | Refresh Token: HttpOnly Cookie (JS không đọc được) |
| CSRF | SameSite=Strict trên Cookie |
| Redis bị dump | Lưu SHA-256 hash thay vì plain refresh token |

---

*File này dùng để theo dõi nội bộ và đã được thêm vào .gitignore.*
