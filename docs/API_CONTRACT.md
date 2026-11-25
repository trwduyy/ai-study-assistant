# AI Study Assistant — API Contract (V2)

Tài liệu hợp đồng API chính thức cho kiến trúc **Modular Monolith V2** (Đợt 1 chi tiết & Lộ trình Đợt 2–6).

---

## Phần 1: Quy ước chung V2

- **REST Base URL:** `http://localhost:8080/api/v1` (Production: `https://{host}/api/v1`)
- **WebSocket STOMP Endpoint:** `ws://localhost:8080/ws` (Production: `wss://{host}/ws`)
    - Kênh gửi (`SEND`): `/app/conversations/{id}/messages`
    - Kênh nhận (`SUBSCRIBE`): `/user/queue/ai-jobs`, `/user/queue/notifications`, `/topic/conversations/{id}`
    - Xác thực kết nối STOMP bằng header `Authorization: Bearer <accessToken>` trong `CONNECT` frame.
- **Định dạng dữ liệu:**
    - JSON UTF-8, thụt lề chuẩn **4 spaces**.
    - Thời gian chuẩn **ISO-8601 UTC**: `yyyy-MM-dd'T'HH:mm:ss'Z'` hoặc có mili-giây `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` (ví dụ: `2026-11-25T08:30:00Z`).
    - Khóa chính dạng số 64-bit (`Long`) cho hầu hết tài nguyên; UUID v4 (`String`, 36 ký tự) cho `ai_jobs.id` và `Idempotency-Key`.
- **Xác thực (JWT + Refresh Token xoay vòng):**
    - Header: `Authorization: Bearer <accessToken>`
    - `accessToken`: thời hạn ngắn **15 phút** (`900` giây).
    - `refreshToken`: thời hạn **7 ngày** (`604800` giây), xoay vòng khi làm mới (`POST /auth/refresh`) — token cũ bị thu hồi ngay sau khi cấp cặp mới.
- **Phân trang:**
    - **Offset-based** (danh sách khóa học, tài liệu, quiz, lịch sử làm bài, người dùng):
      `?page=0&size=20&sort=createdAt,desc`
      Trả về `meta`: `page`, `size`, `totalElements`, `totalPages`, `nextCursor: null`.
    - **Cursor-based** (tin nhắn chat, thông báo, lịch sử chat AI):
      `?cursor=<id>&limit=30`
      Trả về `meta`: `size`, `nextCursor` (null nếu đã hết dữ liệu).
- **Chống xử lý lặp (`Idempotency-Key`):**
    - Header: `Idempotency-Key: <uuid>` bắt buộc với thao tác nhạy cảm gửi lại: nộp bài quiz (`POST /quizzes/{id}/attempts/{attemptId}/submit`), nộp bài tập, tạo sự kiện, gửi tin nhắn REST.
    - Nếu gửi lại cùng `Idempotency-Key`, cùng `userId` và `endpoint`, hệ thống trả về kết quả đã xử lý trước đó mà không tạo bản ghi trùng.
- **Khóa đồng thời lạc quan (`version` / `If-Match`):**
    - Các tài nguyên có `version` (user profile, course, quiz, quiz attempt, assignment, event) khi `PUT`/`PATCH` gửi `version` trong body hoặc header `If-Match: "<version>"`.
    - Nếu `version` không khớp với DB, Backend trả `409 Conflict` (`VERSION_CONFLICT`).
- **Tác vụ AI bất đồng bộ:**
    - Các endpoint sinh nội dung AI nặng (`POST /ai/summaries`, `POST /ai/explanations`, `POST /ai/quizzes`) trả `202 Accepted` kèm `jobId` và `status: "PENDING"`.
    - Client theo dõi bằng polling `GET /ai/jobs/{jobId}` hoặc lắng nghe sự kiện WebSocket `/user/queue/ai-jobs`.
- **Upload file:** `multipart/form-data`, kiểm tra extension (`PDF`, `DOCX`, `TXT`), MIME type thực tế, giới hạn dung lượng (mặc định 20MB) và làm sạch tên file ở Backend.

### 1.1 Cấu trúc phản hồi chuẩn `ApiResponse<T>` V2

#### Thành công (có hoặc không có `meta`)
```json
{
    "success": true,
    "data": {
        "id": 12
    },
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 135,
        "totalPages": 7,
        "nextCursor": null
    },
    "traceId": "b7c14a9e82f04d31"
}
```
*(Trường `meta` chỉ xuất hiện ở các endpoint có phân trang; trường `error` được lược bỏ khi thành công).*

#### Lỗi (chuẩn hóa từ `GlobalExceptionHandler`, tuyệt đối không lộ stack trace hay SQL nội bộ)
```json
{
    "success": false,
    "error": {
        "code": "VALIDATION_ERROR",
        "message": "Dữ liệu đầu vào không hợp lệ.",
        "fields": [
            {
                "field": "email",
                "message": "Email không đúng định dạng"
            }
        ]
    },
    "traceId": "b7c14a9e82f04d31"
}
```

### 1.2 Bảng mã HTTP & Mã lỗi nghiệp vụ

| HTTP | Ý nghĩa | Mã lỗi nghiệp vụ (`error.code`) |
| :--- | :--- | :--- |
| `200` | Thành công | — |
| `201` | Tạo mới thành công | — |
| `202` | Đã tiếp nhận tác vụ bất đồng bộ | — |
| `400` | Dữ liệu không hợp lệ / file sai định dạng | `VALIDATION_ERROR`, `BAD_REQUEST`, `FILE_TYPE_NOT_ALLOWED`, `FILE_TOO_LARGE`, `INVALID_FILE_NAME` |
| `401` | Chưa đăng nhập / token sai hoặc hết hạn | `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `TOKEN_INVALID`, `REFRESH_TOKEN_INVALID`, `BAD_CREDENTIALS` |
| `403` | Không đủ quyền / tài khoản bị khóa | `FORBIDDEN`, `NOT_RESOURCE_OWNER`, `NOT_CLASS_MEMBER`, `ACCOUNT_LOCKED`, `ACCOUNT_DISABLED` |
| `404` | Không tìm thấy tài nguyên | `NOT_FOUND`, `USER_NOT_FOUND`, `COURSE_NOT_FOUND`, `CHAPTER_NOT_FOUND`, `TOPIC_NOT_FOUND`, `MATERIAL_NOT_FOUND`, `QUIZ_NOT_FOUND`, `ATTEMPT_NOT_FOUND`, `AI_JOB_NOT_FOUND`, `CLASS_CODE_INVALID` |
| `409` | Xung đột dữ liệu / phiên bản / trùng lặp | `CONFLICT`, `EMAIL_EXISTS`, `ASSIGNMENT_ALREADY_SUBMITTED`, `QUIZ_ALREADY_SUBMITTED`, `VERSION_CONFLICT`, `JOIN_REQUEST_EXISTS`, `ALREADY_CLASS_MEMBER`, `SCHEDULE_CONFLICT`, `IDEMPOTENCY_CONFLICT` |
| `422` | Không thể xử lý theo quy tắc nghiệp vụ | `UNPROCESSABLE_ENTITY`, `ASSIGNMENT_CLOSED`, `TEXT_NOT_EXTRACTED`, `SCANNED_DOCUMENT_UNSUPPORTED`, `AI_OUTPUT_INVALID`, `ATTEMPT_EXPIRED`, `MAX_ATTEMPTS_REACHED` |
| `429` | Vượt giới hạn tần suất / hạn mức AI (kèm `Retry-After`) | `RATE_LIMITED`, `AI_QUOTA_EXCEEDED` |
| `502` | Dịch vụ ngoài (AI Provider, Google, SMTP) lỗi | `AI_PROVIDER_ERROR`, `GOOGLE_API_ERROR`, `EMAIL_DELIVERY_ERROR` |
| `504` | Dịch vụ ngoài quá thời gian chờ | `AI_TIMEOUT`, `EXTERNAL_API_TIMEOUT` |

---

## Phần 2: Chi tiết API các module Đợt 1

### 2.1 Identity & Auth (`/auth`, `/users`)

#### `POST /auth/register` — Đăng ký tài khoản (`Pub`)
- **Request:**
```json
{
    "email": "sv01@actvn.edu.vn",
    "password": "StrongPassword@123",
    "fullName": "Nguyễn Văn A"
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 12,
        "email": "sv01@actvn.edu.vn",
        "fullName": "Nguyễn Văn A",
        "role": "STUDENT",
        "status": "ACTIVE",
        "avatarUrl": null,
        "version": 0,
        "createdAt": "2026-11-25T08:30:00Z",
        "updatedAt": "2026-11-25T08:30:00Z"
    },
    "traceId": "9f12c0a17b8e4c11"
}
```

#### `POST /auth/login` — Đăng nhập (`Pub`)
- **Request:**
```json
{
    "email": "sv01@actvn.edu.vn",
    "password": "StrongPassword@123"
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
        "refreshToken": "d41f9b7e-6c2a-4f3b-9a1e-8c7d2f1b4e5a",
        "tokenType": "Bearer",
        "expiresIn": 900,
        "user": {
            "id": 12,
            "email": "sv01@actvn.edu.vn",
            "fullName": "Nguyễn Văn A",
            "role": "STUDENT",
            "status": "ACTIVE",
            "avatarUrl": null,
            "version": 0
        }
    },
    "traceId": "9f12c0a17b8e4c12"
}
```

#### `POST /auth/refresh` — Làm mới & xoay vòng Refresh Token (`Pub`)
- **Request:**
```json
{
    "refreshToken": "d41f9b7e-6c2a-4f3b-9a1e-8c7d2f1b4e5a"
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "accessToken": "eyJhbGciOiJIUzI1NiJ9.new...",
        "refreshToken": "7a31e0c4-2d88-4b71-8e1f-9b0d12a34c56",
        "tokenType": "Bearer",
        "expiresIn": 900
    },
    "traceId": "9f12c0a17b8e4c13"
}
```

#### `POST /auth/logout` — Đăng xuất & thu hồi Refresh Token (`All`)
- **Request:**
```json
{
    "refreshToken": "7a31e0c4-2d88-4b71-8e1f-9b0d12a34c56"
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": null,
    "traceId": "9f12c0a17b8e4c14"
}
```

#### `GET /users/me` — Xem hồ sơ cá nhân (`All`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "id": 12,
        "email": "sv01@actvn.edu.vn",
        "fullName": "Nguyễn Văn A",
        "role": "STUDENT",
        "status": "ACTIVE",
        "avatarUrl": "https://cdn.example.com/avatars/12.png",
        "version": 1,
        "createdAt": "2026-11-25T08:30:00Z",
        "updatedAt": "2026-11-25T09:00:00Z"
    },
    "traceId": "9f12c0a17b8e4c15"
}
```

#### `PUT /users/me` — Cập nhật hồ sơ cá nhân (`All`)
- **Request:**
```json
{
    "fullName": "Nguyễn Văn An",
    "avatarUrl": "https://cdn.example.com/avatars/12-v2.png",
    "version": 1
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "id": 12,
        "email": "sv01@actvn.edu.vn",
        "fullName": "Nguyễn Văn An",
        "role": "STUDENT",
        "status": "ACTIVE",
        "avatarUrl": "https://cdn.example.com/avatars/12-v2.png",
        "version": 2,
        "createdAt": "2026-11-25T08:30:00Z",
        "updatedAt": "2026-11-25T09:15:00Z"
    },
    "traceId": "9f12c0a17b8e4c16"
}
```

#### `PUT /users/me/password` — Đổi mật khẩu (`All`)
- **Request:**
```json
{
    "currentPassword": "StrongPassword@123",
    "newPassword": "NewStrongPassword@456"
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": null,
    "traceId": "9f12c0a17b8e4c17"
}
```

---

### 2.2 Course, Chapter & Topic (`/courses`, `/chapters`, `/topics`)

#### `GET /courses?page=0&size=20&sort=createdAt,desc` — Danh sách khóa học (`S`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 101,
            "name": "Mạng máy tính",
            "description": "Kiến trúc tầng giao vận, định tuyến và an toàn mạng",
            "chapterCount": 5,
            "materialCount": 14,
            "version": 0,
            "createdAt": "2026-11-25T08:35:00Z",
            "updatedAt": "2026-11-25T08:35:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1,
        "nextCursor": null
    },
    "traceId": "a1b2c3d4e5f60001"
}
```

#### `POST /courses` — Tạo khóa học mới (`S`)
- **Request:**
```json
{
    "name": "Mạng máy tính",
    "description": "Kiến trúc tầng giao vận, định tuyến và an toàn mạng"
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 101,
        "name": "Mạng máy tính",
        "description": "Kiến trúc tầng giao vận, định tuyến và an toàn mạng",
        "chapterCount": 0,
        "materialCount": 0,
        "version": 0,
        "createdAt": "2026-11-25T08:35:00Z",
        "updatedAt": "2026-11-25T08:35:00Z"
    },
    "traceId": "a1b2c3d4e5f60002"
}
```

#### `GET /courses/{courseId}` — Chi tiết khóa học kèm danh sách chương & chủ đề (`S`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "id": 101,
        "name": "Mạng máy tính",
        "description": "Kiến trúc tầng giao vận, định tuyến và an toàn mạng",
        "version": 0,
        "createdAt": "2026-11-25T08:35:00Z",
        "updatedAt": "2026-11-25T08:35:00Z",
        "chapters": [
            {
                "id": 201,
                "courseId": 101,
                "title": "Chương 3: Tầng giao vận",
                "position": 1,
                "topics": [
                    {
                        "id": 301,
                        "chapterId": 201,
                        "title": "Giao thức TCP và kiểm soát tắc nghẽn",
                        "position": 1
                    }
                ]
            }
        ]
    },
    "traceId": "a1b2c3d4e5f60003"
}
```

#### `PUT /courses/{courseId}` — Cập nhật khóa học (`S`, kiểm tra `version` hoặc `If-Match`)
- **Request:**
```json
{
    "name": "Mạng máy tính nâng cao",
    "description": "Cập nhật nội dung TCP/UDP và bảo mật tầng giao vận",
    "version": 0
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "id": 101,
        "name": "Mạng máy tính nâng cao",
        "description": "Cập nhật nội dung TCP/UDP và bảo mật tầng giao vận",
        "chapterCount": 1,
        "materialCount": 2,
        "version": 1,
        "createdAt": "2026-11-25T08:35:00Z",
        "updatedAt": "2026-11-25T09:10:00Z"
    },
    "traceId": "a1b2c3d4e5f60004"
}
```

#### `DELETE /courses/{courseId}` — Xóa mềm khóa học (`S`, gán `deleted_at`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": null,
    "traceId": "a1b2c3d4e5f60005"
}
```

#### `GET /courses/{courseId}/chapters` & `POST /courses/{courseId}/chapters` (`S`)
- **Request (`POST`):**
```json
{
    "title": "Chương 3: Tầng giao vận",
    "position": 1
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 201,
        "courseId": 101,
        "title": "Chương 3: Tầng giao vận",
        "position": 1,
        "createdAt": "2026-11-25T08:40:00Z",
        "updatedAt": "2026-11-25T08:40:00Z"
    },
    "traceId": "a1b2c3d4e5f60006"
}
```

#### `PUT /chapters/{chapterId}` & `DELETE /chapters/{chapterId}` (`S`)
- **Request (`PUT`):**
```json
{
    "title": "Chương 3: Tầng giao vận (Transport Layer)",
    "position": 1
}
```
- **Response `200 OK`:** Trả về đối tượng `Chapter` (cho `PUT`) hoặc `data: null` (cho `DELETE`).

#### `GET /chapters/{chapterId}/topics` & `POST /chapters/{chapterId}/topics` (`S`)
- **Request (`POST`):**
```json
{
    "title": "Giao thức TCP và kiểm soát tắc nghẽn",
    "position": 1
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 301,
        "chapterId": 201,
        "title": "Giao thức TCP và kiểm soát tắc nghẽn",
        "position": 1,
        "createdAt": "2026-11-25T08:45:00Z",
        "updatedAt": "2026-11-25T08:45:00Z"
    },
    "traceId": "a1b2c3d4e5f60007"
}
```

#### `PUT /topics/{topicId}` & `DELETE /topics/{topicId}` (`S`)
- **Request (`PUT`):**
```json
{
    "title": "So sánh TCP và UDP",
    "position": 2
}
```
- **Response `200 OK`:** Trả về đối tượng `Topic` (cho `PUT`) hoặc `data: null` (cho `DELETE`).

---

### 2.3 Material & Async Extraction (`/courses/{courseId}/materials`, `/materials`)

#### `POST /courses/{courseId}/materials` — Tải tài liệu lên & kích hoạt trích xuất bất đồng bộ (`S`)
- **Content-Type:** `multipart/form-data`
- **Form Fields:**
    - `file`: Binary (`PDF`, `DOCX`, `TXT`, tối đa 20MB)
    - `topicId` (Long, optional)
    - `category` (String, optional: `SLIDE` | `LECTURE_NOTE` | `TEXTBOOK` | `EXERCISE` | `OTHER`)
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 401,
        "courseId": 101,
        "topicId": 301,
        "fileName": "Mang-may-tinh-chuong-3.pdf",
        "mimeType": "application/pdf",
        "sizeBytes": 2481920,
        "category": "SLIDE",
        "status": "EXTRACTING",
        "pageCount": null,
        "contentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "createdAt": "2026-11-25T09:00:00Z",
        "updatedAt": "2026-11-25T09:00:00Z"
    },
    "traceId": "b1c2d3e4f5a60001"
}
```

#### `GET /courses/{courseId}/materials?topicId=&category=&status=&page=0&size=20` — Danh sách tài liệu trong khóa học (`S`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 401,
            "courseId": 101,
            "topicId": 301,
            "fileName": "Mang-may-tinh-chuong-3.pdf",
            "mimeType": "application/pdf",
            "sizeBytes": 2481920,
            "category": "SLIDE",
            "status": "EXTRACTED",
            "pageCount": 42,
            "createdAt": "2026-11-25T09:00:00Z",
            "updatedAt": "2026-11-25T09:00:05Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1,
        "nextCursor": null
    },
    "traceId": "b1c2d3e4f5a60002"
}
```

#### `GET /materials/{id}` — Chi tiết tài liệu & trạng thái trích xuất (`S`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "id": 401,
        "courseId": 101,
        "topicId": 301,
        "fileName": "Mang-may-tinh-chuong-3.pdf",
        "mimeType": "application/pdf",
        "sizeBytes": 2481920,
        "category": "SLIDE",
        "status": "EXTRACTED",
        "pageCount": 42,
        "chunkCount": 18,
        "contentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "errorCode": null,
        "errorMessage": null,
        "createdAt": "2026-11-25T09:00:00Z",
        "updatedAt": "2026-11-25T09:00:05Z"
    },
    "traceId": "b1c2d3e4f5a60003"
}
```

#### `GET /materials/{id}/content?page=0&size=10` — Xem văn bản đã trích xuất theo chunk/trang (`S`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "chunkId": 880,
            "chunkIndex": 0,
            "pageFrom": 1,
            "pageTo": 3,
            "content": "Chương 3: Tầng giao vận cung cấp dịch vụ truyền thông logic giữa các tiến trình ứng dụng...",
            "tokenCount": 312
        }
    ],
    "meta": {
        "page": 0,
        "size": 10,
        "totalElements": 18,
        "totalPages": 2,
        "nextCursor": null
    },
    "traceId": "b1c2d3e4f5a60004"
}
```

#### `GET /materials/{id}/download` — Tải file gốc (`S`)
- **Response `200 OK`:** Binary Stream với header `Content-Disposition: attachment; filename="Mang-may-tinh-chuong-3.pdf"`.

#### `PUT /materials/{id}` — Cập nhật tên, phân loại hoặc chuyển chủ đề (`S`)
- **Request:**
```json
{
    "fileName": "Slide-Tang-Giao-Van.pdf",
    "topicId": 301,
    "category": "SLIDE"
}
```
- **Response `200 OK`:** Trả về thông tin tài liệu sau cập nhật.

#### `DELETE /materials/{id}` — Xóa mềm tài liệu (`S`, gán `deleted_at`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": null,
    "traceId": "b1c2d3e4f5a60005"
}
```

#### `POST /materials/{id}/reextract` — Trích xuất lại văn bản (`S`)
- **Response `202 Accepted`:**
```json
{
    "success": true,
    "data": {
        "id": 401,
        "status": "EXTRACTING",
        "updatedAt": "2026-11-25T09:20:00Z"
    },
    "traceId": "b1c2d3e4f5a60006"
}
```

---

### 2.4 AI Jobs, Summarization, Explanations, Grounded Chat & Feedback (`/ai`)

#### `GET /ai/jobs/{jobId}` — Kiểm tra trạng thái & kết quả tác vụ AI (`Chủ job`)
- **Response `200 OK` (đang chạy):**
```json
{
    "success": true,
    "data": {
        "jobId": "8f21b3a0-4c1d-4e92-8b77-112233445566",
        "type": "SUMMARY",
        "status": "RUNNING",
        "resultType": null,
        "resultId": null,
        "errorCode": null,
        "errorMessage": null,
        "attempts": 1,
        "createdAt": "2026-11-25T09:30:00Z",
        "finishedAt": null
    },
    "traceId": "c1d2e3f4a5b60001"
}
```
- **Response `200 OK` (hoàn thành):**
```json
{
    "success": true,
    "data": {
        "jobId": "8f21b3a0-4c1d-4e92-8b77-112233445566",
        "type": "SUMMARY",
        "status": "SUCCEEDED",
        "resultType": "SUMMARY",
        "resultId": 77,
        "errorCode": null,
        "errorMessage": null,
        "attempts": 1,
        "createdAt": "2026-11-25T09:30:00Z",
        "finishedAt": "2026-11-25T09:30:06Z"
    },
    "traceId": "c1d2e3f4a5b60002"
}
```

#### `DELETE /ai/jobs/{jobId}` — Hủy tác vụ AI đang chờ/chạy (`Chủ job`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "jobId": "8f21b3a0-4c1d-4e92-8b77-112233445566",
        "status": "CANCELLED"
    },
    "traceId": "c1d2e3f4a5b60003"
}
```

#### `POST /ai/summaries` — Tạo bản tóm tắt tài liệu bất đồng bộ (`S`)
- **Request:**
```json
{
    "materialId": 401,
    "length": "MEDIUM"
}
```
*(Giá trị `length`: `SHORT` | `MEDIUM` | `DETAILED`).*
- **Response `202 Accepted`:**
```json
{
    "success": true,
    "data": {
        "jobId": "8f21b3a0-4c1d-4e92-8b77-112233445566",
        "status": "PENDING"
    },
    "traceId": "c1d2e3f4a5b60004"
}
```

#### `GET /materials/{id}/summaries` & `GET /ai/summaries/{id}` & `DELETE /ai/summaries/{id}` (`S`)
- **Response `200 OK` (`GET /ai/summaries/{id}`):**
```json
{
    "success": true,
    "data": {
        "id": 77,
        "materialId": 401,
        "length": "MEDIUM",
        "content": "### Khái niệm cốt lõi\n- TCP là giao thức hướng kết nối, đảm bảo truyền tin cậy...",
        "model": "gemini-4-hydrogen",
        "promptVersion": "summary-v2.0",
        "createdAt": "2026-11-25T09:30:06Z",
        "updatedAt": "2026-11-25T09:30:06Z"
    },
    "traceId": "c1d2e3f4a5b60005"
}
```

#### `POST /ai/explanations` — Giải thích khái niệm bám sát tài liệu bất đồng bộ (`S`)
- **Request:**
```json
{
    "materialId": 401,
    "concept": "Cơ chế bắt tay ba bước (Three-way Handshake)",
    "contextSnippet": "SYN, SYN-ACK, ACK",
    "allowExternalKnowledge": false
}
```
- **Response `202 Accepted`:**
```json
{
    "success": true,
    "data": {
        "jobId": "7d12e4b0-1122-4455-99aa-bcdef0123456",
        "status": "PENDING"
    },
    "traceId": "c1d2e3f4a5b60006"
}
```

#### `POST /ai/chats` & `GET /ai/chats?courseId=&page=0&size=20` — Tạo & liệt kê phiên chat AI (`S`)
- **Request (`POST`):**
```json
{
    "title": "Hỏi đáp Tầng giao vận",
    "courseId": 101
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 501,
        "courseId": 101,
        "title": "Hỏi đáp Tầng giao vận",
        "createdAt": "2026-11-25T09:35:00Z",
        "updatedAt": "2026-11-25T09:35:00Z"
    },
    "traceId": "c1d2e3f4a5b60007"
}
```

#### `GET /ai/chats/{chatId}?cursor=&limit=30` & `DELETE /ai/chats/{chatId}` (`S`)
- **Response `200 OK` (`GET`):**
```json
{
    "success": true,
    "data": {
        "id": 501,
        "courseId": 101,
        "title": "Hỏi đáp Tầng giao vận",
        "messages": [
            {
                "id": 5520,
                "role": "USER",
                "content": "TCP khác UDP ở điểm nào?",
                "grounded": true,
                "sources": [],
                "aiAddedNote": null,
                "createdAt": "2026-11-25T09:36:00Z"
            },
            {
                "id": 5521,
                "role": "ASSISTANT",
                "content": "Theo tài liệu, TCP là giao thức hướng kết nối, đảm bảo thứ tự và kiểm soát tắc nghẽn...",
                "grounded": true,
                "sources": [
                    {
                        "materialId": 401,
                        "fileName": "Mang-may-tinh-chuong-3.pdf",
                        "page": 14,
                        "chunkId": 884
                    }
                ],
                "aiAddedNote": "Phần giải thích thêm của AI: Trong thực tế, UDP thường dùng cho video call do độ trễ thấp.",
                "model": "gemini-4-hydrogen",
                "promptVersion": "grounded-chat-v2.0",
                "createdAt": "2026-11-25T09:36:03Z"
            }
        ]
    },
    "meta": {
        "size": 30,
        "nextCursor": null
    },
    "traceId": "c1d2e3f4a5b60008"
}
```

#### `POST /ai/chats/{chatId}/messages` — Gửi câu hỏi & nhận câu trả lời có trích nguồn (`S`)
- **Request:**
```json
{
    "content": "TCP khác UDP ở điểm nào?",
    "materialIds": [401],
    "allowExternalKnowledge": false
}
```
- **Response `200 OK` (khi tìm thấy căn cứ trong tài liệu):**
```json
{
    "success": true,
    "data": {
        "messageId": 5521,
        "answer": "Theo tài liệu, TCP thiết lập kết nối trước khi truyền, kiểm soát lỗi và luồng; trong khi UDP truyền không kết nối và không đảm bảo thứ tự.",
        "grounded": true,
        "sources": [
            {
                "materialId": 401,
                "fileName": "Mang-may-tinh-chuong-3.pdf",
                "page": 14,
                "chunkId": 884
            }
        ],
        "aiAddedNote": "Phần giải thích thêm của AI: Bạn có thể nhớ TCP = tin cậy, UDP = tốc độ.",
        "model": "gemini-4-hydrogen",
        "promptVersion": "grounded-chat-v2.0",
        "createdAt": "2026-11-25T09:36:03Z"
    },
    "traceId": "c1d2e3f4a5b60009"
}
```
- **Response `200 OK` (khi tài liệu không có thông tin và `allowExternalKnowledge = false`):**
```json
{
    "success": true,
    "data": {
        "messageId": 5523,
        "answer": "Tài liệu hiện tại chưa có thông tin để kết luận",
        "grounded": false,
        "sources": [],
        "aiAddedNote": "Bạn có thể bật tùy chọn tham khảo kiến thức ngoài để AI giải thích mở rộng.",
        "model": "gemini-4-hydrogen",
        "promptVersion": "grounded-chat-v2.0",
        "createdAt": "2026-11-25T09:37:00Z"
    },
    "traceId": "c1d2e3f4a5b60010"
}
```

#### `POST /ai/feedback` — Báo câu trả lời AI sai / không bám tài liệu (`S`)
- **Request:**
```json
{
    "targetType": "AI_CHAT_MESSAGE",
    "targetId": 5521,
    "reason": "Trích dẫn nhầm trang 14 thay vì trang 18 và giải thích sai cờ FIN."
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 91,
        "targetType": "AI_CHAT_MESSAGE",
        "targetId": 5521,
        "reason": "Trích dẫn nhầm trang 14 thay vì trang 18 và giải thích sai cờ FIN.",
        "status": "OPEN",
        "createdAt": "2026-11-25T09:40:00Z"
    },
    "traceId": "c1d2e3f4a5b60011"
}
```

---

### 2.5 Quiz & Evaluation (`/ai/quizzes`, `/quizzes`, `/attempts`)

#### `POST /ai/quizzes` — Sinh quiz bằng AI bất đồng bộ (`S`, `T`)
- **Request:**
```json
{
    "courseId": 101,
    "materialIds": [401],
    "topicId": 301,
    "title": "Quiz Chương 3: Tầng giao vận",
    "topic": "Tầng giao vận",
    "questionCount": 10,
    "difficulty": "MEDIUM",
    "type": "SINGLE_CHOICE"
}
```
- **Response `202 Accepted`:**
```json
{
    "success": true,
    "data": {
        "jobId": "6a99c1d2-3344-4555-88bb-9900aabbccdd",
        "status": "PENDING"
    },
    "traceId": "d1e2f3a4b5c60001"
}
```

#### `GET /quizzes?courseId=&page=0&size=20` — Danh sách quiz của tôi (`S`, `T`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 601,
            "courseId": 101,
            "title": "Quiz Chương 3: Tầng giao vận",
            "difficulty": "MEDIUM",
            "questionCount": 10,
            "model": "gemini-4-hydrogen",
            "promptVersion": "quiz-v2.0",
            "version": 0,
            "createdAt": "2026-11-25T09:45:00Z",
            "updatedAt": "2026-11-25T09:45:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1,
        "nextCursor": null
    },
    "traceId": "d1e2f3a4b5c60002"
}
```

#### `GET /quizzes/{id}` — Chi tiết quiz chế độ làm bài (ẩn đáp án & giải thích) (`Chủ sở hữu`)
- **Quy tắc bảo mật:** Không bao giờ trả `isCorrect` hoặc `explanation` trước khi nộp bài.
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "id": 601,
        "courseId": 101,
        "title": "Quiz Chương 3: Tầng giao vận",
        "difficulty": "MEDIUM",
        "questionCount": 2,
        "version": 0,
        "questions": [
            {
                "id": 7001,
                "topicId": 301,
                "position": 1,
                "content": "Cơ chế nào giúp TCP điều chỉnh tốc độ gửi theo khả năng nhận của bên đích?",
                "type": "SINGLE_CHOICE",
                "options": [
                    {
                        "id": 8001,
                        "label": "A",
                        "content": "Flow Control (Sliding Window)"
                    },
                    {
                        "id": 8002,
                        "label": "B",
                        "content": "ARP Resolution"
                    },
                    {
                        "id": 8003,
                        "label": "C",
                        "content": "DNS Caching"
                    },
                    {
                        "id": 8004,
                        "label": "D",
                        "content": "ICMP Echo"
                    }
                ]
            }
        ]
    },
    "traceId": "d1e2f3a4b5c60003"
}
```

#### `PUT /quizzes/{id}` & `DELETE /quizzes/{id}` — Sửa & xóa mềm quiz (`Chủ sở hữu`)
- **Request (`PUT`, kèm `version`):**
```json
{
    "title": "Quiz Chương 3: Tầng giao vận (Cập nhật)",
    "difficulty": "HARD",
    "version": 0
}
```
- **Response `200 OK`:** Trả về thông tin quiz mới (tăng `version`) hoặc `data: null` khi `DELETE` (gán `deleted_at`).

#### `POST /quizzes/{id}/attempts` — Bắt đầu lượt làm bài (`S`)
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "attemptId": 901,
        "quizId": 601,
        "status": "IN_PROGRESS",
        "startedAt": "2026-11-25T10:00:00Z",
        "deadlineAt": null,
        "maxScore": 10.0,
        "version": 0,
        "answers": []
    },
    "traceId": "d1e2f3a4b5c60004"
}
```

#### `PUT /quizzes/{id}/attempts/{attemptId}` — Lưu nháp đáp án (`S`)
- **Request:**
```json
{
    "version": 0,
    "answers": [
        {
            "questionId": 7001,
            "selectedOptionIds": [8001]
        }
    ]
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "attemptId": 901,
        "status": "IN_PROGRESS",
        "version": 1,
        "updatedAt": "2026-11-25T10:03:00Z"
    },
    "traceId": "d1e2f3a4b5c60005"
}
```

#### `POST /quizzes/{id}/attempts/{attemptId}/submit` — Nộp bài & tự động chấm điểm (`S`)
- **Headers:** `Idempotency-Key: 3f7e81d0-2c4a-4b11-9e8a-001122334455`
- **Request:**
```json
{
    "version": 1,
    "durationSeconds": 540,
    "answers": [
        {
            "questionId": 7001,
            "selectedOptionIds": [8001]
        }
    ]
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "attemptId": 901,
        "quizId": 601,
        "status": "SUBMITTED",
        "score": 8.0,
        "maxScore": 10.0,
        "correctCount": 8,
        "totalQuestions": 10,
        "durationSeconds": 540,
        "submittedAt": "2026-11-25T10:09:00Z",
        "details": [
            {
                "questionId": 7001,
                "selectedOptionIds": [8001],
                "correctOptionIds": [8001],
                "correct": true,
                "explanation": "Sliding Window giúp bên nhận thông báo kích thước cửa sổ rwnd để điều tiết lưu lượng."
            }
        ]
    },
    "traceId": "d1e2f3a4b5c60006"
}
```

#### `GET /quizzes/{id}/attempts?page=0&size=20` — Lịch sử các lượt làm bài của quiz (`S`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "attemptId": 901,
            "status": "SUBMITTED",
            "score": 8.0,
            "maxScore": 10.0,
            "correctCount": 8,
            "totalQuestions": 10,
            "durationSeconds": 540,
            "startedAt": "2026-11-25T10:00:00Z",
            "submittedAt": "2026-11-25T10:09:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1,
        "nextCursor": null
    },
    "traceId": "d1e2f3a4b5c60007"
}
```

#### `GET /attempts/{attemptId}` — Xem lại chi tiết lượt làm & giải thích (`S`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "attemptId": 901,
        "quizId": 601,
        "quizTitle": "Quiz Chương 3: Tầng giao vận",
        "status": "SUBMITTED",
        "score": 8.0,
        "maxScore": 10.0,
        "correctCount": 8,
        "totalQuestions": 10,
        "durationSeconds": 540,
        "submittedAt": "2026-11-25T10:09:00Z",
        "questions": [
            {
                "questionId": 7001,
                "position": 1,
                "content": "Cơ chế nào giúp TCP điều chỉnh tốc độ gửi theo khả năng nhận của bên đích?",
                "type": "SINGLE_CHOICE",
                "options": [
                    {
                        "id": 8001,
                        "label": "A",
                        "content": "Flow Control (Sliding Window)",
                        "correct": true
                    },
                    {
                        "id": 8002,
                        "label": "B",
                        "content": "ARP Resolution",
                        "correct": false
                    }
                ],
                "selectedOptionIds": [8001],
                "correct": true,
                "explanation": "Sliding Window giúp bên nhận thông báo kích thước cửa sổ rwnd để điều tiết lưu lượng.",
                "sourceChunkId": 884
            }
        ]
    },
    "traceId": "d1e2f3a4b5c60008"
}
```

---

### 2.6 Learning Analytics & Student Dashboard (`/analytics`, `/dashboard`)

#### `GET /analytics/overview` — Thống kê tổng quan học tập (`S`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "totalCourses": 4,
        "totalMaterials": 18,
        "totalAttempts": 12,
        "averageScore": 7.85,
        "maxScoreScale": 10.0,
        "totalQuestionsAttempted": 120,
        "totalCorrectAnswers": 94,
        "accuracyRate": 78.33
    },
    "traceId": "e1f2a3b4c5d60001"
}
```

#### `GET /analytics/score-trend?from=2026-11-01T00:00:00Z&to=2026-11-25T23:59:59Z&courseId=` — Xu hướng điểm theo thời gian (`S`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "attemptId": 890,
            "quizId": 601,
            "quizTitle": "Quiz Chương 3: Tầng giao vận",
            "courseId": 101,
            "score": 7.5,
            "maxScore": 10.0,
            "submittedAt": "2026-11-22T14:20:00Z"
        },
        {
            "attemptId": 901,
            "quizId": 601,
            "quizTitle": "Quiz Chương 3: Tầng giao vận",
            "courseId": 101,
            "score": 8.0,
            "maxScore": 10.0,
            "submittedAt": "2026-11-25T10:09:00Z"
        }
    ],
    "traceId": "e1f2a3b4c5d60002"
}
```

#### `GET /analytics/frequent-mistakes?courseId=&limit=10` — Câu sai thường gặp (`S`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "questionId": 7005,
            "courseId": 101,
            "courseName": "Mạng máy tính",
            "topicId": 301,
            "topicTitle": "Giao thức TCP và kiểm soát tắc nghẽn",
            "questionContent": "Trong TCP Congestion Control, sự kiện Triple Duplicate ACK kích hoạt pha nào?",
            "totalAttempts": 4,
            "wrongCount": 3,
            "accuracyRate": 25.0
        }
    ],
    "traceId": "e1f2a3b4c5d60003"
}
```

#### `GET /analytics/review-topics` — Chủ đề nên ôn tập kèm lý do & mức chắc chắn (`S`)
*(Lưu ý chất lượng AI: nêu lý do và mức chắc chắn dữ liệu, không gán nhãn năng lực người học).*
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "courseId": 101,
            "courseName": "Mạng máy tính",
            "topicId": 301,
            "topicTitle": "Giao thức TCP và kiểm soát tắc nghẽn",
            "accuracyRate": 45.0,
            "sampleSize": 11,
            "confidence": "HIGH",
            "reason": "Độ chính xác 45.0% qua 11 câu hỏi thuộc chủ đề này trong 3 lượt làm gần nhất.",
            "suggestedMaterialId": 401,
            "suggestedAction": "Ôn lại mục Kiểm soát tắc nghẽn TCP trong tài liệu Mang-may-tinh-chuong-3.pdf."
        }
    ],
    "traceId": "e1f2a3b4c5d60004"
}
```

#### `GET /dashboard/student` — Bảng điều khiển tổng hợp sinh viên (`S`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "overview": {
            "totalCourses": 4,
            "totalMaterials": 18,
            "averageScore": 7.85,
            "accuracyRate": 78.33
        },
        "recentAttempts": [
            {
                "attemptId": 901,
                "quizId": 601,
                "quizTitle": "Quiz Chương 3: Tầng giao vận",
                "score": 8.0,
                "maxScore": 10.0,
                "submittedAt": "2026-11-25T10:09:00Z"
            }
        ],
        "reviewTopics": [
            {
                "topicId": 301,
                "topicTitle": "Giao thức TCP và kiểm soát tắc nghẽn",
                "confidence": "HIGH",
                "reason": "Độ chính xác 45.0% qua 11 câu hỏi thuộc chủ đề này."
            }
        ],
        "upcomingEvents": [],
        "dueAssignments": [],
        "unreadNotificationCount": 0,
        "todayTasks": []
    },
    "traceId": "e1f2a3b4c5d60005"
}
```
*(Ghi chú: `upcomingEvents`, `dueAssignments`, `unreadNotificationCount`, `todayTasks` mở rộng dữ liệu tương ứng ở Đợt 2, 3, 4, 5).*

---

## Phần 3: Danh mục Endpoint dự kiến Đợt 2–6

Ký hiệu vai trò: `S` = Student, `T` = Teacher, `A` = Admin, `All` = Đã đăng nhập, `TV` = Thành viên lớp/hội thoại.

### 3.1 Classroom — Lớp học (Đợt 2 — FR-12)
| Method | Endpoint | Vai trò | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/classes` | `T` | Tạo lớp mới (tự sinh mã lớp duy nhất) |
| `GET` | `/classes` | `S, T` | Danh sách lớp của tôi |
| `GET, PUT` | `/classes/{id}` | `TV / T` | Xem chi tiết, cập nhật lớp (có `version`) |
| `PUT` | `/classes/{id}/status` | `T` | Đổi trạng thái lớp (`OPEN`, `CLOSED`, `ARCHIVED`) |
| `POST` | `/classes/join-requests` | `S` | Gửi yêu cầu vào lớp bằng `{ "code": "..." }` |
| `GET` | `/classes/{id}/join-requests` | `T` | Danh sách yêu cầu đang chờ (`PENDING`) |
| `PUT` | `/classes/{id}/join-requests/{reqId}` | `T` | Duyệt (`APPROVED`) hoặc từ chối (`REJECTED`) |
| `GET` | `/classes/{id}/members` | `TV` | Danh sách thành viên lớp |
| `POST` | `/classes/{id}/co-teachers` | `T (Chủ nhiệm)` | Thêm giảng viên đồng giảng dạy |
| `DELETE` | `/classes/{id}/members/{userId}` | `T` | Gỡ thành viên khỏi lớp |
| `POST` | `/classes/{id}/leave` | `S` | Tự rời lớp |
| `GET, POST` | `/classes/{id}/posts` | `TV / T` | Bảng tin lớp: xem, đăng bài |
| `PUT` | `/classes/{id}/posts/{postId}/pin` | `T` | Ghim / bỏ ghim thông báo |
| `GET, POST` | `/classes/{id}/materials` | `TV / T` | Tài liệu lớp |
| `GET` | `/class-materials/{id}/download` | `TV` | Tải tài liệu lớp |

### 3.2 Assignment & Test — Bài tập & Bài kiểm tra (Đợt 2 — FR-13, FR-14, FR-20)
| Method | Endpoint | Vai trò | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/classes/{id}/assignments` | `T` | Tạo bài tập (hạn nộp UTC, cho phép nộp muộn) |
| `GET` | `/classes/{id}/assignments` | `TV` | Danh sách bài tập của lớp |
| `GET, PUT, DELETE` | `/assignments/{id}` | `TV / T` | Chi tiết, sửa, xóa bài tập |
| `POST` | `/assignments/{id}/submissions` | `S` | Nộp bài (một lần mặc định, header `Idempotency-Key`, giờ máy chủ quyết định `is_late`) |
| `GET` | `/assignments/{id}/submissions` | `T` | Danh sách bài nộp của sinh viên |
| `GET` | `/assignments/{id}/submissions/me` | `S` | Xem bài nộp & trạng thái của bản thân |
| `PUT` | `/submissions/{id}/grade` | `T` | Chấm điểm, nhận xét, trả bài |
| `PUT` | `/submissions/{id}/reopen` | `T` | Mở lại quyền nộp bài cho sinh viên |
| `GET` | `/submissions/{id}/download` | `S (chủ), T` | Tải file bài nộp |
| `POST` | `/classes/{id}/tests` | `T` | Tạo bài kiểm tra định thời từ quiz |
| `GET` | `/classes/{id}/tests` | `TV` | Danh sách bài kiểm tra |
| `PUT, DELETE` | `/tests/{id}` | `T` | Sửa, xóa bài kiểm tra |
| `POST` | `/tests/{id}/attempts` | `S` | Bắt đầu làm bài kiểm tra (kiểm tra cửa sổ thời gian & số lần) |
| `POST` | `/tests/{id}/attempts/{attemptId}/submit` | `S` | Nộp bài kiểm tra (`Idempotency-Key`) |
| `GET` | `/tests/{id}/results` | `T` | Thống kê kết quả bài kiểm tra của cả lớp |
| `GET` | `/dashboard/teacher` | `T` | Dashboard giảng viên (lớp, bài sắp đến hạn, tiến độ nộp/chấm) |

### 3.3 Schedule & Meeting — Lịch, Sự kiện, Google Calendar (Đợt 3 — FR-15, FR-16)
| Method | Endpoint | Vai trò | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/classes/{id}/events` | `T` | Tạo sự kiện học / kiểm tra / họp (có `meetingUrl` dán thủ công) |
| `PUT, DELETE` | `/events/{id}` | `T` | Đổi lịch, hủy sự kiện (kích hoạt thông báo qua outbox) |
| `GET` | `/calendar?from=&to=&view=` | `All` | Lịch tổng hợp (cá nhân, lớp, kiểm tra, họp, Google sync) |
| `POST` | `/calendar/imports/preview` | `S` | Tải `.ics` / CSV, xem trước & cảnh báo trùng giờ |
| `POST` | `/calendar/imports/{importId}/confirm` | `S` | Xác nhận nhập lịch |
| `GET` | `/calendar/import-template` | `S` | Tải file CSV mẫu |
| `GET, POST` | `/personal-events` | `S` | Xem, tạo sự kiện cá nhân |
| `PUT, DELETE` | `/personal-events/{id}` | `S (chủ)` | Sửa, xóa sự kiện cá nhân |
| `GET` | `/integrations/google/authorize` | `All` | Khởi tạo OAuth Google Calendar (scope chỉ đọc) |
| `GET` | `/integrations/google/callback` | `All` | Callback nhận OAuth code & lưu token mã hóa |
| `POST` | `/integrations/google/sync` | `All` | Đồng bộ đọc sự kiện từ Google Calendar |
| `DELETE` | `/integrations/google` | `All` | Ngắt kết nối & xóa token Google |

### 3.4 Chat & Notification — Chat thời gian thực & Thông báo (Đợt 4 — FR-18, FR-19)
| Method / Destination | Endpoint | Vai trò | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/conversations` | `All` | Danh sách phòng chat 1-1 và chat lớp |
| `POST` | `/conversations/direct` | `All` | Mở hoặc lấy hội thoại 1-1 (`{ "userId": ... }`) |
| `GET` | `/conversations/{id}/messages?cursor=&limit=` | `TV` | Lịch sử tin nhắn (phân trang cursor, tải bù khi reconnect) |
| `POST` | `/conversations/{id}/messages` | `TV` | Gửi tin nhắn qua REST dự phòng (`clientMessageId`) |
| `PUT` | `/conversations/{id}/read` | `TV` | Đánh dấu đã đọc đến `lastReadMessageId` |
| `STOMP SEND` | `/app/conversations/{id}/messages` | `TV` | Gửi tin nhắn thời gian thực (`{ "clientMessageId", "content" }`) |
| `STOMP SUB` | `/topic/conversations/{id}` | `TV` | Nhận tin nhắn mới trong phòng chat |
| `GET` | `/notifications?cursor=&unread=` | `All` | Danh sách thông báo (cursor) |
| `GET` | `/notifications/unread-count` | `All` | Đếm số thông báo chưa đọc |
| `PUT` | `/notifications/{id}/read` | `All` | Đánh dấu đã đọc 1 thông báo |
| `PUT` | `/notifications/read-all` | `All` | Đánh dấu đã đọc tất cả |
| `GET, PUT` | `/notifications/settings` | `All` | Cấu hình bật/tắt kênh `IN_APP` và `EMAIL` theo loại sự kiện |
| `STOMP SUB` | `/user/queue/notifications` | `All` | Nhận thông báo đẩy thời gian thực |

### 3.5 Flashcard, Study Plan & RAG (Đợt 5 — FR-05, FR-08, FR-17)
| Method | Endpoint | Vai trò | Mô tả |
| :--- | :--- | :--- | :--- |
| `POST` | `/ai/flashcards` | `S` | AI sinh bộ flashcard bất đồng bộ (`202`) |
| `GET, POST` | `/flashcard-decks` | `S` | Danh sách, tạo bộ flashcard |
| `GET, PUT, DELETE` | `/flashcard-decks/{id}` | `S` | Xem, sửa, xóa bộ thẻ |
| `POST` | `/flashcard-decks/{id}/cards` | `S` | Thêm flashcard thủ công |
| `GET` | `/flashcard-decks/{id}/review` | `S` | Lấy thẻ đến hạn ôn hôm nay |
| `POST` | `/flashcards/{id}/reviews` | `S` | Ghi mức nhớ (`FORGOT`, `HARD`, `OK`, `EASY`) |
| `GET, POST` | `/goals` | `S` | Danh sách, tạo mục tiêu học tập |
| `PUT, DELETE` | `/goals/{id}` | `S` | Sửa, xóa mục tiêu |
| `GET, POST` | `/tasks` | `S` | Task theo ngày: xem, tạo |
| `PUT, DELETE` | `/tasks/{id}` | `S` | Sửa, xóa task |
| `PUT` | `/tasks/{id}/complete` | `S` | Đánh dấu hoàn thành task |
| `POST` | `/ai/study-plans` | `S` | AI đề xuất lộ trình học tập (`202`) |
| `GET` | `/study-plans/{id}` | `S` | Xem chi tiết đề xuất lộ trình |
| `PUT` | `/study-plans/{id}/items/{itemId}` | `S` | Chấp nhận, chỉnh sửa hoặc từ chối từng mục đề xuất |

### 3.6 Admin & Governance (Đợt 2 & Đợt 6 — FR-11)
| Method | Endpoint | Vai trò | Mô tả |
| :--- | :--- | :--- | :--- |
| `GET` | `/users?role=&status=&keyword=&page=0&size=20` | `A` | Quản lý danh sách người dùng |
| `PUT` | `/users/{id}/role` | `A` | Phân quyền vai trò (`STUDENT`, `TEACHER`, `ADMIN`) |
| `PUT` | `/users/{id}/status` | `A` | Khóa / mở khóa tài khoản |
| `GET` | `/admin/classes` | `A` | Danh sách toàn bộ lớp học trong hệ thống |
| `PUT` | `/admin/classes/{id}/status` | `A` | Khóa / mở lớp học |
| `GET` | `/admin/reports` | `A` | Danh sách báo cáo vi phạm / báo cáo AI sai |
| `PUT` | `/admin/reports/{id}` | `A` | Xử lý & đóng báo cáo |
| `GET, PUT` | `/admin/settings` | `A` | Cấu hình hệ thống, hạn mức AI, chính sách Google cá nhân |
