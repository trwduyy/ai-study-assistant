# AI Study Assistant — API Contract Đợt 2: Lớp học, Bài tập & Bài kiểm tra (V2)

Tài liệu hợp đồng API chi tiết cho **Đợt 2** (Mục 5.7 — Lớp học `FR-12` và Mục 5.8 — Bài tập & Bài kiểm tra `FR-13`, `FR-14`, `FR-20`).

---

## 1. Quy ước chung

- **Base URL:** `http://localhost:8080/api/v1` (Production: `https://{host}/api/v1`)
- **Xác thực:** `Authorization: Bearer <accessToken>` cho toàn bộ endpoint bên dưới.
- **Định dạng:** JSON UTF-8, thụt lề **4 spaces**, thời gian chuẩn **ISO-8601 UTC** (`yyyy-MM-dd'T'HH:mm:ss'Z'`).
- **Chuẩn phản hồi:** Mọi endpoint REST (trừ endpoint tải file nhị phân `Resource`) đều trả về `ApiResponse<T>` V2 gồm `success`, `data`, `meta` (nếu phân trang), `error` (nếu lỗi), `traceId`.
- **Ký hiệu vai trò:**
    - `S`: Sinh viên (`STUDENT`)
    - `T`: Giảng viên (`TEACHER` hoặc `ADMIN` có vai trò giảng viên trong lớp: `OWNER_TEACHER` / `CO_TEACHER`)
    - `T (Chủ nhiệm)`: Chỉ `OWNER_TEACHER` của lớp
    - `TV`: Thành viên đang hoạt động của lớp (`class_members.status = ACTIVE`)
- **Kiểm tra quyền (BR-02, BR-04, BR-05):**
    - Mọi thao tác lớp học kiểm tra quan hệ `class_members` ở Backend (`ACTIVE`).
    - Thành viên `LEFT` hoặc `REMOVED` bị chặn ngay lập tức (`403 NOT_CLASS_MEMBER`), kể cả khi xem/tải tài liệu lớp.

---

## 2. Module Classroom — Lớp học (Mục 5.7, FR-12)

### 2.1 `POST /api/v1/classes` — Tạo lớp học mới (`T`)
Tự động sinh mã lớp `code` 8 ký tự duy nhất (dễ đọc, bỏ qua ký tự dễ nhầm lẫn như `0/O`, `1/I/L`) và thêm người tạo vào `class_members` với vai trò `OWNER_TEACHER`.

- **Request:**
```json
{
    "name": "Lập trình Mạng - K19A",
    "description": "Lớp học phần lập trình mạng TCP/UDP và Socket",
    "subject": "Mạng máy tính",
    "avatarUrl": "https://cdn.example.com/classes/net-k19a.png",
    "startAt": "2026-11-25T00:00:00Z",
    "endAt": "2027-03-15T23:59:59Z"
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 10,
        "code": "KMA7N9P4",
        "name": "Lập trình Mạng - K19A",
        "description": "Lớp học phần lập trình mạng TCP/UDP và Socket",
        "subject": "Mạng máy tính",
        "avatarUrl": "https://cdn.example.com/classes/net-k19a.png",
        "startAt": "2026-11-25T00:00:00Z",
        "endAt": "2027-03-15T23:59:59Z",
        "status": "OPEN",
        "ownerId": 2,
        "ownerName": "TS. Trần Văn B",
        "myClassRole": "OWNER_TEACHER",
        "memberCount": 1,
        "version": 0,
        "createdAt": "2026-11-25T09:00:00Z",
        "updatedAt": "2026-11-25T09:00:00Z"
    },
    "traceId": "c2a1b3d4e5f60001"
}
```

### 2.2 `GET /api/v1/classes` — Danh sách lớp học của tôi (`S, T`)
Trả về các lớp mà người dùng hiện tại đang là thành viên `ACTIVE`. Hỗ trợ lọc `?status=OPEN&keyword=mang&page=0&size=20&sort=createdAt,desc`.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 10,
            "code": "KMA7N9P4",
            "name": "Lập trình Mạng - K19A",
            "description": "Lớp học phần lập trình mạng TCP/UDP và Socket",
            "subject": "Mạng máy tính",
            "avatarUrl": "https://cdn.example.com/classes/net-k19a.png",
            "startAt": "2026-11-25T00:00:00Z",
            "endAt": "2027-03-15T23:59:59Z",
            "status": "OPEN",
            "ownerId": 2,
            "ownerName": "TS. Trần Văn B",
            "myClassRole": "STUDENT",
            "memberCount": 42,
            "version": 1,
            "createdAt": "2026-11-25T09:00:00Z",
            "updatedAt": "2026-11-25T09:10:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    },
    "traceId": "c2a1b3d4e5f60002"
}
```

### 2.3 `GET /api/v1/classes/{id}` — Xem chi tiết lớp học (`TV`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "id": 10,
        "code": "KMA7N9P4",
        "name": "Lập trình Mạng - K19A",
        "description": "Lớp học phần lập trình mạng TCP/UDP và Socket",
        "subject": "Mạng máy tính",
        "avatarUrl": "https://cdn.example.com/classes/net-k19a.png",
        "startAt": "2026-11-25T00:00:00Z",
        "endAt": "2027-03-15T23:59:59Z",
        "status": "OPEN",
        "ownerId": 2,
        "ownerName": "TS. Trần Văn B",
        "myClassRole": "OWNER_TEACHER",
        "memberCount": 42,
        "version": 1,
        "createdAt": "2026-11-25T09:00:00Z",
        "updatedAt": "2026-11-25T09:10:00Z"
    },
    "traceId": "c2a1b3d4e5f60003"
}
```
- **Lỗi thường gặp:** `403 NOT_CLASS_MEMBER`, `404 CLASS_NOT_FOUND`.

### 2.4 `PUT /api/v1/classes/{id}` — Cập nhật thông tin lớp học (`T`)
Kiểm tra optimistic locking qua `version` trong body hoặc header `If-Match`. Sai `version` trả `409 VERSION_CONFLICT`.

- **Request:**
```json
{
    "name": "Lập trình Mạng - K19A (Nâng cao)",
    "description": "Cập nhật đề cương học kỳ 1",
    "subject": "Mạng máy tính",
    "avatarUrl": "https://cdn.example.com/classes/net-k19a-v2.png",
    "startAt": "2026-11-25T00:00:00Z",
    "endAt": "2027-04-01T23:59:59Z",
    "version": 0
}
```
- **Response `200 OK`:** Trả về object chi tiết lớp với `version` mới (`1`).

### 2.5 `PUT /api/v1/classes/{id}/status` — Đổi trạng thái lớp học (`T`)
- **Request:**
```json
{
    "status": "CLOSED",
    "version": 1
}
```
*(Giá trị hợp lệ: `OPEN`, `CLOSED`, `ARCHIVED`; trường `version` tùy chọn hoặc qua header `If-Match`)*.
- **Response `200 OK`:** Trả về object chi tiết lớp với `status` mới.

### 2.6 `POST /api/v1/classes/join-requests` — Gửi yêu cầu tham gia lớp bằng mã (`S, All`)
Kiểm tra mã lớp tồn tại và trạng thái `OPEN` (`404 CLASS_CODE_INVALID` nếu sai mã hoặc lớp không mở), người dùng chưa là thành viên `ACTIVE` (`409 ALREADY_CLASS_MEMBER`), và chưa có yêu cầu `PENDING` (`409 JOIN_REQUEST_EXISTS`). Trong cùng transaction sẽ ghi `notification_outbox` thông báo tới giảng viên lớp.

- **Request:**
```json
{
    "code": "KMA7N9P4"
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 501,
        "classId": 10,
        "className": "Lập trình Mạng - K19A",
        "studentId": 12,
        "studentName": "Nguyễn Văn A",
        "studentEmail": "sv01@actvn.edu.vn",
        "status": "PENDING",
        "decidedById": null,
        "decidedByName": null,
        "decidedAt": null,
        "rejectionReason": null,
        "createdAt": "2026-11-25T09:20:00Z",
        "updatedAt": "2026-11-25T09:20:00Z"
    },
    "traceId": "c2a1b3d4e5f60006"
}
```

### 2.7 `GET /api/v1/classes/{id}/join-requests?status=PENDING` — Danh sách yêu cầu tham gia (`T`)
Mặc định lọc `status=PENDING` nếu không truyền. Hỗ trợ phân trang `?page=0&size=20&sort=createdAt,desc`.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 501,
            "classId": 10,
            "className": "Lập trình Mạng - K19A",
            "studentId": 12,
            "studentName": "Nguyễn Văn A",
            "studentEmail": "sv01@actvn.edu.vn",
            "status": "PENDING",
            "decidedById": null,
            "decidedByName": null,
            "decidedAt": null,
            "rejectionReason": null,
            "createdAt": "2026-11-25T09:20:00Z",
            "updatedAt": "2026-11-25T09:20:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    },
    "traceId": "c2a1b3d4e5f60007"
}
```

### 2.8 `PUT /api/v1/classes/{id}/join-requests/{reqId}` — Duyệt hoặc từ chối yêu cầu (`T`)
Nếu `APPROVED`: trong cùng 1 transaction, cập nhật yêu cầu, thêm hoặc kích hoạt lại `class_members` (`STUDENT`, `ACTIVE`) và ghi `notification_outbox` cho sinh viên. Nếu `REJECTED`: cập nhật yêu cầu kèm lý do và ghi `notification_outbox`.

- **Request:**
```json
{
    "decision": "APPROVED",
    "rejectionReason": null
}
```
*(Nếu từ chối: `"decision": "REJECTED", "rejectionReason": "Sai mã lớp học phần"`).*
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "id": 501,
        "classId": 10,
        "className": "Lập trình Mạng - K19A",
        "studentId": 12,
        "studentName": "Nguyễn Văn A",
        "studentEmail": "sv01@actvn.edu.vn",
        "status": "APPROVED",
        "decidedById": 2,
        "decidedByName": "TS. Trần Văn B",
        "decidedAt": "2026-11-25T09:25:00Z",
        "rejectionReason": null,
        "createdAt": "2026-11-25T09:20:00Z",
        "updatedAt": "2026-11-25T09:25:00Z"
    },
    "traceId": "c2a1b3d4e5f60008"
}
```

### 2.9 `GET /api/v1/classes/{id}/members` — Danh sách thành viên lớp (`TV`)
Trả về các thành viên `ACTIVE` của lớp, hỗ trợ lọc `?role=STUDENT&page=0&size=20`.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 101,
            "classId": 10,
            "userId": 2,
            "fullName": "TS. Trần Văn B",
            "email": "gv01@actvn.edu.vn",
            "avatarUrl": null,
            "classRole": "OWNER_TEACHER",
            "status": "ACTIVE",
            "joinedAt": "2026-11-25T09:00:00Z"
        },
        {
            "id": 102,
            "classId": 10,
            "userId": 12,
            "fullName": "Nguyễn Văn A",
            "email": "sv01@actvn.edu.vn",
            "avatarUrl": null,
            "classRole": "STUDENT",
            "status": "ACTIVE",
            "joinedAt": "2026-11-25T09:25:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 2,
        "totalPages": 1
    },
    "traceId": "c2a1b3d4e5f60009"
}
```

### 2.10 `POST /api/v1/classes/{id}/co-teachers` — Thêm giảng viên đồng giảng dạy (`T (Chủ nhiệm)`)
Chỉ `OWNER_TEACHER` mới có quyền thực hiện (`403 FORBIDDEN` nếu là `CO_TEACHER` hoặc `STUDENT`).

- **Request:**
```json
{
    "userId": 5
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 103,
        "classId": 10,
        "userId": 5,
        "fullName": "ThS. Lê Thị C",
        "email": "gv02@actvn.edu.vn",
        "avatarUrl": null,
        "classRole": "CO_TEACHER",
        "status": "ACTIVE",
        "joinedAt": "2026-11-25T09:30:00Z"
    },
    "traceId": "c2a1b3d4e5f60010"
}
```

### 2.11 `DELETE /api/v1/classes/{id}/members/{userId}` — Gỡ thành viên khỏi lớp (`T`)
Đổi `status = REMOVED` (không xóa vật lý để giữ lịch sử bài nộp). Không thể gỡ `OWNER_TEACHER`; chỉ `OWNER_TEACHER` được gỡ `CO_TEACHER`.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": null,
    "traceId": "c2a1b3d4e5f60011"
}
```

### 2.12 `POST /api/v1/classes/{id}/leave` — Rời lớp (`S, CO_TEACHER`)
Đổi `status = LEFT`. `OWNER_TEACHER` không thể rời lớp do mình làm chủ nhiệm (`422 UNPROCESSABLE_ENTITY`).

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": null,
    "traceId": "c2a1b3d4e5f60012"
}
```

### 2.13 `GET /api/v1/classes/{id}/posts` & `POST /api/v1/classes/{id}/posts` — Bảng tin lớp (`TV / T`)
- **`GET` Response `200 OK` (sắp xếp `pinned DESC, createdAt DESC`):**
```json
{
    "success": true,
    "data": [
        {
            "id": 201,
            "classId": 10,
            "authorId": 2,
            "authorName": "TS. Trần Văn B",
            "title": "Lịch kiểm tra giữa kỳ",
            "content": "Các em ôn tập chương 1 đến chương 3 trước thứ Sáu.",
            "pinned": true,
            "createdAt": "2026-11-25T10:00:00Z",
            "updatedAt": "2026-11-25T10:00:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    },
    "traceId": "c2a1b3d4e5f60013"
}
```
- **`POST` Request (`T`):**
```json
{
    "title": "Lịch kiểm tra giữa kỳ",
    "content": "Các em ôn tập chương 1 đến chương 3 trước thứ Sáu.",
    "pinned": true
}
```
- **`POST` Response `201 Created`:** Trả về object bài đăng vừa tạo.

### 2.14 `PUT /api/v1/classes/{id}/posts/{postId}/pin` — Ghim / bỏ ghim bài đăng (`T`)
- **Request (tùy chọn — nếu bỏ trống body sẽ tự đảo trạng thái `pinned`):**
```json
{
    "pinned": false
}
```
- **Response `200 OK`:** Trả về object `ClassPostResponse` đã cập nhật.

### 2.15 `GET /api/v1/classes/{id}/materials` & `POST /api/v1/classes/{id}/materials` — Tài liệu lớp (`TV / T`)
- **`POST` (`T`, `multipart/form-data` với part `file`):** Hỗ trợ PDF, DOCX, TXT (tối đa 20MB). Ghi file vật lý ngoài transaction.
- **`POST` Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 301,
        "classId": 10,
        "uploaderId": 2,
        "uploaderName": "TS. Trần Văn B",
        "fileName": "Bai-giang-Chuong-3-TCP.pdf",
        "mimeType": "application/pdf",
        "sizeBytes": 1458920,
        "createdAt": "2026-11-25T10:15:00Z",
        "updatedAt": "2026-11-25T10:15:00Z"
    },
    "traceId": "c2a1b3d4e5f60015"
}
```
- **`GET` Response `200 OK` (`TV`):** Trả về mảng `ClassMaterialResponse` phân trang theo `meta`. Người dùng ngoài lớp hoặc có `status` là `LEFT`/`REMOVED` nhận `403 NOT_CLASS_MEMBER`.

### 2.16 `GET /api/v1/class-materials/{id}/download` — Tải tài liệu lớp (`TV`)
Kiểm tra bắt buộc `requireActiveMember(classId, currentUserId)`. Thành viên `LEFT` hoặc `REMOVED` nhận `403 NOT_CLASS_MEMBER`. Thành công trả về luồng file nhị phân kèm header `Content-Disposition: attachment` và `Content-Type`.

---

## 3. Module Assignment & Class Test — Bài tập & Bài kiểm tra (Mục 5.8, FR-13, FR-14, FR-20)

### 3.1 `POST /api/v1/classes/{id}/assignments` — Tạo bài tập (`T`)
- **Request:**
```json
{
    "title": "Bài tập tuần 3: Socket TCP",
    "instructions": "Viết chương trình Echo Server/Client bằng Java Socket và nộp báo cáo PDF.",
    "maxScore": 10.0,
    "openAt": "2026-11-25T10:00:00Z",
    "dueAt": "2026-12-02T16:59:59Z",
    "allowLate": true,
    "maxSubmissions": 1
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 401,
        "classId": 10,
        "creatorId": 2,
        "title": "Bài tập tuần 3: Socket TCP",
        "instructions": "Viết chương trình Echo Server/Client bằng Java Socket và nộp báo cáo PDF.",
        "maxScore": 10.0,
        "openAt": "2026-11-25T10:00:00Z",
        "dueAt": "2026-12-02T16:59:59Z",
        "allowLate": true,
        "maxSubmissions": 1,
        "attachments": [],
        "version": 0,
        "createdAt": "2026-11-25T10:30:00Z",
        "updatedAt": "2026-11-25T10:30:00Z"
    },
    "traceId": "a1b2c3d4e5f60001"
}
```

### 3.2 `GET /api/v1/classes/{id}/assignments` — Danh sách bài tập của lớp (`TV`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 401,
            "classId": 10,
            "title": "Bài tập tuần 3: Socket TCP",
            "maxScore": 10.0,
            "openAt": "2026-11-25T10:00:00Z",
            "dueAt": "2026-12-02T16:59:59Z",
            "allowLate": true,
            "maxSubmissions": 1,
            "mySubmissionStatus": "SUBMITTED",
            "version": 0,
            "createdAt": "2026-11-25T10:30:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    },
    "traceId": "a1b2c3d4e5f60002"
}
```

### 3.3 `GET / PUT / DELETE /api/v1/assignments/{id}` — Chi tiết, sửa, xóa bài tập (`TV / T`)
- **`GET` (`TV`):** Trả về chi tiết bài tập kèm `attachments`.
- **`PUT` (`T`):** Cập nhật thông tin bài tập, bắt buộc `version` (body hoặc `If-Match`); sai version trả `409 VERSION_CONFLICT`.
- **`DELETE` (`T`):** Xóa bài tập, trả `200 OK` với `data: null`.

### 3.4 `POST /api/v1/assignments/{id}/submissions` — Nộp bài tập (`S`)
Bắt buộc header `Idempotency-Key: <uuid>`. Hỗ trợ `multipart/form-data` (gồm `textAnswer` và danh sách `files`). Giờ máy chủ quyết định `isLate`. Nếu quá hạn và `allowLate = false` trả `422 ASSIGNMENT_CLOSED`. Nếu đã nộp mà chưa được mở lại (`reopen`) và khác `Idempotency-Key`, trả `409 ASSIGNMENT_ALREADY_SUBMITTED`.

- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 990,
        "assignmentId": 401,
        "studentId": 12,
        "studentName": "Nguyễn Văn A",
        "textAnswer": "Em đã hoàn thành Echo Server đa luồng bằng ExecutorService.",
        "status": "SUBMITTED",
        "late": false,
        "submittedAt": "2026-11-26T08:30:00Z",
        "score": null,
        "feedback": null,
        "gradedBy": null,
        "gradedAt": null,
        "files": [
            {
                "id": 1201,
                "fileName": "Bao-cao-Socket-TCP.pdf",
                "sizeBytes": 524288
            }
        ],
        "version": 0
    },
    "traceId": "a1b2c3d4e5f60004"
}
```

### 3.5 `GET /api/v1/assignments/{id}/submissions` — Danh sách bài nộp (`T`)
Hỗ trợ lọc `?status=SUBMITTED&late=false&page=0&size=20`.

- **Response `200 OK`:** Trả về danh sách `SubmissionSummaryResponse` kèm `meta` phân trang.

### 3.6 `GET /api/v1/assignments/{id}/submissions/me` — Xem bài nộp của bản thân (`S`)
- **Response `200 OK`:** Trả về chi tiết bài nộp của sinh viên hiện tại (hoặc `data: null` nếu chưa nộp).

### 3.7 `PUT /api/v1/submissions/{id}/grade` — Chấm điểm, nhận xét, trả bài (`T`)
Kiểm tra `score` trong `[0, assignment.maxScore]`, cập nhật `status = GRADED | RETURNED`, `gradedBy`, `gradedAt`, và ghi `notification_outbox` báo sinh viên. Hỗ trợ `version` chống ghi đè đồng thời.

- **Request:**
```json
{
    "score": 9.0,
    "feedback": "Code sạch, xử lý đóng socket tốt. Cần bổ sung kiểm thử timeout.",
    "status": "RETURNED",
    "version": 0
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "id": 990,
        "assignmentId": 401,
        "studentId": 12,
        "status": "RETURNED",
        "late": false,
        "submittedAt": "2026-11-26T08:30:00Z",
        "score": 9.0,
        "feedback": "Code sạch, xử lý đóng socket tốt. Cần bổ sung kiểm thử timeout.",
        "gradedBy": 2,
        "gradedAt": "2026-11-27T03:15:00Z",
        "version": 1
    },
    "traceId": "a1b2c3d4e5f60007"
}
```

### 3.8 `PUT /api/v1/submissions/{id}/reopen` — Mở lại quyền nộp bài cho sinh viên (`T`)
- **Request:**
```json
{
    "reason": "Cho phép nộp bổ sung file báo cáo thiếu",
    "version": 1
}
```
- **Response `200 OK`:** Trả về trạng thái bài nộp đã được mở lại và ghi `notification_outbox`.

### 3.9 `GET /api/v1/submissions/{id}/download?fileId=` — Tải tệp bài nộp (`S (chủ), T`)
Chỉ sinh viên sở hữu bài nộp hoặc giảng viên đang `ACTIVE` của lớp mới được tải (`403 FORBIDDEN` / `403 NOT_CLASS_MEMBER` nếu sai quyền).

### 3.10 `POST /api/v1/classes/{id}/tests` — Tạo bài kiểm tra định thời từ Quiz (`T`)
- **Request:**
```json
{
    "quizId": 601,
    "title": "Kiểm tra 15 phút: Tầng giao vận",
    "startAt": "2026-11-28T01:00:00Z",
    "endAt": "2026-11-28T03:00:00Z",
    "durationMinutes": 15,
    "maxAttempts": 1,
    "shuffleQuestions": true
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 701,
        "classId": 10,
        "quizId": 601,
        "title": "Kiểm tra 15 phút: Tầng giao vận",
        "startAt": "2026-11-28T01:00:00Z",
        "endAt": "2026-11-28T03:00:00Z",
        "durationMinutes": 15,
        "maxAttempts": 1,
        "shuffleQuestions": true,
        "createdAt": "2026-11-25T11:00:00Z",
        "updatedAt": "2026-11-25T11:00:00Z"
    },
    "traceId": "a1b2c3d4e5f60010"
}
```

### 3.11 `GET /api/v1/classes/{id}/tests` — Danh sách bài kiểm tra (`TV`)
- **Response `200 OK`:** Danh sách `ClassTestResponse` phân trang theo `meta`.

### 3.12 `PUT / DELETE /api/v1/tests/{id}` — Sửa, xóa bài kiểm tra (`T`)
- **`PUT`:** Cập nhật cấu hình thời gian/số lượt làm bài.
- **`DELETE`:** Xóa bài kiểm tra (`200 OK`, `data: null`).

### 3.13 `POST /api/v1/tests/{id}/attempts` — Bắt đầu làm bài kiểm tra (`S`)
Kiểm tra thành viên `ACTIVE`, cửa sổ `[startAt, endAt]` theo giờ máy chủ, và số lượt đã làm `< maxAttempts` (`422 MAX_ATTEMPTS_REACHED`). Tạo `quiz_attempt` gắn `testId`, `deadlineAt = min(now + durationMinutes, endAt)`.

- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "attemptId": 950,
        "testId": 701,
        "quizId": 601,
        "startedAt": "2026-11-28T01:05:00Z",
        "deadlineAt": "2026-11-28T01:20:00Z",
        "questions": [
            {
                "questionId": 7001,
                "position": 1,
                "content": "Giao thức nào đảm bảo truyền dữ liệu tin cậy theo luồng byte?",
                "type": "SINGLE_CHOICE",
                "options": [
                    {
                        "id": 8001,
                        "label": "A",
                        "content": "UDP"
                    },
                    {
                        "id": 8002,
                        "label": "B",
                        "content": "TCP"
                    }
                ]
            }
        ]
    },
    "traceId": "a1b2c3d4e5f60013"
}
```

### 3.14 `POST /api/v1/tests/{id}/attempts/{attemptId}/submit` — Nộp bài kiểm tra (`S`)
Bắt buộc header `Idempotency-Key: <uuid>`. Kiểm tra `now <= deadlineAt` (`422 ATTEMPT_EXPIRED` nếu quá hạn). Chấm điểm tại máy chủ.

- **Request:**
```json
{
    "answers": [
        {
            "questionId": 7001,
            "selectedOptionIds": [8002]
        }
    ],
    "durationSeconds": 420
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "attemptId": 950,
        "testId": 701,
        "status": "SUBMITTED",
        "score": 9.0,
        "maxScore": 10.0,
        "correctCount": 9,
        "totalQuestions": 10,
        "submittedAt": "2026-11-28T01:12:00Z"
    },
    "traceId": "a1b2c3d4e5f60014"
}
```

### 3.15 `GET /api/v1/tests/{id}/results` — Thống kê kết quả bài kiểm tra của lớp (`T`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "testId": 701,
        "title": "Kiểm tra 15 phút: Tầng giao vận",
        "totalStudents": 40,
        "submittedCount": 36,
        "averageScore": 7.85,
        "highestScore": 10.0,
        "lowestScore": 4.5,
        "studentResults": [
            {
                "studentId": 12,
                "studentName": "Nguyễn Văn A",
                "attemptsUsed": 1,
                "bestScore": 9.0,
                "lastSubmittedAt": "2026-11-28T01:12:00Z"
            }
        ]
    },
    "traceId": "a1b2c3d4e5f60015"
}
```
