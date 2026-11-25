import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
    BarChart3,
    ArrowLeft,
    Search,
    Award,
    Users,
    TrendingUp,
    TrendingDown,
    Clock,
    CheckCircle2
} from "lucide-react";
import testService from "../services/testService";
import { ClassTestResultsResponse, StudentTestResult } from "../types";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { EmptyState } from "../components/common/EmptyState";

export const ClassTestResultsPage: React.FC = () => {
    const { testId } = useParams<{ testId: string }>();
    const numericTestId = Number(testId);
    const navigate = useNavigate();

    const [results, setResults] = useState<ClassTestResultsResponse | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState<string>("");

    const fetchResults = async () => {
        if (!numericTestId) return;
        try {
            setIsLoading(true);
            const data = await testService.getResults(numericTestId);
            setResults(data);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải kết quả bài kiểm tra.");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        void fetchResults();
    }, [numericTestId]);

    if (isLoading) {
        return <LoadingState label="Đang tổng hợp bảng điểm bài kiểm tra..." />;
    }

    if (error || !results) {
        return (
            <div className="max-w-2xl mx-auto p-6 space-y-4">
                <ErrorBanner
                    title="Lỗi tải kết quả"
                    message={error || "Không tìm thấy dữ liệu kết quả bài kiểm tra."}
                    onRetry={fetchResults}
                />
                <Button
                    variant="outline"
                    onClick={() => navigate(-1)}
                    className="gap-2"
                >
                    <ArrowLeft className="w-4 h-4" />
                    Quay lại
                </Button>
            </div>
        );
    }

    const filteredStudents = results.studentResults.filter((s) =>
        s.studentName.toLowerCase().includes(searchQuery.toLowerCase())
    );

    const submissionPercent = results.totalStudents > 0
        ? Math.round((results.submittedCount / results.totalStudents) * 100)
        : 0;

    return (
        <div className="space-y-6 p-6 max-w-6xl mx-auto">
            {/* Header Navigation */}
            <div className="space-y-3 pb-4 border-b border-slate-200">
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => navigate(-1)}
                    className="gap-1.5 text-xs text-slate-500 hover:text-slate-800 -ml-2 h-8"
                >
                    <ArrowLeft className="w-4 h-4" />
                    Quay lại lớp học
                </Button>

                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <div>
                        <div className="flex items-center gap-2">
                            <span className="text-xs font-semibold text-primary uppercase tracking-wider">
                                Bảng điểm tổng hợp
                            </span>
                            <Badge variant="outline" className="text-xs">
                                Bài kiểm tra định thời
                            </Badge>
                        </div>
                        <h1 className="text-2xl font-bold text-slate-900 tracking-tight mt-1">
                            {results.title}
                        </h1>
                    </div>
                </div>
            </div>

            {/* KPI Metrics */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <Card className="border-slate-200 shadow-sm">
                    <CardContent className="p-5">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-xs font-semibold text-slate-500 uppercase">Tỷ lệ đã nộp</p>
                                <h3 className="text-2xl font-extrabold text-slate-900 mt-1">
                                    {results.submittedCount} <span className="text-sm font-normal text-slate-400">/ {results.totalStudents}</span>
                                </h3>
                                <p className="text-xs text-teal-700 font-bold mt-0.5">{submissionPercent}% sinh viên</p>
                            </div>
                            <div className="w-10 h-10 rounded-xl bg-teal-50 text-teal-700 flex items-center justify-center">
                                <Users className="w-5 h-5" />
                            </div>
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-slate-200 shadow-sm">
                    <CardContent className="p-5">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-xs font-semibold text-slate-500 uppercase">Điểm trung bình</p>
                                <h3 className="text-2xl font-extrabold text-primary mt-1">
                                    {results.averageScore.toFixed(1)} <span className="text-sm font-normal text-slate-400">/ 10</span>
                                </h3>
                                <p className="text-xs text-slate-500 mt-0.5">Toàn bộ lớp</p>
                            </div>
                            <div className="w-10 h-10 rounded-xl bg-blue-50 text-primary flex items-center justify-center">
                                <BarChart3 className="w-5 h-5" />
                            </div>
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-slate-200 shadow-sm">
                    <CardContent className="p-5">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-xs font-semibold text-slate-500 uppercase">Điểm cao nhất</p>
                                <h3 className="text-2xl font-extrabold text-emerald-600 mt-1">
                                    {results.highestScore.toFixed(1)} <span className="text-sm font-normal text-slate-400">/ 10</span>
                                </h3>
                                <p className="text-xs text-emerald-600 font-medium mt-0.5">Thành tích cao</p>
                            </div>
                            <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
                                <TrendingUp className="w-5 h-5" />
                            </div>
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-slate-200 shadow-sm">
                    <CardContent className="p-5">
                        <div className="flex items-center justify-between">
                            <div>
                                <p className="text-xs font-semibold text-slate-500 uppercase">Điểm thấp nhất</p>
                                <h3 className="text-2xl font-extrabold text-rose-600 mt-1">
                                    {results.lowestScore.toFixed(1)} <span className="text-sm font-normal text-slate-400">/ 10</span>
                                </h3>
                                <p className="text-xs text-rose-600 font-medium mt-0.5">Cần hỗ trợ ôn tập</p>
                            </div>
                            <div className="w-10 h-10 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center">
                                <TrendingDown className="w-5 h-5" />
                            </div>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Students Table with Search */}
            <div className="space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                        <Users className="w-5 h-5 text-primary" />
                        Danh sách điểm sinh viên ({filteredStudents.length})
                    </h2>

                    <div className="relative w-full sm:w-72">
                        <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                        <Input
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            placeholder="Tìm kiếm theo tên sinh viên..."
                            className="pl-9 text-xs"
                        />
                    </div>
                </div>

                {filteredStudents.length === 0 ? (
                    <EmptyState
                        icon={<Users className="w-10 h-10 text-slate-300" />}
                        title="Không tìm thấy sinh viên nào"
                        description={searchQuery ? "Không có kết quả khớp với tên tìm kiếm." : "Chưa có sinh viên nào hoàn thành bài kiểm tra."}
                    />
                ) : (
                    <Card className="border-slate-200 shadow-sm overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm text-left">
                                <thead className="bg-slate-50 text-slate-700 text-xs uppercase font-semibold border-b border-slate-200">
                                    <tr>
                                        <th className="py-3 px-4">Sinh viên</th>
                                        <th className="py-3 px-4 text-center">Số lượt làm</th>
                                        <th className="py-3 px-4 text-center">Điểm cao nhất</th>
                                        <th className="py-3 px-4">Thời điểm nộp cuối</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-100">
                                    {filteredStudents.map((st) => (
                                        <tr key={st.studentId} className="hover:bg-slate-50/80 transition-colors">
                                            <td className="py-3.5 px-4 font-semibold text-slate-900">
                                                {st.studentName}
                                            </td>
                                            <td className="py-3.5 px-4 text-center text-xs text-slate-600">
                                                <Badge variant="outline" className="font-semibold">
                                                    {st.attemptsUsed} lần
                                                </Badge>
                                            </td>
                                            <td className="py-3.5 px-4 text-center">
                                                <Badge
                                                    variant={st.bestScore >= 7.0 ? "success" : st.bestScore >= 5.0 ? "warning" : "danger"}
                                                    className="text-xs font-bold px-2.5 py-0.5"
                                                >
                                                    {st.bestScore.toFixed(1)} / 10
                                                </Badge>
                                            </td>
                                            <td className="py-3.5 px-4 text-xs text-slate-500">
                                                {new Date(st.lastSubmittedAt).toLocaleString("vi-VN", {
                                                    day: "2-digit",
                                                    month: "2-digit",
                                                    hour: "2-digit",
                                                    minute: "2-digit"
                                                })}
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

export default ClassTestResultsPage;
