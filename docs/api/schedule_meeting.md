# AI Study Assistant — API Contract Đợt 4: Lịch, Sự kiện lớp & Google Calendar (V2)

Tài liệu hợp đồng API chi tiết cho **Đợt 4** (Mục 5.9 — Sự kiện lớp/họp `meeting`, Lịch tổng hợp, Sự kiện cá nhân, Import `.ics`/CSV và Đồng bộ Google Calendar chỉ đọc `schedule`, `FR-15`, `FR-16`).

---

## 1. Quy ước chung

- **Base URL:** `http://localhost:8080/api/v1` (Production: `https://{host}/api/v1`)
- **Xác thực:** `Authorization: Bearer <accessToken>` cho toàn bộ endpoint (trừ `GET /integrations/google/callback` dùng `state` đã ký để gắn với `userId`).
- **Định dạng:** JSON UTF-8, thụt lề **4 spaces**, thời gian lưu và trao đổi chuẩn **ISO-8601 UTC** (`yyyy-MM-dd'T'HH:mm:ss'Z'`), luôn đi kèm trường `timezone` IANA gốc (ví dụ `Asia/Ho_Chi_Minh`).
- **Chuẩn phản hồi:** Mọi endpoint REST (trừ tải tệp CSV mẫu `Resource`) đều trả về `ApiResponse<T>` V2 gồm `success`, `data`, `meta` (khi phân trang), `error` (khi lỗi), `traceId`.
- **Ký hiệu vai trò:**
    - `S`: Sinh viên (`STUDENT`)
    - `T`: Giảng viên lớp (`OWNER_TEACHER` / `CO_TEACHER` đang `ACTIVE`)
    - `TV`: Thành viên đang `ACTIVE` trong lớp
    - `All`: Mọi người dùng đã đăng nhập
- **Khóa lạc quan (`version` / `If-Match`):**
    - `PUT /events/{id}` và `PUT /personal-events/{id}` bắt buộc gửi `version` trong body hoặc header `If-Match`. Sai `version` trả `409 VERSION_CONFLICT`.
- **Phân quyền cứng:**
    - Sự kiện lớp học (`classId != null`) chỉ do giảng viên lớp tạo/sửa/hủy qua `/classes/{id}/events` và `/events/{id}`.
    - Sinh viên không được phép sửa hoặc xóa sự kiện lớp học; cố tình gọi `/personal-events/{id}` lên sự kiện lớp trả `403 FORBIDDEN`.
    - Google Calendar tuân thủ **chỉ đọc** (D-06): không ghi ngược lên Google, link Meet của lớp do giảng viên dán thủ công.

---

## 2. Module Meeting — Sự kiện lớp học & Cuộc họp (Đợt 4A)

### 2.1 `POST /api/v1/classes/{id}/events` — Tạo sự kiện lớp học (`T`)
Chỉ giảng viên `ACTIVE` của lớp (`OWNER_TEACHER` hoặc `CO_TEACHER`) được phép tạo sự kiện loại `CLASS`, `TEST` hoặc `MEETING`. Hỗ trợ dán `meetingUrl` thủ công (`http://` hoặc `https://`). Trong cùng transaction sẽ ghi `notification_outbox` (`CLASS_EVENT_CREATED`) tới toàn bộ sinh viên `ACTIVE` trong lớp.

- **Request:**
```json
{
    "type": "MEETING",
    "title": "Họp giải đáp đồ án Lập trình Mạng",
    "description": "Thảo luận kiến trúc Socket đa luồng và chuẩn bị kiểm tra giữa kỳ.",
    "startAt": "2026-11-26T12:30:00Z",
    "endAt": "2026-11-26T14:00:00Z",
    "timezone": "Asia/Ho_Chi_Minh",
    "meetingUrl": "https://meet.google.com/abc-defg-hij"
}
```
- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 1001,
        "ownerId": 2,
        "classId": 10,
        "className": "Lập trình Mạng - K19A",
        "type": "MEETING",
        "title": "Họp giải đáp đồ án Lập trình Mạng",
        "description": "Thảo luận kiến trúc Socket đa luồng và chuẩn bị kiểm tra giữa kỳ.",
        "startAt": "2026-11-26T12:30:00Z",
        "endAt": "2026-11-26T14:00:00Z",
        "timezone": "Asia/Ho_Chi_Minh",
        "status": "UPCOMING",
        "meetingUrl": "https://meet.google.com/abc-defg-hij",
        "source": "MANUAL",
        "version": 0,
        "createdAt": "2026-11-25T11:00:00Z",
        "updatedAt": "2026-11-25T11:00:00Z"
    },
    "traceId": "e4a1b2c3d4f50001"
}
```
- **Lỗi thường gặp:** `400 VALIDATION_ERROR` (`endAt <= startAt`, `type = PERSONAL`, `meetingUrl` hoặc `timezone` không hợp lệ), `403 FORBIDDEN` / `403 NOT_CLASS_MEMBER`, `404 CLASS_NOT_FOUND`.

### 2.2 `GET /api/v1/classes/{id}/events` — Danh sách sự kiện của lớp (`TV`)
Trả về các sự kiện chưa bị hủy của lớp (hỗ trợ lọc `?from=&to=` và phân trang `?page=0&size=20&sort=startAt,asc`).

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 1001,
            "ownerId": 2,
            "classId": 10,
            "className": "Lập trình Mạng - K19A",
            "type": "MEETING",
            "title": "Họp giải đáp đồ án Lập trình Mạng",
            "description": "Thảo luận kiến trúc Socket đa luồng và chuẩn bị kiểm tra giữa kỳ.",
            "startAt": "2026-11-26T12:30:00Z",
            "endAt": "2026-11-26T14:00:00Z",
            "timezone": "Asia/Ho_Chi_Minh",
            "status": "UPCOMING",
            "meetingUrl": "https://meet.google.com/abc-defg-hij",
            "source": "MANUAL",
            "version": 0,
            "createdAt": "2026-11-25T11:00:00Z",
            "updatedAt": "2026-11-25T11:00:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    },
    "traceId": "e4a1b2c3d4f50002"
}
```

### 2.3 `PUT /api/v1/events/{id}` — Cập nhật / đổi lịch sự kiện lớp (`T`)
Kiểm tra `version` (body hoặc `If-Match`). Sai `version` trả `409 VERSION_CONFLICT`. Ghi `notification_outbox` (`CLASS_EVENT_UPDATED`) tới toàn bộ sinh viên `ACTIVE` trong lớp.

- **Request:**
```json
{
    "type": "MEETING",
    "title": "Họp giải đáp đồ án (Đổi giờ)",
    "description": "Chuyển sang 20:00 tối thứ Năm.",
    "startAt": "2026-11-26T13:00:00Z",
    "endAt": "2026-11-26T14:30:00Z",
    "timezone": "Asia/Ho_Chi_Minh",
    "meetingUrl": "https://meet.google.com/abc-defg-hij",
    "version": 0
}
```
- **Response `200 OK`:** Trả về object `ClassEventResponse` với `version` mới (`1`).

### 2.4 `DELETE /api/v1/events/{id}` — Hủy sự kiện lớp (`T`)
Đánh dấu `status = CANCELLED` (không xóa vật lý để giữ dấu vết lịch sử) và ghi `notification_outbox` (`CLASS_EVENT_CANCELLED`) báo hủy lịch cho toàn bộ sinh viên `ACTIVE` trong lớp.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": null,
    "traceId": "e4a1b2c3d4f50004"
}
```

---

## 3. Module Schedule — Lịch tổng hợp & Sự kiện cá nhân (Đợt 4A)

### 3.1 `GET /api/v1/calendar?from=&to=&view=` — Lịch tổng hợp (`All`)
Trả về gộp các sự kiện chưa hủy giao với khoảng `[from, to]` (`startAt < to AND endAt > from`) gồm:
1. Sự kiện cá nhân / đã import / đồng bộ Google của người dùng (`classId == null AND ownerId == currentUserId`).
2. Sự kiện lớp học thuộc các lớp người dùng đang là thành viên `ACTIVE` (`classId IN activeClassIds`).

Sắp xếp theo `startAt ASC`. Gắn `readOnly: true` cho sự kiện nguồn `GOOGLE` và sự kiện lớp học khi người dùng là `STUDENT` trong lớp đó.
Tham số `view` hỗ trợ `DAY`, `WEEK`, `MONTH` (mặc định `WEEK` nếu không truyền `from`/`to`).

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 1001,
            "ownerId": 2,
            "classId": 10,
            "className": "Lập trình Mạng - K19A",
            "importId": null,
            "type": "MEETING",
            "title": "Họp giải đáp đồ án Lập trình Mạng",
            "description": "Thảo luận kiến trúc Socket đa luồng.",
            "startAt": "2026-11-26T12:30:00Z",
            "endAt": "2026-11-26T14:00:00Z",
            "timezone": "Asia/Ho_Chi_Minh",
            "status": "UPCOMING",
            "meetingUrl": "https://meet.google.com/abc-defg-hij",
            "source": "MANUAL",
            "externalId": null,
            "readOnly": true,
            "version": 0,
            "createdAt": "2026-11-25T11:00:00Z",
            "updatedAt": "2026-11-25T11:00:00Z"
        },
        {
            "id": 1005,
            "ownerId": 12,
            "classId": null,
            "className": null,
            "importId": null,
            "type": "PERSONAL",
            "title": "Tự ôn tập chương 3 TCP",
            "description": "Làm lại 20 câu quiz kiểm soát tắc nghẽn.",
            "startAt": "2026-11-26T14:30:00Z",
            "endAt": "2026-11-26T16:00:00Z",
            "timezone": "Asia/Ho_Chi_Minh",
            "status": "UPCOMING",
            "meetingUrl": null,
            "source": "MANUAL",
            "externalId": null,
            "readOnly": false,
            "version": 0,
            "createdAt": "2026-11-25T11:20:00Z",
            "updatedAt": "2026-11-25T11:20:00Z"
        }
    ],
    "traceId": "s4a1b2c3d4f50001"
}
```

### 3.2 `GET /api/v1/personal-events` — Danh sách sự kiện cá nhân (`S / All`)
Trả về các sự kiện cá nhân (`type = PERSONAL`, `classId == null`, `status != CANCELLED`) của người dùng hiện tại. Hỗ trợ lọc `?from=&to=` và phân trang `?page=0&size=20&sort=startAt,asc`.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 1005,
            "ownerId": 12,
            "type": "PERSONAL",
            "title": "Tự ôn tập chương 3 TCP",
            "description": "Làm lại 20 câu quiz kiểm soát tắc nghẽn.",
            "startAt": "2026-11-26T14:30:00Z",
            "endAt": "2026-11-26T16:00:00Z",
            "timezone": "Asia/Ho_Chi_Minh",
            "status": "UPCOMING",
            "meetingUrl": null,
            "source": "MANUAL",
            "version": 0,
            "createdAt": "2026-11-25T11:20:00Z",
            "updatedAt": "2026-11-25T11:20:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    },
    "traceId": "s4a1b2c3d4f50002"
}
```

### 3.3 `POST /api/v1/personal-events` — Tạo sự kiện cá nhân (`S / All`)
- **Request:**
```json
{
    "title": "Tự ôn tập chương 3 TCP",
    "description": "Làm lại 20 câu quiz kiểm soát tắc nghẽn.",
    "startAt": "2026-11-26T14:30:00Z",
    "endAt": "2026-11-26T16:00:00Z",
    "timezone": "Asia/Ho_Chi_Minh",
    "meetingUrl": null
}
```
- **Response `201 Created`:** Trả về object `PersonalEventResponse` vừa tạo.

### 3.4 `PUT /api/v1/personal-events/{id}` — Sửa sự kiện cá nhân (`S (chủ)`)
Chỉ chủ sở hữu được sửa sự kiện cá nhân (`classId == null` và `source != GOOGLE`), kiểm tra `version`. Nếu cố tình thao tác lên sự kiện lớp học (`classId != null`) hoặc sự kiện của người khác trả `403 FORBIDDEN`.

- **Request:**
```json
{
    "title": "Tự ôn tập chương 3 TCP & UDP",
    "description": "Bổ sung phần kiểm soát lỗi.",
    "startAt": "2026-11-26T14:30:00Z",
    "endAt": "2026-11-26T16:30:00Z",
    "timezone": "Asia/Ho_Chi_Minh",
    "meetingUrl": null,
    "version": 0
}
```
- **Response `200 OK`:** Trả về object `PersonalEventResponse` với `version` mới (`1`).

### 3.5 `DELETE /api/v1/personal-events/{id}` — Xóa (hủy) sự kiện cá nhân (`S (chủ)`)
Chuyển trạng thái sang `CANCELLED`. Nếu `classId != null` trả `403 FORBIDDEN`.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": null,
    "traceId": "s4a1b2c3d4f50005"
}
```

---

## 4. Import lịch `.ics` / CSV (Đợt 4B — Mục 5.9, FR-15)

### 4.1 `GET /api/v1/calendar/import-template` — Tải file CSV mẫu (`S / All`)
Trả về tệp `text/csv; charset=UTF-8` với header `Content-Disposition: attachment; filename="calendar-template.csv"` và các cột: `title,description,start_at,end_at,timezone,location`.

### 4.2 `POST /api/v1/calendar/imports/preview` — Tải `.ics`/CSV, xem trước & phát hiện trùng giờ (`S / All`)
Request `multipart/form-data` gồm `file` (`.ics` hoặc `.csv`, tối đa 5MB) và tùy chọn `defaultTimezone` (mặc định `Asia/Ho_Chi_Minh`). Backend phân tích từng dòng/VEVENT, quy đổi UTC (giữ `timezone` gốc), kiểm tra trùng giờ với lịch hiện có (`a.start < b.end && b.start < a.end`) và lưu bản nháp vào `calendar_imports` (`status = PREVIEW`). Nếu lỗi định dạng sẽ trả `400 VALIDATION_ERROR` kèm số dòng.

- **Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "importId": 45,
        "sourceType": "ICS",
        "fileName": "thoi-khoa-bieu-hk1.ics",
        "timezone": "Asia/Ho_Chi_Minh",
        "status": "PREVIEW",
        "totalParsed": 1,
        "conflictCount": 1,
        "items": [
            {
                "itemIndex": 0,
                "externalId": "uid-kma-101@actvn.edu.vn",
                "title": "An toàn mạng máy tính",
                "description": "Học lý thuyết",
                "location": "Phòng 402-TA2",
                "startAt": "2026-11-27T00:00:00Z",
                "endAt": "2026-11-27T02:30:00Z",
                "timezone": "Asia/Ho_Chi_Minh",
                "hasConflict": true,
                "conflictingEvents": [
                    {
                        "eventId": 1001,
                        "title": "Họp giải đáp đồ án Lập trình Mạng",
                        "type": "MEETING",
                        "classId": 10,
                        "className": "Lập trình Mạng - K19A",
                        "startAt": "2026-11-27T01:00:00Z",
                        "endAt": "2026-11-27T02:00:00Z",
                        "source": "MANUAL"
                    }
                ]
            }
        ]
    },
    "traceId": "s4b1b2c3d4f50001"
}
```

### 4.3 `POST /api/v1/calendar/imports/{importId}/confirm` — Xác nhận nhập lịch (`S / All`)
- **Request:**
```json
{
    "acceptedItemIndices": [0, 1, 3]
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "importId": 45,
        "status": "CONFIRMED",
        "importedCount": 3,
        "skippedCount": 0,
        "createdEventIds": [2010, 2011, 2012]
    },
    "traceId": "s4b1b2c3d4f50002"
}
```

---

## 5. Tích hợp Google Calendar chỉ đọc (Đợt 4B — Mục 5.9, FR-16, D-06)

### 5.1 `GET /api/v1/integrations/google/authorize` — Bắt đầu OAuth (`All`)
Kiểm tra chính sách `system_settings` (tài khoản cá nhân cần Admin cho phép nếu áp dụng), sinh URL ủy quyền Google với scope chỉ đọc (`https://www.googleapis.com/auth/calendar.readonly`).

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "authorizeUrl": "https://accounts.google.com/o/oauth2/v2/auth?client_id=...&scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcalendar.readonly&access_type=offline&state=...",
        "scopes": [
            "https://www.googleapis.com/auth/calendar.readonly"
        ]
    },
    "traceId": "g4b1b2c3d4f50001"
}
```

### 5.2 `GET /api/v1/integrations/google/callback?code=&state=` — Nhận mã OAuth (`All`)
Đổi `code` lấy `refresh_token`, mã hóa AES-GCM và lưu vào `external_accounts` (`provider = GOOGLE`).

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "provider": "GOOGLE",
        "connected": true,
        "connectedAt": "2026-11-25T11:30:00Z",
        "lastSyncedAt": null
    },
    "traceId": "g4b1b2c3d4f50002"
}
```

### 5.3 `POST /api/v1/integrations/google/sync` — Đồng bộ đọc lịch Google (`All`)
Chạy gọi Google Calendar API **ngoài transaction**, sau đó upsert các sự kiện với `source = GOOGLE` và `external_id` vào bảng `events` (`owner_id = currentUserId`). Không ghi ngược lên Google.

- **Request (tùy chọn):**
```json
{
    "from": "2026-11-25T00:00:00Z",
    "to": "2026-12-25T23:59:59Z"
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "provider": "GOOGLE",
        "syncedCount": 8,
        "createdCount": 5,
        "updatedCount": 3,
        "cancelledCount": 0,
        "lastSyncedAt": "2026-11-25T11:32:00Z"
    },
    "traceId": "g4b1b2c3d4f50003"
}
```

### 5.4 `DELETE /api/v1/integrations/google` — Ngắt kết nối & xóa token (`All`)
Xóa bản ghi `external_accounts` (`provider = GOOGLE`) và có thể dọn sự kiện đồng bộ từ Google của người dùng.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": null,
    "traceId": "g4b1b2c3d4f50004"
}
```
