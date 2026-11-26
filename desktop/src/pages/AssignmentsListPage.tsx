import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    ClipboardList,
    Clock,
    Search,
    BookOpen,
    ArrowRight,
    CheckCircle2,
    AlertCircle,
    Calendar,
    GraduationCap
} from "lucide-react";
import { toast } from "sonner";
import { classroomService } from "../services/classroomService";
import { assignmentService } from "../services/assignmentService";
import { Classroom, AssignmentSummary } from "../types";
import { useAuth } from "../contexts/AuthContext";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Badge } from "../components/ui/badge";
import { Card, CardContent } from "../components/ui/card";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { EmptyState } from "../components/common/EmptyState";

interface EnrichedAssignment extends AssignmentSummary {
    className: string;
}

export const AssignmentsListPage: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuth();
    const isTeacher = user?.role === "TEACHER" || user?.role === "ADMIN";

    const [assignments, setAssignments] = useState<EnrichedAssignment[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState<string>("");

    const fetchAllClassAssignments = async () => {
        try {
            setIsLoading(true);
            const classesRes = await classroomService.getMyClassrooms({ page: 0, size: 50 });
            const classes = classesRes.items || [];

            const assignmentPromises = classes.map(async (cls) => {
                try {
                    const res = await assignmentService.getAssignments(cls.id, 0, 50);
                    return res.items.map((a) => ({
                        ...a,
                        className: cls.name
                    }));
                } catch {
                    return [];
                }
            });

            const results = await Promise.all(assignmentPromises);
            const flattened = results.flat();
            // Sort by dueAt ascending
            flattened.sort((a, b) => new Date(a.dueAt).getTime() - new Date(b.dueAt).getTime());
            setAssignments(flattened);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải danh sách bài tập.");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        void fetchAllClassAssignments();
    }, []);

    const filtered = assignments.filter((a) =>
        a.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        a.className.toLowerCase().includes(searchQuery.toLowerCase())
    );

    return (
        <div className="space-y-6 p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
                        <ClipboardList className="w-7 h-7 text-teal-700" />
                        Danh sách Bài tập
                    </h1>
                    <p className="text-sm text-slate-500 mt-1">
                        Tổng hợp bài tập cần hoàn thành và theo dõi tiến độ từ các lớp học phần
                    </p>
                </div>
            </div>

            {/* Search */}
            <div className="relative w-full sm:w-80">
                <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <Input
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    placeholder="Tìm theo tên bài tập hoặc tên lớp..."
                    className="pl-9 text-sm"
                />
            </div>

            {/* List */}
            {isLoading ? (
                <LoadingState label="Đang tổng hợp danh sách bài tập..." />
            ) : error ? (
                <ErrorBanner
                    title="Lỗi tải bài tập"
                    message={error}
                    onRetry={fetchAllClassAssignments}
                />
            ) : filtered.length === 0 ? (
                <EmptyState
                    icon={<ClipboardList className="w-12 h-12 text-slate-300" />}
                    title={searchQuery ? "Không tìm thấy bài tập nào" : "Chưa có bài tập nào"}
                    description="Các bài tập được giao trong các lớp học của bạn sẽ hiển thị tại đây."
                />
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {filtered.map((assign) => {
                        const now = new Date();
                        const dueDate = new Date(assign.dueAt);
                        const isOverdue = now > dueDate;

                        return (
                            <Card
                                key={assign.id}
                                className="border-slate-200 shadow-sm hover:shadow-md transition-all flex flex-col justify-between"
                            >
                                <CardContent className="p-5 space-y-3">
                                    <div className="flex items-start justify-between gap-2">
                                        <Badge variant="outline" className="text-xs text-slate-600 bg-slate-50 truncate max-w-[200px]">
                                            {assign.className}
                                        </Badge>
                                        <Badge
                                            variant={isOverdue ? "danger" : "secondary"}
                                            className="text-[11px] shrink-0"
                                        >
                                            {isOverdue ? "Đã quá hạn" : "Còn hạn"}
                                        </Badge>
                                    </div>

                                    <div>
                                        <h3
                                            onClick={() => navigate(`/assignments/${assign.id}`)}
                                            className="font-bold text-base text-slate-900 hover:text-teal-700 transition-colors cursor-pointer line-clamp-2"
                                        >
                                            {assign.title}
                                        </h3>
                                        <p className="text-xs text-slate-500 mt-1">
                                            Điểm tối đa: <strong>{assign.maxScore}</strong>
                                        </p>
                                    </div>

                                    <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-100 text-xs text-slate-600 flex items-center gap-1.5">
                                        <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                                        <span>
                                            Hạn nộp: <strong>{dueDate.toLocaleString("vi-VN", {
                                                day: "2-digit",
                                                month: "2-digit",
                                                hour: "2-digit",
                                                minute: "2-digit"
                                            })}</strong>
                                        </span>
                                    </div>

                                    <div className="pt-2 border-t border-slate-100 flex items-center justify-between">
                                        {!isTeacher && assign.mySubmissionStatus && (
                                            <Badge
                                                variant={assign.mySubmissionStatus === "GRADED" ? "success" : "secondary"}
                                                className="text-[11px]"
                                            >
                                                {assign.mySubmissionStatus === "GRADED"
                                                    ? "Đã chấm"
                                                    : assign.mySubmissionStatus === "SUBMITTED"
                                                    ? "Đã nộp"
                                                    : "Mở lại"}
                                            </Badge>
                                        )}
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            onClick={() => navigate(`/assignments/${assign.id}`)}
                                            className="text-xs font-semibold text-teal-700 hover:text-teal-800 hover:bg-teal-50 ml-auto h-8 gap-1"
                                        >
                                            <span>{isTeacher ? "Xem bài nộp" : "Chi tiết bài nộp"}</span>
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
    );
};

export default AssignmentsListPage;
