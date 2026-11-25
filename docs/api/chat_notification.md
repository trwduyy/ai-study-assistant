# AI Study Assistant — API Contract Đợt 3: Chat & Thông báo (V2)

Tài liệu hợp đồng API chi tiết cho **Đợt 3** (Mục 5.10 — Chat thời gian thực `FR-18` & Thông báo đa kênh `FR-19`).

---

## 1. Quy ước chung & WebSocket / STOMP

- **REST Base URL:** `http://localhost:8080/api/v1` (Production: `https://{host}/api/v1`)
- **WebSocket / STOMP Endpoint:**
    - SockJS: `http://localhost:8080/ws` (`ws://localhost:8080/ws/websocket`)
    - Native WebSocket: `ws://localhost:8080/ws-native`
- **Xác thực STOMP `CONNECT`:**
    - Bắt buộc gửi header `Authorization: Bearer <accessToken>` trong frame `CONNECT`.
    - Server xác thực JWT, gán phiên WebSocket theo `userId` (nếu token thiếu, sai hoặc hết hạn sẽ từ chối kết nối).
- **STOMP Destinations:**
    - `SUBSCRIBE /user/queue/notifications`: Nhận thông báo `IN_APP` đẩy thời gian thực (`NotificationResponse`).
    - `SUBSCRIBE /user/queue/ai-jobs`: Nhận cập nhật trạng thái tác vụ AI hoàn tất (`SUCCEEDED` / `FAILED`) dạng `AiJobResponse`.
    - `SUBSCRIBE /topic/conversations/{id}`: Nhận tin nhắn mới trong cuộc trò chuyện.
    - `SEND /app/conversations/{id}/messages`: Gửi tin nhắn thời gian thực (`{ "clientMessageId", "content" }`).
- **Phân trang Cursor:** `?cursor=<id>&limit=30` (trả về `meta`: `{ "size": ..., "nextCursor": "..." }`, `nextCursor = null` nếu hết dữ liệu).

---

## 2. Chat thời gian thực (Mục 5.10, FR-18)

### 2.1 `GET /api/v1/conversations` — Danh sách cuộc trò chuyện (`All`)
Trả về các phòng chat `CLASSROOM` và hội thoại `DIRECT` mà người dùng hiện tại đang tham gia (`left_at IS NULL`), kèm số tin nhắn chưa đọc và tin nhắn cuối cùng.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 15,
            "type": "CLASSROOM",
            "classId": 10,
            "title": "Lập trình Mạng - K19A",
            "unreadCount": 3,
            "lastMessage": {
                "id": 502,
                "senderId": 2,
                "senderName": "TS. Trần Văn B",
                "content": "Các em nhớ nộp bài trước 23:59 nhé.",
                "createdAt": "2026-11-25T10:20:00Z"
            },
            "updatedAt": "2026-11-25T10:20:00Z"
        }
    ],
    "traceId": "d3a1b2c4e5f60001"
}
```

### 2.2 `POST /api/v1/conversations/direct` — Mở hoặc lấy hội thoại 1-1 (`All`)
- **Request:**
```json
{
    "userId": 12
}
```
- **Response `200 OK` / `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 22,
        "type": "DIRECT",
        "classId": null,
        "title": "Nguyễn Văn A",
        "unreadCount": 0,
        "lastMessage": null,
        "updatedAt": "2026-11-25T10:25:00Z"
    },
    "traceId": "d3a1b2c4e5f60002"
}
```

### 2.3 `GET /api/v1/conversations/{id}/messages?cursor=&limit=30` — Lịch sử tin nhắn (`TV`)
Trả về tin nhắn có `id < cursor` (nếu có `cursor`), sắp xếp mới nhất trước hoặc chuẩn cursor để tải bù khi WebSocket reconnect.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 502,
            "conversationId": 15,
            "senderId": 2,
            "senderName": "TS. Trần Văn B",
            "content": "Các em nhớ nộp bài trước 23:59 nhé.",
            "status": "SENT",
            "clientMessageId": "a7d1e2b3-4c5d-6e7f-8a9b-0c1d2e3f4a5b",
            "createdAt": "2026-11-25T10:20:00Z"
        }
    ],
    "meta": {
        "size": 1,
        "nextCursor": null
    },
    "traceId": "d3a1b2c4e5f60003"
}
```

### 2.4 `POST /api/v1/conversations/{id}/messages` — Gửi tin nhắn qua REST dự phòng (`TV`)
Chống trùng lặp theo `(conversation_id, sender_id, client_message_id)` khi client gửi lại lúc mất kết nối.

- **Request:**
```json
{
    "clientMessageId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "content": "Em chào thầy ạ!"
}
```
- **Response `201 Created`:** Trả về object tin nhắn vừa lưu (và đồng thời phát tới `/topic/conversations/{id}`).

### 2.5 `PUT /api/v1/conversations/{id}/read` — Đánh dấu đã đọc đến `lastReadMessageId` (`TV`)
- **Request:**
```json
{
    "lastReadMessageId": 502
}
```
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "conversationId": 15,
        "lastReadMessageId": 502,
        "updatedAt": "2026-11-25T10:26:00Z"
    },
    "traceId": "d3a1b2c4e5f60005"
}
```

### 2.6 STOMP `SEND /app/conversations/{id}/messages` & `SUB /topic/conversations/{id}`
- **Payload gửi:**
```json
{
    "clientMessageId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "content": "Chào cả lớp!"
}
```
- **Payload nhận tại `/topic/conversations/{id}`:**
```json
{
    "id": 503,
    "conversationId": 15,
    "senderId": 12,
    "senderName": "Nguyễn Văn A",
    "content": "Chào cả lớp!",
    "status": "SENT",
    "clientMessageId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "createdAt": "2026-11-25T10:27:00Z"
}
```

---

## 3. Thông báo đa kênh (Mục 5.10, FR-19)

### 3.1 `GET /api/v1/notifications?cursor=&limit=30&unread=` — Danh sách thông báo (`All`)
Phân trang cursor (`id < cursor`), sắp xếp `id DESC`. Tham số `unread=true` chỉ lấy thông báo chưa đọc (`read_at IS NULL`).

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 801,
            "type": "ASSIGNMENT_DUE_SOON",
            "title": "Bài tập sắp đến hạn",
            "body": "Bài tập \"Bài tập tuần 3: Socket TCP\" trong lớp Lập trình Mạng sắp đến hạn nộp.",
            "targetType": "ASSIGNMENT",
            "targetId": 401,
            "readAt": null,
            "createdAt": "2026-11-25T10:30:00Z"
        }
    ],
    "meta": {
        "size": 1,
        "nextCursor": null
    },
    "traceId": "n3a1b2c4e5f60001"
}
```

### 3.2 `GET /api/v1/notifications/unread-count` — Đếm số thông báo chưa đọc (`All`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "unreadCount": 4
    },
    "traceId": "n3a1b2c4e5f60002"
}
```

### 3.3 `PUT /api/v1/notifications/{id}/read` — Đánh dấu đã đọc 1 thông báo (`All`)
- **Response `200 OK`:** Trả về object `NotificationResponse` với `readAt` đã cập nhật.

### 3.4 `PUT /api/v1/notifications/read-all` — Đánh dấu đã đọc tất cả (`All`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": null,
    "traceId": "n3a1b2c4e5f60004"
}
```

### 3.5 `GET /api/v1/notifications/settings` — Xem cấu hình thông báo (`All`)
Trả về cấu hình bật/tắt `inAppEnabled` và `emailEnabled` theo từng loại sự kiện (`CLASS_JOIN_REQUEST_CREATED`, `CLASS_JOIN_REQUEST_APPROVED`, `CLASS_JOIN_REQUEST_REJECTED`, `CLASS_CO_TEACHER_ADDED`, `ASSIGNMENT_CREATED`, `ASSIGNMENT_SUBMITTED`, `ASSIGNMENT_GRADED`, `ASSIGNMENT_REOPENED`, `ASSIGNMENT_DUE_SOON`, `CLASS_TEST_CREATED`).

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "type": "ASSIGNMENT_DUE_SOON",
            "inAppEnabled": true,
            "emailEnabled": true
        },
        {
            "type": "ASSIGNMENT_GRADED",
            "inAppEnabled": true,
            "emailEnabled": false
        }
    ],
    "traceId": "n3a1b2c4e5f60005"
}
```

### 3.6 `PUT /api/v1/notifications/settings` — Cập nhật cấu hình thông báo (`All`)
- **Request:**
```json
{
    "settings": [
        {
            "type": "ASSIGNMENT_DUE_SOON",
            "inAppEnabled": true,
            "emailEnabled": false
        }
    ]
}
```
- **Response `200 OK`:** Trả về danh sách cấu hình đầy đủ sau khi cập nhật.

### 3.7 STOMP `SUB /user/queue/notifications` & `SUB /user/queue/ai-jobs`
- **Payload tại `/user/queue/notifications`:**
```json
{
    "id": 802,
    "type": "ASSIGNMENT_GRADED",
    "title": "Bài tập đã được chấm điểm",
    "body": "Bài tập \"Bài tập tuần 3: Socket TCP\" đã được chấm 9.00 điểm.",
    "targetType": "SUBMISSION",
    "targetId": 990,
    "readAt": null,
    "createdAt": "2026-11-25T10:35:00Z"
}
```
- **Payload tại `/user/queue/ai-jobs`:** Trả về object `AiJobResponse` với `status` là `SUCCEEDED` hoặc `FAILED`.
