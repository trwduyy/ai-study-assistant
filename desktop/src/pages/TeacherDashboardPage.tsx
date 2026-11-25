import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
    Users,
    ClipboardList,
    Clock,
    FileText,
    ArrowRight,
    TrendingUp,
    CheckCircle2,
    AlertCircle,
    GraduationCap,
    BarChart3
} from "lucide-react";
import dashboardService from "../services/dashboardService";
import { TeacherDashboardResponse } from "../types";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { EmptyState } from "../components/common/EmptyState";

export const TeacherDashboardPage: React.FC = () => {
    const navigate = useNavigate();
    const [data, setData] = useState<TeacherDashboardResponse | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    const fetchTeacherDashboard = async () => {
        try {
            setIsLoading(true);
            const response = await dashboardService.getTeacherDashboard();
            setData(response);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải dữ liệu bảng điều khiển giảng viên.");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        void fetchTeacherDashboard();
    }, []);

    if (isLoading) {
        return <LoadingState label="Đang tải dữ liệu giảng dạy..." />;
    }

    if (error || !data) {
        return (
            <ErrorBanner
                title="Lỗi tải bảng điều khiển"
                message={error || "Không thể đồng bộ dữ liệu giảng dạy."}
                onRetry={fetchTeacherDashboard}
            />
        );
    }

    return (
        <div className="space-y-8 p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
                        <GraduationCap className="w-7 h-7 text-primary" />
                        Bảng điều khiển Giảng viên
                    </h1>
                    <p className="text-sm text-slate-500 mt-1">
                        Tổng quan hoạt động giảng dạy, theo dõi tiến độ nộp bài và kết quả kiểm tra.
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <Button
                        variant="outline"
                        onClick={() => navigate("/classes")}
                        className="text-slate-700"
                    >
                        Quản lý lớp học
                    </Button>
                    <Button
                        variant="default"
                        onClick={() => navigate("/classes")}
                        className="gap-2"
                    >
                        <Users className="w-4 h-4" />
                        Tạo lớp mới
                    </Button>
                </div>
            </div>

            {/* KPI Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                {/* Total classes */}
                <Card className="border-slate-200 shadow-sm hover:shadow-md transition-shadow">
                    <CardContent className="p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                                    Lớp đang phụ trách
                                </p>
                                <h3 className="text-3xl font-extrabold text-slate-900 mt-2">
                                    {data.totalClasses}
                                </h3>
                                <p className="text-xs text-slate-500 mt-1">Lớp học phần đang mở</p>
                            </div>
                            <div className="w-12 h-12 rounded-2xl bg-blue-50 text-primary flex items-center justify-center">
                                <Users className="w-6 h-6" />
                            </div>
                        </div>
                    </CardContent>
                </Card>

                {/* Pending Join Requests */}
                <Card className="border-slate-200 shadow-sm hover:shadow-md transition-shadow">
                    <CardContent className="p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                                    Yêu cầu chờ duyệt
                                </p>
                                <h3 className="text-3xl font-extrabold text-amber-600 mt-2">
                                    {data.pendingJoinRequests}
                                </h3>
                                <p className="text-xs text-slate-500 mt-1">Sinh viên xin tham gia lớp</p>
                            </div>
                            <div className="w-12 h-12 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center">
                                <Clock className="w-6 h-6" />
                            </div>
                        </div>
                    </CardContent>
                </Card>

                {/* Upcoming Assignments */}
                <Card className="border-slate-200 shadow-sm hover:shadow-md transition-shadow">
                    <CardContent className="p-6">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                                    Bài tập sắp đến hạn
                                </p>
                                <h3 className="text-3xl font-extrabold text-teal-700 mt-2">
                                    {data.upcomingAssignments.length}
                                </h3>
                                <p className="text-xs text-slate-500 mt-1">Cần theo dõi thu bài</p>
                            </div>
                            <div className="w-12 h-12 rounded-2xl bg-teal-50 text-teal-700 flex items-center justify-center">
                                <ClipboardList className="w-6 h-6" />
                            </div>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Section 1: Upcoming Assignments with Progress Bars */}
            <div className="space-y-4">
                <div className="flex items-center justify-between">
                    <div>
                        <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                            <ClipboardList className="w-5 h-5 text-teal-700" />
                            Tiến độ thu bài tập sắp đến hạn
                        </h2>
                        <p className="text-xs text-slate-500">
                            Tỷ lệ sinh viên đã nộp bài tập trên tổng số sinh viên trong từng lớp
                        </p>
                    </div>
                </div>

                {data.upcomingAssignments.length === 0 ? (
                    <EmptyState
                        icon={<CheckCircle2 className="w-10 h-10 text-emerald-500" />}
                        title="Không có bài tập sắp đến hạn"
                        description="Hiện tại không có bài tập nào gần đến hạn nộp trong các lớp học phần của bạn."
                    />
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {data.upcomingAssignments.map((assignment) => {
                            const percent = assignment.totalStudents > 0
                                ? Math.round((assignment.submittedCount / assignment.totalStudents) * 100)
                                : 0;
                            const isOverdue = new Date(assignment.dueAt) < new Date();

                            return (
                                <Card
                                    key={assignment.assignmentId}
                                    className="border-slate-200 shadow-sm hover:border-slate-300 transition-colors"
                                >
                                    <CardContent className="p-5 space-y-4">
                                        <div className="flex items-start justify-between gap-3">
                                            <div className="space-y-1">
                                                <Badge variant="outline" className="text-xs font-medium text-slate-600 bg-slate-50">
                                                    {assignment.className}
                                                </Badge>
                                                <h3 className="font-semibold text-sm text-slate-900 line-clamp-1">
                                                    {assignment.title}
                                                </h3>
                                            </div>
                                            <Badge
                                                variant={isOverdue ? "danger" : "secondary"}
                                                className="shrink-0 text-xs"
                                            >
                                                Hạn: {new Date(assignment.dueAt).toLocaleString("vi-VN", {
                                                    day: "2-digit",
                                                    month: "2-digit",
                                                    hour: "2-digit",
                                                    minute: "2-digit"
                                                })}
                                            </Badge>
                                        </div>

                                        {/* Progress bar */}
                                        <div className="space-y-1.5">
                                            <div className="flex items-center justify-between text-xs text-slate-600">
                                                <span>Đã nộp: <strong>{assignment.submittedCount}</strong> / {assignment.totalStudents} sinh viên</span>
                                                <span className="font-bold text-teal-700">{percent}%</span>
                                            </div>
                                            <div className="w-full h-2.5 bg-slate-100 rounded-full overflow-hidden">
                                                <div
                                                    className="h-full bg-teal-600 rounded-full transition-all duration-300"
                                                    style={{ width: `${Math.min(percent, 100)}%` }}
                                                />
                                            </div>
                                        </div>

                                        <div className="flex items-center justify-between pt-2 border-t border-slate-100">
                                            <span className="text-xs text-slate-500">
                                                Điểm tối đa: <strong>{assignment.maxScore}</strong>
                                            </span>
                                            <Button
                                                size="sm"
                                                variant="outline"
                                                onClick={() => navigate(`/assignments/${assignment.assignmentId}`)}
                                                className="text-xs gap-1.5 h-8"
                                            >
                                                Xem danh sách bài nộp
                                                <ArrowRight className="w-3.5 h-3.5" />
                                            </Button>
                                        </div>
                                    </CardContent>
                                </Card>
                            );
                        })}
                    </div>
                )}
            </div>

            {/* Section 2: Recent Tests Results */}
            <div className="space-y-4">
                <div className="flex items-center justify-between">
                    <div>
                        <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                            <FileText className="w-5 h-5 text-primary" />
                            Kết quả các bài kiểm tra gần đây
                        </h2>
                        <p className="text-xs text-slate-500">
                            Thống kê điểm số và số lượng sinh viên tham gia làm bài kiểm tra định thời
                        </p>
                    </div>
                </div>

                {data.recentTestResults.length === 0 ? (
                    <EmptyState
                        icon={<FileText className="w-10 h-10 text-slate-300" />}
                        title="Chưa có kết quả bài kiểm tra"
                        description="Chưa có bài kiểm tra nào được tổ chức gần đây trong các lớp bạn quản lý."
                    />
                ) : (
                    <Card className="border-slate-200 shadow-sm overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm text-left">
                                <thead className="bg-slate-50 text-slate-700 text-xs uppercase font-semibold border-b border-slate-200">
                                    <tr>
                                        <th className="py-3 px-4">Bài kiểm tra</th>
                                        <th className="py-3 px-4">Lớp học</th>
                                        <th className="py-3 px-4">Thời gian</th>
                                        <th className="py-3 px-4 text-center">Đã nộp</th>
                                        <th className="py-3 px-4 text-center">Điểm TB</th>
                                        <th className="py-3 px-4 text-right">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-100">
                                    {data.recentTestResults.map((test) => (
                                        <tr key={test.testId} className="hover:bg-slate-50/80 transition-colors">
                                            <td className="py-3.5 px-4 font-medium text-slate-900">
                                                {test.title}
                                            </td>
                                            <td className="py-3.5 px-4 text-slate-600">
                                                <Badge variant="outline" className="font-normal text-xs">
                                                    {test.className}
                                                </Badge>
                                            </td>
                                            <td className="py-3.5 px-4 text-xs text-slate-500">
                                                {new Date(test.startAt).toLocaleDateString("vi-VN", {
                                                    day: "2-digit",
                                                    month: "2-digit"
                                                })} - {new Date(test.endAt).toLocaleDateString("vi-VN", {
                                                    day: "2-digit",
                                                    month: "2-digit"
                                                })}
                                            </td>
                                            <td className="py-3.5 px-4 text-center">
                                                <span className="font-semibold text-slate-800">
                                                    {test.submittedStudents}
                                                </span>
                                                <span className="text-slate-400">/{test.totalStudents}</span>
                                            </td>
                                            <td className="py-3.5 px-4 text-center">
                                                <Badge
                                                    variant={test.averageScore >= 7.0 ? "success" : test.averageScore >= 5.0 ? "warning" : "danger"}
                                                    className="font-bold text-xs"
                                                >
                                                    {test.averageScore.toFixed(1)} / 10
                                                </Badge>
                                            </td>
                                            <td className="py-3.5 px-4 text-right">
                                                <div className="flex items-center justify-end gap-2">
                                                    <Button
                                                        size="sm"
                                                        variant="ghost"
                                                        onClick={() => navigate(`/classes/${test.classId}`)}
                                                        className="text-xs h-8 text-slate-600"
                                                    >
                                                        Vào lớp
                                                    </Button>
                                                    <Button
                                                        size="sm"
                                                        variant="outline"
                                                        onClick={() => navigate(`/tests/${test.testId}/results`)}
                                                        className="text-xs h-8 gap-1 text-primary border-primary/20 hover:bg-primary/5"
                                                    >
                                                        <BarChart3 className="w-3.5 h-3.5" />
                                                        Xem bảng điểm
                                                    </Button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </Card>
                )}
            </div>
        </div>
    );
};

export default TeacherDashboardPage;
