import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
    AlertTriangle,
    Calendar,
    Check,
    CheckCircle2,
    ClipboardCheck,
    Clock,
    Edit3,
    Loader2,
    Pencil,
    Plus,
    Sparkles,
    Target,
    Trash2,
    X,
    XCircle
} from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import { useAiJob } from "../hooks/useAiJob";
import planService from "../services/planService";
import {
    Goal,
    GoalPriority,
    GoalStatus,
    StudyPlan,
    StudyPlanItem,
    Task,
    TaskStatus
} from "../types";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../components/ui/dialog";
import { Input } from "../components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { EmptyState } from "../components/common/EmptyState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { LoadingState } from "../components/common/LoadingState";

const WEEKDAYS_OPTIONS: Array<{ value: string; label: string }> = [
    { value: "MON", label: "T2" },
    { value: "TUE", label: "T3" },
    { value: "WED", label: "T4" },
    { value: "THU", label: "T5" },
    { value: "FRI", label: "T6" },
    { value: "SAT", label: "T7" },
    { value: "SUN", label: "CN" }
];

const LAST_PLAN_STORAGE_PREFIX = "ai_study_last_plan_id_";

function formatLocalDateInput(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}

function toDateTimeLocalValue(iso?: string | null): string {
    if (!iso) return "";
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "";
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    const hours = String(d.getHours()).padStart(2, "0");
    const mins = String(d.getMinutes()).padStart(2, "0");
    return `${year}-${month}-${day}T${hours}:${mins}`;
}

function getPriorityBadge(priority: GoalPriority): React.ReactNode {
    switch (priority) {
        case "HIGH":
            return <Badge variant="danger">Ưu tiên cao</Badge>;
        case "MEDIUM":
            return <Badge variant="warning">Trung bình</Badge>;
        case "LOW":
        default:
            return <Badge variant="outline">Thấp</Badge>;
    }
}

function getTaskStatusBadge(status: TaskStatus): React.ReactNode {
    switch (status) {
        case "DONE":
            return (
                <Badge variant="success" icon={<CheckCircle2 className="h-3 w-3" />}>
                    Hoàn thành
                </Badge>
            );
        case "MISSED":
            return (
                <Badge variant="danger" icon={<XCircle className="h-3 w-3" />}>
                    Bỏ lỡ
                </Badge>
            );
        case "TODO":
        default:
            return (
                <Badge variant="info" icon={<Clock className="h-3 w-3" />}>
                    Cần làm
                </Badge>
            );
    }
}

function getGoalStatusBadge(status: GoalStatus): React.ReactNode {
    switch (status) {
        case "COMPLETED":
            return <Badge variant="success">Đã đạt</Badge>;
        case "CANCELLED":
            return <Badge variant="outline">Đã hủy</Badge>;
        case "ACTIVE":
        default:
            return <Badge variant="default">Đang thực hiện</Badge>;
    }
}

export const StudyPlanPage: React.FC = () => {
    const { user } = useAuth();
    const [activeTab, setActiveTab] = useState<string>("tasks");

    // -------------------------------------------------------------------------
    // Tasks State
    // -------------------------------------------------------------------------
    const [tasks, setTasks] = useState<Task[]>([]);
    const [isTasksLoading, setIsTasksLoading] = useState<boolean>(true);
    const [tasksError, setTasksError] = useState<string | null>(null);
    const [taskRangeFilter, setTaskRangeFilter] = useState<"TODAY" | "WEEK" | "ALL">("WEEK");
    const [taskStatusFilter, setTaskStatusFilter] = useState<"ALL" | TaskStatus>("ALL");
    const [togglingTaskId, setTogglingTaskId] = useState<number | null>(null);

    // Task Modal
    const [isTaskModalOpen, setIsTaskModalOpen] = useState<boolean>(false);
    const [editingTask, setEditingTask] = useState<Task | null>(null);
    const [taskTitle, setTaskTitle] = useState<string>("");
    const [taskGoalId, setTaskGoalId] = useState<string>("");
    const [taskDate, setTaskDate] = useState<string>(() => formatLocalDateInput(new Date()));
    const [taskPriority, setTaskPriority] = useState<GoalPriority>("MEDIUM");
    const [taskRemindAt, setTaskRemindAt] = useState<string>("");
    const [taskModalStatus, setTaskModalStatus] = useState<TaskStatus>("TODO");
    const [isSavingTask, setIsSavingTask] = useState<boolean>(false);

    // -------------------------------------------------------------------------
    // Goals State
    // -------------------------------------------------------------------------
    const [goals, setGoals] = useState<Goal[]>([]);
    const [isGoalsLoading, setIsGoalsLoading] = useState<boolean>(true);
    const [goalsError, setGoalsError] = useState<string | null>(null);

    // Goal Modal
    const [isGoalModalOpen, setIsGoalModalOpen] = useState<boolean>(false);
    const [editingGoal, setEditingGoal] = useState<Goal | null>(null);
    const [goalTitle, setGoalTitle] = useState<string>("");
    const [goalSubject, setGoalSubject] = useState<string>("");
    const [goalDueDate, setGoalDueDate] = useState<string>("");
    const [goalPriority, setGoalPriority] = useState<GoalPriority>("HIGH");
    const [goalStatus, setGoalStatus] = useState<GoalStatus>("ACTIVE");
    const [goalWeekdays, setGoalWeekdays] = useState<string[]>(["MON", "WED", "FRI"]);
    const [goalHoursPerDay, setGoalHoursPerDay] = useState<number>(2);
    const [isSavingGoal, setIsSavingGoal] = useState<boolean>(false);

    // -------------------------------------------------------------------------
    // AI Study Plan State (BR-11)
    // -------------------------------------------------------------------------
    const [isAiModalOpen, setIsAiModalOpen] = useState<boolean>(false);
    const [aiGoalId, setAiGoalId] = useState<number | "">("");
    const [aiFromDate, setAiFromDate] = useState<string>(() => formatLocalDateInput(new Date()));
    const [aiToDate, setAiToDate] = useState<string>(() => {
        const d = new Date();
        d.setDate(d.getDate() + 6);
        return formatLocalDateInput(d);
    });
    const [isRequestingAiPlan, setIsRequestingAiPlan] = useState<boolean>(false);

    const [currentPlan, setCurrentPlan] = useState<StudyPlan | null>(null);
    const [isLoadingPlan, setIsLoadingPlan] = useState<boolean>(false);
    const [planError, setPlanError] = useState<string | null>(null);
    const [decidingItemId, setDecidingItemId] = useState<number | null>(null);

    // Edit Study Plan Item Modal
    const [editingPlanItem, setEditingPlanItem] = useState<StudyPlanItem | null>(null);
    const [editItemTitle, setEditItemTitle] = useState<string>("");
    const [editItemStart, setEditItemStart] = useState<string>("");
    const [editItemEnd, setEditItemEnd] = useState<string>("");

    // Fetch Goals
    const fetchGoals = useCallback(async (): Promise<void> => {
        try {
            setIsGoalsLoading(true);
            const res = await planService.listGoals();
            setGoals(res.items);
            setGoalsError(null);
        } catch (err: unknown) {
            setGoalsError(err instanceof Error ? err.message : "Không thể tải danh sách mục tiêu.");
        } finally {
            setIsGoalsLoading(false);
        }
    }, []);

    // Fetch Tasks
    const fetchTasks = useCallback(async (): Promise<void> => {
        try {
            setIsTasksLoading(true);
            const today = new Date();
            let from: string | undefined;
            let to: string | undefined;

            if (taskRangeFilter === "TODAY") {
                from = formatLocalDateInput(today);
                to = formatLocalDateInput(today);
            } else if (taskRangeFilter === "WEEK") {
                const start = new Date(today);
                const end = new Date(today);
                end.setDate(end.getDate() + 6);
                from = formatLocalDateInput(start);
                to = formatLocalDateInput(end);
            }

            const res = await planService.listTasks({
                from,
                to,
                status: taskStatusFilter === "ALL" ? undefined : taskStatusFilter
            });
            setTasks(res.items);
            setTasksError(null);
        } catch (err: unknown) {
            setTasksError(err instanceof Error ? err.message : "Không thể tải danh sách nhiệm vụ.");
        } finally {
            setIsTasksLoading(false);
        }
    }, [taskRangeFilter, taskStatusFilter]);

    useEffect(() => {
        void fetchGoals();
    }, [fetchGoals]);

    useEffect(() => {
        void fetchTasks();
    }, [fetchTasks]);

    // Load study plan by id
    const loadStudyPlanById = useCallback(
        async (planId: number): Promise<void> => {
            try {
                setIsLoadingPlan(true);
                const plan = await planService.getStudyPlan(planId);
                setCurrentPlan(plan);
                setPlanError(null);
                if (user?.id) {
                    localStorage.setItem(`${LAST_PLAN_STORAGE_PREFIX}${user.id}`, String(plan.id));
                }
            } catch (err: unknown) {
                setPlanError(err instanceof Error ? err.message : "Không thể tải lộ trình đề xuất.");
            } finally {
                setIsLoadingPlan(false);
            }
        },
        [user?.id]
    );

    // Restore last viewed plan on mount
    useEffect(() => {
        if (!user?.id) return;
        const storedId = localStorage.getItem(`${LAST_PLAN_STORAGE_PREFIX}${user.id}`);
        if (storedId && !Number.isNaN(Number(storedId))) {
            void loadStudyPlanById(Number(storedId));
        }
    }, [loadStudyPlanById, user?.id]);

    // AI Job tracking hook
    const aiJob = useAiJob(null, {
        onCompleted: (job) => {
            if (job.resultId) {
                toast.success("AI đã tạo xong lộ trình học tập đề xuất!");
                setActiveTab("ai-plan");
                void loadStudyPlanById(job.resultId);
            } else {
                toast.error("Tác vụ hoàn tất nhưng không tìm thấy mã lộ trình.");
            }
        },
        onError: (errMsg) => {
            toast.error(errMsg);
        }
    });

    // -------------------------------------------------------------------------
    // Task Handlers
    // -------------------------------------------------------------------------
    const openCreateTaskModal = () => {
        setEditingTask(null);
        setTaskTitle("");
        setTaskGoalId("");
        setTaskDate(formatLocalDateInput(new Date()));
        setTaskPriority("MEDIUM");
        setTaskRemindAt("");
        setTaskModalStatus("TODO");
        setIsTaskModalOpen(true);
    };

    const openEditTaskModal = (task: Task) => {
        setEditingTask(task);
        setTaskTitle(task.title);
        setTaskGoalId(task.goalId ? String(task.goalId) : "");
        setTaskDate(task.taskDate);
        setTaskPriority(task.priority);
        setTaskRemindAt(toDateTimeLocalValue(task.remindAt));
        setTaskModalStatus(task.status);
        setIsTaskModalOpen(true);
    };

    const handleSaveTask = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!taskTitle.trim() || !taskDate) {
            toast.error("Vui lòng nhập tiêu đề và ngày thực hiện nhiệm vụ.");
            return;
        }

        try {
            setIsSavingTask(true);
            const remindIso = taskRemindAt ? new Date(taskRemindAt).toISOString() : null;
            const parsedGoalId = taskGoalId ? Number(taskGoalId) : null;

            if (editingTask) {
                await planService.updateTask(editingTask.id, {
                    goalId: parsedGoalId,
                    title: taskTitle.trim(),
                    taskDate,
                    priority: taskPriority,
                    status: taskModalStatus,
                    remindAt: remindIso
                });
                toast.success("Đã cập nhật nhiệm vụ!");
            } else {
                await planService.createTask({
                    goalId: parsedGoalId,
                    title: taskTitle.trim(),
                    taskDate,
                    priority: taskPriority,
                    remindAt: remindIso
                });
                toast.success("Đã thêm nhiệm vụ mới!");
            }
            setIsTaskModalOpen(false);
            void fetchTasks();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lưu nhiệm vụ thất bại.");
        } finally {
            setIsSavingTask(false);
        }
    };

    const handleToggleTask = async (task: Task) => {
        try {
            setTogglingTaskId(task.id);
            const nextCompleted = task.status !== "DONE";
            const updated = await planService.toggleTaskComplete(task.id, nextCompleted);
            setTasks((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
            toast.success(
                updated.status === "DONE"
                    ? "Đã hoàn thành nhiệm vụ!"
                    : "Đã chuyển lại trạng thái Cần làm."
            );
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể cập nhật trạng thái nhiệm vụ.");
        } finally {
            setTogglingTaskId(null);
        }
    };

    const handleDeleteTask = async (task: Task) => {
        if (!window.confirm(`Bạn có chắc muốn xóa nhiệm vụ "${task.title}" không?`)) return;
        try {
            await planService.deleteTask(task.id);
            toast.success("Đã xóa nhiệm vụ.");
            void fetchTasks();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xóa nhiệm vụ thất bại.");
        }
    };

    // -------------------------------------------------------------------------
    // Goal Handlers
    // -------------------------------------------------------------------------
    const openCreateGoalModal = () => {
        setEditingGoal(null);
        setGoalTitle("");
        setGoalSubject("");
        setGoalDueDate("");
        setGoalPriority("HIGH");
        setGoalStatus("ACTIVE");
        setGoalWeekdays(["MON", "WED", "FRI"]);
        setGoalHoursPerDay(2);
        setIsGoalModalOpen(true);
    };

    const openEditGoalModal = (goal: Goal) => {
        setEditingGoal(goal);
        setGoalTitle(goal.title);
        setGoalSubject(goal.subject || "");
        setGoalDueDate(goal.dueDate || "");
        setGoalPriority(goal.priority);
        setGoalStatus(goal.status);
        setGoalWeekdays(goal.freeTime?.weekdays ?? ["MON", "WED", "FRI"]);
        setGoalHoursPerDay(Number(goal.freeTime?.hoursPerDay ?? 2));
        setIsGoalModalOpen(true);
    };

    const toggleGoalWeekday = (dayCode: string) => {
        setGoalWeekdays((prev) =>
            prev.includes(dayCode) ? prev.filter((d) => d !== dayCode) : [...prev, dayCode]
        );
    };

    const handleSaveGoal = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!goalTitle.trim()) {
            toast.error("Vui lòng nhập tên mục tiêu học tập.");
            return;
        }

        try {
            setIsSavingGoal(true);
            const freeTime = {
                weekdays: goalWeekdays,
                hoursPerDay: Number(goalHoursPerDay) || 2
            };

            if (editingGoal) {
                await planService.updateGoal(editingGoal.id, {
                    title: goalTitle.trim(),
                    subject: goalSubject.trim() || null,
                    dueDate: goalDueDate || null,
                    priority: goalPriority,
                    status: goalStatus,
                    freeTime
                });
                toast.success("Đã cập nhật mục tiêu!");
            } else {
                await planService.createGoal({
                    title: goalTitle.trim(),
                    subject: goalSubject.trim() || null,
                    dueDate: goalDueDate || null,
                    priority: goalPriority,
                    freeTime
                });
                toast.success("Đã tạo mục tiêu học tập mới!");
            }
            setIsGoalModalOpen(false);
            void fetchGoals();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lưu mục tiêu thất bại.");
        } finally {
            setIsSavingGoal(false);
        }
    };

    const handleDeleteGoal = async (goal: Goal) => {
        if (!window.confirm(`Bạn có chắc muốn xóa mục tiêu "${goal.title}" không?`)) return;
        try {
            await planService.deleteGoal(goal.id);
            toast.success("Đã xóa mục tiêu.");
            void fetchGoals();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xóa mục tiêu thất bại.");
        }
    };

    // -------------------------------------------------------------------------
    // AI Study Plan Handlers
    // -------------------------------------------------------------------------
    const openRequestAiModal = (preselectedGoalId?: number) => {
        const activeGoals = goals.filter((g) => g.status === "ACTIVE");
        setAiGoalId(preselectedGoalId ?? activeGoals[0]?.id ?? goals[0]?.id ?? "");
        setIsAiModalOpen(true);
    };

    const handleRequestAiPlan = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!aiGoalId) {
            toast.error("Vui lòng chọn mục tiêu học tập trước khi đề xuất lộ trình.");
            return;
        }
        if (aiFromDate && aiToDate && aiToDate < aiFromDate) {
            toast.error("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
            return;
        }

        try {
            setIsRequestingAiPlan(true);
            const accepted = await planService.requestAiStudyPlan({
                goalId: Number(aiGoalId),
                fromDate: aiFromDate || undefined,
                toDate: aiToDate || undefined
            });
            setIsAiModalOpen(false);
            setActiveTab("ai-plan");
            aiJob.startJob(accepted.jobId);
            toast.info("AI đang phân tích mục tiêu, lịch bận và chủ đề yếu để đề xuất lộ trình...");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể gửi yêu cầu tạo lộ trình AI.");
        } finally {
            setIsRequestingAiPlan(false);
        }
    };

    const handleDecidePlanItem = async (
        item: StudyPlanItem,
        decision: "ACCEPTED" | "REJECTED"
    ) => {
        if (!currentPlan) return;
        try {
            setDecidingItemId(item.id);
            const updatedItem = await planService.decidePlanItem(currentPlan.id, item.id, {
                decision
            });
            setCurrentPlan((prev) =>
                prev
                    ? {
                          ...prev,
                          status: updatedItem.planStatus,
                          items: prev.items.map((it) => (it.id === updatedItem.id ? updatedItem : it))
                      }
                    : null
            );

            if (decision === "ACCEPTED") {
                toast.success(
                    `Đã chấp nhận "${updatedItem.title}" và tự động thêm vào Nhiệm vụ theo ngày!`
                );
                void fetchTasks();
            } else {
                toast.info(`Đã bỏ qua buổi học "${updatedItem.title}".`);
            }
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xử lý mục lộ trình thất bại.");
        } finally {
            setDecidingItemId(null);
        }
    };

    const openEditPlanItemModal = (item: StudyPlanItem) => {
        setEditingPlanItem(item);
        setEditItemTitle(item.title);
        setEditItemStart(toDateTimeLocalValue(item.plannedStart));
        setEditItemEnd(toDateTimeLocalValue(item.plannedEnd));
    };

    const handleConfirmEditedItem = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!currentPlan || !editingPlanItem) return;
        if (!editItemTitle.trim() || !editItemStart || !editItemEnd) {
            toast.error("Vui lòng nhập đầy đủ tiêu đề và thời gian buổi học.");
            return;
        }
        const start = new Date(editItemStart);
        const end = new Date(editItemEnd);
        if (end <= start) {
            toast.error("Giờ kết thúc phải sau giờ bắt đầu.");
            return;
        }

        try {
            setDecidingItemId(editingPlanItem.id);
            const updatedItem = await planService.decidePlanItem(
                currentPlan.id,
                editingPlanItem.id,
                {
                    decision: "EDITED",
                    title: editItemTitle.trim(),
                    plannedStart: start.toISOString(),
                    plannedEnd: end.toISOString()
                }
            );
            setCurrentPlan((prev) =>
                prev
                    ? {
                          ...prev,
                          status: updatedItem.planStatus,
                          items: prev.items.map((it) => (it.id === updatedItem.id ? updatedItem : it))
                      }
                    : null
            );
            setEditingPlanItem(null);
            toast.success(
                `Đã chỉnh sửa, chấp nhận "${updatedItem.title}" và thêm vào Nhiệm vụ theo ngày!`
            );
            void fetchTasks();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể lưu chỉnh sửa mục lộ trình.");
        } finally {
            setDecidingItemId(null);
        }
    };

    const pendingPlanItemsCount = useMemo(
        () => currentPlan?.items.filter((i) => i.decision === "PENDING").length ?? 0,
        [currentPlan]
    );

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2.5">
                        <ClipboardCheck className="h-6 w-6 text-primary" />
                        <span>Kế hoạch học tập & Mục tiêu AI</span>
                    </h1>
                    <p className="text-sm text-muted-foreground mt-1">
                        Quản lý nhiệm vụ hàng ngày, đặt mục tiêu môn học và duyệt lộ trình do AI đề xuất.
                    </p>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={openCreateGoalModal}
                        className="gap-1.5 text-xs"
                    >
                        <Target className="h-3.5 w-3.5 text-teal-700" />
                        <span>Thêm mục tiêu</span>
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={openCreateTaskModal}
                        className="gap-1.5 text-xs"
                    >
                        <Plus className="h-3.5 w-3.5 text-primary" />
                        <span>Thêm nhiệm vụ</span>
                    </Button>
                    <Button
                        size="sm"
                        onClick={() => openRequestAiModal()}
                        disabled={aiJob.isLoading}
                        className="gap-1.5 text-xs"
                    >
                        <Sparkles className="h-3.5 w-3.5" />
                        <span>Nhờ AI đề xuất lộ trình</span>
                    </Button>
                </div>
            </div>

            {/* 3 Tabs */}
            <Tabs value={activeTab} onValueChange={setActiveTab}>
                <TabsList>
                    <TabsTrigger value="tasks" className="gap-2">
                        <ClipboardCheck className="h-4 w-4" />
                        <span>Nhiệm vụ theo ngày</span>
                        <Badge variant="outline" className="text-[10px] px-1.5 py-0">
                            {tasks.length}
                        </Badge>
                    </TabsTrigger>
                    <TabsTrigger value="goals" className="gap-2">
                        <Target className="h-4 w-4" />
                        <span>Mục tiêu học tập</span>
                        <Badge variant="outline" className="text-[10px] px-1.5 py-0">
                            {goals.length}
                        </Badge>
                    </TabsTrigger>
                    <TabsTrigger value="ai-plan" className="gap-2">
                        <Sparkles className="h-4 w-4" />
                        <span>Lộ trình AI đề xuất</span>
                        {pendingPlanItemsCount > 0 && (
                            <Badge variant="warning" className="text-[10px] px-1.5 py-0">
                                {pendingPlanItemsCount} chờ duyệt
                            </Badge>
                        )}
                    </TabsTrigger>
                </TabsList>

                {/* TAB 1: DAILY TASKS */}
                <TabsContent value="tasks" className="space-y-4">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                        <div className="flex flex-wrap items-center gap-2">
                            <div className="inline-flex rounded-lg border border-border bg-surface p-1 text-xs font-medium">
                                {(
                                    [
                                        { key: "TODAY", label: "Hôm nay" },
                                        { key: "WEEK", label: "7 ngày tới" },
                                        { key: "ALL", label: "Tất cả" }
                                    ] as const
                                ).map((range) => (
                                    <button
                                        key={range.key}
                                        type="button"
                                        onClick={() => setTaskRangeFilter(range.key)}
                                        className={`px-3 py-1 rounded-md transition-colors ${
                                            taskRangeFilter === range.key
                                                ? "bg-primary text-white shadow-sm"
                                                : "text-slate-600 hover:text-slate-900"
                                        }`}
                                    >
                                        {range.label}
                                    </button>
                                ))}
                            </div>

                            <div className="inline-flex rounded-lg border border-border bg-surface p-1 text-xs font-medium">
                                {(
                                    [
                                        { key: "ALL", label: "Mọi trạng thái" },
                                        { key: "TODO", label: "Cần làm" },
                                        { key: "DONE", label: "Hoàn thành" },
                                        { key: "MISSED", label: "Bỏ lỡ" }
                                    ] as const
                                ).map((st) => (
                                    <button
                                        key={st.key}
                                        type="button"
                                        onClick={() => setTaskStatusFilter(st.key)}
                                        className={`px-2.5 py-1 rounded-md transition-colors ${
                                            taskStatusFilter === st.key
                                                ? "bg-slate-900 text-white shadow-sm"
                                                : "text-slate-600 hover:text-slate-900"
                                        }`}
                                    >
                                        {st.label}
                                    </button>
                                ))}
                            </div>
                        </div>

                        <Button size="sm" onClick={openCreateTaskModal} className="gap-1.5 text-xs">
                            <Plus className="h-3.5 w-3.5" />
                            <span>Thêm nhiệm vụ</span>
                        </Button>
                    </div>

                    {isTasksLoading ? (
                        <LoadingState label="Đang tải danh sách nhiệm vụ..." />
                    ) : tasksError ? (
                        <ErrorBanner
                            title="Lỗi tải nhiệm vụ"
                            message={tasksError}
                            onRetry={() => void fetchTasks()}
                        />
                    ) : tasks.length === 0 ? (
                        <EmptyState
                            icon={<ClipboardCheck className="h-12 w-12 text-slate-300" />}
                            title="Chưa có nhiệm vụ nào trong bộ lọc này"
                            description="Thêm nhiệm vụ học tập thủ công hoặc chấp nhận các buổi học từ Lộ trình AI đề xuất."
                            action={
                                <Button onClick={openCreateTaskModal} className="gap-1.5 text-xs">
                                    <Plus className="h-4 w-4" />
                                    <span>Tạo nhiệm vụ đầu tiên</span>
                                </Button>
                            }
                        />
                    ) : (
                        <Card className="border-border shadow-sm overflow-hidden">
                            <div className="divide-y divide-border">
                                {tasks.map((task) => {
                                    const isDone = task.status === "DONE";
                                    return (
                                        <div
                                            key={task.id}
                                            className={`p-4 flex items-center justify-between gap-4 transition-colors hover:bg-slate-50/70 ${
                                                isDone ? "bg-slate-50/50" : "bg-surface"
                                            }`}
                                        >
                                            <div className="flex items-start gap-3 min-w-0 flex-1">
                                                <input
                                                    type="checkbox"
                                                    checked={isDone}
                                                    disabled={togglingTaskId === task.id}
                                                    onChange={() => void handleToggleTask(task)}
                                                    aria-label={`Đánh dấu hoàn thành ${task.title}`}
                                                    className="mt-1 h-4 w-4 rounded border-slate-300 text-primary focus:ring-primary cursor-pointer"
                                                />
                                                <div className="min-w-0 space-y-1">
                                                    <div className="flex flex-wrap items-center gap-2">
                                                        <p
                                                            className={`text-sm font-semibold ${
                                                                isDone
                                                                    ? "line-through text-slate-400"
                                                                    : "text-slate-900"
                                                            }`}
                                                        >
                                                            {task.title}
                                                        </p>
                                                        {getTaskStatusBadge(task.status)}
                                                        {getPriorityBadge(task.priority)}
                                                    </div>

                                                    <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                                                        <span className="inline-flex items-center gap-1">
                                                            <Calendar className="h-3.5 w-3.5" />
                                                            {new Date(`${task.taskDate}T00:00:00`).toLocaleDateString("vi-VN")}
                                                        </span>
                                                        {task.goalTitle && (
                                                            <span className="inline-flex items-center gap-1 text-primary font-medium">
                                                                <Target className="h-3.5 w-3.5" />
                                                                {task.goalTitle}
                                                            </span>
                                                        )}
                                                        {task.remindAt && (
                                                            <span className="inline-flex items-center gap-1">
                                                                <Clock className="h-3.5 w-3.5" />
                                                                Nhắc lúc:{" "}
                                                                {new Date(task.remindAt).toLocaleString("vi-VN", {
                                                                    hour: "2-digit",
                                                                    minute: "2-digit"
                                                                })}
                                                            </span>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>

                                            <div className="flex items-center gap-1 shrink-0">
                                                <Button
                                                    size="icon"
                                                    variant="ghost"
                                                    onClick={() => openEditTaskModal(task)}
                                                    className="h-8 w-8 text-slate-500 hover:text-slate-900"
                                                    title="Chỉnh sửa nhiệm vụ"
                                                    aria-label="Chỉnh sửa nhiệm vụ"
                                                >
                                                    <Pencil className="h-3.5 w-3.5" />
                                                </Button>
                                                <Button
                                                    size="icon"
                                                    variant="ghost"
                                                    onClick={() => void handleDeleteTask(task)}
                                                    className="h-8 w-8 text-slate-400 hover:text-rose-600"
                                                    title="Xóa nhiệm vụ"
                                                    aria-label="Xóa nhiệm vụ"
                                                >
                                                    <Trash2 className="h-3.5 w-3.5" />
                                                </Button>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </Card>
                    )}
                </TabsContent>

                {/* TAB 2: GOALS */}
                <TabsContent value="goals" className="space-y-4">
                    <div className="flex justify-end">
                        <Button size="sm" onClick={openCreateGoalModal} className="gap-1.5 text-xs">
                            <Plus className="h-3.5 w-3.5" />
                            <span>Tạo mục tiêu mới</span>
                        </Button>
                    </div>

                    {isGoalsLoading ? (
                        <LoadingState label="Đang tải mục tiêu học tập..." />
                    ) : goalsError ? (
                        <ErrorBanner
                            title="Lỗi tải mục tiêu"
                            message={goalsError}
                            onRetry={() => void fetchGoals()}
                        />
                    ) : goals.length === 0 ? (
                        <EmptyState
                            icon={<Target className="h-12 w-12 text-slate-300" />}
                            title="Bạn chưa thiết lập mục tiêu học tập nào"
                            description="Hãy tạo mục tiêu môn học và thời gian rảnh mỗi ngày để AI đề xuất lộ trình học cá nhân hóa."
                            action={
                                <Button onClick={openCreateGoalModal} className="gap-1.5 text-xs">
                                    <Plus className="h-4 w-4" />
                                    <span>Tạo mục tiêu đầu tiên</span>
                                </Button>
                            }
                        />
                    ) : (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {goals.map((goal) => (
                                <Card
                                    key={goal.id}
                                    className="border-border shadow-sm flex flex-col justify-between"
                                >
                                    <CardContent className="p-5 space-y-4">
                                        <div className="flex items-start justify-between gap-3">
                                            <div className="space-y-1">
                                                <div className="flex flex-wrap items-center gap-2">
                                                    {getGoalStatusBadge(goal.status)}
                                                    {getPriorityBadge(goal.priority)}
                                                </div>
                                                <h3 className="font-bold text-base text-slate-900 pt-1">
                                                    {goal.title}
                                                </h3>
                                                {goal.subject && (
                                                    <p className="text-xs font-medium text-teal-700">
                                                        Môn học: {goal.subject}
                                                    </p>
                                                )}
                                            </div>

                                            <div className="flex items-center gap-1 shrink-0">
                                                <Button
                                                    size="icon"
                                                    variant="ghost"
                                                    onClick={() => openEditGoalModal(goal)}
                                                    className="h-8 w-8 text-slate-500"
                                                    title="Sửa mục tiêu"
                                                >
                                                    <Pencil className="h-3.5 w-3.5" />
                                                </Button>
                                                <Button
                                                    size="icon"
                                                    variant="ghost"
                                                    onClick={() => void handleDeleteGoal(goal)}
                                                    className="h-8 w-8 text-slate-400 hover:text-rose-600"
                                                    title="Xóa mục tiêu"
                                                >
                                                    <Trash2 className="h-3.5 w-3.5" />
                                                </Button>
                                            </div>
                                        </div>

                                        <div className="p-3 rounded-lg bg-slate-50 border border-border space-y-1 text-xs text-slate-600">
                                            {goal.dueDate && (
                                                <p>
                                                    Hạn mục tiêu:{" "}
                                                    <strong>
                                                        {new Date(`${goal.dueDate}T00:00:00`).toLocaleDateString("vi-VN")}
                                                    </strong>
                                                </p>
                                            )}
                                            {goal.freeTime && (
                                                <p>
                                                    Lịch rảnh:{" "}
                                                    <strong>
                                                        {goal.freeTime.hoursPerDay ?? 2} giờ/ngày
                                                    </strong>
                                                    {goal.freeTime.weekdays &&
                                                        goal.freeTime.weekdays.length > 0 && (
                                                            <span>
                                                                {" "}
                                                                (
                                                                {goal.freeTime.weekdays
                                                                    .map(
                                                                        (d) =>
                                                                            WEEKDAYS_OPTIONS.find(
                                                                                (w) => w.value === d
                                                                            )?.label || d
                                                                    )
                                                                    .join(", ")}
                                                                )
                                                            </span>
                                                        )}
                                                </p>
                                            )}
                                        </div>

                                        <div className="pt-2 border-t border-border flex justify-end">
                                            <Button
                                                size="sm"
                                                variant="outline"
                                                onClick={() => openRequestAiModal(goal.id)}
                                                disabled={aiJob.isLoading}
                                                className="gap-1.5 text-xs text-primary border-primary/30 hover:bg-blue-50"
                                            >
                                                <Sparkles className="h-3.5 w-3.5" />
                                                <span>Đề xuất lộ trình AI cho mục tiêu này</span>
                                            </Button>
                                        </div>
                                    </CardContent>
                                </Card>
                            ))}
                        </div>
                    )}
                </TabsContent>

                {/* TAB 3: AI STUDY PLAN (BR-11) */}
                <TabsContent value="ai-plan" className="space-y-5">
                    {/* Active AI Job Progress */}
                    {aiJob.isLoading && (
                        <Card className="border-primary/30 bg-blue-50/50 shadow-sm">
                            <CardContent className="p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                                <div className="flex items-center gap-3">
                                    <Loader2 className="h-6 w-6 text-primary animate-spin shrink-0" />
                                    <div>
                                        <p className="text-sm font-bold text-slate-900">
                                            AI đang xây dựng lộ trình học tập đề xuất
                                        </p>
                                        <p className="text-xs text-slate-600 mt-0.5">
                                            {aiJob.progressText || "Đang phân tích mục tiêu, lịch bận và chủ đề yếu..."}
                                        </p>
                                    </div>
                                </div>
                                <Button
                                    size="sm"
                                    variant="outline"
                                    onClick={() => void aiJob.cancel()}
                                    className="text-xs text-rose-600 border-rose-200 hover:bg-rose-50 shrink-0"
                                >
                                    Hủy tác vụ
                                </Button>
                            </CardContent>
                        </Card>
                    )}

                    {aiJob.error && <ErrorBanner title="Lỗi tạo lộ trình AI" message={aiJob.error} />}

                    {isLoadingPlan ? (
                        <LoadingState label="Đang tải chi tiết lộ trình AI..." />
                    ) : planError ? (
                        <ErrorBanner title="Không thể tải lộ trình" message={planError} />
                    ) : !currentPlan ? (
                        <EmptyState
                            icon={<Sparkles className="h-12 w-12 text-amber-400" />}
                            title="Chưa có lộ trình đề xuất nào"
                            description="Bấm 'Nhờ AI đề xuất lộ trình' để AI tự động sắp xếp các buổi học phù hợp với lịch rảnh của bạn. Bạn luôn có quyền duyệt, sửa hoặc bỏ qua từng buổi."
                            action={
                                <Button
                                    onClick={() => openRequestAiModal()}
                                    disabled={aiJob.isLoading}
                                    className="gap-2 text-xs"
                                >
                                    <Sparkles className="h-4 w-4" />
                                    <span>Nhờ AI đề xuất lộ trình ngay</span>
                                </Button>
                            }
                        />
                    ) : (
                        <div className="space-y-4">
                            <Card className="border-border shadow-sm">
                                <CardHeader className="pb-3 border-b border-border flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                                    <div className="space-y-1">
                                        <div className="flex items-center gap-2">
                                            <CardTitle className="text-base font-bold text-slate-900 flex items-center gap-2">
                                                <Sparkles className="h-4 w-4 text-primary" />
                                                <span>Đề xuất lộ trình #{currentPlan.id}</span>
                                            </CardTitle>
                                            <Badge
                                                variant={
                                                    currentPlan.status === "CLOSED"
                                                        ? "success"
                                                        : currentPlan.status === "PARTIAL"
                                                        ? "warning"
                                                        : "info"
                                                }
                                            >
                                                {currentPlan.status === "CLOSED"
                                                    ? "Đã duyệt xong"
                                                    : currentPlan.status === "PARTIAL"
                                                    ? "Đang duyệt dở"
                                                    : "Chờ bạn duyệt"}
                                            </Badge>
                                        </div>
                                        {currentPlan.model && (
                                            <p className="text-[11px] text-muted-foreground">
                                                Mô hình: {currentPlan.model} • Tạo lúc{" "}
                                                {new Date(currentPlan.createdAt).toLocaleString("vi-VN")}
                                            </p>
                                        )}
                                    </div>
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        onClick={() => openRequestAiModal()}
                                        disabled={aiJob.isLoading}
                                        className="gap-1.5 text-xs shrink-0"
                                    >
                                        <Sparkles className="h-3.5 w-3.5" />
                                        <span>Đề xuất lộ trình mới</span>
                                    </Button>
                                </CardHeader>
                                <CardContent className="p-5 space-y-4">
                                    {currentPlan.rationale && (
                                        <div className="p-3.5 rounded-xl bg-blue-50/70 border border-blue-200 text-xs text-slate-800 space-y-1">
                                            <p className="font-bold text-primary">
                                                Lý do sắp xếp lộ trình từ AI:
                                            </p>
                                            <p className="leading-relaxed">{currentPlan.rationale}</p>
                                        </div>
                                    )}

                                    <div className="p-3 rounded-lg bg-slate-50 border border-border text-xs text-slate-600">
                                        Lưu ý: Các buổi học được <strong>Chấp nhận</strong> hoặc{" "}
                                        <strong>Chỉnh sửa & Chấp nhận</strong> sẽ tự động được thêm vào danh sách{" "}
                                        <strong>Nhiệm vụ theo ngày</strong> và không tự ghi đè sự kiện lịch hiện có của bạn.
                                    </div>

                                    <div className="space-y-3">
                                        {currentPlan.items.map((item) => {
                                            const isPending = item.decision === "PENDING";
                                            const isBusy = decidingItemId === item.id;

                                            return (
                                                <div
                                                    key={item.id}
                                                    className={`p-4 rounded-xl border transition-all space-y-3 ${
                                                        item.hasConflict && isPending
                                                            ? "border-amber-300 bg-amber-50/40"
                                                            : "border-border bg-surface"
                                                    }`}
                                                >
                                                    <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
                                                        <div className="space-y-1">
                                                            <div className="flex flex-wrap items-center gap-2">
                                                                <h4 className="font-bold text-sm text-slate-900">
                                                                    {item.title}
                                                                </h4>
                                                                {item.decision === "PENDING" && (
                                                                    <Badge variant="info">Chờ duyệt</Badge>
                                                                )}
                                                                {item.decision === "ACCEPTED" && (
                                                                    <Badge variant="success">
                                                                        Đã chấp nhận (đã thêm Task)
                                                                    </Badge>
                                                                )}
                                                                {item.decision === "EDITED" && (
                                                                    <Badge variant="success">
                                                                        Đã sửa & chấp nhận (đã thêm Task)
                                                                    </Badge>
                                                                )}
                                                                {item.decision === "REJECTED" && (
                                                                    <Badge variant="outline">Đã bỏ qua</Badge>
                                                                )}
                                                                {item.hasConflict && (
                                                                    <Badge
                                                                        variant="warning"
                                                                        icon={<AlertTriangle className="h-3 w-3" />}
                                                                    >
                                                                        Trùng giờ lịch hiện có
                                                                    </Badge>
                                                                )}
                                                            </div>
                                                            <p className="text-xs text-slate-600 flex items-center gap-1.5">
                                                                <Clock className="h-3.5 w-3.5 text-slate-400" />
                                                                <span>
                                                                    {new Date(item.plannedStart).toLocaleString("vi-VN", {
                                                                        weekday: "short",
                                                                        day: "2-digit",
                                                                        month: "2-digit",
                                                                        hour: "2-digit",
                                                                        minute: "2-digit"
                                                                    })}{" "}
                                                                    →{" "}
                                                                    {new Date(item.plannedEnd).toLocaleTimeString("vi-VN", {
                                                                        hour: "2-digit",
                                                                        minute: "2-digit"
                                                                    })}
                                                                </span>
                                                            </p>
                                                        </div>

                                                        {isPending && (
                                                            <div className="flex flex-wrap items-center gap-2 shrink-0">
                                                                <Button
                                                                    size="sm"
                                                                    variant="ghost"
                                                                    disabled={isBusy}
                                                                    onClick={() =>
                                                                        void handleDecidePlanItem(item, "REJECTED")
                                                                    }
                                                                    className="h-8 text-xs text-slate-600 hover:text-rose-600 gap-1"
                                                                >
                                                                    <X className="h-3.5 w-3.5" />
                                                                    <span>Bỏ qua</span>
                                                                </Button>
                                                                <Button
                                                                    size="sm"
                                                                    variant="outline"
                                                                    disabled={isBusy}
                                                                    onClick={() => openEditPlanItemModal(item)}
                                                                    className="h-8 text-xs gap-1"
                                                                >
                                                                    <Edit3 className="h-3.5 w-3.5" />
                                                                    <span>Chỉnh sửa & Chấp nhận</span>
                                                                </Button>
                                                                <Button
                                                                    size="sm"
                                                                    variant="default"
                                                                    disabled={isBusy}
                                                                    onClick={() =>
                                                                        void handleDecidePlanItem(item, "ACCEPTED")
                                                                    }
                                                                    className="h-8 text-xs bg-emerald-600 hover:bg-emerald-700 gap-1"
                                                                >
                                                                    <Check className="h-3.5 w-3.5" />
                                                                    <span>Chấp nhận</span>
                                                                </Button>
                                                            </div>
                                                        )}
                                                    </div>

                                                    {item.hasConflict && item.conflictingEvents.length > 0 && (
                                                        <div className="p-2.5 rounded-lg bg-amber-100/70 border border-amber-200 text-xs text-amber-950 space-y-0.5">
                                                            <p className="font-semibold">
                                                                Cảnh báo trùng lịch với:
                                                            </p>
                                                            {item.conflictingEvents.map((conf) => (
                                                                <p key={conf.eventId}>
                                                                    • {conf.title} (
                                                                    {new Date(conf.startAt).toLocaleTimeString("vi-VN", {
                                                                        hour: "2-digit",
                                                                        minute: "2-digit"
                                                                    })}{" "}
                                                                    -{" "}
                                                                    {new Date(conf.endAt).toLocaleTimeString("vi-VN", {
                                                                        hour: "2-digit",
                                                                        minute: "2-digit"
                                                                    })}
                                                                    )
                                                                </p>
                                                            ))}
                                                        </div>
                                                    )}
                                                </div>
                                            );
                                        })}
                                    </div>
                                </CardContent>
                            </Card>
                        </div>
                    )}
                </TabsContent>
            </Tabs>

            {/* TASK MODAL */}
            <Dialog open={isTaskModalOpen} onOpenChange={setIsTaskModalOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>
                            {editingTask ? "Cập nhật nhiệm vụ" : "Thêm nhiệm vụ theo ngày"}
                        </DialogTitle>
                    </DialogHeader>

                    <form onSubmit={(e) => void handleSaveTask(e)} className="space-y-4">
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Tiêu đề nhiệm vụ <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                value={taskTitle}
                                onChange={(e) => setTaskTitle(e.target.value)}
                                placeholder="Ví dụ: Ôn tập kiểm soát tắc nghẽn TCP"
                                required
                                disabled={isSavingTask}
                            />
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">
                                    Ngày thực hiện <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="date"
                                    value={taskDate}
                                    onChange={(e) => setTaskDate(e.target.value)}
                                    required
                                    disabled={isSavingTask}
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">Mức ưu tiên</label>
                                <select
                                    value={taskPriority}
                                    onChange={(e) => setTaskPriority(e.target.value as GoalPriority)}
                                    disabled={isSavingTask}
                                    className="w-full text-sm rounded-lg border border-border p-2 focus:outline-none focus:ring-2 focus:ring-primary/20"
                                >
                                    <option value="HIGH">Cao</option>
                                    <option value="MEDIUM">Trung bình</option>
                                    <option value="LOW">Thấp</option>
                                </select>
                            </div>
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Gắn với mục tiêu (tùy chọn)
                            </label>
                            <select
                                value={taskGoalId}
                                onChange={(e) => setTaskGoalId(e.target.value)}
                                disabled={isSavingTask}
                                className="w-full text-sm rounded-lg border border-border p-2 focus:outline-none focus:ring-2 focus:ring-primary/20"
                            >
                                <option value="">Không gắn mục tiêu</option>
                                {goals.map((g) => (
                                    <option key={g.id} value={g.id}>
                                        {g.title}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Thời điểm nhắc nhở (tùy chọn)
                            </label>
                            <Input
                                type="datetime-local"
                                value={taskRemindAt}
                                onChange={(e) => setTaskRemindAt(e.target.value)}
                                disabled={isSavingTask}
                            />
                        </div>

                        {editingTask && (
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold text-slate-700">Trạng thái</label>
                                <select
                                    value={taskModalStatus}
                                    onChange={(e) => setTaskModalStatus(e.target.value as TaskStatus)}
                                    disabled={isSavingTask}
                                    className="w-full text-sm rounded-lg border border-border p-2 focus:outline-none focus:ring-2 focus:ring-primary/20"
                                >
                                    <option value="TODO">Cần làm</option>
                                    <option value="DONE">Hoàn thành</option>
                                    <option value="MISSED">Bỏ lỡ</option>
                                </select>
                            </div>
                        )}

                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsTaskModalOpen(false)}
                                disabled={isSavingTask}
                            >
                                Hủy
                            </Button>
                            <Button type="submit" disabled={isSavingTask}>
                                {isSavingTask ? "Đang lưu..." : "Lưu nhiệm vụ"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            {/* GOAL MODAL */}
            <Dialog open={isGoalModalOpen} onOpenChange={setIsGoalModalOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>
                            {editingGoal ? "Cập nhật mục tiêu học tập" : "Tạo mục tiêu học tập mới"}
                        </DialogTitle>
                    </DialogHeader>

                    <form onSubmit={(e) => void handleSaveGoal(e)} className="space-y-4">
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Tên mục tiêu <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                value={goalTitle}
                                onChange={(e) => setGoalTitle(e.target.value)}
                                placeholder="Ví dụ: Đạt 9.0 môn Mạng máy tính"
                                required
                                disabled={isSavingGoal}
                            />
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">Môn học</label>
                            <Input
                                value={goalSubject}
                                onChange={(e) => setGoalSubject(e.target.value)}
                                placeholder="Ví dụ: Mạng máy tính"
                                disabled={isSavingGoal}
                            />
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">Hạn hoàn thành</label>
                                <Input
                                    type="date"
                                    value={goalDueDate}
                                    onChange={(e) => setGoalDueDate(e.target.value)}
                                    disabled={isSavingGoal}
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">Mức ưu tiên</label>
                                <select
                                    value={goalPriority}
                                    onChange={(e) => setGoalPriority(e.target.value as GoalPriority)}
                                    disabled={isSavingGoal}
                                    className="w-full text-sm rounded-lg border border-border p-2 focus:outline-none focus:ring-2 focus:ring-primary/20"
                                >
                                    <option value="HIGH">Cao</option>
                                    <option value="MEDIUM">Trung bình</option>
                                    <option value="LOW">Thấp</option>
                                </select>
                            </div>
                        </div>

                        {editingGoal && (
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold text-slate-700">Trạng thái</label>
                                <select
                                    value={goalStatus}
                                    onChange={(e) => setGoalStatus(e.target.value as GoalStatus)}
                                    disabled={isSavingGoal}
                                    className="w-full text-sm rounded-lg border border-border p-2 focus:outline-none focus:ring-2 focus:ring-primary/20"
                                >
                                    <option value="ACTIVE">Đang thực hiện</option>
                                    <option value="COMPLETED">Đã hoàn thành</option>
                                    <option value="CANCELLED">Đã hủy</option>
                                </select>
                            </div>
                        )}

                        <div className="space-y-2 p-3 rounded-lg bg-slate-50 border border-border">
                            <label className="text-xs font-semibold text-slate-700">
                                Cấu hình khung giờ rảnh cho mục tiêu
                            </label>
                            <div className="flex flex-wrap gap-1.5">
                                {WEEKDAYS_OPTIONS.map((w) => {
                                    const active = goalWeekdays.includes(w.value);
                                    return (
                                        <button
                                            key={w.value}
                                            type="button"
                                            disabled={isSavingGoal}
                                            onClick={() => toggleGoalWeekday(w.value)}
                                            className={`px-2.5 py-1 rounded-md text-xs font-semibold border transition-colors ${
                                                active
                                                    ? "bg-primary text-white border-primary"
                                                    : "bg-white text-slate-600 border-border hover:bg-muted"
                                            }`}
                                        >
                                            {w.label}
                                        </button>
                                    );
                                })}
                            </div>
                            <div className="flex items-center gap-2 pt-1">
                                <span className="text-xs text-slate-600">Số giờ học mỗi ngày rảnh:</span>
                                <Input
                                    type="number"
                                    min={1}
                                    max={12}
                                    value={goalHoursPerDay}
                                    onChange={(e) => setGoalHoursPerDay(Number(e.target.value))}
                                    disabled={isSavingGoal}
                                    className="w-20 h-8 text-xs"
                                />
                            </div>
                        </div>

                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsGoalModalOpen(false)}
                                disabled={isSavingGoal}
                            >
                                Hủy
                            </Button>
                            <Button type="submit" disabled={isSavingGoal}>
                                {isSavingGoal ? "Đang lưu..." : "Lưu mục tiêu"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            {/* REQUEST AI STUDY PLAN MODAL */}
            <Dialog open={isAiModalOpen} onOpenChange={setIsAiModalOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <Sparkles className="h-5 w-5 text-primary" />
                            <span>Nhờ AI đề xuất lộ trình học tập</span>
                        </DialogTitle>
                        <DialogDescription>
                            AI sẽ tổng hợp mục tiêu, lịch bận hiện có và các chủ đề bạn còn yếu để đề xuất thời gian biểu tối ưu.
                        </DialogDescription>
                    </DialogHeader>

                    <form onSubmit={(e) => void handleRequestAiPlan(e)} className="space-y-4">
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Chọn mục tiêu <span className="text-rose-500">*</span>
                            </label>
                            <select
                                value={aiGoalId}
                                onChange={(e) => setAiGoalId(Number(e.target.value))}
                                required
                                disabled={isRequestingAiPlan}
                                className="w-full text-sm rounded-lg border border-border p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20"
                            >
                                {goals.length === 0 ? (
                                    <option value="">Bạn cần tạo ít nhất 1 mục tiêu trước</option>
                                ) : (
                                    goals.map((g) => (
                                        <option key={g.id} value={g.id}>
                                            {g.title} {g.subject ? `(${g.subject})` : ""}
                                        </option>
                                    ))
                                )}
                            </select>
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">Từ ngày</label>
                                <Input
                                    type="date"
                                    value={aiFromDate}
                                    onChange={(e) => setAiFromDate(e.target.value)}
                                    required
                                    disabled={isRequestingAiPlan}
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">Đến ngày</label>
                                <Input
                                    type="date"
                                    value={aiToDate}
                                    onChange={(e) => setAiToDate(e.target.value)}
                                    required
                                    disabled={isRequestingAiPlan}
                                />
                            </div>
                        </div>

                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsAiModalOpen(false)}
                                disabled={isRequestingAiPlan}
                            >
                                Hủy
                            </Button>
                            <Button type="submit" disabled={isRequestingAiPlan || !aiGoalId}>
                                {isRequestingAiPlan ? "Đang gửi yêu cầu..." : "Tạo đề xuất lộ trình"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            {/* EDIT STUDY PLAN ITEM MODAL */}
            <Dialog
                open={!!editingPlanItem}
                onOpenChange={(open) => !open && setEditingPlanItem(null)}
            >
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>Chỉnh sửa & Chấp nhận buổi học</DialogTitle>
                        <DialogDescription>
                            Điều chỉnh lại tiêu đề hoặc khung giờ phù hợp trước khi lưu thành Nhiệm vụ theo ngày.
                        </DialogDescription>
                    </DialogHeader>

                    <form onSubmit={(e) => void handleConfirmEditedItem(e)} className="space-y-4">
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Tiêu đề buổi học <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                value={editItemTitle}
                                onChange={(e) => setEditItemTitle(e.target.value)}
                                required
                                disabled={decidingItemId !== null}
                            />
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Bắt đầu <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                type="datetime-local"
                                value={editItemStart}
                                onChange={(e) => setEditItemStart(e.target.value)}
                                required
                                disabled={decidingItemId !== null}
                            />
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Kết thúc <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                type="datetime-local"
                                value={editItemEnd}
                                onChange={(e) => setEditItemEnd(e.target.value)}
                                required
                                disabled={decidingItemId !== null}
                            />
                        </div>

                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setEditingPlanItem(null)}
                                disabled={decidingItemId !== null}
                            >
                                Hủy
                            </Button>
                            <Button type="submit" disabled={decidingItemId !== null}>
                                {decidingItemId !== null ? "Đang lưu..." : "Lưu & Chấp nhận"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default StudyPlanPage;
