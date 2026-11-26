import apiClient from "./apiClient";
import {
    ApiResponse,
    ChatMessage,
    Conversation,
    ConversationReadStateResponse,
    CreateDirectConversationRequest,
    SendMessageRequest
} from "../types";

export interface GetMessagesResult {
    items: ChatMessage[];
    nextCursor: string | null;
}

export const chatService = {
    async getConversations(): Promise<Conversation[]> {
        const response = await apiClient.get<ApiResponse<Conversation[]>>("/conversations");
        return response.data.data ?? [];
    },

    async openOrGetDirectConversation(userId: number): Promise<Conversation> {
        const payload: CreateDirectConversationRequest = { userId };
        const response = await apiClient.post<ApiResponse<Conversation>>(
            "/conversations/direct",
            payload
        );
        return response.data.data;
    },

    async getMessages(
        conversationId: number,
        cursor?: string | number | null,
        limit = 30
    ): Promise<GetMessagesResult> {
        const params: Record<string, string | number> = { limit };
        if (cursor !== undefined && cursor !== null && cursor !== "") {
            params.cursor = cursor;
        }
        const response = await apiClient.get<ApiResponse<ChatMessage[]>>(
            `/conversations/${conversationId}/messages`,
            { params }
        );
        return {
            items: response.data.data ?? [],
            nextCursor: response.data.meta?.nextCursor ?? null
        };
    },

    async sendMessageRest(
        conversationId: number,
        payload: SendMessageRequest
    ): Promise<ChatMessage> {
        const response = await apiClient.post<ApiResponse<ChatMessage>>(
            `/conversations/${conversationId}/messages`,
            payload
        );
        return response.data.data;
    },

    async markAsRead(
        conversationId: number,
        lastReadMessageId: number
    ): Promise<ConversationReadStateResponse> {
        const response = await apiClient.put<ApiResponse<ConversationReadStateResponse>>(
            `/conversations/${conversationId}/read`,
            { lastReadMessageId }
        );
        return response.data.data;
    }
};

export default chatService;
