import apiClient from "./apiClient";
import {
    ApiResponse,
    ChatAnswerResponse,
    ChatMessage,
    ChatSession,
    CreateChatSessionRequest,
    GenerateSummaryRequest,
    SendMessageRequest,
    Summary
} from "../types";

export const aiService = {
    // -------------------------------------------------------------
    // Summarization APIs
    // -------------------------------------------------------------
    async generateSummary(data: GenerateSummaryRequest): Promise<Summary> {
        const response = await apiClient.post<ApiResponse<Summary>>("/summaries/generate", data);
        return response.data.data;
    },

    async getMaterialSummaries(materialId: number): Promise<Summary[]> {
        const response = await apiClient.get<ApiResponse<Summary[]>>(`/materials/${materialId}/summaries`);
        return response.data.data;
    },

    async getSummary(id: number): Promise<Summary> {
        const response = await apiClient.get<ApiResponse<Summary>>(`/summaries/${id}`);
        return response.data.data;
    },

    // -------------------------------------------------------------
    // AI Study Chat APIs
    // -------------------------------------------------------------
    async createChatSession(data: CreateChatSessionRequest): Promise<ChatSession> {
        const response = await apiClient.post<ApiResponse<ChatSession>>("/chat/sessions", data);
        return response.data.data;
    },

    async getChatSessions(courseId?: number): Promise<ChatSession[]> {
        const params: Record<string, number> = {};
        if (courseId) params.courseId = courseId;

        const response = await apiClient.get<ApiResponse<ChatSession[]>>("/chat/sessions", { params });
        return response.data.data;
    },

    async getChatSession(id: number): Promise<ChatSession> {
        const response = await apiClient.get<ApiResponse<ChatSession>>(`/chat/sessions/${id}`);
        return response.data.data;
    },

    async deleteChatSession(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/chat/sessions/${id}`);
    },

    async getChatMessages(sessionId: number): Promise<ChatMessage[]> {
        const response = await apiClient.get<ApiResponse<ChatMessage[]>>(`/chat/sessions/${sessionId}/messages`);
        return response.data.data;
    },

    async sendMessage(sessionId: number, content: string): Promise<ChatAnswerResponse> {
        const payload: SendMessageRequest = { content };
        const response = await apiClient.post<ApiResponse<ChatAnswerResponse>>(
            `/chat/sessions/${sessionId}/messages`,
            payload
        );
        return response.data.data;
    }
};

export default aiService;
