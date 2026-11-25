import React, { useEffect, useState } from "react";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import { Bell, ChevronDown, CircleAlert, LogOut, UserRound } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import { useWebSocket } from "../../contexts/WebSocketContext";
import notificationService from "../../services/notificationService";
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

export const Header: React.FC<HeaderProps> = ({ title = "Bảng điều khiển" }) => {
    const { user, logout } = useAuth();
    const { status, subscribeNotifications } = useWebSocket();
    const [unreadCount, setUnreadCount] = useState(0);
    const navigate = useNavigate();

    useEffect(() => subscribeNotifications((notification) => {
        if (!notification.readAt) {
            setUnreadCount((count) => count + 1);
        }
    }), [subscribeNotifications]);

    useEffect(() => {
        const loadUnreadCount = async (): Promise<void> => {
            try {
                setUnreadCount(await notificationService.getUnreadCount());
            } catch {
                // Notifications are non-blocking and can be refreshed later.
            }
        };
        void loadUnreadCount();
    }, []);

    const handleLogout = async (): Promise<void> => {
        await logout();
        navigate("/login", { replace: true });
    };

    const initials = user?.fullName.split(" ").filter(Boolean).slice(-2).map((part) => part[0]).join("").toUpperCase() || "ND";
    const connectionVariant = status === "CONNECTED" ? "success" : status === "ERROR" ? "danger" : "warning";

    return (
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-border bg-surface px-6 shadow-sm">
            <h2 className="text-lg font-semibold text-foreground">{title}</h2>
            <div className="flex items-center gap-2 sm:gap-3">
                <Badge variant={connectionVariant} icon={<span className="h-2 w-2 rounded-full bg-current" />} className="hidden sm:inline-flex">
                    {statusText[status]}
                </Badge>
                <Button variant="ghost" size="icon" className="relative" aria-label={unreadCount > 0 ? `${unreadCount} thông báo chưa đọc` : "Không có thông báo chưa đọc"} title="Thông báo">
                    <Bell className="h-5 w-5" />
                    {unreadCount > 0 && <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold text-white">{unreadCount > 99 ? "99+" : unreadCount}</span>}
                </Button>
                <DropdownMenu.Root>
                    <DropdownMenu.Trigger asChild>
                        <button className="flex items-center gap-2 rounded-md p-1 text-left hover:bg-muted focus:outline-none focus:ring-2 focus:ring-primary" aria-label="Mở menu tài khoản">
                            <Avatar><AvatarImage src={user?.avatarUrl ?? undefined} alt="Ảnh đại diện" /><AvatarFallback>{initials}</AvatarFallback></Avatar>
                            <span className="hidden max-w-36 truncate text-sm font-medium text-foreground md:inline">{user?.fullName}</span>
                            <ChevronDown className="hidden h-4 w-4 text-muted-foreground md:block" />
                        </button>
                    </DropdownMenu.Trigger>
                    <DropdownMenu.Portal>
                        <DropdownMenu.Content align="end" className="z-50 min-w-52 rounded-md border border-border bg-surface p-1 shadow-elevated">
                            <div className="border-b border-border px-3 py-2"><p className="truncate text-sm font-medium text-foreground">{user?.fullName}</p><p className="truncate text-xs text-muted-foreground">{user?.email}</p></div>
                            <DropdownMenu.Item asChild><Link to="/profile" className="mt-1 flex cursor-pointer items-center gap-2 rounded-sm px-3 py-2 text-sm text-foreground outline-none hover:bg-muted"><UserRound className="h-4 w-4" />Hồ sơ cá nhân</Link></DropdownMenu.Item>
                            <DropdownMenu.Separator className="my-1 h-px bg-border" />
                            <DropdownMenu.Item onSelect={() => void handleLogout()} className="flex cursor-pointer items-center gap-2 rounded-sm px-3 py-2 text-sm text-danger outline-none hover:bg-danger-bg"><LogOut className="h-4 w-4" />Đăng xuất</DropdownMenu.Item>
                        </DropdownMenu.Content>
                    </DropdownMenu.Portal>
                </DropdownMenu.Root>
            </div>
            {status === "ERROR" && <span className="sr-only"><CircleAlert /> Kết nối trực tiếp gặp lỗi</span>}
        </header>
    );
};

export default Header;
