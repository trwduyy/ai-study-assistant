import React from "react";
import { Link, useLocation } from "react-router-dom";
import {
    BookOpen,
    CalendarDays,
    ClipboardCheck,
    FileText,
    GraduationCap,
    LayoutDashboard,
    MessageSquare,
    Presentation,
    Settings,
    Users,
    UserCircle,
    BarChart3,
    ClipboardList,
    BrainCircuit,
    ShieldCheck
} from "lucide-react";
import { useAuth } from "../../contexts/AuthContext";
import { UserRole } from "../../types";

interface MenuItem {
    path: string;
    label: string;
    icon: React.ComponentType<{ className?: string }>;
}

const menuByRole: Record<UserRole, MenuItem[]> = {
    STUDENT: [
        { path: "/dashboard", label: "Tổng quan", icon: LayoutDashboard },
        { path: "/classes", label: "Lớp học", icon: Users },
        { path: "/schedule", label: "Lịch học", icon: CalendarDays },
        { path: "/assignments", label: "Bài tập", icon: ClipboardList },
        { path: "/messages", label: "Tin nhắn", icon: MessageSquare },
        { path: "/study-plan", label: "Kế hoạch học", icon: ClipboardCheck },
        { path: "/ai-materials", label: "Tài liệu AI", icon: BrainCircuit },
        { path: "/profile", label: "Hồ sơ", icon: UserCircle }
    ],
    TEACHER: [
        { path: "/dashboard", label: "Tổng quan", icon: LayoutDashboard },
        { path: "/my-classes", label: "Lớp của tôi", icon: Users },
        { path: "/assignments", label: "Bài tập", icon: ClipboardList },
        { path: "/tests", label: "Bài kiểm tra", icon: FileText },
        { path: "/schedule", label: "Lịch", icon: CalendarDays },
        { path: "/meetings", label: "Cuộc họp", icon: Presentation },
        { path: "/messages", label: "Tin nhắn", icon: MessageSquare },
        { path: "/class-statistics", label: "Thống kê lớp", icon: BarChart3 },
        { path: "/profile", label: "Hồ sơ", icon: UserCircle }
    ],
    ADMIN: [
        { path: "/users", label: "Người dùng", icon: Users },
        { path: "/admin/classes", label: "Lớp học", icon: BookOpen },
        { path: "/reports", label: "Báo cáo", icon: BarChart3 },
        { path: "/settings", label: "Cấu hình", icon: Settings },
        { path: "/profile", label: "Hồ sơ", icon: UserCircle }
    ]
};

export const Sidebar: React.FC = () => {
    const location = useLocation();
    const { user } = useAuth();
    const menuItems = menuByRole[user?.role ?? "STUDENT"];

    const isActive = (path: string) => {
        if (path === "/dashboard") {
            return location.pathname === "/dashboard" || location.pathname === "/";
        }
        return location.pathname.startsWith(path);
    };

    return (
        <aside className="w-64 bg-slate-900 text-white flex flex-col shrink-0 h-screen sticky top-0">
            <div className="h-16 flex items-center gap-3 px-6 border-b border-slate-800">
                <div className="w-10 h-10 rounded-xl bg-primary flex items-center justify-center text-white shadow-lg shadow-primary/30">
                    <GraduationCap className="w-6 h-6" />
                </div>
                <div>
                    <h1 className="font-bold text-base tracking-wide text-white">AI Study Assistant</h1>
                    <p className="text-xs text-slate-400">Trợ lý học tập thông minh</p>
                </div>
            </div>

            <nav className="flex-1 py-6 px-3 space-y-1.5 overflow-y-auto">
                <div className="px-3 mb-2 text-xs font-semibold uppercase tracking-wider text-slate-400">
                    {user?.role === "TEACHER" ? "Không gian giảng dạy" : user?.role === "ADMIN" ? "Quản trị hệ thống" : "Không gian học tập"}
                </div>
                {menuItems.map((item) => {
                    const Icon = item.icon;
                    const active = isActive(item.path);

                    return (
                        <Link
                            key={item.path}
                            to={item.path}
                            className={`flex items-center gap-3.5 px-3.5 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                                active
                                    ? "bg-primary text-white shadow-md shadow-primary/20"
                                    : "text-slate-300 hover:bg-slate-800 hover:text-white"
                            }`}
                        >
                            <Icon className={`w-5 h-5 ${active ? "text-white" : "text-slate-400"}`} />
                            <span>{item.label}</span>
                        </Link>
                    );
                })}
            </nav>

            <div className="p-4 border-t border-slate-800 text-xs text-slate-400 text-center">
                <p className="flex items-center justify-center gap-1 font-medium text-slate-300"><ShieldCheck className="h-3.5 w-3.5" /> Phiên bản V2</p>
                <p className="mt-0.5 text-slate-500">Học tập và giảng dạy thông minh</p>
            </div>
        </aside>
    );
};

export default Sidebar;
