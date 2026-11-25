import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
    AlertCircle,
    AlertTriangle,
    Award,
    BookOpen,
    Calendar,
    CheckCircle2,
    Clock,
    FileText,
    GraduationCap,
    Layers,
    Play,
    Sparkles,
    Target,
    TrendingUp,
    Zap
} from "lucide-react";
import {
    Area,
    AreaChart,
    CartesianGrid,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis
} from "recharts";
import analyticsService from "../services/analyticsService";
import {
    AnalyticsOverview,
    FrequentMistake,
    ReviewTopic,
    ScoreTrendItem,
    StudentDashboardData
} from "../types";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";

export const DashboardPage: React.FC = () => {
    const navigate = useNavigate();

    const [dashboard, setDashboard] = useState<StudentDashboardData | null>(null);
    const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
    const [scoreTrend, setScoreTrend] = useState<ScoreTrendItem[]>([]);
    const [frequentMistakes, setFrequentMistakes] = useState<FrequentMistake[]>([]);
    const [reviewTopics, setReviewTopics] = useState<ReviewTopic[]>([]);

    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    const fetchAllDashboardData = async () => {
        try {
            setIsLoading(true);
            const [dashData, overviewData, trendData, mistakesData, recsData] = await Promise.all([
                analyticsService.getStudentDashboard(),
                analyticsService.getOverview(),
                analyticsService.getScoreTrend(),
                analyticsService.getFrequentMistakes(undefined, 5),
                analyticsService.getReviewTopics()
            ]);

            setDashboard(dashData);
            setOverview(overviewData);
            setScoreTrend(trendData);
            setFrequentMistakes(mistakesData);
            setReviewTopics(recsData);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải dữ liệu bảng điều khiển.");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        void fetchAllDashboardData();
    }, []);

    if (isLoading) {
        return <LoadingState text="Đang đồng bộ dữ liệu tổng quan học tập..." />;
    }

    if (error) {
        return (
            <ErrorBanner
                title="Lỗi tải tổng quan"
                message={error}
                onRetry={fetchAllDashboardData}
            />
        );
    }

    const chartData = scoreTrend.map((item, idx) => ({
        index: idx + 1,
        date: new Date(item.submittedAt).toLocaleDateString("vi-VN", {
            month: "numeric",
            day: "numeric"
        }),
        score: item.score,
        quizTitle: item.quizTitle
    }));

    return (
        <div className="space-y-8 pb-12">
            {/* Top Welcome Banner */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
                        Tổng quan học tập
                    </h1>
                    <p className="text-sm text-slate-500 mt-1">
                        Theo dõi tiến độ học tập, bài thi trắc nghiệm và các chủ đề AI gợi ý ôn tập
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <Button
                        variant="outline"
                        onClick={() => navigate("/flashcards")}
                        className="gap-2"
                    >
                        <Layers className="w-4 h-4 text-emerald-600" />
                        <span>Ôn Flashcards</span>
                    </Button>
                    <Button onClick={() => navigate("/courses")} className="gap-2">
                        <BookOpen className="w-4 h-4" />
                        <span>Khóa học của tôi</span>
                    </Button>
                </div>
            </div>

            {/* KPI Cards */}
            <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
                <Card className="border-slate-200 shadow-sm">
                    <CardContent className="p-4 space-y-2">
                        <div className="w-8 h-8 rounded-lg bg-blue-50 text-primary flex items-center justify-center">
                            <BookOpen className="w-4 h-4" />
                        </div>
                        <div>
                            <p className="text-xs text-slate-500 font-medium">Khóa học</p>
                            <p className="text-xl font-bold text-slate-900 mt-0.5">
                                {overview?.totalCourses || 0}
                            </p>
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-slate-200 shadow-sm">
                    <CardContent className="p-4 space-y-2">
                        <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
                            <FileText className="w-4 h-4" />
                        </div>
                        <div>
                            <p className="text-xs text-slate-500 font-medium">Tài liệu đã học</p>
                            <p className="text-xl font-bold text-slate-900 mt-0.5">
                                {overview?.totalMaterials || 0}
                            </p>
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-slate-200 shadow-sm">
                    <CardContent className="p-4 space-y-2">
                        <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                            <Award className="w-4 h-4" />
                        </div>
                        <div>
                            <p className="text-xs text-slate-500 font-medium">Lượt làm Quiz</p>
                            <p className="text-xl font-bold text-slate-900 mt-0.5">
                                {overview?.totalAttempts || 0}
                            </p>
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-slate-200 shadow-sm">
                    <CardContent className="p-4 space-y-2">
                        <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                            <TrendingUp className="w-4 h-4" />
                        </div>
                        <div>
                            <p className="text-xs text-slate-500 font-medium">Điểm trung bình</p>
                            <p className="text-xl font-bold text-slate-900 mt-0.5">
                                {overview?.averageScore || 0}/{overview?.maxScoreScale || 10}
                            </p>
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-slate-200 shadow-sm col-span-2 md:col-span-1">
                    <CardContent className="p-4 space-y-2">
                        <div className="w-8 h-8 rounded-lg bg-sky-50 text-sky-600 flex items-center justify-center">
                            <Zap className="w-4 h-4" />
                        </div>
                        <div>
                            <p className="text-xs text-slate-500 font-medium">Độ chính xác</p>
                            <p className="text-xl font-bold text-slate-900 mt-0.5">
                                {overview?.accuracyRate || 0}%
                            </p>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Score Trend Chart */}
            <Card className="border-slate-200 shadow-sm">
                <CardHeader className="pb-2">
                    <CardTitle className="text-base font-bold text-slate-900 flex items-center gap-2">
                        <TrendingUp className="w-4 h-4 text-primary" />
                        <span>Xu hướng điểm số theo thời gian</span>
                    </CardTitle>
                </CardHeader>
                <CardContent className="p-6">
                    {chartData.length === 0 ? (
                        <div className="h-64 flex flex-col items-center justify-center text-slate-400 text-xs">
                            <p>Chưa có dữ liệu lịch sử làm bài để vẽ biểu đồ.</p>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => navigate("/courses")}
                                className="mt-3 text-xs"
                            >
                                Bắt đầu làm bài Quiz
                            </Button>
                        </div>
                    ) : (
                        <div className="h-72 w-full">
                            <ResponsiveContainer width="100%" height="100%">
                                <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                                    <defs>
                                        <linearGradient id="scoreColor" x1="0" y1="0" x2="0" y2="1">
                                            <stop offset="5%" stopColor="#2563EB" stopOpacity={0.4} />
                                            <stop offset="95%" stopColor="#2563EB" stopOpacity={0.0} />
                                        </linearGradient>
                                    </defs>
                                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E2E8F0" />
                                    <XAxis dataKey="date" stroke="#94A3B8" fontSize={11} tickLine={false} />
                                    <YAxis stroke="#94A3B8" fontSize={11} tickLine={false} domain={[0, 10]} />
                                    <Tooltip
                                        content={({ active, payload }) => {
                                            if (active && payload && payload.length) {
                                                const data = payload[0].payload as {
                                                    score: number;
                                                    quizTitle: string;
                                                    date: string;
                                                };
                                                return (
                                                    <div className="bg-white p-3 rounded-xl border border-slate-200 shadow-elevated text-xs space-y-1">
                                                        <p className="font-semibold text-slate-900">{data.quizTitle}</p>
                                                        <p className="text-primary font-bold">Điểm số: {data.score}/10</p>
                                                        <p className="text-slate-400 text-[10px]">{data.date}</p>
                                                    </div>
                                                );
                                            }
                                            return null;
                                        }}
                                    />
                                    <Area
                                        type="monotone"
                                        dataKey="score"
                                        stroke="#2563EB"
                                        strokeWidth={2.5}
                                        fillOpacity={1}
                                        fill="url(#scoreColor)"
                                    />
                                </AreaChart>
                            </ResponsiveContainer>
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* 2-Columns: AI Recommendations & Frequent Mistakes */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
                {/* Left: Review Topics Suggested by AI */}
                <Card className="border-slate-200 shadow-sm">
                    <CardHeader className="pb-3 border-b border-slate-100">
                        <CardTitle className="text-base font-bold text-slate-900 flex items-center gap-2">
                            <Sparkles className="w-4 h-4 text-amber-500" />
                            <span>Chủ đề nên ôn tập (AI gợi ý)</span>
                        </CardTitle>
                    </CardHeader>
                    <CardContent className="p-5 space-y-3">
                        {reviewTopics.length === 0 ? (
                            <p className="text-xs text-slate-400 py-6 text-center">
                                Bạn chưa có câu hỏi sai đáng kể. Hãy tiếp tục duy trì phong độ!
                            </p>
                        ) : (
                            reviewTopics.map((item, idx) => (
                                <div
                                    key={idx}
                                    className="p-3.5 rounded-xl border border-slate-200 bg-white hover:border-primary/40 transition space-y-2"
                                >
                                    <div className="flex items-start justify-between gap-2">
                                        <div>
                                            <h4 className="font-semibold text-slate-900 text-xs">
                                                {item.topicTitle}
                                            </h4>
                                            <p className="text-[11px] text-slate-500 mt-0.5">
                                                Môn: {item.courseName}
                                            </p>
                                        </div>
                                        <Badge
                                            variant={
                                                item.confidence === "HIGH"
                                                    ? "danger"
                                                    : item.confidence === "MEDIUM"
                                                    ? "warning"
                                                    : "info"
                                            }
                                            className="text-[10px] shrink-0"
                                        >
                                            {item.confidence === "HIGH"
                                                ? "Cần ôn gấp"
                                                : item.confidence === "MEDIUM"
                                                ? "Nên xem lại"
                                                : "Lưu ý"}
                                        </Badge>
                                    </div>
                                    <p className="text-xs text-slate-600 leading-relaxed bg-slate-50 p-2 rounded-lg">
                                        {item.reason}
                                    </p>
                                    {item.suggestedAction && (
                                        <p className="text-[11px] font-medium text-primary">
                                            👉 {item.suggestedAction}
                                        </p>
                                    )}
                                </div>
                            ))
                        )}
                    </CardContent>
                </Card>

                {/* Right: Frequent Mistakes */}
                <Card className="border-slate-200 shadow-sm">
                    <CardHeader className="pb-3 border-b border-slate-100">
                        <CardTitle className="text-base font-bold text-slate-900 flex items-center gap-2">
                            <AlertTriangle className="w-4 h-4 text-rose-500" />
                            <span>Câu hỏi hay làm sai</span>
                        </CardTitle>
                    </CardHeader>
                    <CardContent className="p-5 space-y-3">
                        {frequentMistakes.length === 0 ? (
                            <p className="text-xs text-slate-400 py-6 text-center">
                                Chưa ghi nhận câu hỏi sai lặp lại nào.
                            </p>
                        ) : (
                            frequentMistakes.map((mistake) => (
                                <div
                                    key={mistake.questionId}
                                    className="p-3.5 rounded-xl border border-rose-100 bg-rose-50/40 space-y-2"
                                >
                                    <div className="flex items-start justify-between gap-2">
                                        <p className="text-xs font-semibold text-slate-900 line-clamp-2">
                                            {mistake.questionContent}
                                        </p>
                                        <span className="text-[11px] font-bold text-rose-600 shrink-0 px-2 py-0.5 rounded bg-rose-100/60">
                                            Sai {mistake.wrongCount}/{mistake.totalAttempts} lần
                                        </span>
                                    </div>
                                    <div className="flex items-center gap-3 text-[11px] text-slate-500">
                                        <span>Môn: {mistake.courseName}</span>
                                        <span>•</span>
                                        <span>Độ chính xác: {mistake.accuracyRate}%</span>
                                    </div>
                                </div>
                            ))
                        )}
                    </CardContent>
                </Card>
            </div>
        </div>
    );
};

export default DashboardPage;
