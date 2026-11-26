# Hướng dẫn Kiểm thử Thủ công Tương tác Chéo (E2E User Flows Guide)

Tài liệu này mô tả chi tiết các kịch bản kiểm thử thủ công đầu-cuối (End-to-End) và tương tác chéo thời gian thực giữa 3 vai trò **`ADMIN` ↔ `TEACHER` ↔ `STUDENT`** dựa trên mã nguồn thực tế trong `/backend`, `/desktop` và đặc tả API (`docs/API_CONTRACT.md`, `docs/api/*.md`).

---

## 0. Chuẩn bị môi trường & Phiên test song song (3–4 tài khoản)

### 0.1. Khởi chạy Database, Backend và Desktop Client

1. **Cấu hình biến môi trường (tại thư mục gốc `d:\Web\ai-study-assistant`):**
   ```powershell
   Copy-Item .env.example .env
   # Kiểm tra MYSQL_*, JWT_SECRET, GEMINI_API_KEY (và GOOGLE_CLIENT_ID/SECRET nếu test Google Calendar)
   ```
2. **Khởi động MySQL 8.0 bằng Docker:**
   ```powershell
   docker compose up -d
   docker compose ps
   ```
   - MySQL chạy ở cổng `3307` (`utf8mb4_unicode_ci`, múi giờ `UTC`). Schema và bảng `system_settings` được Flyway tự động migrate (`V1` → `V6`) khi Backend khởi động.
3. **Khởi động Backend (Spring Boot 3.2.5 / Java 17):**
   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```
   - REST API Base URL: `http://localhost:8080/api/v1`
   - WebSocket STOMP (SockJS): `http://localhost:8080/ws` (native WS: `ws://localhost:8080/ws-native`)
   - Swagger UI: `http://localhost:8080/swagger-ui.html`
4. **Khởi động Frontend / Desktop Client:**
   - **Cách A — Chạy Electron App (`desktop`):**
     ```powershell
     cd desktop
     npm install
     npm run electron:dev
     ```
     *(Tự động chạy Vite dev server tại `http://localhost:5173` và mở cửa sổ Electron).*
   - **Cách B — Chỉ chạy Web Vite (nếu không cần Electron IPC):**
     ```powershell
     cd desktop
     npm run dev
     ```
   - Lưu ý: Ứng dụng dùng `HashRouter` (các đường dẫn dạng `http://localhost:5173/#/login`, `http://localhost:5173/#/dashboard`, `http://localhost:5173/#/admin/users`, ...).

### 0.2. Mở đồng thời 3–4 phiên độc lập (không cần đăng xuất qua lại)

Trong mã nguồn `desktop/src/services/tokenStorage.ts`, JWT (`asa_access_token`, `asa_refresh_token`) và thông tin người dùng (`asa_user`) được lưu trong **`localStorage`**. Do đó, các tab trong cùng một cửa sổ trình duyệt sẽ dùng chung phiên đăng nhập.

Để mở song song **4 phiên độc lập** kiểm tra tác động thời gian thực (WebSocket STOMP & Notification):
- **Phiên 1 (`ADMIN`):** Dùng cửa sổ **Electron App** (`npm run electron:dev`).
- **Phiên 2 (`TEACHER`):** Mở **Chrome Profile 1** (hoặc Edge) truy cập `http://localhost:5173`.
- **Phiên 3 (`STUDENT A`):** Mở **Chrome Profile 2** (hoặc cửa sổ Ẩn danh Chrome) truy cập `http://localhost:5173`.
- **Phiên 4 (`STUDENT B`):** Mở **Firefox / Edge InPrivate** (hoặc Chrome Profile 3) truy cập `http://localhost:5173`.

> **Mẹo:** Không mở `STUDENT A` và `STUDENT B` trong 2 tab của cùng 1 cửa sổ ẩn danh vì chúng vẫn dùng chung `localStorage`. Hãy dùng mỗi tài khoản một Profile/Trình duyệt riêng.

### 0.3. Tài khoản Seed & Cách tạo nhanh 4 tài khoản test

- **Trạng thái Seed trong mã nguồn:** Các migration Flyway (`V1`–`V6`) **không tạo sẵn tài khoản mẫu** (chỉ seed 3 tham số trong `system_settings`: `AI_DAILY_QUOTA_PER_USER=50`, `MAX_UPLOAD_FILE_SIZE_MB=20`, `GOOGLE_ALLOW_PERSONAL_ACCOUNTS=false`).
- **Cách tạo nhanh 4 tài khoản test:**
  1. Trên màn hình `/register` (**Tạo tài khoản**), đăng ký 4 tài khoản (mặc định đều có role `STUDENT`, trạng thái `ACTIVE`):
     - `admin@actvn.edu.vn` (Mật khẩu gợi ý: `Test@123456`)
     - `teacher@actvn.edu.vn` (Mật khẩu gợi ý: `Test@123456`)
     - `student.a@actvn.edu.vn` (Mật khẩu gợi ý: `Test@123456`)
     - `student.b@gmail.com` (dùng email cá nhân để tiện test thêm luồng Google Calendar ở Phần 1 & 7)
  2. Cập nhật quyền `ADMIN` và `TEACHER` trực tiếp trong MySQL (chỉ cần làm 1 lần đầu; sau này `ADMIN` có thể tự đổi role user khác trên UI):
     ```powershell
     docker exec -it ai_study_assistant_mysql mysql -u root -p
     ```
     ```sql
     USE ai_study_assistant;
     UPDATE users SET role = 'ADMIN'   WHERE email = 'admin@actvn.edu.vn';
     UPDATE users SET role = 'TEACHER' WHERE email = 'teacher@actvn.edu.vn';
     SELECT id, email, role, status FROM users;
     ```
  3. Đăng nhập lại ở cả 4 cửa sổ tương ứng. Kiểm tra góc trên phải (`Header`) hiện trạng thái **`Đã kết nối trực tiếp`** (WebSocket STOMP kết nối thành công).

---

## Phần 1: Luồng Admin ↔ Teacher & Student (Quản trị & Tác động hệ thống)

### `FLOW-01`: Admin cấp quyền Giảng viên (`STUDENT` → `TEACHER`)
- **Mục tiêu:** Xác nhận Admin đổi vai trò tài khoản và bộ menu Sidebar/route của user cập nhật đúng quyền Giảng viên sau khi đăng nhập lại.
- **Điều kiện chuẩn bị:** Phiên 1 đăng nhập `ADMIN` (`/admin/users`); Phiên 4 đang có tài khoản `STUDENT B` (`student.b@gmail.com`) ở trạng thái `ACTIVE`.

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`ADMIN`:** Tại Sidebar chọn **Người dùng** (`/admin/users`). Tìm `student.b@gmail.com` ở ô *Tìm theo tên hoặc email...* → bấm **Tìm kiếm**. | `GET /api/v1/users?keyword=student.b@gmail.com` | Hiển thị dòng `student.b@gmail.com` với badge vai trò **Sinh viên** và trạng thái **Đang hoạt động**. |
| 2 | **`ADMIN`:** Bấm nút **Đổi vai trò** trên dòng user đó → tại dialog *Xác nhận đổi vai trò tài khoản*, chọn **Giảng viên (TEACHER)** → bấm **Xác nhận đổi vai trò**. | `PUT /api/v1/users/{id}/role` (`{ "role": "TEACHER", "version": ... }`); cập nhật `users.role = 'TEACHER'`; ghi audit log `USER_ROLE_UPDATED`. | Bảng tại `/admin/users` cập nhật badge thành **Giảng viên**. |
| 3 | **`STUDENT B`:** Trên phiên của `STUDENT B`, mở menu tài khoản ở Header → đăng xuất và đăng nhập lại (để cấp JWT mới chứa `TEACHER`). | `POST /api/v1/auth/logout` → `POST /api/v1/auth/login` trả về `accessToken` và `user.role = "TEACHER"`. | Sidebar chuyển nhóm từ *Không gian học tập* sang *Không gian giảng dạy* với đầy đủ menu: **Tổng quan**, **Lớp của tôi**, **Bài tập**, **Bài kiểm tra**, **Lịch**, **Cuộc họp**, **Tin nhắn**, **Thống kê lớp**, **Hồ sơ**; vào `/dashboard` thấy **Bảng điều khiển Giảng viên**. *(Sau bước test này có thể đổi `student.b@gmail.com` về lại **Sinh viên (STUDENT)** để dùng cho các phần sau).* |

- **Nhánh lỗi / Edge-case:**
  - `ADMIN` bấm **Đổi vai trò** trên chính dòng của mình (`(Bạn)`): nút trên UI bị vô hiệu hóa; nếu gọi trực tiếp API `PUT /api/v1/users/{myId}/role` sẽ trả `400 BAD_REQUEST`.
  - User có vai trò `STUDENT` truy cập trực tiếp `#/admin/users` hoặc `#/teacher/dashboard` sẽ bị chặn sang trang `/403`; gọi API `/api/v1/admin/**` trả `403 FORBIDDEN`.

---

### `FLOW-02`: Admin khóa và mở khóa tài khoản (`LOCKED` ↔ `ACTIVE`)
- **Mục tiêu:** Xác nhận khi Admin khóa tài khoản, user bị chặn đăng nhập / từ chối làm mới phiên và khôi phục bình thường khi mở khóa.
- **Điều kiện chuẩn bị:** Phiên 1 đăng nhập `ADMIN`; Phiên 3 đăng nhập `STUDENT A`.

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`ADMIN`:** Vào **Người dùng** (`/admin/users`), tìm `STUDENT A` → bấm **Khóa** → tại dialog *Xác nhận khóa tài khoản* bấm **Khóa tài khoản**. | `PUT /api/v1/users/{id}/status` (`{ "status": "LOCKED" }`); cập nhật `users.status = 'LOCKED'`; ghi audit log `USER_STATUS_UPDATED`. | Badge trạng thái trên bảng đổi thành **Đã khóa**, nút thao tác đổi thành **Mở khóa**. |
| 2 | **`STUDENT A`:** Thực hiện thao tác tải lại dữ liệu (ví dụ F5 hoặc bấm menu **Lớp học**) hoặc đăng xuất rồi đăng nhập lại tại `/login`. | `JwtAuthenticationFilter` thấy `!isAccountNonLocked()` nên không set `SecurityContext` → trả `401 UNAUTHORIZED` (auto-refresh cũng bị từ chối với `403 ACCOUNT_LOCKED`, đẩy về `/login`). Khi đăng nhập lại qua `POST /api/v1/auth/login`, backend trả `403 ACCOUNT_LOCKED`. | `STUDENT A` bị đăng xuất khỏi phiên hoặc không thể đăng nhập, hiển thị thông báo tài khoản đã bị khóa. |
| 3 | **`ADMIN`:** Bấm **Mở khóa** trên dòng `STUDENT A` → tại dialog *Xác nhận mở khóa tài khoản* bấm **Mở khóa tài khoản**. | `PUT /api/v1/users/{id}/status` (`{ "status": "ACTIVE" }`); ghi audit log `USER_STATUS_UPDATED`. | Trạng thái trên bảng về lại **Đang hoạt động**. |
| 4 | **`STUDENT A`:** Đăng nhập lại tại `/login`. | `POST /api/v1/auth/login` trả `200 OK` cùng JWT mới. | `STUDENT A` vào lại **Tổng quan** (`/dashboard`) bình thường. |

- **Nhánh lỗi / Edge-case:**
  - Nhập sai mật khẩu 5 lần liên tiếp trên `/login`: tài khoản tự khóa tạm 15 phút, trả `403 ACCOUNT_LOCKED`.
  - Gửi `version` cũ khi 2 tab Admin sửa cùng lúc: trả `409 VERSION_CONFLICT`.

---

### `FLOW-03`: Admin khóa / lưu trữ lớp học toàn hệ thống
- **Mục tiêu:** Xác nhận Admin có thể đổi trạng thái lớp học sang `CLOSED` hoặc `ARCHIVED` và tác động trực tiếp đến hành vi tham gia/hiển thị bên Teacher & Student.
- **Điều kiện chuẩn bị:** Đã có ít nhất 1 lớp học ở trạng thái `OPEN` có `TEACHER` chủ nhiệm và `STUDENT A` là thành viên (hoặc tạo trước ở Phần 2 rồi quay lại test).

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`ADMIN`:** Vào Sidebar → **Lớp học** (`/admin/classes`). Tìm lớp mục tiêu → bấm **Đổi trạng thái** → chọn **Đã đóng (CLOSED) — Khóa nhận thành viên mới** (hoặc **Lưu trữ (ARCHIVED) — Kết thúc học phần**) → bấm **Xác nhận thay đổi**. | `PUT /api/v1/admin/classes/{id}/status`; cập nhật `classrooms.status`; ghi audit log `ADMIN_CLASSROOM_STATUS_UPDATED`. | Bảng tại `/admin/classes` cập nhật badge **Đã đóng** hoặc **Lưu trữ**. |
| 2 | **`TEACHER` & `STUDENT A`:** Mở **Lớp của tôi** (`/my-classes`) hoặc **Lớp học** (`/classes`), bấm **Vào lớp** (`/classes/:classId`). | `GET /api/v1/classes/{id}` trả `status` mới (`CLOSED` hoặc `ARCHIVED`). | Header chi tiết lớp hiển thị badge **Đã đóng** hoặc **Lưu trữ**; tại danh sách `/classes`, lọc theo trạng thái tương ứng sẽ thấy lớp này. |
| 3 | **`STUDENT B` (ngoài lớp):** Vào **Lớp học** → bấm **Tham gia lớp** → nhập mã lớp của lớp vừa đóng/lưu trữ → bấm **Gửi yêu cầu tham gia**. | `POST /api/v1/classes/join-requests` kiểm tra `classroom.status != OPEN` và trả `404 CLASS_CODE_INVALID`. | `STUDENT B` không thể gửi yêu cầu tham gia và thấy thông báo lỗi mã lớp không hợp lệ / lớp không còn mở. |

- **Nhánh lỗi / Edge-case:**
  - `ADMIN` đổi lại về **Đang mở (OPEN) — Cho phép tham gia & hoạt động** ⇒ `STUDENT B` gửi mã xin vào lớp thành công (`201 Created`).

---

### `FLOW-04`: Admin thay đổi cấu hình hệ thống (`AI_DAILY_QUOTA_PER_USER`, `MAX_UPLOAD_FILE_SIZE_MB`, `GOOGLE_ALLOW_PERSONAL_ACCOUNTS`)
- **Mục tiêu:** Kiểm tra các tham số vận hành hệ thống áp dụng ngay lập tức lên người dùng (`429`, giới hạn tải tệp, kết nối Google cá nhân).
- **Điều kiện chuẩn bị:** Phiên 1 (`ADMIN`), Phiên 3 (`STUDENT A` có sẵn 1 tài liệu `EXTRACTED` để hỏi AI), Phiên 4 (`STUDENT B` dùng email `@gmail.com` hoặc nối Google cá nhân).

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`ADMIN`:** Vào Sidebar → **Cấu hình** (`/admin/settings`, tab **Cấu hình hệ thống**). Đặt **Hạn mức lượt AI / ngày / người dùng** (`AI_DAILY_QUOTA_PER_USER`) = `2`, **Dung lượng tệp tải lên tối đa (MB)** (`MAX_UPLOAD_FILE_SIZE_MB`) = `5`, tắt công tắc **Cho phép kết nối tài khoản Google cá nhân** (`GOOGLE_ALLOW_PERSONAL_ACCOUNTS` → **Đang tắt**) → bấm **Lưu cấu hình hệ thống**. | `PUT /api/v1/admin/settings` lưu vào `system_settings`; ghi audit log `SYSTEM_SETTINGS_UPDATED`. | Hiện thông báo lưu thành công và các giá trị mới giữ nguyên khi tải lại trang. |
| 2 | **`STUDENT A`:** Mở **Tài liệu AI** → vào chi tiết một tài liệu ở tab **Hỏi đáp Grounded** (hoặc tab **AI Tóm tắt**), gửi liên tiếp các yêu cầu AI vượt quá `2` lượt trong ngày. | `AiQuotaServiceImpl.checkAndEnforceDailyQuota` đếm `ai_jobs` + `ai_chat_messages` trong ngày UTC `>= 2` → trả `429 Too Many Requests` (`AI_QUOTA_EXCEEDED`) kèm header `Retry-After`. | Đến lượt thứ 3, UI hiển thị banner lỗi vượt hạn mức sử dụng AI trong ngày (mã `429`). |
| 3 | **`TEACHER` / `STUDENT A`:** Thử tải lên một tệp `.pdf` dung lượng lớn (ví dụ `> 20MB` trên UI hoặc gọi API `POST /api/v1/courses/{id}/materials` với tệp vượt ngưỡng). | UI kiểm tra `> 20MB` báo toast *"Dung lượng tệp vượt quá giới hạn 20MB."* *(Lưu ý mã nguồn hiện tại: `MAX_UPLOAD_FILE_SIZE_MB` đã được lưu trong `system_settings`, nhưng tầng kiểm tra upload ở `FileStorageServiceImpl` và Desktop UI vẫn đang áp mức cố định 20MB trả `400 FILE_TOO_LARGE`)*. | Upload tệp quá cỡ bị từ chối với lỗi rõ ràng, không tạo bản ghi `materials` lỗi trên DB. |
| 4 | **`STUDENT B` (email `@gmail.com`):** Vào **Lịch học** (`/calendar`) → bấm **Google Calendar** → bấm **Kết nối Google Calendar** và hoàn tất OAuth bằng tài khoản Gmail cá nhân. | `GoogleIntegrationServiceImpl` đọc `GOOGLE_ALLOW_PERSONAL_ACCOUNTS = false`, kiểm tra email không kết thúc bằng `@actvn.edu.vn` → trả `403 FORBIDDEN`. | Kết nối Google cá nhân bị từ chối (`403`). Khi `ADMIN` bật lại công tắc **Đang bật** và bấm **Lưu cấu hình hệ thống**, `STUDENT B` kết nối Gmail cá nhân thành công. |

- **Nhánh lỗi / Edge-case:**
  - `ADMIN` nhập giá trị âm hoặc `0` cho `AI_DAILY_QUOTA_PER_USER` / `MAX_UPLOAD_FILE_SIZE_MB`: trả `400 VALIDATION_ERROR`.
  - *(Nhớ đặt lại `AI_DAILY_QUOTA_PER_USER = 50` và `MAX_UPLOAD_FILE_SIZE_MB = 20` sau khi test xong để không ảnh hưởng Phần 7).*

---

### `FLOW-05`: Student báo cáo câu trả lời AI sai / gửi góp ý ⇒ Admin xử lý & kiểm tra Audit Log
- **Mục tiêu:** Kiểm tra luồng báo cáo hai kênh (`POST /api/v1/ai/feedback` và `POST /api/v1/reports`), quy trình Admin xử lý báo cáo và dấu vết trong *Nhật ký hệ thống (Audit Log)*.
- **Điều kiện chuẩn bị:** Phiên 1 (`ADMIN`), Phiên 3 (`STUDENT A`).

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`STUDENT A`:** Trong tab **Hỏi đáp Grounded** của một tài liệu (`/materials/:materialId`), dưới một câu trả lời của AI bấm nút **Báo câu trả lời sai** → tại modal *Báo câu trả lời AI sai hoặc không bám tài liệu*, nhập lý do → bấm **Gửi báo cáo**. | `POST /api/v1/ai/feedback` (`201 Created`) tạo bản ghi trong bảng `reports` với `type = "AI_INACCURATE"`, `targetType = "AI_CHAT_MESSAGE"`, `status = "OPEN"`. | Toast xác nhận gửi báo cáo thành công. |
| 2 | **`STUDENT A`:** Ở cuối Sidebar trái (hoặc menu user trên Header), bấm **Gửi báo cáo / Góp ý** → chọn **Phân loại** = *Báo cáo vi phạm trong lớp học* (hoặc *Góp ý chung / Sự cố hệ thống*) → nhập *Nội dung chi tiết \** → bấm **Gửi báo cáo**. | `POST /api/v1/reports` (`201 Created`) tạo thêm bản ghi `reports` trạng thái `OPEN`. | Modal đóng lại kèm thông báo gửi thành công. |
| 3 | **`ADMIN`:** Vào Sidebar → **Báo cáo & Nhật ký** (`/admin/reports`, tab **Báo cáo vi phạm & AI**). Lọc trạng thái **Chờ xử lý** → bấm **Xử lý** ở từng báo cáo vừa tạo → nhập *Ghi chú xử lý của Quản trị viên* → bấm **Đánh dấu đã xử lý**. | `PUT /api/v1/admin/reports/{id}` cập nhật `status = "RESOLVED"`, lưu ghi chú admin; ghi audit log `REPORT_RESOLVED`. | Trạng thái trên bảng đổi từ **Chờ xử lý** sang **Đã xử lý**; nút thao tác đổi thành **Xem chi tiết**. |
| 4 | **`ADMIN`:** Chuyển sang tab **Nhật ký hệ thống (Audit Log)** (hoặc vào `/admin/audit-logs`). Thử nhập `REPORT_RESOLVED` vào ô *Lọc theo mã hành động* → bấm **Lọc**, rồi xóa bộ lọc để xem toàn bộ. | `GET /api/v1/admin/audit-logs?action=...` trả danh sách log theo thời gian giảm dần. | Thấy đầy đủ các dòng log từ Phần 1: `USER_ROLE_UPDATED`, `USER_STATUS_UPDATED`, `ADMIN_CLASSROOM_STATUS_UPDATED`, `SYSTEM_SETTINGS_UPDATED`, `REPORT_RESOLVED` với đúng người thực hiện và thời điểm. |

- **Nhánh lỗi / Edge-case:**
  - Gửi form **Gửi báo cáo / Góp ý** khi để trống nội dung: bị chặn validation, không gọi API.
  - Tài khoản `TEACHER` hoặc `STUDENT` gọi `GET /api/v1/admin/audit-logs`: trả `403 FORBIDDEN`.

---

## Phần 2: Luồng Teacher ↔ Student — Vòng đời Lớp học & Cách ly dữ liệu

### `FLOW-06`: Tạo lớp → Xin vào bằng mã → Duyệt → Thông báo real-time & Phòng chat lớp
- **Mục tiêu:** Kiểm tra toàn bộ quy trình mở lớp, duyệt yêu cầu tham gia, đồng bộ thành viên sang phòng chat lớp và thông báo chuông thời gian thực.
- **Điều kiện chuẩn bị:** Phiên 2 (`TEACHER`), Phiên 3 (`STUDENT A`), Phiên 4 (`STUDENT B`).

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`TEACHER`:** Vào Sidebar → **Lớp của tôi** (`/my-classes`) → bấm **Tạo lớp mới**. Nhập *Tên lớp học \**, *Môn học \**, *Mô tả lớp học*, ngày bắt đầu/kết thúc → bấm **Xác nhận tạo lớp** → bấm **Sao chép mã** (8 ký tự). | `POST /api/v1/classes` (`201 Created`) tạo lớp `OPEN`, thêm `TEACHER` làm `OWNER_TEACHER`; phát `ClassroomCreatedEvent` tự động tạo phòng chat `CLASSROOM`. | Thấy card lớp mới với badge **Đang mở** và **Chủ nhiệm**; `TEACHER` mở **Tin nhắn** (`/messages`) đã thấy phòng chat của lớp mới. |
| 2 | **`STUDENT A`:** Vào Sidebar → **Lớp học** (`/classes`) → bấm **Tham gia lớp** → dán **Mã lớp học \*** → bấm **Gửi yêu cầu tham gia**. | `POST /api/v1/classes/join-requests` (`201 Created`) lưu `PENDING`; enqueue thông báo `CLASS_JOIN_REQUEST_CREATED` tới `TEACHER` qua `NotificationWorker` (poll tối đa ~5s) + đẩy WS `/user/queue/notifications`. | `STUDENT A` nhận toast *"Yêu cầu đã gửi, vui lòng chờ giảng viên duyệt!"*. `TEACHER` nhận thông báo chuông ở Header và card **Yêu cầu chờ duyệt** trên **Tổng quan** tăng lên. |
| 3 | **`TEACHER`:** Vào lớp vừa tạo → mở tab **Thành viên**. Tại mục *Yêu cầu tham gia chờ duyệt*, bấm **Duyệt vào lớp** trên dòng của `STUDENT A`. | `PUT /api/v1/classes/{id}/join-requests/{reqId}` (`APPROVED`); tạo `ClassMember(STUDENT, ACTIVE)`; phát `ClassMemberActivatedEvent` thêm `STUDENT A` vào phòng chat lớp; enqueue `CLASS_JOIN_REQUEST_APPROVED` đẩy WS. | `STUDENT A` nhận thông báo chuông real-time trên Header (*"Được duyệt vào lớp học"*); ở `/classes` của `STUDENT A` xuất hiện card lớp mới với badge **Sinh viên**; vào **Tin nhắn** thấy xuất hiện **Phòng lớp** tương ứng. |

- **Nhánh lỗi / Edge-case:**
  - `STUDENT B` nhập mã lớp sai (ví dụ `XXXXXXXX`): trả `404 CLASS_CODE_INVALID`.
  - `STUDENT A` thử gửi lại mã lớp khi đang `PENDING`: trả `409 JOIN_REQUEST_EXISTS`; khi đã là thành viên `ACTIVE`: trả `409 ALREADY_CLASS_MEMBER`.
  - `TEACHER` bấm **Từ chối** một yêu cầu khác (có thể nhập lý do từ chối): student nhận thông báo `CLASS_JOIN_REQUEST_REJECTED` và không vào được lớp.

---

### `FLOW-07`: Đăng bảng tin, tải tài liệu lớp & Kiểm tra cách ly dữ liệu (`STUDENT A` vs `STUDENT B`)
- **Mục tiêu:** Đảm bảo chỉ thành viên `ACTIVE` trong lớp mới xem/tải được nội dung lớp, người ngoài lớp bị chặn `403`.
- **Điều kiện chuẩn bị:** Lớp đang `OPEN` có `STUDENT A` đã duyệt; `STUDENT B` **không** thuộc lớp này.

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`TEACHER`:** Tại chi tiết lớp (`/classes/:classId`), ở tab **Bảng tin** bấm **Đăng thông báo mới** → nhập tiêu đề, nội dung, tick **Ghim thông báo này lên đầu bảng tin** → bấm **Đăng thông báo**. | `POST /api/v1/classes/{id}/posts` (`201 Created`). | Bài đăng xuất hiện đầu danh sách kèm badge **Đã ghim**; có thể thử bấm **Bỏ ghim** / **Ghim** (`PUT /api/v1/classes/{id}/posts/{postId}/pin`). |
| 2 | **`TEACHER`:** Chuyển tab **Tài liệu lớp** → bấm **Tải tài liệu lên lớp** → chọn file `.pdf` hoặc `.docx` (`<= 20MB`) → bấm **Tải lên**. | `POST /api/v1/classes/{id}/materials` (`201 Created`, multipart). | Dòng tài liệu hiện lên bảng với cột *Tên tệp*, *Định dạng*, *Dung lượng*, *Người tải*, *Ngày tải*, nút **Tải tệp**. |
| 3 | **`STUDENT A`:** Mở lớp → kiểm tra tab **Bảng tin** và **Tài liệu lớp** → bấm **Tải tệp**. | `GET /api/v1/classes/{id}/posts`, `GET /api/v1/classes/{id}/materials`, `GET /api/v1/class-materials/{id}/download` (`200 OK`). | `STUDENT A` đọc được thông báo đã ghim và tải được file tài liệu về máy. |
| 4 | **`STUDENT B` (ngoài lớp):** Trong **Lớp học** (`/classes`) không thấy lớp này. Thử truy cập thẳng URL `#/classes/{classId}` hoặc gọi API `GET /api/v1/classes/{classId}` / `GET /api/v1/class-materials/{materialId}/download`. | Backend kiểm tra `requireActiveMember` → trả **`403 NOT_CLASS_MEMBER`**. | `STUDENT B` bị chặn truy cập, không đọc được bảng tin và không tải được tài liệu lớp. |

- **Nhánh lỗi / Edge-case:**
  - `TEACHER` tải file sai định dạng (ví dụ `.exe`, `.jpg`, `.zip`) lên **Tài liệu lớp**: trả `400 FILE_TYPE_NOT_ALLOWED`.

---

### `FLOW-08`: Gỡ thành viên khỏi lớp & Thu hồi quyền tức thì
- **Mục tiêu:** Kiểm tra khi Teacher gỡ `STUDENT A` khỏi lớp, toàn bộ quyền xem tài liệu/bài tập và khung gửi/đọc tin nhắn phòng chat lớp bị khóa ngay.
- **Điều kiện chuẩn bị:** `STUDENT A` đang là thành viên `ACTIVE` của lớp và đang mở sẵn phòng chat lớp đó ở `/messages`.

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`TEACHER`:** Mở chi tiết lớp → tab **Thành viên** → bấm **Gỡ** trên dòng của `STUDENT A` → xác nhận hộp thoại. | `DELETE /api/v1/classes/{id}/members/{userId}` cập nhật `status = REMOVED`; phát `ClassMemberDeactivatedEvent` cập nhật `left_at` ở `conversation_members`; ghi audit log `CLASS_MEMBER_REMOVED`. | `STUDENT A` biến mất khỏi bảng *Danh sách thành viên*. |
| 2 | **`STUDENT A`:** Thử bấm vào tab **Tài liệu lớp** / **Bài tập** hoặc tải lại `/classes/{classId}`. | API trả **`403 NOT_CLASS_MEMBER`**. | `STUDENT A` mất quyền xem lớp, tài liệu và bài tập của lớp đó; lớp biến mất khỏi danh sách `/classes`. |
| 3 | **`STUDENT A`:** Vào **Tin nhắn** (`/messages`), chọn phòng chat của lớp vừa bị gỡ và thử gửi tin nhắn (hoặc bấm nút làm mới tin nhắn). | STOMP `SEND /app/conversations/{id}/messages` và REST `GET/POST /api/v1/conversations/{id}/messages` đều trả **`403 NOT_CONVERSATION_MEMBER`**. | Trên UI xuất hiện banner *"Không thể truy cập cuộc trò chuyện — Bạn đã rời lớp học hoặc bị giảng viên gỡ khỏi lớp nên khung chat đã bị khóa."* Ô nhập chuyển sang trạng thái khóa (`disabled`) với placeholder *"Khung nhập tin nhắn đã bị khóa do không còn quyền thành viên"*. |

- **Nhánh lỗi / Edge-case:**
  - Thử gỡ `OWNER_TEACHER` hoặc `CO_TEACHER` gỡ một `CO_TEACHER` khác: trả `403 FORBIDDEN`.
  - *(Sau flow này, cho `STUDENT A` xin vào lại lớp và `TEACHER` duyệt lại để tiếp tục Phần 3, 4, 5, 6).*

---

## Phần 3: Luồng Teacher ↔ Student — Bài tập, Nộp bài 1 lần, Chấm điểm & Mở lại nộp

### `FLOW-09`: Giao bài tập → Nộp bài 1 lần (`409` lần 2) → Chấm điểm → Mở lại quyền nộp
- **Mục tiêu:** Kiểm tra đầy đủ chu trình bài tập, khóa nộp lặp, chấm điểm/nhận xét, mở lại nộp và kiểm soát hạn nộp.
- **Điều kiện chuẩn bị:** Lớp đang `OPEN` có `TEACHER` và `STUDENT A` là thành viên `ACTIVE`.

| Bước | Vai trò & Thao tác cụ thể (Menu → Nộp/Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`TEACHER`:** Vào lớp → tab **Bài tập** → bấm **Giao bài tập mới**. Nhập *Tiêu đề bài tập \**, *Yêu cầu & Hướng dẫn làm bài*, *Thang điểm tối đa* (`10`), *Hạn nộp bài \** (trong tương lai), đính kèm tệp đề bài, **bỏ tick** *Cho phép sinh viên nộp muộn sau thời hạn* → bấm **Giao bài tập**. | `POST /api/v1/classes/{id}/assignments` (`201 Created`); enqueue thông báo `ASSIGNMENT_CREATED` tới sinh viên trong lớp. | `STUDENT A` nhận thông báo chuông; bài tập hiện trên **Tổng quan** (`/dashboard` mục *Bài tập sắp đến hạn (chưa nộp)*) và Sidebar → **Bài tập** (`/assignments`). |
| 2 | **`STUDENT A`:** Mở bài tập (`/assignments/:assignmentId`) → tải thử file đề bài → nhập *Câu trả lời văn bản* và/hoặc chọn *Tệp đính kèm bài làm* → bấm **Xác nhận nộp bài**. | `POST /api/v1/assignments/{id}/submissions` (kèm header `Idempotency-Key`) → `201 Created` (`late = false`, `status = SUBMITTED`); enqueue `ASSIGNMENT_SUBMITTED` cho `TEACHER`. | `STUDENT A` thấy card *Bài làm của bạn đã được ghi nhận* kèm badge **Đúng hạn**; form nộp bị ẩn. `TEACHER` nhận thông báo chuông và khi bấm **Xem bài nộp & Chấm điểm** thấy bài của `STUDENT A` với trạng thái **Đã nộp**, tải được file bài làm về. |
| 3 | **`STUDENT A`:** Thử gửi lại API `POST /api/v1/assignments/{id}/submissions` với một `Idempotency-Key` khác (hoặc qua Swagger/DevTools). | Backend kiểm tra đã tồn tại bài nộp chưa mở lại → trả **`409 ASSIGNMENT_ALREADY_SUBMITTED`**. | Hệ thống từ chối nộp lần 2 (mã `409`). |
| 4 | **`TEACHER`:** Tại `/assignments/:assignmentId`, bấm **Chấm điểm** trên dòng `STUDENT A` → nhập *Điểm số* (vd `8.75`), *Lời nhận xét & Góp ý*, chọn *Trạng thái trả kết quả* = **Trả bài ngay (Sinh viên thấy điểm & nhận xét)** (`RETURNED`) → bấm **Xác nhận chấm**. | `PUT /api/v1/submissions/{id}/grade` (`200 OK`); enqueue `ASSIGNMENT_GRADED` tới `STUDENT A`. | `STUDENT A` nhận thông báo chuông; tại `/assignments/:assignmentId` hiện khung **Kết quả chấm bài của Giảng viên** với badge **Điểm: 8.75 / 10** và **Lời nhận xét**. |
| 5 | **`TEACHER`:** Bấm **Mở lại** trên dòng `STUDENT A` → nhập *Lý do mở lại* → bấm **Xác nhận mở lại nộp**. | `PUT /api/v1/submissions/{id}/reopen` xóa `score`, `gradedAt`, `idempotencyKey`, đặt `status = RETURNED`; enqueue `ASSIGNMENT_REOPENED`. | `STUDENT A` nhận thông báo *Mở lại quyền nộp bài*. *(Lưu ý thực tế mã nguồn: backend trả `status: "RETURNED"` kèm `score: null, gradedAt: null`, trong khi `AssignmentDetailPage.tsx` đang kiểm tra `mySubmission.status !== "REOPENED"` nên giao diện chi tiết bài tập vẫn có thể giữ trạng thái đã ghi nhận cũ cho tới khi UI đồng bộ điều kiện này; ở API `POST /assignments/{id}/submissions` vẫn cho phép nộp bản mới `201 Created`).* |

- **Nhánh lỗi / Edge-case (Quá hạn nộp — bật vs tắt cho phép nộp muộn):**
  - **Bài tập quá hạn & TẮT *Cho phép nộp muộn*:** Bên `STUDENT A`, giao diện hiện cảnh báo *"Bài tập đã đóng nộp do đã quá hạn."* / *"Bài tập đã chính thức đóng nộp..."*; gửi API `POST /api/v1/assignments/{id}/submissions` trả **`422 ASSIGNMENT_CLOSED`**.
  - **Bài tập quá hạn & BẬT *Cho phép nộp muộn*:** Giao diện hiện cảnh báo *"Bài tập đã quá hạn nộp. Bài làm của bạn sẽ bị đánh dấu nộp muộn."* → bấm **Xác nhận nộp bài** thành công (`201 Created`), hiển thị badge **Nộp muộn** bên cả `STUDENT A` lẫn cột *Thời điểm nộp `(Muộn)`* của `TEACHER`.

---

## Phần 4: Luồng Teacher ↔ Student — Bài kiểm tra trong lớp (`ClassTest`)

### `FLOW-10`: Tạo bài kiểm tra từ Quiz → Làm bài định thời → Bảng điểm tổng hợp
- **Mục tiêu:** Kiểm tra tạo `ClassTest` từ một bộ Quiz, làm bài trong khung giờ mở, đồng hồ đếm ngược, bảo mật đáp án và bảng điểm tổng hợp.
- **Điều kiện chuẩn bị:** `TEACHER` sở hữu ít nhất 1 bộ Quiz (tạo tại **Tài liệu AI** / khóa học cá nhân của `TEACHER`); `STUDENT A` là thành viên `ACTIVE` của lớp.

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`TEACHER`:** Vào chi tiết lớp → tab **Bài kiểm tra** → bấm **Tạo bài kiểm tra định thời**. Chọn *Chọn bộ câu hỏi Quiz \**, nhập *Tiêu đề bài kiểm tra \**, đặt *Thời gian mở \** (hiện tại hoặc vừa qua 1 phút) và *Thời gian đóng \** (sau 30 phút), *Thời lượng (phút)* = `5`, *Số lần làm tối đa* = `1`, tick **Tự động xáo trộn thứ tự câu hỏi cho từng sinh viên** → bấm **Xác nhận tạo bài kiểm tra**. | `POST /api/v1/classes/{id}/tests` (`201 Created`); enqueue `CLASS_TEST_CREATED` tới sinh viên trong lớp. | `STUDENT A` nhận thông báo chuông; trong lớp tab **Bài kiểm tra** hiện card với badge **Đang diễn ra** và nút **Bắt đầu làm bài**. |
| 2 | **`STUDENT A`:** Bấm **Bắt đầu làm bài** (mở `/tests/:testId/take`). Kiểm tra Network tab của request khởi tạo lượt làm bài và nội dung câu hỏi. | `POST /api/v1/tests/{id}/attempts` (`201 Created`) tính `deadlineAt = min(now + duration, endAt)`, xáo trộn thứ tự câu hỏi nếu bật `shuffleQuestions`; response **không** trả trường `isCorrect` / `explanation`. | Giao diện hiện badge **Bài kiểm tra định thời**, đồng hồ đếm ngược `MM:SS` (khi `<= 120s` đổi đỏ nhấp nháy kèm banner cảnh báo hết giờ) và tiến độ câu đã chọn. |
| 3 | **`STUDENT A`:** Chọn đủ đáp án các câu → bấm **Nộp bài thi** (hoặc **Hoàn thành & Nộp bài thi**) → xác nhận. | `POST /api/v1/tests/{id}/attempts/{attemptId}/submit` (kèm `Idempotency-Key`) chấm điểm thang `10.00` trên server. | Màn hình chuyển sang **Hoàn thành bài kiểm tra!** hiển thị *Điểm số đạt được*, *Số câu trả lời đúng*, *Tỷ lệ chính xác*. |
| 4 | **`TEACHER`:** Mở tab **Bài kiểm tra** (hoặc Sidebar → **Bài kiểm tra** `/tests`) → bấm **Xem bảng điểm lớp** / **Xem bảng điểm** (`/tests/:testId/results`). | `GET /api/v1/tests/{id}/results` tổng hợp `submittedCount`, `averageScore`, `highestScore`, `lowestScore`, `studentResults`. | Thấy 4 thẻ KPI (*Tỷ lệ đã nộp*, *Điểm trung bình*, *Điểm cao nhất*, *Điểm thấp nhất*) và dòng điểm của `STUDENT A` trong *Danh sách điểm sinh viên*. |

- **Nhánh lỗi / Edge-case:**
  - **Vào làm ngoài khung giờ** (tạo 1 bài test có *Thời gian mở* ở tương lai → badge **Sắp mở**, hoặc đã quá *Thời gian đóng* → badge **Đã kết thúc**): nếu gọi `POST /api/v1/tests/{id}/attempts` trả **`422 TEST_WINDOW_CLOSED`**; màn hình làm bài hiện *"Không thể làm bài kiểm tra"*.
  - **Vượt số lượt tối đa:** `STUDENT A` quay lại làm lần 2 khi `maxAttempts = 1` → trả **`422 MAX_ATTEMPTS_REACHED`**.
  - **Hết giờ làm bài:** Nếu đợi đồng hồ đếm ngược về `00:00`, UI tự nộp bài; nếu gửi submit trễ quá 30 giây ân hạn sau `deadlineAt`, server trả **`422 ATTEMPT_EXPIRED`**.
  - **`STUDENT A` truy cập `#/tests/{testId}/results`:** Bị chặn sang `/403`; API trả `403 FORBIDDEN`.

---

## Phần 5: Luồng Teacher ↔ Student — Lịch học, Sự kiện lớp & Google Meet

### `FLOW-11`: Tạo, cập nhật, hủy sự kiện lớp & Google Meet (Student Chỉ đọc)
- **Mục tiêu:** Kiểm tra đồng bộ sự kiện lớp lên lịch tổng hợp của sinh viên ở trạng thái chỉ đọc, mở link Google Meet qua trình duyệt mặc định và nhận thông báo khi sửa/hủy.
- **Điều kiện chuẩn bị:** Lớp đang `OPEN` có `TEACHER` và `STUDENT A`; Phiên 2 (`TEACHER`) và Phiên 3 (`STUDENT A`) đang mở.

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`TEACHER`:** Vào Sidebar → **Lịch** (`/calendar`) bấm **Tạo sự kiện lớp** (hoặc vào **Cuộc họp** `/meetings` bấm **Tạo cuộc họp Meet mới**). Chọn *Lớp học phần*, *Loại sự kiện* = **Cuộc họp (Google Meet)**, nhập *Tiêu đề*, *Bắt đầu*, *Kết thúc*, dán link `https://meet.google.com/abc-defg-hij` vào *Dán link Google Meet (thủ công)* → bấm **Lưu sự kiện lớp** / **Tạo cuộc họp**. | `POST /api/v1/classes/{id}/events` (`201 Created`); enqueue `CLASS_EVENT_CREATED` tới thành viên lớp qua WS. | `STUDENT A` nhận thông báo chuông (*"Lịch học / Cuộc họp mới"*). Trên **Tổng quan** (`/dashboard` mục *Lịch sắp tới 7 ngày*) và Sidebar → **Lịch học** (`/calendar` chuyển qua lại 3 chế độ **Ngày** / **Tuần** / **Tháng**), `STUDENT A` thấy sự kiện mới với badge **Cuộc họp** & **Sắp diễn ra**. |
| 2 | **`STUDENT A`:** Bấm vào sự kiện vừa thấy trên **Lịch học** để mở chi tiết, kiểm tra quyền sửa/xóa, sau đó bấm **Mở link Google Meet** (hoặc **Mở Google Meet** / **Mở Meet**). | UI nhận `readOnly = true` (ẩn các nút sửa/xóa); `openExternalBrowserUrl` gọi Electron IPC (`shell.openExternal`) hoặc `window.open` trên web. | Hộp thoại hiện cảnh báo **Chỉ đọc:** *"Sự kiện lớp học do giảng viên quản lý."*, không có nút sửa/hủy. Bấm nút mở Meet mở đúng link trong trình duyệt mặc định. |
| 3 | **`TEACHER`:** Mở lại sự kiện trên `/calendar` → bấm **Chỉnh sửa** → đổi giờ *Bắt đầu* / *Kết thúc* → bấm **Lưu sự kiện lớp**. | `PUT /api/v1/events/{id}` (kiểm tra `version`/`If-Match`); enqueue `CLASS_EVENT_UPDATED`. | `STUDENT A` nhận thông báo *"Cập nhật lịch học / Cuộc họp"*; tải lại **Lịch học** thấy khung giờ mới. |
| 4 | **`TEACHER`:** Mở lại sự kiện → bấm **Hủy sự kiện lớp** (hoặc bấm **Hủy lịch** trên `/meetings`). | `DELETE /api/v1/events/{id}` đặt `status = CANCELLED`; enqueue `CLASS_EVENT_CANCELLED`. | `STUDENT A` nhận thông báo *"Hủy lịch học / Cuộc họp"*; sự kiện đổi trạng thái **Đã hủy** / biến mất khỏi lịch sắp tới. |

- **Nhánh lỗi / Edge-case:**
  - `TEACHER` nhập link Meet không phải `http://` hoặc `https://` (ví dụ `ftp://...`) hoặc đặt *Kết thúc* <= *Bắt đầu*: trả `400 VALIDATION_ERROR`.
  - `STUDENT A` gọi API `PUT /api/v1/events/{id}` hoặc `DELETE /api/v1/events/{id}`: trả **`403 FORBIDDEN`**.

---

## Phần 6: Luồng Real-time Chat & Thông báo (`Teacher ↔ Student` & `Student ↔ Student`)

### `FLOW-12`: Chat phòng lớp, Chat 1-1, Trạng thái chưa đọc & Đánh dấu đã đọc
- **Mục tiêu:** Kiểm tra gửi/nhận tin nhắn tức thì qua WebSocket STOMP, bộ đếm chưa đọc và cơ chế đánh dấu đã đọc tự động.
- **Điều kiện chuẩn bị:** Phiên 2 (`TEACHER`), Phiên 3 (`STUDENT A`), Phiên 4 (`STUDENT B` — nếu muốn test 1-1 giữa 2 sinh viên). Đều đã kết nối WebSocket (`Đã kết nối trực tiếp`).

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`TEACHER`:** Vào **Tin nhắn** (`/messages`), chọn tab **Phòng lớp** → chọn phòng chat của lớp → nhập tin nhắn và nhấn `Enter` (hoặc bấm **Gửi**). | Client gửi STOMP `SEND /app/conversations/{id}/messages` kèm `clientMessageId`; backend broadcast tới `/topic/conversations/{id}`. | Tin nhắn của `TEACHER` hiện ở trạng thái **Đã gửi**; nếu `STUDENT A` chưa mở phòng chat đó, danh sách hội thoại bên `STUDENT A` tăng badge số chưa đọc (`1`, `2`, ...) theo thời gian thực. |
| 2 | **`STUDENT A`:** Bấm vào phòng chat lớp vừa có tin mới để xem, sau đó trả lời lại 1 tin nhắn. | Sau 500ms mở khung chat, client tự gọi `PUT /api/v1/conversations/{id}/read` với `lastReadMessageId`. | Badge số chưa đọc về `0`; bên `TEACHER` nhận được tin nhắn trả lời của `STUDENT A` tức thì. |
| 3 | **`TEACHER` (hoặc `STUDENT A`):** Bấm nút **Tin nhắn mới** → tại modal *Bắt đầu tin nhắn riêng 1-1*, chọn `STUDENT A` (hoặc `STUDENT B`) từ danh sách *Chọn nhanh thành viên cùng lớp* (hoặc nhập ID người dùng rồi bấm **Mở chat**) → gửi tin nhắn chào. | `POST /api/v1/conversations/direct` (`200 OK`) → gửi tin qua STOMP `/app/conversations/{id}/messages`. | Cuộc trò chuyện xuất hiện ở tab **Cá nhân** với badge **Tin nhắn riêng**; đối phương nhận tin nhắn tức thì. |

---

### `FLOW-13`: Mất mạng / Tải bù tin nhắn & Cài đặt kênh thông báo
- **Mục tiêu:** Xác nhận khả năng fallback REST, tự kết nối lại WebSocket tải bù tin nhắn và bộ lọc cài đặt thông báo từng loại sự kiện.
- **Điều kiện chuẩn bị:** Phiên 2 (`TEACHER`) và Phiên 3 (`STUDENT A`) đang mở **Tin nhắn**.

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`STUDENT A`:** Mở DevTools → tab **Network** → chuyển chế độ mạng sang **Offline** (hoặc ngắt mạng máy ảo). | `wsClient` chuyển sang `RECONNECTING`/`DISCONNECTED`. | Header hiện **Đang kết nối lại** / **Kết nối trực tiếp gặp lỗi**; khung chat hiện banner *"Kết nối trực tiếp đang gián đoạn. Tin nhắn gửi đi sẽ dùng kênh dự phòng REST và tự đồng bộ lại khi có mạng."* |
| 2 | **`TEACHER`:** Trong lúc `STUDENT A` mất mạng, gửi 2 tin nhắn mới vào phòng chat đang mở. | Backend lưu DB và broadcast qua WS. | Tin nhắn lưu thành công bên `TEACHER`. |
| 3 | **`STUDENT A`:** Chuyển Network về **No throttling** (có mạng lại). | `wsClient` tự reconnect STOMP (delay 5s); `MessagesPage` phát hiện chuyển sang `CONNECTED` và tự gọi `fetchConversations` + `loadLatestMessages` tải bù. | Header quay lại **Đã kết nối trực tiếp**; 2 tin nhắn gửi trong lúc mất mạng tự động xuất hiện đúng thứ tự thời gian mà không cần F5. |
| 4 | **`STUDENT A`:** Bấm chuông **Thông báo** → **Xem tất cả thông báo** → chuyển tab **Cài đặt thông báo** (`/notifications/settings`). Tắt công tắc **Trong ứng dụng** ở dòng sự kiện **Bài tập mới** (`ASSIGNMENT_CREATED`) → bấm **Lưu cài đặt**. | `PUT /api/v1/notifications/settings` lưu cấu hình `inAppEnabled = false` cho `ASSIGNMENT_CREATED`. | Hiện thông báo lưu cài đặt thành công. |
| 5 | **`TEACHER`:** Tạo một bài tập mới trong lớp (như `FLOW-09` bước 1). | `NotificationOutboxService` kiểm tra setting `inAppEnabled = false` của `STUDENT A` nên không tạo bản ghi `IN_APP` cho loại sự kiện này. | `STUDENT A` **không** nhận thông báo chuông/toast cho bài tập mới vừa tạo, nhưng vẫn thấy bài tập đó khi mở tab **Bài tập**. *(Sau bước này nhớ bật lại công tắc để nhận thông báo bình thường).* |

---

## Phần 7: Luồng tự học cá nhân & AI của Student (Khép kín)

### `FLOW-14`: Khóa học → Chương/Chủ đề → Upload PDF/DOCX (`EXTRACTED` vs `FAILED`)
- **Mục tiêu:** Kiểm tra cấu trúc phân cấp học liệu cá nhân và pipeline trích xuất văn bản bất đồng bộ.
- **Điều kiện chuẩn bị:** Phiên 3 (`STUDENT A`) đang đăng nhập. Chuẩn bị 1 file PDF/DOCX có chữ rõ ràng (`<= 20MB`) và 1 file PDF scan chỉ chứa ảnh (hoặc file PDF rỗng/hỏng).

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`STUDENT A`:** Vào Sidebar → **Tài liệu AI** (`/ai-materials` hoặc `/courses`) → bấm **Tạo khóa học mới** → nhập tên môn học, mô tả → bấm **Tạo khóa học** → bấm **Chi tiết** (`/courses/:courseId`). | `POST /api/v1/courses` (`201 Created`). | Mở trang chi tiết khóa học. |
| 2 | **`STUDENT A`:** Tại khung *Chương & Chủ đề*, bấm **Thêm chương** → lưu chương; sau đó bấm biểu tượng thêm chủ đề con trên chương vừa tạo → **Lưu chủ đề**. | `POST /api/v1/courses/{id}/chapters`, `POST /api/v1/chapters/{id}/topics`. | Cây *Chương & Chủ đề* hiển thị đúng phân cấp. |
| 3 | **`STUDENT A`:** Bấm **Tải tài liệu** → chọn file PDF/DOCX có chữ, chọn *Loại tài liệu* (vd *Giáo trình*), gắn vào chủ đề vừa tạo → bấm **Tải lên ngay**. Làm tương tự với file PDF scan/rỗng. | `POST /api/v1/courses/{id}/materials` (`201 Created`); backend trích xuất text bất đồng bộ; UI tự poll `GET /api/v1/courses/{id}/materials` mỗi 3 giây khi còn trạng thái `EXTRACTING`. | File có chữ chuyển **Đang trích xuất** → **Sẵn sàng** (`EXTRACTED`) kèm nút **Học với AI**. File PDF scan/rỗng chuyển thành **Lỗi trích xuất** (`FAILED`) kèm nút **Thử lại** (`reextract`). |

---

### `FLOW-15`: Tóm tắt AI (Async Job) & Hỏi đáp Grounded RAG (Trích nguồn trang + Câu ngoài tài liệu)
- **Mục tiêu:** Kiểm tra cơ chế hàng đợi `ai_jobs` không treo giao diện và tính trung thực bám sát tài liệu của RAG.
- **Điều kiện chuẩn bị:** Tài liệu vừa upload ở `FLOW-14` đã đạt trạng thái **Sẵn sàng** (`EXTRACTED`).

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`STUDENT A`:** Bấm **Học với AI** (`/materials/:materialId`). Mở tab **AI Tóm tắt** → chọn mức **Ngắn gọn** / **Vừa phải** / **Chi tiết** → bấm **Tạo tóm tắt bằng AI**. | `POST /api/v1/ai/summaries` (`202 Accepted`) trả `jobId`; `useAiJob` theo dõi qua `/user/queue/ai-jobs` + poll `GET /api/v1/ai/jobs/{jobId}` mỗi 2s. | UI hiện trạng thái *"Đang xếp hàng chờ xử lý..."* → *"AI đang xử lý nội dung..."* (có nút **Hủy** hoạt động không treo UI) → hiển thị bản tóm tắt và lưu vào *Lịch sử các bản tóm tắt*. |
| 2 | **`STUDENT A`:** Chuyển sang tab **Hỏi đáp Grounded** → bấm `+` tạo phiên mới → đặt 1 câu hỏi có nội dung **nằm trong tài liệu** (tắt tùy chọn *Mở rộng kiến thức ngoài*). | `POST /api/v1/ai/chats/{chatId}/messages` truy xuất chunk liên quan và sinh câu trả lời kèm danh sách `sources`. | Câu trả lời hiển thị mục **Nguồn trích dẫn:** kèm badge `{tên file} (Trang X)`. |
| 3 | **`STUDENT A`:** Đặt 1 câu hỏi **hoàn toàn ngoài tài liệu** (vd *"Giá vàng hôm nay là bao nhiêu?"*) khi vẫn **tắt** *Mở rộng kiến thức ngoài*. | Backend không tìm thấy ngữ cảnh phù hợp → trả `grounded = false`, `sources = []`, nội dung cố định: `"Tài liệu hiện tại chưa có thông tin để kết luận"`. | Giao diện hiện banner cảnh báo màu vàng *"Tài liệu hiện tại chưa có thông tin rõ ràng về câu hỏi này."* cùng đúng câu trả lời **`"Tài liệu hiện tại chưa có thông tin để kết luận"`** (có thể bật thử *Mở rộng kiến thức ngoài* ở tab **Giải thích nhanh** để kiểm tra nhãn **Kiến thức mở rộng**). |

---

### `FLOW-16`: Tự sinh Quiz, Làm bài & Ôn tập Flashcards 4 mức SM-2
- **Mục tiêu:** Kiểm tra sinh Quiz & Flashcard bằng AI, tự động lưu nháp Quiz, chấm điểm và thuật toán lặp lại ngắt quãng SM-2 4 mức.
- **Điều kiện chuẩn bị:** Có ít nhất 1 tài liệu `EXTRACTED`.

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`STUDENT A`:** Tại `/courses/:courseId`, bấm **Tạo Quiz bằng AI** → điền tiêu đề, số câu (`5 câu`), độ khó (**Vừa**), chọn tài liệu nguồn → bấm **Tạo Quiz ngay**. Chờ job hoàn tất rồi bấm **Làm bài** (`/quizzes/:quizId/take`). | `POST /api/v1/ai/quizzes` (`202 Accepted`) → `POST /api/v1/quizzes/{id}/attempts`; mỗi lần chọn đáp án gọi `PUT /api/v1/quizzes/{id}/attempts/{attemptId}` lưu nháp. | Chọn đáp án thấy hiện *"Đang lưu nháp..."*; bấm **Nộp bài ngay** (`POST .../submit` có `Idempotency-Key`) → chuyển sang `/attempts/:attemptId` hiện điểm số, badge **Đạt yêu cầu** / **Cần ôn tập thêm** và **Chi tiết đáp án & Lời giải thích**. |
| 2 | **`STUDENT A`:** Vào Sidebar → **Flashcards** (`/flashcards`) → bấm **Tạo thẻ bằng AI** → chọn môn học, số thẻ (`5 thẻ`), tài liệu nguồn → bấm **Sinh bộ thẻ ngay**. | `POST /api/v1/ai/flashcards` (`202 Accepted`). | Xuất hiện bộ thẻ mới với số *Cần ôn hôm nay* = `5 thẻ`. |
| 3 | **`STUDENT A`:** Bấm **Ôn tập ngay** (`/flashcards/:deckId/review`). Nhấn `Space` hoặc bấm **Lật thẻ xem đáp án (Space)** để xem mặt sau, lần lượt đánh giá các thẻ bằng 4 nút/phím tắt `1`–`4`: **1: Quên**, **2: Khó**, **3: Tạm**, **4: Dễ**. | `POST /api/v1/flashcards/{id}/reviews` tính `intervalDays`, `easeFactor`, `nextReviewAt` theo SM-2 (`Quên`: +10 phút, `Khó`: +1 ngày, `Tạm`: +1/6 ngày, `Dễ`: +4/10 ngày). | Thẻ đánh giá **1: Quên** được hẹn ôn lại sớm (+10 phút); hoàn tất hết bộ thẻ hiện màn hình **Hoàn thành mục tiêu ôn tập!** và quay về `/flashcards` nút chuyển thành **Đã ôn xong hôm nay**. |

---

### `FLOW-17`: Import lịch `.ics`/CSV trùng giờ, Google Calendar & Lộ trình AI → Nhiệm vụ ngày
- **Mục tiêu:** Kiểm tra xem trước phát hiện trùng giờ khi import lịch, đồng bộ Google Calendar chỉ đọc, và vòng đời *Mục tiêu → Lộ trình AI đề xuất → Duyệt từng mục → Nhiệm vụ theo ngày*.
- **Điều kiện chuẩn bị:** Phiên 3 (`STUDENT A`) đã có ít nhất 1 sự kiện cá nhân hoặc sự kiện lớp vào khung giờ `09:00 - 11:00` ngày mai để test trùng lịch.

| Bước | Vai trò & Thao tác cụ thể (Menu → Nút bấm) | Hệ thống xử lý (API / WebSocket / DB) | Kết quả phải thấy ở vai trò đối ứng (Tiêu chí PASS) |
| :--- | :--- | :--- | :--- |
| 1 | **`STUDENT A`:** Vào **Lịch học** (`/calendar`) → bấm **Nhập .ics / CSV** → bấm **Tải tệp CSV mẫu** (`calendar-template.csv`). Sửa tệp mẫu thêm 1 dòng trùng giờ `09:00 - 11:00` ngày mai và 1 dòng không trùng → chọn tệp → bấm **Phân tích & Xem trước**. | `POST /api/v1/calendar/imports/preview` (`201 Created`, tối đa 5MB) phân tích và đánh dấu `hasConflict = true` + `conflictingEvents`. | Màn hình bước 2 hiển thị badge `{N} mục trùng giờ`, dòng trùng giờ có badge **Trùng giờ** kèm hộp *Trùng thời gian với: ...* và mặc định bỏ chọn dòng trùng. Tick/bỏ tick theo ý muốn → bấm **Xác nhận nhập** (`POST .../confirm`) thấy sự kiện đã chọn xuất hiện trên lịch. |
| 2 | **`STUDENT A`:** Bấm **Google Calendar** → bấm **Kết nối Google Calendar** → cấp quyền `calendar.readonly` → bấm **Đồng bộ ngay**. | `GET /api/v1/integrations/google/authorize` → `callback` → `POST /api/v1/integrations/google/sync` (chỉ đọc). | Sự kiện từ Google Calendar hiện trên lịch kèm badge **Google**; mở chi tiết có nhãn **Chỉ đọc** (không sửa/xóa được trên app và không ghi ngược lên Google). |
| 3 | **`STUDENT A`:** Vào Sidebar → **Kế hoạch học** (`/study-plan`) → tab **Mục tiêu học tập** → bấm **Tạo mục tiêu mới** (chọn môn, ngày rảnh `T2..CN`, số giờ rảnh/ngày) → bấm **Đề xuất lộ trình AI cho mục tiêu này** → chọn *Từ ngày* / *Đến ngày* → bấm **Tạo đề xuất lộ trình**. | `POST /api/v1/goals` → `POST /api/v1/ai/study-plans` (`202 Accepted`) chạy qua `useAiJob`. | Tự động chuyển sang tab **Lộ trình AI đề xuất**; khi xong hiển thị danh sách buổi học đề xuất kèm *Lý do sắp xếp lộ trình từ AI* (và cảnh báo **Trùng giờ lịch hiện có** nếu đụng lịch). |
| 4 | **`STUDENT A`:** Trên các mục trạng thái **Chờ duyệt**, thực hiện thử cả 3 thao tác: bấm **Chấp nhận** ở mục 1, bấm **Chỉnh sửa & Chấp nhận** → sửa giờ/tiêu đề → **Lưu & Chấp nhận** ở mục 2, bấm **Bỏ qua** ở mục 3. Sau đó chuyển sang tab **Nhiệm vụ theo ngày**. | `PUT /api/v1/study-plans/{id}/items/{itemId}` (`ACCEPTED` / `EDITED` / `REJECTED`); chỉ các mục `ACCEPTED` và `EDITED` sinh bản ghi mới trong bảng `tasks`. | Hai mục được chấp nhận/chỉnh sửa tự động xuất hiện trong tab **Nhiệm vụ theo ngày** (và mục *Nhiệm vụ hôm nay* trên Dashboard nếu đúng ngày); mục bị **Bỏ qua** không sinh Task; có thể tick hoàn thành nhiệm vụ (`PUT /api/v1/tasks/{id}/complete`). |

---

## Bảng tổng hợp Checklist nghiệm thu nhanh

| Nhóm | Mã Flow | Nội dung kiểm thử trọng tâm | Kết quả | Ghi chú |
| :--- | :--- | :--- | :---: | :--- |
| **Môi trường** | `SETUP` | Chạy Docker MySQL + Backend `8080` + Desktop `5173` với 4 phiên (`ADMIN`, `TEACHER`, `STUDENT A`, `STUDENT B`) kết nối WS | `[ ]` | |
| **Phần 1** | `FLOW-01` | Admin đổi vai trò `STUDENT` → `TEACHER`, user đăng nhập lại đổi bộ menu Sidebar | `[ ]` | |
| **Phần 1** | `FLOW-02` | Admin khóa (`LOCKED`) & mở khóa (`ACTIVE`) tài khoản; kiểm tra chặn `403` / `401` | `[ ]` | |
| **Phần 1** | `FLOW-03` | Admin đổi trạng thái lớp (`CLOSED`/`ARCHIVED`) tác động tới Teacher & chặn `STUDENT B` xin vào | `[ ]` | |
| **Phần 1** | `FLOW-04` | Admin chỉnh `AI_DAILY_QUOTA_PER_USER` (`429`), `MAX_UPLOAD_FILE_SIZE_MB`, `GOOGLE_ALLOW_PERSONAL_ACCOUNTS` | `[ ]` | |
| **Phần 1** | `FLOW-05` | Student báo AI sai + Gửi báo cáo / Góp ý ⇒ Admin xử lý & kiểm tra Audit Log | `[ ]` | |
| **Phần 2** | `FLOW-06` | Teacher tạo lớp ⇒ Student A xin vào ⇒ Teacher Duyệt ⇒ WS thông báo + tự vào Phòng chat lớp | `[ ]` | |
| **Phần 2** | `FLOW-07` | Teacher đăng Bảng tin & Tài liệu lớp ⇒ Student A tải được, Student B ngoài lớp bị chặn `403` | `[ ]` | |
| **Phần 2** | `FLOW-08` | Teacher gỡ Student A ⇒ mất quyền lớp & khóa ngay khung chat lớp (`403`) | `[ ]` | |
| **Phần 3** | `FLOW-09` | Bài tập: nộp 1 lần (`409` lần 2), chấm điểm + nhận xét, mở lại nộp, test quá hạn bật/tắt nộp muộn (`422`) | `[ ]` | |
| **Phần 4** | `FLOW-10` | `ClassTest`: tạo từ Quiz, làm bài đếm ngược không lộ đáp án, tổng hợp điểm, chặn ngoài giờ/vượt lượt (`422`) | `[ ]` | |
| **Phần 5** | `FLOW-11` | Sự kiện lớp & Google Meet: Student chỉ đọc, mở link Meet ngoài app, Teacher sửa/hủy báo real-time | `[ ]` | |
| **Phần 6** | `FLOW-12` | Chat phòng lớp & 1-1 qua WebSocket, đếm chưa đọc, tự đánh dấu đã đọc | `[ ]` | |
| **Phần 6** | `FLOW-13` | Mất mạng → tự reconnect & tải bù tin nhắn; tắt 1 loại trong *Cài đặt thông báo* có hiệu lực | `[ ]` | |
| **Phần 7** | `FLOW-14` | Khóa học → Chương → Chủ đề → Upload PDF/DOCX (`EXTRACTED`) & PDF scan/rỗng (`FAILED`) | `[ ]` | |
| **Phần 7** | `FLOW-15` | AI Tóm tắt (Async Job không treo UI) & Hỏi đáp Grounded có trích trang + câu ngoài tài liệu | `[ ]` | |
| **Phần 7** | `FLOW-16` | Tự sinh Quiz + lưu nháp + chấm điểm; Flashcards + ôn 4 mức (*Quên/Khó/Tạm/Dễ*) tính lịch SM-2 | `[ ]` | |
| **Phần 7** | `FLOW-17` | Import `.ics`/CSV cảnh báo trùng giờ, Google Calendar chỉ đọc, Lộ trình AI duyệt từng mục → Task ngày | `[ ]` | |
