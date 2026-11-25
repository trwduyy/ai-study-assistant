import React from "react";
import { Outlet, useLocation } from "react-router-dom";
import Header from "./Header";
import Sidebar from "./Sidebar";

export const MainLayout: React.FC = () => {
    const location = useLocation();

    const getPageTitle = (path: string): string => {
        if (path === "/" || path.startsWith("/dashboard")) return "Tổng quan";
        if (path.startsWith("/courses/")) return "Chi tiết khóa học";
        if (path === "/courses" || path === "/ai-materials") return "Khóa học & Tài liệu học tập";
        if (path.startsWith("/materials/")) return "Không gian học tập AI";
        if (path.startsWith("/flashcards/") && path.includes("/review")) return "Ôn tập Flashcards SM-2";
        if (path === "/flashcards") return "Bộ thẻ Flashcards (SM-2)";
        if (path.includes("/take")) return "Làm bài thi trắc nghiệm";
        if (path.startsWith("/attempts/")) return "Kết quả & Giải thích bài thi";
        if (path === "/history") return "Lịch sử luyện tập";
        if (path === "/profile") return "Hồ sơ cá nhân";
        if (path.includes("class")) return "Lớp học";
        if (path.includes("schedule")) return "Lịch học";
        if (path.includes("assignment")) return "Bài tập";
        if (path.includes("message")) return "Tin nhắn";
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
