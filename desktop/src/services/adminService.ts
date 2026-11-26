import apiClient from "./apiClient";
import {
    AdminClassItem,
    AdminUserItem,
    ApiResponse,
    AuditLogItem,
    ClassroomStatus,
    CreateReportRequest,
    PageMeta,
    ReportItem,
    ReportStatus,
    ResolveReportRequest,
    SystemSettingItem,
    SystemSettingUpdateInput,
    User,
    UserRole,
    UserStatus
} from "../types";

export const adminService = {
    // -------------------------------------------------------------
    // Users Governance (Admin)
    // -------------------------------------------------------------
    async listUsers(params?: {
        role?: UserRole;
        status?: UserStatus;
        keyword?: string;
        page?: number;
        size?: number;
    }): Promise<{ items: AdminUserItem[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<AdminUserItem[]>>("/users", {
            params: {
                role: params?.role,
                status: params?.status,
                keyword: params?.keyword || undefined,
                page: params?.page ?? 0,
                size: params?.size ?? 20
            }
        });
        return {
            items: response.data.data ?? [],
            meta: response.data.meta
        };
    },

    async updateUserRole(userId: number, role: UserRole, version: number): Promise<User> {
        const response = await apiClient.put<ApiResponse<User>>(
            `/users/${userId}/role`,
            { role, version },
            {
                headers: {
                    "If-Match": `"${version}"`
                }
            }
        );
        return response.data.data;
    },

    async updateUserStatus(userId: number, status: UserStatus, version: number): Promise<User> {
        const response = await apiClient.put<ApiResponse<User>>(
            `/users/${userId}/status`,
            { status, version },
            {
                headers: {
                    "If-Match": `"${version}"`
                }
            }
        );
        return response.data.data;
    },

    // -------------------------------------------------------------
    // Classrooms Governance (Admin)
    // -------------------------------------------------------------
    async listClasses(params?: {
        status?: ClassroomStatus;
        keyword?: string;
        page?: number;
        size?: number;
    }): Promise<{ items: AdminClassItem[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<AdminClassItem[]>>("/admin/classes", {
            params: {
                status: params?.status,
                keyword: params?.keyword || undefined,
                page: params?.page ?? 0,
                size: params?.size ?? 20
            }
        });
        return {
            items: response.data.data ?? [],
            meta: response.data.meta
        };
    },

    async updateClassroomStatus(
        classId: number,
        status: ClassroomStatus,
        version: number
    ): Promise<AdminClassItem> {
        const response = await apiClient.put<ApiResponse<AdminClassItem>>(
            `/admin/classes/${classId}/status`,
            { status, version },
            {
                headers: {
                    "If-Match": `"${version}"`
                }
            }
        );
        return response.data.data;
    },

    // -------------------------------------------------------------
    // Reports (Admin + All users createReport)
    // -------------------------------------------------------------
    async createReport(data: CreateReportRequest): Promise<ReportItem> {
        const response = await apiClient.post<ApiResponse<ReportItem>>("/reports", data);
        return response.data.data;
    },

    async listReports(params?: {
        status?: ReportStatus;
        type?: string;
        page?: number;
        size?: number;
    }): Promise<{ items: ReportItem[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<ReportItem[]>>("/admin/reports", {
            params: {
                status: params?.status,
                type: params?.type || undefined,
                page: params?.page ?? 0,
                size: params?.size ?? 20
            }
        });
        return {
            items: response.data.data ?? [],
            meta: response.data.meta
        };
    },

    async resolveReport(reportId: number, data: ResolveReportRequest): Promise<ReportItem> {
        const response = await apiClient.put<ApiResponse<ReportItem>>(
            `/admin/reports/${reportId}`,
            data
        );
        return response.data.data;
    },

    // -------------------------------------------------------------
    // System Settings (Admin)
    // -------------------------------------------------------------
    async getSettings(): Promise<SystemSettingItem[]> {
        const response = await apiClient.get<ApiResponse<SystemSettingItem[]>>("/admin/settings");
        return response.data.data ?? [];
    },

    async updateSettings(settings: SystemSettingUpdateInput[]): Promise<SystemSettingItem[]> {
        const response = await apiClient.put<ApiResponse<SystemSettingItem[]>>("/admin/settings", {
            settings
        });
        return response.data.data ?? [];
    },

    // -------------------------------------------------------------
    // Audit Logs (Admin)
    // -------------------------------------------------------------
    async listAuditLogs(params?: {
        userId?: number;
        action?: string;
        page?: number;
        size?: number;
    }): Promise<{ items: AuditLogItem[]; meta?: PageMeta | null }> {
        const response = await apiClient.get<ApiResponse<AuditLogItem[]>>("/admin/audit-logs", {
            params: {
                userId: params?.userId,
                action: params?.action || undefined,
                page: params?.page ?? 0,
                size: params?.size ?? 30
            }
        });
        return {
            items: response.data.data ?? [],
            meta: response.data.meta
        };
    }
};

export default adminService;
