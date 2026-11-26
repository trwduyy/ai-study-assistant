import apiClient from "./apiClient";
import {
    AiChatAnswerResponse,
    AiChatDetail,
    AiChatMessage,
    AiChatSession,
    AiFeedbackResponse,
    AiJobAcceptedResponse,
    AiJobCancelResponse,
    AiJobResponse,
    AiSummary,
    ApiResponse,
    CreateAiChatRequest,
    CreateAiFeedbackRequest,
    CreateExplanationRequest,
    CreateSummaryRequest,
    GenerateAiFlashcardsRequest,
    GenerateAiQuizRequest,
    PageMeta,
    SendAiChatMessageRequest
} from "../types";

export interface PaginatedAiChats {
    items: AiChatSession[];
    meta?: PageMeta | null;
}

export interface ChatDetailWithCursor {
    detail: AiChatDetail;
    nextCursor: string | null;
}

export const aiService = {
    // -------------------------------------------------------------
    // AI Jobs Polling & Cancellation
    // -------------------------------------------------------------
    async getJob(jobId: string): Promise<AiJobResponse> {
        const response = await apiClient.get<ApiResponse<AiJobResponse>>(`/ai/jobs/${jobId}`);
        return response.data.data;
    },

    async cancelJob(jobId: string): Promise<AiJobCancelResponse> {
        const response = await apiClient.delete<ApiResponse<AiJobCancelResponse>>(`/ai/jobs/${jobId}`);
        return response.data.data;
    },

    // -------------------------------------------------------------
    // Summaries
    // -------------------------------------------------------------
    async requestSummary(data: CreateSummaryRequest): Promise<AiJobAcceptedResponse> {
        const response = await apiClient.post<ApiResponse<AiJobAcceptedResponse>>("/ai/summaries", data);
        return response.data.data;
    },

    async listMaterialSummaries(materialId: number): Promise<AiSummary[]> {
        const response = await apiClient.get<ApiResponse<AiSummary[]>>(`/materials/${materialId}/summaries`);
        return response.data.data;
    },

    async getSummary(id: number): Promise<AiSummary> {
        const response = await apiClient.get<ApiResponse<AiSummary>>(`/ai/summaries/${id}`);
        return response.data.data;
    },

    async deleteSummary(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/ai/summaries/${id}`);
    },

    // -------------------------------------------------------------
    // Explanations
    // -------------------------------------------------------------
    async requestExplanation(data: CreateExplanationRequest): Promise<AiJobAcceptedResponse> {
        const response = await apiClient.post<ApiResponse<AiJobAcceptedResponse>>("/ai/explanations", data);
        return response.data.data;
    },

    // -------------------------------------------------------------
    // Quiz & Flashcard Generation requests
    // -------------------------------------------------------------
    async requestQuiz(data: GenerateAiQuizRequest): Promise<AiJobAcceptedResponse> {
        const response = await apiClient.post<ApiResponse<AiJobAcceptedResponse>>("/ai/quizzes", data);
        return response.data.data;
    },

    async requestFlashcards(data: GenerateAiFlashcardsRequest): Promise<AiJobAcceptedResponse> {
        const response = await apiClient.post<ApiResponse<AiJobAcceptedResponse>>("/ai/flashcards", data);
        return response.data.data;
    },

    // -------------------------------------------------------------
    // Grounded Q&A Chat Sessions & Messages
    // -------------------------------------------------------------
    async createChat(data: CreateAiChatRequest): Promise<AiChatSession> {
        const response = await apiClient.post<ApiResponse<AiChatSession>>("/ai/chats", data);
        return response.data.data;
    },

    async listChats(courseId?: number, page = 0, size = 20): Promise<PaginatedAiChats> {
        const params: Record<string, number> = { page, size };
        if (courseId) params.courseId = courseId;
        const response = await apiClient.get<ApiResponse<AiChatSession[]>>("/ai/chats", { params });
        return {
            items: response.data.data,
            meta: response.data.meta
        };
    },

    async getChatDetail(chatId: number, cursor?: number, limit = 30): Promise<ChatDetailWithCursor> {
        const params: Record<string, number> = { limit };
        if (cursor) params.cursor = cursor;
        const response = await apiClient.get<ApiResponse<AiChatDetail>>(`/ai/chats/${chatId}`, { params });
        return {
            detail: response.data.data,
            nextCursor: response.data.meta?.nextCursor ?? null
        };
    },

    async deleteChat(chatId: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/ai/chats/${chatId}`);
    },

    async sendMessage(chatId: number, data: SendAiChatMessageRequest): Promise<AiChatAnswerResponse> {
        const response = await apiClient.post<ApiResponse<AiChatAnswerResponse>>(
            `/ai/chats/${chatId}/messages`,
            data
        );
        return response.data.data;
    },

    // -------------------------------------------------------------
    // AI Feedback
    // -------------------------------------------------------------
    async submitFeedback(data: CreateAiFeedbackRequest): Promise<AiFeedbackResponse> {
        const response = await apiClient.post<ApiResponse<AiFeedbackResponse>>("/ai/feedback", data);
        return response.data.data;
    }
};

export default aiService;
