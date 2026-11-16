# PHASE 2.3 — FINAL AUDIT

**Ngày kiểm tra:** 2026-11-09  
**Phạm vi:** implementation hiện có, source sau các thay đổi audit, và Docker Compose đang chạy.

## Kết luận

**Có thể chuyển sang Phase 2.4, có điều kiện.** Các luồng account/profile, xác thực JWT và E2E đã được kiểm chứng. Các rủi ro còn lại được liệt kê bên dưới, đặc biệt là logout chưa thu hồi JWT phía server, 10 advisory moderate trong dependency phát triển và bundle Phaser lớn.

Không xóa volume, không reset database, không sửa/xóa Flyway history. Backend container được build lại và thay riêng để chạy source đã kiểm tra; PostgreSQL vẫn giữ nguyên. Flyway V1 và V2 đều còn `success=true`.

## PASS / FAIL / BLOCKED theo tiêu chí

| # | Tiêu chí | Kết quả | Bằng chứng |
|---|---|---|---|
| 1 | Account và PlayerProfile trong PostgreSQL | **PASS** | Kết nối trực tiếp DB Compose; 5 account, 5 profile. V1/V2 thành công; có FK `fk_player_profiles_account` và unique theo account cho profile. |
| 2 | Password lưu BCrypt | **PASS** | Cả 5/5 `password_hash` có prefix `$2a$`; encoder trong backend là `BCryptPasswordEncoder`. Không đọc/in hash ra báo cáo. |
| 3 | Email unique | **PASS** | PostgreSQL có constraint `uk_accounts_email` (`UNIQUE`); đăng ký chuẩn hóa email về chữ thường. |
| 4 | JWT signature, expiration, invalid token | **PASS** | Integration test chấp nhận token hợp lệ, từ chối chữ ký sai và token hết hạn; malformed Bearer token trên Compose trả HTTP 403. E2E xác nhận JWT được gửi trong Authorization header và truy cập API thành công. |
| 5 | ACTIVE / LOCKED / DISABLED | **PASS** | Kiểm tra trên Compose: ACTIVE đọc profile HTTP 200; LOCKED và DISABLED từ chối login HTTP 400 và token đã cấp không đọc được profile HTTP 403. Tài khoản kiểm tra tạm thời đã được xóa riêng, profile được cascade; không tác động các hàng khác. |
| 6 | Chỉ sửa profile của chính mình | **PASS** | Integration test tạo hai account: token thứ hai sửa `/players/me`; profile account thứ nhất vẫn giữ nguyên. Controller lấy account ID từ authenticated principal. |
| 7 | Register/login trên Docker Compose | **PASS** | Stack database/backend/frontend đều healthy; Playwright đăng ký và đăng nhập qua frontend Nginx đang chạy, gọi API backend thật. |
| 8 | Playwright register → login → JWT → profile → update → logout | **PASS** | Luồng đầy đủ chạy trên Chromium: đọc profile bằng Bearer token, cập nhật, reload để kiểm tra dữ liệu lưu, logout xóa token phía client và route profile chuyển về login. |
| 9 | 10 npm moderate advisories | **PASS — đã đánh giá** | 10 finding đều thuộc dependency phát triển; `npm audit --omit=dev` báo 0 vulnerability. Chưa nâng package vì đường sửa được npm đề xuất có thay đổi major/downgrade; không chạy `npm audit fix --force`. |
| 10 | React Fast Refresh warning | **PASS** | Tách `useAuth` khỏi module chỉ export `AuthProvider`; `npm run lint` hoàn tất không warning. |
| 11 | Vite bundle và Phaser | **PASS — đã đánh giá** | Đã đo bundle và xác nhận route Game đã dùng `lazy(() => import('./pages/GamePage'))`. Không thêm dynamic import lồng nhau vì chỉ chuyển chunk 1.2 MB sang file khác, không giảm kích thước Phaser hay initial bundle hiện tại. |
| 12 | Java giữa POM, Docker build/runtime | **PASS** | `pom.xml` target Java 17; Docker build image Maven/Temurin 17; runtime Temurin 17; container chạy Java 17.0.21. |

Không có tiêu chí **BLOCKED**. Không có tiêu chí chức năng **FAIL** sau kiểm chứng cuối.

## Test results

| Kiểm tra | Kết quả | Chi tiết |
|---|---|---|
| Backend integration | **PASS**, exit 0 | `mvn -q clean test`: 10 test, 0 failure, 0 error, 0 skipped. Bao gồm auth, persistence, status, ownership và JWT. |
| Browser E2E | **PASS**, exit 0 | PowerShell: `$env:PLAYWRIGHT_BASE_URL='http://localhost:8081'; npm run test:e2e -- --reporter=list`; 4/4 test trên Compose, gồm luồng register/login/profile/logout. |
| Frontend lint | **PASS**, exit 0 | `npm run lint`; không còn Fast Refresh warning. |
| Frontend production build | **PASS**, exit 0 | `npm run build`; TypeScript và Vite build thành công. Còn cảnh báo chunk lớn được ghi ở phần rủi ro. |
| Production dependency audit | **PASS**, exit 0 | `npm audit --omit=dev --json`: 0 vulnerability runtime. |
| Full npm audit | **10 moderate**, exit 1 | 0 high, 0 critical. Đây là advisory cần theo dõi cho dev toolchain, không được tính là production dependency exposure theo kết quả `--omit=dev`. |
| Docker Compose | **PASS** | database, backend, frontend đều healthy sau khi chỉ build/recreate backend. |
| PostgreSQL state | **PASS** | 5 account, 5 profile, cả 5 password hash dùng `$2a$`; mọi account hiện có trạng thái ACTIVE. Flyway V1/V2 đều thành công. |

Một lần chạy `mvn test` không clean trước đó vấp `UnsupportedClassVersionError` do artifact `target` cũ được biên dịch bằng Java 21 trong khi Maven chạy Java 17; `mvn clean test` đã build lại và đạt 10/10. Lần Playwright chạy song song đầu tiên có một đăng ký thất bại; chạy đơn lẻ và chạy lại toàn bộ suite đều thành công. Không xác định được nguyên nhân cho lần lỗi thoáng qua.

## Npm advisory details

`npm audit` ghi nhận 10 moderate findings: direct vulnerable package entries gồm `eslint`, `@eslint/js`, `typescript-eslint`; các package ảnh hưởng gián tiếp gồm `@eslint/config-array`, `@typescript-eslint/eslint-plugin`, `@typescript-eslint/parser`, `@typescript-eslint/type-utils`, `@typescript-eslint/typescript-estree`, `@typescript-eslint/utils` và `minimatch`. Root advisory là `minimatch` 10.2.6 (GHSA-g3hv-75wg-m249), DoS qua globparts amplification. Các đường phụ thuộc đi qua ESLint/TypeScript lint tooling; không có finding trong production dependency set.

Không thay dependency trong audit này. Cần xử lý trong đợt nâng cấp toolchain riêng, xác nhận tương thích với ESLint 10 hiện tại; không dùng force downgrade.

## Bundle assessment

Build phát ra:

- Main JS: **327.35 kB** (106.16 kB gzip).
- `GamePage` JS: **1,195.50 kB** (319.17 kB gzip), vượt ngưỡng cảnh báo 500 kB của Vite.

Phaser nằm trong route chunk đã lazy-load, không nằm trong main JS. Có thể tối ưu tiếp bằng cách xem xét import/tree-shaking Phaser theo tính năng thực dùng hoặc giảm phần demo/game; dynamic import thêm bên trong route hiện tại chủ yếu tách file, không làm payload Phaser nhỏ đi.

## Thay đổi thực hiện trong audit

- Bỏ JWT secret mặc định trong source; `JWT_SECRET` trở thành cấu hình bắt buộc và `.env.example` không chứa secret/password mẫu có thể dùng trực tiếp.
- Từ chối JWT đã cấp nếu tài khoản hiện không còn `ACTIVE`.
- Thêm regression test cho chữ ký/hết hạn JWT, trạng thái tài khoản và cô lập profile theo principal.
- Thêm Playwright flow đầy đủ và cho phép chạy test trực tiếp với URL Compose.
- Tách hook `useAuth` để loại Fast Refresh warning; cập nhật README liên quan đến auth và secret.

Giá trị secret trong `.env` hiện có chỉ được kiểm tra trạng thái cấu hình/độ dài và so sánh với placeholder mẫu; không được đọc ra hay ghi vào báo cáo. Tài khoản đăng ký bởi các lần E2E trên DB persistent được giữ lại; không reset hoặc dọn dữ liệu người dùng. Riêng account tạm dùng cho status smoke test đã được xóa chính xác.

## Rủi ro còn tồn tại

1. **Logout chỉ xóa token phía client.** Backend dùng JWT stateless và không có cơ chế revoke/denylist; token bị đánh cắp vẫn dùng được đến lúc hết hạn. Cần quyết định chính sách thu hồi token ở phase phù hợp.
2. **10 moderate advisory trong dev toolchain** vẫn còn; hiện không có package production bị audit báo lỗi.
3. **GamePage chunk 1,195.50 kB** còn cảnh báo Vite dù route đã lazy-load.
4. **JDK local không đồng nhất theo lệnh:** `java` trên PATH báo Java 21.0.10, trong khi Maven chạy bằng Java 17.0.16; deployment/POM/Docker đều Java 17. Nên thống nhất `JAVA_HOME` khi phát triển để tránh artifact stale/version mismatch.
5. **E2E trên database persistent tạo dữ liệu account thử nghiệm.** Hiện tổng số account/profile đã kiểm tra là 5/5; không reset DB để dọn dữ liệu thử nghiệm.

## Quyết định chuyển phase

**ĐỦ ĐIỀU KIỆN chuyển sang Phase 2.4 — CÓ ĐIỀU KIỆN.** Các luồng được yêu cầu và kiểm soát cốt lõi đều pass trên source và stack Compose đã cập nhật. Nên ghi nhận rủi ro logout/revocation, dev advisories và bundle Phaser làm follow-up; không có blocker chức năng đã biết từ audit này.
