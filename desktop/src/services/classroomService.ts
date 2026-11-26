import { apiClient } from "./apiClient";
import {
    ApiResponse,
    Classroom,
    ClassroomStatus,
    CreateClassroomRequest,
    UpdateClassroomRequest,
    UpdateClassroomStatusRequest,
    ClassMember,
    ClassRole,
    AddCoTeacherRequest,
    ClassJoinRequest,
    JoinRequestStatus,
    CreateJoinRequestRequest,
    DecideJoinRequestRequest,
    ClassPost,
    CreateClassPostRequest,
    PinClassPostRequest,
    ClassMaterial,
    PageMeta
} from "../types";

export const classroomService = {
    async createClassroom(data: CreateClassroomRequest): Promise<Classroom> {
        const response = await apiClient.post<ApiResponse<Classroom>>("/classes", data);
        return response.data.data;
    },

    async getMyClassrooms(params?: {
        status?: ClassroomStatus;
        keyword?: string;
        page?: number;
        size?: number;
    }): Promise<{ items: Classroom[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<Classroom[]>>("/classes", {
            params: {
                status: params?.status,
                keyword: params?.keyword,
                page: params?.page ?? 0,
                size: params?.size ?? 20
            }
        });
        return {
            items: response.data.data || [],
            meta: response.data.meta
        };
    },

    async getClassroomDetail(id: number): Promise<Classroom> {
        const response = await apiClient.get<ApiResponse<Classroom>>(`/classes/${id}`);
        return response.data.data;
    },

    async updateClassroom(id: number, data: UpdateClassroomRequest): Promise<Classroom> {
        const headers: Record<string, string> = {};
        if (typeof data.version === "number") {
            headers["If-Match"] = `"${data.version}"`;
        }
        const response = await apiClient.put<ApiResponse<Classroom>>(`/classes/${id}`, data, { headers });
        return response.data.data;
    },

    async updateStatus(id: number, data: UpdateClassroomStatusRequest): Promise<Classroom> {
        const headers: Record<string, string> = {};
        if (typeof data.version === "number") {
            headers["If-Match"] = `"${data.version}"`;
        }
        const response = await apiClient.put<ApiResponse<Classroom>>(`/classes/${id}/status`, data, { headers });
        return response.data.data;
    },

    // -------------------------------------------------------------
    // Join Requests
    // -------------------------------------------------------------
    async createJoinRequest(data: CreateJoinRequestRequest): Promise<ClassJoinRequest> {
        const response = await apiClient.post<ApiResponse<ClassJoinRequest>>("/classes/join-requests", data);
        return response.data.data;
    },

    async getJoinRequests(
        classId: number,
        status: JoinRequestStatus = "PENDING",
        page: number = 0,
        size: number = 20
    ): Promise<{ items: ClassJoinRequest[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<ClassJoinRequest[]>>(`/classes/${classId}/join-requests`, {
            params: { status, page, size }
        });
        return {
            items: response.data.data || [],
            meta: response.data.meta
        };
    },

    async decideJoinRequest(
        classId: number,
        reqId: number,
        data: DecideJoinRequestRequest
    ): Promise<ClassJoinRequest> {
        const response = await apiClient.put<ApiResponse<ClassJoinRequest>>(
            `/classes/${classId}/join-requests/${reqId}`,
            data
        );
        return response.data.data;
    },

    // -------------------------------------------------------------
    // Members & Roles
    // -------------------------------------------------------------
    async getMembers(
        classId: number,
        role?: ClassRole,
        page: number = 0,
        size: number = 50
    ): Promise<{ items: ClassMember[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<ClassMember[]>>(`/classes/${classId}/members`, {
            params: { role, page, size }
        });
        return {
            items: response.data.data || [],
            meta: response.data.meta
        };
    },

    async addCoTeacher(classId: number, data: AddCoTeacherRequest): Promise<ClassMember> {
        const response = await apiClient.post<ApiResponse<ClassMember>>(`/classes/${classId}/co-teachers`, data);
        return response.data.data;
    },

    async removeMember(classId: number, userId: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/classes/${classId}/members/${userId}`);
    },

    async leaveClassroom(classId: number): Promise<void> {
        await apiClient.post<ApiResponse<null>>(`/classes/${classId}/leave`);
    },

    // -------------------------------------------------------------
    // Class Feed / Posts
    // -------------------------------------------------------------
    async getPosts(
        classId: number,
        page: number = 0,
        size: number = 20
    ): Promise<{ items: ClassPost[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<ClassPost[]>>(`/classes/${classId}/posts`, {
            params: { page, size }
        });
        return {
            items: response.data.data || [],
            meta: response.data.meta
        };
    },

    async createPost(classId: number, data: CreateClassPostRequest): Promise<ClassPost> {
        const response = await apiClient.post<ApiResponse<ClassPost>>(`/classes/${classId}/posts`, data);
        return response.data.data;
    },

    async pinPost(classId: number, postId: number, data?: PinClassPostRequest): Promise<ClassPost> {
        const response = await apiClient.put<ApiResponse<ClassPost>>(
            `/classes/${classId}/posts/${postId}/pin`,
            data ?? {}
        );
        return response.data.data;
    },

    // -------------------------------------------------------------
    // Class Materials
    // -------------------------------------------------------------
    async getMaterials(
        classId: number,
        page: number = 0,
        size: number = 20
    ): Promise<{ items: ClassMaterial[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<ClassMaterial[]>>(`/classes/${classId}/materials`, {
            params: { page, size }
        });
        return {
            items: response.data.data || [],
            meta: response.data.meta
        };
    },

    async uploadMaterial(classId: number, file: File): Promise<ClassMaterial> {
        const formData = new FormData();
        formData.append("file", file);
        const response = await apiClient.post<ApiResponse<ClassMaterial>>(
            `/classes/${classId}/materials`,
            formData,
            {
                headers: { "Content-Type": "multipart/form-data" }
            }
        );
        return response.data.data;
    },

    getDownloadMaterialUrl(materialId: number): string {
        const baseURL = apiClient.defaults.baseURL || "http://localhost:8080/api/v1";
        return `${baseURL}/class-materials/${materialId}/download`;
    }
};

export default classroomService;
