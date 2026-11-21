# PHASE 2.4 — GAME SESSION & PLAYER PROGRESS
## Implementation Master Prompt

Bạn là Senior Full-stack Game Engineer / Software Architect. Hãy triển khai Phase 2.4 cho dự án **VIỆT SỬ KÝ** tại repository hiện tại.

### 1. Project context

Dự án là web-based Vietnamese history educational game.

Tech stack dự kiến:
- Frontend: React + TypeScript + Vite + SCSS.
- Game engine: Phaser 3.
- Backend: Java 17 + Spring Boot.
- Database: PostgreSQL 16.
- Infrastructure: Docker Compose + Nginx.
- Authentication: JWT đã triển khai ở Phase 2.3.

Architecture:
- React chịu trách nhiệm UI, routing và giao tiếp API.
- Phaser chịu trách nhiệm rendering, input và gameplay.
- Spring Boot chịu trách nhiệm nghiệp vụ và dữ liệu có tính quyết định.
- PostgreSQL lưu dữ liệu lâu dài.
- Backend là nguồn dữ liệu chính thức cho player progress, mission completion, score, rewards và unlocks.

Phase 2.3 đã được audit PASS có điều kiện:
- Account và PlayerProfile hoạt động.
- JWT authentication hoạt động.
- Register/login/profile/logout đã có E2E.
- PostgreSQL và Flyway V1, V2 đã thành công.
- Docker Compose đang hoạt động.

### 2. BẮT BUỘC: Audit repository trước khi code

Trước khi thay đổi bất kỳ file nào:

1. Đọc README, PROJECT_CONTEXT, ARCHITECTURE, TECHNICAL_DESIGN, API docs và các tài liệu hiện có.
2. Kiểm tra toàn bộ cấu trúc frontend và backend.
3. Kiểm tra các entity, repository, service, controller, DTO, security config và migration hiện tại.
4. Kiểm tra tích hợp React với Phaser.
5. Kiểm tra authentication principal và cách lấy account/player hiện tại.
6. Kiểm tra Docker Compose, Nginx proxy và cấu hình môi trường.
7. Xác định những thành phần Phase 2.4 đã có, chưa có hoặc đang triển khai dở.

Không tạo lại những thành phần đã tồn tại.

Không tự ý thay đổi architecture chỉ vì muốn code theo cấu trúc quen thuộc của bạn.

Tạo tài liệu `PHASE_2_4_IMPLEMENTATION_PLAN.md` trước khi code, bao gồm:
- Current state.
- Existing components.
- Missing components.
- Proposed architecture.
- Database ERD.
- API contract.
- Implementation sequence.
- Risks and assumptions.

Nếu phát hiện yêu cầu mâu thuẫn với source hoặc tài liệu, hãy ghi nhận và chọn phương án ít phá vỡ hệ thống nhất. Không tự ý triển khai một nghiệp vụ chưa được xác định.

### 3. Business scope

Triển khai các module:

**A. Mission**
- Quản lý nhiệm vụ.
- Mã nhiệm vụ duy nhất.
- Tên, mô tả, chương, thứ tự.
- Loại nhiệm vụ.
- Điều kiện mở khóa.
- Điều kiện hoàn thành.
- Phần thưởng cấu hình.
- Trạng thái hoạt động.

**B. Game Session**
- Tạo phiên chơi cho người chơi đã đăng nhập.
- Gắn session với player và mission.
- Ghi nhận thời điểm bắt đầu.
- Theo dõi trạng thái session.
- Kết thúc, hoàn thành hoặc bỏ dở session.
- Không cho phép người chơi sử dụng session của tài khoản khác.
- Kiểm soát session đã kết thúc hoặc hết hạn.

**C. Mission Progress**
- Theo dõi tiến trình nhiệm vụ của từng player.
- Trạng thái LOCKED, AVAILABLE, IN_PROGRESS, COMPLETED (điều chỉnh nếu cần).
- Ghi nhận thời gian bắt đầu và hoàn thành.
- Không cho phép hoàn thành nhiệm vụ đang bị khóa.

**D. Player Progress**
- Tổng hợp tiến trình theo chương.
- Số nhiệm vụ đã hoàn thành.
- Điểm tích lũy.
- Các nội dung đã mở khóa.
- Có API lấy tiến trình hiện tại.

**E. Player Rewards**
- Lưu lịch sử nhận thưởng.
- Điểm và phần thưởng được Backend tính toán.
- Không nhận thưởng lặp lại khi gửi request nhiều lần.
- Có lịch sử giao dịch phần thưởng.

**F. Game Save**
- Xác định dữ liệu gameplay cần lưu.
- Cho phép lưu và khôi phục trạng thái cần thiết.
- Gắn dữ liệu save với player và mission/session phù hợp.
- Không cho phép đọc hoặc ghi save của người chơi khác.
- Giới hạn kích thước và xác thực cấu trúc dữ liệu đầu vào.

### 4. Database design

Thiết kế ERD trước khi triển khai.

Xem xét các bảng:
- `missions`
- `game_sessions`
- `mission_progress`
- `player_progress`
- `player_rewards`
- `game_saves`

Đây là danh sách đề xuất, không phải yêu cầu tạo tất cả bằng mọi giá. Hãy tránh lưu trùng dữ liệu có thể suy ra một cách đáng tin cậy.

Yêu cầu:
- UUID primary key nếu phù hợp với convention hiện tại.
- Foreign key rõ ràng.
- Unique constraint phù hợp.
- Index cho các truy vấn thường xuyên.
- Created/updated timestamps.
- Enum hoặc status constraint nhất quán.
- Transaction cho các thao tác cập nhật liên quan.

Migration:
- Tạo migration tiếp theo sau V2, thông thường là `V3__...sql`.
- Tuyệt đối không sửa V1 hoặc V2 đã được thực thi.
- Không xóa Flyway history.
- Không reset PostgreSQL volume.
- Không xóa dữ liệu account/player hiện có.
- Kiểm tra migration trên database Compose hiện tại.

### 5. Backend API

Thiết kế API theo convention hiện tại của dự án.

Các API dự kiến:

**Mission**
- `GET /api/v1/missions`
- `GET /api/v1/missions/{missionId}`
- `GET /api/v1/missions/{missionId}/availability`

**Game Session**
- `POST /api/v1/game-sessions`
- `GET /api/v1/game-sessions/{sessionId}`
- `POST /api/v1/game-sessions/{sessionId}/events`
- `POST /api/v1/game-sessions/{sessionId}/complete`
- `POST /api/v1/game-sessions/{sessionId}/abandon`

**Player Progress**
- `GET /api/v1/players/me/progress`
- `GET /api/v1/players/me/progress/missions`
- `GET /api/v1/players/me/rewards`

**Game Save**
- `GET /api/v1/players/me/game-saves`
- `GET /api/v1/players/me/game-saves/{saveId}`
- `PUT /api/v1/players/me/game-saves/{saveId}`

Đây là API contract dự kiến. Hãy kiểm tra tính phù hợp với source và điều chỉnh trước khi implement.

Mỗi API cần:
- Request DTO.
- Response DTO.
- Validation.
- Error response nhất quán.
- Authentication/authorization phù hợp.
- Integration test.

Không trả entity database trực tiếp ra API.

Không nhận `accountId` hoặc `playerId` từ client để quyết định quyền sở hữu. Lấy danh tính từ authenticated principal.

### 6. Gameplay security và tính toàn vẹn dữ liệu

Đây là yêu cầu quan trọng nhất.

Không tin tưởng các giá trị do frontend gửi:
- `isCompleted`
- `score`
- `reward`
- `unlocked`
- `duration`
- `missionStatus`

Frontend và Phaser có thể gửi gameplay events hoặc kết quả cần thiết, nhưng Backend phải xác minh các điều kiện nghiệp vụ trước khi cập nhật dữ liệu chính thức.

Thiết kế tối thiểu:
- Session phải thuộc đúng player.
- Mission phải hợp lệ và đã được mở khóa.
- Session phải đang ở trạng thái cho phép thao tác.
- Kiểm tra event hợp lệ theo loại nhiệm vụ.
- Có giới hạn dữ liệu đầu vào.
- Có idempotency cho request hoàn thành và cấp thưởng.
- Không cho phép cộng điểm hoặc nhận thưởng lặp lại.
- Dùng transaction khi hoàn thành nhiệm vụ.
- Có kiểm soát cạnh tranh khi hai request cùng hoàn thành một session.

Không cần xây dựng anti-cheat phức tạp ở phase này. Tuy nhiên, không được thiết kế hệ thống mà chỉ cần sửa request JSON là người chơi có thể tự cấp điểm và phần thưởng cho mình.

### 7. Frontend integration

Tích hợp vào React hiện tại.

Cần có:
- Mission list.
- Mission detail.
- Start mission.
- Game session state.
- Mission result.
- Player progress.
- Reward summary.
- Resume/load save nếu nghiệp vụ hỗ trợ.

Yêu cầu:
- Dùng API service riêng.
- TypeScript types rõ ràng.
- Có loading, error, empty và success states.
- Không hardcode dữ liệu người chơi hoặc kết quả nhiệm vụ.
- Không làm hỏng AuthProvider, protected routes và API client của Phase 2.3.
- Không đưa logic quyết định phần thưởng vào React.

### 8. Phaser integration

Kiểm tra GamePage và cấu trúc Phaser hiện có trước khi chỉnh sửa.

Phaser cần:
- Nhận mission/session context.
- Khởi tạo gameplay.
- Phát sinh các gameplay events cần thiết.
- Thông báo trạng thái cho React qua integration interface rõ ràng.
- Kết thúc gameplay và chuyển dữ liệu cần thiết cho flow gửi kết quả.

Không để Phaser truy cập trực tiếp database.

Không để Phaser tự quyết định dữ liệu tiến trình chính thức.

Không tạo thêm một game engine hoặc thay thế Phaser.

### 9. Testing

**Backend**
- Tạo session thành công.
- Không tạo session cho mission bị khóa.
- Không truy cập session của player khác.
- Không thao tác session đã kết thúc.
- Không hoàn thành mission nhiều lần để nhận thưởng trùng.
- Cập nhật mission progress chính xác.
- Player progress phản ánh đúng database.
- Game save có kiểm soát quyền sở hữu.
- Transaction rollback khi cập nhật thất bại.
- Xử lý hai request hoàn thành đồng thời.

**Frontend / E2E**
- Đăng nhập.
- Xem danh sách nhiệm vụ.
- Chọn nhiệm vụ hợp lệ.
- Bắt đầu session.
- Thực hiện flow gameplay.
- Hoàn thành nhiệm vụ.
- Xem kết quả và tiến trình.
- Reload trang và xác nhận dữ liệu vẫn tồn tại.
- Đăng xuất và kiểm tra protected route.

**Infrastructure**
- Maven clean test.
- Frontend lint.
- Frontend production build.
- Playwright E2E.
- Docker Compose startup.
- Flyway migration.
- Backend health.
- Nginx API proxy.
- PostgreSQL persistence.

Không báo PASS nếu chưa chạy test tương ứng.

### 10. Documentation

Cập nhật:
- `README.md`
- `PROJECT_CONTEXT.md`
- `ARCHITECTURE.md`
- `TECHNICAL_DESIGN.md`
- API documentation.
- Database documentation.
- `PHASE_2_4_IMPLEMENTATION_PLAN.md`
- `PHASE_2_4_TEST_REPORT.md`

ERD và sequence diagram nên sử dụng Mermaid.

### 11. Implementation sequence

Triển khai theo thứ tự:

1. Repository audit.
2. Architecture, ERD và API contract.
3. Database migration.
4. Backend entities/repositories.
5. Backend services và business rules.
6. Backend controllers và security.
7. Backend integration tests.
8. Frontend API services và types.
9. React mission/progress UI.
10. Phaser integration.
11. E2E tests.
12. Docker Compose verification.
13. Documentation và final report.

Sau mỗi nhóm công việc, chạy test phù hợp và sửa lỗi trước khi đi tiếp.

### 12. Safety constraints

- Không xóa database volume.
- Không reset PostgreSQL.
- Không xóa dữ liệu account/player.
- Không sửa migration đã chạy.
- Không in JWT secret, password hoặc dữ liệu nhạy cảm vào log/report.
- Không hardcode secret.
- Không tắt Spring Security để test cho dễ.
- Không bỏ qua test lỗi.
- Không thay đổi Java target 17 nếu chưa có lý do kiến trúc được xác nhận.
- Không tự ý thay đổi dependency lớn.
- Không xóa hoặc viết lại chức năng Phase 2.3 đang hoạt động.
- Không tuyên bố đã triển khai nếu chỉ mới tạo file hoặc viết code nhưng chưa kiểm thử.

### 13. Final report

Khi hoàn thành, tạo báo cáo gồm:

1. Summary.
2. Files created.
3. Files modified.
4. Database changes.
5. API endpoints.
6. Business rules.
7. Security controls.
8. React/Phaser integration.
9. Test results với số lượng pass/fail.
10. Docker Compose validation.
11. Known issues.
12. Remaining risks.
13. Phase 2.4 acceptance checklist.
14. Recommendation: PASS / PASS WITH CONDITIONS / FAIL.

**Bắt đầu bằng repository audit và implementation plan. Không code ngay trước khi hoàn tất audit.**