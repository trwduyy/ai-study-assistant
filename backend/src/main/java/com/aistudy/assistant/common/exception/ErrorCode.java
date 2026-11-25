package com.aistudy.assistant.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Standardized V2 business and protocol error codes paired with HTTP statuses and safe default messages.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ."),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Định dạng tệp không được hỗ trợ."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "Dung lượng tệp vượt quá giới hạn cho phép (20MB)."),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "Tên tệp không hợp lệ."),
    INVALID_FORMAT(HttpStatus.BAD_REQUEST, "Định dạng dữ liệu không hợp lệ."),

    // 401 Unauthorized
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để thực hiện thao tác này."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token xác thực không hợp lệ."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã bị thu hồi."),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác."),

    // 403 Forbidden
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này."),
    NOT_RESOURCE_OWNER(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này."),
    NOT_CLASS_MEMBER(HttpStatus.FORBIDDEN, "Bạn không phải thành viên của lớp học này."),
    NOT_CONVERSATION_MEMBER(HttpStatus.FORBIDDEN, "Bạn không phải thành viên của cuộc trò chuyện này."),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản tạm thời bị khóa do đăng nhập sai nhiều lần."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa."),

    // 404 Not Found
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên yêu cầu."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng."),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học."),
    CHAPTER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy chương học."),
    TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy chủ đề."),
    MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài liệu."),
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài quiz."),
    ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lượt làm bài."),
    AI_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tác vụ AI."),
    AI_SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bản tóm tắt."),
    AI_CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiên hội thoại AI."),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi."),
    CLASS_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học."),
    CLASS_CODE_INVALID(HttpStatus.NOT_FOUND, "Mã lớp học không hợp lệ hoặc lớp đã đóng."),
    JOIN_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu tham gia lớp học."),
    CLASS_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài đăng trong lớp học."),
    CLASS_MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài liệu lớp học."),
    ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài tập."),
    SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài nộp."),
    SUBMISSION_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tệp bài nộp."),
    CLASS_TEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài kiểm tra."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện."),
    CALENDAR_IMPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiên nhập lịch."),
    EXTERNAL_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy kết nối tài khoản ngoài."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo."),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy cuộc trò chuyện."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "Đường dẫn API không tồn tại."),

    // 405 Method Not Allowed
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ."),

    // 409 Conflict
    CONFLICT(HttpStatus.CONFLICT, "Dữ liệu bị xung đột hoặc đã tồn tại."),
    EMAIL_EXISTS(HttpStatus.CONFLICT, "Email đã được đăng ký trong hệ thống."),
    ASSIGNMENT_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Bạn đã nộp bài tập này rồi."),
    QUIZ_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Lượt làm bài này đã được nộp trước đó."),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Dữ liệu đã bị thay đổi bởi thao tác khác. Vui lòng tải lại và thử lại."),
    JOIN_REQUEST_EXISTS(HttpStatus.CONFLICT, "Bạn đã gửi yêu cầu tham gia lớp học này."),
    ALREADY_CLASS_MEMBER(HttpStatus.CONFLICT, "Bạn đã là thành viên của lớp học này."),
    SCHEDULE_CONFLICT(HttpStatus.CONFLICT, "Sự kiện bị trùng thời gian với lịch hiện có."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Khóa Idempotency-Key đã được sử dụng cho một nội dung khác."),

    // 422 Unprocessable Entity
    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY, "Không thể xử lý yêu cầu theo quy tắc nghiệp vụ."),
    ASSIGNMENT_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "Bài tập đã quá hạn và không cho phép nộp muộn."),
    TEXT_NOT_EXTRACTED(HttpStatus.UNPROCESSABLE_ENTITY, "Tài liệu chưa hoàn tất trích xuất văn bản."),
    SCANNED_DOCUMENT_UNSUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "Tài liệu có ít hơn 50 ký tự trích xuất được; tài liệu scan chưa được hỗ trợ."),
    AI_OUTPUT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Kết quả từ AI không đạt cấu trúc hợp lệ."),
    ATTEMPT_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Đã quá thời gian làm bài."),
    MAX_ATTEMPTS_REACHED(HttpStatus.UNPROCESSABLE_ENTITY, "Bạn đã hết số lần làm bài cho phép."),
    TEST_WINDOW_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "Hiện không nằm trong thời gian mở bài kiểm tra."),

    // 429 Too Many Requests
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh. Vui lòng thử lại sau."),
    AI_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã vượt quá hạn mức sử dụng AI trong ngày."),

    // 502 / 504 External Dependencies
    AI_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "Dịch vụ AI tạm thời gặp sự cố. Vui lòng thử lại sau."),
    GOOGLE_API_ERROR(HttpStatus.BAD_GATEWAY, "Không thể kết nối tới Google Calendar API."),
    EMAIL_DELIVERY_ERROR(HttpStatus.BAD_GATEWAY, "Không thể gửi email thông báo."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Tác vụ AI vượt quá thời gian xử lý. Vui lòng thử lại."),
    EXTERNAL_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Kết nối dịch vụ ngoài bị quá thời gian."),

    // 500 Internal Server Error
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống đang gặp sự cố không mong muốn. Vui lòng thử lại sau.");

    private final HttpStatus status;
    private final String defaultMessage;
}
