import apiClient from "./apiClient";
import {
    AnalyticsOverview,
    ApiResponse,
    CourseAnalytics,
    ReviewRecommendation
} from "../types";

export const analyticsService = {
    async getOverview(): Promise<AnalyticsOverview> {
        const response = await apiClient.get<ApiResponse<AnalyticsOverview>>("/analytics/overview");
        return response.data.data;
    },

    async getCourseAnalytics(courseId: number): Promise<CourseAnalytics> {
        const response = await apiClient.get<ApiResponse<CourseAnalytics>>(`/analytics/courses/${courseId}`);
        return response.data.data;
    },

    async getReviewRecommendations(): Promise<ReviewRecommendation[]> {
        const response = await apiClient.get<ApiResponse<ReviewRecommendation[]>>("/analytics/review-recommendations");
        return response.data.data;
    }
};

export default analyticsService;
