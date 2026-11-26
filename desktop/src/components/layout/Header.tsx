import React, { useState } from "react";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import {
    Bell,
    CheckCheck,
    ChevronDown,
    CircleAlert,
    Clock,
    ExternalLink,
    LogOut,
    UserRound
} from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import { useNotifications } from "../../contexts/NotificationContext";
import { useWebSocket } from "../../contexts/WebSocketContext";
import { Avatar, AvatarFallback, AvatarImage } from "../ui/avatar";
import { Badge } from "../ui/badge";
import { Button } from "../ui/button";

interface HeaderProps {
    title?: string;
}

const statusText = {
    CONNECTED: "Đã kết nối trực tiếp",
    CONNECTING: "Đang kết nối trực tiếp",
    RECONNECTING: "Đang kết nối lại",
    DISCONNECTED: "Chưa kết nối trực tiếp",
    ERROR: "Kết nối trực tiếp gặp lỗi"
} as const;

function formatRelativeTime(isoDate: string): string {
    const time = new Date(isoDate).getTime();
    if (Number.isNaN(time)) {
        return "";
    }
    const diffMinutes = Math.floor((Date.now() - time) / 60000);
    if (diffMinutes < 1) {
        return "Vừa xong";
    }
    if (diffMinutes < 60) {
        return `${diffMinutes} phút trước`;
    }
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) {
        return `${diffHours} giờ trước`;
    }
    return new Date(isoDate).toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit"
    });
}

export const Header: React.FC<HeaderProps> = ({ title = "Bảng điều khiển" }) => {
    const { user, logout } = useAuth();
    const { status } = useWebSocket();
    const {
        unreadCount,
        recentNotifications,
        isLoadingRecent,
        isMarkingAll,
        refreshSummary,
        markAllAsRead,
        openNotificationTarget
    } = useNotifications();
    const [isBellOpen, setIsBellOpen] = useState(false);
    const navigate = useNavigate();

    const handleLogout = async (): Promise<void> => {
        await logout();
        navigate("/login", { replace: true });
    };

    const handleBellOpenChange = (open: boolean): void => {
        setIsBellOpen(open);
        if (open) {
            void refreshSummary();
        }
    };

    const initials =
        user?.fullName
            .split(" ")
            .filter(Boolean)
            .slice(-2)
            .map((part) => part[0])
            .join("")
            .toUpperCase() || "ND";
    const connectionVariant =
        status === "CONNECTED" ? "success" : status === "ERROR" ? "danger" : "warning";

    return (
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-border bg-surface px-6 shadow-sm">
            <h2 className="text-lg font-semibold text-foreground">{title}</h2>
            <div className="flex items-center gap-2 sm:gap-3">
                <Badge
                    variant={connectionVariant}
                    icon={<span className="h-2 w-2 rounded-full bg-current" />}
                    className="hidden sm:inline-flex"
                >
                    {statusText[status]}
                </Badge>

                {/* Notification Bell Popover / Dropdown */}
                <DropdownMenu.Root open={isBellOpen} onOpenChange={handleBellOpenChange}>
                    <DropdownMenu.Trigger asChild>
                        <Button
                            variant="ghost"
                            size="icon"
                            className="relative"
                            aria-label={
                                unreadCount > 0
                                    ? `${unreadCount} thông báo chưa đọc`
                                    : "Không có thông báo chưa đọc"
                            }
                            title="Thông báo"
                        >
                            <Bell className="h-5 w-5" />
                            {unreadCount > 0 && (
                                <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold text-white">
                                    {unreadCount > 99 ? "99+" : unreadCount}
                                </span>
                            )}
                        </Button>
                    </DropdownMenu.Trigger>

                    <DropdownMenu.Portal>
                        <DropdownMenu.Content
                            align="end"
                            sideOffset={8}
                            className="z-50 w-80 sm:w-96 rounded-xl border border-border bg-surface shadow-elevated overflow-hidden"
                        >
                            <div className="flex items-center justify-between border-b border-border px-4 py-3 bg-slate-50/70">
                                <div className="flex items-center gap-2">
                                    <span className="text-sm font-bold text-foreground">
                                        Thông báo
                                    </span>
                                    {unreadCount > 0 && (
                                        <Badge variant="danger" className="text-[10px] px-1.5 py-0">
                                            {unreadCount > 99 ? "99+" : unreadCount} mới
                                        </Badge>
                                    )}
                                </div>

                                <button
                                    type="button"
                                    onClick={() => void markAllAsRead()}
                                    disabled={isMarkingAll || unreadCount === 0}
                                    className="inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline disabled:opacity-40 disabled:no-underline"
                                >
                                    <CheckCheck className="h-3.5 w-3.5" />
                                    <span>Đánh dấu tất cả đã đọc</span>
                                </button>
                            </div>

                            <div className="max-h-96 overflow-y-auto divide-y divide-border">
                                {isLoadingRecent && recentNotifications.length === 0 ? (
                                    <div className="p-6 text-center text-xs text-muted-foreground">
                                        Đang tải thông báo...
                                    </div>
                                ) : recentNotifications.length === 0 ? (
                                    <div className="p-8 text-center space-y-2">
                                        <Bell className="h-8 w-8 text-slate-300 mx-auto" />
                                        <p className="text-xs font-medium text-slate-700">
                                            Chưa có thông báo nào
                                        </p>
                                        <p className="text-[11px] text-muted-foreground">
                                            Các cập nhật về lớp học, bài tập và tin nhắn sẽ hiển thị ở đây.
                                        </p>
                                    </div>
                                ) : (
                                    recentNotifications.slice(0, 10).map((item) => {
                                        const isUnread = !item.readAt;
                                        return (
                                            <DropdownMenu.Item
                                                key={item.id}
                                                onSelect={() => {
                                                    setIsBellOpen(false);
                                                    void openNotificationTarget(item);
                                                }}
                                                className={`flex cursor-pointer items-start gap-3 px-4 py-3 text-left outline-none transition-colors hover:bg-muted/70 ${
                                                    isUnread ? "bg-blue-50/40" : "bg-surface"
                                                }`}
                                            >
                                                <span
                                                    className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${
                                                        isUnread ? "bg-primary" : "bg-transparent"
                                                    }`}
                                                    aria-hidden="true"
                                                />
                                                <div className="min-w-0 flex-1 space-y-1">
                                                    <div className="flex items-start justify-between gap-2">
                                                        <p
                                                            className={`text-xs leading-snug line-clamp-1 ${
                                                                isUnread
                                                                    ? "font-bold text-foreground"
                                                                    : "font-medium text-slate-700"
                                                            }`}
                                                        >
                                                            {item.title}
                                                        </p>
                                                        <span className="shrink-0 text-[10px] text-muted-foreground flex items-center gap-0.5">
                                                            <Clock className="h-2.5 w-2.5" />
                                                            {formatRelativeTime(item.createdAt)}
                                                        </span>
                                                    </div>
                                                    <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed">
                                                        {item.body}
                                                    </p>
                                                </div>
                                            </DropdownMenu.Item>
                                        );
                                    })
                                )}
                            </div>

                            <div className="border-t border-border bg-slate-50/70 p-2">
                                <DropdownMenu.Item asChild>
                                    <Link
                                        to="/notifications"
                                        onClick={() => setIsBellOpen(false)}
                                        className="flex w-full items-center justify-center gap-1.5 rounded-lg py-2 text-xs font-semibold text-primary outline-none hover:bg-primary/10 transition-colors"
                                    >
                                        <span>Xem tất cả thông báo</span>
                                        <ExternalLink className="h-3.5 w-3.5" />
                                    </Link>
                                </DropdownMenu.Item>
                            </div>
                        </DropdownMenu.Content>
                    </DropdownMenu.Portal>
                </DropdownMenu.Root>

                {/* User Account Menu */}
                <DropdownMenu.Root>
                    <DropdownMenu.Trigger asChild>
                        <button
                            className="flex items-center gap-2 rounded-md p-1 text-left hover:bg-muted focus:outline-none focus:ring-2 focus:ring-primary"
                            aria-label="Mở menu tài khoản"
                        >
                            <Avatar>
                                <AvatarImage src={user?.avatarUrl ?? undefined} alt="Ảnh đại diện" />
                                <AvatarFallback>{initials}</AvatarFallback>
                            </Avatar>
                            <span className="hidden max-w-36 truncate text-sm font-medium text-foreground md:inline">
                                {user?.fullName}
                            </span>
                            <ChevronDown className="hidden h-4 w-4 text-muted-foreground md:block" />
                        </button>
                    </DropdownMenu.Trigger>
                    <DropdownMenu.Portal>
                        <DropdownMenu.Content
                            align="end"
                            className="z-50 min-w-52 rounded-md border border-border bg-surface p-1 shadow-elevated"
                        >
                            <div className="border-b border-border px-3 py-2">
                                <p className="truncate text-sm font-medium text-foreground">
                                    {user?.fullName}
                                </p>
                                <p className="truncate text-xs text-muted-foreground">
                                    {user?.email}
                                </p>
                            </div>
                            <DropdownMenu.Item asChild>
                                <Link
                                    to="/profile"
                                    className="mt-1 flex cursor-pointer items-center gap-2 rounded-sm px-3 py-2 text-sm text-foreground outline-none hover:bg-muted"
                                >
                                    <UserRound className="h-4 w-4" />
                                    Hồ sơ cá nhân
                                </Link>
                            </DropdownMenu.Item>
                            <DropdownMenu.Item asChild>
                                <Link
                                    to="/notifications/settings"
                                    className="flex cursor-pointer items-center gap-2 rounded-sm px-3 py-2 text-sm text-foreground outline-none hover:bg-muted"
                                >
                                    <Bell className="h-4 w-4" />
                                    Cài đặt thông báo
                                </Link>
                            </DropdownMenu.Item>
                            <DropdownMenu.Separator className="my-1 h-px bg-border" />
                            <DropdownMenu.Item
                                onSelect={() => void handleLogout()}
                                className="flex cursor-pointer items-center gap-2 rounded-sm px-3 py-2 text-sm text-danger outline-none hover:bg-danger-bg"
                            >
                                <LogOut className="h-4 w-4" />
                                Đăng xuất
                            </DropdownMenu.Item>
                        </DropdownMenu.Content>
                    </DropdownMenu.Portal>
                </DropdownMenu.Root>
            </div>
            {status === "ERROR" && (
                <span className="sr-only">
                    <CircleAlert /> Kết nối trực tiếp gặp lỗi
                </span>
            )}
        </header>
    );
};

export default Header;
