# 🌿 Urban Green Tracker — Tài Liệu Dự Án

> **Cập nhật lần cuối:** 2026-09-20
> **Trạng thái tổng thể:** 🟡 **Backend đang phát triển – Giai đoạn Alpha (Auth Phase hoàn tất)**

---

## 1. Tổng Quan Dự Án

**Urban Green Tracker** là một nền tảng web cho phép người dân báo cáo, theo dõi và tương tác với các vấn đề môi trường đô thị (cây xanh bị chặt phá, rác thải, ô nhiễm, v.v.). Dự án hướng đến ba nhóm người dùng chính:

| Vai trò | Mô tả |
|---------|-------|
| `USER` | Người dân – đăng bài, bình luận, vote |
| `MANAGER` | Cán bộ chính quyền – duyệt, xử lý, cập nhật trạng thái bài đăng |
| `ADMIN` | Quản trị hệ thống – toàn quyền |

---

## 2. Kiến Trúc Hệ Thống

Tech Stack:

| Thành phần | Công nghệ |
|-----------|-----------|
| Backend Framework | Spring Boot 4.1.0 |
| Ngôn ngữ | Java 25 |
| Database | PostgreSQL + PostGIS (spatial data) |
| Cache & Session | Redis (Lettuce connection pool) |
| ORM | Spring Data JPA + Hibernate Spatial |
| Bảo mật | Spring Security, JWT (Nimbus JOSE) |
| Email | Spring Boot Mail (Gmail SMTP) |
| Google OAuth | google-api-client 2.2.0 |
| Validation | Spring Boot Validation |
| Boilerplate | Lombok |
| Container | Docker Compose (PostgreSQL + Redis) |
| Frontend | **Chưa có** |

---

## 3. Database Schema (Đã Thiết Kế Qua Entity)

| Bảng | Entity | Mô tả |
|------|--------|-------|
| `users` | `User` | Tài khoản người dùng, điểm thưởng, trạng thái |
| `posts` | `Post` | Bài báo cáo sự cố, tọa độ GPS (PostGIS Point) |
| `post_status_history` | `PostStatusHistory` | Lịch sử thay đổi trạng thái bài đăng |
| `post_images` | `PostImage` | Ảnh đính kèm bài đăng |
| `resolution_images` | `ResolutionImage` | Ảnh xử lý do MANAGER/ADMIN upload |
| `comments` | `Comment` | Bình luận, có thể đính kèm media |
| `comment_reactions` | `CommentReaction` | LIKE/DISLIKE bình luận |
| `votes` | `Vote` | Bình chọn bài đăng (1 user/1 bài) |
| `notifications` | `Notification` | Thông báo hệ thống |
| `categories` | `Category` | Danh mục loại sự cố |

Điểm đáng chú ý:
- Post.location dùng JTS Point + geometry(Point, 4326) -> hỗ trợ PostGIS
- User có totalPoints và monthlyPoints -> leaderboard
- Vote có UniqueConstraint(user_id, post_id) -> mỗi user chỉ vote 1 lần/bài
- Post.type và Post.status dùng String thay vì Enum -> nên cân nhắc refactor

---

## 4. Tiến Độ Chi Tiết

### ✅ Module AUTH (~90% hoàn chỉnh)

| Chức năng | Trạng thái | Ghi chú |
|-----------|-----------|---------|
| Login (email/username + password) | ✅ Hoàn thành | Có brute-force protection |
| JWT Access Token (HS512, 15 phút) | ✅ Hoàn thành | Nimbus JOSE, có JTI |
| Refresh Token (rotate + reuse detection) | ✅ Hoàn thành | Lưu hash SHA-256 trong Redis |
| Logout (blacklist access token) | ✅ Hoàn thành | Redis TTL theo expiry token |
| Logout All Devices | ✅ Hoàn thành | Xóa toàn bộ sessions trong Redis |
| Brute-force protection | ✅ Hoàn thành | 5 lần/account, 10 lần/IP, khóa 15 phút |
| JWT Filter (OncePerRequestFilter) | ✅ Hoàn thành | Tích hợp SecurityContextHolder |
| Security Config (Stateless) | ✅ Hoàn thành | Stateless, phân quyền theo role |
| Register | ⚠️ Thiếu | DTO có nhưng chưa có service/logic |
| Google OAuth Login | ⚠️ Thiếu | Route khai báo nhưng chưa implement |
| Forgot/Reset Password | ⚠️ Thiếu | Route khai báo nhưng chưa implement |
| Bug: mock refresh token | ❌ Bug | AuthService.java:77 trả về string giả |

### ✅ Module USER (~30% hoàn chỉnh)

| Chức năng | Trạng thái | Ghi chú |
|-----------|-----------|---------|
| Entity User | ✅ Hoàn thành | Đầy đủ fields, Lombok |
| UserRepository | ✅ Hoàn thành | Có query điểm thưởng, leaderboard |
| GET /api/v1/users/me | ⚠️ Cơ bản | Trả về string thô, chưa có DTO/service |
| Manager Dashboard, Delete User | ⚠️ Placeholder | Trả về string cứng |
| User Service (CRUD đầy đủ) | ❌ Chưa có | |
| Profile update, Leaderboard | ❌ Chưa có | |

### 🟡 Module POST (Entity xong, API chưa có)

| Chức năng | Trạng thái |
|-----------|-----------|
| Entity Post (với GPS location) | ✅ Hoàn thành |
| Entity PostStatusHistory | ✅ Hoàn thành |
| Repository / Service / Controller | ❌ Chưa có |
| API CRUD bài đăng | ❌ Chưa có |
| API lọc bài đăng (status, category, GIS) | ❌ Chưa có |
| API duyệt bài (MANAGER/ADMIN) | ❌ Chưa có |

### 🟡 Module COMMENT (Entity xong, API chưa có)

| Chức năng | Trạng thái |
|-----------|-----------|
| Entity Comment (hỗ trợ media) | ✅ Hoàn thành |
| Entity CommentReaction (LIKE/DISLIKE) | ✅ Hoàn thành |
| Repository / Service / Controller | ❌ Chưa có |

### 🟡 Các Entity đã xong nhưng chưa có API

| Module | Entity | API |
|--------|--------|-----|
| Vote | ✅ Xong | ❌ Chưa có |
| PostImage / ResolutionImage | ✅ Xong | ❌ Chưa có |
| Notification | ✅ Xong | ❌ Chưa có |
| Category | ✅ Xong | ❌ Chưa có |

### ❌ Các Module Trống Hoàn Toàn

| Module | Trạng thái |
|--------|-----------|
| GIS (spatial queries, heatmap) | ❌ Directory rỗng |
| Dashboard (thống kê) | ❌ Directory rỗng |
| Scheduler (cron jobs) | ❌ Directory rỗng |
| Common (utilities) | ❌ Directory rỗng |
| Frontend | ❌ Thư mục frontend/ hoàn toàn trống |

### ✅ Infrastructure & Exception Handling

| Thành phần | Trạng thái | Ghi chú |
|-----------|-----------|---------|
| Docker Compose (PostgreSQL + Redis) | ✅ Hoàn thành | Redis port: 15673 |
| GlobalExceptionHandler | ✅ Hoàn thành | |
| AppException + ErrorCode | ✅ Hoàn thành | |
| ApiResponse wrapper | ✅ Hoàn thành | |
| .env (gitignored) | ✅ Có | ⚠️ Chứa secrets thật – cần rotate |
| Schema Migration (Flyway/Liquibase) | ❌ Chưa có | |
| Unit Tests | ❌ Chưa có | |
| Swagger/OpenAPI docs | ❌ Chưa có | |

---

## 5. Các Lỗi / Vấn Đề Cần Sửa Ngay

| # | Vấn đề | Vị trí | Mức độ |
|---|--------|--------|--------|
| 1 | authenticate() trả về refresh token giả "mock-refresh-token-waiting-for-phase-13" | AuthService.java:77 | 🔴 Cao |
| 2 | Cookie path /api/v1/auth/refresh không khớp với controller mapping /api/auth/refresh | AuthenticationController.java:98 | 🔴 Cao |
| 3 | RegisterRequest.java tồn tại nhưng không có service/logic đăng ký | auth/dto/ | 🔴 Cao |
| 4 | .env chứa credentials thật có thể bị lộ – cần rotate ngay | .env | 🔴 Bảo mật |
| 5 | Post.type và Post.status dùng String thay vì Enum | Post.java | 🟡 Trung bình |
| 6 | UserController.getMyProfile() trả về string thô thay vì DTO | UserController.java | 🟡 Trung bình |

---

## 6. Bước Tiếp Theo (Ưu Tiên Cao → Thấp)

### 🔥 Ưu Tiên 1 – Sửa lỗi hiện có (làm ngay)
1. Fix refresh token bug trong AuthService.authenticate() -> dùng refreshToken thật
2. Fix cookie path trong AuthenticationController.buildCookie()
3. Implement Register -> AuthService.register() + validate unique email/username
4. Rotate secrets trong .env (email password + JWT secret key đang bị lộ)

### 🚀 Ưu Tiên 2 – Hoàn thiện User Module
5. Tạo UserService với: getProfile(), updateProfile(), changePassword()
6. Trả về UserProfileDTO thay vì string thô ở GET /api/v1/users/me
7. Implement API Leaderboard (query đã có sẵn trong UserRepository)
8. Quyết định & implement image storage (local / S3 / Cloudinary) cho avatar

### 📦 Ưu Tiên 3 – Post Module (core của ứng dụng)
9. Tạo PostRepository, PostService, PostController
10. Implement CRUD bài đăng (tạo, sửa, xóa, xem danh sách, xem chi tiết)
11. Upload ảnh đính kèm bài đăng
12. API lọc theo status, category, khu vực địa lý
13. API thay đổi trạng thái (MANAGER/ADMIN) + ghi PostStatusHistory
14. Refactor Post.type và Post.status sang Enum

### 📬 Ưu Tiên 4 – Hoàn thiện Auth
15. Implement Forgot Password / Reset Password (gửi email OTP/link)
16. Implement Google OAuth Login

### 💬 Ưu Tiên 5 – Comment, Vote, Notification
17. CommentService + CommentController (CRUD, reaction LIKE/DISLIKE)
18. VoteService + VoteController
19. NotificationService -> tự tạo notification khi bài được duyệt/phản hồi
20. (Tùy chọn) WebSocket / SSE cho real-time notification

### 🗺️ Ưu Tiên 6 – GIS & Dashboard
21. Spatial query: tìm bài đăng trong bán kính X km bằng PostGIS
22. Heatmap data endpoint
23. Dashboard thống kê tổng hợp

### ⚙️ Ưu Tiên 7 – Infrastructure & Quality
24. Thêm Flyway/Liquibase để quản lý schema migration
25. Thêm Swagger/OpenAPI documentation
26. Viết Unit Tests cho AuthService, TokenService
27. Scheduler: reset monthlyPoints đầu tháng, auto-close bài quá hạn
28. CORS config cho frontend domain

### 🖥️ Ưu Tiên 8 – Frontend (bắt đầu khi backend ổn định)
29. Khởi tạo dự án frontend (React + Vite hoặc Next.js)
30. Giao diện đăng nhập / đăng ký
31. Bản đồ tương tác (Google Maps API hoặc Leaflet + OpenStreetMap)
32. Feed bài đăng, trang chi tiết sự cố
33. Dashboard quản lý cho MANAGER/ADMIN

---

## 7. Cấu Hình Môi Trường

### Docker Services
| Service | Image | Port | Volume |
|---------|-------|------|--------|
| PostgreSQL + PostGIS | postgis/postgis | 5432:5432 | db_data |
| Redis | redis:alpine | 15673:6379 | redis_data |

### Chạy Backend Locally
```bash
cd backend/urban-green-tracker
docker-compose up -d
./mvnw spring-boot:run
```

Backend chạy tại: http://localhost:8080

### Quy Ước Package

```
com.urbangreen.urban_green_tracker.{module}/
├── controller/     REST Controllers
├── service/        Business Logic
├── repository/     Spring Data JPA
├── entity/         JPA Entities
│   └── enums/      Enum types
└── dto/            Data Transfer Objects
```

---

*File này dùng để theo dõi nội bộ và đã được thêm vào .gitignore.*
