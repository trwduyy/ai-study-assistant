import apiClient from "./apiClient";
import {
    AiJobAcceptedResponse,
    ApiResponse,
    CreateGoalRequest,
    CreateTaskRequest,
    DecideStudyPlanItemRequest,
    GenerateStudyPlanRequest,
    Goal,
    GoalStatus,
    PageMeta,
    StudyPlan,
    StudyPlanItem,
    Task,
    TaskStatus,
    UpdateGoalRequest,
    UpdateTaskRequest
} from "../types";

export const planService = {
    // -------------------------------------------------------------
    // Goals
    // -------------------------------------------------------------
    async listGoals(
        status?: GoalStatus,
        page = 0,
        size = 50
    ): Promise<{ items: Goal[]; meta?: PageMeta | null }> {
        const params: Record<string, string | number> = { page, size };
        if (status) params.status = status;
        const response = await apiClient.get<ApiResponse<Goal[]>>("/goals", { params });
        return {
            items: response.data.data ?? [],
            meta: response.data.meta
        };
    },

    async createGoal(data: CreateGoalRequest): Promise<Goal> {
        const response = await apiClient.post<ApiResponse<Goal>>("/goals", data);
        return response.data.data;
    },

    async updateGoal(id: number, data: UpdateGoalRequest): Promise<Goal> {
        const response = await apiClient.put<ApiResponse<Goal>>(`/goals/${id}`, data);
        return response.data.data;
    },

    async deleteGoal(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/goals/${id}`);
    },

    // -------------------------------------------------------------
    // Daily Tasks
    // -------------------------------------------------------------
    async listTasks(params?: {
        from?: string;
        to?: string;
        date?: string;
        status?: TaskStatus;
        goalId?: number;
        page?: number;
        size?: number;
    }): Promise<{ items: Task[]; meta?: PageMeta | null }> {
        const query: Record<string, string | number> = {
            page: params?.page ?? 0,
            size: params?.size ?? 100
        };
        if (params?.from) query.from = params.from;
        if (params?.to) query.to = params.to;
        if (params?.date) query.date = params.date;
        if (params?.status) query.status = params.status;
        if (params?.goalId) query.goalId = params.goalId;

        const response = await apiClient.get<ApiResponse<Task[]>>("/tasks", { params: query });
        return {
            items: response.data.data ?? [],
            meta: response.data.meta
        };
    },

    async createTask(data: CreateTaskRequest): Promise<Task> {
        const response = await apiClient.post<ApiResponse<Task>>("/tasks", data);
        return response.data.data;
    },

    async updateTask(id: number, data: UpdateTaskRequest): Promise<Task> {
        const response = await apiClient.put<ApiResponse<Task>>(`/tasks/${id}`, data);
        return response.data.data;
    },

    async deleteTask(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/tasks/${id}`);
    },

    async toggleTaskComplete(id: number, completed?: boolean): Promise<Task> {
        const response = await apiClient.put<ApiResponse<Task>>(
            `/tasks/${id}/complete`,
            typeof completed === "boolean" ? { completed } : {}
        );
        return response.data.data;
    },

    // -------------------------------------------------------------
    // AI Study Plans (BR-11)
    // -------------------------------------------------------------
    async requestAiStudyPlan(data: GenerateStudyPlanRequest): Promise<AiJobAcceptedResponse> {
        const response = await apiClient.post<ApiResponse<AiJobAcceptedResponse>>(
            "/ai/study-plans",
            data
        );
        return response.data.data;
    },

    async getStudyPlan(planId: number): Promise<StudyPlan> {
        const response = await apiClient.get<ApiResponse<StudyPlan>>(`/study-plans/${planId}`);
        return response.data.data;
    },

    async decidePlanItem(
        planId: number,
        itemId: number,
        data: DecideStudyPlanItemRequest
    ): Promise<StudyPlanItem> {
        const response = await apiClient.put<ApiResponse<StudyPlanItem>>(
            `/study-plans/${planId}/items/${itemId}`,
            data
        );
        return response.data.data;
    }
};

export default planService;
