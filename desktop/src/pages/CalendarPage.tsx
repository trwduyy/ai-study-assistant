import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
    AlertTriangle,
    BookOpen,
    Calendar as CalendarIcon,
    CheckSquare,
    ChevronLeft,
    ChevronRight,
    Clock,
    Download,
    ExternalLink,
    FileCheck,
    FileSpreadsheet,
    Globe,
    Lock,
    Pencil,
    Plus,
    RefreshCw,
    Trash2,
    Unlink,
    UploadCloud,
    UserRound,
    Video
} from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import classroomService from "../services/classroomService";
import scheduleService, { openExternalBrowserUrl } from "../services/scheduleService";
import {
    CalendarEvent,
    CalendarImportPreview,
    CalendarViewMode,
    Classroom,
    EventSource,
    EventStatus,
    EventType,
    GoogleIntegrationStatus
} from "../types";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../components/ui/dialog";
import { Input } from "../components/ui/input";
import { EmptyState } from "../components/common/EmptyState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { LoadingState } from "../components/common/LoadingState";

const WEEKDAY_LABELS = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "CN"];

function getLocalTimezone(): string {
    try {
        return Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Ho_Chi_Minh";
    } catch {
        return "Asia/Ho_Chi_Minh";
    }
}

function startOfLocalDay(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 0, 0, 0, 0);
}

function endOfLocalDay(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59, 999);
}

function getMondayOfWeek(date: Date): Date {
    const d = startOfLocalDay(date);
    const day = d.getDay(); // 0=Sun, 1=Mon
    const diff = day === 0 ? -6 : 1 - day;
    d.setDate(d.getDate() + diff);
    return d;
}

function addDays(date: Date, days: number): Date {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

function toLocalDateKey(date: Date): string {
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

function formatTimeRange(startIso: string, endIso: string): string {
    const s = new Date(startIso);
    const e = new Date(endIso);
    if (Number.isNaN(s.getTime()) || Number.isNaN(e.getTime())) return "";
    const startTime = s.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
    const endTime = e.toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
    return `${startTime} - ${endTime}`;
}

function formatFullLocalDateTime(iso: string): string {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "";
    return d.toLocaleString("vi-VN", {
        weekday: "short",
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit"
    });
}

function getEventTypeMeta(type: EventType): {
    label: string;
    icon: React.ReactNode;
    badgeVariant: "default" | "warning" | "secondary" | "info";
    cardClasses: string;
} {
    switch (type) {
        case "CLASS":
            return {
                label: "Lịch lớp",
                icon: <BookOpen className="h-3.5 w-3.5" />,
                badgeVariant: "default",
                cardClasses: "border-l-4 border-l-primary bg-blue-50/70 hover:bg-blue-50"
            };
        case "TEST":
            return {
                label: "Kiểm tra",
                icon: <FileCheck className="h-3.5 w-3.5" />,
                badgeVariant: "warning",
                cardClasses: "border-l-4 border-l-amber-600 bg-amber-50/70 hover:bg-amber-50"
            };
        case "MEETING":
            return {
                label: "Cuộc họp",
                icon: <Video className="h-3.5 w-3.5" />,
                badgeVariant: "secondary",
                cardClasses: "border-l-4 border-l-teal-700 bg-teal-50/70 hover:bg-teal-50"
            };
        case "PERSONAL":
        default:
            return {
                label: "Cá nhân",
                icon: <UserRound className="h-3.5 w-3.5" />,
                badgeVariant: "info",
                cardClasses: "border-l-4 border-l-sky-700 bg-sky-50/70 hover:bg-sky-50"
            };
    }
}

function getSourceBadge(source: EventSource): React.ReactNode {
    if (source === "GOOGLE") {
        return (
            <Badge variant="outline" icon={<Globe className="h-3 w-3 text-emerald-600" />} className="text-[10px]">
                Google
            </Badge>
        );
    }
    if (source === "ICS" || source === "CSV") {
        return (
            <Badge variant="outline" icon={<FileSpreadsheet className="h-3 w-3 text-slate-600" />} className="text-[10px]">
                {source}
            </Badge>
        );
    }
    return null;
}

function getStatusBadge(status: EventStatus): React.ReactNode {
    switch (status) {
        case "ONGOING":
            return <Badge variant="success">Đang diễn ra</Badge>;
        case "ENDED":
            return <Badge variant="outline">Đã kết thúc</Badge>;
        case "CANCELLED":
            return <Badge variant="danger">Đã hủy</Badge>;
        case "UPCOMING":
        default:
            return <Badge variant="info">Sắp diễn ra</Badge>;
    }
}

export const CalendarPage: React.FC = () => {
    const { user } = useAuth();
    const isTeacherOrAdmin = user?.role === "TEACHER" || user?.role === "ADMIN";
    const defaultTimezone = useMemo(() => getLocalTimezone(), []);

    const [viewMode, setViewMode] = useState<CalendarViewMode>("WEEK");
    const [anchorDate, setAnchorDate] = useState<Date>(() => new Date());
    const [events, setEvents] = useState<CalendarEvent[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Teacher's taught classes for creating class events
    const [taughtClasses, setTaughtClasses] = useState<Classroom[]>([]);

    // Detail drawer/dialog
    const [selectedEvent, setSelectedEvent] = useState<CalendarEvent | null>(null);
    const [isDeletingEvent, setIsDeletingEvent] = useState<boolean>(false);

    // Personal Event Form Modal
    const [isPersonalModalOpen, setIsPersonalModalOpen] = useState<boolean>(false);
    const [editingPersonalEvent, setEditingPersonalEvent] = useState<CalendarEvent | null>(null);
    const [personalTitle, setPersonalTitle] = useState<string>("");
    const [personalDesc, setPersonalDesc] = useState<string>("");
    const [personalStart, setPersonalStart] = useState<string>("");
    const [personalEnd, setPersonalEnd] = useState<string>("");
    const [personalMeetUrl, setPersonalMeetUrl] = useState<string>("");
    const [isSavingPersonal, setIsSavingPersonal] = useState<boolean>(false);

    // Class Event Form Modal (Teacher)
    const [isClassEventModalOpen, setIsClassEventModalOpen] = useState<boolean>(false);
    const [editingClassEvent, setEditingClassEvent] = useState<CalendarEvent | null>(null);
    const [classEventClassId, setClassEventClassId] = useState<number | "">("");
    const [classEventType, setClassEventType] = useState<"CLASS" | "TEST" | "MEETING">("MEETING");
    const [classEventTitle, setClassEventTitle] = useState<string>("");
    const [classEventDesc, setClassEventDesc] = useState<string>("");
    const [classEventStart, setClassEventStart] = useState<string>("");
    const [classEventEnd, setClassEventEnd] = useState<string>("");
    const [classEventMeetUrl, setClassEventMeetUrl] = useState<string>("");
    const [isSavingClassEvent, setIsSavingClassEvent] = useState<boolean>(false);

    // Import (.ics / .csv) Modal
    const [isImportModalOpen, setIsImportModalOpen] = useState<boolean>(false);
    const [importFile, setImportFile] = useState<File | null>(null);
    const [importTimezone, setImportTimezone] = useState<string>("Asia/Ho_Chi_Minh");
    const [isPreviewingImport, setIsPreviewingImport] = useState<boolean>(false);
    const [importPreview, setImportPreview] = useState<CalendarImportPreview | null>(null);
    const [acceptedIndices, setAcceptedIndices] = useState<number[]>([]);
    const [isConfirmingImport, setIsConfirmingImport] = useState<boolean>(false);

    // Google Calendar Integration Modal & Status
    const [googleStatus, setGoogleStatus] = useState<GoogleIntegrationStatus>(() =>
        scheduleService.getStoredGoogleStatus(user?.id)
    );
    const [isGoogleModalOpen, setIsGoogleModalOpen] = useState<boolean>(false);
    const [isAuthorizingGoogle, setIsAuthorizingGoogle] = useState<boolean>(false);
    const [isWaitingOAuthConsent, setIsWaitingOAuthConsent] = useState<boolean>(false);
    const [isSyncingGoogle, setIsSyncingGoogle] = useState<boolean>(false);
    const [isDisconnectingGoogle, setIsDisconnectingGoogle] = useState<boolean>(false);

    useEffect(() => {
        setGoogleStatus(scheduleService.getStoredGoogleStatus(user?.id));
    }, [user?.id]);

    // Compute visible date range [from, to]
    const visibleRange = useMemo(() => {
        if (viewMode === "DAY") {
            const from = startOfLocalDay(anchorDate);
            const to = endOfLocalDay(anchorDate);
            return { from, to, days: [startOfLocalDay(anchorDate)] };
        }
        if (viewMode === "WEEK") {
            const monday = getMondayOfWeek(anchorDate);
            const sunday = endOfLocalDay(addDays(monday, 6));
            const days = Array.from({ length: 7 }, (_, i) => addDays(monday, i));
            return { from: monday, to: sunday, days };
        }
        // MONTH
        const firstOfMonth = new Date(anchorDate.getFullYear(), anchorDate.getMonth(), 1);
        const lastOfMonth = new Date(anchorDate.getFullYear(), anchorDate.getMonth() + 1, 0);
        const gridStart = getMondayOfWeek(firstOfMonth);
        const lastWeekMonday = getMondayOfWeek(lastOfMonth);
        const gridEnd = endOfLocalDay(addDays(lastWeekMonday, 6));
        const totalDays =
            Math.round((startOfLocalDay(gridEnd).getTime() - gridStart.getTime()) / 86400000) + 1;
        const days = Array.from({ length: totalDays }, (_, i) => addDays(gridStart, i));
        return { from: gridStart, to: gridEnd, days };
    }, [anchorDate, viewMode]);

    const fetchCalendarEvents = useCallback(async (): Promise<void> => {
        try {
            setIsLoading(true);
            const data = await scheduleService.getCalendar(
                visibleRange.from.toISOString(),
                visibleRange.to.toISOString(),
                viewMode
            );
            setEvents(data);
            setError(null);

            if (data.some((ev) => ev.source === "GOOGLE")) {
                setGoogleStatus((prev) => {
                    if (prev.connected) return prev;
                    const updated: GoogleIntegrationStatus = {
                        provider: "GOOGLE",
                        connected: true,
                        connectedAt: prev.connectedAt || new Date().toISOString(),
                        lastSyncedAt: prev.lastSyncedAt
                    };
                    scheduleService.saveStoredGoogleStatus(user?.id, updated);
                    return updated;
                });
            }
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải lịch học tổng hợp.");
        } finally {
            setIsLoading(false);
        }
    }, [user?.id, viewMode, visibleRange.from, visibleRange.to]);

    useEffect(() => {
        void fetchCalendarEvents();
    }, [fetchCalendarEvents]);

    // Fetch taught classes for Teachers
    useEffect(() => {
        if (!isTeacherOrAdmin) return;
        const loadTaughtClasses = async () => {
            try {
                const res = await classroomService.getMyClassrooms({ status: "OPEN", page: 0, size: 50 });
                const teacherClasses = res.items.filter(
                    (c) => c.myClassRole === "OWNER_TEACHER" || c.myClassRole === "CO_TEACHER"
                );
                setTaughtClasses(teacherClasses);
            } catch {
                // Non-blocking
            }
        };
        void loadTaughtClasses();
    }, [isTeacherOrAdmin]);

    // Group events by local date key
    const eventsByDateKey = useMemo(() => {
        const map = new Map<string, CalendarEvent[]>();
        for (const ev of events) {
            const start = new Date(ev.startAt);
            const end = new Date(ev.endAt);
            if (Number.isNaN(start.getTime())) continue;

            const startDay = startOfLocalDay(start);
            const endDay = Number.isNaN(end.getTime()) ? startDay : startOfLocalDay(end);
            let cursor = new Date(startDay);
            let guard = 0;

            while (cursor.getTime() <= endDay.getTime() && guard < 31) {
                const key = toLocalDateKey(cursor);
                const list = map.get(key) ?? [];
                list.push(ev);
                map.set(key, list);
                cursor = addDays(cursor, 1);
                guard++;
            }
        }
        return map;
    }, [events]);

    // Navigation handlers
    const handleNavigatePrev = () => {
        if (viewMode === "DAY") {
            setAnchorDate((d) => addDays(d, -1));
        } else if (viewMode === "WEEK") {
            setAnchorDate((d) => addDays(d, -7));
        } else {
            setAnchorDate((d) => new Date(d.getFullYear(), d.getMonth() - 1, 1));
        }
    };

    const handleNavigateNext = () => {
        if (viewMode === "DAY") {
            setAnchorDate((d) => addDays(d, 1));
        } else if (viewMode === "WEEK") {
            setAnchorDate((d) => addDays(d, 7));
        } else {
            setAnchorDate((d) => new Date(d.getFullYear(), d.getMonth() + 1, 1));
        }
    };

    const handleToday = () => {
        setAnchorDate(new Date());
    };

    const rangeLabel = useMemo(() => {
        if (viewMode === "DAY") {
            return anchorDate.toLocaleDateString("vi-VN", {
                weekday: "long",
                day: "2-digit",
                month: "2-digit",
                year: "numeric"
            });
        }
        if (viewMode === "WEEK") {
            const startStr = visibleRange.from.toLocaleDateString("vi-VN", {
                day: "2-digit",
                month: "2-digit"
            });
            const endStr = visibleRange.to.toLocaleDateString("vi-VN", {
                day: "2-digit",
                month: "2-digit",
                year: "numeric"
            });
            return `Tuần ${startStr} - ${endStr}`;
        }
        return anchorDate.toLocaleDateString("vi-VN", {
            month: "long",
            year: "numeric"
        });
    }, [anchorDate, viewMode, visibleRange.from, visibleRange.to]);

    // -------------------------------------------------------------------------
    // Personal Event CRUD
    // -------------------------------------------------------------------------
    const openCreatePersonalModal = () => {
        const now = new Date();
        now.setMinutes(0, 0, 0);
        const start = addDays(now, 0);
        start.setHours(now.getHours() + 1);
        const end = new Date(start.getTime() + 60 * 60 * 1000);

        setEditingPersonalEvent(null);
        setPersonalTitle("");
        setPersonalDesc("");
        setPersonalStart(toDateTimeLocalValue(start.toISOString()));
        setPersonalEnd(toDateTimeLocalValue(end.toISOString()));
        setPersonalMeetUrl("");
        setIsPersonalModalOpen(true);
    };

    const openEditPersonalModal = (ev: CalendarEvent) => {
        setSelectedEvent(null);
        setEditingPersonalEvent(ev);
        setPersonalTitle(ev.title);
        setPersonalDesc(ev.description || "");
        setPersonalStart(toDateTimeLocalValue(ev.startAt));
        setPersonalEnd(toDateTimeLocalValue(ev.endAt));
        setPersonalMeetUrl(ev.meetingUrl || "");
        setIsPersonalModalOpen(true);
    };

    const handleSavePersonalEvent = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!personalTitle.trim() || !personalStart || !personalEnd) {
            toast.error("Vui lòng điền tiêu đề và thời gian bắt đầu/kết thúc.");
            return;
        }
        const startDate = new Date(personalStart);
        const endDate = new Date(personalEnd);
        if (endDate <= startDate) {
            toast.error("Thời gian kết thúc phải sau thời gian bắt đầu.");
            return;
        }

        try {
            setIsSavingPersonal(true);
            if (editingPersonalEvent) {
                await scheduleService.updatePersonalEvent(editingPersonalEvent.id, {
                    title: personalTitle.trim(),
                    description: personalDesc.trim() || null,
                    startAt: startDate.toISOString(),
                    endAt: endDate.toISOString(),
                    timezone: defaultTimezone,
                    meetingUrl: personalMeetUrl.trim() || null,
                    version: editingPersonalEvent.version
                });
                toast.success("Đã cập nhật sự kiện cá nhân!");
            } else {
                await scheduleService.createPersonalEvent({
                    title: personalTitle.trim(),
                    description: personalDesc.trim() || null,
                    startAt: startDate.toISOString(),
                    endAt: endDate.toISOString(),
                    timezone: defaultTimezone,
                    meetingUrl: personalMeetUrl.trim() || null
                });
                toast.success("Đã thêm sự kiện cá nhân!");
            }
            setIsPersonalModalOpen(false);
            void fetchCalendarEvents();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lưu sự kiện cá nhân thất bại.");
        } finally {
            setIsSavingPersonal(false);
        }
    };

    // -------------------------------------------------------------------------
    // Class Event CRUD (Teacher)
    // -------------------------------------------------------------------------
    const openCreateClassEventModal = () => {
        const now = new Date();
        now.setMinutes(0, 0, 0);
        const start = new Date(now.getTime() + 60 * 60 * 1000);
        const end = new Date(start.getTime() + 90 * 60 * 1000);

        setEditingClassEvent(null);
        setClassEventClassId(taughtClasses[0]?.id ?? "");
        setClassEventType("MEETING");
        setClassEventTitle("");
        setClassEventDesc("");
        setClassEventStart(toDateTimeLocalValue(start.toISOString()));
        setClassEventEnd(toDateTimeLocalValue(end.toISOString()));
        setClassEventMeetUrl("");
        setIsClassEventModalOpen(true);
    };

    const openEditClassEventModal = (ev: CalendarEvent) => {
        setSelectedEvent(null);
        setEditingClassEvent(ev);
        setClassEventClassId(ev.classId ?? "");
        setClassEventType(
            ev.type === "CLASS" || ev.type === "TEST" || ev.type === "MEETING"
                ? ev.type
                : "MEETING"
        );
        setClassEventTitle(ev.title);
        setClassEventDesc(ev.description || "");
        setClassEventStart(toDateTimeLocalValue(ev.startAt));
        setClassEventEnd(toDateTimeLocalValue(ev.endAt));
        setClassEventMeetUrl(ev.meetingUrl || "");
        setIsClassEventModalOpen(true);
    };

    const handleSaveClassEvent = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!classEventTitle.trim() || !classEventStart || !classEventEnd) {
            toast.error("Vui lòng điền tiêu đề và thời gian sự kiện lớp.");
            return;
        }
        const startDate = new Date(classEventStart);
        const endDate = new Date(classEventEnd);
        if (endDate <= startDate) {
            toast.error("Thời gian kết thúc phải sau thời gian bắt đầu.");
            return;
        }

        try {
            setIsSavingClassEvent(true);
            if (editingClassEvent) {
                await scheduleService.updateClassEvent(editingClassEvent.id, {
                    type: classEventType,
                    title: classEventTitle.trim(),
                    description: classEventDesc.trim() || null,
                    startAt: startDate.toISOString(),
                    endAt: endDate.toISOString(),
                    timezone: defaultTimezone,
                    meetingUrl: classEventMeetUrl.trim() || null,
                    version: editingClassEvent.version
                });
                toast.success("Đã cập nhật sự kiện lớp học!");
            } else {
                if (!classEventClassId) {
                    toast.error("Vui lòng chọn lớp học phần.");
                    return;
                }
                await scheduleService.createClassEvent(Number(classEventClassId), {
                    type: classEventType,
                    title: classEventTitle.trim(),
                    description: classEventDesc.trim() || null,
                    startAt: startDate.toISOString(),
                    endAt: endDate.toISOString(),
                    timezone: defaultTimezone,
                    meetingUrl: classEventMeetUrl.trim() || null
                });
                toast.success("Đã tạo sự kiện lớp học và thông báo cho sinh viên!");
            }
            setIsClassEventModalOpen(false);
            void fetchCalendarEvents();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lưu sự kiện lớp học thất bại.");
        } finally {
            setIsSavingClassEvent(false);
        }
    };

    const handleDeleteOrCancelEvent = async (ev: CalendarEvent) => {
        const isClass = ev.classId !== null;
        const confirmMsg = isClass
            ? `Bạn có chắc muốn hủy sự kiện lớp "${ev.title}"? Hệ thống sẽ gửi thông báo hủy lịch đến sinh viên.`
            : `Bạn có chắc muốn xóa sự kiện cá nhân "${ev.title}" không?`;
        if (!window.confirm(confirmMsg)) return;

        try {
            setIsDeletingEvent(true);
            if (isClass) {
                await scheduleService.cancelClassEvent(ev.id);
                toast.success("Đã hủy sự kiện lớp học.");
            } else {
                await scheduleService.deletePersonalEvent(ev.id);
                toast.success("Đã xóa sự kiện cá nhân.");
            }
            setSelectedEvent(null);
            void fetchCalendarEvents();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể xóa/hủy sự kiện.");
        } finally {
            setIsDeletingEvent(false);
        }
    };

    // -------------------------------------------------------------------------
    // Import .ics / .csv
    // -------------------------------------------------------------------------
    const resetImportModal = () => {
        setIsImportModalOpen(false);
        setImportFile(null);
        setImportPreview(null);
        setAcceptedIndices([]);
        setImportTimezone("Asia/Ho_Chi_Minh");
    };

    const handlePreviewImport = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!importFile) {
            toast.error("Vui lòng chọn tệp .ics hoặc .csv.");
            return;
        }
        if (importFile.size > 5 * 1024 * 1024) {
            toast.error("Dung lượng tệp vượt quá giới hạn 5MB.");
            return;
        }

        try {
            setIsPreviewingImport(true);
            const preview = await scheduleService.previewImport(importFile, importTimezone);
            setImportPreview(preview);
            // Default select non-conflicting items (user can toggle any)
            const initialIndices = preview.items
                .filter((item) => !item.hasConflict)
                .map((item) => item.itemIndex);
            setAcceptedIndices(
                initialIndices.length > 0
                    ? initialIndices
                    : preview.items.map((item) => item.itemIndex)
            );
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể đọc tệp lịch.");
        } finally {
            setIsPreviewingImport(false);
        }
    };

    const toggleImportItem = (itemIndex: number) => {
        setAcceptedIndices((prev) =>
            prev.includes(itemIndex) ? prev.filter((i) => i !== itemIndex) : [...prev, itemIndex]
        );
    };

    const handleConfirmImport = async () => {
        if (!importPreview || acceptedIndices.length === 0) {
            toast.error("Vui lòng chọn ít nhất 1 sự kiện để nhập.");
            return;
        }

        try {
            setIsConfirmingImport(true);
            const res = await scheduleService.confirmImport(
                importPreview.importId,
                acceptedIndices
            );
            toast.success(
                `Đã nhập thành công ${res.importedCount} sự kiện vào lịch của bạn!`
            );
            resetImportModal();
            void fetchCalendarEvents();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xác nhận nhập lịch thất bại.");
        } finally {
            setIsConfirmingImport(false);
        }
    };

    // -------------------------------------------------------------------------
    // Google Calendar Read-Only Integration
    // -------------------------------------------------------------------------
    const handleAuthorizeGoogle = async () => {
        try {
            setIsAuthorizingGoogle(true);
            const res = await scheduleService.authorizeGoogle();
            openExternalBrowserUrl(res.authorizeUrl);
            setIsWaitingOAuthConsent(true);
            toast.info(
                "Đã mở trình duyệt để bạn cấp quyền chỉ đọc Google Calendar. Sau khi hoàn tất, hãy nhấn 'Đồng bộ ngay'."
            );
        } catch (err: unknown) {
            toast.error(
                err instanceof Error ? err.message : "Không thể khởi tạo kết nối Google Calendar."
            );
        } finally {
            setIsAuthorizingGoogle(false);
        }
    };

    const handleSyncGoogle = async () => {
        try {
            setIsSyncingGoogle(true);
            const res = await scheduleService.syncGoogle({
                from: visibleRange.from.toISOString(),
                to: visibleRange.to.toISOString()
            });
            const updated: GoogleIntegrationStatus = {
                provider: "GOOGLE",
                connected: true,
                connectedAt: googleStatus.connectedAt || res.lastSyncedAt,
                lastSyncedAt: res.lastSyncedAt
            };
            setGoogleStatus(updated);
            scheduleService.saveStoredGoogleStatus(user?.id, updated);
            setIsWaitingOAuthConsent(false);
            toast.success(
                `Đồng bộ thành công ${res.syncedCount} sự kiện (mới: ${res.createdCount}, cập nhật: ${res.updatedCount}).`
            );
            void fetchCalendarEvents();
        } catch (err: unknown) {
            toast.error(
                err instanceof Error
                    ? err.message
                    : "Đồng bộ Google Calendar thất bại. Hãy kiểm tra lại bước cấp quyền."
            );
        } finally {
            setIsSyncingGoogle(false);
        }
    };

    const handleDisconnectGoogle = async () => {
        if (
            !window.confirm(
                "Bạn có chắc chắn muốn ngắt kết nối Google Calendar? Các sự kiện đã đồng bộ từ Google sẽ bị xóa khỏi lịch ứng dụng."
            )
        ) {
            return;
        }

        try {
            setIsDisconnectingGoogle(true);
            await scheduleService.disconnectGoogle();
            const resetStatus: GoogleIntegrationStatus = {
                provider: "GOOGLE",
                connected: false,
                connectedAt: null,
                lastSyncedAt: null
            };
            setGoogleStatus(resetStatus);
            scheduleService.saveStoredGoogleStatus(user?.id, resetStatus);
            setIsWaitingOAuthConsent(false);
            toast.success("Đã ngắt kết nối Google Calendar.");
            void fetchCalendarEvents();
        } catch (err: unknown) {
            toast.error(
                err instanceof Error ? err.message : "Không thể ngắt kết nối Google Calendar."
            );
        } finally {
            setIsDisconnectingGoogle(false);
        }
    };

    const todayKey = toLocalDateKey(new Date());

    return (
        <div className="space-y-6">
            {/* Top Header & Actions */}
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-4 border-b border-border">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2.5">
                        <CalendarIcon className="h-6 w-6 text-primary" />
                        <span>Lịch học tổng hợp & Sự kiện</span>
                    </h1>
                    <p className="text-sm text-muted-foreground mt-1">
                        Hợp nhất lịch học lớp, bài kiểm tra, cuộc họp Meet, sự kiện cá nhân và Google Calendar (múi giờ {defaultTimezone}).
                    </p>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setIsGoogleModalOpen(true)}
                        className="gap-1.5 text-xs"
                    >
                        <Globe className="h-3.5 w-3.5 text-emerald-600" />
                        <span>
                            {googleStatus.connected ? "Google Calendar (Đã nối)" : "Google Calendar"}
                        </span>
                    </Button>

                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setIsImportModalOpen(true)}
                        className="gap-1.5 text-xs"
                    >
                        <UploadCloud className="h-3.5 w-3.5 text-primary" />
                        <span>Nhập .ics / CSV</span>
                    </Button>

                    {isTeacherOrAdmin && (
                        <Button
                            size="sm"
                            variant="secondary"
                            onClick={openCreateClassEventModal}
                            className="gap-1.5 text-xs"
                        >
                            <Video className="h-3.5 w-3.5" />
                            <span>Tạo sự kiện lớp</span>
                        </Button>
                    )}

                    <Button
                        size="sm"
                        onClick={openCreatePersonalModal}
                        className="gap-1.5 text-xs"
                    >
                        <Plus className="h-3.5 w-3.5" />
                        <span>Thêm sự kiện cá nhân</span>
                    </Button>
                </div>
            </div>

            {/* Google Calendar Read-Only Status Bar */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl border border-emerald-200 bg-emerald-50/50 px-4 py-2.5 text-xs">
                <div className="flex items-center gap-2.5 text-emerald-950">
                    <Globe className="h-4 w-4 text-emerald-700 shrink-0" />
                    <span>
                        <strong>Google Calendar (chỉ đọc):</strong> Chỉ đọc lịch từ Google về ứng dụng, không thay đổi dữ liệu trên Google Calendar của bạn.
                    </span>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                    {googleStatus.connected ? (
                        <>
                            {googleStatus.lastSyncedAt && (
                                <span className="text-[11px] text-emerald-800">
                                    Đồng bộ lần cuối:{" "}
                                    {new Date(googleStatus.lastSyncedAt).toLocaleString("vi-VN", {
                                        day: "2-digit",
                                        month: "2-digit",
                                        hour: "2-digit",
                                        minute: "2-digit"
                                    })}
                                </span>
                            )}
                            <Button
                                size="sm"
                                variant="outline"
                                onClick={() => void handleSyncGoogle()}
                                disabled={isSyncingGoogle}
                                className="h-7 text-xs gap-1 border-emerald-300 bg-white hover:bg-emerald-50"
                            >
                                <RefreshCw className={`h-3 w-3 ${isSyncingGoogle ? "animate-spin" : ""}`} />
                                <span>{isSyncingGoogle ? "Đang đồng bộ..." : "Đồng bộ ngay"}</span>
                            </Button>
                        </>
                    ) : (
                        <Button
                            size="sm"
                            variant="outline"
                            onClick={() => setIsGoogleModalOpen(true)}
                            className="h-7 text-xs border-emerald-300 bg-white text-emerald-800 hover:bg-emerald-50"
                        >
                            Kết nối ngay
                        </Button>
                    )}
                </div>
            </div>

            {/* Calendar Toolbar: Navigation + View Switcher + Legend */}
            <Card className="border-border shadow-sm">
                <CardContent className="p-4 flex flex-col md:flex-row md:items-center justify-between gap-4">
                    <div className="flex items-center gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={handleToday}
                            className="h-8 text-xs font-semibold"
                        >
                            Hôm nay
                        </Button>
                        <div className="flex items-center rounded-lg border border-border bg-surface">
                            <button
                                type="button"
                                onClick={handleNavigatePrev}
                                className="p-1.5 hover:bg-muted rounded-l-lg text-slate-600"
                                aria-label="Khoảng thời gian trước"
                            >
                                <ChevronLeft className="h-4 w-4" />
                            </button>
                            <button
                                type="button"
                                onClick={handleNavigateNext}
                                className="p-1.5 hover:bg-muted rounded-r-lg text-slate-600 border-l border-border"
                                aria-label="Khoảng thời gian tiếp theo"
                            >
                                <ChevronRight className="h-4 w-4" />
                            </button>
                        </div>
                        <h2 className="text-sm sm:text-base font-bold text-slate-900 ml-2 capitalize">
                            {rangeLabel}
                        </h2>
                    </div>

                    <div className="flex flex-wrap items-center gap-3">
                        {/* Legend with text + icon */}
                        <div className="hidden xl:flex items-center gap-2 text-[11px]">
                            <Badge variant="default" icon={<BookOpen className="h-3 w-3" />}>
                                Lịch lớp
                            </Badge>
                            <Badge variant="warning" icon={<FileCheck className="h-3 w-3" />}>
                                Kiểm tra
                            </Badge>
                            <Badge variant="secondary" icon={<Video className="h-3 w-3" />}>
                                Cuộc họp
                            </Badge>
                            <Badge variant="info" icon={<UserRound className="h-3 w-3" />}>
                                Cá nhân
                            </Badge>
                            <Badge variant="outline" icon={<Globe className="h-3 w-3 text-emerald-600" />}>
                                Google
                            </Badge>
                        </div>

                        {/* View Switcher */}
                        <div className="inline-flex rounded-lg bg-muted p-1 text-xs font-semibold">
                            {(
                                [
                                    { key: "DAY", label: "Ngày" },
                                    { key: "WEEK", label: "Tuần" },
                                    { key: "MONTH", label: "Tháng" }
                                ] as const
                            ).map((mode) => (
                                <button
                                    key={mode.key}
                                    type="button"
                                    onClick={() => setViewMode(mode.key)}
                                    className={`px-3 py-1 rounded-md transition-colors ${
                                        viewMode === mode.key
                                            ? "bg-surface text-slate-900 shadow-sm"
                                            : "text-slate-500 hover:text-slate-900"
                                    }`}
                                >
                                    {mode.label}
                                </button>
                            ))}
                        </div>
                    </div>
                </CardContent>
            </Card>

            {/* Calendar Body */}
            {isLoading ? (
                <LoadingState label="Đang tải dữ liệu lịch học..." />
            ) : error ? (
                <ErrorBanner
                    title="Lỗi tải lịch học"
                    message={error}
                    onRetry={() => void fetchCalendarEvents()}
                />
            ) : viewMode === "DAY" ? (
                /* DAY VIEW */
                <Card className="border-border shadow-sm">
                    <CardContent className="p-5 space-y-3">
                        {(() => {
                            const key = toLocalDateKey(anchorDate);
                            const dayEvents = eventsByDateKey.get(key) ?? [];
                            if (dayEvents.length === 0) {
                                return (
                                    <EmptyState
                                        icon={<CalendarIcon className="h-10 w-10 text-slate-300" />}
                                        title="Không có sự kiện nào trong ngày này"
                                        description="Bạn có thể thêm sự kiện cá nhân, nhập lịch hoặc đồng bộ Google Calendar."
                                    />
                                );
                            }
                            return dayEvents.map((ev) => {
                                const typeMeta = getEventTypeMeta(ev.type);
                                return (
                                    <div
                                        key={ev.id}
                                        onClick={() => setSelectedEvent(ev)}
                                        className={`p-4 rounded-xl border border-border cursor-pointer transition-all flex flex-col sm:flex-row sm:items-center justify-between gap-3 ${typeMeta.cardClasses}`}
                                    >
                                        <div className="space-y-1.5">
                                            <div className="flex flex-wrap items-center gap-2">
                                                <Badge variant={typeMeta.badgeVariant} icon={typeMeta.icon}>
                                                    {typeMeta.label}
                                                </Badge>
                                                {getSourceBadge(ev.source)}
                                                {getStatusBadge(ev.status)}
                                                {ev.className && (
                                                    <Badge variant="outline" className="text-[11px]">
                                                        {ev.className}
                                                    </Badge>
                                                )}
                                            </div>
                                            <h3 className="font-bold text-base text-slate-900">
                                                {ev.title}
                                            </h3>
                                            <p className="text-xs text-slate-600 flex items-center gap-1.5">
                                                <Clock className="h-3.5 w-3.5" />
                                                <span>{formatTimeRange(ev.startAt, ev.endAt)}</span>
                                            </p>
                                        </div>

                                        {ev.meetingUrl && (
                                            <Button
                                                size="sm"
                                                variant="secondary"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    openExternalBrowserUrl(ev.meetingUrl!);
                                                }}
                                                className="gap-1.5 text-xs shrink-0"
                                            >
                                                <Video className="h-3.5 w-3.5" />
                                                <span>Mở Google Meet</span>
                                            </Button>
                                        )}
                                    </div>
                                );
                            });
                        })()}
                    </CardContent>
                </Card>
            ) : viewMode === "WEEK" ? (
                /* WEEK VIEW */
                <div className="grid grid-cols-1 md:grid-cols-7 gap-3">
                    {visibleRange.days.map((day, idx) => {
                        const key = toLocalDateKey(day);
                        const dayEvents = eventsByDateKey.get(key) ?? [];
                        const isToday = key === todayKey;

                        return (
                            <Card
                                key={key}
                                className={`border-border shadow-sm flex flex-col min-h-[300px] ${
                                    isToday ? "ring-2 ring-primary/40 border-primary/50" : ""
                                }`}
                            >
                                <div
                                    className={`p-3 border-b border-border text-center ${
                                        isToday ? "bg-blue-50/80" : "bg-slate-50/70"
                                    }`}
                                >
                                    <p className="text-[11px] font-semibold uppercase text-slate-500">
                                        {WEEKDAY_LABELS[idx]}
                                    </p>
                                    <p
                                        className={`text-sm font-bold mt-0.5 ${
                                            isToday ? "text-primary" : "text-slate-900"
                                        }`}
                                    >
                                        {day.toLocaleDateString("vi-VN", {
                                            day: "2-digit",
                                            month: "2-digit"
                                        })}
                                    </p>
                                </div>

                                <div className="p-2 space-y-2 flex-1">
                                    {dayEvents.length === 0 ? (
                                        <p className="text-[11px] text-slate-400 text-center py-8">
                                            Trống
                                        </p>
                                    ) : (
                                        dayEvents.map((ev) => {
                                            const typeMeta = getEventTypeMeta(ev.type);
                                            return (
                                                <button
                                                    key={ev.id}
                                                    type="button"
                                                    onClick={() => setSelectedEvent(ev)}
                                                    className={`w-full text-left p-2.5 rounded-lg border border-border/80 transition-all space-y-1 ${typeMeta.cardClasses}`}
                                                >
                                                    <div className="flex items-center justify-between gap-1">
                                                        <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-slate-700">
                                                            {typeMeta.icon}
                                                            {typeMeta.label}
                                                        </span>
                                                        {ev.source === "GOOGLE" && (
                                                            <Globe className="h-3 w-3 text-emerald-600 shrink-0" />
                                                        )}
                                                    </div>
                                                    <p className="text-xs font-bold text-slate-900 line-clamp-2 leading-snug">
                                                        {ev.title}
                                                    </p>
                                                    <p className="text-[10px] text-slate-600 flex items-center gap-1">
                                                        <Clock className="h-2.5 w-2.5 shrink-0" />
                                                        <span>{formatTimeRange(ev.startAt, ev.endAt)}</span>
                                                    </p>
                                                </button>
                                            );
                                        })
                                    )}
                                </div>
                            </Card>
                        );
                    })}
                </div>
            ) : (
                /* MONTH VIEW */
                <Card className="border-border shadow-sm overflow-hidden">
                    <div className="grid grid-cols-7 bg-slate-50 border-b border-border text-center text-xs font-semibold text-slate-600">
                        {WEEKDAY_LABELS.map((label) => (
                            <div key={label} className="py-2.5 border-r last:border-r-0 border-border">
                                {label}
                            </div>
                        ))}
                    </div>
                    <div className="grid grid-cols-7 divide-x divide-y divide-border">
                        {visibleRange.days.map((day) => {
                            const key = toLocalDateKey(day);
                            const dayEvents = eventsByDateKey.get(key) ?? [];
                            const isCurrentMonth = day.getMonth() === anchorDate.getMonth();
                            const isToday = key === todayKey;

                            return (
                                <div
                                    key={key}
                                    className={`min-h-[115px] p-2 space-y-1.5 ${
                                        isCurrentMonth ? "bg-surface" : "bg-slate-50/70"
                                    }`}
                                >
                                    <div className="flex items-center justify-between">
                                        <button
                                            type="button"
                                            onClick={() => {
                                                setAnchorDate(day);
                                                setViewMode("DAY");
                                            }}
                                            className={`h-6 w-6 rounded-full text-xs font-bold flex items-center justify-center transition-colors ${
                                                isToday
                                                    ? "bg-primary text-white"
                                                    : isCurrentMonth
                                                    ? "text-slate-800 hover:bg-muted"
                                                    : "text-slate-400 hover:bg-muted"
                                            }`}
                                        >
                                            {day.getDate()}
                                        </button>
                                        {dayEvents.length > 0 && (
                                            <span className="text-[10px] text-slate-400">
                                                {dayEvents.length} sự kiện
                                            </span>
                                        )}
                                    </div>

                                    <div className="space-y-1">
                                        {dayEvents.slice(0, 3).map((ev) => {
                                            const typeMeta = getEventTypeMeta(ev.type);
                                            return (
                                                <button
                                                    key={ev.id}
                                                    type="button"
                                                    onClick={() => setSelectedEvent(ev)}
                                                    className={`w-full text-left px-1.5 py-1 rounded text-[11px] truncate flex items-center gap-1 border border-border/60 ${typeMeta.cardClasses}`}
                                                    title={`${ev.title} (${formatTimeRange(ev.startAt, ev.endAt)})`}
                                                >
                                                    <span className="shrink-0">{typeMeta.icon}</span>
                                                    <span className="truncate font-medium text-slate-900">
                                                        {ev.title}
                                                    </span>
                                                </button>
                                            );
                                        })}
                                        {dayEvents.length > 3 && (
                                            <button
                                                type="button"
                                                onClick={() => {
                                                    setAnchorDate(day);
                                                    setViewMode("DAY");
                                                }}
                                                className="text-[10px] font-semibold text-primary hover:underline pl-1"
                                            >
                                                +{dayEvents.length - 3} sự kiện khác
                                            </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </Card>
            )}

            {/* EVENT DETAIL DIALOG */}
            <Dialog open={!!selectedEvent} onOpenChange={(open) => !open && setSelectedEvent(null)}>
                <DialogContent className="max-w-lg">
                    {selectedEvent && (
                        <>
                            <DialogHeader>
                                <div className="flex flex-wrap items-center gap-2 mb-1">
                                    {(() => {
                                        const meta = getEventTypeMeta(selectedEvent.type);
                                        return (
                                            <Badge variant={meta.badgeVariant} icon={meta.icon}>
                                                {meta.label}
                                            </Badge>
                                        );
                                    })()}
                                    {getSourceBadge(selectedEvent.source)}
                                    {getStatusBadge(selectedEvent.status)}
                                </div>
                                <DialogTitle className="text-lg font-bold text-slate-900">
                                    {selectedEvent.title}
                                </DialogTitle>
                                {selectedEvent.className && (
                                    <DialogDescription>
                                        Lớp học phần: <strong>{selectedEvent.className}</strong>
                                    </DialogDescription>
                                )}
                            </DialogHeader>

                            <div className="space-y-4 py-2 text-sm">
                                <div className="p-3.5 rounded-xl bg-slate-50 border border-border space-y-1.5 text-xs text-slate-700">
                                    <p className="flex items-center gap-2">
                                        <Clock className="h-4 w-4 text-primary shrink-0" />
                                        <span>
                                            <strong>Bắt đầu:</strong>{" "}
                                            {formatFullLocalDateTime(selectedEvent.startAt)}
                                        </span>
                                    </p>
                                    <p className="flex items-center gap-2">
                                        <Clock className="h-4 w-4 text-slate-400 shrink-0" />
                                        <span>
                                            <strong>Kết thúc:</strong>{" "}
                                            {formatFullLocalDateTime(selectedEvent.endAt)}
                                        </span>
                                    </p>
                                    <p className="text-[11px] text-slate-500 pl-6">
                                        Múi giờ gốc: {selectedEvent.timezone}
                                    </p>
                                </div>

                                {selectedEvent.description && (
                                    <div className="space-y-1">
                                        <p className="text-xs font-semibold text-slate-500 uppercase">
                                            Mô tả
                                        </p>
                                        <p className="text-sm text-slate-700 whitespace-pre-line bg-slate-50 p-3 rounded-lg border border-border">
                                            {selectedEvent.description}
                                        </p>
                                    </div>
                                )}

                                {selectedEvent.meetingUrl && (
                                    <div className="p-3.5 rounded-xl border border-teal-200 bg-teal-50/60 flex items-center justify-between gap-3">
                                        <div className="min-w-0">
                                            <p className="text-xs font-bold text-teal-900">
                                                Phòng họp trực tuyến
                                            </p>
                                            <p className="text-xs text-teal-800 truncate">
                                                {selectedEvent.meetingUrl}
                                            </p>
                                        </div>
                                        <Button
                                            size="sm"
                                            variant="secondary"
                                            onClick={() =>
                                                openExternalBrowserUrl(selectedEvent.meetingUrl!)
                                            }
                                            className="gap-1.5 text-xs shrink-0"
                                        >
                                            <ExternalLink className="h-3.5 w-3.5" />
                                            <span>Mở link Google Meet</span>
                                        </Button>
                                    </div>
                                )}

                                {(selectedEvent.readOnly || selectedEvent.source === "GOOGLE") && (
                                    <div className="p-3 rounded-lg bg-slate-100 border border-slate-200 text-xs text-slate-600 flex items-center gap-2">
                                        <Lock className="h-4 w-4 shrink-0 text-slate-500" />
                                        <span>
                                            <strong>Chỉ đọc:</strong>{" "}
                                            {selectedEvent.source === "GOOGLE"
                                                ? "Sự kiện được đồng bộ từ Google Calendar nên chỉ hỗ trợ xem."
                                                : "Sự kiện lớp học do giảng viên quản lý."}
                                        </span>
                                    </div>
                                )}
                            </div>

                            <DialogFooter className="gap-2">
                                {!selectedEvent.readOnly &&
                                    selectedEvent.source !== "GOOGLE" &&
                                    (selectedEvent.classId === null || isTeacherOrAdmin) && (
                                        <>
                                            <Button
                                                variant="outline"
                                                size="sm"
                                                disabled={isDeletingEvent}
                                                onClick={() => void handleDeleteOrCancelEvent(selectedEvent)}
                                                className="text-rose-600 border-rose-200 hover:bg-rose-50 gap-1.5"
                                            >
                                                <Trash2 className="h-3.5 w-3.5" />
                                                <span>
                                                    {selectedEvent.classId !== null
                                                        ? "Hủy sự kiện lớp"
                                                        : "Xóa sự kiện"}
                                                </span>
                                            </Button>

                                            <Button
                                                variant="outline"
                                                size="sm"
                                                disabled={isDeletingEvent}
                                                onClick={() => {
                                                    if (selectedEvent.classId !== null) {
                                                        openEditClassEventModal(selectedEvent);
                                                    } else {
                                                        openEditPersonalModal(selectedEvent);
                                                    }
                                                }}
                                                className="gap-1.5"
                                            >
                                                <Pencil className="h-3.5 w-3.5" />
                                                <span>Chỉnh sửa</span>
                                            </Button>
                                        </>
                                    )}
                                <Button variant="default" size="sm" onClick={() => setSelectedEvent(null)}>
                                    Đóng
                                </Button>
                            </DialogFooter>
                        </>
                    )}
                </DialogContent>
            </Dialog>

            {/* PERSONAL EVENT MODAL */}
            <Dialog open={isPersonalModalOpen} onOpenChange={setIsPersonalModalOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>
                            {editingPersonalEvent ? "Cập nhật sự kiện cá nhân" : "Thêm sự kiện cá nhân"}
                        </DialogTitle>
                    </DialogHeader>

                    <form onSubmit={(e) => void handleSavePersonalEvent(e)} className="space-y-4">
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Tiêu đề sự kiện <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                value={personalTitle}
                                onChange={(e) => setPersonalTitle(e.target.value)}
                                placeholder="Ví dụ: Tự ôn tập chương 3 TCP"
                                required
                                disabled={isSavingPersonal}
                            />
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">Mô tả</label>
                            <textarea
                                value={personalDesc}
                                onChange={(e) => setPersonalDesc(e.target.value)}
                                rows={3}
                                placeholder="Ghi chú nội dung cần chuẩn bị..."
                                disabled={isSavingPersonal}
                                className="w-full text-sm rounded-lg border border-border p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                            />
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">
                                    Bắt đầu <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="datetime-local"
                                    value={personalStart}
                                    onChange={(e) => setPersonalStart(e.target.value)}
                                    required
                                    disabled={isSavingPersonal}
                                    className="text-xs"
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">
                                    Kết thúc <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="datetime-local"
                                    value={personalEnd}
                                    onChange={(e) => setPersonalEnd(e.target.value)}
                                    required
                                    disabled={isSavingPersonal}
                                    className="text-xs"
                                />
                            </div>
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Link họp trực tuyến (tùy chọn)
                            </label>
                            <Input
                                type="url"
                                value={personalMeetUrl}
                                onChange={(e) => setPersonalMeetUrl(e.target.value)}
                                placeholder="https://meet.google.com/..."
                                disabled={isSavingPersonal}
                            />
                        </div>

                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsPersonalModalOpen(false)}
                                disabled={isSavingPersonal}
                            >
                                Hủy
                            </Button>
                            <Button type="submit" disabled={isSavingPersonal}>
                                {isSavingPersonal ? "Đang lưu..." : "Lưu sự kiện"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            {/* CLASS EVENT MODAL (TEACHER) */}
            <Dialog open={isClassEventModalOpen} onOpenChange={setIsClassEventModalOpen}>
                <DialogContent className="max-w-lg">
                    <DialogHeader>
                        <DialogTitle>
                            {editingClassEvent ? "Cập nhật sự kiện lớp học" : "Tạo sự kiện lớp học / Cuộc họp"}
                        </DialogTitle>
                        <DialogDescription>
                            Sự kiện tạo mới sẽ tự động gửi thông báo tới toàn bộ sinh viên trong lớp.
                        </DialogDescription>
                    </DialogHeader>

                    <form onSubmit={(e) => void handleSaveClassEvent(e)} className="space-y-4">
                        {!editingClassEvent && (
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold text-slate-700">
                                    Lớp học phần <span className="text-rose-500">*</span>
                                </label>
                                <select
                                    value={classEventClassId}
                                    onChange={(e) => setClassEventClassId(Number(e.target.value))}
                                    required
                                    disabled={isSavingClassEvent}
                                    className="w-full text-sm rounded-lg border border-border p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                                >
                                    {taughtClasses.length === 0 ? (
                                        <option value="">Bạn chưa phụ trách lớp đang mở nào</option>
                                    ) : (
                                        taughtClasses.map((cls) => (
                                            <option key={cls.id} value={cls.id}>
                                                {cls.name} ({cls.subject})
                                            </option>
                                        ))
                                    )}
                                </select>
                            </div>
                        )}

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold text-slate-700">Loại sự kiện</label>
                                <select
                                    value={classEventType}
                                    onChange={(e) =>
                                        setClassEventType(e.target.value as "CLASS" | "TEST" | "MEETING")
                                    }
                                    disabled={isSavingClassEvent}
                                    className="w-full text-sm rounded-lg border border-border p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                                >
                                    <option value="MEETING">Cuộc họp (Google Meet)</option>
                                    <option value="CLASS">Buổi học lớp</option>
                                    <option value="TEST">Lịch kiểm tra</option>
                                </select>
                            </div>
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold text-slate-700">
                                    Tiêu đề <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    value={classEventTitle}
                                    onChange={(e) => setClassEventTitle(e.target.value)}
                                    placeholder="Ví dụ: Họp giải đáp đồ án"
                                    required
                                    disabled={isSavingClassEvent}
                                />
                            </div>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">
                                    Bắt đầu <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="datetime-local"
                                    value={classEventStart}
                                    onChange={(e) => setClassEventStart(e.target.value)}
                                    required
                                    disabled={isSavingClassEvent}
                                    className="text-xs"
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">
                                    Kết thúc <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="datetime-local"
                                    value={classEventEnd}
                                    onChange={(e) => setClassEventEnd(e.target.value)}
                                    required
                                    disabled={isSavingClassEvent}
                                    className="text-xs"
                                />
                            </div>
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Dán link Google Meet (thủ công)
                            </label>
                            <Input
                                type="url"
                                value={classEventMeetUrl}
                                onChange={(e) => setClassEventMeetUrl(e.target.value)}
                                placeholder="https://meet.google.com/abc-defg-hij"
                                disabled={isSavingClassEvent}
                            />
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">Mô tả chi tiết</label>
                            <textarea
                                value={classEventDesc}
                                onChange={(e) => setClassEventDesc(e.target.value)}
                                rows={3}
                                placeholder="Nội dung buổi học hoặc dặn dò trước khi tham gia..."
                                disabled={isSavingClassEvent}
                                className="w-full text-sm rounded-lg border border-border p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                            />
                        </div>

                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsClassEventModalOpen(false)}
                                disabled={isSavingClassEvent}
                            >
                                Hủy
                            </Button>
                            <Button
                                type="submit"
                                disabled={isSavingClassEvent || (!editingClassEvent && !classEventClassId)}
                            >
                                {isSavingClassEvent ? "Đang lưu..." : "Lưu sự kiện lớp"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            {/* IMPORT .ICS / .CSV MODAL */}
            <Dialog open={isImportModalOpen} onOpenChange={(open) => !open && resetImportModal()}>
                <DialogContent className="max-w-2xl">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <FileSpreadsheet className="h-5 w-5 text-primary" />
                            <span>Nhập lịch từ tệp .ics hoặc .csv</span>
                        </DialogTitle>
                        <DialogDescription>
                            Tải lên thời khóa biểu hoặc lịch cá nhân, xem trước các mục trùng giờ và chọn sự kiện cần lưu.
                        </DialogDescription>
                    </DialogHeader>

                    {!importPreview ? (
                        <form onSubmit={(e) => void handlePreviewImport(e)} className="space-y-4">
                            <div className="flex items-center justify-between p-3 rounded-lg bg-slate-50 border border-border">
                                <span className="text-xs text-slate-600">
                                    Chưa có mẫu CSV chuẩn? Tải tệp mẫu để điền đúng cột thời gian.
                                </span>
                                <Button
                                    type="button"
                                    size="sm"
                                    variant="outline"
                                    onClick={() => void scheduleService.downloadTemplateCsv()}
                                    className="gap-1.5 text-xs h-8 shrink-0"
                                >
                                    <Download className="h-3.5 w-3.5" />
                                    <span>Tải tệp CSV mẫu</span>
                                </Button>
                            </div>

                            <div className="p-5 border-2 border-dashed border-border rounded-xl text-center space-y-2 hover:border-primary/50 transition-colors">
                                <UploadCloud className="h-9 w-9 text-primary mx-auto" />
                                <label className="block text-xs font-semibold text-primary cursor-pointer hover:underline">
                                    Chọn tệp .ics hoặc .csv (tối đa 5MB)
                                    <input
                                        type="file"
                                        accept=".ics,.csv"
                                        className="hidden"
                                        onChange={(e) => setImportFile(e.target.files?.[0] || null)}
                                        disabled={isPreviewingImport}
                                    />
                                </label>
                                {importFile && (
                                    <p className="text-xs font-semibold text-slate-800 bg-slate-100 p-2 rounded truncate">
                                        {importFile.name} ({(importFile.size / 1024).toFixed(1)} KB)
                                    </p>
                                )}
                            </div>

                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold text-slate-700">
                                    Múi giờ mặc định của tệp
                                </label>
                                <Input
                                    value={importTimezone}
                                    onChange={(e) => setImportTimezone(e.target.value)}
                                    placeholder="Asia/Ho_Chi_Minh"
                                    disabled={isPreviewingImport}
                                />
                            </div>

                            <DialogFooter>
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={resetImportModal}
                                    disabled={isPreviewingImport}
                                >
                                    Hủy
                                </Button>
                                <Button type="submit" disabled={isPreviewingImport || !importFile}>
                                    {isPreviewingImport ? "Đang phân tích..." : "Phân tích & Xem trước"}
                                </Button>
                            </DialogFooter>
                        </form>
                    ) : (
                        <div className="space-y-4">
                            <div className="flex flex-wrap items-center justify-between gap-2 p-3 rounded-lg bg-slate-50 border border-border text-xs">
                                <div className="flex items-center gap-3">
                                    <span>
                                        Tệp: <strong>{importPreview.fileName}</strong>
                                    </span>
                                    <Badge variant="info">
                                        Đọc được {importPreview.totalParsed} sự kiện
                                    </Badge>
                                    {importPreview.conflictCount > 0 && (
                                        <Badge variant="warning" icon={<AlertTriangle className="h-3 w-3" />}>
                                            {importPreview.conflictCount} mục trùng giờ
                                        </Badge>
                                    )}
                                </div>

                                <button
                                    type="button"
                                    onClick={() =>
                                        setAcceptedIndices(
                                            acceptedIndices.length === importPreview.items.length
                                                ? []
                                                : importPreview.items.map((i) => i.itemIndex)
                                        )
                                    }
                                    className="text-primary font-semibold hover:underline"
                                >
                                    {acceptedIndices.length === importPreview.items.length
                                        ? "Bỏ chọn tất cả"
                                        : "Chọn tất cả"}
                                </button>
                            </div>

                            <div className="max-h-80 overflow-y-auto divide-y divide-border rounded-xl border border-border">
                                {importPreview.items.map((item) => {
                                    const checked = acceptedIndices.includes(item.itemIndex);
                                    return (
                                        <div
                                            key={item.itemIndex}
                                            className={`p-3.5 flex items-start gap-3 text-xs ${
                                                item.hasConflict ? "bg-amber-50/60" : "bg-surface"
                                            }`}
                                        >
                                            <input
                                                type="checkbox"
                                                checked={checked}
                                                onChange={() => toggleImportItem(item.itemIndex)}
                                                disabled={isConfirmingImport}
                                                className="mt-1 h-4 w-4 rounded text-primary focus:ring-primary"
                                            />
                                            <div className="min-w-0 flex-1 space-y-1">
                                                <div className="flex items-center justify-between gap-2">
                                                    <p className="font-bold text-sm text-slate-900">
                                                        {item.title}
                                                    </p>
                                                    {item.hasConflict && (
                                                        <Badge
                                                            variant="warning"
                                                            icon={<AlertTriangle className="h-3 w-3" />}
                                                        >
                                                            Trùng giờ
                                                        </Badge>
                                                    )}
                                                </div>
                                                <p className="text-slate-600">
                                                    {formatFullLocalDateTime(item.startAt)} →{" "}
                                                    {formatFullLocalDateTime(item.endAt)}
                                                </p>
                                                {item.location && (
                                                    <p className="text-slate-500">Địa điểm: {item.location}</p>
                                                )}
                                                {item.hasConflict && item.conflictingEvents.length > 0 && (
                                                    <div className="mt-1.5 p-2 rounded-lg bg-amber-100/70 border border-amber-200 text-[11px] text-amber-950 space-y-0.5">
                                                        <p className="font-semibold">
                                                            Trùng thời gian với:
                                                        </p>
                                                        {item.conflictingEvents.map((conf) => (
                                                            <p key={conf.eventId}>
                                                                • {conf.title} (
                                                                {formatTimeRange(conf.startAt, conf.endAt)})
                                                            </p>
                                                        ))}
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>

                            <DialogFooter>
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => setImportPreview(null)}
                                    disabled={isConfirmingImport}
                                >
                                    Chọn tệp khác
                                </Button>
                                <Button
                                    type="button"
                                    onClick={() => void handleConfirmImport()}
                                    disabled={isConfirmingImport || acceptedIndices.length === 0}
                                    className="gap-1.5"
                                >
                                    <CheckSquare className="h-4 w-4" />
                                    <span>
                                        {isConfirmingImport
                                            ? "Đang nhập..."
                                            : `Xác nhận nhập (${acceptedIndices.length} sự kiện)`}
                                    </span>
                                </Button>
                            </DialogFooter>
                        </div>
                    )}
                </DialogContent>
            </Dialog>

            {/* GOOGLE CALENDAR MODAL */}
            <Dialog open={isGoogleModalOpen} onOpenChange={setIsGoogleModalOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <Globe className="h-5 w-5 text-emerald-600" />
                            <span>Kết nối Google Calendar (Chỉ đọc)</span>
                        </DialogTitle>
                        <DialogDescription>
                            Chỉ đọc lịch từ Google về ứng dụng, không thay đổi dữ liệu trên Google Calendar của bạn.
                        </DialogDescription>
                    </DialogHeader>

                    <div className="space-y-4 py-2 text-sm">
                        <div className="p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-xs text-emerald-950 space-y-1.5">
                            <p className="font-semibold">Quyền truy cập sử dụng:</p>
                            <p>
                                • Phạm vi chỉ đọc (<code>calendar.readonly</code>), không tạo, sửa hay xóa sự kiện trên tài khoản Google của bạn.
                            </p>
                            <p>
                                • Các sự kiện đồng bộ từ Google sẽ hiển thị ở chế độ chỉ đọc trên lịch học.
                            </p>
                        </div>

                        {googleStatus.connected ? (
                            <div className="p-3.5 rounded-xl bg-slate-50 border border-border space-y-2 text-xs">
                                <div className="flex items-center justify-between">
                                    <span className="font-semibold text-slate-700">Trạng thái:</span>
                                    <Badge variant="success">Đã kết nối</Badge>
                                </div>
                                {googleStatus.lastSyncedAt && (
                                    <div className="flex items-center justify-between text-slate-600">
                                        <span>Đồng bộ gần nhất:</span>
                                        <strong>
                                            {new Date(googleStatus.lastSyncedAt).toLocaleString("vi-VN")}
                                        </strong>
                                    </div>
                                )}
                            </div>
                        ) : isWaitingOAuthConsent ? (
                            <div className="p-3.5 rounded-xl bg-blue-50 border border-blue-200 text-xs text-blue-950 space-y-2">
                                <p className="font-semibold">Bước tiếp theo:</p>
                                <p>
                                    Sau khi bạn đăng nhập và cấp quyền trên trình duyệt mặc định, hãy bấm nút <strong>Đồng bộ ngay</strong> bên dưới để tải sự kiện về lịch.
                                </p>
                            </div>
                        ) : null}
                    </div>

                    <DialogFooter className="gap-2">
                        {googleStatus.connected ? (
                            <>
                                <Button
                                    type="button"
                                    variant="outline"
                                    disabled={isDisconnectingGoogle || isSyncingGoogle}
                                    onClick={() => void handleDisconnectGoogle()}
                                    className="text-rose-600 border-rose-200 hover:bg-rose-50 gap-1.5"
                                >
                                    <Unlink className="h-3.5 w-3.5" />
                                    <span>{isDisconnectingGoogle ? "Đang ngắt..." : "Ngắt kết nối"}</span>
                                </Button>
                                <Button
                                    type="button"
                                    disabled={isSyncingGoogle || isDisconnectingGoogle}
                                    onClick={() => void handleSyncGoogle()}
                                    className="gap-1.5"
                                >
                                    <RefreshCw className={`h-3.5 w-3.5 ${isSyncingGoogle ? "animate-spin" : ""}`} />
                                    <span>{isSyncingGoogle ? "Đang đồng bộ..." : "Đồng bộ ngay"}</span>
                                </Button>
                            </>
                        ) : (
                            <>
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => void handleAuthorizeGoogle()}
                                    disabled={isAuthorizingGoogle || isSyncingGoogle}
                                    className="gap-1.5"
                                >
                                    <ExternalLink className="h-3.5 w-3.5" />
                                    <span>
                                        {isAuthorizingGoogle
                                            ? "Đang mở..."
                                            : isWaitingOAuthConsent
                                            ? "Mở lại trang cấp quyền"
                                            : "Kết nối Google Calendar"}
                                    </span>
                                </Button>
                                {isWaitingOAuthConsent && (
                                    <Button
                                        type="button"
                                        onClick={() => void handleSyncGoogle()}
                                        disabled={isSyncingGoogle}
                                        className="gap-1.5"
                                    >
                                        <RefreshCw className={`h-3.5 w-3.5 ${isSyncingGoogle ? "animate-spin" : ""}`} />
                                        <span>{isSyncingGoogle ? "Đang đồng bộ..." : "Đồng bộ ngay"}</span>
                                    </Button>
                                )}
                            </>
                        )}
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default CalendarPage;
