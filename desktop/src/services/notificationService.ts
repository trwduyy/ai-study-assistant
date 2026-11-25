import apiClient from "./apiClient";
import {
    ApiResponse,
    NotificationItem,
    NotificationSetting,
    NotificationUnreadCount
} from "../types";

export interface GetNotificationsResult {
    items: NotificationItem[];
    nextCursor: string | null;
}

export const notificationService = {
    async getNotifications(
        cursor?: string | null,
        limit = 15,
        unread?: boolean
    ): Promise<GetNotificationsResult> {
        const params: Record<string, string | number | boolean> = { limit };
        if (cursor) {
            params.cursor = cursor;
        }
        if (typeof unread === "boolean") {
            params.unread = unread;
        }
        const response = await apiClient.get<ApiResponse<NotificationItem[]>>("/notifications", {
            params
        });
        return {
            items: response.data.data ?? [],
            nextCursor: response.data.meta?.nextCursor ?? null
        };
    },

    async getUnreadCount(): Promise<number> {
        const response = await apiClient.get<ApiResponse<NotificationUnreadCount>>(
            "/notifications/unread-count"
        );
        return response.data.data?.unreadCount ?? 0;
    },

    async markAsRead(id: number): Promise<NotificationItem> {
        const response = await apiClient.put<ApiResponse<NotificationItem>>(
            `/notifications/${id}/read`
        );
        return response.data.data;
    },

    async markAllAsRead(): Promise<void> {
        await apiClient.put<ApiResponse<null>>("/notifications/read-all");
    },

    async getSettings(): Promise<NotificationSetting[]> {
        const response = await apiClient.get<ApiResponse<NotificationSetting[]>>(
            "/notifications/settings"
        );
        return response.data.data ?? [];
    },

    async updateSettings(settings: NotificationSetting[]): Promise<NotificationSetting[]> {
        const response = await apiClient.put<ApiResponse<NotificationSetting[]>>(
            "/notifications/settings",
            { settings }
        );
        return response.data.data ?? [];
    }
};

export default notificationService;
