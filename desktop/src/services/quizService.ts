import apiClient, { withIdempotencyKey } from "./apiClient";
import {
    ApiResponse,
    AttemptReview,
    GenerateAiQuizRequest,
    PageMeta,
    QuizAttemptSummary,
    QuizDetail,
    QuizSubmitResult,
    QuizSummary,
    SaveAttemptDraftRequest,
    SaveAttemptDraftResponse,
    StartAttemptResponse,
    SubmitAttemptRequest,
    UpdateQuizRequest
} from "../types";
import aiService from "./aiService";

export interface PaginatedQuizzes {
    items: QuizSummary[];
    meta?: PageMeta | null;
}

export interface PaginatedAttempts {
    items: QuizAttemptSummary[];
    meta?: PageMeta | null;
}

export const quizService = {
    async generateQuiz(data: GenerateAiQuizRequest) {
        return aiService.requestQuiz(data);
    },

    async listQuizzes(courseId?: number, page = 0, size = 20): Promise<PaginatedQuizzes> {
        const params: Record<string, number> = { page, size };
        if (courseId) params.courseId = courseId;
        const response = await apiClient.get<ApiResponse<QuizSummary[]>>("/quizzes", { params });
        return {
            items: response.data.data,
            meta: response.data.meta
        };
    },

    async getQuiz(id: number): Promise<QuizDetail> {
        const response = await apiClient.get<ApiResponse<QuizDetail>>(`/quizzes/${id}`);
        return response.data.data;
    },

    async updateQuiz(id: number, data: UpdateQuizRequest): Promise<QuizSummary> {
        const response = await apiClient.put<ApiResponse<QuizSummary>>(`/quizzes/${id}`, data);
        return response.data.data;
    },

    async deleteQuiz(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/quizzes/${id}`);
    },

    async startAttempt(quizId: number): Promise<StartAttemptResponse> {
        const response = await apiClient.post<ApiResponse<StartAttemptResponse>>(`/quizzes/${quizId}/attempts`);
        return response.data.data;
    },

    async saveAttemptDraft(
        quizId: number,
        attemptId: number,
        data: SaveAttemptDraftRequest
    ): Promise<SaveAttemptDraftResponse> {
        const response = await apiClient.put<ApiResponse<SaveAttemptDraftResponse>>(
            `/quizzes/${quizId}/attempts/${attemptId}`,
            data
        );
        return response.data.data;
    },

    async submitAttempt(
        quizId: number,
        attemptId: number,
        data: SubmitAttemptRequest,
        existingIdempotencyKey?: string
    ): Promise<QuizSubmitResult> {
        const headers: Record<string, string> = {
            "Idempotency-Key": existingIdempotencyKey || crypto.randomUUID()
        };
        const response = await apiClient.post<ApiResponse<QuizSubmitResult>>(
            `/quizzes/${quizId}/attempts/${attemptId}/submit`,
            data,
            { headers }
        );
        return response.data.data;
    },

    async listAttempts(quizId: number, page = 0, size = 20): Promise<PaginatedAttempts> {
        const response = await apiClient.get<ApiResponse<QuizAttemptSummary[]>>(`/quizzes/${quizId}/attempts`, {
            params: { page, size }
        });
        return {
            items: response.data.data,
            meta: response.data.meta
        };
    },

    async getAttemptReview(attemptId: number): Promise<AttemptReview> {
        const response = await apiClient.get<ApiResponse<AttemptReview>>(`/attempts/${attemptId}`);
        return response.data.data;
    }
};

export default quizService;
