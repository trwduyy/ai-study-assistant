export type WsConnectionStatus =
    | "DISCONNECTED"
    | "CONNECTING"
    | "CONNECTED"
    | "RECONNECTING"
    | "ERROR";

export interface NotificationItem {
    id: number;
    type: string;
    title: string;
    body: string;
    targetType: string | null;
    targetId: number | null;
    readAt: string | null;
    createdAt: string;
}

export interface NotificationUnreadCount {
    unreadCount: number;
}

export interface NotificationSetting {
    eventType: string;
    inAppEnabled: boolean;
    emailEnabled: boolean;
}

export type AiJobType =
    | "SUMMARY"
    | "EXPLANATION"
    | "QUIZ"
    | "FLASHCARD"
    | "STUDY_PLAN"
    | (string & {});

export type AiJobStatus =
    | "PENDING"
    | "RUNNING"
    | "SUCCEEDED"
    | "FAILED"
    | "CANCELLED";

export interface AiJobResponse {
    jobId: string;
    type: AiJobType;
    status: AiJobStatus;
    resultType: string | null;
    resultId: number | null;
    resultPayload?: Record<string, unknown> | null;
    errorCode: string | null;
    errorMessage: string | null;
    attempts: number;
    createdAt: string;
    finishedAt: string | null;
}

export type ChatMessageDeliveryStatus = "SENT" | "DELIVERED" | "READ";

export interface ChatRoomMessage {
    id: number;
    conversationId: number;
    senderId: number;
    senderName: string;
    content: string;
    status: ChatMessageDeliveryStatus;
    clientMessageId: string | null;
    createdAt: string;
}
