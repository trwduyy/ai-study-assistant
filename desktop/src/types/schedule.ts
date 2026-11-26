export type EventType = "CLASS" | "TEST" | "MEETING" | "PERSONAL";
export type EventStatus = "UPCOMING" | "ONGOING" | "ENDED" | "CANCELLED";
export type EventSource = "MANUAL" | "ICS" | "CSV" | "GOOGLE";
export type CalendarViewMode = "DAY" | "WEEK" | "MONTH";

export interface CalendarEvent {
    id: number;
    ownerId: number;
    classId: number | null;
    className: string | null;
    importId?: number | null;
    type: EventType;
    title: string;
    description: string | null;
    startAt: string;
    endAt: string;
    timezone: string;
    status: EventStatus;
    meetingUrl: string | null;
    source: EventSource;
    externalId?: string | null;
    readOnly: boolean;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface CreateClassEventRequest {
    type: "CLASS" | "TEST" | "MEETING";
    title: string;
    description?: string | null;
    startAt: string;
    endAt: string;
    timezone: string;
    meetingUrl?: string | null;
}

export interface UpdateClassEventRequest {
    type: "CLASS" | "TEST" | "MEETING";
    title: string;
    description?: string | null;
    startAt: string;
    endAt: string;
    timezone: string;
    meetingUrl?: string | null;
    version: number;
}

export interface CreatePersonalEventRequest {
    title: string;
    description?: string | null;
    startAt: string;
    endAt: string;
    timezone: string;
    meetingUrl?: string | null;
}

export interface UpdatePersonalEventRequest {
    title: string;
    description?: string | null;
    startAt: string;
    endAt: string;
    timezone: string;
    meetingUrl?: string | null;
    version: number;
}

export interface ConflictingEventSummary {
    eventId: number;
    title: string;
    type: EventType;
    classId: number | null;
    className: string | null;
    startAt: string;
    endAt: string;
    source: EventSource;
}

export interface CalendarImportItem {
    itemIndex: number;
    externalId: string | null;
    title: string;
    description: string | null;
    location: string | null;
    startAt: string;
    endAt: string;
    timezone: string;
    hasConflict: boolean;
    conflictingEvents: ConflictingEventSummary[];
}

export interface CalendarImportPreview {
    importId: number;
    sourceType: "ICS" | "CSV";
    fileName: string;
    timezone: string;
    status: "PREVIEW" | "CONFIRMED" | "CANCELLED";
    totalParsed: number;
    conflictCount: number;
    items: CalendarImportItem[];
}

export interface ConfirmCalendarImportRequest {
    acceptedItemIndices: number[];
}

export interface ConfirmCalendarImportResponse {
    importId: number;
    status: string;
    importedCount: number;
    skippedCount: number;
    createdEventIds: number[];
}

export interface GoogleAuthorizeResponse {
    authorizeUrl: string;
    scopes: string[];
}

export interface GoogleIntegrationStatus {
    provider: "GOOGLE";
    connected: boolean;
    connectedAt: string | null;
    lastSyncedAt: string | null;
}

export interface GoogleSyncRequest {
    from?: string;
    to?: string;
}

export interface GoogleSyncResponse {
    provider: "GOOGLE";
    syncedCount: number;
    createdCount: number;
    updatedCount: number;
    cancelledCount: number;
    lastSyncedAt: string;
}

// =============================================================================
// Goals, Daily Tasks & AI Study Plan (FR-17)
// =============================================================================

export type GoalPriority = "LOW" | "MEDIUM" | "HIGH";
export type GoalStatus = "ACTIVE" | "COMPLETED" | "CANCELLED";
export type TaskStatus = "TODO" | "DONE" | "MISSED";
export type StudyPlanStatus = "PROPOSED" | "PARTIAL" | "CLOSED";
export type StudyPlanItemDecision = "PENDING" | "ACCEPTED" | "EDITED" | "REJECTED";

export interface GoalFreeTimeConfig {
    weekdays?: string[];
    hoursPerDay?: number;
    [key: string]: unknown;
}

export interface Goal {
    id: number;
    title: string;
    subject: string | null;
    dueDate: string | null;
    priority: GoalPriority;
    status: GoalStatus;
    freeTime: GoalFreeTimeConfig | null;
    createdAt: string;
    updatedAt: string;
}

export interface CreateGoalRequest {
    title: string;
    subject?: string | null;
    dueDate?: string | null;
    priority?: GoalPriority;
    freeTime?: GoalFreeTimeConfig | null;
}

export interface UpdateGoalRequest {
    title: string;
    subject?: string | null;
    dueDate?: string | null;
    priority?: GoalPriority;
    status?: GoalStatus;
    freeTime?: GoalFreeTimeConfig | null;
}

export interface Task {
    id: number;
    goalId: number | null;
    goalTitle: string | null;
    title: string;
    taskDate: string;
    priority: GoalPriority;
    status: TaskStatus;
    remindAt: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface CreateTaskRequest {
    goalId?: number | null;
    title: string;
    taskDate: string;
    priority?: GoalPriority;
    remindAt?: string | null;
}

export interface UpdateTaskRequest {
    goalId?: number | null;
    title: string;
    taskDate: string;
    priority?: GoalPriority;
    status?: TaskStatus;
    remindAt?: string | null;
}

export interface GenerateStudyPlanRequest {
    goalId: number;
    fromDate?: string;
    toDate?: string;
}

export interface StudyPlanItem {
    id: number;
    planId: number;
    planStatus: StudyPlanStatus;
    title: string;
    plannedStart: string;
    plannedEnd: string;
    decision: StudyPlanItemDecision;
    taskId: number | null;
    hasConflict: boolean;
    conflictingEvents: ConflictingEventSummary[];
}

export interface StudyPlan {
    id: number;
    jobId: string | null;
    status: StudyPlanStatus;
    rationale: string | null;
    model: string | null;
    promptVersion: string | null;
    items: StudyPlanItem[];
    createdAt: string;
    updatedAt: string;
}

export interface DecideStudyPlanItemRequest {
    decision: Exclude<StudyPlanItemDecision, "PENDING">;
    title?: string;
    plannedStart?: string;
    plannedEnd?: string;
}
