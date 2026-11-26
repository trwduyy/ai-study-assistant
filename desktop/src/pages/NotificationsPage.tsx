import React, { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
    Bell,
    BellRing,
    Calendar,
    CheckCheck,
    ClipboardList,
    Clock,
    ExternalLink,
    FileText,
    GraduationCap,
    Mail,
    MessageSquare,
    Save,
    Settings,
    Smartphone
} from "lucide-react";
import { toast } from "sonner";
import { useNotifications } from "../contexts/NotificationContext";
import notificationService from "../services/notificationService";
import { NotificationItem, NotificationSetting } from "../types";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { EmptyState } from "../components/common/EmptyState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { LoadingState } from "../components/common/LoadingState";

interface EventTypeDisplayMeta {
    label: string;
    description: string;
    category: string;
}

const EVENT_TYPE_LABELS: Record<string, EventTypeDisplayMeta> = {
    ASSIGNMENT_CREATED: {
        label: "Bài tập mới",
        description: "Thông báo khi giảng viên giao bài tập mới trong lớp học.",
        category: "Bài tập"
    },
    ASSIGNMENT_DUE_SOON: {
        label: "Nhắc hạn nộp bài tập",
        description: "Nhắc nhở trước khi bài tập sắp đến hạn nộp.",
        category: "Bài tập"
    },
    ASSIGNMENT_SUBMITTED: {
        label: "Sinh viên nộp bài tập",
        description: "Thông báo cho giảng viên khi có sinh viên nộp bài.",
        category: "Bài tập"
    },
    ASSIGNMENT_GRADED: {
        label: "Bài tập đã được chấm điểm",
        description: "Thông báo điểm số và nhận xét từ giảng viên.",
        category: "Bài tập"
    },
    ASSIGNMENT_REOPENED: {
        label: "Mở lại quyền nộp bài",
        description: "Thông báo khi giảng viên mở lại hạn nộp cho phép nộp bổ sung.",
        category: "Bài tập"
    },
    CLASS_TEST_CREATED: {
        label: "Bài kiểm tra mới",
        description: "Thông báo lịch mở và thời lượng bài kiểm tra định thời.",
        category: "Kiểm tra"
    },
    CLASS_JOIN_REQUEST_CREATED: {
        label: "Yêu cầu tham gia lớp mới",
        description: "Thông báo cho giảng viên khi có sinh viên xin vào lớp.",
        category: "Lớp học"
    },
    CLASS_JOIN_REQUEST_APPROVED: {
        label: "Được duyệt vào lớp học",
        description: "Thông báo khi yêu cầu tham gia lớp của bạn được chấp thuận.",
        category: "Lớp học"
    },
    CLASS_JOIN_REQUEST_REJECTED: {
        label: "Yêu cầu tham gia lớp bị từ chối",
        description: "Thông báo kèm lý do từ giảng viên khi không được duyệt vào lớp.",
        category: "Lớp học"
    },
    CLASS_CO_TEACHER_ADDED: {
        label: "Phân công đồng giảng dạy",
        description: "Thông báo khi bạn được mời làm giảng viên đồng giảng dạy lớp.",
        category: "Lớp học"
    },
    CLASS_EVENT_CREATED: {
        label: "Lịch học / Cuộc họp mới",
        description: "Thông báo khi lớp có lịch học trực tuyến hoặc buổi học mới.",
        category: "Lịch học & Cuộc họp"
    },
    CLASS_EVENT_UPDATED: {
        label: "Cập nhật lịch học / Cuộc họp",
        description: "Thông báo khi thời gian hoặc liên kết buổi học thay đổi.",
        category: "Lịch học & Cuộc họp"
    },
    CLASS_EVENT_CANCELLED: {
        label: "Hủy lịch học / Cuộc họp",
        description: "Thông báo khi một buổi học hoặc cuộc họp lớp bị hủy.",
        category: "Lịch học & Cuộc họp"
    },
    EVENT_REMINDER_30M: {
        label: "Nhắc lịch học trước 30 phút",
        description: "Nhắc nhở trước giờ bắt đầu buổi học hoặc sự kiện cá nhân.",
        category: "Lịch học & Cuộc họp"
    },
    TASK_REMINDER: {
        label: "Nhắc việc học tập cá nhân",
        description: "Nhắc nhở công việc cần hoàn thành trong kế hoạch học tập.",
        category: "Kế hoạch học"
    },
    NEW_MESSAGE: {
        label: "Tin nhắn mới",
        description: "Thông báo khi có tin nhắn mới trong phòng lớp hoặc chat 1-1.",
        category: "Tin nhắn"
    }
};

function getEventMeta(type: string): EventTypeDisplayMeta {
    return (
        EVENT_TYPE_LABELS[type] ?? {
            label: type,
            description: "Thông báo cập nhật hoạt động hệ thống.",
            category: "Khác"
        }
    );
}

function getNotificationIcon(item: NotificationItem): React.ReactNode {
    const target = (item.targetType || "").toUpperCase();
    switch (target) {
        case "ASSIGNMENT":
        case "SUBMISSION":
            return <ClipboardList className="h-4 w-4 text-teal-700" />;
        case "CLASSROOM":
        case "CLASS_JOIN_REQUEST":
            return <GraduationCap className="h-4 w-4 text-primary" />;
        case "TEST":
        case "CLASS_TEST":
            return <FileText className="h-4 w-4 text-amber-600" />;
        case "CONVERSATION":
            return <MessageSquare className="h-4 w-4 text-indigo-600" />;
        case "EVENT":
        case "CLASS_EVENT":
            return <Calendar className="h-4 w-4 text-emerald-600" />;
        default:
            return <BellRing className="h-4 w-4 text-primary" />;
    }
}

interface ToggleSwitchProps {
    checked: boolean;
    disabled?: boolean;
    ariaLabel: string;
    onChange: (nextValue: boolean) => void;
}

const ToggleSwitch: React.FC<ToggleSwitchProps> = ({
    checked,
    disabled,
    ariaLabel,
    onChange
}) => (
    <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={ariaLabel}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors focus:outline-none focus:ring-2 focus:ring-primary/30 disabled:cursor-not-allowed disabled:opacity-50 ${
            checked ? "bg-primary" : "bg-slate-200"
        }`}
    >
        <span
            className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
                checked ? "translate-x-5" : "translate-x-0"
            }`}
        />
    </button>
);

export const NotificationsPage: React.FC = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const {
        unreadCount,
        isMarkingAll,
        markAsRead,
        markAllAsRead,
        openNotificationTarget,
        latestRealtimeNotification
    } = useNotifications();

    const activeMainTab = location.pathname.endsWith("/settings") ? "settings" : "list";

    // List state
    const [filterMode, setFilterMode] = useState<"ALL" | "UNREAD">("ALL");
    const [items, setItems] = useState<NotificationItem[]>([]);
    const [nextCursor, setNextCursor] = useState<string | null>(null);
    const [isLoadingList, setIsLoadingList] = useState<boolean>(true);
    const [isLoadingMore, setIsLoadingMore] = useState<boolean>(false);
    const [listError, setListError] = useState<string | null>(null);

    // Settings state
    const [settings, setSettings] = useState<NotificationSetting[]>([]);
    const [isLoadingSettings, setIsLoadingSettings] = useState<boolean>(false);
    const [settingsError, setSettingsError] = useState<string | null>(null);
    const [isSavingSettings, setIsSavingSettings] = useState<boolean>(false);
    const [hasUnsavedSettings, setHasUnsavedSettings] = useState<boolean>(false);

    const fetchNotifications = useCallback(
        async (mode: "ALL" | "UNREAD"): Promise<void> => {
            try {
                setIsLoadingList(true);
                const res = await notificationService.getNotifications(
                    null,
                    20,
                    mode === "UNREAD" ? true : undefined
                );
                setItems(res.items);
                setNextCursor(res.nextCursor);
                setListError(null);
            } catch (err: unknown) {
                setListError(
                    err instanceof Error ? err.message : "Không thể tải danh sách thông báo."
                );
            } finally {
                setIsLoadingList(false);
            }
        },
        []
    );

    useEffect(() => {
        if (activeMainTab === "list") {
            void fetchNotifications(filterMode);
        }
    }, [activeMainTab, fetchNotifications, filterMode]);

    // Sync real-time pushed notifications into list
    useEffect(() => {
        if (!latestRealtimeNotification) {
            return;
        }
        setItems((prev) => {
            if (prev.some((item) => item.id === latestRealtimeNotification.id)) {
                return prev;
            }
            return [latestRealtimeNotification, ...prev];
        });
    }, [latestRealtimeNotification]);

    const handleLoadMore = async (): Promise<void> => {
        if (!nextCursor || isLoadingMore) {
            return;
        }
        try {
            setIsLoadingMore(true);
            const res = await notificationService.getNotifications(
                nextCursor,
                20,
                filterMode === "UNREAD" ? true : undefined
            );
            setItems((prev) => {
                const existingIds = new Set(prev.map((i) => i.id));
                const uniqueNew = res.items.filter((i) => !existingIds.has(i.id));
                return [...prev, ...uniqueNew];
            });
            setNextCursor(res.nextCursor);
        } catch (err: unknown) {
            toast.error(
                err instanceof Error ? err.message : "Không thể tải thêm thông báo cũ hơn."
            );
        } finally {
            setIsLoadingMore(false);
        }
    };

    const handleItemClick = async (item: NotificationItem): Promise<void> => {
        if (!item.readAt) {
            const nowIso = new Date().toISOString();
            setItems((prev) => {
                const updated = prev.map((n) =>
                    n.id === item.id ? { ...n, readAt: n.readAt || nowIso } : n
                );
                return filterMode === "UNREAD"
                    ? updated.filter((n) => !n.readAt)
                    : updated;
            });
            await markAsRead(item.id);
        }
        await openNotificationTarget(item);
    };

    const handleMarkAll = async (): Promise<void> => {
        await markAllAsRead();
        const nowIso = new Date().toISOString();
        setItems((prev) => {
            if (filterMode === "UNREAD") {
                return [];
            }
            return prev.map((n) => (n.readAt ? n : { ...n, readAt: nowIso }));
        });
    };

    // Fetch notification settings
    const fetchSettings = useCallback(async (): Promise<void> => {
        try {
            setIsLoadingSettings(true);
            const data = await notificationService.getSettings();
            setSettings(data);
            setHasUnsavedSettings(false);
            setSettingsError(null);
        } catch (err: unknown) {
            setSettingsError(
                err instanceof Error ? err.message : "Không thể tải cấu hình thông báo."
            );
        } finally {
            setIsLoadingSettings(false);
        }
    }, []);

    useEffect(() => {
        if (activeMainTab === "settings") {
            void fetchSettings();
        }
    }, [activeMainTab, fetchSettings]);

    const handleToggleSetting = (
        type: string,
        channel: "inAppEnabled" | "emailEnabled",
        value: boolean
    ): void => {
        setSettings((prev) =>
            prev.map((item) => (item.type === type ? { ...item, [channel]: value } : item))
        );
        setHasUnsavedSettings(true);
    };

    const handleSaveSettings = async (): Promise<void> => {
        try {
            setIsSavingSettings(true);
            const saved = await notificationService.updateSettings(settings);
            setSettings(saved);
            setHasUnsavedSettings(false);
            toast.success("Đã lưu cấu hình kênh nhận thông báo!");
        } catch (err: unknown) {
            toast.error(
                err instanceof Error ? err.message : "Không thể lưu cấu hình thông báo."
            );
        } finally {
            setIsSavingSettings(false);
        }
    };

    return (
        <div className="space-y-6 max-w-5xl mx-auto">
            {/* Page Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2.5">
                        <Bell className="h-6 w-6 text-primary" />
                        <span>Thông báo & Cài đặt</span>
                    </h1>
                    <p className="text-sm text-muted-foreground mt-1">
                        Theo dõi cập nhật lớp học, hạn nộp bài tập và tùy chỉnh kênh nhận thông báo.
                    </p>
                </div>

                {activeMainTab === "list" && (
                    <Button
                        variant="outline"
                        size="sm"
                        disabled={isMarkingAll || unreadCount === 0}
                        onClick={() => void handleMarkAll()}
                        className="gap-2 text-xs self-start sm:self-auto"
                    >
                        <CheckCheck className="h-4 w-4 text-primary" />
                        <span>
                            {isMarkingAll
                                ? "Đang xử lý..."
                                : `Đánh dấu tất cả đã đọc${unreadCount > 0 ? ` (${unreadCount})` : ""}`}
                        </span>
                    </Button>
                )}
            </div>

            {/* Main Tabs: Notifications vs Settings */}
            <Tabs
                value={activeMainTab}
                onValueChange={(value) => {
                    navigate(value === "settings" ? "/notifications/settings" : "/notifications");
                }}
            >
                <TabsList>
                    <TabsTrigger value="list" className="gap-2">
                        <BellRing className="h-4 w-4" />
                        <span>Danh sách thông báo</span>
                        {unreadCount > 0 && (
                            <Badge variant="danger" className="text-[10px] px-1.5 py-0">
                                {unreadCount > 99 ? "99+" : unreadCount}
                            </Badge>
                        )}
                    </TabsTrigger>
                    <TabsTrigger value="settings" className="gap-2">
                        <Settings className="h-4 w-4" />
                        <span>Cài đặt thông báo</span>
                    </TabsTrigger>
                </TabsList>

                {/* TAB 1: Notifications List */}
                <TabsContent value="list" className="space-y-4">
                    <div className="flex items-center justify-between gap-3">
                        <div className="inline-flex rounded-lg border border-border bg-surface p-1 text-xs font-medium">
                            <button
                                type="button"
                                onClick={() => setFilterMode("ALL")}
                                className={`px-3 py-1.5 rounded-md transition-colors ${
                                    filterMode === "ALL"
                                        ? "bg-primary text-white shadow-sm"
                                        : "text-slate-600 hover:text-slate-900"
                                }`}
                            >
                                Tất cả
                            </button>
                            <button
                                type="button"
                                onClick={() => setFilterMode("UNREAD")}
                                className={`px-3 py-1.5 rounded-md transition-colors ${
                                    filterMode === "UNREAD"
                                        ? "bg-primary text-white shadow-sm"
                                        : "text-slate-600 hover:text-slate-900"
                                }`}
                            >
                                Chưa đọc
                            </button>
                        </div>
                    </div>

                    {isLoadingList ? (
                        <LoadingState label="Đang tải danh sách thông báo..." />
                    ) : listError ? (
                        <ErrorBanner
                            title="Lỗi tải thông báo"
                            message={listError}
                            onRetry={() => void fetchNotifications(filterMode)}
                        />
                    ) : items.length === 0 ? (
                        <EmptyState
                            icon={<Bell className="h-12 w-12 text-slate-300" />}
                            title={
                                filterMode === "UNREAD"
                                    ? "Bạn không có thông báo chưa đọc nào"
                                    : "Chưa có thông báo nào"
                            }
                            description={
                                filterMode === "UNREAD"
                                    ? "Tất cả thông báo của bạn đều đã được đọc."
                                    : "Các sự kiện mới từ lớp học, bài tập và tin nhắn sẽ hiển thị tại đây."
                            }
                        />
                    ) : (
                        <div className="space-y-4">
                            <Card className="border-border shadow-sm overflow-hidden">
                                <div className="divide-y divide-border">
                                    {items.map((item) => {
                                        const isUnread = !item.readAt;
                                        return (
                                            <div
                                                key={item.id}
                                                onClick={() => void handleItemClick(item)}
                                                role="button"
                                                tabIndex={0}
                                                onKeyDown={(e) => {
                                                    if (e.key === "Enter" || e.key === " ") {
                                                        e.preventDefault();
                                                        void handleItemClick(item);
                                                    }
                                                }}
                                                className={`p-4 flex items-start gap-4 cursor-pointer transition-colors hover:bg-slate-50 ${
                                                    isUnread ? "bg-blue-50/40" : "bg-surface"
                                                }`}
                                            >
                                                <div className="h-10 w-10 rounded-xl bg-slate-100 flex items-center justify-center shrink-0 mt-0.5">
                                                    {getNotificationIcon(item)}
                                                </div>

                                                <div className="min-w-0 flex-1 space-y-1">
                                                    <div className="flex flex-wrap items-center justify-between gap-2">
                                                        <div className="flex items-center gap-2 min-w-0">
                                                            {isUnread && (
                                                                <span
                                                                    className="h-2 w-2 rounded-full bg-primary shrink-0"
                                                                    aria-label="Chưa đọc"
                                                                />
                                                            )}
                                                            <h3
                                                                className={`text-sm truncate ${
                                                                    isUnread
                                                                        ? "font-bold text-slate-900"
                                                                        : "font-semibold text-slate-700"
                                                                }`}
                                                            >
                                                                {item.title}
                                                            </h3>
                                                        </div>

                                                        <span className="text-xs text-muted-foreground flex items-center gap-1 shrink-0">
                                                            <Clock className="h-3 w-3" />
                                                            {new Date(item.createdAt).toLocaleString(
                                                                "vi-VN",
                                                                {
                                                                    day: "2-digit",
                                                                    month: "2-digit",
                                                                    year: "numeric",
                                                                    hour: "2-digit",
                                                                    minute: "2-digit"
                                                                }
                                                            )}
                                                        </span>
                                                    </div>

                                                    <p className="text-xs text-slate-600 leading-relaxed">
                                                        {item.body}
                                                    </p>

                                                    <div className="pt-1 flex items-center gap-2">
                                                        <Badge
                                                            variant="outline"
                                                            className="text-[10px] font-normal"
                                                        >
                                                            {getEventMeta(item.type).label}
                                                        </Badge>
                                                        {item.targetType && (
                                                            <span className="inline-flex items-center gap-1 text-[11px] font-medium text-primary">
                                                                <span>Đi tới nội dung</span>
                                                                <ExternalLink className="h-3 w-3" />
                                                            </span>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </Card>

                            {nextCursor && (
                                <div className="flex justify-center pt-2">
                                    <Button
                                        variant="outline"
                                        onClick={() => void handleLoadMore()}
                                        disabled={isLoadingMore}
                                        className="text-xs"
                                    >
                                        {isLoadingMore ? "Đang tải thêm..." : "Tải thêm thông báo"}
                                    </Button>
                                </div>
                            )}
                        </div>
                    )}
                </TabsContent>

                {/* TAB 2: Notification Delivery Preferences */}
                <TabsContent value="settings" className="space-y-4">
                    {isLoadingSettings ? (
                        <LoadingState label="Đang tải cấu hình thông báo..." />
                    ) : settingsError ? (
                        <ErrorBanner
                            title="Lỗi tải cài đặt thông báo"
                            message={settingsError}
                            onRetry={() => void fetchSettings()}
                        />
                    ) : (
                        <Card className="border-border shadow-sm overflow-hidden">
                            <CardHeader className="pb-3 border-b border-border flex flex-row items-center justify-between gap-4">
                                <div>
                                    <CardTitle className="text-base font-bold text-slate-900">
                                        Tùy chọn kênh nhận thông báo theo sự kiện
                                    </CardTitle>
                                    <p className="text-xs text-muted-foreground mt-0.5">
                                        Bật hoặc tắt thông báo hiển thị trực tiếp trong ứng dụng và qua email cho từng loại sự kiện.
                                    </p>
                                </div>
                                <Button
                                    onClick={() => void handleSaveSettings()}
                                    disabled={isSavingSettings || !hasUnsavedSettings}
                                    className="gap-2 text-xs shrink-0"
                                >
                                    <Save className="h-4 w-4" />
                                    <span>
                                        {isSavingSettings ? "Đang lưu..." : "Lưu cài đặt"}
                                    </span>
                                </Button>
                            </CardHeader>

                            <CardContent className="p-0">
                                {settings.length === 0 ? (
                                    <div className="p-8 text-center text-xs text-muted-foreground">
                                        Chưa có danh mục cài đặt thông báo nào.
                                    </div>
                                ) : (
                                    <div className="overflow-x-auto">
                                        <table className="w-full text-sm text-left">
                                            <thead className="bg-slate-50 text-slate-700 text-xs uppercase font-semibold border-b border-border">
                                                <tr>
                                                    <th className="py-3.5 px-5">Loại sự kiện</th>
                                                    <th className="py-3.5 px-4 text-center w-40">
                                                        <span className="inline-flex items-center gap-1.5">
                                                            <Smartphone className="h-3.5 w-3.5 text-primary" />
                                                            Trong ứng dụng
                                                        </span>
                                                    </th>
                                                    <th className="py-3.5 px-4 text-center w-36">
                                                        <span className="inline-flex items-center gap-1.5">
                                                            <Mail className="h-3.5 w-3.5 text-teal-700" />
                                                            Email
                                                        </span>
                                                    </th>
                                                </tr>
                                            </thead>
                                            <tbody className="divide-y divide-border">
                                                {settings.map((setting) => {
                                                    const meta = getEventMeta(setting.type);
                                                    return (
                                                        <tr
                                                            key={setting.type}
                                                            className="hover:bg-slate-50/70 transition-colors"
                                                        >
                                                            <td className="py-3.5 px-5">
                                                                <div className="flex items-center gap-2">
                                                                    <span className="font-semibold text-slate-900">
                                                                        {meta.label}
                                                                    </span>
                                                                    <Badge
                                                                        variant="outline"
                                                                        className="text-[10px] font-normal"
                                                                    >
                                                                        {meta.category}
                                                                    </Badge>
                                                                </div>
                                                                <p className="text-xs text-muted-foreground mt-0.5">
                                                                    {meta.description}
                                                                </p>
                                                            </td>
                                                            <td className="py-3.5 px-4 text-center">
                                                                <div className="flex justify-center">
                                                                    <ToggleSwitch
                                                                        checked={setting.inAppEnabled}
                                                                        disabled={isSavingSettings}
                                                                        ariaLabel={`Bật/tắt thông báo trong ứng dụng cho ${meta.label}`}
                                                                        onChange={(val) =>
                                                                            handleToggleSetting(
                                                                                setting.type,
                                                                                "inAppEnabled",
                                                                                val
                                                                            )
                                                                        }
                                                                    />
                                                                </div>
                                                            </td>
                                                            <td className="py-3.5 px-4 text-center">
                                                                <div className="flex justify-center">
                                                                    <ToggleSwitch
                                                                        checked={setting.emailEnabled}
                                                                        disabled={isSavingSettings}
                                                                        ariaLabel={`Bật/tắt thông báo email cho ${meta.label}`}
                                                                        onChange={(val) =>
                                                                            handleToggleSetting(
                                                                                setting.type,
                                                                                "emailEnabled",
                                                                                val
                                                                            )
                                                                        }
                                                                    />
                                                                </div>
                                                            </td>
                                                        </tr>
                                                    );
                                                })}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                            </CardContent>
                        </Card>
                    )}
                </TabsContent>
            </Tabs>
        </div>
    );
};

export default NotificationsPage;
