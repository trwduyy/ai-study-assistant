import apiClient from "./apiClient";
import {
    ApiResponse,
    Course,
    CourseDetail,
    CreateCourseRequest,
    CreateTopicRequest,
    Topic,
    UpdateCourseRequest,
    UpdateTopicRequest
} from "../types";

export const courseService = {
    async getCourses(): Promise<Course[]> {
        const response = await apiClient.get<ApiResponse<Course[]>>("/courses");
        return response.data.data;
    },

    async getCourseDetail(id: number): Promise<CourseDetail> {
        const response = await apiClient.get<ApiResponse<CourseDetail>>(`/courses/${id}`);
        return response.data.data;
    },

    async createCourse(data: CreateCourseRequest): Promise<Course> {
        const response = await apiClient.post<ApiResponse<Course>>("/courses", data);
        return response.data.data;
    },

    async updateCourse(id: number, data: UpdateCourseRequest): Promise<Course> {
        const response = await apiClient.put<ApiResponse<Course>>(`/courses/${id}`, data);
        return response.data.data;
    },

    async deleteCourse(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/courses/${id}`);
    },

    async createTopic(courseId: number, data: CreateTopicRequest): Promise<Topic> {
        const response = await apiClient.post<ApiResponse<Topic>>(`/courses/${courseId}/topics`, data);
        return response.data.data;
    },

    async updateTopic(courseId: number, topicId: number, data: UpdateTopicRequest): Promise<Topic> {
        const response = await apiClient.put<ApiResponse<Topic>>(`/courses/${courseId}/topics/${topicId}`, data);
        return response.data.data;
    },

    async deleteTopic(courseId: number, topicId: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/courses/${courseId}/topics/${topicId}`);
    }
};

export default courseService;
