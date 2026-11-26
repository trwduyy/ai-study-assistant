import React, { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
    CheckCircle2,
    Clock,
    FileClock,
    Flag,
    Globe,
    HardDrive,
    Save,
    Settings,
    ShieldCheck,
    Sparkles
} from "lucide-react";
import { toast } from "sonner";
import adminService from "../../services/adminService";
import {
    AuditLogItem,
    ReportItem,
    ReportStatus,
    SystemSettingItem
} from "../../types";
import { Badge } from "../../components/ui/badge";
import { Button } from "../../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../../components/ui/card";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../../components/ui/tabs";
import { EmptyState } from "../../components/common/EmptyState";
import { ErrorBanner } from "../../components/common/ErrorBanner";
import { LoadingState } from "../../components/common/LoadingState";

function getReportTypeLabel(type: string): string {
    switch (type) {
        case "AI_INACCURATE":
            return "AI trả lời sai";
        case "CLASS_ABUSE":
            return "Vi phạm lớp học";
        case "CHAT_ABUSE":
            return "Tin nhắn vi phạm";
        case "OTHER":
            return "Khác";
        default:
            return type;
    }
}

export const AdminGovernancePage: React.FC = () => {
    const location = useLocation();
    const navigate = useNavigate();

    const defaultTab = location.pathname.includes("/settings")
        ? "settings"
        : location.pathname.includes("/audit-logs")
        ? "audit"
        : "reports";
    const [activeTab, setActiveTab] = useState<string>(defaultTab);

    useEffect(() => {
        if (location.pathname.includes("/settings")) {
            setActiveTab("settings");
        } else if (location.pathname.includes("/audit-logs")) {
            setActiveTab("audit");
        } else if (location.pathname.includes("/reports")) {
            setActiveTab("reports");
        }
    }, [location.pathname]);

    // -------------------------------------------------------------------------
    // TAB 1: Reports
    // -------------------------------------------------------------------------
    const [reports, setReports] = useState<ReportItem[]>([]);
    const [isLoadingReports, setIsLoadingReports] = useState<boolean>(true);
    const [reportsError, setReportsError] = useState<string | null>(null);
    const [reportStatusFilter, setReportStatusFilter] = useState<"ALL" | ReportStatus>("ALL");
    const [reportTypeFilter, setReportTypeFilter] = useState<string>("ALL");
    const [reportsPage, setReportsPage] = useState<number>(0);
    const [reportsTotalPages, setReportsTotalPages] = useState<number>(1);

    const [selectedReport, setSelectedReport] = useState<ReportItem | null>(null);
    const [resolutionNote, setResolutionNote] = useState<string>("");
    const [isResolvingReport, setIsResolvingReport] = useState<boolean>(false);

    // -------------------------------------------------------------------------
    // TAB 2: System Settings
    // -------------------------------------------------------------------------
    const [settings, setSettings] = useState<SystemSettingItem[]>([]);
    const [settingValues, setSettingValues] = useState<Record<string, string>>({});
    const [isLoadingSettings, setIsLoadingSettings] = useState<boolean>(false);
    const [settingsError, setSettingsError] = useState<string | null>(null);
    const [isSavingSettings, setIsSavingSettings] = useState<boolean>(false);

    // -------------------------------------------------------------------------
    // TAB 3: Audit Logs
    // -------------------------------------------------------------------------
    const [auditLogs, setAuditLogs] = useState<AuditLogItem[]>([]);
    const [isLoadingAudit, setIsLoadingAudit] = useState<boolean>(false);
    const [auditError, setAuditError] = useState<string | null>(null);
    const [auditActionFilter, setAuditActionFilter] = useState<string>("");
    const [auditPage, setAuditPage] = useState<number>(0);
    const [auditTotalPages, setAuditTotalPages] = useState<number>(1);

    // Fetch Reports
    const fetchReports = useCallback(async (): Promise<void> => {
        try {
            setIsLoadingReports(true);
            const res = await adminService.listReports({
                status: reportStatusFilter === "ALL" ? undefined : reportStatusFilter,
                type: reportTypeFilter === "ALL" ? undefined : reportTypeFilter,
                page: reportsPage,
                size: 15
            });
            setReports(res.items);
            setReportsTotalPages(res.meta?.totalPages ?? 1);
            setReportsError(null);
        } catch (err: unknown) {
            setReportsError(err instanceof Error ? err.message : "Không thể tải danh sách báo cáo.");
        } finally {
            setIsLoadingReports(false);
        }
    }, [reportStatusFilter, reportTypeFilter, reportsPage]);

    // Fetch System Settings
    const fetchSettings = useCallback(async (): Promise<void> => {
        try {
            setIsLoadingSettings(true);
            const data = await adminService.getSettings();
            setSettings(data);
            const map: Record<string, string> = {};
            data.forEach((s) => {
                map[s.settingKey] = s.settingValue;
            });
            setSettingValues(map);
            setSettingsError(null);
        } catch (err: unknown) {
            setSettingsError(err instanceof Error ? err.message : "Không thể tải cấu hình hệ thống.");
        } finally {
            setIsLoadingSettings(false);
        }
    }, []);

    // Fetch Audit Logs
    const fetchAuditLogs = useCallback(async (): Promise<void> => {
        try {
            setIsLoadingAudit(true);
            const res = await adminService.listAuditLogs({
                action: auditActionFilter.trim() || undefined,
                page: auditPage,
                size: 20
            });
            setAuditLogs(res.items);
            setAuditTotalPages(res.meta?.totalPages ?? 1);
            setAuditError(null);
        } catch (err: unknown) {
            setAuditError(err instanceof Error ? err.message : "Không thể tải nhật ký hệ thống.");
        } finally {
            setIsLoadingAudit(false);
        }
    }, [auditActionFilter, auditPage]);

    useEffect(() => {
        if (activeTab === "reports") void fetchReports();
        if (activeTab === "settings") void fetchSettings();
        if (activeTab === "audit") void fetchAuditLogs();
    }, [activeTab, fetchAuditLogs, fetchReports, fetchSettings]);

    // Handle Report Resolution
    const openReportDialog = (report: ReportItem) => {
        setSelectedReport(report);
        setResolutionNote(report.resolutionNote || "");
    };

    const handleResolveReport = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedReport) return;
        try {
            setIsResolvingReport(true);
            const updated = await adminService.resolveReport(selectedReport.id, {
                status: "RESOLVED",
                resolutionNote: resolutionNote.trim() || undefined
            });
            setReports((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
            toast.success(`Đã đánh dấu báo cáo #${selectedReport.id} là đã xử lý.`);
            setSelectedReport(null);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xử lý báo cáo thất bại.");
        } finally {
            setIsResolvingReport(false);
        }
    };

    // Handle Save System Settings
    const handleSaveSettings = async (e: React.FormEvent) => {
        e.preventDefault();
        const quotaVal = Number(settingValues.AI_DAILY_QUOTA_PER_USER ?? "50");
        const uploadVal = Number(settingValues.MAX_UPLOAD_FILE_SIZE_MB ?? "20");

        if (!Number.isInteger(quotaVal) || quotaVal <= 0) {
            toast.error("Hạn mức AI mỗi ngày phải là số nguyên dương.");
            return;
        }
        if (!Number.isInteger(uploadVal) || uploadVal <= 0) {
            toast.error("Dung lượng tệp tối đa (MB) phải là số nguyên dương.");
            return;
        }

        try {
            setIsSavingSettings(true);
            const payload = Object.entries(settingValues).map(([settingKey, settingValue]) => ({
                settingKey,
                settingValue
            }));
            const updated = await adminService.updateSettings(payload);
            setSettings(updated);
            toast.success("Đã cập nhật cấu hình hệ thống thành công!");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lưu cấu hình hệ thống thất bại.");
        } finally {
            setIsSavingSettings(false);
        }
    };

    const googlePersonalAllowed =
        (settingValues.GOOGLE_ALLOW_PERSONAL_ACCOUNTS ?? "false").toLowerCase() === "true";

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2.5">
                        <ShieldCheck className="h-6 w-6 text-primary" />
                        <span>Báo cáo, Cấu hình & Nhật ký Hệ thống</span>
                    </h1>
                    <p className="text-sm text-muted-foreground mt-1">
                        Xử lý phản hồi vi phạm, thiết lập hạn mức AI/tệp tải lên và theo dõi nhật ký kiểm toán quản trị.
                    </p>
                </div>
            </div>

            <Tabs
                value={activeTab}
                onValueChange={(val) => {
                    setActiveTab(val);
                    if (val === "reports") navigate("/admin/reports");
                    if (val === "settings") navigate("/admin/settings");
                }}
            >
                <TabsList>
                    <TabsTrigger value="reports" className="gap-2">
                        <Flag className="h-4 w-4" />
                        <span>Báo cáo vi phạm & AI</span>
                    </TabsTrigger>
                    <TabsTrigger value="settings" className="gap-2">
                        <Settings className="h-4 w-4" />
                        <span>Cấu hình hệ thống</span>
                    </TabsTrigger>
                    <TabsTrigger value="audit" className="gap-2">
                        <FileClock className="h-4 w-4" />
                        <span>Nhật ký hệ thống (Audit Log)</span>
                    </TabsTrigger>
                </TabsList>

                {/* TAB 1: REPORTS */}
                <TabsContent value="reports" className="space-y-4">
                    <div className="flex flex-wrap items-center gap-3 text-xs">
                        <div className="flex items-center gap-1.5">
                            <span className="text-muted-foreground font-medium">Trạng thái:</span>
                            <select
                                value={reportStatusFilter}
                                onChange={(e) => {
                                    setReportStatusFilter(e.target.value as "ALL" | ReportStatus);
                                    setReportsPage(0);
                                }}
                                className="h-9 rounded-lg border border-border bg-surface px-2.5 text-xs font-medium"
                            >
                                <option value="ALL">Tất cả trạng thái</option>
                                <option value="OPEN">Chờ xử lý</option>
                                <option value="RESOLVED">Đã xử lý</option>
                            </select>
                        </div>

                        <div className="flex items-center gap-1.5">
                            <span className="text-muted-foreground font-medium">Phân loại:</span>
                            <select
                                value={reportTypeFilter}
                                onChange={(e) => {
                                    setReportTypeFilter(e.target.value);
                                    setReportsPage(0);
                                }}
                                className="h-9 rounded-lg border border-border bg-surface px-2.5 text-xs font-medium"
                            >
                                <option value="ALL">Tất cả loại báo cáo</option>
                                <option value="AI_INACCURATE">AI trả lời sai</option>
                                <option value="CLASS_ABUSE">Vi phạm lớp học</option>
                                <option value="CHAT_ABUSE">Tin nhắn vi phạm</option>
                                <option value="OTHER">Khác</option>
                            </select>
                        </div>
                    </div>

                    {isLoadingReports ? (
                        <LoadingState label="Đang tải danh sách báo cáo..." />
                    ) : reportsError ? (
                        <ErrorBanner
                            title="Lỗi tải báo cáo"
                            message={reportsError}
                            onRetry={() => void fetchReports()}
                        />
                    ) : reports.length === 0 ? (
                        <EmptyState
                            icon={<Flag className="h-12 w-12 text-slate-300" />}
                            title="Không có báo cáo nào"
                            description="Chưa có báo cáo vi phạm hoặc phản hồi nào khớp với bộ lọc hiện tại."
                        />
                    ) : (
                        <div className="space-y-4">
                            <Card className="border-border shadow-sm overflow-hidden">
                                <div className="overflow-x-auto">
                                    <table className="w-full text-sm text-left">
                                        <thead className="bg-slate-50 text-slate-700 text-xs uppercase font-semibold border-b border-border">
                                            <tr>
                                                <th className="py-3.5 px-4">Người gửi</th>
                                                <th className="py-3.5 px-4">Loại báo cáo</th>
                                                <th className="py-3.5 px-4">Nội dung</th>
                                                <th className="py-3.5 px-4">Trạng thái</th>
                                                <th className="py-3.5 px-4">Thời điểm</th>
                                                <th className="py-3.5 px-4 text-right">Thao tác</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-border">
                                            {reports.map((report) => (
                                                <tr
                                                    key={report.id}
                                                    onClick={() => openReportDialog(report)}
                                                    className="hover:bg-slate-50/80 cursor-pointer transition-colors"
                                                >
                                                    <td className="py-3.5 px-4">
                                                        <p className="font-semibold text-slate-900">
                                                            {report.reporterName}
                                                        </p>
                                                        <p className="text-xs text-muted-foreground">
                                                            {report.reporterEmail}
                                                        </p>
                                                    </td>
                                                    <td className="py-3.5 px-4">
                                                        <Badge variant="outline" className="text-xs">
                                                            {getReportTypeLabel(report.type)}
                                                        </Badge>
                                                    </td>
                                                    <td className="py-3.5 px-4 max-w-xs">
                                                        <p className="text-xs text-slate-700 line-clamp-2">
                                                            {report.detail}
                                                        </p>
                                                    </td>
                                                    <td className="py-3.5 px-4">
                                                        {report.status === "RESOLVED" ? (
                                                            <Badge
                                                                variant="success"
                                                                icon={<CheckCircle2 className="h-3 w-3" />}
                                                            >
                                                                Đã xử lý
                                                            </Badge>
                                                        ) : (
                                                            <Badge
                                                                variant="warning"
                                                                icon={<Clock className="h-3 w-3" />}
                                                            >
                                                                Chờ xử lý
                                                            </Badge>
                                                        )}
                                                    </td>
                                                    <td className="py-3.5 px-4 text-xs text-muted-foreground">
                                                        {new Date(report.createdAt).toLocaleString("vi-VN")}
                                                    </td>
                                                    <td className="py-3.5 px-4 text-right">
                                                        <Button
                                                            size="sm"
                                                            variant="outline"
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                openReportDialog(report);
                                                            }}
                                                            className="h-8 text-xs"
                                                        >
                                                            {report.status === "OPEN" ? "Xử lý" : "Xem chi tiết"}
                                                        </Button>
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </Card>

                            {reportsTotalPages > 1 && (
                                <div className="flex justify-center pt-2">
                                    <Pagination
                                        page={reportsPage}
                                        totalPages={reportsTotalPages}
                                        onPageChange={setReportsPage}
                                    />
                                </div>
                            )}
                        </div>
                    )}
                </TabsContent>

                {/* TAB 2: SYSTEM SETTINGS */}
                <TabsContent value="settings" className="space-y-4">
                    {isLoadingSettings ? (
                        <LoadingState label="Đang tải cấu hình hệ thống..." />
                    ) : settingsError ? (
                        <ErrorBanner
                            title="Lỗi tải cấu hình"
                            message={settingsError}
                            onRetry={() => void fetchSettings()}
                        />
                    ) : (
                        <Card className="border-border shadow-sm">
                            <CardHeader className="pb-3 border-b border-border">
                                <CardTitle className="text-base font-bold text-slate-900">
                                    Thiết lập tham số vận hành hệ thống
                                </CardTitle>
                            </CardHeader>
                            <CardContent className="p-6">
                                <form onSubmit={(e) => void handleSaveSettings(e)} className="space-y-6">
                                    {/* AI Daily Quota */}
                                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-center pb-5 border-b border-border">
                                        <div className="md:col-span-2 space-y-1">
                                            <label className="text-sm font-bold text-slate-900 flex items-center gap-2">
                                                <Sparkles className="h-4 w-4 text-primary" />
                                                <span>Hạn mức lượt AI / ngày / người dùng</span>
                                            </label>
                                            <p className="text-xs text-muted-foreground">
                                                Khóa cấu hình: <code>AI_DAILY_QUOTA_PER_USER</code>. Tổng số AI Job và câu hỏi AI Chat tối đa trong 1 ngày UTC.
                                            </p>
                                        </div>
                                        <Input
                                            type="number"
                                            min={1}
                                            value={settingValues.AI_DAILY_QUOTA_PER_USER ?? "50"}
                                            onChange={(e) =>
                                                setSettingValues((prev) => ({
                                                    ...prev,
                                                    AI_DAILY_QUOTA_PER_USER: e.target.value
                                                }))
                                            }
                                            disabled={isSavingSettings}
                                            className="font-semibold"
                                        />
                                    </div>

                                    {/* Max Upload File Size */}
                                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-center pb-5 border-b border-border">
                                        <div className="md:col-span-2 space-y-1">
                                            <label className="text-sm font-bold text-slate-900 flex items-center gap-2">
                                                <HardDrive className="h-4 w-4 text-teal-700" />
                                                <span>Dung lượng tệp tải lên tối đa (MB)</span>
                                            </label>
                                            <p className="text-xs text-muted-foreground">
                                                Khóa cấu hình: <code>MAX_UPLOAD_FILE_SIZE_MB</code>. Giới hạn dung lượng tối đa mỗi tệp bài giảng, bài tập hoặc tài liệu lớp.
                                            </p>
                                        </div>
                                        <Input
                                            type="number"
                                            min={1}
                                            value={settingValues.MAX_UPLOAD_FILE_SIZE_MB ?? "20"}
                                            onChange={(e) =>
                                                setSettingValues((prev) => ({
                                                    ...prev,
                                                    MAX_UPLOAD_FILE_SIZE_MB: e.target.value
                                                }))
                                            }
                                            disabled={isSavingSettings}
                                            className="font-semibold"
                                        />
                                    </div>

                                    {/* Google Allow Personal Accounts */}
                                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-center pb-5 border-b border-border">
                                        <div className="md:col-span-2 space-y-1">
                                            <label className="text-sm font-bold text-slate-900 flex items-center gap-2">
                                                <Globe className="h-4 w-4 text-emerald-600" />
                                                <span>Cho phép kết nối tài khoản Google cá nhân</span>
                                            </label>
                                            <p className="text-xs text-muted-foreground">
                                                Khóa cấu hình: <code>GOOGLE_ALLOW_PERSONAL_ACCOUNTS</code>. Khi tắt, chỉ tài khoản email học viện được phép liên kết Google Calendar.
                                            </p>
                                        </div>
                                        <div className="flex items-center justify-start md:justify-end gap-3">
                                            <button
                                                type="button"
                                                role="switch"
                                                aria-checked={googlePersonalAllowed}
                                                disabled={isSavingSettings}
                                                onClick={() =>
                                                    setSettingValues((prev) => ({
                                                        ...prev,
                                                        GOOGLE_ALLOW_PERSONAL_ACCOUNTS: googlePersonalAllowed
                                                            ? "false"
                                                            : "true"
                                                    }))
                                                }
                                                className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors ${
                                                    googlePersonalAllowed ? "bg-primary" : "bg-slate-200"
                                                }`}
                                            >
                                                <span
                                                    className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
                                                        googlePersonalAllowed ? "translate-x-5" : "translate-x-0"
                                                    }`}
                                                />
                                            </button>
                                            <span className="text-xs font-semibold text-slate-700">
                                                {googlePersonalAllowed ? "Đang bật" : "Đang tắt"}
                                            </span>
                                        </div>
                                    </div>

                                    {settings.some(
                                        (s) =>
                                            s.settingKey !== "AI_DAILY_QUOTA_PER_USER" &&
                                            s.settingKey !== "MAX_UPLOAD_FILE_SIZE_MB" &&
                                            s.settingKey !== "GOOGLE_ALLOW_PERSONAL_ACCOUNTS"
                                    ) && (
                                        <div className="space-y-3">
                                            <p className="text-xs font-bold uppercase text-muted-foreground">
                                                Tham số mở rộng khác
                                            </p>
                                            {settings
                                                .filter(
                                                    (s) =>
                                                        s.settingKey !== "AI_DAILY_QUOTA_PER_USER" &&
                                                        s.settingKey !== "MAX_UPLOAD_FILE_SIZE_MB" &&
                                                        s.settingKey !== "GOOGLE_ALLOW_PERSONAL_ACCOUNTS"
                                                )
                                                .map((s) => (
                                                    <div
                                                        key={s.settingKey}
                                                        className="grid grid-cols-1 md:grid-cols-3 gap-4 items-center"
                                                    >
                                                        <div className="md:col-span-2">
                                                            <p className="text-xs font-bold text-slate-800">
                                                                {s.settingKey}
                                                            </p>
                                                            {s.description && (
                                                                <p className="text-xs text-muted-foreground">
                                                                    {s.description}
                                                                </p>
                                                            )}
                                                        </div>
                                                        <Input
                                                            value={settingValues[s.settingKey] ?? s.settingValue}
                                                            onChange={(e) =>
                                                                setSettingValues((prev) => ({
                                                                    ...prev,
                                                                    [s.settingKey]: e.target.value
                                                                }))
                                                            }
                                                            disabled={isSavingSettings}
                                                        />
                                                    </div>
                                                ))}
                                        </div>
                                    )}

                                    <div className="flex justify-end">
                                        <Button
                                            type="submit"
                                            disabled={isSavingSettings}
                                            className="gap-2"
                                        >
                                            <Save className="h-4 w-4" />
                                            <span>
                                                {isSavingSettings ? "Đang lưu..." : "Lưu cấu hình hệ thống"}
                                            </span>
                                        </Button>
                                    </div>
                                </form>
                            </CardContent>
                        </Card>
                    )}
                </TabsContent>

                {/* TAB 3: AUDIT LOGS */}
                <TabsContent value="audit" className="space-y-4">
                    <form
                        onSubmit={(e) => {
                            e.preventDefault();
                            setAuditPage(0);
                            void fetchAuditLogs();
                        }}
                        className="flex items-center gap-2 w-full sm:w-96"
                    >
                        <Input
                            value={auditActionFilter}
                            onChange={(e) => setAuditActionFilter(e.target.value)}
                            placeholder="Lọc theo mã hành động (ví dụ: REPORT_RESOLVED)..."
                            className="h-9 text-xs"
                        />
                        <Button type="submit" variant="outline" size="sm" className="h-9 text-xs shrink-0">
                            Lọc
                        </Button>
                    </form>

                    {isLoadingAudit ? (
                        <LoadingState label="Đang tải nhật ký hệ thống..." />
                    ) : auditError ? (
                        <ErrorBanner
                            title="Lỗi tải nhật ký hệ thống"
                            message={auditError}
                            onRetry={() => void fetchAuditLogs()}
                        />
                    ) : auditLogs.length === 0 ? (
                        <EmptyState
                            icon={<FileClock className="h-12 w-12 text-slate-300" />}
                            title="Chưa có bản ghi nhật ký nào"
                            description="Mọi hành động quản trị quan trọng sẽ được lưu lại tại đây."
                        />
                    ) : (
                        <div className="space-y-4">
                            <Card className="border-border shadow-sm overflow-hidden">
                                <div className="overflow-x-auto">
                                    <table className="w-full text-sm text-left">
                                        <thead className="bg-slate-50 text-slate-700 text-xs uppercase font-semibold border-b border-border">
                                            <tr>
                                                <th className="py-3.5 px-4">Thời điểm</th>
                                                <th className="py-3.5 px-4">Người thực hiện</th>
                                                <th className="py-3.5 px-4">Hành động</th>
                                                <th className="py-3.5 px-4">Đối tượng</th>
                                                <th className="py-3.5 px-4">Chi tiết</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-border">
                                            {auditLogs.map((log) => (
                                                <tr key={log.id} className="hover:bg-slate-50/70">
                                                    <td className="py-3 px-4 text-xs text-muted-foreground whitespace-nowrap">
                                                        {new Date(log.createdAt).toLocaleString("vi-VN")}
                                                    </td>
                                                    <td className="py-3 px-4 text-xs font-medium text-slate-800">
                                                        {log.userEmail || `User #${log.userId ?? "Hệ thống"}`}
                                                    </td>
                                                    <td className="py-3 px-4">
                                                        <Badge variant="outline" className="font-mono text-[11px]">
                                                            {log.action}
                                                        </Badge>
                                                    </td>
                                                    <td className="py-3 px-4 text-xs text-slate-600">
                                                        {log.objectType
                                                            ? `${log.objectType}${log.objectId ? ` #${log.objectId}` : ""}`
                                                            : "-"}
                                                    </td>
                                                    <td className="py-3 px-4 text-xs font-mono text-slate-500 max-w-xs truncate">
                                                        {log.metadataJson
                                                            ? JSON.stringify(log.metadataJson)
                                                            : "-"}
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </Card>

                            {auditTotalPages > 1 && (
                                <div className="flex justify-center pt-2">
                                    <Pagination
                                        page={auditPage}
                                        totalPages={auditTotalPages}
                                        onPageChange={setAuditPage}
                                    />
                                </div>
                            )}
                        </div>
                    )}
                </TabsContent>
            </Tabs>

            {/* Report Detail & Resolve Dialog */}
            <Dialog
                open={!!selectedReport}
                onOpenChange={(open) => !open && setSelectedReport(null)}
            >
                <DialogContent className="max-w-lg">
                    {selectedReport && (
                        <>
                            <DialogHeader>
                                <DialogTitle className="flex items-center gap-2">
                                    <Flag className="h-5 w-5 text-primary" />
                                    <span>Chi tiết báo cáo #{selectedReport.id}</span>
                                </DialogTitle>
                                <DialogDescription>
                                    Gửi bởi <strong>{selectedReport.reporterName}</strong> (
                                    {selectedReport.reporterEmail})
                                </DialogDescription>
                            </DialogHeader>

                            <form onSubmit={(e) => void handleResolveReport(e)} className="space-y-4 py-2">
                                <div className="flex flex-wrap items-center gap-2 text-xs">
                                    <Badge variant="outline">
                                        {getReportTypeLabel(selectedReport.type)}
                                    </Badge>
                                    <Badge variant="info">
                                        Đối tượng: {selectedReport.targetType} #{selectedReport.targetId}
                                    </Badge>
                                    <Badge
                                        variant={
                                            selectedReport.status === "RESOLVED" ? "success" : "warning"
                                        }
                                    >
                                        {selectedReport.status === "RESOLVED"
                                            ? "Đã xử lý"
                                            : "Chờ xử lý"}
                                    </Badge>
                                </div>

                                <div className="p-3.5 rounded-xl bg-slate-50 border border-border text-sm text-slate-800 whitespace-pre-line">
                                    {selectedReport.detail}
                                </div>

                                <div className="space-y-1.5">
                                    <label className="text-xs font-semibold text-slate-700">
                                        Ghi chú xử lý của Quản trị viên
                                    </label>
                                    <textarea
                                        value={resolutionNote}
                                        onChange={(e) => setResolutionNote(e.target.value)}
                                        rows={3}
                                        disabled={
                                            isResolvingReport || selectedReport.status === "RESOLVED"
                                        }
                                        placeholder="Nhập kết quả kiểm tra hoặc biện pháp xử lý đã thực hiện..."
                                        className="w-full text-sm rounded-lg border border-border p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-60"
                                    />
                                </div>

                                {selectedReport.status === "RESOLVED" && selectedReport.handledByName && (
                                    <p className="text-xs text-muted-foreground">
                                        Đã xử lý bởi <strong>{selectedReport.handledByName}</strong> lúc{" "}
                                        {selectedReport.handledAt
                                            ? new Date(selectedReport.handledAt).toLocaleString("vi-VN")
                                            : ""}
                                    </p>
                                )}

                                <DialogFooter>
                                    <Button
                                        type="button"
                                        variant="outline"
                                        onClick={() => setSelectedReport(null)}
                                        disabled={isResolvingReport}
                                    >
                                        Đóng
                                    </Button>
                                    {selectedReport.status === "OPEN" && (
                                        <Button
                                            type="submit"
                                            disabled={isResolvingReport}
                                            className="bg-emerald-600 hover:bg-emerald-700"
                                        >
                                            {isResolvingReport
                                                ? "Đang lưu..."
                                                : "Đánh dấu đã xử lý"}
                                        </Button>
                                    )}
                                </DialogFooter>
                            </form>
                        </>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default AdminGovernancePage;
