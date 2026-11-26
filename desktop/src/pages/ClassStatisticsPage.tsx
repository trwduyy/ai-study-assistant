import React, { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    BarChart3,
    ClipboardList,
    FileText,
    GraduationCap,
    Users
} from "lucide-react";
import {
    Bar,
    BarChart,
    CartesianGrid,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis
} from "recharts";
import classroomService from "../services/classroomService";
import dashboardService from "../services/dashboardService";
import { Classroom, TeacherDashboardResponse } from "../types";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { EmptyState } from "../components/common/EmptyState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { LoadingState } from "../components/common/LoadingState";

export const ClassStatisticsPage: React.FC = () => {
    const navigate = useNavigate();
    const [dashboard, setDashboard] = useState<TeacherDashboardResponse | null>(null);
    const [classes, setClasses] = useState<Classroom[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    const fetchStats = useCallback(async (): Promise<void> => {
        try {
            setIsLoading(true);
            const [dashData, clsRes] = await Promise.all([
                dashboardService.getTeacherDashboard(),
                classroomService.getMyClassrooms({ page: 0, size: 50 })
            ]);
            setDashboard(dashData);
            setClasses(clsRes.items);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải dữ liệu thống kê lớp.");
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        void fetchStats();
    }, [fetchStats]);

    if (isLoading) {
        return <LoadingState label="Đang tổng hợp số liệu thống kê lớp học..." />;
    }

    if (error || !dashboard) {
        return (
            <ErrorBanner
                title="Lỗi tải thống kê lớp"
                message={error || "Không thể tổng hợp dữ liệu."}
                onRetry={() => void fetchStats()}
            />
        );
    }

    const totalStudentsAcrossClasses = classes.reduce((sum, c) => sum + c.memberCount, 0);
    const testChartData = dashboard.recentTestResults.map((t) => ({
        name: t.title.length > 22 ? `${t.title.slice(0, 20)}…` : t.title,
        className: t.className,
        averageScore: Number(t.averageScore.toFixed(2)),
        submittedStudents: t.submittedStudents
    }));

    return (
        <div className="space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2.5">
                        <BarChart3 className="h-6 w-6 text-primary" />
                        <span>Thống kê Lớp học & Kết quả</span>
                    </h1>
                    <p className="text-sm text-muted-foreground mt-1">
                        Theo dõi sĩ số, tiến độ nộp bài tập và điểm trung bình kiểm tra theo từng lớp phụ trách.
                    </p>
                </div>
            </div>

            {/* Summary KPIs */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <Card className="border-border shadow-sm">
                    <CardContent className="p-5 flex items-center justify-between">
                        <div>
                            <p className="text-xs font-semibold uppercase text-muted-foreground">
                                Tổng số lớp đang dạy
                            </p>
                            <p className="text-3xl font-extrabold text-slate-900 mt-1">
                                {dashboard.totalClasses}
                            </p>
                        </div>
                        <div className="h-11 w-11 rounded-xl bg-blue-50 text-primary flex items-center justify-center">
                            <GraduationCap className="h-6 w-6" />
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-border shadow-sm">
                    <CardContent className="p-5 flex items-center justify-between">
                        <div>
                            <p className="text-xs font-semibold uppercase text-muted-foreground">
                                Tổng thành viên tham gia
                            </p>
                            <p className="text-3xl font-extrabold text-teal-700 mt-1">
                                {totalStudentsAcrossClasses}
                            </p>
                        </div>
                        <div className="h-11 w-11 rounded-xl bg-teal-50 text-teal-700 flex items-center justify-center">
                            <Users className="h-6 w-6" />
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-border shadow-sm">
                    <CardContent className="p-5 flex items-center justify-between">
                        <div>
                            <p className="text-xs font-semibold uppercase text-muted-foreground">
                                Bài kiểm tra gần đây
                            </p>
                            <p className="text-3xl font-extrabold text-amber-600 mt-1">
                                {dashboard.recentTestResults.length}
                            </p>
                        </div>
                        <div className="h-11 w-11 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">
                            <FileText className="h-6 w-6" />
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Chart: Average Test Scores */}
            <Card className="border-border shadow-sm">
                <CardHeader className="pb-2 border-b border-border">
                    <CardTitle className="text-base font-bold text-slate-900">
                        Điểm trung bình các bài kiểm tra gần đây
                    </CardTitle>
                </CardHeader>
                <CardContent className="p-6">
                    {testChartData.length === 0 ? (
                        <EmptyState
                            icon={<BarChart3 className="h-10 w-10 text-slate-300" />}
                            title="Chưa có dữ liệu điểm kiểm tra"
                            description="Biểu đồ sẽ hiển thị khi sinh viên hoàn thành các bài kiểm tra định thời."
                        />
                    ) : (
                        <div className="h-72 w-full">
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={testChartData} margin={{ top: 10, right: 20, left: -10, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E2E8F0" />
                                    <XAxis dataKey="name" stroke="#64748B" fontSize={11} />
                                    <YAxis stroke="#64748B" fontSize={11} domain={[0, 10]} />
                                    <Tooltip
                                        formatter={(value) => [`${value} / 10`, "Điểm TB"]}
                                    />
                                    <Bar dataKey="averageScore" fill="#2563EB" radius={[6, 6, 0, 0]} maxBarSize={48} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Assignment Submission Rates Table */}
            <Card className="border-border shadow-sm overflow-hidden">
                <CardHeader className="pb-3 border-b border-border">
                    <CardTitle className="text-base font-bold text-slate-900 flex items-center gap-2">
                        <ClipboardList className="h-4 w-4 text-teal-700" />
                        <span>Tỷ lệ nộp bài tập theo lớp</span>
                    </CardTitle>
                </CardHeader>
                <CardContent className="p-0">
                    {dashboard.upcomingAssignments.length === 0 ? (
                        <div className="p-8 text-center text-xs text-muted-foreground">
                            Chưa có bài tập nào đang theo dõi.
                        </div>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm text-left">
                                <thead className="bg-slate-50 text-slate-700 text-xs uppercase font-semibold border-b border-border">
                                    <tr>
                                        <th className="py-3 px-4">Bài tập</th>
                                        <th className="py-3 px-4">Lớp</th>
                                        <th className="py-3 px-4 text-center">Đã nộp / Sĩ số</th>
                                        <th className="py-3 px-4">Tỷ lệ</th>
                                        <th className="py-3 px-4 text-right">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border">
                                    {dashboard.upcomingAssignments.map((a) => {
                                        const rate =
                                            a.totalStudents > 0
                                                ? Math.round((a.submittedCount / a.totalStudents) * 100)
                                                : 0;
                                        return (
                                            <tr key={a.assignmentId} className="hover:bg-slate-50/70">
                                                <td className="py-3 px-4 font-semibold text-slate-900">
                                                    {a.title}
                                                </td>
                                                <td className="py-3 px-4">
                                                    <Badge variant="outline" className="text-xs">
                                                        {a.className}
                                                    </Badge>
                                                </td>
                                                <td className="py-3 px-4 text-center text-xs font-semibold">
                                                    {a.submittedCount} / {a.totalStudents}
                                                </td>
                                                <td className="py-3 px-4 w-48">
                                                    <div className="flex items-center gap-2">
                                                        <div className="flex-1 h-2 bg-slate-100 rounded-full overflow-hidden">
                                                            <div
                                                                className="h-full bg-teal-600 rounded-full"
                                                                style={{ width: `${Math.min(rate, 100)}%` }}
                                                            />
                                                        </div>
                                                        <span className="text-xs font-bold text-teal-700">
                                                            {rate}%
                                                        </span>
                                                    </div>
                                                </td>
                                                <td className="py-3 px-4 text-right">
                                                    <Button
                                                        size="sm"
                                                        variant="outline"
                                                        onClick={() =>
                                                            navigate(`/assignments/${a.assignmentId}`)
                                                        }
                                                        className="h-8 text-xs"
                                                    >
                                                        Chi tiết
                                                    </Button>
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
        </div>
    );
};

export default ClassStatisticsPage;
