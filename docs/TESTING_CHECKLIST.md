# Danh sách Kiểm thử Nghiệm thu V2 — AI Study Assistant

Tài liệu kiểm thử đầu cuối (End-to-End Acceptance Checklist) cho phiên bản V2 (Backend Spring Boot + Frontend Desktop React/Electron), phủ đầy đủ 6 phân hệ chức năng và đóng gói đa nền tảng.

---

## Phân hệ 1: Xác thực V2, Xoay vòng Token & Layout theo Vai trò (FE-1A)
- [ ] Đăng nhập (`POST /api/v1/auth/login`) bằng tài khoản `STUDENT`, `TEACHER`, `ADMIN`: lưu `accessToken` + `refreshToken`, chuyển hướng đúng trang chính theo vai trò (`ADMIN` $\rightarrow$ `/admin/users`, `STUDENT`/`TEACHER` $\rightarrow$ `/dashboard`).
- [ ] Đăng ký (`POST /api/v1/auth/register`) và kiểm tra thông báo lỗi tiếng Việt thân thiện khi email đã tồn tại hoặc mật khẩu chưa đạt chuẩn.
- [ ] **Xoay vòng Refresh Token chống gọi lặp:** Khi `accessToken` hết hạn (`401 TOKEN_EXPIRED`) và có nhiều API chạy song song, chỉ gọi `POST /api/v1/auth/refresh` đúng **1 lần**, xếp hàng các request khác và chạy lại thành công sau khi có token mới.
- [ ] Khi `refreshToken` hết hạn/bị thu hồi: xóa phiên cục bộ, ngắt kết nối WebSocket STOMP và chuyển về `#/login`.
- [ ] Truy cập route trái quyền (ví dụ `STUDENT` mở `#/admin/users` hoặc `#/tests/1/results`): hiển thị trang **403 Không có quyền truy cập** kèm nút quay về trang chính.
- [ ] Truy cập route không tồn tại (`#/duong-dan-la`): hiển thị trang **404 Không tìm thấy trang**.
- [ ] Trạng thái kết nối WebSocket STOMP trên Header hiển thị chính xác (*Đã kết nối trực tiếp / Đang kết nối lại / Lỗi*).

---

## Phân hệ 2: Học tập Cá nhân, Tài liệu & AI Async V2 (FE-1B)
- [ ] **Khóa học $\rightarrow$ Chương $\rightarrow$ Chủ đề (`/ai-materials`, `/courses/:id`):** Tạo, sửa (kiểm tra `version` optimistic locking), xóa mềm khóa học; CRUD chương & chủ đề sắp xếp theo `position`.
- [ ] **Tải lên & Trích xuất bất đồng bộ:** Tải tệp `.pdf`, `.docx`, `.txt` ($\le$ 20MB); trạng thái cập nhật từ *Đang trích xuất* (`EXTRACTING`) sang *Sẵn sàng* (`EXTRACTED`) hoặc *Lỗi* (`FAILED`); nút **Trích xuất lại** hoạt động khi lỗi.
- [ ] **Không gian học tập AI (`/materials/:materialId`):**
  - Xem nội dung văn bản trích xuất phân trang từng đoạn/trang và tải tệp gốc.
  - **AI Tóm tắt:** Chọn độ dài (*Ngắn / Vừa / Chi tiết*) $\rightarrow$ `useAiJob` nhận sự kiện STOMP `/user/queue/ai-jobs` (hoặc polling fallback 2s), có nút **Hủy tác vụ**, render Markdown kèm model & lịch sử bản tóm tắt.
  - **Grounded Chat:** Bật/tắt `allowExternalKnowledge`; hiển thị trích dẫn nguồn (`sources[]`: tên tài liệu + số trang); tách riêng hộp **"Phần AI giải thích thêm"** (`aiAddedNote`); cảnh báo nhẹ khi `grounded === false`; nút **"Báo câu trả lời sai"** gửi `POST /api/v1/ai/feedback`.
  - **Giải thích nhanh khái niệm:** Gửi `POST /api/v1/ai/explanations` và nhận kết quả qua `useAiJob`.
- [ ] **Quiz V2 & Analytics:** Tạo quiz AI; làm bài tự động lưu bản nháp; nộp bài gắn `Idempotency-Key`; xem giải thích chi tiết & trích dẫn; kiểm tra biểu đồ xu hướng điểm số, lỗi sai thường gặp và chủ đề gợi ý ôn tập trên `/dashboard`.
- [ ] **Flashcards & SM-2 (`/flashcards`, `/flashcards/:deckId/review`):** Sinh bộ thẻ AI hoặc thêm thủ công; lật thẻ 3D bằng phím `Space`, chấm điểm SM-2 bằng phím `1-4` (*Lại / Khó / Tốt / Dễ*) và tính `nextReviewAt`.

---

## Phân hệ 3: Lớp học, Bài tập & Bài kiểm tra Định thời (FE-2A)
- [ ] **Lớp học (`/classes`, `/my-classes`, `/classes/:classId`):**
  - Giảng viên tạo lớp mới sinh mã 8 ký tự kèm nút sao chép; đổi trạng thái lớp (*Đang mở / Đã đóng / Lưu trữ*); duyệt/từ chối yêu cầu tham gia; thêm đồng giảng dạy; gỡ thành viên.
  - Sinh viên gửi yêu cầu tham gia bằng mã lớp và nhận thông báo chờ duyệt; tự rời lớp có hộp thoại xác nhận.
  - 5 Tab lớp học hoạt động đầy đủ: *Bảng tin* (ghim/bỏ ghim), *Tài liệu lớp* (upload & download bảo vệ quyền thành viên), *Bài tập*, *Bài kiểm tra*, *Thành viên*.
- [ ] **Bài tập & Chấm điểm (`/assignments/:assignmentId`):**
  - Sinh viên nộp bài (văn bản + nhiều tệp) với `Idempotency-Key` giữ nguyên khi thử lại lúc lỗi mạng; hiển thị đúng trạng thái *Đúng hạn / Nộp muộn*, khóa form sau khi nộp và hiển thị điểm/nhận xét khi trả bài.
  - Giảng viên lọc bài nộp, chấm điểm ($0 \le score \le maxScore$), trả bài (`RETURNED`) và mở lại quyền nộp (`reopen`).
- [ ] **Bài kiểm tra định thời (`/tests/:testId/take`, `/tests/:testId/results`):**
  - Sinh viên làm bài với đồng hồ đếm ngược theo `deadlineAt`, cảnh báo khi còn dưới 2 phút và tự động nộp bài khi hết giờ.
  - Giảng viên xem bảng điểm tổng hợp cả lớp và tìm kiếm theo tên sinh viên.
- [ ] **Dashboard Giảng viên (`/teacher/dashboard`) & Giảng viên mở rộng (`/meetings`, `/class-statistics`):** Hiển thị KPI, tiến độ thu bài, kết quả kiểm tra, danh sách cuộc họp Meet và biểu đồ thống kê lớp.

---

## Phân hệ 4: Chat Thời gian thực & Hệ thống Thông báo (FE-2B)
- [ ] **Chat 2 cột (`/messages`, `/messages/:conversationId`):**
  - Phân biệt rõ *Phòng lớp* (`CLASSROOM`) và *Tin nhắn riêng* (`DIRECT`), tìm kiếm và mở chat 1-1 (`POST /api/v1/conversations/direct`).
  - Phân trang lịch sử theo **cursor** với nút **"Tải tin nhắn cũ hơn"** giữ nguyên vị trí cuộn.
  - Gửi tin nhắn với `clientMessageId`: hiển thị ngay trạng thái *Đang gửi* $\rightarrow$ *Đã gửi*, chống trùng khi nhận qua `/topic/conversations/{id}`, tự chuyển sang REST fallback khi mất WebSocket và tự động tải bù tin nhắn bị lỡ khi WebSocket kết nối lại.
  - Debounce 500ms gọi `PUT /conversations/{id}/read`; khóa khung chat rõ ràng khi bị lỗi `403` (rời lớp / bị gỡ).
- [ ] **Thông báo (`NotificationContext` + Chuông Header + `/notifications`):**
  - Chuông Header ẩn badge khi `0`, hiển thị `99+` khi `> 99`; mở dropdown 10 thông báo mới nhất; nhận push `/user/queue/notifications` kèm Toast có nút **Xem nhanh**.
  - Trang `/notifications`: lọc *Tất cả / Chưa đọc*, tải thêm theo cursor, đánh dấu đã đọc & điều hướng đúng màn hình đích theo `targetType` + `targetId`.
  - Tab `/notifications/settings`: bật/tắt 2 kênh *Trong ứng dụng* và *Email* theo từng loại sự kiện (`PUT /api/v1/notifications/settings`).

---

## Phân hệ 5: Lịch học Tổng hợp, Import, Google Calendar & AI Study Plan (FE-3A)
- [ ] **Lịch tổng hợp (`/calendar` / `/schedule`):** Chuyển đổi 3 chế độ *Ngày / Tuần / Tháng*, nút *Hôm nay / Trước / Sau*; phân biệt bằng icon + nhãn chữ (*Lịch lớp / Kiểm tra / Cuộc họp / Cá nhân / Google*); hiển thị giờ địa phương và gửi chuẩn UTC lên Backend.
- [ ] **Sự kiện & Google Meet:** Giảng viên tạo/sửa (`version`)/hủy sự kiện lớp kèm dán link Meet; sinh viên quản lý sự kiện cá nhân và chỉ được xem (*Chỉ đọc*) đối với sự kiện lớp/Google; bấm **"Mở link Google Meet"** mở trình duyệt ngoài hệ thống.
- [ ] **Import `.ics` / `.csv`:** Tải tệp CSV mẫu; tải tệp xem trước; cảnh báo rõ các mục `hasConflict === true` kèm sự kiện trùng; cho phép tick chọn từng mục trước khi xác nhận nhập.
- [ ] **Google Calendar (Chỉ đọc):** Nút kết nối mở URL OAuth bằng trình duyệt mặc định hệ thống; đồng bộ về (`POST /integrations/google/sync`) và ngắt kết nối (`DELETE /integrations/google`).
- [ ] **Mục tiêu, Nhiệm vụ & AI Study Plan (`/study-plan`):** CRUD mục tiêu (kèm `freeTime`) và nhiệm vụ theo ngày (tick hoàn thành trên `/study-plan` và `/dashboard`); đề xuất lộ trình qua `useAiJob`, duyệt từng mục theo BR-11 (*Chấp nhận*, *Chỉnh sửa & Chấp nhận*, *Bỏ qua*).

---

## Phân hệ 6: Quản trị Admin, Báo cáo & Đóng gói Electron V2 (FE-3B)
- [ ] **Quản trị Admin (`role === 'ADMIN'`):**
  - `/admin/users`: Tìm kiếm, lọc vai trò/trạng thái, đổi vai trò & khóa/mở khóa tài khoản có hộp thoại xác nhận.
  - `/admin/classes`: Quản lý danh sách lớp toàn hệ thống, khóa/mở/lưu trữ lớp (`PUT /admin/classes/{id}/status`).
  - `/admin/reports` & `/admin/settings`: Lọc & xử lý báo cáo (`OPEN` $\rightarrow$ `RESOLVED`); cấu hình `AI_DAILY_QUOTA_PER_USER`, `MAX_UPLOAD_FILE_SIZE_MB`, công tắc `GOOGLE_ALLOW_PERSONAL_ACCOUNTS`; tra cứu `audit-logs`.
- [ ] **Gửi báo cáo / góp ý (`POST /api/v1/reports`):** Mở được từ menu tài khoản Header hoặc footer Sidebar trên mọi vai trò.
- [ ] **Bảo mật & Đóng gói Electron V2:**
  - `electron/main.ts` bật `contextIsolation: true`, `nodeIntegration: false`, `sandbox: true`; chỉ mở `http://` & `https://` qua `shell.openExternal`.
  - `vite.config.ts` cấu hình `base: './'` và dùng `HashRouter` không lỗi đường dẫn khi mở bằng `file://`.
  - Biến môi trường `.env` và `.env.production` có đủ `VITE_API_BASE_URL` và `VITE_WS_URL`.
  - Kiểm tra build production & đóng gói:
    - `npm --prefix desktop run build` (pass `tsc && vite build`).
    - `npm --prefix desktop run electron:build:win` (tạo bộ cài `.exe` trên Windows).
    - `npm --prefix desktop run electron:build:linux` (tạo `.AppImage` trên Linux).
    - `npm --prefix desktop run electron:build:mac` (tạo `.dmg` trên macOS).
