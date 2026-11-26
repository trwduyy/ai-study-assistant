import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    FileText,
    Clock,
    Search,
    BookOpen,
    ArrowRight,
    BarChart3,
    Calendar,
    Users
} from "lucide-react";
import { classroomService } from "../services/classroomService";
import { testService } from "../services/testService";
import { ClassTest } from "../types";
import { useAuth } from "../contexts/AuthContext";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Badge } from "../components/ui/badge";
import { Card, CardContent } from "../components/ui/card";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { EmptyState } from "../components/common/EmptyState";

interface EnrichedTest extends ClassTest {
    className: string;
}

export const TestsListPage: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuth();
    const isTeacher = user?.role === "TEACHER" || user?.role === "ADMIN";

    const [tests, setTests] = useState<EnrichedTest[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState<string>("");

    const fetchAllClassTests = async () => {
        try {
            setIsLoading(true);
            const classesRes = await classroomService.getMyClassrooms({ page: 0, size: 50 });
            const classes = classesRes.items || [];

            const testPromises = classes.map(async (cls) => {
                try {
                    const res = await testService.getTests(cls.id, 0, 50);
                    return res.items.map((t) => ({
                        ...t,
                        className: cls.name
                    }));
                } catch {
                    return [];
                }
            });

            const results = await Promise.all(testPromises);
            const flattened = results.flat();
            flattened.sort((a, b) => new Date(b.startAt).getTime() - new Date(a.startAt).getTime());
            setTests(flattened);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải danh sách bài kiểm tra.");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        void fetchAllClassTests();
    }, []);

    const filtered = tests.filter((t) =>
        t.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        t.className.toLowerCase().includes(searchQuery.toLowerCase())
    );

    return (
        <div className="space-y-6 p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
                        <FileText className="w-7 h-7 text-primary" />
                        Danh sách Bài kiểm tra
                    </h1>
                    <p className="text-sm text-slate-500 mt-1">
                        Quản lý và theo dõi các bài kiểm tra định thời trong các lớp học phần
                    </p>
                </div>
            </div>

            {/* Search */}
            <div className="relative w-full sm:w-80">
                <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <Input
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Tìm theo tên bài kiểm tra, tên lớp..."
                    className="pl-9 text-sm"
                />
            </div>

            {/* Grid */}
            {isLoading ? (
                <LoadingState label="Đang tải danh sách bài kiểm tra..." />
            ) : error ? (
                <ErrorBanner
                    title="Lỗi tải bài kiểm tra"
                    message={error}
                    onRetry={fetchAllClassTests}
                />
            ) : filtered.length === 0 ? (
                <EmptyState
                    icon={<FileText className="w-12 h-12 text-slate-300" />}
                    title={searchQuery ? "Không tìm thấy bài kiểm tra nào" : "Chưa có bài kiểm tra nào"}
                    description="Các bài kiểm tra định thời được tạo trong lớp học sẽ xuất hiện tại đây."
                />
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {filtered.map((test) => {
                        const now = new Date();
                        const start = new Date(test.startAt);
                        const end = new Date(test.endAt);
                        const isOpening = now >= start && now <= end;
                        const isUpcoming = now < start;

                        return (
                            <Card
                                key={test.id}
                                className="border-slate-200 shadow-sm hover:shadow-md transition-all flex flex-col justify-between"
                            >
                                <CardContent className="p-5 space-y-3">
                                    <div className="flex items-start justify-between gap-2">
                                        <Badge variant="outline" className="text-xs text-slate-600 bg-slate-50 truncate max-w-[200px]">
                                            {test.className}
                                        </Badge>
                                        <Badge
                                            variant={isOpening ? "success" : isUpcoming ? "warning" : "outline"}
                                            className="text-[11px] font-semibold shrink-0"
                                        >
                                            {isOpening ? "Đang mở" : isUpcoming ? "Sắp mở" : "Đã đóng"}
                                        </Badge>
                                    </div>

                                    <div>
                                        <h3 className="font-bold text-base text-slate-900 line-clamp-2">
                                            {test.title}
                                        </h3>
                                        <p className="text-xs text-slate-500 mt-1">
                                            Thời lượng: <strong>{test.durationMinutes} phút</strong> • Tối đa {test.maxAttempts} lần làm
                                        </p>
                                    </div>

                                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-100 text-xs text-slate-600 space-y-1">
                                        <p className="flex items-center gap-1.5">
                                            <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                                            <span>
                                                Mở: {start.toLocaleString("vi-VN", {
                                                    day: "2-digit",
                                                    month: "2-digit",
                                                    hour: "2-digit",
                                                    minute: "2-digit"
                                                })}
                                            </span>
                                        </p>
                                        <p className="flex items-center gap-1.5">
                                            <Calendar className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                                            <span>
                                                Đóng: {end.toLocaleString("vi-VN", {
                                                    day: "2-digit",
                                                    month: "2-digit",
                                                    hour: "2-digit",
                                                    minute: "2-digit"
                                                })}
                                            </span>
                                        </p>
                                    </div>

                                    <div className="pt-2 border-t border-slate-100 flex items-center justify-between">
                                        {isTeacher ? (
                                            <Button
                                                size="sm"
                                                variant="outline"
                                                onClick={() => navigate(`/tests/${test.id}/results`)}
                                                className="text-xs font-semibold text-primary border-primary/20 hover:bg-primary/5 ml-auto h-8 gap-1"
                                            >
                                                <BarChart3 className="w-3.5 h-3.5" />
                                                Xem bảng điểm
                                            </Button>
                                        ) : (
                                            <Button
                                                size="sm"
                                                variant="default"
                                                disabled={!isOpening}
                                                onClick={() => navigate(`/tests/${test.id}/take`)}
                                                className="text-xs font-semibold ml-auto h-8 gap-1"
                                            >
                                                <span>Làm bài thi</span>
                                                <ArrowRight className="w-3.5 h-3.5" />
                                            </Button>
                                        )}
                                    </div>
                                </CardContent>
                            </Card>
                        );
                    })}
                </div>
            )}
        </div>
    );
};

export default TestsListPage;
