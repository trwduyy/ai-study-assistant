# AI Study Assistant — API Contract Đợt 6: Quản trị hệ thống, Báo cáo & Audit Log (V2)

Tài liệu hợp đồng API chi tiết cho **Đợt 6A** (Mục 5.11 — Quản trị lớp học, xử lý báo cáo người dùng, cấu hình hệ thống & hạn mức AI, tra cứu nhật ký audit `UC-59`, `UC-60`).

---

## 1. Quy ước chung

- **Base URL:** `http://localhost:8080/api/v1` (Production: `https://{host}/api/v1`)
- **Xác thực:** `Authorization: Bearer <accessToken>` cho toàn bộ endpoint.
- **Định dạng:** JSON UTF-8, thụt lề **4 spaces**, thời gian lưu và trao đổi chuẩn **ISO-8601 UTC** (`yyyy-MM-dd'T'HH:mm:ss'Z'`).
- **Chuẩn phản hồi:** Mọi endpoint trả về `ApiResponse<T>` V2 gồm `success`, `data`, `meta` (khi phân trang), `error` (khi lỗi), `traceId`.
- **Phân quyền cứng:**
    - `All`: Mọi người dùng đã đăng nhập (`POST /reports`).
    - `ADMIN`: Toàn bộ endpoint `/api/v1/admin/**` yêu cầu `ROLE_ADMIN` (`@PreAuthorize("hasRole('ADMIN')")`); tài khoản `STUDENT` hoặc `TEACHER` gọi sẽ bị chặn với `403 FORBIDDEN`.
- **Hạn mức AI hàng ngày (`AI_QUOTA_EXCEEDED`):**
    - Khi tổng số lượt `ai_jobs` và tin nhắn Chat (`USER`) trong ngày UTC hiện tại đạt ngưỡng `AI_DAILY_QUOTA_PER_USER`, các API `/ai/**` trả `429 Too Many Requests` (`code = AI_QUOTA_EXCEEDED`) kèm header `Retry-After` (số giây tới `00:00:00Z` ngày tiếp theo).

---

## 2. Báo cáo từ người dùng (`All`)

### 2.1 `POST /api/v1/reports` — Gửi báo cáo sự cố / vi phạm (`All`)
Cho phép người dùng gửi báo cáo (`AI_INACCURATE`, `CLASS_ABUSE`, `CHAT_ABUSE`, `OTHER`). Ngoài ra, khi người dùng gọi `POST /api/v1/ai/feedback`, hệ thống tự động tạo thêm 1 bản ghi tương ứng trong `reports` với `type = "AI_INACCURATE"`.

- **Request:**
```json
{
    "type": "CLASS_ABUSE",
    "targetType": "CLASSROOM",
    "targetId": 10,
    "detail": "Nội dung bài đăng trong lớp chứa liên kết quảng cáo không phù hợp."
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 301,
        "reporterId": 11,
        "reporterName": "Nguyễn Văn A",
        "reporterEmail": "sv01@actvn.edu.vn",
        "type": "CLASS_ABUSE",
        "targetType": "CLASSROOM",
        "targetId": 10,
        "detail": "Nội dung bài đăng trong lớp chứa liên kết quảng cáo không phù hợp.",
        "status": "OPEN",
        "resolutionNote": null,
        "handledById": null,
        "handledByName": null,
        "handledAt": null,
        "createdAt": "2026-11-25T14:40:00Z",
        "updatedAt": "2026-11-25T14:40:00Z"
    },
    "traceId": "r6a1b2c3d4f50001"
}
```

---

## 3. Quản trị lớp học (`/api/v1/admin/classes`, `ADMIN`)

### 3.1 `GET /api/v1/admin/classes?status=&keyword=&page=0&size=20` — Danh sách lớp toàn hệ thống (`ADMIN`)
Trả về toàn bộ lớp học dùng DTO projection kèm số thành viên `ACTIVE`, tránh N+1 query.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 10,
            "code": "K19MANG1",
            "name": "Lập trình Mạng - K19A",
            "description": "Lớp học phần Lập trình Mạng kỳ 1",
            "subject": "Mạng máy tính",
            "status": "OPEN",
            "ownerId": 2,
            "ownerName": "TS. Trần Văn B",
            "ownerEmail": "gv01@actvn.edu.vn",
            "memberCount": 42,
            "version": 1,
            "createdAt": "2026-11-01T08:00:00Z",
            "updatedAt": "2026-11-20T09:15:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    },
    "traceId": "a6a1b2c3d4f50001"
}
```

### 3.2 `PUT /api/v1/admin/classes/{id}/status` — Khóa / mở / lưu trữ lớp học (`ADMIN`)
Kiểm tra `version` (nếu truyền qua body hoặc header `If-Match`) và ghi `audit_logs` (`ADMIN_CLASSROOM_STATUS_UPDATED`).

- **Request:**
```json
{
    "status": "CLOSED",
    "version": 1
}
```
*(Giá trị `status` hợp lệ: `OPEN`, `CLOSED`, `ARCHIVED`).*
- **Response `200 OK`:** Trả về `AdminClassroomResponse` sau cập nhật với `version` mới.

---

## 4. Quản lý báo cáo (`/api/v1/admin/reports`, `ADMIN`)

### 4.1 `GET /api/v1/admin/reports?status=&type=&page=0&size=20` — Danh sách báo cáo (`ADMIN`)
Hỗ trợ lọc theo `status` (`OPEN`, `RESOLVED`) và `type` (`AI_INACCURATE`, `CLASS_ABUSE`, `CHAT_ABUSE`, `OTHER`).

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 301,
            "reporterId": 11,
            "reporterName": "Nguyễn Văn A",
            "reporterEmail": "sv01@actvn.edu.vn",
            "type": "AI_INACCURATE",
            "targetType": "AI_CHAT_MESSAGE",
            "targetId": 812,
            "detail": "Câu trả lời trích dẫn sai số trang tài liệu.",
            "status": "OPEN",
            "resolutionNote": null,
            "handledById": null,
            "handledByName": null,
            "handledAt": null,
            "createdAt": "2026-11-25T14:40:00Z",
            "updatedAt": "2026-11-25T14:40:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    },
    "traceId": "a6a1b2c3d4f50002"
}
```

### 4.2 `PUT /api/v1/admin/reports/{id}` — Xử lý báo cáo (`ADMIN`)
Cập nhật `status` (mặc định `RESOLVED`), `resolutionNote`, `handledBy`, `handledAt` và ghi `audit_logs` (`REPORT_RESOLVED`).

- **Request:**
```json
{
    "status": "RESOLVED",
    "resolutionNote": "Đã kiểm tra nội dung và nhắc nhở giảng viên phụ trách."
}
```
- **Response `200 OK`:** Trả về `ReportResponse` đã cập nhật.

---

## 5. Cấu hình hệ thống (`/api/v1/admin/settings`, `ADMIN`)

### 5.1 `GET /api/v1/admin/settings` — Xem cấu hình hệ thống (`ADMIN`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "settingKey": "AI_DAILY_QUOTA_PER_USER",
            "settingValue": "50",
            "description": "Số lượt gọi AI tối đa/ngày/người dùng (UTC)",
            "updatedById": null,
            "updatedByName": null,
            "updatedAt": "2026-11-25T00:00:00Z"
        },
        {
            "settingKey": "GOOGLE_ALLOW_PERSONAL_ACCOUNTS",
            "settingValue": "false",
            "description": "Chỉ cho phép tài khoản Học viện trừ khi Admin bật",
            "updatedById": null,
            "updatedByName": null,
            "updatedAt": "2026-11-25T00:00:00Z"
        },
        {
            "settingKey": "MAX_UPLOAD_FILE_SIZE_MB",
            "settingValue": "20",
            "description": "Dung lượng tối đa (MB) cho mỗi tệp tải lên hệ thống",
            "updatedById": null,
            "updatedByName": null,
            "updatedAt": "2026-11-25T00:00:00Z"
        }
    ],
    "traceId": "a6a1b2c3d4f50003"
}
```

### 5.2 `PUT /api/v1/admin/settings` — Cập nhật cấu hình hệ thống (`ADMIN`)
Kiểm tra giá trị hợp lệ theo từng khóa (`AI_DAILY_QUOTA_PER_USER`, `MAX_UPLOAD_FILE_SIZE_MB` là số nguyên dương; `GOOGLE_ALLOW_PERSONAL_ACCOUNTS` là `true`/`false`), làm mới bộ nhớ đệm cấu hình và ghi `audit_logs` (`SYSTEM_SETTINGS_UPDATED`).

- **Request:**
```json
{
    "settings": [
        {
            "settingKey": "AI_DAILY_QUOTA_PER_USER",
            "settingValue": "80"
        },
        {
            "settingKey": "GOOGLE_ALLOW_PERSONAL_ACCOUNTS",
            "settingValue": "true"
        }
    ]
}
```
- **Response `200 OK`:** Trả về danh sách `SystemSettingResponse` đầy đủ sau khi cập nhật.

---

## 6. Nhật ký kiểm toán (`/api/v1/admin/audit-logs`, `ADMIN`)

### 6.1 `GET /api/v1/admin/audit-logs?userId=&action=&page=0&size=30` — Tra cứu Audit Log (`ADMIN`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 901,
            "userId": 1,
            "userEmail": "admin@actvn.edu.vn",
            "action": "SYSTEM_SETTINGS_UPDATED",
            "objectType": "SYSTEM_SETTING",
            "objectId": null,
            "metadataJson": {
                "updatedKeys": ["AI_DAILY_QUOTA_PER_USER", "GOOGLE_ALLOW_PERSONAL_ACCOUNTS"]
            },
            "createdAt": "2026-11-25T14:45:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 30,
        "totalElements": 1,
        "totalPages": 1
    },
    "traceId": "a6a1b2c3d4f50004"
}
```
