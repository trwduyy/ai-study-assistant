# AI Study Assistant — API Contract Đợt 5: RAG, Flashcards, Goals, Tasks & AI Study Plan (V2)

Tài liệu hợp đồng API chi tiết cho **Đợt 5** (Mục 5.4/5.5 — Flashcards & SM-2 Spaced Repetition `FR-08`; Mục 5.9 — Mục tiêu, Task theo ngày & Lộ trình học tập AI `FR-17`).

---

## 1. Quy ước chung

- **Base URL:** `http://localhost:8080/api/v1` (Production: `https://{host}/api/v1`)
- **Xác thực:** `Authorization: Bearer <accessToken>` cho toàn bộ endpoint.
- **Định dạng:** JSON UTF-8, thụt lề **4 spaces**, thời gian chuẩn **ISO-8601 UTC** (`yyyy-MM-dd'T'HH:mm:ss'Z'`), ngày theo `yyyy-MM-dd`.
- **Chuẩn phản hồi:** Mọi endpoint đều trả về `ApiResponse<T>` V2 (`success`, `data`, `meta`, `error`, `traceId`).
- **Quyền truy cập:** Toàn bộ Flashcard Decks, Goals, Tasks và Study Plans chỉ thuộc quyền sở hữu của người tạo (`ownerId == currentUserId`). Người dùng khác truy cập nhận `403 NOT_RESOURCE_OWNER`.

---

## 2. Module Flashcards & Spaced Repetition (Đợt 5A — FR-08)

### 2.1 `POST /api/v1/ai/flashcards` — AI sinh bộ Flashcards bất đồng bộ (`S, T`)
Kiểm tra quyền sở hữu tài liệu đã `EXTRACTED`, tạo `ai_job` loại `FLASHCARD` và trả về `202 Accepted`. Sau khi hoàn tất, `resultType = "FLASHCARD_DECK"`, `resultId = deckId`.

- **Request:**
```json
{
    "materialIds": [401, 402],
    "deckId": null,
    "deckTitle": "Flashcards Tầng giao vận TCP/UDP",
    "courseId": 101,
    "cardCount": 10
}
```
- **Response `202 Accepted`:**
```json
{
    "success": true,
    "data": {
        "jobId": "6f9c2b8d-4e1a-4b70-9c11-2a3b4c5d6e7f",
        "status": "PENDING"
    },
    "traceId": "f5a1b2c3d4e50001"
}
```

### 2.2 `GET /api/v1/flashcard-decks?courseId=&page=0&size=20` — Danh sách bộ thẻ (`S`)
Trả về danh sách bộ thẻ của người dùng kèm tổng số thẻ (`totalCards`) và số thẻ đến hạn ôn hôm nay (`dueCardsCount`).

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": [
        {
            "id": 301,
            "courseId": 101,
            "courseName": "Mạng máy tính",
            "title": "Flashcards Tầng giao vận TCP/UDP",
            "totalCards": 10,
            "dueCardsCount": 4,
            "createdAt": "2026-11-25T14:00:00Z",
            "updatedAt": "2026-11-25T14:00:00Z"
        }
    ],
    "meta": {
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1
    },
    "traceId": "f5a1b2c3d4e50002"
}
```

### 2.3 `POST /api/v1/flashcard-decks` — Tạo bộ thẻ thủ công (`S`)
- **Request:**
```json
{
    "courseId": 101,
    "title": "Từ vựng & Giao thức Mạng"
}
```
- **Response `201 Created`:** Trả về object `FlashcardDeckSummaryResponse`.

### 2.4 `GET /api/v1/flashcard-decks/{id}` — Xem chi tiết bộ thẻ & danh sách thẻ (`S (chủ)`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "id": 301,
        "courseId": 101,
        "courseName": "Mạng máy tính",
        "title": "Flashcards Tầng giao vận TCP/UDP",
        "totalCards": 1,
        "dueCardsCount": 1,
        "cards": [
            {
                "id": 701,
                "deckId": 301,
                "front": "Cơ chế Three-way Handshake của TCP gồm những cờ nào?",
                "back": "SYN → SYN-ACK → ACK dùng để thiết lập kết nối tin cậy.",
                "sourceChunkId": 884,
                "lastRating": null,
                "easeFactor": 2.5,
                "intervalDays": 0,
                "repetitions": 0,
                "nextReviewAt": null,
                "createdAt": "2026-11-25T14:05:00Z",
                "updatedAt": "2026-11-25T14:05:00Z"
            }
        ],
        "createdAt": "2026-11-25T14:00:00Z",
        "updatedAt": "2026-11-25T14:00:00Z"
    },
    "traceId": "f5a1b2c3d4e50004"
}
```

### 2.5 `PUT /api/v1/flashcard-decks/{id}` & `DELETE /api/v1/flashcard-decks/{id}` — Sửa / Xóa bộ thẻ (`S (chủ)`)
- **`PUT` Request:**
```json
{
    "courseId": 101,
    "title": "Flashcards Tầng giao vận (Cập nhật)"
}
```
- **`PUT` Response `200 OK`:** Trả về `FlashcardDeckSummaryResponse`.
- **`DELETE` Response `200 OK`:** `data: null`.

### 2.6 `POST /api/v1/flashcard-decks/{id}/cards` — Thêm thẻ thủ công vào bộ thẻ (`S (chủ)`)
- **Request:**
```json
{
    "front": "UDP khác TCP ở điểm cốt lõi nào?",
    "back": "UDP không thiết lập kết nối, không đảm bảo thứ tự và độ tin cậy như TCP nhưng độ trễ thấp.",
    "sourceChunkId": null
}
```
- **Response `201 Created`:** Trả về object `FlashcardItemResponse`.

### 2.7 `GET /api/v1/flashcard-decks/{id}/review` — Lấy danh sách thẻ cần ôn hôm nay (`S (chủ)`)
Trả về các thẻ trong bộ thẻ chưa từng được ôn hoặc có `nextReviewAt <= Instant.now()`.

- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "deckId": 301,
        "deckTitle": "Flashcards Tầng giao vận TCP/UDP",
        "dueCount": 1,
        "cards": [
            {
                "id": 701,
                "deckId": 301,
                "front": "Cơ chế Three-way Handshake của TCP gồm những cờ nào?",
                "back": "SYN → SYN-ACK → ACK dùng để thiết lập kết nối tin cậy.",
                "sourceChunkId": 884,
                "lastRating": null,
                "easeFactor": 2.5,
                "intervalDays": 0,
                "repetitions": 0,
                "nextReviewAt": null,
                "createdAt": "2026-11-25T14:05:00Z",
                "updatedAt": "2026-11-25T14:05:00Z"
            }
        ]
    },
    "traceId": "f5a1b2c3d4e50007"
}
```

### 2.8 `POST /api/v1/flashcards/{id}/reviews` — Chấm mức nhớ theo SM-2 (`S (chủ)`)
- **Request:**
```json
{
    "rating": "OK"
}
```
*(Giá trị hợp lệ: `FORGOT`, `HARD`, `OK`, `EASY`).*
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "reviewId": 901,
        "cardId": 701,
        "rating": "OK",
        "easeFactor": 2.5,
        "intervalDays": 1,
        "repetitions": 1,
        "nextReviewAt": "2026-11-26T14:15:00Z",
        "reviewedAt": "2026-11-25T14:15:00Z"
    },
    "traceId": "f5a1b2c3d4e50008"
}
```

---

## 3. Module Goals & Daily Tasks (Đợt 5B — FR-17)

### 3.1 `GET /api/v1/goals` & `POST /api/v1/goals` — Mục tiêu học tập (`S`)
- **`POST` Request:**
```json
{
    "title": "Đạt 9.0 môn Mạng máy tính",
    "subject": "Mạng máy tính",
    "dueDate": "2026-12-20",
    "priority": "HIGH",
    "freeTime": {
        "weekdays": ["MON", "WED", "FRI"],
        "hoursPerDay": 2
    }
}
```
- **`POST` Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 51,
        "title": "Đạt 9.0 môn Mạng máy tính",
        "subject": "Mạng máy tính",
        "dueDate": "2026-12-20",
        "priority": "HIGH",
        "status": "ACTIVE",
        "freeTime": {
            "weekdays": ["MON", "WED", "FRI"],
            "hoursPerDay": 2
        },
        "createdAt": "2026-11-25T14:20:00Z",
        "updatedAt": "2026-11-25T14:20:00Z"
    },
    "traceId": "g5b1c2d3e4f50001"
}
```

### 3.2 `PUT /api/v1/goals/{id}` & `DELETE /api/v1/goals/{id}` — Sửa / Xóa mục tiêu (`S (chủ)`)
- **`PUT` Request:** Cập nhật `title`, `subject`, `dueDate`, `priority`, `status` (`ACTIVE`, `COMPLETED`, `CANCELLED`), `freeTime`.
- **`DELETE` Response `200 OK`:** `data: null`.

### 3.3 `GET /api/v1/tasks?from=&to=&status=&goalId=` & `POST /api/v1/tasks` — Task theo ngày (`S`)
- **`POST` Request:**
```json
{
    "goalId": 51,
    "title": "Ôn tập kiểm soát tắc nghẽn TCP",
    "taskDate": "2026-11-26",
    "priority": "HIGH",
    "remindAt": "2026-11-26T01:00:00Z"
}
```
- **`POST` Response `201 Created`:**
```json
{
    "success": true,
    "data": {
        "id": 401,
        "goalId": 51,
        "title": "Ôn tập kiểm soát tắc nghẽn TCP",
        "taskDate": "2026-11-26",
        "priority": "HIGH",
        "status": "TODO",
        "remindAt": "2026-11-26T01:00:00Z",
        "createdAt": "2026-11-25T14:25:00Z",
        "updatedAt": "2026-11-25T14:25:00Z"
    },
    "traceId": "t5b1c2d3e4f50001"
}
```

### 3.4 `PUT /api/v1/tasks/{id}`, `DELETE /api/v1/tasks/{id}`, `PUT /api/v1/tasks/{id}/complete` (`S (chủ)`)
- **`PUT /api/v1/tasks/{id}/complete` Response `200 OK`:** Trả về object Task với `"status": "DONE"` (hỗ trợ body tùy chọn `{ "completed": true | false }` để toggle `DONE` / `TODO`).

---

## 4. AI Study Plan — Đề xuất lộ trình học tập (Đợt 5B — FR-17)

### 4.1 `POST /api/v1/ai/study-plans` — Yêu cầu AI đề xuất lộ trình (`S`)
Tổng hợp mục tiêu, lịch rảnh/bận và chủ đề cần ôn tập trong khoảng `[fromDate, toDate]`, tạo `ai_job` loại `PLAN` và trả `202 Accepted`.

- **Request:**
```json
{
    "goalId": 51,
    "fromDate": "2026-11-26",
    "toDate": "2026-12-02"
}
```
- **Response `202 Accepted`:**
```json
{
    "success": true,
    "data": {
        "jobId": "9a8b7c6d-5e4f-3a2b-1c0d-112233445566",
        "status": "PENDING"
    },
    "traceId": "p5b1c2d3e4f50001"
}
```

### 4.2 `GET /api/v1/study-plans/{id}` — Xem chi tiết lộ trình đề xuất (`S (chủ)`)
- **Response `200 OK`:**
```json
{
    "success": true,
    "data": {
        "id": 88,
        "jobId": "9a8b7c6d-5e4f-3a2b-1c0d-112233445566",
        "status": "PROPOSED",
        "rationale": "Ưu tiên ôn lại chủ đề TCP Congestion Control (độ chính xác 45%) vào các khung giờ trống buổi tối.",
        "model": "gemini-4-hydrogen",
        "promptVersion": "plan-v2.0",
        "items": [
            {
                "id": 601,
                "planId": 88,
                "planStatus": "PROPOSED",
                "title": "Ôn lại lý thuyết Slow Start & Fast Recovery",
                "plannedStart": "2026-11-26T12:30:00Z",
                "plannedEnd": "2026-11-26T13:30:00Z",
                "decision": "PENDING",
                "taskId": null,
                "hasConflict": false,
                "conflictingEvents": []
            }
        ],
        "createdAt": "2026-11-25T14:30:00Z",
        "updatedAt": "2026-11-25T14:30:00Z"
    },
    "traceId": "p5b1c2d3e4f50002"
}
```

### 4.3 `PUT /api/v1/study-plans/{id}/items/{itemId}` — Chấp nhận, sửa hoặc từ chối từng mục (`S (chủ)`)
Khi `decision` là `ACCEPTED` hoặc `EDITED`, Backend tự động tạo một `Task` tương ứng gắn `taskId` vào `study_plan_items` và cập nhật trạng thái lộ trình (`PROPOSED` → `PARTIAL` → `CLOSED`).

- **Request:**
```json
{
    "decision": "EDITED",
    "title": "Ôn tập Slow Start & làm 15 câu quiz TCP",
    "plannedStart": "2026-11-26T13:00:00Z",
    "plannedEnd": "2026-11-26T14:00:00Z"
}
```
*(Giá trị `decision` hợp lệ: `ACCEPTED`, `EDITED`, `REJECTED`. Nếu mục đã được xử lý trước đó sẽ trả `409 CONFLICT`.)*
- **Response `200 OK`:** Trả về `StudyPlanItemResponse` kèm `taskId`, `planStatus`, `hasConflict`, `conflictingEvents`.
