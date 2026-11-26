export type ErrorCode =
    | "VALIDATION_ERROR"
    | "BAD_REQUEST"
    | "FILE_TYPE_NOT_ALLOWED"
    | "FILE_TOO_LARGE"
    | "INVALID_FILE_NAME"
    | "UNAUTHENTICATED"
    | "TOKEN_EXPIRED"
    | "TOKEN_INVALID"
    | "REFRESH_TOKEN_INVALID"
    | "BAD_CREDENTIALS"
    | "FORBIDDEN"
    | "NOT_RESOURCE_OWNER"
    | "NOT_CLASS_MEMBER"
    | "NOT_CONVERSATION_MEMBER"
    | "ACCOUNT_LOCKED"
    | "ACCOUNT_DISABLED"
    | "NOT_FOUND"
    | "USER_NOT_FOUND"
    | "COURSE_NOT_FOUND"
    | "CHAPTER_NOT_FOUND"
    | "TOPIC_NOT_FOUND"
    | "MATERIAL_NOT_FOUND"
    | "QUIZ_NOT_FOUND"
    | "ATTEMPT_NOT_FOUND"
    | "AI_JOB_NOT_FOUND"
    | "CLASS_CODE_INVALID"
    | "CONFLICT"
    | "EMAIL_EXISTS"
    | "ASSIGNMENT_ALREADY_SUBMITTED"
    | "QUIZ_ALREADY_SUBMITTED"
    | "VERSION_CONFLICT"
    | "JOIN_REQUEST_EXISTS"
    | "ALREADY_CLASS_MEMBER"
    | "SCHEDULE_CONFLICT"
    | "IDEMPOTENCY_CONFLICT"
    | "UNPROCESSABLE_ENTITY"
    | "ASSIGNMENT_CLOSED"
    | "TEXT_NOT_EXTRACTED"
    | "SCANNED_DOCUMENT_UNSUPPORTED"
    | "AI_OUTPUT_INVALID"
    | "ATTEMPT_EXPIRED"
    | "MAX_ATTEMPTS_REACHED"
    | "RATE_LIMITED"
    | "AI_QUOTA_EXCEEDED"
    | "AI_PROVIDER_ERROR"
    | "GOOGLE_API_ERROR"
    | "EMAIL_DELIVERY_ERROR"
    | "AI_TIMEOUT"
    | "EXTERNAL_API_TIMEOUT"
    | "INTERNAL_ERROR"
    | (string & {});

export interface PageMeta {
    page?: number | null;
    size: number;
    totalElements?: number | null;
    totalPages?: number | null;
    nextCursor: string | null;
}

export interface FieldErrorDetail {
    field: string;
    message: string;
}

export interface ErrorDetail {
    code: ErrorCode;
    message: string;
    fields?: FieldErrorDetail[] | null;
}

export interface ApiResponse<T> {
    success: boolean;
    data: T;
    meta?: PageMeta | null;
    error?: ErrorDetail | null;
    traceId: string;
}
