import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
    AlertTriangle,
    Award,
    BookOpen,
    CheckCircle,
    FileText,
    GraduationCap,
    TrendingUp
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
import analyticsService from "../services/analyticsService";
import courseService from "../services/courseService";
import { AnalyticsOverview, Course, ReviewRecommendation } from "../types";
import LoadingSpinner from "../components/common/LoadingSpinner";

export const DashboardPage: React.FC = () => {
    const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
    const [recommendations, setRecommendations] = useState<ReviewRecommendation[]>([]);
    const [courseChartData, setCourseChartData] = useState<{ name: string; score: number }[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchDashboardData = async () => {
            try {
                setIsLoading(true);
                const [overviewData, recsData, coursesData] = await Promise.all([
                    analyticsService.getOverview(),
                    analyticsService.getReviewRecommendations(),
                    courseService.getCourses()
                ]);

                setOverview(overviewData);
                setRecommendations(recsData);

                // Load score data for each course to build chart
                const chartPromises = coursesData.map(async (course: Course) => {
                    try {
                        const ca = await analyticsService.getCourseAnalytics(course.id);
                        return {
                            name: course.title.length > 15 ? course.title.substring(0, 15) + "..." : course.title,
                            score: ca.averageScore !== null ? ca.averageScore : 0
                        };
                    } catch {
                        return {
                            name: course.title,
                            score: 0
                        };
                    }
                });

                const chartResults = await Promise.all(chartPromises);
                setCourseChartData(chartResults);
            } catch (err: unknown) {
                if (err instanceof Error) {
                    setError(err.message);
                } else {
                    setError("Không thể tải dữ liệu bảng điều khiển.");
                }
            } finally {
                setIsLoading(false);
            }
        };

        fetchDashboardData();
    }, []);

    if (isLoading) {
        return <LoadingSpinner size="lg" text="Đang tải dữ liệu tổng quan..." className="py-20" />;
    }

    if (error) {
        return (
            <div className="p-6 rounded-2xl bg-rose-50 border border-rose-200 text-rose-700">
                <p className="font-semibold">Lỗi tải dữ liệu</p>
                <p className="text-sm mt-1">{error}</p>
            </div>
        );
    }

    const kpiCards = [
        {
            label: "Khóa học",
            value: overview?.totalCourses || 0,
            icon: BookOpen,
            color: "text-blue-600",
            bg: "bg-blue-50",
            border: "border-blue-100"
        },
        {
            label: "Tài liệu học tập",
            value: overview?.totalMaterials || 0,
            icon: FileText,
            color: "text-indigo-600",
            bg: "bg-indigo-50",
            border: "border-indigo-100"
        },
        {
            label: "Bài Quiz đã làm",
            value: overview?.totalQuizzesTaken || 0,
            icon: Award,
            color: "text-amber-600",
            bg: "bg-amber-50",
            border: "border-amber-100"
        },
        {
            label: "Điểm trung bình",
            value: `${overview?.averageScore || 0}/100`,
            icon: TrendingUp,
            color: "text-emerald-600",
            bg: "bg-emerald-50",
            border: "border-emerald-100"
        },
        {
            label: "Tỷ lệ làm đúng",
            value: `${overview?.accuracyRate || 0}%`,
            icon: CheckCircle,
            color: "text-purple-600",
            bg: "bg-purple-50",
            border: "border-purple-100"
        }
    ];

    return (
        <div className="space-y-8">
            {/* Top Welcome Banner */}
            <div className="p-6 rounded-2xl bg-gradient-to-r from-indigo-700 to-indigo-900 text-white shadow-lg shadow-indigo-900/10 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <h2 className="text-xl md:text-2xl font-bold tracking-tight">Chào mừng bạn quay trở lại! 🎓</h2>
                    <p className="text-indigo-100 text-sm mt-1">
                        Hãy tiếp tục chu trình học tập: Đọc tài liệu → Tóm tắt AI → Hỏi đáp thông minh → Luyện tập Quiz.
                    </p>
                </div>
                <Link
                    to="/courses"
                    className="self-start md:self-auto px-4 py-2.5 rounded-xl bg-white text-indigo-700 hover:bg-indigo-50 font-semibold text-sm transition shadow-sm"
                >
                    Vào học ngay
                </Link>
            </div>

            {/* KPI Cards Grid */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
                {kpiCards.map((card, idx) => {
                    const Icon = card.icon;
                    return (
                        <div
                            key={idx}
                            className={`p-5 rounded-2xl bg-white border ${card.border} shadow-sm transition hover:shadow-md`}
                        >
                            <div className="flex items-center justify-between">
                                <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
                                    {card.label}
                                </span>
                                <div className={`w-9 h-9 rounded-xl ${card.bg} ${card.color} flex items-center justify-center`}>
                                    <Icon className="w-5 h-5" />
                                </div>
                            </div>
                            <div className="mt-3">
                                <span className="text-2xl font-bold text-slate-800">{card.value}</span>
                            </div>
                        </div>
                    );
                })}
            </div>

            {/* Chart and Recommendations Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                {/* Score Chart */}
                <div className="lg:col-span-2 bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col">
                    <div className="flex items-center justify-between mb-6">
                        <div>
                            <h3 className="text-base font-bold text-slate-800">Điểm trung bình theo Khóa học</h3>
                            <p className="text-xs text-slate-500 mt-0.5">So sánh kết quả làm bài trắc nghiệm giữa các môn</p>
                        </div>
                    </div>

                    <div className="h-72 w-full">
                        {courseChartData.length > 0 ? (
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={courseChartData} margin={{ top: 10, right: 10, left: -20, bottom: 20 }}>
                                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                                    <XAxis dataKey="name" stroke="#94a3b8" fontSize={11} tickLine={false} />
                                    <YAxis domain={[0, 100]} stroke="#94a3b8" fontSize={11} tickLine={false} />
                                    <Tooltip
                                        contentStyle={{
                                            backgroundColor: "#1e293b",
                                            borderRadius: "12px",
                                            border: "none",
                                            color: "#fff",
                                            fontSize: "12px"
                                        }}
                                        formatter={(val: number) => [`${val} điểm`, "Điểm TB"]}
                                    />
                                    <Bar dataKey="score" fill="#6366f1" radius={[6, 6, 0, 0]} maxBarSize={45} />
                                </BarChart>
                            </ResponsiveContainer>
                        ) : (
                            <div className="h-full flex items-center justify-center text-sm text-slate-400">
                                Chưa có khóa học nào để hiển thị biểu đồ
                            </div>
                        )}
                    </div>
                </div>

                {/* Review Recommendations List */}
                <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col">
                    <div className="flex items-center gap-2 mb-4">
                        <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
                            <AlertTriangle className="w-4 h-4" />
                        </div>
                        <div>
                            <h3 className="text-base font-bold text-slate-800">Chủ đề cần ôn tập</h3>
                            <p className="text-xs text-slate-500">Dựa trên các câu hỏi bạn trả lời sai</p>
                        </div>
                    </div>

                    <div className="flex-1 overflow-y-auto space-y-3.5 pr-1">
                        {recommendations.length > 0 ? (
                            recommendations.map((rec, index) => (
                                <div
                                    key={index}
                                    className="p-4 rounded-xl border border-amber-200/70 bg-amber-50/50 hover:bg-amber-50 transition"
                                >
                                    <div className="flex items-center justify-between mb-1">
                                        <span className="text-xs font-semibold text-amber-900 line-clamp-1">
                                            {rec.courseTitle}
                                        </span>
                                        <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 font-medium shrink-0">
                                            {rec.reason}
                                        </span>
                                    </div>
                                    <p className="text-xs text-slate-600 italic line-clamp-2 mt-1">
                                        &ldquo;{rec.sampleQuestion}&rdquo;
                                    </p>
                                    <div className="mt-2.5 pt-2 border-t border-amber-200/40 flex items-center justify-between">
                                        <span className="text-[11px] text-slate-500 line-clamp-1">{rec.suggestedAction}</span>
                                        <Link
                                            to={`/courses/${rec.courseId}`}
                                            className="text-xs font-semibold text-indigo-600 hover:text-indigo-800 shrink-0 ml-2"
                                        >
                                            Ôn tập →
                                        </Link>
                                    </div>
                                </div>
                            ))
                        ) : (
                            <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-400">
                                <GraduationCap className="w-12 h-12 text-slate-300 mb-2" />
                                <p className="text-sm font-medium text-slate-600">Phong độ học tập xuất sắc!</p>
                                <p className="text-xs text-slate-400 mt-1">
                                    Không có câu hỏi hay chủ đề nào bị tỷ lệ đúng dưới 60%.
                                </p>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default DashboardPage;
