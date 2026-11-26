import { UserRole, UserStatus } from "./auth";
import { ClassroomStatus } from "./classroom";

export interface AdminUserItem {
    id: number;
    email: string;
    fullName: string;
    role: UserRole;
    status: UserStatus;
    avatarUrl: string | null;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface AdminClassItem {
    id: number;
    code: string;
    name: string;
    description: string | null;
    subject: string;
    status: ClassroomStatus;
    ownerId: number;
    ownerName: string;
    ownerEmail: string;
    memberCount: number;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export type ReportType = "AI_INACCURATE" | "CLASS_ABUSE" | "CHAT_ABUSE" | "OTHER" | (string & {});
export type ReportStatus = "OPEN" | "RESOLVED";

export interface ReportItem {
    id: number;
    reporterId: number;
    reporterName: string;
    reporterEmail: string;
    type: ReportType;
    targetType: string;
    targetId: number;
    detail: string;
    status: ReportStatus;
    resolutionNote: string | null;
    handledById: number | null;
    handledByName: string | null;
    handledAt: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface CreateReportRequest {
    type: ReportType;
    targetType: string;
    targetId: number;
    detail: string;
}

export interface ResolveReportRequest {
    status: ReportStatus;
    resolutionNote?: string;
}

export interface SystemSettingItem {
    settingKey: string;
    settingValue: string;
    description: string | null;
    updatedById: number | null;
    updatedByName: string | null;
    updatedAt: string | null;
}

export interface SystemSettingUpdateInput {
    settingKey: string;
    settingValue: string;
}

export interface AuditLogItem {
    id: number;
    userId: number | null;
    userEmail: string | null;
    action: string;
    objectType: string | null;
    objectId: number | null;
    metadataJson: Record<string, unknown> | null;
    createdAt: string;
}
