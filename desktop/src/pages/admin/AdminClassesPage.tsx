import React, { useCallback, useEffect, useState } from "react";
import {
    Archive,
    BookOpen,
    DoorOpen,
    Lock,
    Search,
    Settings2,
    Users
} from "lucide-react";
import { toast } from "sonner";
import adminService from "../../services/adminService";
import { AdminClassItem, ClassroomStatus } from "../../types";
import { Badge } from "../../components/ui/badge";
import { Button } from "../../components/ui/button";
import { Card } from "../../components/ui/card";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../../components/ui/dialog";
import { Input } from "../../components/ui/input";
import { Pagination } from "../../components/ui/pagination";
import { EmptyState } from "../../components/common/EmptyState";
import { ErrorBanner } from "../../components/common/ErrorBanner";
import { LoadingState } from "../../components/common/LoadingState";

function getClassStatusBadge(status: ClassroomStatus): React.ReactNode {
    switch (status) {
        case "OPEN":
            return (
                <Badge variant="success" icon={<DoorOpen className="h-3 w-3" />}>
                    Đang mở
                </Badge>
            );
        case "CLOSED":
            return (
                <Badge variant="warning" icon={<Lock className="h-3 w-3" />}>
                    Đã đóng
                </Badge>
            );
        case "ARCHIVED":
        default:
            return (
                <Badge variant="outline" icon={<Archive className="h-3 w-3" />}>
                    Lưu trữ
                </Badge>
            );
    }
}

export const AdminClassesPage: React.FC = () => {
    const [classes, setClasses] = useState<AdminClassItem[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    const [keyword, setKeyword] = useState<string>("");
    const [statusFilter, setStatusFilter] = useState<"ALL" | ClassroomStatus>("ALL");
    const [page, setPage] = useState<number>(0);
    const [totalPages, setTotalPages] = useState<number>(1);
    const [totalElements, setTotalElements] = useState<number>(0);

    // Status Modal
    const [selectedClass, setSelectedClass] = useState<AdminClassItem | null>(null);
    const [targetStatus, setTargetStatus] = useState<ClassroomStatus>("CLOSED");
    const [isUpdating, setIsUpdating] = useState<boolean>(false);

    const fetchClasses = useCallback(async (): Promise<void> => {
        try {
            setIsLoading(true);
            const res = await adminService.listClasses({
                keyword: keyword.trim() || undefined,
                status: statusFilter === "ALL" ? undefined : statusFilter,
                page,
                size: 15
            });
            setClasses(res.items);
            setTotalPages(res.meta?.totalPages ?? 1);
            setTotalElements(res.meta?.totalElements ?? res.items.length);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải danh sách lớp học.");
        } finally {
            setIsLoading(false);
        }
    }, [keyword, page, statusFilter]);

    useEffect(() => {
        void fetchClasses();
    }, [fetchClasses]);

    const handleSearchSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        void fetchClasses();
    };

    const openStatusModal = (cls: AdminClassItem) => {
        setSelectedClass(cls);
        setTargetStatus(cls.status === "OPEN" ? "CLOSED" : "OPEN");
    };

    const handleConfirmStatus = async () => {
        if (!selectedClass) return;
        try {
            setIsUpdating(true);
            const updated = await adminService.updateClassroomStatus(
                selectedClass.id,
                targetStatus,
                selectedClass.version
            );
            setClasses((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
            toast.success(`Đã cập nhật trạng thái lớp "${selectedClass.name}".`);
            setSelectedClass(null);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể đổi trạng thái lớp học.");
        } finally {
            setIsUpdating(false);
        }
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2.5">
                        <BookOpen className="h-6 w-6 text-primary" />
                        <span>Quản lý Lớp học toàn hệ thống</span>
                    </h1>
                    <p className="text-sm text-muted-foreground mt-1">
                        Giám sát toàn bộ lớp học phần, kiểm tra sĩ số và khóa/mở/lưu trữ lớp ({totalElements} lớp).
                    </p>
                </div>
            </div>

            {/* Filters */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <form onSubmit={handleSearchSubmit} className="flex items-center gap-2 w-full sm:w-96">
                    <div className="relative flex-1">
                        <Search className="h-4 w-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                        <Input
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            placeholder="Tìm theo tên lớp, mã lớp hoặc giảng viên..."
                            className="pl-9 h-9 text-sm"
                        />
                    </div>
                    <Button type="submit" variant="outline" size="sm" className="h-9 text-xs shrink-0">
                        Tìm kiếm
                    </Button>
                </form>

                <div className="flex items-center gap-2 text-xs">
                    <span className="text-muted-foreground font-medium">Trạng thái:</span>
                    <select
                        value={statusFilter}
                        onChange={(e) => {
                            setStatusFilter(e.target.value as "ALL" | ClassroomStatus);
                            setPage(0);
                        }}
                        className="h-9 rounded-lg border border-border bg-surface px-2.5 text-xs font-medium"
                    >
                        <option value="ALL">Tất cả trạng thái</option>
                        <option value="OPEN">Đang mở</option>
                        <option value="CLOSED">Đã đóng</option>
                        <option value="ARCHIVED">Lưu trữ</option>
                    </select>
                </div>
            </div>

            {/* Table */}
            {isLoading ? (
                <LoadingState label="Đang tải danh sách lớp học..." />
            ) : error ? (
                <ErrorBanner
                    title="Lỗi tải danh sách lớp học"
                    message={error}
                    onRetry={() => void fetchClasses()}
                />
            ) : classes.length === 0 ? (
                <EmptyState
                    icon={<BookOpen className="h-12 w-12 text-slate-300" />}
                    title="Không có lớp học nào"
                    description="Chưa có lớp học nào khớp với từ khóa hoặc bộ lọc đã chọn."
                />
            ) : (
                <div className="space-y-4">
                    <Card className="border-border shadow-sm overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm text-left">
                                <thead className="bg-slate-50 text-slate-700 text-xs uppercase font-semibold border-b border-border">
                                    <tr>
                                        <th className="py-3.5 px-4">Tên lớp & Môn học</th>
                                        <th className="py-3.5 px-4">Mã lớp</th>
                                        <th className="py-3.5 px-4">Giảng viên chủ nhiệm</th>
                                        <th className="py-3.5 px-4 text-center">Thành viên</th>
                                        <th className="py-3.5 px-4">Trạng thái</th>
                                        <th className="py-3.5 px-4 text-right">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border">
                                    {classes.map((cls) => (
                                        <tr key={cls.id} className="hover:bg-slate-50/70 transition-colors">
                                            <td className="py-3.5 px-4">
                                                <p className="font-semibold text-slate-900">{cls.name}</p>
                                                <p className="text-xs text-muted-foreground">{cls.subject}</p>
                                            </td>
                                            <td className="py-3.5 px-4">
                                                <span className="font-mono text-xs font-bold bg-slate-100 px-2 py-1 rounded text-slate-800">
                                                    {cls.code}
                                                </span>
                                            </td>
                                            <td className="py-3.5 px-4">
                                                <p className="text-xs font-semibold text-slate-800">
                                                    {cls.ownerName}
                                                </p>
                                                <p className="text-[11px] text-muted-foreground">
                                                    {cls.ownerEmail}
                                                </p>
                                            </td>
                                            <td className="py-3.5 px-4 text-center">
                                                <span className="inline-flex items-center gap-1 text-xs font-semibold text-slate-700">
                                                    <Users className="h-3.5 w-3.5 text-slate-400" />
                                                    {cls.memberCount}
                                                </span>
                                            </td>
                                            <td className="py-3.5 px-4">{getClassStatusBadge(cls.status)}</td>
                                            <td className="py-3.5 px-4 text-right">
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    onClick={() => openStatusModal(cls)}
                                                    className="h-8 text-xs gap-1.5"
                                                >
                                                    <Settings2 className="h-3.5 w-3.5" />
                                                    <span>Đổi trạng thái</span>
                                                </Button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </Card>

                    {totalPages > 1 && (
                        <div className="flex justify-center pt-2">
                            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
                        </div>
                    )}
                </div>
            )}

            {/* Confirm Status Dialog */}
            <Dialog open={!!selectedClass} onOpenChange={(open) => !open && setSelectedClass(null)}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>Cập nhật trạng thái lớp học</DialogTitle>
                        <DialogDescription>
                            Thay đổi trạng thái hoạt động cho lớp{" "}
                            <strong>{selectedClass?.name}</strong> (Mã: {selectedClass?.code}).
                        </DialogDescription>
                    </DialogHeader>

                    <div className="space-y-3 py-2">
                        <label className="text-xs font-semibold text-slate-700">Trạng thái mới</label>
                        <select
                            value={targetStatus}
                            onChange={(e) => setTargetStatus(e.target.value as ClassroomStatus)}
                            disabled={isUpdating}
                            className="w-full text-sm rounded-lg border border-border p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20"
                        >
                            <option value="OPEN">Đang mở (OPEN) — Cho phép tham gia & hoạt động</option>
                            <option value="CLOSED">Đã đóng (CLOSED) — Khóa nhận thành viên mới</option>
                            <option value="ARCHIVED">Lưu trữ (ARCHIVED) — Kết thúc học phần</option>
                        </select>
                    </div>

                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => setSelectedClass(null)}
                            disabled={isUpdating}
                        >
                            Hủy
                        </Button>
                        <Button
                            type="button"
                            onClick={() => void handleConfirmStatus()}
                            disabled={isUpdating || targetStatus === selectedClass?.status}
                        >
                            {isUpdating ? "Đang cập nhật..." : "Xác nhận thay đổi"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default AdminClassesPage;
