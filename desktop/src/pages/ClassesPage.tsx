import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    Users,
    Plus,
    Search,
    Copy,
    Check,
    Lock,
    Archive,
    DoorOpen,
    Calendar,
    GraduationCap,
    Clock,
    BookOpen,
    Layers,
    ArrowRight
} from "lucide-react";
import { toast } from "sonner";
import { classroomService } from "../services/classroomService";
import { Classroom, ClassroomStatus } from "../types";
import { useAuth } from "../contexts/AuthContext";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Badge } from "../components/ui/badge";
import { Card, CardContent } from "../components/ui/card";
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
import { Pagination } from "../components/ui/pagination";

export const ClassesPage: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuth();
    const isTeacher = user?.role === "TEACHER" || user?.role === "ADMIN";

    const [classes, setClasses] = useState<Classroom[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Filters
    const [searchQuery, setSearchQuery] = useState<string>("");
    const [statusFilter, setStatusFilter] = useState<ClassroomStatus | "ALL">("ALL");
    const [page, setPage] = useState<number>(0);
    const [totalPages, setTotalPages] = useState<number>(1);
    const [totalElements, setTotalElements] = useState<number>(0);

    // Create Class Modal (Teacher)
    const [isCreateOpen, setIsCreateOpen] = useState<boolean>(false);
    const [className, setClassName] = useState<string>("");
    const [subject, setSubject] = useState<string>("");
    const [description, setDescription] = useState<string>("");
    const [startAt, setStartAt] = useState<string>("");
    const [endAt, setEndAt] = useState<string>("");
    const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
    const [createdCode, setCreatedCode] = useState<string | null>(null);
    const [hasCopiedCode, setHasCopiedCode] = useState<boolean>(false);

    // Join Class Modal (Student)
    const [isJoinOpen, setIsJoinOpen] = useState<boolean>(false);
    const [joinCode, setJoinCode] = useState<string>("");
    const [isJoining, setIsJoining] = useState<boolean>(false);

    // Copy helper state
    const [copiedClassId, setCopiedClassId] = useState<number | null>(null);

    const fetchClasses = async () => {
        try {
            setIsLoading(true);
            const res = await classroomService.getMyClassrooms({
                keyword: searchQuery.trim() || undefined,
                status: statusFilter === "ALL" ? undefined : statusFilter,
                page,
                size: 9
            });
            setClasses(res.items);
            if (res.meta) {
                setTotalPages(res.meta.totalPages || 1);
                setTotalElements(res.meta.totalElements || res.items.length);
            }
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải danh sách lớp học.");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        void fetchClasses();
    }, [page, statusFilter]);

    const handleSearchSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        void fetchClasses();
    };

    const handleCopyCode = async (code: string, classId?: number) => {
        try {
            await navigator.clipboard.writeText(code);
            if (classId) {
                setCopiedClassId(classId);
                setTimeout(() => setCopiedClassId(null), 2000);
            } else {
                setHasCopiedCode(true);
                setTimeout(() => setHasCopiedCode(false), 2000);
            }
            toast.success("Đã sao chép mã lớp vào bộ nhớ tạm!");
        } catch {
            toast.error("Không thể sao chép mã lớp.");
        }
    };

    const handleCreateClass = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!className.trim() || !subject.trim()) {
            toast.error("Vui lòng điền tên lớp và tên môn học.");
            return;
        }

        try {
            setIsSubmitting(true);
            const created = await classroomService.createClassroom({
                name: className.trim(),
                subject: subject.trim(),
                description: description.trim() || undefined,
                startAt: startAt ? new Date(startAt).toISOString() : undefined,
                endAt: endAt ? new Date(endAt).toISOString() : undefined
            });

            setCreatedCode(created.code);
            toast.success("Tạo lớp học thành công!");
            void fetchClasses();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Tạo lớp học thất bại.");
        } finally {
            setIsSubmitting(false);
        }
    };

    const resetCreateModal = () => {
        setIsCreateOpen(false);
        setCreatedCode(null);
        setClassName("");
        setSubject("");
        setDescription("");
        setStartAt("");
        setEndAt("");
        setHasCopiedCode(false);
    };

    const handleJoinClass = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!joinCode.trim()) {
            toast.error("Vui lòng nhập mã lớp học.");
            return;
        }

        try {
            setIsJoining(true);
            await classroomService.createJoinRequest({
                code: joinCode.trim().toUpperCase()
            });

            toast.success("Yêu cầu đã gửi, vui lòng chờ giảng viên duyệt!", {
                duration: 5000
            });
            setIsJoinOpen(false);
            setJoinCode("");
            void fetchClasses();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể gửi yêu cầu tham gia lớp.");
        } finally {
            setIsJoining(false);
        }
    };

    const getStatusBadge = (status: ClassroomStatus) => {
        switch (status) {
            case "OPEN":
                return (
                    <Badge variant="success" className="gap-1 text-xs font-semibold">
                        <DoorOpen className="w-3 h-3" />
                        Đang mở
                    </Badge>
                );
            case "CLOSED":
                return (
                    <Badge variant="warning" className="gap-1 text-xs font-semibold">
                        <Lock className="w-3 h-3" />
                        Đã đóng
                    </Badge>
                );
            case "ARCHIVED":
                return (
                    <Badge variant="outline" className="gap-1 text-xs font-semibold text-slate-500">
                        <Archive className="w-3 h-3" />
                        Lưu trữ
                    </Badge>
                );
            default:
                return null;
        }
    };

    return (
        <div className="space-y-6 p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
                        <GraduationCap className="w-7 h-7 text-primary" />
                        {isTeacher ? "Quản lý Lớp học phần" : "Lớp học của tôi"}
                    </h1>
                    <p className="text-sm text-slate-500 mt-1">
                        {isTeacher
                            ? "Theo dõi các lớp đang phụ trách, tạo lớp mới và quản lý sinh viên"
                            : "Tham gia lớp học phần bằng mã lớp, xem tài liệu, bài tập và bảng tin"}
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    {isTeacher ? (
                        <Button
                            variant="default"
                            onClick={() => setIsCreateOpen(true)}
                            className="gap-2"
                        >
                            <Plus className="w-4 h-4" />
                            Tạo lớp mới
                        </Button>
                    ) : (
                        <Button
                            variant="default"
                            onClick={() => setIsJoinOpen(true)}
                            className="gap-2 bg-teal-700 hover:bg-teal-800"
                        >
                            <Plus className="w-4 h-4" />
                            Tham gia lớp
                        </Button>
                    )}
                </div>
            </div>

            {/* Filters Bar */}
            <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
                <form onSubmit={handleSearchSubmit} className="relative w-full sm:w-80">
                    <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                    <Input
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Tìm theo tên lớp, môn học..."
                        className="pl-9 text-sm"
                    />
                </form>

                <div className="flex items-center gap-2 self-end sm:self-auto">
                    <span className="text-xs text-slate-500 font-medium">Trạng thái:</span>
                    <div className="flex rounded-lg border border-slate-200 p-0.5 bg-slate-50 text-xs">
                        {(["ALL", "OPEN", "CLOSED", "ARCHIVED"] as const).map((st) => (
                            <button
                                key={st}
                                onClick={() => {
                                    setStatusFilter(st);
                                    setPage(0);
                                }}
                                className={`px-2.5 py-1 rounded-md font-medium transition-colors ${
                                    statusFilter === st
                                        ? "bg-white text-slate-900 shadow-sm"
                                        : "text-slate-500 hover:text-slate-900"
                                }`}
                            >
                                {st === "ALL"
                                    ? "Tất cả"
                                    : st === "OPEN"
                                    ? "Đang mở"
                                    : st === "CLOSED"
                                    ? "Đã đóng"
                                    : "Lưu trữ"}
                            </button>
                        ))}
                    </div>
                </div>
            </div>

            {/* Classes Grid */}
            {isLoading ? (
                <LoadingState label="Đang tải danh sách lớp học..." />
            ) : error ? (
                <ErrorBanner
                    title="Lỗi tải danh sách lớp"
                    message={error}
                    onRetry={fetchClasses}
                />
            ) : classes.length === 0 ? (
                <EmptyState
                    icon={<Users className="w-12 h-12 text-slate-300" />}
                    title={searchQuery ? "Không tìm thấy lớp học nào" : "Bạn chưa tham gia lớp học nào"}
                    description={
                        isTeacher
                            ? "Hãy tạo lớp học mới để bắt đầu đăng tải tài liệu, tạo bài tập và quản lý sinh viên."
                            : "Nhập mã lớp được giảng viên cung cấp để gửi yêu cầu tham gia lớp."
                    }
                    action={
                        isTeacher ? (
                            <Button onClick={() => setIsCreateOpen(true)} className="gap-2">
                                <Plus className="w-4 h-4" />
                                Tạo lớp mới
                            </Button>
                        ) : (
                            <Button onClick={() => setIsJoinOpen(true)} className="gap-2 bg-teal-700 hover:bg-teal-800">
                                <Plus className="w-4 h-4" />
                                Tham gia lớp
                            </Button>
                        )
                    }
                />
            ) : (
                <div className="space-y-6">
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                        {classes.map((cls) => {
                            const isOwner = cls.myClassRole === "OWNER_TEACHER";
                            const isCo = cls.myClassRole === "CO_TEACHER";

                            return (
                                <Card
                                    key={cls.id}
                                    className="border-slate-200 shadow-sm hover:shadow-md transition-all flex flex-col justify-between group overflow-hidden"
                                >
                                    <div className="p-5 space-y-4">
                                        {/* Status & Role Badge */}
                                        <div className="flex items-center justify-between gap-2">
                                            {getStatusBadge(cls.status)}
                                            <Badge
                                                variant="outline"
                                                className={`text-[11px] font-medium ${
                                                    isOwner
                                                        ? "text-primary border-primary/30 bg-blue-50/50"
                                                        : isCo
                                                        ? "text-teal-700 border-teal-300 bg-teal-50/50"
                                                        : "text-slate-600 bg-slate-50"
                                                }`}
                                            >
                                                {isOwner
                                                    ? "Chủ nhiệm"
                                                    : isCo
                                                    ? "Đồng giảng dạy"
                                                    : "Sinh viên"}
                                            </Badge>
                                        </div>

                                        {/* Class Name & Subject */}
                                        <div className="space-y-1">
                                            <p className="text-xs font-semibold uppercase tracking-wider text-primary">
                                                {cls.subject}
                                            </p>
                                            <h3
                                                onClick={() => navigate(`/classes/${cls.id}`)}
                                                className="font-bold text-base text-slate-900 group-hover:text-primary transition-colors cursor-pointer line-clamp-2"
                                            >
                                                {cls.name}
                                            </h3>
                                            {cls.description && (
                                                <p className="text-xs text-slate-500 line-clamp-2 mt-1">
                                                    {cls.description}
                                                </p>
                                            )}
                                        </div>

                                        {/* Class Code pill */}
                                        <div className="p-2.5 rounded-lg bg-slate-50 border border-slate-100 flex items-center justify-between">
                                            <div className="flex items-center gap-1.5 text-xs text-slate-600">
                                                <span className="text-slate-400">Mã lớp:</span>
                                                <span className="font-mono font-bold tracking-wider text-slate-900">
                                                    {cls.code}
                                                </span>
                                            </div>
                                            <button
                                                type="button"
                                                onClick={() => handleCopyCode(cls.code, cls.id)}
                                                className="p-1 rounded hover:bg-slate-200 text-slate-500 hover:text-slate-800 transition-colors"
                                                title="Sao chép mã lớp"
                                            >
                                                {copiedClassId === cls.id ? (
                                                    <Check className="w-3.5 h-3.5 text-emerald-600" />
                                                ) : (
                                                    <Copy className="w-3.5 h-3.5" />
                                                )}
                                            </button>
                                        </div>

                                        {/* Meta info: Members & Teacher */}
                                        <div className="flex items-center justify-between text-xs text-slate-500 pt-2 border-t border-slate-100">
                                            <span className="flex items-center gap-1">
                                                <Users className="w-3.5 h-3.5" />
                                                <strong>{cls.memberCount}</strong> thành viên
                                            </span>
                                            <span className="truncate max-w-[140px]" title={cls.ownerName}>
                                                GV: {cls.ownerName}
                                            </span>
                                        </div>
                                    </div>

                                    {/* Action button */}
                                    <div className="px-5 py-3 bg-slate-50/70 border-t border-slate-100 flex items-center justify-between">
                                        <span className="text-[11px] text-slate-400">
                                            {cls.startAt
                                                ? `Bắt đầu: ${new Date(cls.startAt).toLocaleDateString("vi-VN")}`
                                                : "Lớp học phần"}
                                        </span>
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            onClick={() => navigate(`/classes/${cls.id}`)}
                                            className="text-xs font-semibold gap-1 text-primary hover:text-primary hover:bg-primary/10 h-8"
                                        >
                                            <span>Vào lớp</span>
                                            <ArrowRight className="w-3.5 h-3.5" />
                                        </Button>
                                    </div>
                                </Card>
                            );
                        })}
                    </div>

                    {/* Pagination */}
                    {totalPages > 1 && (
                        <div className="pt-4 flex justify-center">
                            <Pagination
                                page={page}
                                totalPages={totalPages}
                                onPageChange={setPage}
                            />
                        </div>
                    )}
                </div>
            )}

            {/* Create Class Modal (Teacher) */}
            <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900">
                            {createdCode ? "Tạo lớp thành công!" : "Tạo lớp học phần mới"}
                        </DialogTitle>
                    </DialogHeader>

                    {createdCode ? (
                        <div className="py-6 space-y-4 text-center">
                            <div className="w-12 h-12 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center mx-auto">
                                <Check className="w-6 h-6" />
                            </div>
                            <div>
                                <h3 className="text-base font-bold text-slate-900">{className}</h3>
                                <p className="text-xs text-slate-500 mt-1">
                                    Lớp học đã được tạo. Hãy chia sẻ mã lớp này cho sinh viên để họ gửi yêu cầu tham gia.
                                </p>
                            </div>
                            <div className="p-4 rounded-xl bg-slate-100 border border-slate-200 flex items-center justify-center gap-3">
                                <span className="font-mono text-2xl font-extrabold tracking-widest text-primary">
                                    {createdCode}
                                </span>
                                <Button
                                    size="sm"
                                    variant="outline"
                                    onClick={() => handleCopyCode(createdCode)}
                                    className="gap-1.5 text-xs"
                                >
                                    {hasCopiedCode ? (
                                        <>
                                            <Check className="w-3.5 h-3.5 text-emerald-600" />
                                            Đã chép
                                        </>
                                    ) : (
                                        <>
                                            <Copy className="w-3.5 h-3.5" />
                                            Sao chép mã
                                        </>
                                    )}
                                </Button>
                            </div>
                        </div>
                    ) : (
                        <form onSubmit={handleCreateClass} className="space-y-4 py-2">
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold text-slate-700">
                                    Tên lớp học <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    value={className}
                                    onChange={(e) => setClassName(e.target.value)}
                                    placeholder="Ví dụ: Lập trình Mạng - K19A"
                                    required
                                    disabled={isSubmitting}
                                />
                            </div>

                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold text-slate-700">
                                    Môn học <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    value={subject}
                                    onChange={(e) => setSubject(e.target.value)}
                                    placeholder="Ví dụ: Mạng máy tính"
                                    required
                                    disabled={isSubmitting}
                                />
                            </div>

                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold text-slate-700">
                                    Mô tả lớp học
                                </label>
                                <textarea
                                    value={description}
                                    onChange={(e) => setDescription(e.target.value)}
                                    placeholder="Giới thiệu nội dung, yêu cầu hoặc thời khóa biểu..."
                                    rows={3}
                                    className="w-full text-sm rounded-lg border border-slate-200 p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary disabled:opacity-50"
                                    disabled={isSubmitting}
                                />
                            </div>

                            <div className="grid grid-cols-2 gap-3">
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-slate-700">
                                        Ngày bắt đầu
                                    </label>
                                    <Input
                                        type="date"
                                        value={startAt}
                                        onChange={(e) => setStartAt(e.target.value)}
                                        disabled={isSubmitting}
                                        className="text-xs"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-slate-700">
                                        Ngày kết thúc
                                    </label>
                                    <Input
                                        type="date"
                                        value={endAt}
                                        onChange={(e) => setEndAt(e.target.value)}
                                        disabled={isSubmitting}
                                        className="text-xs"
                                    />
                                </div>
                            </div>

                            <DialogFooter className="pt-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={resetCreateModal}
                                    disabled={isSubmitting}
                                >
                                    Hủy
                                </Button>
                                <Button
                                    type="submit"
                                    variant="default"
                                    disabled={isSubmitting}
                                >
                                    {isSubmitting ? "Đang tạo..." : "Xác nhận tạo lớp"}
                                </Button>
                            </DialogFooter>
                        </form>
                    )}

                    {createdCode && (
                        <DialogFooter>
                            <Button
                                variant="default"
                                onClick={resetCreateModal}
                                className="w-full"
                            >
                                Đóng
                            </Button>
                        </DialogFooter>
                    )}
                </DialogContent>
            </Dialog>

            {/* Join Class Modal (Student) */}
            <Dialog open={isJoinOpen} onOpenChange={setIsJoinOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="text-lg font-bold text-slate-900 flex items-center gap-2">
                            <Plus className="w-5 h-5 text-teal-700" />
                            Tham gia lớp học
                        </DialogTitle>
                    </DialogHeader>

                    <form onSubmit={handleJoinClass} className="space-y-4 py-2">
                        <p className="text-xs text-slate-500">
                            Nhập mã gồm 8 ký tự được giảng viên cung cấp để gửi yêu cầu tham gia lớp học phần.
                        </p>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Mã lớp học <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                value={joinCode}
                                onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                                placeholder="Ví dụ: KMA7N9P4"
                                maxLength={12}
                                className="font-mono text-base tracking-widest uppercase text-center font-bold"
                                required
                                disabled={isJoining}
                                autoFocus
                            />
                        </div>

                        <div className="p-3 rounded-lg bg-teal-50 border border-teal-100 text-xs text-teal-800 space-y-1">
                            <p className="font-semibold flex items-center gap-1.5">
                                <Clock className="w-3.5 h-3.5" />
                                Lưu ý xét duyệt:
                            </p>
                            <p className="text-[11px] leading-relaxed">
                                Sau khi gửi mã thành công, yêu cầu của bạn sẽ ở trạng thái chờ duyệt. Bạn chỉ có thể truy cập tài liệu và bài tập sau khi giảng viên chấp thuận.
                            </p>
                        </div>

                        <DialogFooter className="pt-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsJoinOpen(false)}
                                disabled={isJoining}
                            >
                                Hủy
                            </Button>
                            <Button
                                type="submit"
                                variant="default"
                                className="bg-teal-700 hover:bg-teal-800"
                                disabled={isJoining}
                            >
                                {isJoining ? "Đang gửi yêu cầu..." : "Gửi yêu cầu tham gia"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default ClassesPage;
