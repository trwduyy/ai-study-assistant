import apiClient from "./apiClient";
import {
    ApiResponse,
    CalendarEvent,
    CalendarImportPreview,
    CalendarViewMode,
    ConfirmCalendarImportResponse,
    CreateClassEventRequest,
    CreatePersonalEventRequest,
    GoogleAuthorizeResponse,
    GoogleIntegrationStatus,
    GoogleSyncRequest,
    GoogleSyncResponse,
    PageMeta,
    UpdateClassEventRequest,
    UpdatePersonalEventRequest
} from "../types";

const GOOGLE_STATUS_STORAGE_PREFIX = "ai_study_google_calendar_status_";

export function openExternalBrowserUrl(url: string): void {
    const openFn = window.electronAPI?.openExternal ?? window.electronAPI?.openExternalUrl;
    if (openFn) {
        void openFn(url);
        return;
    }
    window.open(url, "_blank", "noopener,noreferrer");
}

export const scheduleService = {
    // -------------------------------------------------------------
    // Unified Calendar & Personal Events
    // -------------------------------------------------------------
    async getCalendar(
        from?: string,
        to?: string,
        view: CalendarViewMode = "WEEK"
    ): Promise<CalendarEvent[]> {
        const params: Record<string, string> = { view };
        if (from) params.from = from;
        if (to) params.to = to;
        const response = await apiClient.get<ApiResponse<CalendarEvent[]>>("/calendar", {
            params
        });
        return response.data.data ?? [];
    },

    async getPersonalEvents(
        from?: string,
        to?: string,
        page = 0,
        size = 50
    ): Promise<{ items: CalendarEvent[]; meta?: PageMeta | null }> {
        const params: Record<string, string | number> = { page, size };
        if (from) params.from = from;
        if (to) params.to = to;
        const response = await apiClient.get<ApiResponse<CalendarEvent[]>>("/personal-events", {
            params
        });
        return {
            items: response.data.data ?? [],
            meta: response.data.meta
        };
    },

    async createPersonalEvent(data: CreatePersonalEventRequest): Promise<CalendarEvent> {
        const response = await apiClient.post<ApiResponse<CalendarEvent>>(
            "/personal-events",
            data
        );
        return response.data.data;
    },

    async updatePersonalEvent(
        id: number,
        data: UpdatePersonalEventRequest
    ): Promise<CalendarEvent> {
        const response = await apiClient.put<ApiResponse<CalendarEvent>>(
            `/personal-events/${id}`,
            data,
            {
                headers: {
                    "If-Match": `"${data.version}"`
                }
            }
        );
        return response.data.data;
    },

    async deletePersonalEvent(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/personal-events/${id}`);
    },

    // -------------------------------------------------------------
    // Classroom Events & Meetings (Teacher)
    // -------------------------------------------------------------
    async createClassEvent(
        classId: number,
        data: CreateClassEventRequest
    ): Promise<CalendarEvent> {
        const response = await apiClient.post<ApiResponse<CalendarEvent>>(
            `/classes/${classId}/events`,
            data
        );
        return response.data.data;
    },

    async getClassEvents(
        classId: number,
        from?: string,
        to?: string,
        page = 0,
        size = 50
    ): Promise<{ items: CalendarEvent[]; meta?: PageMeta | null }> {
        const params: Record<string, string | number> = { page, size };
        if (from) params.from = from;
        if (to) params.to = to;
        const response = await apiClient.get<ApiResponse<CalendarEvent[]>>(
            `/classes/${classId}/events`,
            { params }
        );
        return {
            items: response.data.data ?? [],
            meta: response.data.meta
        };
    },

    async updateClassEvent(id: number, data: UpdateClassEventRequest): Promise<CalendarEvent> {
        const response = await apiClient.put<ApiResponse<CalendarEvent>>(`/events/${id}`, data, {
            headers: {
                "If-Match": `"${data.version}"`
            }
        });
        return response.data.data;
    },

    async cancelClassEvent(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/events/${id}`);
    },

    // -------------------------------------------------------------
    // Calendar Imports (.ics / .csv)
    // -------------------------------------------------------------
    async downloadTemplateCsv(): Promise<void> {
        const response = await apiClient.get<Blob>("/calendar/import-template", {
            responseType: "blob"
        });
        const blobUrl = URL.createObjectURL(response.data);
        const anchor = document.createElement("a");
        anchor.href = blobUrl;
        anchor.download = "calendar-template.csv";
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(blobUrl);
    },

    async previewImport(
        file: File,
        defaultTimezone = "Asia/Ho_Chi_Minh"
    ): Promise<CalendarImportPreview> {
        const formData = new FormData();
        formData.append("file", file);
        const response = await apiClient.post<ApiResponse<CalendarImportPreview>>(
            "/calendar/imports/preview",
            formData,
            {
                params: { defaultTimezone },
                headers: {
                    "Content-Type": "multipart/form-data"
                }
            }
        );
        return response.data.data;
    },

    async confirmImport(
        importId: number,
        acceptedItemIndices: number[]
    ): Promise<ConfirmCalendarImportResponse> {
        const response = await apiClient.post<ApiResponse<ConfirmCalendarImportResponse>>(
            `/calendar/imports/${importId}/confirm`,
            { acceptedItemIndices }
        );
        return response.data.data;
    },

    // -------------------------------------------------------------
    // Google Calendar Read-Only Integration
    // -------------------------------------------------------------
    async authorizeGoogle(): Promise<GoogleAuthorizeResponse> {
        const response = await apiClient.get<ApiResponse<GoogleAuthorizeResponse>>(
            "/integrations/google/authorize"
        );
        return response.data.data;
    },

    async syncGoogle(request?: GoogleSyncRequest): Promise<GoogleSyncResponse> {
        const response = await apiClient.post<ApiResponse<GoogleSyncResponse>>(
            "/integrations/google/sync",
            request ?? {}
        );
        return response.data.data;
    },

    async disconnectGoogle(): Promise<void> {
        await apiClient.delete<ApiResponse<null>>("/integrations/google");
    },

    getStoredGoogleStatus(userId?: number | null): GoogleIntegrationStatus {
        if (!userId) {
            return {
                provider: "GOOGLE",
                connected: false,
                connectedAt: null,
                lastSyncedAt: null
            };
        }
        try {
            const raw = localStorage.getItem(`${GOOGLE_STATUS_STORAGE_PREFIX}${userId}`);
            if (!raw) {
                return {
                    provider: "GOOGLE",
                    connected: false,
                    connectedAt: null,
                    lastSyncedAt: null
                };
            }
            return JSON.parse(raw) as GoogleIntegrationStatus;
        } catch {
            return {
                provider: "GOOGLE",
                connected: false,
                connectedAt: null,
                lastSyncedAt: null
            };
        }
    },

    saveStoredGoogleStatus(userId: number | undefined | null, status: GoogleIntegrationStatus): void {
        if (!userId) return;
        try {
            localStorage.setItem(
                `${GOOGLE_STATUS_STORAGE_PREFIX}${userId}`,
                JSON.stringify(status)
            );
        } catch {
            // Ignore localStorage quota failures
        }
    }
};

export default scheduleService;
