# AI Study Assistant (V2 — Modular Monolith)

Ứng dụng Desktop hỗ trợ học tập và giảng dạy (**Organize → Understand → Practice → Evaluate → Improve**) tích hợp AI bám sát tài liệu, lớp học trực tuyến, bài tập, bài kiểm tra, thời khóa biểu, chat thời gian thực và thông báo đa kênh.

---

## 1. Tổng quan kiến trúc

- **Mô hình:** **Modular Monolith** — Desktop Client gọi REST API & WebSocket STOMP tới Backend độc lập (tuyệt đối không nhúng JDK/Spring Boot/MySQL vào bộ cài Electron).
- **Frontend (`/desktop`):** Electron + React + TypeScript + Tailwind CSS + `shadcn/ui` (Radix UI).
- **Backend (`/backend`):** Java 17 + Spring Boot 3.2.5 + Spring Security (JWT + Refresh Token xoay vòng) + Spring Data JPA + WebSocket STOMP + Apache PDFBox / Apache POI + MySQL 8.0 + Flyway.
- **Database (`/backend/src/main/resources/db/migration`):** MySQL 8.0 (`utf8mb4_unicode_ci`), quản lý phiên bản schema hoàn toàn bằng **Flyway**.

### Cấu trúc 9 module nghiệp vụ trong `/backend` (`com.aistudy.assistant`)

| Module | Trách nhiệm |
| :--- | :--- |
| `common` | Base entities (`id`, `createdAt`, `updatedAt`, `deletedAt`, `version`), `ApiResponse<T>` V2, `ErrorCode`, `AppException`, `GlobalExceptionHandler`, `TraceIdFilter`, `AsyncConfig`, tiện ích bảo mật |
| `identity` | Đăng ký, đăng nhập, xoay vòng refresh token, khóa tạm tài khoản, hồ sơ người dùng, quản trị tài khoản & phân quyền |
| `learning` | Khóa học, chương, chủ đề, tài liệu học tập, trích xuất & chia chunk, quiz/lượt làm bài, flashcard, thống kê & gợi ý ôn tập |
| `ai` | Hàng đợi tác vụ AI (`ai_jobs`), worker bất đồng bộ, tóm tắt, giải thích, hỏi đáp bám sát tài liệu có trích nguồn, sinh quiz/flashcard/lộ trình, báo cáo câu trả lời sai, tích hợp AI Provider |
| `classroom` | Lớp học, mã lớp, yêu cầu tham gia (`PENDING/APPROVED/REJECTED`), thành viên, đồng giảng dạy, bảng tin, tài liệu lớp |
| `assignment` | Bài tập, nộp bài (giờ máy chủ, chống nộp lặp), chấm điểm/trả bài, bài kiểm tra định thời |
| `schedule` | Thời khóa biểu, import `.ics`/CSV xem trước & phát hiện trùng giờ, sự kiện cá nhân, đồng bộ Google Calendar (chỉ đọc), mục tiêu & task |
| `meeting` | Sự kiện học tập/kiểm tra/họp lớp, lưu đường dẫn Meet |
| `chat` | Chat 1-1 và phòng chat lớp thời gian thực (WebSocket STOMP + REST fallback), phân trang cursor, trạng thái đã đọc |
| `notification` | Transactional Outbox (`IN_APP`, `EMAIL`), worker gửi & retry có chống trùng, cấu hình thông báo, nhắc hạn |

---

## 2. Bộ luật cứng Backend (`/backend`)

1. **Quản lý Database bằng Flyway & chuẩn UTC:**
    - Bắt buộc `spring.jpa.hibernate.ddl-auto: validate` và `spring.jpa.open-in-view: false`. Mọi thay đổi schema chỉ thực hiện qua file migration Flyway (`V{n}__{mo_ta}.sql`).
    - Mọi bảng đều có `created_at`, `updated_at` kiểu `TIMESTAMP(3)` lưu chuẩn **UTC** (`TimeZone.setDefault("UTC")`, `hibernate.jdbc.time_zone: UTC`).
    - Các bảng có thể cập nhật đồng thời phải có cột `version` (`@Version` optimistic locking).
    - `courses`, `materials`, `quizzes` sử dụng xóa mềm (`deleted_at`) để bảo toàn lịch sử điểm và truy vết.
2. **Phân lớp nghiêm ngặt:**
    - Tách rõ `Controller → Service → Repository → Entity / DTO / Mapper` (và `integration` cho dịch vụ ngoài).
    - **Tuyệt đối không trả JPA `@Entity` ra REST API**, luôn ánh xạ sang DTO. Controller không chứa logic nghiệp vụ.
3. **Quản trị Transaction:**
    - **Tuyệt đối không giữ `@Transactional` mở** khi gọi AI Provider, Google Calendar API, gửi Email SMTP hoặc đọc/ghi file vật lý. Tác vụ nặng phải chạy ngoài transaction (hoặc qua `ai_jobs` / `notification_outbox`).
4. **Chống N+1 Query:**
    - Dùng DTO Projection cho truy vấn danh sách/thống kê.
    - Dùng `@EntityGraph` hoặc `JOIN FETCH` cho quan hệ to-one khi cần; giữ `hibernate.default_batch_fetch_size: 30`.
    - **Không** dùng `JOIN FETCH` trên collection đi kèm phân trang (`Pageable`). Phân trang luôn thực hiện ở tầng database.
5. **Chống Race Condition:**
    - Ràng buộc duy nhất (`UNIQUE`) ở mức database là chốt chặn cuối (`email`, `idempotency_key`, `class_member`, `submission`, `notification_outbox`...).
    - Dùng `@Version` (trả `409 Conflict` khi xung đột), `Idempotency-Key` cho thao tác gửi lại (nộp quiz, nộp bài, tạo sự kiện, gửi tin nhắn).
    - Giờ máy chủ quyết định hạn nộp, trạng thái muộn (`is_late`) và thời gian làm bài kiểm tra.
6. **Chuẩn phản hồi `ApiResponse<T>` V2 & Bảo mật lỗi:**
    - Mọi phản hồi REST dùng `ApiResponse<T>` gồm 5 trường: `success`, `data`, `meta`, `error`, `traceId`.
    - Mọi lỗi qua `GlobalExceptionHandler` chuẩn hóa theo `ErrorCode`, gắn `traceId` từ `TraceIdFilter` (`X-Trace-Id`), **tuyệt đối không lộ stack trace, tên bảng hay thông báo SQL nội bộ**.
    - API Key AI, Google OAuth Secret, SMTP credentials chỉ tồn tại ở Backend.

---

## 3. Bộ luật cứng Frontend (`/desktop`)

1. **TypeScript nghiêm ngặt:** Cấm dùng `any`. Định nghĩa đầy đủ type/interface khớp với `ApiResponse<T>` V2 và DTO của Backend.
2. **UI & Ngôn ngữ:** Ưu tiên hệ thành phần **`shadcn/ui` + Radix UI + Tailwind CSS**. Sử dụng **tiếng Việt phổ thông** nhất quán, rõ trạng thái tải / rỗng / lỗi / thành công, hỗ trợ bàn phím.
3. **Xác thực & Làm mới Token:** HTTP client tập trung tự động gắn `Authorization: Bearer <accessToken>`, tự động gọi `POST /api/v1/auth/refresh` khi gặp `401` (có hàng đợi chống gọi refresh trùng lặp) và chuyển về đăng nhập nếu refresh thất bại.
4. **Tác vụ AI & Electron Security:** Các thao tác AI bất đồng bộ hiển thị trạng thái chờ, chống bấm gửi trùng lặp, xử lý timeout/lỗi rõ ràng. Luôn giữ `contextIsolation: true` và `nodeIntegration: false`.

---

## 4. Quy chuẩn code

- **Thụt lề:** Bắt buộc **4 spaces** trên toàn bộ mã nguồn (Java, TypeScript, JSON, YAML, SQL, CSS).
- **Không placeholder:** Không để `TODO` bỏ ngỏ hay code mẫu rỗng khi triển khai module.
- **Đặt tên:** Java (`PascalCase` / `camelCase`), Database (`snake_case`), REST URI (`kebab-case`, số nhiều cho resource).

---

## 5. Chạy nhanh cục bộ (Docker + Flyway + Maven Wrapper)

### Bước 1: Chuẩn bị biến môi trường
```powershell
Copy-Item .env.example .env
# Cập nhật MYSQL_*, JWT_SECRET, GEMINI_API_KEY trong .env
```

### Bước 2: Khởi động MySQL 8.0 bằng Docker
```powershell
docker compose up -d
docker compose ps
```
*(MySQL chạy ở cổng `3307` mặc định, UTF-8 `utf8mb4_unicode_ci`, múi giờ UTC; schema được Flyway tự động migrate khi Backend khởi động).*

### Bước 3: Biên dịch & chạy Backend với Maven Wrapper
```powershell
cd backend
.\mvnw.cmd test-compile
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- API Base URL: `http://localhost:8080/api/v1`

### Bước 4: Chạy Desktop Client (`/desktop`)
```powershell
cd desktop
npm install
npm run electron:dev
```
