import React, { useCallback, useEffect, useState } from "react";
import {
    CheckCircle2,
    Lock,
    Search,
    Shield,
    ShieldAlert,
    Unlock,
    UserCog,
    Users
} from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "../../contexts/AuthContext";
import adminService from "../../services/adminService";
import { AdminUserItem, UserRole, UserStatus } from "../../types";
import { Avatar, AvatarFallback } from "../../components/ui/avatar";
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

function getRoleBadge(role: UserRole): React.ReactNode {
    switch (role) {
        case "ADMIN":
            return (
                <Badge variant="danger" icon={<ShieldAlert className="h-3 w-3" />}>
                    Quản trị viên
                </Badge>
            );
        case "TEACHER":
            return (
                <Badge variant="default" icon={<Shield className="h-3 w-3" />}>
                    Giảng viên
                </Badge>
            );
        case "STUDENT":
        default:
            return (
                <Badge variant="secondary" icon={<Users className="h-3 w-3" />}>
                    Sinh viên
                </Badge>
            );
    }
}

function getStatusBadge(status: UserStatus): React.ReactNode {
    switch (status) {
        case "ACTIVE":
            return (
                <Badge variant="success" icon={<CheckCircle2 className="h-3 w-3" />}>
                    Đang hoạt động
                </Badge>
            );
        case "LOCKED":
            return (
                <Badge variant="danger" icon={<Lock className="h-3 w-3" />}>
                    Đã khóa
                </Badge>
            );
        case "DISABLED":
        default:
            return (
                <Badge variant="outline" icon={<Lock className="h-3 w-3" />}>
                    Vô hiệu hóa
                </Badge>
            );
    }
}

export const AdminUsersPage: React.FC = () => {
    const { user: currentUser } = useAuth();

    const [users, setUsers] = useState<AdminUserItem[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Filters & Pagination
    const [keyword, setKeyword] = useState<string>("");
    const [roleFilter, setRoleFilter] = useState<"ALL" | UserRole>("ALL");
    const [statusFilter, setStatusFilter] = useState<"ALL" | UserStatus>("ALL");
    const [page, setPage] = useState<number>(0);
    const [totalPages, setTotalPages] = useState<number>(1);
    const [totalElements, setTotalElements] = useState<number>(0);

    // Role change confirmation modal
    const [roleModalUser, setRoleModalUser] = useState<AdminUserItem | null>(null);
    const [nextRole, setNextRole] = useState<UserRole>("STUDENT");
    const [isUpdatingRole, setIsUpdatingRole] = useState<boolean>(false);

    // Status change confirmation modal
    const [statusModalUser, setStatusModalUser] = useState<AdminUserItem | null>(null);
    const [nextStatus, setNextStatus] = useState<UserStatus>("LOCKED");
    const [isUpdatingStatus, setIsUpdatingStatus] = useState<boolean>(false);

    const fetchUsers = useCallback(async (): Promise<void> => {
        try {
            setIsLoading(true);
            const res = await adminService.listUsers({
                keyword: keyword.trim() || undefined,
                role: roleFilter === "ALL" ? undefined : roleFilter,
                status: statusFilter === "ALL" ? undefined : statusFilter,
                page,
                size: 15
            });
            setUsers(res.items);
            setTotalPages(res.meta?.totalPages ?? 1);
            setTotalElements(res.meta?.totalElements ?? res.items.length);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải danh sách người dùng.");
        } finally {
            setIsLoading(false);
        }
    }, [keyword, page, roleFilter, statusFilter]);

    useEffect(() => {
        void fetchUsers();
    }, [fetchUsers]);

    const handleSearchSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        void fetchUsers();
    };

    const openRoleModal = (target: AdminUserItem) => {
        setRoleModalUser(target);
        setNextRole(target.role);
    };

    const handleConfirmRoleChange = async () => {
        if (!roleModalUser) return;
        try {
            setIsUpdatingRole(true);
            const updated = await adminService.updateUserRole(
                roleModalUser.id,
                nextRole,
                roleModalUser.version
            );
            setUsers((prev) =>
                prev.map((u) =>
                    u.id === updated.id
                        ? { ...u, role: updated.role, version: updated.version }
                        : u
                )
            );
            toast.success(`Đã đổi vai trò của ${roleModalUser.fullName} thành ${nextRole}.`);
            setRoleModalUser(null);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể cập nhật vai trò.");
        } finally {
            setIsUpdatingRole(false);
        }
    };

    const openStatusModal = (target: AdminUserItem) => {
        setStatusModalUser(target);
        setNextStatus(target.status === "ACTIVE" ? "LOCKED" : "ACTIVE");
    };

    const handleConfirmStatusChange = async () => {
        if (!statusModalUser) return;
        try {
            setIsUpdatingStatus(true);
            const updated = await adminService.updateUserStatus(
                statusModalUser.id,
                nextStatus,
                statusModalUser.version
            );
            setUsers((prev) =>
                prev.map((u) =>
                    u.id === updated.id
                        ? { ...u, status: updated.status, version: updated.version }
                        : u
                )
            );
            toast.success(
                nextStatus === "LOCKED"
                    ? `Đã khóa tài khoản ${statusModalUser.fullName}.`
                    : `Đã mở khóa tài khoản ${statusModalUser.fullName}.`
            );
            setStatusModalUser(null);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể cập nhật trạng thái tài khoản.");
        } finally {
            setIsUpdatingStatus(false);
        }
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2.5">
                        <Users className="h-6 w-6 text-primary" />
                        <span>Quản lý Người dùng</span>
                    </h1>
                    <p className="text-sm text-muted-foreground mt-1">
                        Tra cứu, phân quyền vai trò và khóa/mở khóa tài khoản trong hệ thống ({totalElements} tài khoản).
                    </p>
                </div>
            </div>

            {/* Filters */}
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
                <form onSubmit={handleSearchSubmit} className="flex items-center gap-2 w-full lg:w-96">
                    <div className="relative flex-1">
                        <Search className="h-4 w-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                        <Input
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            placeholder="Tìm theo tên hoặc email..."
                            className="pl-9 h-9 text-sm"
                        />
                    </div>
                    <Button type="submit" variant="outline" size="sm" className="h-9 text-xs shrink-0">
                        Tìm kiếm
                    </Button>
                </form>

                <div className="flex flex-wrap items-center gap-3 text-xs">
                    <div className="flex items-center gap-1.5">
                        <span className="text-muted-foreground font-medium">Vai trò:</span>
                        <select
                            value={roleFilter}
                            onChange={(e) => {
                                setRoleFilter(e.target.value as "ALL" | UserRole);
                                setPage(0);
                            }}
                            className="h-9 rounded-lg border border-border bg-surface px-2.5 text-xs font-medium"
                        >
                            <option value="ALL">Tất cả vai trò</option>
                            <option value="STUDENT">Sinh viên</option>
                            <option value="TEACHER">Giảng viên</option>
                            <option value="ADMIN">Quản trị viên</option>
                        </select>
                    </div>

                    <div className="flex items-center gap-1.5">
                        <span className="text-muted-foreground font-medium">Trạng thái:</span>
                        <select
                            value={statusFilter}
                            onChange={(e) => {
                                setStatusFilter(e.target.value as "ALL" | UserStatus);
                                setPage(0);
                            }}
                            className="h-9 rounded-lg border border-border bg-surface px-2.5 text-xs font-medium"
                        >
                            <option value="ALL">Tất cả trạng thái</option>
                            <option value="ACTIVE">Đang hoạt động</option>
                            <option value="LOCKED">Đã khóa</option>
                        </select>
                    </div>
                </div>
            </div>

            {/* Table */}
            {isLoading ? (
                <LoadingState label="Đang tải danh sách người dùng..." />
            ) : error ? (
                <ErrorBanner
                    title="Lỗi tải danh sách người dùng"
                    message={error}
                    onRetry={() => void fetchUsers()}
                />
            ) : users.length === 0 ? (
                <EmptyState
                    icon={<Users className="h-12 w-12 text-slate-300" />}
                    title="Không tìm thấy người dùng nào"
                    description="Thử thay đổi từ khóa tìm kiếm hoặc bộ lọc vai trò/trạng thái."
                />
            ) : (
                <div className="space-y-4">
                    <Card className="border-border shadow-sm overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm text-left">
                                <thead className="bg-slate-50 text-slate-700 text-xs uppercase font-semibold border-b border-border">
                                    <tr>
                                        <th className="py-3.5 px-4">Người dùng</th>
                                        <th className="py-3.5 px-4">Vai trò</th>
                                        <th className="py-3.5 px-4">Trạng thái</th>
                                        <th className="py-3.5 px-4">Ngày tạo</th>
                                        <th className="py-3.5 px-4 text-right">Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-border">
                                    {users.map((item) => {
                                        const isSelf = item.id === currentUser?.id;
                                        const initials = item.fullName
                                            .split(" ")
                                            .filter(Boolean)
                                            .slice(-2)
                                            .map((p) => p[0])
                                            .join("")
                                            .toUpperCase();

                                        return (
                                            <tr key={item.id} className="hover:bg-slate-50/70 transition-colors">
                                                <td className="py-3.5 px-4">
                                                    <div className="flex items-center gap-3">
                                                        <Avatar className="h-9 w-9 shrink-0">
                                                            <AvatarFallback className="text-xs">
                                                                {initials || "ND"}
                                                            </AvatarFallback>
                                                        </Avatar>
                                                        <div className="min-w-0">
                                                            <p className="font-semibold text-slate-900 truncate">
                                                                {item.fullName}{" "}
                                                                {isSelf && (
                                                                    <span className="text-[11px] text-primary font-medium">
                                                                        (Bạn)
                                                                    </span>
                                                                )}
                                                            </p>
                                                            <p className="text-xs text-muted-foreground truncate">
                                                                {item.email} • ID: {item.id}
                                                            </p>
                                                        </div>
                                                    </div>
                                                </td>
                                                <td className="py-3.5 px-4">{getRoleBadge(item.role)}</td>
                                                <td className="py-3.5 px-4">{getStatusBadge(item.status)}</td>
                                                <td className="py-3.5 px-4 text-xs text-muted-foreground">
                                                    {new Date(item.createdAt).toLocaleDateString("vi-VN")}
                                                </td>
                                                <td className="py-3.5 px-4 text-right">
                                                    <div className="flex items-center justify-end gap-2">
                                                        <Button
                                                            size="sm"
                                                            variant="outline"
                                                            disabled={isSelf}
                                                            onClick={() => openRoleModal(item)}
                                                            className="h-8 text-xs gap-1"
                                                        >
                                                            <UserCog className="h-3.5 w-3.5" />
                                                            <span>Đổi vai trò</span>
                                                        </Button>

                                                        <Button
                                                            size="sm"
                                                            variant="outline"
                                                            disabled={isSelf}
                                                            onClick={() => openStatusModal(item)}
                                                            className={`h-8 text-xs gap-1 ${
                                                                item.status === "ACTIVE"
                                                                    ? "text-rose-600 border-rose-200 hover:bg-rose-50"
                                                                    : "text-emerald-700 border-emerald-200 hover:bg-emerald-50"
                                                            }`}
                                                        >
                                                            {item.status === "ACTIVE" ? (
                                                                <>
                                                                    <Lock className="h-3.5 w-3.5" />
                                                                    <span>Khóa</span>
                                                                </>
                                                            ) : (
                                                                <>
                                                                    <Unlock className="h-3.5 w-3.5" />
                                                                    <span>Mở khóa</span>
                                                                </>
                                                            )}
                                                        </Button>
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })}
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

            {/* Confirm Role Change Dialog */}
            <Dialog open={!!roleModalUser} onOpenChange={(open) => !open && setRoleModalUser(null)}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>Xác nhận đổi vai trò tài khoản</DialogTitle>
                        <DialogDescription>
                            Thay đổi quyền truy cập hệ thống cho tài khoản{" "}
                            <strong>{roleModalUser?.fullName}</strong> ({roleModalUser?.email}).
                        </DialogDescription>
                    </DialogHeader>

                    <div className="space-y-3 py-2">
                        <label className="text-xs font-semibold text-slate-700">Chọn vai trò mới</label>
                        <select
                            value={nextRole}
                            onChange={(e) => setNextRole(e.target.value as UserRole)}
                            disabled={isUpdatingRole}
                            className="w-full text-sm rounded-lg border border-border p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20"
                        >
                            <option value="STUDENT">Sinh viên (STUDENT)</option>
                            <option value="TEACHER">Giảng viên (TEACHER)</option>
                            <option value="ADMIN">Quản trị viên (ADMIN)</option>
                        </select>
                    </div>

                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => setRoleModalUser(null)}
                            disabled={isUpdatingRole}
                        >
                            Hủy
                        </Button>
                        <Button
                            type="button"
                            onClick={() => void handleConfirmRoleChange()}
                            disabled={isUpdatingRole || nextRole === roleModalUser?.role}
                        >
                            {isUpdatingRole ? "Đang cập nhật..." : "Xác nhận đổi vai trò"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Confirm Status Lock/Unlock Dialog */}
            <Dialog
                open={!!statusModalUser}
                onOpenChange={(open) => !open && setStatusModalUser(null)}
            >
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>
                            {nextStatus === "LOCKED"
                                ? "Xác nhận khóa tài khoản"
                                : "Xác nhận mở khóa tài khoản"}
                        </DialogTitle>
                        <DialogDescription>
                            {nextStatus === "LOCKED"
                                ? `Tài khoản ${statusModalUser?.fullName} sẽ bị đăng xuất và không thể đăng nhập cho đến khi được mở khóa.`
                                : `Khôi phục quyền đăng nhập cho tài khoản ${statusModalUser?.fullName}.`}
                        </DialogDescription>
                    </DialogHeader>

                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => setStatusModalUser(null)}
                            disabled={isUpdatingStatus}
                        >
                            Hủy
                        </Button>
                        <Button
                            type="button"
                            variant={nextStatus === "LOCKED" ? "danger" : "default"}
                            onClick={() => void handleConfirmStatusChange()}
                            disabled={isUpdatingStatus}
                        >
                            {isUpdatingStatus
                                ? "Đang xử lý..."
                                : nextStatus === "LOCKED"
                                ? "Khóa tài khoản"
                                : "Mở khóa tài khoản"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default AdminUsersPage;
