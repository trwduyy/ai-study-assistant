import { apiClient } from "./apiClient";
import {
    ApiResponse,
    TeacherDashboardResponse,
    StudentDashboardData
} from "../types";

export const dashboardService = {
    async getTeacherDashboard(): Promise<TeacherDashboardResponse> {
        const response = await apiClient.get<ApiResponse<TeacherDashboardResponse>>("/dashboard/teacher");
        return response.data.data;
    },

    async getStudentDashboard(): Promise<StudentDashboardData> {
        const response = await apiClient.get<ApiResponse<StudentDashboardData>>("/dashboard/student");
        return response.data.data;
    }
};

export default dashboardService;
