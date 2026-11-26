import apiClient from "./apiClient";
import {
    AnalyticsOverview,
    ApiResponse,
    FrequentMistake,
    ReviewTopic,
    ScoreTrendItem,
    StudentDashboardData
} from "../types";

export const analyticsService = {
    async getOverview(): Promise<AnalyticsOverview> {
        const response = await apiClient.get<ApiResponse<AnalyticsOverview>>("/analytics/overview");
        return response.data.data;
    },

    async getScoreTrend(from?: string, to?: string, courseId?: number): Promise<ScoreTrendItem[]> {
        const params: Record<string, string | number> = {};
        if (from) params.from = from;
        if (to) params.to = to;
        if (courseId) params.courseId = courseId;

        const response = await apiClient.get<ApiResponse<ScoreTrendItem[]>>("/analytics/score-trend", {
            params
        });
        return response.data.data;
    },

    async getFrequentMistakes(courseId?: number, limit = 10): Promise<FrequentMistake[]> {
        const params: Record<string, number> = { limit };
        if (courseId) params.courseId = courseId;

        const response = await apiClient.get<ApiResponse<FrequentMistake[]>>("/analytics/frequent-mistakes", {
            params
        });
        return response.data.data;
    },

    async getReviewTopics(): Promise<ReviewTopic[]> {
        const response = await apiClient.get<ApiResponse<ReviewTopic[]>>("/analytics/review-topics");
        return response.data.data;
    },

    async getStudentDashboard(): Promise<StudentDashboardData> {
        const response = await apiClient.get<ApiResponse<StudentDashboardData>>("/dashboard/student");
        return response.data.data;
    }
};

export default analyticsService;
