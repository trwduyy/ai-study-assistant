import React from "react";
import { Link, useLocation } from "react-router-dom";
import {
    BookOpen,
    GraduationCap,
    History,
    LayoutDashboard,
    UserCircle
} from "lucide-react";

export const Sidebar: React.FC = () => {
    const location = useLocation();

    const menuItems = [
        {
            path: "/dashboard",
            label: "Tổng quan",
            icon: LayoutDashboard
        },
        {
            path: "/courses",
            label: "Khóa học của tôi",
            icon: BookOpen
        },
        {
            path: "/history",
            label: "Lịch sử luyện tập",
            icon: History
        },
        {
            path: "/profile",
            label: "Hồ sơ cá nhân",
            icon: UserCircle
        }
    ];

    const isActive = (path: string) => {
        if (path === "/dashboard") {
            return location.pathname === "/dashboard" || location.pathname === "/";
        }
        return location.pathname.startsWith(path);
    };

    return (
        <aside className="w-64 bg-slate-900 text-white flex flex-col shrink-0 h-screen sticky top-0">
            {/* App Brand Header */}
            <div className="h-16 flex items-center gap-3 px-6 border-b border-slate-800">
                <div className="w-10 h-10 rounded-xl bg-indigo-600 flex items-center justify-center text-white shadow-lg shadow-indigo-600/30">
                    <GraduationCap className="w-6 h-6" />
                </div>
                <div>
                    <h1 className="font-bold text-base tracking-wide text-white">AI Study Assistant</h1>
                    <p className="text-xs text-slate-400">Trợ lý học tập thông minh</p>
                </div>
            </div>

            {/* Navigation Menu */}
            <nav className="flex-1 py-6 px-3 space-y-1.5 overflow-y-auto">
                <div className="px-3 mb-2 text-xs font-semibold uppercase tracking-wider text-slate-400">
                    Menu Học Tập
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
                                    ? "bg-indigo-600 text-white shadow-md shadow-indigo-600/20"
                                    : "text-slate-300 hover:bg-slate-800 hover:text-white"
                            }`}
                        >
                            <Icon className={`w-5 h-5 ${active ? "text-white" : "text-slate-400"}`} />
                            <span>{item.label}</span>
                        </Link>
                    );
                })}
            </nav>

            {/* Footer Workspace Info */}
            <div className="p-4 border-t border-slate-800 text-xs text-slate-400 text-center">
                <p className="font-medium text-slate-300">Phiên bản 1.0 (MVP)</p>
                <p className="mt-0.5 text-slate-500">Phát triển với React & Spring Boot</p>
            </div>
        </aside>
    );
};

export default Sidebar;
