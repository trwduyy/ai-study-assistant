import apiClient from "./apiClient";
import { ApiResponse, Material, MaterialDetail, MaterialUploadResponse } from "../types";

export const materialService = {
    async uploadMaterial(
        file: File,
        courseId: number,
        topicId?: number,
        onProgress?: (percent: number) => void
    ): Promise<MaterialUploadResponse> {
        const formData = new FormData();
        formData.append("file", file);
        formData.append("courseId", courseId.toString());
        if (topicId) {
            formData.append("topicId", topicId.toString());
        }

        const response = await apiClient.post<ApiResponse<MaterialUploadResponse>>(
            "/materials/upload",
            formData,
            {
                headers: {
                    "Content-Type": "multipart/form-data"
                },
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

    async getMaterials(courseId?: number, topicId?: number): Promise<Material[]> {
        const params: Record<string, number> = {};
        if (courseId) params.courseId = courseId;
        if (topicId) params.topicId = topicId;

        const response = await apiClient.get<ApiResponse<Material[]>>("/materials", { params });
        return response.data.data;
    },

    async getMaterialDetail(id: number): Promise<MaterialDetail> {
        const response = await apiClient.get<ApiResponse<MaterialDetail>>(`/materials/${id}`);
        return response.data.data;
    },

    async downloadMaterial(id: number, filename: string): Promise<void> {
        const response = await apiClient.get(`/materials/${id}/download`, {
            responseType: "blob"
        });

        // Trigger browser file download
        const blobUrl = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement("a");
        link.href = blobUrl;
        link.setAttribute("download", filename);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(blobUrl);
    },

    async deleteMaterial(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/materials/${id}`);
    }
};

export default materialService;
