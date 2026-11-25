import React, { useEffect, useState, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
    ClipboardList,
    Clock,
    Paperclip,
    Download,
    CheckCircle2,
    AlertCircle,
    ArrowLeft,
    Send,
    Edit3,
    RotateCcw,
    Award,
    FileText,
    Calendar,
    Users,
    UploadCloud,
    X
} from "lucide-react";
import { toast } from "sonner";
import { assignmentService } from "../services/assignmentService";
import {
    Assignment,
    Submission,
    SubmissionSummary,
    SubmissionStatus,
    GradeSubmissionRequest
} from "../types";
import { useAuth } from "../contexts/AuthContext";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Badge } from "../components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogFooter
} from "../components/ui/dialog";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { EmptyState } from "../components/common/EmptyState";

export const AssignmentDetailPage: React.FC = () => {
    const { assignmentId } = useParams<{ assignmentId: string }>();
    const numericAssignmentId = Number(assignmentId);
    const navigate = useNavigate();
    const { user } = useAuth();
    const isTeacher = user?.role === "TEACHER" || user?.role === "ADMIN";

    const [assignment, setAssignment] = useState<Assignment | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Student View State
    const [mySubmission, setMySubmission] = useState<Submission | null>(null);
    const [textAnswer, setTextAnswer] = useState<string>("");
    const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
    const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
    // Keep idempotency key unchanged on network retry
    const idempotencyKeyRef = useRef<string>(crypto.randomUUID());

    // Teacher View State (Submissions list)
    const [submissions, setSubmissions] = useState<SubmissionSummary[]>([]);
    const [isSubmissionsLoading, setIsSubmissionsLoading] = useState<boolean>(false);
    const [statusFilter, setStatusFilter] = useState<SubmissionStatus | "ALL">("ALL");
    const [lateFilter, setLateFilter] = useState<boolean | undefined>(undefined);

    // Teacher Grade Modal
    const [selectedSubForGrade, setSelectedSubForGrade] = useState<SubmissionSummary | null>(null);
    const [gradeScore, setGradeScore] = useState<number>(0);
    const [gradeFeedback, setGradeFeedback] = useState<string>("");
    const [gradeStatus, setGradeStatus] = useState<"GRADED" | "RETURNED">("RETURNED");
    const [isGrading, setIsGrading] = useState<boolean>(false);

    // Teacher Reopen Modal
    const [selectedSubForReopen, setSelectedSubForReopen] = useState<SubmissionSummary | null>(null);
    const [reopenReason, setReopenReason] = useState<string>("");
    const [isReopening, setIsReopening] = useState<boolean>(false);

    const fetchAssignmentData = async () => {
        if (!numericAssignmentId) return;
        try {
            setIsLoading(true);
            const data = await assignmentService.getAssignmentDetail(numericAssignmentId);
            setAssignment(data);

            if (!isTeacher) {
                const sub = await assignmentService.getMySubmission(numericAssignmentId);
                setMySubmission(sub);
                if (sub?.textAnswer) {
                    setTextAnswer(sub.textAnswer);
                }
            } else {
                void fetchSubmissionsList();
            }

            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải thông tin bài tập.");
        } finally {
            setIsLoading(false);
        }
    };

    const fetchSubmissionsList = async () => {
        if (!numericAssignmentId) return;
        try {
            setIsSubmissionsLoading(true);
            const res = await assignmentService.getSubmissions(numericAssignmentId, {
                status: statusFilter === "ALL" ? undefined : statusFilter,
                late: lateFilter
            });
            setSubmissions(res.items);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lỗi tải danh sách bài nộp.");
        } finally {
            setIsSubmissionsLoading(false);
        }
    };

    useEffect(() => {
        void fetchAssignmentData();
    }, [numericAssignmentId]);

    useEffect(() => {
        if (isTeacher && assignment) {
            void fetchSubmissionsList();
        }
    }, [statusFilter, lateFilter]);

    // Student: Submit Assignment
    const handleSubmitAssignment = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!textAnswer.trim() && selectedFiles.length === 0) {
            toast.error("Vui lòng nhập câu trả lời hoặc đính kèm tệp bài làm.");
            return;
        }

        try {
            setIsSubmitting(true);
            const sub = await assignmentService.submitAssignment(
                numericAssignmentId,
                textAnswer.trim() || undefined,
                selectedFiles,
                idempotencyKeyRef.current
            );

            // Successfully submitted -> reset key for any future new submission
            idempotencyKeyRef.current = crypto.randomUUID();
            setMySubmission(sub);
            setSelectedFiles([]);
            toast.success("Nộp bài tập thành công!");
        } catch (err: unknown) {
            // Do NOT reset idempotencyKeyRef.current so retry keeps the same key
            toast.error(err instanceof Error ? err.message : "Nộp bài thất bại. Bạn có thể nhấn thử lại.");
        } finally {
            setIsSubmitting(false);
        }
    };

    // Teacher: Grade Submission
    const handleGradeSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedSubForGrade || !assignment) return;

        if (gradeScore < 0 || gradeScore > assignment.maxScore) {
            toast.error(`Điểm số phải nằm trong khoảng từ 0 đến ${assignment.maxScore}.`);
            return;
        }

        try {
            setIsGrading(true);
            await assignmentService.gradeSubmission(selectedSubForGrade.id, {
                score: Number(gradeScore),
                feedback: gradeFeedback.trim() || undefined,
                status: gradeStatus
            });

            toast.success("Đã chấm điểm và cập nhật bài nộp!");
            setSelectedSubForGrade(null);
            void fetchSubmissionsList();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Chấm điểm thất bại.");
        } finally {
            setIsGrading(false);
        }
    };

    // Teacher: Reopen Submission
    const handleReopenSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedSubForReopen) return;

        try {
            setIsReopening(true);
            await assignmentService.reopenSubmission(selectedSubForReopen.id, {
                reason: reopenReason.trim() || undefined
            });

            toast.success("Đã mở lại quyền nộp bài cho sinh viên!");
            setSelectedSubForReopen(null);
            setReopenReason("");
            void fetchSubmissionsList();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Mở lại nộp bài thất bại.");
        } finally {
            setIsReopening(false);
        }
    };

    if (isLoading) {
        return <LoadingState label="Đang tải chi tiết bài tập..." />;
    }

    if (error || !assignment) {
        return (
            <ErrorBanner
                title="Lỗi tải bài tập"
                message={error || "Không tìm thấy bài tập được yêu cầu."}
                onRetry={fetchAssignmentData}
            />
        );
    }

    const now = new Date();
    const dueDate = new Date(assignment.dueAt);
    const isOverdue = now > dueDate;
    const isCanSubmit = !mySubmission || mySubmission.status === "REOPENED";

    return (
        <div className="space-y-6 p-6 max-w-5xl mx-auto">
            {/* Header Navigation */}
            <div className="space-y-3 pb-4 border-b border-slate-200">
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => navigate(`/classes/${assignment.classId}`)}
                    className="gap-1.5 text-xs text-slate-500 hover:text-slate-800 -ml-2 h-8"
                >
                    <ArrowLeft className="w-4 h-4" />
                    Quay lại lớp học
                </Button>

                <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
                    <div className="space-y-1">
                        <div className="flex items-center gap-2">
                            <span className="text-xs font-semibold text-primary uppercase tracking-wider">
                                Bài tập lớp học phần
                            </span>
                            <Badge
                                variant={isOverdue ? "danger" : "secondary"}
                                className="text-xs"
                            >
                                {isOverdue ? "Đã hết hạn" : "Đang mở nộp"}
                            </Badge>
                        </div>
                        <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
                            {assignment.title}
                        </h1>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                        <div className="text-right bg-slate-50 border border-slate-200 px-3 py-1.5 rounded-lg">
                            <p className="text-[11px] text-slate-500">Điểm tối đa</p>
                            <p className="text-lg font-bold text-slate-900">{assignment.maxScore}</p>
                        </div>
                    </div>
                </div>
            </div>

            {/* Assignment Info Card */}
            <Card className="border-slate-200 shadow-sm">
                <CardContent className="p-6 space-y-4">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs text-slate-600 pb-3 border-b border-slate-100">
                        <div className="flex items-center gap-2">
                            <Clock className="w-4 h-4 text-slate-400" />
                            <span>Hạn nộp: <strong>{dueDate.toLocaleString("vi-VN", {
                                day: "2-digit",
                                month: "2-digit",
                                year: "numeric",
                                hour: "2-digit",
                                minute: "2-digit"
                            })}</strong></span>
                        </div>
                        <div className="flex items-center gap-2">
                            <Calendar className="w-4 h-4 text-slate-400" />
                            <span>Quy tắc nộp muộn: <strong>{assignment.allowLate ? "Cho phép nộp muộn" : "Khóa khi hết hạn"}</strong></span>
                        </div>
                    </div>

                    {/* Instructions */}
                    <div className="space-y-2">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                            Yêu cầu & Hướng dẫn làm bài
                        </h3>
                        <p className="text-sm text-slate-700 whitespace-pre-line leading-relaxed bg-slate-50 p-4 rounded-xl border border-slate-100">
                            {assignment.instructions || "Không có hướng dẫn bổ sung từ giảng viên."}
                        </p>
                    </div>

                    {/* Attachments */}
                    {assignment.attachments && assignment.attachments.length > 0 && (
                        <div className="space-y-2 pt-2">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                                Tệp đính kèm đề bài
                            </h3>
                            <div className="flex flex-wrap gap-2">
                                {assignment.attachments.map((att) => (
                                    <div
                                        key={att.id}
                                        className="inline-flex items-center gap-2 p-2 rounded-lg bg-slate-100 text-xs font-medium text-slate-800"
                                    >
                                        <Paperclip className="w-3.5 h-3.5 text-slate-500" />
                                        <span>{att.fileName}</span>
                                        <span className="text-[11px] text-slate-400">
                                            ({(att.sizeBytes / 1024).toFixed(1)} KB)
                                        </span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* VIEW 1: STUDENT VIEW (SUBMIT OR VIEW GRADED RESULT) */}
            {!isTeacher && (
                <div className="space-y-6">
                    {/* Graded & Returned Feedback Banner */}
                    {mySubmission && (mySubmission.status === "GRADED" || mySubmission.status === "RETURNED") && (
                        <Card className="border-emerald-200 bg-emerald-50/40 shadow-sm">
                            <CardContent className="p-6 space-y-3">
                                <div className="flex items-center justify-between">
                                    <h3 className="text-base font-bold text-emerald-950 flex items-center gap-2">
                                        <Award className="w-5 h-5 text-emerald-600" />
                                        Kết quả chấm bài của Giảng viên
                                    </h3>
                                    <Badge variant="success" className="text-sm font-bold px-3 py-1">
                                        Điểm: {mySubmission.score} / {assignment.maxScore}
                                    </Badge>
                                </div>
                                <div className="p-3.5 rounded-lg bg-white border border-emerald-200 space-y-1">
                                    <p className="text-xs font-semibold text-slate-700">Lời nhận xét:</p>
                                    <p className="text-sm text-slate-800 whitespace-pre-line">
                                        {mySubmission.feedback || "Không có nhận xét chi tiết."}
                                    </p>
                                </div>
                            </CardContent>
                        </Card>
                    )}

                    {/* Submitted details card */}
                    {mySubmission && mySubmission.status !== "REOPENED" ? (
                        <Card className="border-slate-200 shadow-sm">
                            <CardHeader className="pb-3 border-b border-slate-100 flex flex-row items-center justify-between">
                                <div>
                                    <CardTitle className="text-base font-bold text-slate-900 flex items-center gap-2">
                                        <CheckCircle2 className="w-5 h-5 text-emerald-600" />
                                        Bài làm của bạn đã được ghi nhận
                                    </CardTitle>
                                    <p className="text-xs text-slate-500 mt-0.5">
                                        Nộp lúc: <strong>{new Date(mySubmission.submittedAt).toLocaleString("vi-VN")}</strong>
                                        {mySubmission.late && (
                                            <span className="text-amber-600 font-bold ml-2">(Nộp muộn)</span>
                                        )}
                                    </p>
                                </div>
                                <Badge
                                    variant={mySubmission.late ? "warning" : "success"}
                                    className="text-xs font-semibold"
                                >
                                    {mySubmission.late ? "Nộp muộn" : "Đúng hạn"}
                                </Badge>
                            </CardHeader>
                            <CardContent className="p-6 space-y-4">
                                {mySubmission.textAnswer && (
                                    <div className="space-y-1.5">
                                        <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                                            Nội dung câu trả lời
                                        </h4>
                                        <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 text-sm text-slate-800 whitespace-pre-line leading-relaxed">
                                            {mySubmission.textAnswer}
                                        </div>
                                    </div>
                                )}

                                {mySubmission.files && mySubmission.files.length > 0 && (
                                    <div className="space-y-2">
                                        <h4 className="text-xs font-bold uppercase tracking-wider text-slate-500">
                                            Tệp bài làm đã nộp
                                        </h4>
                                        <div className="space-y-2">
                                            {mySubmission.files.map((f) => (
                                                <div
                                                    key={f.id}
                                                    className="flex items-center justify-between p-3 rounded-lg border border-slate-200 bg-white"
                                                >
                                                    <div className="flex items-center gap-2 text-sm text-slate-800 truncate">
                                                        <Paperclip className="w-4 h-4 text-slate-400 shrink-0" />
                                                        <span className="truncate">{f.fileName}</span>
                                                        <span className="text-xs text-slate-400">
                                                            ({(f.sizeBytes / 1024).toFixed(1)} KB)
                                                        </span>
                                                    </div>
                                                    <a
                                                        href={assignmentService.getDownloadSubmissionFileUrl(mySubmission.id, f.id)}
                                                        download
                                                        className="text-xs font-semibold text-primary hover:underline flex items-center gap-1"
                                                    >
                                                        <Download className="w-3.5 h-3.5" />
                                                        Tải về
                                                    </a>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </CardContent>
                        </Card>
                    ) : (
                        /* Submission Form (When not submitted yet OR reopened) */
                        <Card className="border-slate-200 shadow-sm">
                            <CardHeader className="pb-3 border-b border-slate-100">
                                <CardTitle className="text-base font-bold text-slate-900 flex items-center gap-2">
                                    <Send className="w-5 h-5 text-teal-700" />
                                    Nộp bài tập
                                </CardTitle>
                                {isOverdue && (
                                    <p className="text-xs text-rose-600 font-semibold mt-1">
                                        {assignment.allowLate
                                            ? "Bài tập đã quá hạn nộp. Bài làm của bạn sẽ bị đánh dấu nộp muộn."
                                            : "Bài tập đã đóng nộp do đã quá hạn."}
                                    </p>
                                )}
                            </CardHeader>
                            <CardContent className="p-6">
                                {isOverdue && !assignment.allowLate ? (
                                    <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-xs text-rose-800 text-center">
                                        Bài tập đã chính thức đóng nộp. Vui lòng liên hệ giảng viên nếu bạn cần mở lại quyền nộp bài.
                                    </div>
                                ) : (
                                    <form onSubmit={handleSubmitAssignment} className="space-y-4">
                                        <div className="space-y-1.5">
                                            <label className="text-xs font-semibold text-slate-700">
                                                Câu trả lời văn bản
                                            </label>
                                            <textarea
                                                value={textAnswer}
                                                onChange={(e) => setTextAnswer(e.target.value)}
                                                placeholder="Nhập câu trả lời, giải thích hoặc ghi chú nộp bài..."
                                                rows={4}
                                                className="w-full text-sm rounded-lg border border-slate-200 p-3 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                                                disabled={isSubmitting}
                                            />
                                        </div>

                                        <div className="space-y-2">
                                            <label className="text-xs font-semibold text-slate-700">
                                                Tệp đính kèm bài làm
                                            </label>
                                            <div className="p-4 border-2 border-dashed border-slate-200 rounded-xl text-center space-y-2 hover:border-primary/50 transition-colors">
                                                <UploadCloud className="w-8 h-8 text-slate-400 mx-auto" />
                                                <div className="text-xs text-slate-600">
                                                    <label className="font-semibold text-primary cursor-pointer hover:underline">
                                                        Chọn tệp bài làm
                                                        <input
                                                            type="file"
                                                            multiple
                                                            className="hidden"
                                                            onChange={(e) => {
                                                                if (e.target.files) {
                                                                    setSelectedFiles(Array.from(e.target.files));
                                                                }
                                                            }}
                                                            disabled={isSubmitting}
                                                        />
                                                    </label>
                                                    <p className="text-[11px] text-slate-400 mt-1">
                                                        Hỗ trợ tải lên nhiều tệp (báo cáo, mã nguồn, tài liệu)
                                                    </p>
                                                </div>
                                            </div>

                                            {selectedFiles.length > 0 && (
                                                <div className="space-y-1.5 pt-1">
                                                    {selectedFiles.map((file, idx) => (
                                                        <div
                                                            key={idx}
                                                            className="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-200 text-xs"
                                                        >
                                                            <div className="flex items-center gap-2 truncate">
                                                                <Paperclip className="w-3.5 h-3.5 text-slate-400" />
                                                                <span className="font-medium text-slate-800 truncate">{file.name}</span>
                                                                <span className="text-slate-400">({(file.size / 1024).toFixed(1)} KB)</span>
                                                            </div>
                                                            <button
                                                                type="button"
                                                                onClick={() => setSelectedFiles(selectedFiles.filter((_, i) => i !== idx))}
                                                                className="text-slate-400 hover:text-rose-600 p-1"
                                                                disabled={isSubmitting}
                                                            >
                                                                <X className="w-3.5 h-3.5" />
                                                            </button>
                                                        </div>
                                                    ))}
                                                </div>
                                            )}
                                        </div>

                                        <div className="pt-2 flex justify-end">
                                            <Button
                                                type="submit"
                                                variant="default"
                                                disabled={isSubmitting || (!textAnswer.trim() && selectedFiles.length === 0)}
                                                className="bg-teal-700 hover:bg-teal-800 gap-2"
                                            >
                                                <Send className="w-4 h-4" />
                                                {isSubmitting ? "Đang gửi bài..." : "Xác nhận nộp bài"}
                                            </Button>
                                        </div>
                                    </form>
                                )}
                            </CardContent>
                        </Card>
                    )}
                </div>
            )}

            {/* VIEW 2: TEACHER VIEW (SUBMISSIONS LIST & GRADING MODAL) */}
            {isTeacher && (
                <div className="space-y-4">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                        <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                            <Users className="w-5 h-5 text-primary" />
                            Danh sách bài nộp của lớp ({submissions.length})
                        </h2>

                        {/* Filter Bar */}
                        <div className="flex items-center gap-2 text-xs">
                            <span className="text-slate-500 font-medium">Lọc:</span>
                            <div className="flex rounded-lg border border-slate-200 p-0.5 bg-slate-50">
                                {(["ALL", "SUBMITTED", "GRADED", "RETURNED"] as const).map((st) => (
                                    <button
                                        key={st}
                                        onClick={() => setStatusFilter(st)}
                                        className={`px-2.5 py-1 rounded-md font-medium transition-colors ${
                                            statusFilter === st
                                                ? "bg-white text-slate-900 shadow-sm"
                                                : "text-slate-500 hover:text-slate-900"
                                        }`}
                                    >
                                        {st === "ALL"
                                            ? "Tất cả"
                                            : st === "SUBMITTED"
                                            ? "Chưa chấm"
                                            : st === "GRADED"
                                            ? "Đã chấm"
                                            : "Đã trả bài"}
                                    </button>
                                ))}
                            </div>
                        </div>
                    </div>

                    {isSubmissionsLoading ? (
                        <LoadingState label="Đang tải danh sách bài nộp..." />
                    ) : submissions.length === 0 ? (
                        <EmptyState
                            icon={<FileText className="w-10 h-10 text-slate-300" />}
                            title="Chưa có sinh viên nào nộp bài"
                            description="Danh sách bài nộp sẽ hiển thị tại đây khi sinh viên nộp bài làm."
                        />
                    ) : (
                        <Card className="border-slate-200 shadow-sm overflow-hidden">
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm text-left">
                                    <thead className="bg-slate-50 text-slate-700 text-xs uppercase font-semibold border-b border-slate-200">
                                        <tr>
                                            <th className="py-3 px-4">Sinh viên</th>
                                            <th className="py-3 px-4">Thời điểm nộp</th>
                                            <th className="py-3 px-4">Trạng thái</th>
                                            <th className="py-3 px-4 text-center">Điểm số</th>
                                            <th className="py-3 px-4 text-right">Thao tác</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-slate-100">
                                        {submissions.map((sub) => (
                                            <tr key={sub.id} className="hover:bg-slate-50/80 transition-colors">
                                                <td className="py-3.5 px-4 font-semibold text-slate-900">
                                                    {sub.studentName}
                                                </td>
                                                <td className="py-3.5 px-4 text-xs text-slate-600">
                                                    {new Date(sub.submittedAt).toLocaleString("vi-VN", {
                                                        day: "2-digit",
                                                        month: "2-digit",
                                                        hour: "2-digit",
                                                        minute: "2-digit"
                                                    })}
                                                    {sub.late && (
                                                        <span className="text-amber-600 font-bold ml-1.5">(Muộn)</span>
                                                    )}
                                                </td>
                                                <td className="py-3.5 px-4 text-xs">
                                                    <Badge
                                                        variant={
                                                            sub.status === "GRADED" || sub.status === "RETURNED"
                                                                ? "success"
                                                                : sub.status === "REOPENED"
                                                                ? "warning"
                                                                : "secondary"
                                                        }
                                                        className="text-[11px]"
                                                    >
                                                        {sub.status === "GRADED"
                                                            ? "Đã chấm"
                                                            : sub.status === "RETURNED"
                                                            ? "Đã trả bài"
                                                            : sub.status === "REOPENED"
                                                            ? "Được mở lại"
                                                            : "Đã nộp"}
                                                    </Badge>
                                                </td>
                                                <td className="py-3.5 px-4 text-center">
                                                    {typeof sub.score === "number" ? (
                                                        <span className="font-bold text-emerald-700 text-sm">
                                                            {sub.score} / {assignment.maxScore}
                                                        </span>
                                                    ) : (
                                                        <span className="text-slate-400 text-xs">Chưa chấm</span>
                                                    )}
                                                </td>
                                                <td className="py-3.5 px-4 text-right">
                                                    <div className="flex items-center justify-end gap-2">
                                                        <Button
                                                            size="sm"
                                                            variant="outline"
                                                            onClick={() => {
                                                                setSelectedSubForReopen(sub);
                                                            }}
                                                            className="text-xs h-8 text-amber-600 border-amber-200 hover:bg-amber-50"
                                                        >
                                                            <RotateCcw className="w-3.5 h-3.5 mr-1" />
                                                            Mở lại
                                                        </Button>

                                                        <Button
                                                            size="sm"
                                                            variant="default"
                                                            onClick={() => {
                                                                setSelectedSubForGrade(sub);
                                                                setGradeScore(sub.score ?? 0);
                                                            }}
                                                            className="text-xs h-8 gap-1"
                                                        >
                                                            <Edit3 className="w-3.5 h-3.5" />
                                                            {typeof sub.score === "number" ? "Sửa điểm" : "Chấm điểm"}
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
            )}

            {/* MODAL 1: TEACHER GRADE MODAL */}
            <Dialog open={!!selectedSubForGrade} onOpenChange={(open) => !open && setSelectedSubForGrade(null)}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900 flex items-center gap-2">
                            <Award className="w-5 h-5 text-primary" />
                            Chấm điểm bài nộp - {selectedSubForGrade?.studentName}
                        </DialogTitle>
                    </DialogHeader>

                    <form onSubmit={handleGradeSubmit} className="space-y-4 py-2">
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Điểm số (Thang điểm 0 - {assignment.maxScore}) <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                type="number"
                                step="0.25"
                                min={0}
                                max={assignment.maxScore}
                                value={gradeScore}
                                onChange={(e) => setGradeScore(Number(e.target.value))}
                                required
                                disabled={isGrading}
                                className="font-bold text-base"
                            />
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Lời nhận xét & Góp ý
                            </label>
                            <textarea
                                value={gradeFeedback}
                                onChange={(e) => setGradeFeedback(e.target.value)}
                                placeholder="Ghi nhận xét ưu điểm, lỗi sai hoặc hướng dẫn sửa..."
                                rows={4}
                                className="w-full text-sm rounded-lg border border-slate-200 p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                                disabled={isGrading}
                            />
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Trạng thái trả kết quả
                            </label>
                            <select
                                value={gradeStatus}
                                onChange={(e) => setGradeStatus(e.target.value as "GRADED" | "RETURNED")}
                                className="w-full text-sm rounded-lg border border-slate-200 p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                                disabled={isGrading}
                            >
                                <option value="RETURNED">Trả bài ngay (Sinh viên thấy điểm & nhận xét)</option>
                                <option value="GRADED">Lưu nháp điểm (Chưa công bố cho sinh viên)</option>
                            </select>
                        </div>

                        <DialogFooter className="pt-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setSelectedSubForGrade(null)}
                                disabled={isGrading}
                            >
                                Hủy
                            </Button>
                            <Button
                                type="submit"
                                variant="default"
                                disabled={isGrading}
                            >
                                {isGrading ? "Đang lưu điểm..." : "Xác nhận chấm"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            {/* MODAL 2: TEACHER REOPEN MODAL */}
            <Dialog open={!!selectedSubForReopen} onOpenChange={(open) => !open && setSelectedSubForReopen(null)}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900 flex items-center gap-2 text-amber-700">
                            <RotateCcw className="w-5 h-5 text-amber-600" />
                            Mở lại quyền nộp bài cho sinh viên
                        </DialogTitle>
                    </DialogHeader>

                    <form onSubmit={handleReopenSubmit} className="space-y-4 py-2">
                        <p className="text-xs text-slate-500">
                            Sinh viên <strong>{selectedSubForReopen?.studentName}</strong> sẽ được phép gửi lại bài làm mới cho bài tập này.
                        </p>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Lý do mở lại (tùy chọn)
                            </label>
                            <Input
                                value={reopenReason}
                                onChange={(e) => setReopenReason(e.target.value)}
                                placeholder="Ví dụ: Cho phép nộp bổ sung file báo cáo thiếu..."
                                disabled={isReopening}
                            />
                        </div>

                        <DialogFooter className="pt-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setSelectedSubForReopen(null)}
                                disabled={isReopening}
                            >
                                Hủy
                            </Button>
                            <Button
                                type="submit"
                                variant="default"
                                className="bg-amber-600 hover:bg-amber-700"
                                disabled={isReopening}
                            >
                                {isReopening ? "Đang mở lại..." : "Xác nhận mở lại nộp"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default AssignmentDetailPage;
