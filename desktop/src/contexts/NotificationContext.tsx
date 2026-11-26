import React, {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useRef,
    useState
} from "react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import { NotificationItem, UserRole } from "../types";
import notificationService from "../services/notificationService";
import { useAuth } from "./AuthContext";
import { useWebSocket } from "./WebSocketContext";

interface NotificationContextValue {
    unreadCount: number;
    recentNotifications: NotificationItem[];
    isLoadingRecent: boolean;
    isMarkingAll: boolean;
    refreshSummary: () => Promise<void>;
    markAsRead: (id: number) => Promise<NotificationItem | null>;
    markAllAsRead: () => Promise<void>;
    openNotificationTarget: (item: NotificationItem) => Promise<void>;
    syncReadItemLocally: (updated: NotificationItem) => void;
    latestRealtimeNotification: NotificationItem | null;
}

const NotificationContext = createContext<NotificationContextValue | undefined>(undefined);

export function resolveNotificationTargetPath(
    item: Pick<NotificationItem, "targetType" | "targetId">,
    role?: UserRole
): string {
    const type = (item.targetType || "").toUpperCase();
    const id = item.targetId;
    const isTeacherOrAdmin = role === "TEACHER" || role === "ADMIN";

    switch (type) {
        case "CLASSROOM":
            return id ? `/classes/${id}` : "/classes";
        case "CLASS_JOIN_REQUEST":
            return "/classes";
        case "ASSIGNMENT":
            return id ? `/assignments/${id}` : "/assignments";
        case "SUBMISSION":
            return "/assignments";
        case "TEST":
        case "CLASS_TEST":
            if (!id) {
                return isTeacherOrAdmin ? "/tests" : "/classes";
            }
            return isTeacherOrAdmin ? `/tests/${id}/results` : `/tests/${id}/take`;
        case "CONVERSATION":
            return id ? `/messages/${id}` : "/messages";
        case "EVENT":
        case "CLASS_EVENT":
            return "/calendar";
        default:
            return "/notifications";
    }
}

export const NotificationProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
    const { isAuthenticated, user } = useAuth();
    const { status, subscribeNotifications } = useWebSocket();
    const navigate = useNavigate();

    const [unreadCount, setUnreadCount] = useState<number>(0);
    const [recentNotifications, setRecentNotifications] = useState<NotificationItem[]>([]);
    const [isLoadingRecent, setIsLoadingRecent] = useState<boolean>(false);
    const [isMarkingAll, setIsMarkingAll] = useState<boolean>(false);
    const [latestRealtimeNotification, setLatestRealtimeNotification] =
        useState<NotificationItem | null>(null);

    const prevWsStatusRef = useRef(status);

    const refreshSummary = useCallback(async (): Promise<void> => {
        if (!isAuthenticated) {
            return;
        }
        try {
            setIsLoadingRecent(true);
            const [count, page] = await Promise.all([
                notificationService.getUnreadCount(),
                notificationService.getNotifications(null, 10)
            ]);
            setUnreadCount(count);
            setRecentNotifications(page.items);
        } catch {
            // Keep existing notification state if network transiently fails
        } finally {
            setIsLoadingRecent(false);
        }
    }, [isAuthenticated]);

    useEffect(() => {
        if (isAuthenticated) {
            void refreshSummary();
        } else {
            setUnreadCount(0);
            setRecentNotifications([]);
            setLatestRealtimeNotification(null);
        }
    }, [isAuthenticated, refreshSummary]);

    // Auto-resync missed notifications when WebSocket reconnects
    useEffect(() => {
        const previousStatus = prevWsStatusRef.current;
        prevWsStatusRef.current = status;

        if (
            isAuthenticated &&
            status === "CONNECTED" &&
            (previousStatus === "RECONNECTING" || previousStatus === "ERROR")
        ) {
            void refreshSummary();
        }
    }, [isAuthenticated, refreshSummary, status]);

    const syncReadItemLocally = useCallback((updated: NotificationItem): void => {
        setRecentNotifications((prev) =>
            prev.map((item) => (item.id === updated.id ? updated : item))
        );
    }, []);

    const markAsRead = useCallback(
        async (id: number): Promise<NotificationItem | null> => {
            const targetInList = recentNotifications.find((item) => item.id === id);
            const wasUnread = targetInList ? !targetInList.readAt : true;

            try {
                const updated = await notificationService.markAsRead(id);
                syncReadItemLocally(updated);
                if (wasUnread) {
                    setUnreadCount((count) => Math.max(0, count - 1));
                }
                return updated;
            } catch (err: unknown) {
                toast.error(
                    err instanceof Error ? err.message : "Không thể đánh dấu thông báo đã đọc."
                );
                return null;
            }
        },
        [recentNotifications, syncReadItemLocally]
    );

    const markAllAsRead = useCallback(async (): Promise<void> => {
        try {
            setIsMarkingAll(true);
            await notificationService.markAllAsRead();
            const nowIso = new Date().toISOString();
            setUnreadCount(0);
            setRecentNotifications((prev) =>
                prev.map((item) => (item.readAt ? item : { ...item, readAt: nowIso }))
            );
            toast.success("Đã đánh dấu tất cả thông báo là đã đọc.");
        } catch (err: unknown) {
            toast.error(
                err instanceof Error ? err.message : "Không thể đánh dấu tất cả thông báo đã đọc."
            );
        } finally {
            setIsMarkingAll(false);
        }
    }, []);

    const openNotificationTarget = useCallback(
        async (item: NotificationItem): Promise<void> => {
            if (!item.readAt) {
                void markAsRead(item.id);
            }
            const targetPath = resolveNotificationTargetPath(item, user?.role);
            navigate(targetPath);
        },
        [markAsRead, navigate, user?.role]
    );

    // Subscribe to real-time STOMP notifications
    useEffect(() => {
        if (!isAuthenticated) {
            return;
        }
        return subscribeNotifications((notification) => {
            setLatestRealtimeNotification(notification);
            setRecentNotifications((prev) => {
                const exists = prev.some((item) => item.id === notification.id);
                if (exists) {
                    return prev.map((item) => (item.id === notification.id ? notification : item));
                }
                return [notification, ...prev].slice(0, 10);
            });

            if (!notification.readAt) {
                setUnreadCount((count) => count + 1);
            }

            toast.info(notification.title, {
                description: notification.body,
                action: {
                    label: "Xem nhanh",
                    onClick: () => {
                        void openNotificationTarget(notification);
                    }
                }
            });
        });
    }, [isAuthenticated, openNotificationTarget, subscribeNotifications]);

    const value = useMemo<NotificationContextValue>(
        () => ({
            unreadCount,
            recentNotifications,
            isLoadingRecent,
            isMarkingAll,
            refreshSummary,
            markAsRead,
            markAllAsRead,
            openNotificationTarget,
            syncReadItemLocally,
            latestRealtimeNotification
        }),
        [
            unreadCount,
            recentNotifications,
            isLoadingRecent,
            isMarkingAll,
            refreshSummary,
            markAsRead,
            markAllAsRead,
            openNotificationTarget,
            syncReadItemLocally,
            latestRealtimeNotification
        ]
    );

    return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
};

export function useNotifications(): NotificationContextValue {
    const context = useContext(NotificationContext);
    if (!context) {
        throw new Error("useNotifications phải được sử dụng bên trong NotificationProvider.");
    }
    return context;
}
