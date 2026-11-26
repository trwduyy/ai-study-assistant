import { apiClient } from "./apiClient";
import {
    ApiResponse,
    Assignment,
    AssignmentSummary,
    CreateAssignmentRequest,
    UpdateAssignmentRequest,
    Submission,
    SubmissionSummary,
    SubmissionStatus,
    GradeSubmissionRequest,
    ReopenSubmissionRequest,
    PageMeta
} from "../types";

export const assignmentService = {
    async createAssignment(
        classId: number,
        data: CreateAssignmentRequest,
        files?: File[]
    ): Promise<Assignment> {
        if (files && files.length > 0) {
            const formData = new FormData();
            formData.append(
                "data",
                new Blob([JSON.stringify(data)], { type: "application/json" })
            );
            files.forEach((file) => {
                formData.append("files", file);
            });

            const response = await apiClient.post<ApiResponse<Assignment>>(
                `/classes/${classId}/assignments`,
                formData,
                {
                    headers: { "Content-Type": "multipart/form-data" }
                }
            );
            return response.data.data;
        }

        const response = await apiClient.post<ApiResponse<Assignment>>(
            `/classes/${classId}/assignments`,
            data
        );
        return response.data.data;
    },

    async getAssignments(
        classId: number,
        page: number = 0,
        size: number = 20
    ): Promise<{ items: AssignmentSummary[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<AssignmentSummary[]>>(
            `/classes/${classId}/assignments`,
            {
                params: { page, size }
            }
        );
        return {
            items: response.data.data || [],
            meta: response.data.meta
        };
    },

    async getAssignmentDetail(id: number): Promise<Assignment> {
        const response = await apiClient.get<ApiResponse<Assignment>>(`/assignments/${id}`);
        return response.data.data;
    },

    async updateAssignment(id: number, data: UpdateAssignmentRequest): Promise<Assignment> {
        const headers: Record<string, string> = {};
        if (typeof data.version === "number") {
            headers["If-Match"] = `"${data.version}"`;
        }
        const response = await apiClient.put<ApiResponse<Assignment>>(`/assignments/${id}`, data, {
            headers
        });
        return response.data.data;
    },

    async deleteAssignment(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/assignments/${id}`);
    },

    // -------------------------------------------------------------
    // Submissions
    // -------------------------------------------------------------
    async submitAssignment(
        id: number,
        textAnswer?: string,
        files?: File[],
        idempotencyKey?: string
    ): Promise<Submission> {
        const key = idempotencyKey || crypto.randomUUID();

        if (files && files.length > 0) {
            const formData = new FormData();
            if (textAnswer) {
                formData.append("textAnswer", textAnswer);
            }
            files.forEach((f) => formData.append("files", f));

            const response = await apiClient.post<ApiResponse<Submission>>(
                `/assignments/${id}/submissions`,
                formData,
                {
                    headers: {
                        "Content-Type": "multipart/form-data",
                        "Idempotency-Key": key
                    }
                }
            );
            return response.data.data;
        }

        const response = await apiClient.post<ApiResponse<Submission>>(
            `/assignments/${id}/submissions`,
            { textAnswer: textAnswer || "" },
            {
                headers: {
                    "Idempotency-Key": key
                }
            }
        );
        return response.data.data;
    },

    async getSubmissions(
        assignmentId: number,
        params?: {
            status?: SubmissionStatus;
            late?: boolean;
            page?: number;
            size?: number;
        }
    ): Promise<{ items: SubmissionSummary[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<SubmissionSummary[]>>(
            `/assignments/${assignmentId}/submissions`,
            {
                params: {
                    status: params?.status,
                    late: params?.late,
                    page: params?.page ?? 0,
                    size: params?.size ?? 20
                }
            }
        );
        return {
            items: response.data.data || [],
            meta: response.data.meta
        };
    },

    async getMySubmission(assignmentId: number): Promise<Submission | null> {
        try {
            const response = await apiClient.get<ApiResponse<Submission | null>>(
                `/assignments/${assignmentId}/submissions/me`
            );
            return response.data.data;
        } catch {
            return null;
        }
    },

    async gradeSubmission(id: number, data: GradeSubmissionRequest): Promise<Submission> {
        const headers: Record<string, string> = {};
        if (typeof data.version === "number") {
            headers["If-Match"] = `"${data.version}"`;
        }
        const response = await apiClient.put<ApiResponse<Submission>>(
            `/submissions/${id}/grade`,
            data,
            { headers }
        );
        return response.data.data;
    },

    async reopenSubmission(id: number, data?: ReopenSubmissionRequest): Promise<Submission> {
        const headers: Record<string, string> = {};
        if (data && typeof data.version === "number") {
            headers["If-Match"] = `"${data.version}"`;
        }
        const response = await apiClient.put<ApiResponse<Submission>>(
            `/submissions/${id}/reopen`,
            data ?? {},
            { headers }
        );
        return response.data.data;
    },

    getDownloadSubmissionFileUrl(submissionId: number, fileId: number): string {
        const baseURL = apiClient.defaults.baseURL || "http://localhost:8080/api/v1";
        return `${baseURL}/submissions/${submissionId}/download?fileId=${fileId}`;
    }
};

export default assignmentService;
