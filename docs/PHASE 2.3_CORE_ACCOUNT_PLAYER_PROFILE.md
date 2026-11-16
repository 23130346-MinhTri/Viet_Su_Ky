# TASK: IMPLEMENT PHASE 2.3 – CORE ACCOUNT & PLAYER PROFILE
## Project: VIỆT SỬ KÝ

Bạn đang làm việc trực tiếp trong repository VIỆT SỬ KÝ.

**Project root:** `D:\Games\History`

Mục tiêu: Phân tích codebase hiện tại và triển khai hoàn chỉnh Phase 2.3 – Core Account & Player Profile, bao gồm Database, Backend Authentication, JWT Security, Player Profile API, Frontend và Integration Testing.

Đây là yêu cầu IMPLEMENT trực tiếp vào source code, không chỉ đưa ra hướng dẫn hoặc ví dụ.

---

# 1. PROJECT CONTEXT

Dự án VIỆT SỬ KÝ là một web game giáo dục lịch sử Việt Nam.

Tech stack hiện tại:

### Frontend
- React
- TypeScript
- Vite
- Phaser 3
- SCSS

### Backend
- Java 17
- Spring Boot 3.x
- Spring Security
- Spring Data JPA
- Hibernate
- Maven
- Flyway

### Database
- PostgreSQL 16
- Schema: `app`

### Infrastructure
- Docker Compose
- Docker
- Nginx Reverse Proxy

Kiến trúc:
- Modular Monolith.
- React quản lý UI và routing.
- Phaser quản lý game rendering và gameplay.
- Spring Boot quản lý business logic và dữ liệu.
- PostgreSQL lưu dữ liệu.
- Backend là nguồn dữ liệu đáng tin cậy đối với tài khoản và tiến trình người chơi.

Các địa chỉ đang sử dụng:
- Frontend: `http://localhost:8081`
- Backend trực tiếp: `http://localhost:8082`
- API thông qua Nginx: `http://localhost:8081/api`
- PostgreSQL host port: `5433`

Các kiểm tra Phase 2.2 đã PASS:
- Docker Compose.
- PostgreSQL connection.
- Spring Boot startup.
- Flyway V1.
- Frontend HTTP 200.
- Backend Actuator HTTP 200.
- Nginx API reverse proxy HTTP 200.

Database hiện có schema `app` và bảng `app.flyway_schema_history`.

Migration V1 đã chạy thành công:
`V1__create_app_schema.sql`

Không được xóa dữ liệu hoặc phá vỡ cấu hình hiện tại.

---

# 2. QUY TẮC BẮT BUỘC TRƯỚC KHI CODE

Trước tiên, hãy đọc và phân tích:

- README.md
- PROJECT_CONTEXT.md
- ARCHITECTURE.md
- TECHNICAL_DESIGN.md
- SRS.md
- backend/pom.xml
- backend/src/main/resources/application.yml
- backend/src/main/resources/application-dev.yml
- backend/src/main/resources/application-prod.yml
- backend/src/main/java/**
- frontend/package.json
- frontend/src/**
- docker-compose.yml
- Dockerfile của Backend và Frontend
- Cấu hình Nginx hiện tại.

Nếu một file không tồn tại, ghi nhận và tiếp tục.

Không tự giả định cấu trúc package, tên module, dependencies hoặc API đang tồn tại.

Không tạo các class trùng với class hiện có.

Không ghi đè code hiện có nếu chưa hiểu mục đích của nó.

Trước khi triển khai, tạo một bản kế hoạch ngắn gồm:
1. Cấu trúc hiện tại.
2. Những thành phần đã có.
3. Những thành phần còn thiếu.
4. Danh sách file cần tạo hoặc sửa.
5. Dependencies cần bổ sung.
6. Các rủi ro tương thích.

Sau đó tiếp tục triển khai, không dừng lại chỉ để trình bày kế hoạch.

Nếu phát hiện yêu cầu xung đột với kiến trúc hiện tại, ưu tiên kiến trúc hiện tại và ghi rõ lý do.

---

# 3. DATABASE DESIGN

Triển khai Database cho Account và Player Profile.

Sử dụng schema `app`.

Tạo Flyway migration tiếp theo, ví dụ:

`V2__create_account_and_player_profile.sql`

Trước khi đặt version, kiểm tra toàn bộ migration hiện có để tránh trùng version.

## 3.1. accounts

Các trường đề xuất:

- id: UUID, Primary Key.
- email: VARCHAR(255), NOT NULL, UNIQUE.
- password_hash: VARCHAR(255), NOT NULL.
- status: VARCHAR(30), NOT NULL.
- created_at: TIMESTAMP.
- updated_at: TIMESTAMP.

Status tối thiểu:
- ACTIVE
- LOCKED
- DISABLED

Email cần được chuẩn hóa trước khi lưu:
- Trim khoảng trắng.
- Chuyển về lowercase.
- Kiểm tra định dạng.
- Unique constraint ở Database.

Không lưu password dạng plaintext.

## 3.2. player_profiles

Các trường đề xuất:

- id: UUID, Primary Key.
- account_id: UUID, NOT NULL, UNIQUE.
- display_name: VARCHAR(50), NOT NULL.
- avatar_url: TEXT, NULL.
- created_at: TIMESTAMP.
- updated_at: TIMESTAMP.

Relationship:
- Account 1 – 1 PlayerProfile.
- Foreign key đến accounts.id.
- Không cho phép một account có nhiều player profile.

Thiết lập index phù hợp.

Dùng PostgreSQL UUID và các constraint phù hợp.

Không dùng Hibernate ddl-auto update để tạo bảng.

Giữ nguyên:
`spring.jpa.hibernate.ddl-auto: validate`

Migration phải tương thích với cấu hình Flyway hiện tại.

---

# 4. BACKEND ARCHITECTURE

Triển khai theo kiến trúc modular, phân tách trách nhiệm rõ ràng.

Có thể tổ chức theo cấu trúc:

com.[existing-package]
├── account
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
├── auth
│   ├── controller
│   ├── service
│   ├── security
│   └── dto
├── player
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
└── common
    ├── exception
    ├── response
    └── config

Đây là cấu trúc tham khảo. Hãy điều chỉnh theo package thực tế của dự án.

## 4.1. Account module

Triển khai:
- Account Entity.
- Account Repository.
- Account Service.
- Email normalization.
- Kiểm tra email trùng.
- Quản lý trạng thái tài khoản.

Không trả password_hash trong bất kỳ API response nào.

## 4.2. Authentication module

Sử dụng:
- Spring Security.
- BCrypt PasswordEncoder.
- JWT.
- Stateless authentication.

JWT phải có:
- Subject: account UUID.
- Issued At.
- Expiration.
- Signing algorithm phù hợp.

Không hardcode JWT secret.

JWT secret phải lấy từ environment variable.

Ví dụ:
`JWT_SECRET`

Nếu secret chưa được cấu hình, phải xử lý theo profile và fail-fast khi ứng dụng cần khởi chạy môi trường production.

Không sử dụng secret mặc định yếu trong production.

Access token có thời hạn hợp lý, mặc định khoảng 15–30 phút và có thể cấu hình bằng environment variable.

Chưa cần triển khai OAuth2 hoặc đăng nhập Google trong Phase 2.3.

Không đưa password hoặc token vào log.

## 4.3. Player module

Triển khai:
- PlayerProfile Entity.
- PlayerProfile Repository.
- PlayerProfile Service.
- PlayerProfile Controller.

Tạo PlayerProfile tự động sau khi đăng ký Account thành công.

Không để trạng thái đăng ký nửa chừng nếu tạo profile thất bại.

Sử dụng transaction phù hợp.

Người chơi chỉ được đọc hoặc cập nhật profile của chính mình.

Không nhận accountId từ Client để quyết định quyền sở hữu.

Lấy accountId từ JWT đã xác thực.

---

# 5. API CONTRACT

Prefix API cần thống nhất với cấu hình hiện tại của dự án.

Dự kiến:

`/api/v1/auth`

`/api/v1/players`

Nếu Nginx đang strip `/api`, cần giữ nguyên cách định tuyến đã được xác minh ở Phase 2.2.

## 5.1. Register

POST `/api/v1/auth/register`

Request:

{
  "email": "player@example.com",
  "password": "StrongPassword123",
  "displayName": "PlayerOne"
}

Response dự kiến:

{
  "success": true,
  "message": "Registration successful",
  "data": {
    "accountId": "uuid",
    "email": "player@example.com",
    "displayName": "PlayerOne"
  }
}

Yêu cầu:
- Validate email.
- Password tối thiểu 8 ký tự.
- Validate displayName.
- Email không được trùng.
- Password phải được BCrypt hash.
- Tạo Account và PlayerProfile trong cùng transaction.

Không trả password hoặc password_hash.

## 5.2. Login

POST `/api/v1/auth/login`

Request:

{
  "email": "player@example.com",
  "password": "StrongPassword123"
}

Response:

{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "account": {
      "id": "uuid",
      "email": "player@example.com",
      "displayName": "PlayerOne"
    }
  }
}

Yêu cầu:
- Kiểm tra password.
- Kiểm tra account status.
- Từ chối tài khoản LOCKED hoặc DISABLED.
- Không tiết lộ email có tồn tại hay không thông qua thông báo lỗi đăng nhập.
- Không log credentials.

## 5.3. Get current player

GET `/api/v1/players/me`

Header:

Authorization: Bearer <accessToken>

Trả về profile của người đang đăng nhập.

## 5.4. Update current player

PUT `/api/v1/players/me`

Request:

{
  "displayName": "NewDisplayName",
  "avatarUrl": "https://example.com/avatar.png"
}

Yêu cầu:
- JWT hợp lệ.
- Chỉ cập nhật profile hiện tại.
- Validate dữ liệu.
- Không cho phép thay đổi accountId.
- Không cho phép thay đổi email hoặc status qua API này.

## 5.5. Get current account

GET `/api/v1/auth/me`

Yêu cầu:
- Chỉ cho phép người đã đăng nhập.
- Trả thông tin tài khoản hiện tại.
- Không trả password hash.

## 5.6. Error handling

Chuẩn hóa response lỗi.

Các trường hợp:
- 400: Validation error.
- 401: Unauthorized.
- 403: Forbidden.
- 404: Resource not found.
- 409: Email already exists.
- 500: Internal Server Error.

Không trả stack trace hoặc thông tin nhạy cảm cho Client.

---

# 6. SECURITY CONFIGURATION

Triển khai SecurityFilterChain.

Yêu cầu:
- Stateless session.
- JWT authentication filter.
- BCrypt.
- Disable form login nếu không sử dụng.
- Disable HTTP Basic nếu không sử dụng.
- CORS lấy từ cấu hình environment hiện có.
- Không mở toàn bộ API cho anonymous access.

Public endpoints:
- POST /api/v1/auth/register
- POST /api/v1/auth/login
- Actuator health endpoints theo cấu hình hiện tại.

Protected endpoints:
- GET /api/v1/auth/me
- GET /api/v1/players/me
- PUT /api/v1/players/me

Chú ý mapping thực tế sau khi Nginx strip `/api`.

Không vô hiệu hóa Security để giải quyết lỗi test.

Không cho phép JWT hết hạn vẫn truy cập được.

---

# 7. FRONTEND IMPLEMENTATION

Trước tiên phân tích frontend hiện có và giữ nguyên UI conventions, SCSS variables, component structure và routing.

Triển khai:

## 7.1. Authentication

- Register Page.
- Login Page.
- Form validation.
- Loading state.
- Error state.
- API service.
- Authentication state management.

## 7.2. Token management

Sử dụng cơ chế lưu token phù hợp với kiến trúc bảo mật.

Ưu tiên thiết kế có thể chuyển sang HttpOnly Secure Cookie khi triển khai production.

Nếu MVP sử dụng Bearer token ở Client:
- Không lưu password.
- Không ghi token ra console.
- Không đưa token vào URL.
- Xử lý token hết hạn.
- Xóa token khi logout.

Không giả định JWT tự động được bảo vệ chỉ vì sử dụng HTTPS.

## 7.3. Player Profile

Triển khai:
- Profile Page.
- Display name.
- Avatar URL.
- Update profile.
- Loading và error states.

Tạo API client tập trung.

Không gọi API trực tiếp rải rác trong UI components.

Không làm hỏng Phaser game initialization hoặc routing hiện tại.

Nếu frontend chưa có routing/auth architecture, đề xuất cách tích hợp tối thiểu và nhất quán.

---

# 8. TESTING

Backend phải có:

## Unit tests
- Register success.
- Duplicate email.
- Password hashing.
- Invalid email.
- Invalid password.
- Login success.
- Login failure.
- Disabled account.
- Update own profile.

## Security tests
- API không token trả 401.
- Token không hợp lệ trả 401.
- Token hết hạn trả 401.
- Protected API không truy cập anonymous.
- User không thể cập nhật profile của account khác.

## Integration tests
- Register → Database → PlayerProfile.
- Login → JWT.
- JWT → Get current profile.
- JWT → Update profile.

Test database nên dùng PostgreSQL Testcontainers nếu phù hợp với dependencies và môi trường hiện có. Nếu không, thiết kế integration test theo cấu hình PostgreSQL riêng, không làm ảnh hưởng database đang chạy.

Frontend:
- TypeScript typecheck.
- Lint.
- Build.
- Unit/component tests nếu dự án đã có framework phù hợp.

Không sửa test để biến lỗi thành PASS.

Không bỏ qua lỗi build.

---

# 9. DOCKER & ENVIRONMENT

Kiểm tra và cập nhật:
- docker-compose.yml.
- Backend Dockerfile.
- Frontend Dockerfile nếu cần.
- Nginx config nếu cần.
- Environment variable documentation.

Các biến cần xem xét:

DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
JWT_SECRET
JWT_EXPIRATION
FRONTEND_ORIGINS

Không ghi secret thực vào Git.

Cập nhật `.env.example` nếu có.

Không in nội dung `.env` ra terminal hoặc tài liệu.

Không thực hiện:
- docker compose down -v
- Xóa PostgreSQL volume.
- Reset Database.
- Xóa Flyway history.
- Chạy SQL destructive.
- Thay đổi dữ liệu hiện có không có lý do.

Đảm bảo các service vẫn sử dụng đúng network và service name hiện tại.

---

# 10. DOCUMENTATION

Cập nhật hoặc tạo:

- docs/phase-2.3/PHASE_2_3_IMPLEMENTATION.md
- docs/phase-2.3/DATABASE_DESIGN.md
- docs/phase-2.3/API_DOCUMENTATION.md
- docs/phase-2.3/SECURITY_DESIGN.md
- docs/phase-2.3/TEST_REPORT.md

Tài liệu phải phản ánh implementation thực tế, không ghi những chức năng chưa triển khai là đã hoàn thành.

Cập nhật README nếu cần.

---

# 11. EXECUTION STRATEGY

Thực hiện theo thứ tự:

1. Repository audit.
2. Architecture audit.
3. Database migration.
4. Entity và Repository.
5. Account Service.
6. Authentication + JWT.
7. Player Profile Service và API.
8. Security configuration.
9. Backend tests.
10. Frontend authentication.
11. Frontend profile.
12. Docker integration.
13. End-to-end verification.
14. Documentation.

Sau mỗi nhóm:
- Kiểm tra compile.
- Chạy test liên quan.
- Kiểm tra lỗi.
- Sửa lỗi trước khi đi tiếp.

Không chỉ tạo file mà phải đảm bảo chúng được tích hợp và sử dụng thực sự.

Không tạo code placeholder, TODO giả hoặc endpoint trả dữ liệu hardcode để báo hoàn thành.

---

# 12. ACCEPTANCE CRITERIA

Phase 2.3 chỉ được đánh dấu COMPLETE khi:

[ ] Flyway migration chạy thành công.

[ ] Account và PlayerProfile được tạo đúng.

[ ] Email unique.

[ ] Password được BCrypt hash.

[ ] Register API hoạt động.

[ ] Login API hoạt động.

[ ] JWT được cấp và xác thực đúng.

[ ] JWT hết hạn bị từ chối.

[ ] API protected yêu cầu authentication.

[ ] GET current profile hoạt động.

[ ] UPDATE current profile hoạt động.

[ ] Không thể truy cập hoặc cập nhật profile người khác.

[ ] Frontend Register hoạt động.

[ ] Frontend Login hoạt động.

[ ] Frontend Profile hoạt động.

[ ] Docker Compose hoạt động.

[ ] Nginx API Reverse Proxy hoạt động.

[ ] Backend tests PASS.

[ ] Frontend build PASS.

[ ] Integration tests PASS.

[ ] Documentation được cập nhật.

Nếu tiêu chí nào chưa đạt, ghi rõ BLOCKED hoặc NOT IMPLEMENTED. Không tự đánh dấu PASS.

---

# 13. FINAL REPORT

Sau khi triển khai, báo cáo bằng tiếng Việt:

1. Những gì đã kiểm tra trong repository.
2. Kiến trúc thực tế sau khi triển khai.
3. Danh sách file đã tạo.
4. Danh sách file đã sửa.
5. Database schema và migration.
6. Danh sách API.
7. Security implementation.
8. Frontend implementation.
9. Các lệnh đã chạy.
10. Kết quả từng test.
11. Các tiêu chí PASS/FAIL/BLOCKED.
12. Những vấn đề còn tồn tại.
13. Hướng dẫn chạy dự án.
14. Đề xuất bước tiếp theo.

Chỉ báo cáo thành công nếu đã thực sự chạy và xác minh.

**BẮT ĐẦU NGAY: Đọc repository, lập kế hoạch và triển khai Phase 2.3 trực tiếp. Không chỉ trả về hướng dẫn hoặc code mẫu.**