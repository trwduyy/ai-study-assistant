import React from "react";
import { Outlet, useLocation } from "react-router-dom";
import Header from "./Header";
import Sidebar from "./Sidebar";

export const MainLayout: React.FC = () => {
    const location = useLocation();

    const getPageTitle = (path: string): string => {
        if (path === "/" || path === "/dashboard" || path === "/teacher/dashboard") return "Tổng quan";
        if (path.startsWith("/courses/")) return "Chi tiết khóa học";
        if (path === "/courses" || path === "/ai-materials") return "Khóa học & Tài liệu học tập";
        if (path.startsWith("/materials/")) return "Không gian học tập AI";
        if (path.startsWith("/flashcards/") && path.includes("/review")) return "Ôn tập Flashcards SM-2";
        if (path === "/flashcards") return "Bộ thẻ Flashcards (SM-2)";
        if (path.startsWith("/tests/") && path.includes("/take")) return "Làm bài kiểm tra định thời";
        if (path.startsWith("/tests/") && path.includes("/results")) return "Kết quả bài kiểm tra cả lớp";
        if (path === "/tests") return "Danh sách bài kiểm tra";
        if (path.includes("/take")) return "Làm bài thi trắc nghiệm";
        if (path.startsWith("/attempts/")) return "Kết quả & Giải thích bài thi";
        if (path === "/history") return "Lịch sử luyện tập";
        if (path.startsWith("/assignments/") && path.length > 13) return "Chi tiết bài tập & Nộp bài";
        if (path === "/assignments") return "Danh sách bài tập";
        if (path.startsWith("/classes/") && path.length > 9) return "Chi tiết lớp học phần";
        if (path === "/classes" || path === "/my-classes") return "Lớp học phần";
        if (path === "/profile") return "Hồ sơ cá nhân";
        if (path.startsWith("/notifications/settings")) return "Cài đặt thông báo";
        if (path.startsWith("/notifications")) return "Thông báo";
        if (path.startsWith("/messages")) return "Tin nhắn thời gian thực";
        if (path.includes("schedule") || path.includes("calendar")) return "Lịch học";
        if (path.includes("report") || path.includes("statistic")) return "Báo cáo và thống kê";
        return "AI Study Assistant";
    };

    return (
        <div className="flex min-h-screen bg-slate-50">
            {/* Left navigation sidebar */}
            <Sidebar />

            {/* Main content area */}
            <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
                <Header title={getPageTitle(location.pathname)} />

                <main className="flex-1 overflow-y-auto p-6 md:p-8">
                    <div className="max-w-7xl mx-auto">
                        <Outlet />
                    </div>
                </main>
            </div>
        </div>
    );
};

export default MainLayout;
