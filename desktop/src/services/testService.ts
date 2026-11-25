import { apiClient } from "./apiClient";
import {
    ApiResponse,
    ClassTest,
    CreateClassTestRequest,
    UpdateClassTestRequest,
    StartClassTestAttemptResponse,
    ClassTestSubmitResultResponse,
    ClassTestResultsResponse,
    PageMeta
} from "../types";

export interface SubmitTestAttemptRequest {
    answers: Array<{
        questionId: number;
        selectedOptionIds: number[];
    }>;
    durationSeconds: number;
}

export const testService = {
    async createTest(classId: number, data: CreateClassTestRequest): Promise<ClassTest> {
        const response = await apiClient.post<ApiResponse<ClassTest>>(`/classes/${classId}/tests`, data);
        return response.data.data;
    },

    async getTests(
        classId: number,
        page: number = 0,
        size: number = 20
    ): Promise<{ items: ClassTest[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<ClassTest[]>>(`/classes/${classId}/tests`, {
            params: { page, size }
        });
        return {
            items: response.data.data || [],
            meta: response.data.meta
        };
    },

    async updateTest(id: number, data: UpdateClassTestRequest): Promise<ClassTest> {
        const response = await apiClient.put<ApiResponse<ClassTest>>(`/tests/${id}`, data);
        return response.data.data;
    },

    async deleteTest(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/tests/${id}`);
    },

    async startAttempt(testId: number): Promise<StartClassTestAttemptResponse> {
        const response = await apiClient.post<ApiResponse<StartClassTestAttemptResponse>>(
            `/tests/${testId}/attempts`
        );
        return response.data.data;
    },

    async submitAttempt(
        testId: number,
        attemptId: number,
        data: SubmitTestAttemptRequest,
        idempotencyKey?: string
    ): Promise<ClassTestSubmitResultResponse> {
        const key = idempotencyKey || crypto.randomUUID();
        const response = await apiClient.post<ApiResponse<ClassTestSubmitResultResponse>>(
            `/tests/${testId}/attempts/${attemptId}/submit`,
            data,
            {
                headers: {
                    "Idempotency-Key": key
                }
            }
        );
        return response.data.data;
    },

    async getResults(testId: number): Promise<ClassTestResultsResponse> {
        const response = await apiClient.get<ApiResponse<ClassTestResultsResponse>>(`/tests/${testId}/results`);
        return response.data.data;
    }
};

export default testService;
