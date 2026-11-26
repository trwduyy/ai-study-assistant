# AI Study Assistant – Project Specification v1

## 1. Project Overview

**Project title:** Phát triển ứng dụng Desktop hỗ trợ học tập thông minh tích hợp trí tuệ nhân tạo (AI Study Assistant) 
**Product type:** Cross-platform Desktop Application 
**Target users:** Sinh viên, người tự học, người học trực tuyến.

Mục tiêu là xây dựng ứng dụng Desktop hỗ trợ chu trình học tập: **Organize → Understand → Practice → Evaluate → Improve**. Sản phẩm không chỉ là CRUD hay chatbot, mà kết hợp quản lý tài liệu, hỗ trợ hiểu bài, luyện tập và theo dõi tiến bộ.

## 2. Objectives

- Quản lý khóa học, chủ đề và tài liệu học tập tập trung.

- Upload và xử lý PDF, DOCX, TXT.

- AI tóm tắt tài liệu, giải thích khái niệm, hỏi đáp theo tài liệu.

- Tạo câu hỏi trắc nghiệm và flashcards từ tài liệu.

- Chấm điểm bài làm, lưu lịch sử và thống kê học tập.

- Đóng gói ứng dụng Desktop cho Windows/Linux/macOS.

## 3. Functional Requirements (MVP)

- **FR-01 User Management:** đăng ký, đăng nhập, đăng xuất, hồ sơ, đổi mật khẩu.

- **FR-02 Course Management:** CRUD khóa học, chương, chủ đề.

- **FR-03 Material Management:** upload PDF/DOCX/TXT, phân loại, xem/xóa/tải, trích xuất văn bản.

- **FR-04 AI Summarization:** tóm tắt, ý chính, tiêu đề cấu trúc, lựa chọn độ dài.

- **FR-05 AI Study Chat:** giải thích khái niệm, hỏi đáp dựa trên tài liệu, lưu lịch sử; có trích nguồn khi hỏi đáp theo tài liệu.

- **FR-06 AI Quiz Generator:** tạo câu hỏi trắc nghiệm theo số lượng, chủ đề, độ khó; đáp án và giải thích.

- **FR-07 Quiz & Evaluation:** làm bài, nộp bài, tự chấm, xem giải thích, lưu kết quả.

- **FR-08 Flashcards:** tạo thủ công/AI, ôn tập và đánh giá mức nhớ (giai đoạn mở rộng).

- **FR-09 Study Planner:** nhiệm vụ học, deadline, trạng thái, kế hoạch gợi ý AI (giai đoạn mở rộng).

- **FR-10 Learning Analytics:** thống kê điểm, tiến độ, câu sai thường gặp và chủ đề nên ôn.

## 4. Non-functional Requirements

- **NFR-01 Cross-platform:** Desktop hỗ trợ Windows/Linux/macOS.

- **NFR-02 Performance:** thao tác CRUD phản hồi nhanh; tác vụ AI chạy bất đồng bộ, UI không bị treo; có trạng thái loading, timeout, lỗi.

- **NFR-03 Security:** mật khẩu băm; kiểm soát quyền; API key AI không lưu ở Desktop client; kiểm tra file upload.

- **NFR-04 Reliability:** xử lý khi AI provider lỗi; logging; retry có kiểm soát; lưu dữ liệu bền vững.

- **NFR-05 Maintainability:** phân chia module; tách UI, business, data và AI; API nhất quán.

- **NFR-06 Usability:** điều hướng rõ ràng, thông báo trạng thái, dễ dùng.

## 5. Actors

- **Student/Learner (primary):** đăng nhập, quản lý khóa học/tài liệu, chat AI, làm quiz, flashcards, planner, xem tiến độ.

- **AI Provider/Model:** xử lý ngôn ngữ, tóm tắt, giải thích, sinh quiz/flashcards/kế hoạch.

- **File Processing Engine:** trích xuất, chuẩn hóa, chia nhỏ văn bản PDF/DOCX/TXT.

- **Local Operating System:** quản lý file, lưu trữ cục bộ và runtime Desktop.

- **Future actors:** Lecturer/Teacher (chia sẻ tài liệu/quiz, theo dõi người học), Administrator (quản trị người dùng/cấu hình/hạn mức AI).

MVP chỉ cần Student và các hệ thống hỗ trợ; Teacher/Admin là hướng phát triển sau.

## 6. Architecture & Technology Stack

Kiến trúc đề xuất: **Desktop Client → Backend API → Business Services → Database / AI / File Processing**.

| Layer | Technology | Responsibilities |
| :--- | :--- | :--- |
| Desktop | Electron | Runtime và đóng gói cross-platform |
| Frontend | React + TypeScript | UI, điều hướng, gọi REST API, hiển thị AI |
| Backend | Java + Spring Boot | Auth, business logic, API, quản lý module |
| Database | MySQL | User, course, material, quiz, history, progress |
| AI | Gemini API hoặc provider tương đương | Summary, explain, chat, quiz, flashcards |
| Parsing | Apache PDFBox, Apache POI | Trích xuất PDF/DOCX |
| AI/RAG (later) | Embeddings + vector store | Retrieval, semantic search, document-grounded Q & A |
| ML | Python + scikit-learn | Topic classification / phân tích điểm yếu |
| Testing | JUnit, Mockito, frontend testing tools | Unit/integration testing |
| DevOps | Git, GitHub, Docker | Version control, CI/CD, container hóa (nếu phù hợp) |

**AI API key phải nằm ở Backend, không nhúng vào Desktop client.** Có thể tách Python FastAPI AI service ở giai đoạn nâng cao.

## 7. Main Modules

1. Authentication & User Management.

1. Course Management.

1. Material Management: upload, validation, categorization, extraction, storage.

1. AI Learning: summarization, explain, document Q&A, quiz generation, flashcards, study plan.

1. Quiz & Learning Evaluation.

1. Flashcards.

1. Study Planner.

1. Learning Analytics.

## 8. AI Models & Use Cases

### Pretrained LLM/API (MVP – recommended)

Dùng model đã huấn luyện sẵn qua API; không tự train LLM. Dùng cho summary, chat, explanation, quiz, flashcards. Có thể chọn Gemini hoặc provider khác; API call do backend thực hiện.

### Embedding model (phase 2)

Chuyển đoạn văn và câu hỏi thành vector phục vụ semantic search và RAG. Có thể dùng embedding API hoặc model như Sentence Transformers/BGE/E5.

### OCR (optional)

Nhận dạng chữ trong tài liệu scan/ảnh khi text extraction thông thường không đủ; cân nhắc Tesseract hoặc OCR phù hợp.

### Custom ML training (optional learning experiment)

- **Topic classifier:** phân loại tài liệu/câu hỏi vào các chủ đề (Java, Database, Network, Software Engineering, OS, AI/ML). Có thể bắt đầu TF-IDF + Logistic Regression/SVM/Naive Bayes; đánh giá Accuracy, Precision, Recall, F1.

- **Learning weakness analysis:** sử dụng lịch sử câu hỏi, chủ đề, điểm, thời gian trả lời, lần ôn để ước lượng chủ đề cần ôn. Ban đầu dùng rule-based; sau đó thử Logistic Regression, Decision Tree hoặc Random Forest. Dữ liệu nhỏ không đủ để khẳng định dự đoán chính xác.

- **Fine-tuning small model (advanced/optional):** có thể thử LoRA/QLoRA khi có dataset, tài nguyên; không cần cho MVP. Fine-tuning không thay thế RAG để trả lời theo tài liệu riêng của người dùng.

## 9. AI Processing Flows

### Summarization

Upload → Extract text → Clean → Chunk (nếu tài liệu dài) → Prompt → LLM → Validate output → Save → Display.

### Quiz Generation

Chọn tài liệu → Extract content → Prompt LLM → Parse JSON → Validate → Save Quiz → Learner attempt → Score → Save result.

### Document Q&A / RAG (phase 2)

Question → Embed query → Retrieve relevant chunks → Build contextual prompt → LLM → Answer with source references. RAG giúp bám sát tài liệu nhưng không đảm bảo câu trả lời luôn đúng.

## 10. MVP Scope (Keep achievable)

- Register/login.

- CRUD courses.

- Upload/manage materials.

- Extract PDF/DOCX text.

- AI summary.

- AI explanation/chat.

- AI quiz generation.

- Take quiz + scoring.

- History.

- Basic dashboard/analytics.

- Desktop packaging.

Flashcards nâng cao, RAG, study planner, AI recommendations và custom model training để phase 2/3 nếu thời gian cho phép.

## 11. Roadmap

- **Phase 1 – MVP:** user/course/material, file extraction, summary, explain/chat, quiz generation, quiz-taking/scoring, basic analytics, Desktop packaging.

- **Phase 2 – AI enhancement:** document Q&A, RAG, embeddings, semantic search, flashcards, study planner, AI recommendations.

- **Phase 3 – ML experiment:** dataset, topic classifier, weakness analysis, model evaluation; fine-tuning nếu khả thi.

- **Phase 4 – Product expansion:** cloud sync, web/mobile, study groups, lecturer features, model selection, local model support.

## 12. Risks & Notes

- Giới hạn API quota/chi phí và độ trễ.

- Chất lượng trích xuất PDF/DOCX, đặc biệt tài liệu scan.

- AI có thể tạo nội dung sai; cần kiểm tra/đánh giá output và thông báo người dùng.

- Đảm bảo tác vụ AI không làm treo UI.

- Giữ MVP nhỏ, ưu tiên hoàn thành end-to-end trước khi mở rộng.

---

# *13. Expanded Product Scope – Classroom & Collaboration*

## 13.1 User roles

- **Student (Sinh viên):** tham gia lớp, xem lịch, nhận và nộp bài, tham gia buổi học/cuộc họp, chat, lập kế hoạch cá nhân, học cùng AI.

- **Teacher (Giáo viên):** tạo và quản lý lớp, mời/thêm sinh viên, đăng tài liệu, giao bài có hạn nộp, tạo bài kiểm tra, lên lịch học/kiểm tra, tạo cuộc họp Google Meet, theo dõi tiến độ và gửi thông báo lớp.

- **Administrator (Quản trị viên):** quản lý tài khoản, vai trò, lớp học, cấu hình và xử lý báo cáo.

Mỗi lớp có thành viên và vai trò riêng. Giáo viên chỉ quản lý lớp được phân quyền; sinh viên chỉ xem nội dung của lớp mình tham gia.

## 13.2 Classroom Management

- Giáo viên tạo lớp: tên lớp, mã lớp, mô tả, môn học, ảnh đại diện, thời gian bắt đầu/kết thúc.

- Mời sinh viên bằng mã tham gia hoặc lời mời; giáo viên có thể duyệt yêu cầu tham gia.

- Quản lý danh sách thành viên, giáo viên đồng giảng dạy, trạng thái lớp.

- Tạo bảng tin lớp, ghim thông báo, đăng tài liệu và nội dung buổi học.

- Tự tạo phòng chat chung cho mỗi lớp; chỉ thành viên lớp được truy cập.

- Khi thành viên rời lớp hoặc bị gỡ, quyền truy cập phòng chat và tài liệu phải được cập nhật.

### 13.2.1 Quy trình mời và duyệt sinh viên tham gia lớp

1. Giảng viên tạo lớp; hệ thống sinh mã tham gia duy nhất cho lớp. Có thể cung cấp thêm liên kết hoặc QR chứa mã tham gia.

1. Giảng viên chia sẻ mã/lời mời cho sinh viên qua kênh phù hợp.

1. Sinh viên đăng nhập ứng dụng, nhập mã lớp và gửi yêu cầu tham gia.

1. Hệ thống kiểm tra mã lớp, trạng thái lớp và tư cách thành viên; yêu cầu hợp lệ được lưu ở trạng thái PENDING.

1. Giảng viên xem danh sách yêu cầu, thông tin nhận diện sinh viên cần thiết và chọn duyệt hoặc từ chối.

1. Khi được duyệt, sinh viên trở thành thành viên lớp, được cấp quyền truy cập tài liệu, bài tập, lịch và phòng chat nhóm của lớp; hệ thống gửi thông báo trong ứng dụng và email.

1. Khi bị từ chối, yêu cầu được lưu trạng thái REJECTED; sinh viên không được cấp quyền truy cập nội dung lớp.

**Quy tắc nghiệp vụ:**

- Mã lớp không hợp lệ, đã hết hạn hoặc đã bị vô hiệu hóa thì không thể gửi yêu cầu.

- Một sinh viên không được tạo yêu cầu trùng khi đã là thành viên hoặc đang có yêu cầu PENDING.

- Giảng viên chỉ được duyệt/từ chối yêu cầu thuộc lớp mình có quyền quản lý.

- Lưu người duyệt, thời điểm duyệt và trạng thái xử lý để phục vụ kiểm tra/audit.

- Có thể vô hiệu hóa hoặc tạo lại mã lớp; việc đổi mã không tự động xóa thành viên hiện tại.

- Khi xóa thành viên, phải thu hồi quyền truy cập tài liệu, bài tập, lịch và chat của lớp.

## 13.3 Assignments & Tests

- Giáo viên tạo bài tập: tiêu đề, hướng dẫn, tệp đính kèm, điểm tối đa, thời gian mở, hạn nộp, cho phép nộp muộn hay không.

- Sinh viên xem yêu cầu, tải tài liệu, nộp tệp hoặc câu trả lời, xem trạng thái đã nộp/quá hạn/đã chấm.

- Giáo viên xem bài nộp, chấm điểm, nhận xét và trả bài.

- Hỗ trợ bài kiểm tra có thời gian bắt đầu/kết thúc, thời lượng làm bài, số lần làm, trộn câu hỏi nếu cần.

- Lưu dấu thời gian tạo bài, nộp bài và chấm bài; máy chủ quyết định bài có đúng hạn hay không.

- Thông báo trước hạn nộp theo các mốc cấu hình; tránh gửi lặp khi tác vụ chạy lại.

## 13.4 Google Meet & Class Calendar

- Giáo viên tạo sự kiện học/kiểm tra/cuộc họp với tên, mô tả, lớp, giờ bắt đầu/kết thúc.

- Tạo Google Meet link từ tích hợp Google Calendar API; yêu cầu người tạo cấp quyền Google OAuth phù hợp. Lưu meeting URL, mã sự kiện ngoài hệ thống và người tạo.

- Cho phép giáo viên dán link Meet thủ công nếu chưa bật tích hợp Google.

- Sinh viên xem lịch theo ngày/tuần/tháng, mở link tham gia từ ứng dụng.

- Có trạng thái sự kiện: sắp diễn ra, đang diễn ra, đã kết thúc, đã hủy.

- Tạo URL Meet không đồng nghĩa với việc đã đặt lịch trên Google Calendar; cần xử lý OAuth, quyền truy cập, lỗi API và đồng bộ/hủy sự kiện.

## 13.5 Student Timetable Import

- Cho phép sinh viên nhập lịch từ tệp .ics; có thể hỗ trợ CSV theo mẫu ở giai đoạn sau.

- Xem lịch cá nhân theo ngày/tuần/tháng; phân biệt lịch cá nhân, lịch lớp, lịch kiểm tra, lịch họp.

- Phát hiện lịch trùng giờ và yêu cầu người dùng xác nhận trước khi nhập.

- Sinh viên có thể sửa/xóa sự kiện cá nhân; không được sửa lịch lớp do giáo viên tạo.

- Lưu nguồn nhập, múi giờ và thời điểm cập nhật để tránh sai lệch giờ.

## 13.6 Personal Study Roadmap

- Sinh viên tự tạo mục tiêu, môn học, thời gian rảnh, ngày cần hoàn thành và mức độ ưu tiên.

- Tạo kế hoạch theo tuần/tháng; chia mục tiêu lớn thành các buổi học và nhiệm vụ nhỏ.

- AI đề xuất lộ trình dựa trên lịch học, hạn nộp, kết quả quiz và thời gian rảnh do sinh viên cung cấp.

- Sinh viên có thể chấp nhận, sửa hoặc bỏ đề xuất; AI không tự ý ghi đè lịch cá nhân.

- Cập nhật tiến độ khi hoàn thành nhiệm vụ; đề xuất điều chỉnh khi bỏ lỡ kế hoạch.

## 13.7 Chat & Real-time Notifications

- Chat 1-1 giữa người dùng; chat nhóm lớp tự tạo khi lớp được tạo.

- Hỗ trợ gửi tin nhắn văn bản trước; tệp đính kèm, emoji, trả lời và tìm kiếm là phần mở rộng.

- Có trạng thái gửi/đã nhận/đã đọc ở mức cần thiết; phân trang lịch sử tin nhắn.

- Thông báo về bài tập mới, hạn nộp, bài đã chấm, lịch học/kiểm tra, lời mời lớp, tin nhắn mới và thay đổi sự kiện.

- Có danh sách thông báo, đánh dấu đã đọc, mở đúng nội dung liên quan và thiết lập loại thông báo.

- Ưu tiên thông báo trong ứng dụng; email/push là phần mở rộng. Không gửi thông báo trùng lặp.

# *14. Expanded Functional Requirements*

- **FR-11 Role & Permission:** phân quyền Student, Teacher, Administrator; kiểm tra quyền ở Backend cho mọi thao tác.

- **FR-12 Classroom:** tạo lớp, sinh mã/lời mời, sinh viên gửi yêu cầu tham gia, giảng viên duyệt/từ chối, quản lý thành viên, bảng tin và tài liệu lớp; tự động cấp/thu hồi quyền truy cập các tài nguyên lớp.

- **FR-13 Assignment:** tạo bài tập, hạn nộp, nộp bài, chấm điểm, nhận xét và xử lý nộp muộn.

- **FR-14 Test Schedule:** tạo lịch kiểm tra, thời gian làm và quy tắc làm bài.

- **FR-15 Meeting:** tạo/lưu Google Meet link, tích hợp Google Calendar khi người dùng cấp quyền.

- **FR-16 Timetable:** nhập .ics, xem lịch cá nhân và lịch lớp, cảnh báo trùng lịch.

- **FR-17 Personal Plan:** lập mục tiêu và kế hoạch tuần/tháng; AI đề xuất lịch học có thể chỉnh sửa.

- **FR-18 Direct & Group Chat:** nhắn tin 1-1 và phòng chat theo lớp.

- **FR-19 Notifications:** thông báo trong ứng dụng theo sự kiện, có trạng thái đọc và chống gửi trùng.

- **FR-20 Teacher Dashboard:** xem danh sách lớp, bài tập sắp đến hạn, tình hình nộp bài và kết quả kiểm tra.

- **FR-21 Student Dashboard:** lịch sắp tới, bài tập cần nộp, thông báo chưa đọc, mục tiêu và tiến độ học.

# *15. AI Learning Quality – Grounded, Concise & Understandable*

## 15.1 Required behavior

- Câu trả lời mặc định bằng tiếng Việt phổ thông, ngắn gọn, dễ hiểu; giải thích thuật ngữ bằng từ thông dụng.

- Bám sát tài liệu người dùng chọn. Không tự thêm kiến thức ngoài tài liệu như thể tài liệu có đề cập.

- Nếu tài liệu không có câu trả lời, nói rõ: “Tài liệu hiện tại chưa có thông tin để kết luận”; có thể đề nghị bật chế độ tham khảo kiến thức bên ngoài.

- Khi trả lời theo tài liệu, hiển thị tên tài liệu và trang/đoạn tham chiếu nếu trích xuất được.

- Phân biệt nội dung trong tài liệu với phần AI suy luận hoặc giải thích thêm.

- Không tự tạo số liệu, định nghĩa, trích dẫn hoặc đáp án không có căn cứ.

## 15.2 AI features

- **Tóm tắt:** ngắn gọn nhưng giữ đủ ý chính; bảo toàn khái niệm, công thức, quy trình, điều kiện và cảnh báo quan trọng. Có tùy chọn ngắn/vừa/chi tiết.

- **Giải thích:** diễn đạt bằng ngôn ngữ đơn giản, ví dụ dễ hiểu; chỉ mở rộng ngoài tài liệu khi người học yêu cầu.

- **Hỏi đáp tài liệu:** tìm đúng đoạn liên quan, trả lời trực tiếp, kèm nguồn; nếu không tìm thấy thì không đoán.

- **Flashcards:** mỗi thẻ có một câu hỏi/ý cần nhớ và câu trả lời súc tích; không tách sai ngữ cảnh.

- **Trắc nghiệm:** câu hỏi rõ ràng; chỉ một đáp án đúng nếu là dạng một lựa chọn; phương án nhiễu hợp lý; giải thích đáp án dựa trên tài liệu; kiểm tra cấu trúc trước khi lưu.

- **Phân tích tài liệu:** nhận diện mục lục, tiêu đề, khái niệm, định nghĩa, ví dụ, công thức, quy trình và nội dung quan trọng; giữ liên kết với trang/đoạn nguồn.

- **Phân tích tiến độ:** gợi ý dựa trên dữ liệu học tập thực tế; nêu lý do và mức độ chắc chắn, không gán nhãn năng lực người học.

## 15.3 AI quality checks

- Lưu phiên bản hướng dẫn AI, model, tài liệu nguồn và thời điểm tạo để kiểm tra kết quả.

- Kiểm tra cấu trúc dữ liệu của quiz và flashcard trước khi lưu.

- Có nút báo câu trả lời sai hoặc không bám tài liệu.

- Có bộ câu hỏi kiểm thử mẫu để đánh giá độ đúng, độ bám nguồn, tính dễ hiểu và độ ngắn gọn.

- Không gửi toàn bộ tài liệu dài lên model nếu không cần; chia đoạn, tìm đoạn liên quan và giới hạn ngữ cảnh.

# *16. UI/UX Design System*

## 16.1 Design principles

- Giao diện sáng sủa, thân thiện, nhất quán, phù hợp môi trường học tập; ưu tiên nội dung và thao tác chính.

- Dùng tiếng Việt phổ thông trên giao diện. Tránh từ chuyên ngành trong nút, nhãn và thông báo; nếu bắt buộc dùng, kèm giải thích ngắn.

- Một màn hình chỉ nên có một hành động chính nổi bật; không lạm dụng màu, hiệu ứng, popup hoặc biểu đồ.

- Có trạng thái tải, rỗng, lỗi, thành công; thông báo lỗi nói rõ vấn đề và cách xử lý.

- Responsive với kích thước cửa sổ Desktop khác nhau; hỗ trợ bàn phím, focus rõ, độ tương phản tốt.

- Màu sắc không phải cách duy nhất biểu thị trạng thái; luôn kèm chữ hoặc biểu tượng.

## 16.2 Suggested visual tokens

Bộ màu khởi đầu, cần kiểm tra độ tương phản và tinh chỉnh khi triển khai:

- Primary: xanh dương **#2563EB** – hành động chính, liên kết; hover **#1D4ED8**.

- Secondary: xanh ngọc **#0F766E** – điểm nhấn phụ.

- App background: **#F8FAFC**; Surface/card: **#FFFFFF**.

- Main text: **#0F172A**; Secondary text: **#475569**; Border: **#E2E8F0**.

- Success: **#15803D**; Warning: **#B45309**; Danger: **#B91C1C**; Info: **#0369A1**.

- Dark mode (giai đoạn sau): background **#0B1220**, surface **#111827**, text **#F8FAFC**, border **#334155**.

- Font: ưu tiên Inter hoặc font hệ thống hỗ trợ tiếng Việt; nội dung 14–16px, tiêu đề theo cấp bậc rõ ràng.

- Spacing theo bội số 4/8px; bo góc 8–12px; bóng nhẹ và nhất quán.

## 16.3 Core components and screens

- Thành phần dùng chung: Button, Input, Select, Dialog, Toast, Badge, Tabs, Table, Pagination, Dropdown, Tooltip, Calendar, Avatar, Empty State, Loading State.

- Bố cục chính: thanh điều hướng trái, thanh tiêu đề, vùng nội dung; menu thay đổi theo vai trò.

- Student: Tổng quan, Lớp học, Lịch học, Bài tập, Tin nhắn, Kế hoạch học, Tài liệu AI, Hồ sơ.

- Teacher: Tổng quan, Lớp của tôi, Bài tập, Bài kiểm tra, Lịch, Cuộc họp, Tin nhắn, Thống kê lớp.

- Admin: Người dùng, Vai trò, Lớp học, Báo cáo và cấu hình.

## 16.4 UI copy rules

- Dùng “Tạo lớp” thay cho “Create classroom”; “Hạn nộp” thay cho “Deadline”; “Tin nhắn” thay cho “Chat module”; “Lịch học” thay cho “Timetable”.

- Dùng câu ngắn, chủ động: “Bạn chưa có lớp học nào”, “Bài tập sẽ đóng lúc 23:59 ngày …”.

- Không hiển thị lỗi kỹ thuật, stack trace, tên bảng hoặc mã nội bộ cho người dùng cuối.

- Giữ cùng một cách gọi cho cùng một chức năng ở tất cả màn hình.

# *17. UI Reference Repositories & Design Skills*

Các nguồn tham khảo phù hợp với React + TypeScript + Desktop app. Nên đọc hướng dẫn, chọn thành phần cần thiết và đưa vào dự án có kiểm soát; không clone toàn bộ nhiều thư viện vào source code.

## 17.1 Component libraries / design systems

- **shadcn/ui:** [https://github.com/shadcn-ui/ui](https://github.com/shadcn-ui/ui) — component có thể tùy biến, phong cách gọn, hợp React + Tailwind.

- **Radix UI Primitives:** [https://github.com/radix-ui/primitives](https://github.com/radix-ui/primitives) — tham khảo hành vi và khả năng truy cập cho dialog, menu, tabs, tooltip.

- **Material UI:** [https://github.com/mui/material-ui](https://github.com/mui/material-ui) — hệ thống thành phần đầy đủ và quy tắc giao diện nhất quán.

- **assistant-ui:** [https://github.com/assistant-ui/assistant-ui](https://github.com/assistant-ui/assistant-ui) — tham khảo giao diện chat AI trong React.

- **UI UX Pro Max Skill:** [https://github.com/nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) — skill hỗ trợ lựa chọn phong cách, bố cục và thành phần giao diện.

- **Awesome Frontend Skills:** [https://github.com/finfin/awesome-frontend-skills](https://github.com/finfin/awesome-frontend-skills) — danh sách skill frontend để chọn hướng dẫn phù hợp.

## 17.2 Recommended selection

- Chọn **shadcn/ui + Radix UI + Tailwind CSS** làm hướng chính nếu giữ React + TypeScript; tránh dùng đồng thời nhiều bộ component có phong cách khác nhau.

- Dùng **assistant-ui** làm tài liệu tham khảo riêng cho màn hình AI chat; chỉ tích hợp nếu phù hợp phiên bản React và kiến trúc.

- Dùng **UI UX Pro Max Skill** để hỗ trợ tạo quy tắc thiết kế và kiểm tra giao diện; nhóm cần rà soát kết quả.

- MUI là phương án thay thế nếu nhóm muốn bộ component đầy đủ có sẵn; không cần dùng đồng thời với shadcn/ui.

## 17.3 Clone / add skills workflow

- Tạo thư mục tài liệu thiết kế riêng, ví dụ **docs/design-system/** và lưu quyết định về màu sắc, chữ, khoảng cách, component và mẫu màn hình.

- Đọc README, license, phiên bản, yêu cầu cài đặt và mức độ hoạt động của repo trước khi sử dụng.

- Chỉ thêm skill cần thiết vào workspace/project theo hướng dẫn chính thức; không copy toàn bộ kho mã nguồn vào thư mục ứng dụng.

- Với repo skill, có thể clone về thư mục tham khảo riêng để đọc và áp dụng; không chạy script lạ khi chưa kiểm tra nội dung.

- Ghi lại nguồn, phiên bản/commit, license và phần đã sử dụng trong **docs/THIRD_PARTY_NOTICES.md**.

- Không sao chép thương hiệu, logo, hình ảnh hoặc giao diện độc quyền; chỉ học nguyên tắc và dùng thành phần theo license.

# *18. Clean Code & Backend Engineering Standards*

- Tách rõ Controller, Service, Repository, DTO/Mapper, Entity và cấu hình; Controller chỉ nhận request, gọi service và trả response.

- Service chứa quy tắc nghiệp vụ; không đặt business logic trong Controller hoặc Entity.

- Đặt tên rõ nghĩa, một hàm một nhiệm vụ chính; giới hạn độ dài hàm/lớp; tránh lặp code và magic number.

- Dùng DTO cho request/response; không trả Entity trực tiếp; validate dữ liệu đầu vào; chuẩn hóa lỗi bằng global exception handler.

- Phân chia module theo nghiệp vụ: identity, classroom, assignment, schedule, meeting, chat, notification, learning, AI.

- Dùng transaction đúng ranh giới nghiệp vụ; không giữ transaction mở trong lúc gọi AI hoặc dịch vụ ngoài.

- Có unit test cho quy tắc nghiệp vụ và integration test cho luồng quan trọng; kiểm thử quyền truy cập giữa các lớp.

- Log có cấu trúc, không ghi mật khẩu, token, nội dung riêng tư hoặc dữ liệu nhạy cảm không cần thiết.

- Code review theo checklist: đúng nghiệp vụ, phân quyền, lỗi biên, hiệu năng truy vấn, race condition, bảo mật và test.

# *19. Performance, N+1 & Race Condition*

## 19.1 Prevent N+1 queries

- Không truy cập quan hệ LAZY trong vòng lặp khi trả danh sách.

- Dùng DTO projection cho màn hình danh sách; chỉ lấy đúng cột cần hiển thị.

- Dùng JOIN FETCH hoặc EntityGraph cho quan hệ cần tải; cẩn thận khi fetch nhiều collection cùng lúc.

- Dùng BatchSize/batch fetching khi phù hợp.

- Phân trang ở database; tránh fetch join collection trực tiếp với pagination nếu có thể làm sai kết quả hoặc tải quá nhiều.

- Bật Hibernate statistics hoặc log SQL ở môi trường phát triển; kiểm tra số query theo từng API và viết test phát hiện truy vấn tăng bất thường.

## 19.2 Prevent race conditions / duplicate actions

- Dùng unique constraint ở database cho dữ liệu không được trùng, ví dụ một sinh viên chỉ có một lượt nộp hợp lệ theo quy tắc bài.

- Dùng optimistic locking với trường Version cho dữ liệu có thể bị cập nhật đồng thời.

- Dùng transaction và câu lệnh cập nhật nguyên tử cho thao tác như tăng số lượng, đánh dấu đã đọc, nhận chỗ trong lớp.

- Dùng idempotency key cho thao tác có thể gửi lại như nộp bài, tạo sự kiện, gửi thông báo.

- Với bài kiểm tra/hạn nộp, Backend dùng thời gian máy chủ và kiểm tra hạn trong cùng luồng lưu kết quả; không tin đồng hồ từ máy người dùng.

- Chỉ dùng pessimistic lock khi đã xác định có tranh chấp thực sự và cần khóa ngắn; tránh khóa trong lúc gọi API ngoài.

- Kiểm thử đồng thời bằng nhiều request song song: nộp bài hai lần, tham gia lớp cùng lúc, cập nhật lịch và gửi tin nhắn lặp.

# *20. Real-time Chat & Notification Architecture*

## 20.1 Suggested architecture

- REST API: đăng nhập, tải lịch sử, tạo lớp, tạo bài tập, cấu hình thông báo.

- WebSocket: gửi/nhận tin nhắn và cập nhật sự kiện tức thời; có thể dùng Spring WebSocket + STOMP.

- Database: lưu tin nhắn và thông báo để người dùng offline vẫn xem lại được.

- Redis Pub/Sub (khi triển khai nhiều Backend instance): phân phối sự kiện giữa các máy chủ; không dùng Redis làm nơi lưu lịch sử duy nhất.

- Notification worker: xử lý hàng đợi sự kiện và gửi thông báo; có retry, giới hạn tốc độ và chống trùng.

- Frontend: tự kết nối lại khi mất mạng, tải bù dữ liệu qua REST, hiển thị trạng thái kết nối.

## 20.2 Chat data & delivery

- Các bảng gợi ý: Conversation, ConversationMember, Message, MessageReadState, Attachment.

- Phòng lớp có loại CLASSROOM; chat riêng có loại DIRECT.

- Mỗi tin nhắn có ID, conversation ID, sender ID, nội dung, thời điểm tạo, trạng thái và khóa chống gửi trùng.

- Kiểm tra người gửi là thành viên cuộc trò chuyện trước khi cho phép gửi/đọc.

- Phân trang tin nhắn theo cursor (ví dụ message ID hoặc createdAt + ID), không tải toàn bộ lịch sử.

- Chỉ cập nhật trạng thái đã đọc theo mốc tin nhắn hoặc theo batch; tránh tạo một truy vấn cho từng tin.

## 20.3 Notification events

Các sự kiện nên phát sinh thông báo:

- Sinh viên được thêm vào lớp hoặc được duyệt tham gia.

- Giáo viên đăng bài tập/bài kiểm tra mới.

- Sắp đến hạn nộp; bài nộp được chấm hoặc có nhận xét.

- Có lịch học, lịch kiểm tra hoặc cuộc họp mới; lịch bị đổi/hủy.

- Có tin nhắn mới hoặc thông báo quan trọng của lớp.

Dùng transactional outbox: ghi sự kiện cần phát cùng transaction với dữ liệu nghiệp vụ, sau đó worker đọc và gửi. Có unique key theo người nhận + loại sự kiện + đối tượng + mốc nhắc để chống gửi trùng. Lưu trạng thái pending/sent/failed và số lần thử.

# *21. Security & Privacy for Collaboration*

- Kiểm tra quyền ở Backend cho từng lớp, bài tập, file, tin nhắn và sự kiện; ẩn nút ở UI không phải biện pháp bảo mật.

- File bài nộp/tài liệu lớp phải có quyền truy cập; không dùng URL công khai không giới hạn.

- Tích hợp Google theo OAuth tối thiểu quyền cần thiết; bảo vệ refresh token và cho phép người dùng ngắt kết nối.

- Có giới hạn kích thước/loại file, kiểm tra file phù hợp, tên file an toàn.

- Chỉ gửi dữ liệu tài liệu cần thiết tới AI provider; thông báo rõ dữ liệu được gửi đi và không đưa tài liệu riêng của lớp này vào câu trả lời cho lớp khác.

- Có chính sách lưu/xóa dữ liệu, quyền rời lớp và xử lý tài khoản bị khóa.

# *22. Delivery Roadmap – Expanded*

- **Phase 1 – Learning MVP:** tài khoản sinh viên, khóa học cá nhân, tài liệu, AI summary/explain/quiz, làm quiz, lịch sử và dashboard.

- **Phase 2 – Teacher & Classroom:** vai trò giáo viên, tạo lớp, tham gia lớp, bảng tin, tài liệu lớp, bài tập có hạn nộp, nộp/chấm bài.

- **Phase 3 – Calendar & Meeting:** lịch lớp, lịch kiểm tra, nhập .ics, tạo Google Meet/Calendar event, nhắc lịch.

- **Phase 4 – Chat & Notifications:** chat riêng, chat nhóm lớp tự tạo, thông báo trong ứng dụng, WebSocket, worker và chống gửi trùng.

- **Phase 5 – AI Personal Learning:** kế hoạch tuần/tháng, phân tích tiến độ, flashcards, hỏi đáp theo tài liệu có nguồn, cải thiện chất lượng quiz.

- **Phase 6 – Hardening & Scale:** tối ưu truy vấn, N+1, race condition, bảo mật, kiểm thử tải, logging/monitoring và CI/CD.

**Khuyến nghị:** nếu nhóm nhỏ, hãy hoàn thành Phase 1 và một luồng end-to-end của Phase 2 trước. Chat real-time, Google OAuth và AI nâng cao nên triển khai sau khi mô hình lớp học, quyền truy cập và dữ liệu đã ổn định.

## *23. Open Decisions*

- *Google Calendar/Meet: hỗ trợ tài khoản tổ chức và tài khoản Google cá nhân; tài khoản cá nhân chỉ được kết nối khi quản trị viên Học viện cho phép. Google Calendar chỉ dùng để đọc/đồng bộ dữ liệu lịch vào ứng dụng, không ghi hoặc chỉnh sửa dữ liệu trên Google Calendar.*

- *Phạm vi áp dụng: phục vụ hoạt động học tập và giảng dạy tại Học viện Kỹ thuật Mật mã; cần xem xét chính sách quản trị, quyền truy cập và bảo mật dữ liệu của Học viện.*

- *Tham gia lớp: sinh viên gửi yêu cầu bằng mã lớp; giảng viên phải duyệt trước khi trở thành thành viên.*

- *Bài tập: bài trắc nghiệm làm trực tiếp trên ứng dụng và bài nộp bằng file đều chỉ cho phép nộp một lần theo mặc định.*

- *Thông báo: gửi cả trong ứng dụng và email.*

- *Import lịch: hỗ trợ .ics và CSV theo mẫu.*

- *Nền tảng: chỉ phát triển ứng dụng Desktop; không cần Web Portal riêng cho giảng viên.*

- *Calendar & task: bổ sung lịch cá nhân dạng ngày/tuần/tháng và task theo ngày, cho phép tạo, cập nhật, hoàn thành, ưu tiên và nhắc task trong ứng dụng. Có thể đọc lịch Google Calendar khi người dùng kết nối và được cấp phép; dữ liệu Google là nguồn đọc, thao tác tạo/sửa task cá nhân trong app không ghi ngược lên Google Calendar.*

## *24. Reference Links*

- shadcn/ui: [https://github.com/shadcn-ui/ui](https://github.com/shadcn-ui/ui)

- Radix UI: [https://github.com/radix-ui/primitives](https://github.com/radix-ui/primitives)

- Material UI: [https://github.com/mui/material-ui](https://github.com/mui/material-ui)

- assistant-ui: [https://github.com/assistant-ui/assistant-ui](https://github.com/assistant-ui/assistant-ui)

- UI UX Pro Max Skill: [https://github.com/nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)

- Awesome Frontend Skills: [https://github.com/finfin/awesome-frontend-skills](https://github.com/finfin/awesome-frontend-skills)

- Design system library overview: [https://awesomedesignsystems.dev/component-libraries](https://awesomedesignsystems.dev/component-libraries)

# *25. Class Invitation – Database & API Design*

## 25.1 Suggested database tables

- **classrooms:** thông tin lớp; có `join_code`, `join_code_expires_at` (nếu dùng hạn), `join_code_enabled` và trạng thái lớp.

- **class_members:** quan hệ sinh viên/giảng viên với lớp; đặt unique constraint phù hợp trên cặp `classroom_id + user_id` để tránh thành viên trùng.

- **class_join_requests:** yêu cầu tham gia; gồm `id`, `classroom_id`, `student_id`, `status`, `requested_at`, `reviewed_by`, `reviewed_at`, `rejection_reason` (tùy chọn).

- **conversations:** phòng chat lớp, liên kết với lớp và có loại `CLASSROOM`.

- **conversation_members:** thành viên phòng chat; được đồng bộ khi yêu cầu tham gia được duyệt hoặc thành viên bị gỡ.

Trạng thái yêu cầu tham gia: `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`. Cần ràng buộc dữ liệu để không tạo nhiều yêu cầu đang chờ cho cùng một sinh viên trong cùng một lớp.

## 25.2 Suggested REST APIs

- `POST /api/v1/classrooms` – giảng viên tạo lớp.

- `POST /api/v1/classrooms/join-requests` – sinh viên gửi mã lớp và tạo yêu cầu tham gia.

- `GET /api/v1/classrooms/{classroomId}/join-requests?status=PENDING` – giảng viên xem yêu cầu.

- `PATCH /api/v1/classrooms/{classroomId}/join-requests/{requestId}/approve` – duyệt yêu cầu.

- `PATCH /api/v1/classrooms/{classroomId}/join-requests/{requestId}/reject` – từ chối yêu cầu.

- `GET /api/v1/classrooms/{classroomId}/members` – xem danh sách thành viên.

- `DELETE /api/v1/classrooms/{classroomId}/members/{studentId}` – gỡ sinh viên khỏi lớp.

- Có thể bổ sung API quản lý mã tham gia để vô hiệu hóa hoặc tạo lại mã.

## 25.3 Backend implementation notes

- Kiểm tra quyền ở Backend cho mọi API; không dựa vào việc ẩn/hiện nút trên Desktop UI.

- Thao tác duyệt nên chạy trong một transaction: cập nhật yêu cầu, thêm `class_members`, thêm `conversation_members` và ghi nhận sự kiện thông báo.

- Dùng unique constraints và xử lý trường hợp hai yêu cầu được duyệt đồng thời để tránh thành viên/phòng chat bị thêm trùng.

- Chỉ gửi thông báo sau khi giao dịch nghiệp vụ được ghi nhận thành công; cân nhắc Transactional Outbox để gửi email/thông báo tin cậy và chống gửi trùng.

- Khi từ chối hoặc gỡ thành viên, không cấp quyền đọc tài liệu, bài tập, lịch và tin nhắn lớp.

- Nếu Học viện cung cấp danh sách lớp chính thức, có thể phát triển chức năng import Excel/CSV để đối chiếu tài khoản sinh viên; đây là tính năng mở rộng, cần kiểm soát quyền truy cập dữ liệu.
