import apiClient from "./apiClient";
import {
    ApiResponse,
    AttemptDetail,
    GenerateQuizRequest,
    Quiz,
    QuizAttemptHistory,
    QuizExam,
    QuizSubmitResult,
    SubmitQuizRequest
} from "../types";

export const quizService = {
    async generateQuiz(data: GenerateQuizRequest): Promise<Quiz> {
        const response = await apiClient.post<ApiResponse<Quiz>>("/quizzes/generate", data);
        return response.data.data;
    },

    async getQuizzesByCourse(courseId: number): Promise<Quiz[]> {
        const response = await apiClient.get<ApiResponse<Quiz[]>>("/quizzes", {
            params: { courseId }
        });
        return response.data.data;
    },

    async getQuizExam(id: number): Promise<QuizExam> {
        const response = await apiClient.get<ApiResponse<QuizExam>>(`/quizzes/${id}`);
        return response.data.data;
    },

    async submitQuiz(quizId: number, data: SubmitQuizRequest): Promise<QuizSubmitResult> {
        const response = await apiClient.post<ApiResponse<QuizSubmitResult>>(
            `/quizzes/${quizId}/attempts`,
            data
        );
        return response.data.data;
    },

    async getAttemptDetail(attemptId: number): Promise<AttemptDetail> {
        const response = await apiClient.get<ApiResponse<AttemptDetail>>(`/quiz-attempts/${attemptId}`);
        return response.data.data;
    },

    async getQuizAttempts(quizId: number): Promise<QuizAttemptHistory[]> {
        const response = await apiClient.get<ApiResponse<QuizAttemptHistory[]>>(`/quizzes/${quizId}/attempts`);
        return response.data.data;
    }
};

export default quizService;
