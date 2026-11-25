import apiClient from "./apiClient";
import {
    ApiResponse,
    Chapter,
    Course,
    CourseDetail,
    CreateChapterRequest,
    CreateCourseRequest,
    CreateTopicRequest,
    Material,
    MaterialCategory,
    MaterialChunk,
    MaterialDetail,
    MaterialStatus,
    PageMeta,
    ReextractResponse,
    Topic,
    UpdateChapterRequest,
    UpdateCourseRequest,
    UpdateMaterialRequest,
    UpdateTopicRequest
} from "../types";

export interface PaginatedResult<T> {
    items: T[];
    meta?: PageMeta | null;
}

export const learningService = {
    // -------------------------------------------------------------
    // Course Management
    // -------------------------------------------------------------
    async getCourses(page = 0, size = 20): Promise<PaginatedResult<Course>> {
        const response = await apiClient.get<ApiResponse<Course[]>>("/courses", {
            params: { page, size, sort: "createdAt,desc" }
        });
        return {
            items: response.data.data,
            meta: response.data.meta
        };
    },

    async getCourseDetail(courseId: number): Promise<CourseDetail> {
        const response = await apiClient.get<ApiResponse<CourseDetail>>(`/courses/${courseId}`);
        return response.data.data;
    },

    async createCourse(data: CreateCourseRequest): Promise<Course> {
        const response = await apiClient.post<ApiResponse<Course>>("/courses", data);
        return response.data.data;
    },

    async updateCourse(courseId: number, data: UpdateCourseRequest): Promise<Course> {
        const response = await apiClient.put<ApiResponse<Course>>(`/courses/${courseId}`, data);
        return response.data.data;
    },

    async deleteCourse(courseId: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/courses/${courseId}`);
    },

    // -------------------------------------------------------------
    // Chapters
    // -------------------------------------------------------------
    async getChapters(courseId: number): Promise<Chapter[]> {
        const response = await apiClient.get<ApiResponse<Chapter[]>>(`/courses/${courseId}/chapters`);
        return response.data.data;
    },

    async createChapter(courseId: number, data: CreateChapterRequest): Promise<Chapter> {
        const response = await apiClient.post<ApiResponse<Chapter>>(`/courses/${courseId}/chapters`, data);
        return response.data.data;
    },

    async updateChapter(chapterId: number, data: UpdateChapterRequest): Promise<Chapter> {
        const response = await apiClient.put<ApiResponse<Chapter>>(`/chapters/${chapterId}`, data);
        return response.data.data;
    },

    async deleteChapter(chapterId: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/chapters/${chapterId}`);
    },

    // -------------------------------------------------------------
    // Topics
    // -------------------------------------------------------------
    async getTopics(chapterId: number): Promise<Topic[]> {
        const response = await apiClient.get<ApiResponse<Topic[]>>(`/chapters/${chapterId}/topics`);
        return response.data.data;
    },

    async createTopic(chapterId: number, data: CreateTopicRequest): Promise<Topic> {
        const response = await apiClient.post<ApiResponse<Topic>>(`/chapters/${chapterId}/topics`, data);
        return response.data.data;
    },

    async updateTopic(topicId: number, data: UpdateTopicRequest): Promise<Topic> {
        const response = await apiClient.put<ApiResponse<Topic>>(`/topics/${topicId}`, data);
        return response.data.data;
    },

    async deleteTopic(topicId: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/topics/${topicId}`);
    },

    // -------------------------------------------------------------
    // Materials
    // -------------------------------------------------------------
    async uploadMaterial(
        courseId: number,
        file: File,
        topicId?: number,
        category?: MaterialCategory,
        onProgress?: (percent: number) => void
    ): Promise<Material> {
        const formData = new FormData();
        formData.append("file", file);
        if (topicId) formData.append("topicId", topicId.toString());
        if (category) formData.append("category", category);

        const response = await apiClient.post<ApiResponse<Material>>(
            `/courses/${courseId}/materials`,
            formData,
            {
                headers: { "Content-Type": "multipart/form-data" },
                onUploadProgress: (progressEvent) => {
                    if (progressEvent.total && onProgress) {
                        const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
                        onProgress(percent);
                    }
                }
            }
        );
        return response.data.data;
    },

    async getMaterials(
        courseId: number,
        topicId?: number,
        category?: string,
        status?: MaterialStatus,
        page = 0,
        size = 20
    ): Promise<PaginatedResult<Material>> {
        const params: Record<string, string | number> = { page, size };
        if (topicId) params.topicId = topicId;
        if (category) params.category = category;
        if (status) params.status = status;

        const response = await apiClient.get<ApiResponse<Material[]>>(`/courses/${courseId}/materials`, {
            params
        });
        return {
            items: response.data.data,
            meta: response.data.meta
        };
    },

    async getMaterialDetail(id: number): Promise<MaterialDetail> {
        const response = await apiClient.get<ApiResponse<MaterialDetail>>(`/materials/${id}`);
        return response.data.data;
    },

    async getMaterialContent(
        id: number,
        pageFrom?: number,
        pageTo?: number,
        page = 0,
        size = 10
    ): Promise<PaginatedResult<MaterialChunk>> {
        const params: Record<string, number> = { page, size };
        if (pageFrom) params.pageFrom = pageFrom;
        if (pageTo) params.pageTo = pageTo;

        const response = await apiClient.get<ApiResponse<MaterialChunk[]>>(`/materials/${id}/content`, {
            params
        });
        return {
            items: response.data.data,
            meta: response.data.meta
        };
    },

    async downloadMaterial(id: number, defaultFileName = "material.pdf"): Promise<void> {
        const response = await apiClient.get(`/materials/${id}/download`, {
            responseType: "blob"
        });
        const blobUrl = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement("a");
        link.href = blobUrl;
        link.setAttribute("download", defaultFileName);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(blobUrl);
    },

    async updateMaterial(id: number, data: UpdateMaterialRequest): Promise<Material> {
        const response = await apiClient.put<ApiResponse<Material>>(`/materials/${id}`, data);
        return response.data.data;
    },

    async deleteMaterial(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/materials/${id}`);
    },

    async reextractMaterial(id: number): Promise<ReextractResponse> {
        const response = await apiClient.post<ApiResponse<ReextractResponse>>(`/materials/${id}/reextract`);
        return response.data.data;
    }
};

export default learningService;
