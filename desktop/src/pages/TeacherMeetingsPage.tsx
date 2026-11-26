import React, { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    Calendar,
    Clock,
    ExternalLink,
    Plus,
    Presentation,
    Trash2,
    Video
} from "lucide-react";
import { toast } from "sonner";
import classroomService from "../services/classroomService";
import scheduleService, { openExternalBrowserUrl } from "../services/scheduleService";
import { CalendarEvent, Classroom } from "../types";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../components/ui/dialog";
import { Input } from "../components/ui/input";
import { EmptyState } from "../components/common/EmptyState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { LoadingState } from "../components/common/LoadingState";

function toDateTimeLocalValue(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    const hours = String(date.getHours()).padStart(2, "0");
    const mins = String(date.getMinutes()).padStart(2, "0");
    return `${year}-${month}-${day}T${hours}:${mins}`;
}

export const TeacherMeetingsPage: React.FC = () => {
    const navigate = useNavigate();
    const [taughtClasses, setTaughtClasses] = useState<Classroom[]>([]);
    const [meetings, setMeetings] = useState<CalendarEvent[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Create Meeting Modal
    const [isCreateOpen, setIsCreateOpen] = useState<boolean>(false);
    const [classId, setClassId] = useState<number | "">("");
    const [title, setTitle] = useState<string>("");
    const [description, setDescription] = useState<string>("");
    const [startAt, setStartAt] = useState<string>("");
    const [endAt, setEndAt] = useState<string>("");
    const [meetingUrl, setMeetingUrl] = useState<string>("");
    const [isSaving, setIsSaving] = useState<boolean>(false);

    const fetchMeetings = useCallback(async (): Promise<void> => {
        try {
            setIsLoading(true);
            const classesRes = await classroomService.getMyClassrooms({ page: 0, size: 50 });
            const myTaught = classesRes.items.filter(
                (c) => c.myClassRole === "OWNER_TEACHER" || c.myClassRole === "CO_TEACHER"
            );
            setTaughtClasses(myTaught);

            const eventsList = await Promise.all(
                myTaught.map(async (cls) => {
                    try {
                        const res = await scheduleService.getClassEvents(cls.id, undefined, undefined, 0, 50);
                        return res.items;
                    } catch {
                        return [] as CalendarEvent[];
                    }
                })
            );

            const onlyMeetings = eventsList
                .flat()
                .filter((ev) => ev.type === "MEETING" || Boolean(ev.meetingUrl))
                .sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime());

            setMeetings(onlyMeetings);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải danh sách cuộc họp lớp.");
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        void fetchMeetings();
    }, [fetchMeetings]);

    const openCreateModal = () => {
        const now = new Date();
        now.setMinutes(0, 0, 0);
        const start = new Date(now.getTime() + 60 * 60 * 1000);
        const end = new Date(start.getTime() + 90 * 60 * 1000);

        setClassId(taughtClasses[0]?.id ?? "");
        setTitle("");
        setDescription("");
        setStartAt(toDateTimeLocalValue(start));
        setEndAt(toDateTimeLocalValue(end));
        setMeetingUrl("");
        setIsCreateOpen(true);
    };

    const handleCreateMeeting = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!classId || !title.trim() || !startAt || !endAt) {
            toast.error("Vui lòng điền đầy đủ lớp học, tiêu đề và thời gian.");
            return;
        }
        const s = new Date(startAt);
        const en = new Date(endAt);
        if (en <= s) {
            toast.error("Giờ kết thúc phải sau giờ bắt đầu.");
            return;
        }

        const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Ho_Chi_Minh";

        try {
            setIsSaving(true);
            await scheduleService.createClassEvent(Number(classId), {
                type: "MEETING",
                title: title.trim(),
                description: description.trim() || null,
                startAt: s.toISOString(),
                endAt: en.toISOString(),
                timezone: tz,
                meetingUrl: meetingUrl.trim() || null
            });
            toast.success("Đã tạo cuộc họp trực tuyến và gửi thông báo tới sinh viên!");
            setIsCreateOpen(false);
            void fetchMeetings();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Tạo cuộc họp thất bại.");
        } finally {
            setIsSaving(false);
        }
    };

    const handleCancelMeeting = async (ev: CalendarEvent) => {
        if (!window.confirm(`Bạn có chắc muốn hủy cuộc họp "${ev.title}" không?`)) return;
        try {
            await scheduleService.cancelClassEvent(ev.id);
            toast.success("Đã hủy cuộc họp.");
            void fetchMeetings();
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Hủy cuộc họp thất bại.");
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-border">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2.5">
                        <Presentation className="h-6 w-6 text-teal-700" />
                        <span>Cuộc họp & Giảng dạy trực tuyến</span>
                    </h1>
                    <p className="text-sm text-muted-foreground mt-1">
                        Quản lý lịch họp Google Meet của các lớp học phần bạn đang phụ trách.
                    </p>
                </div>

                <Button
                    onClick={openCreateModal}
                    disabled={taughtClasses.length === 0}
                    className="gap-2 text-xs"
                >
                    <Plus className="h-4 w-4" />
                    <span>Tạo cuộc họp Meet mới</span>
                </Button>
            </div>

            {isLoading ? (
                <LoadingState label="Đang tải danh sách cuộc họp..." />
            ) : error ? (
                <ErrorBanner
                    title="Lỗi tải cuộc họp"
                    message={error}
                    onRetry={() => void fetchMeetings()}
                />
            ) : meetings.length === 0 ? (
                <EmptyState
                    icon={<Video className="h-12 w-12 text-slate-300" />}
                    title="Chưa có cuộc họp lớp nào"
                    description="Tạo cuộc họp mới và dán link Google Meet để gửi thông báo ngay cho sinh viên."
                    action={
                        taughtClasses.length > 0 ? (
                            <Button onClick={openCreateModal} className="gap-2 text-xs">
                                <Plus className="h-4 w-4" />
                                <span>Tạo cuộc họp đầu tiên</span>
                            </Button>
                        ) : undefined
                    }
                />
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {meetings.map((ev) => (
                        <Card key={ev.id} className="border-border shadow-sm">
                            <CardContent className="p-5 space-y-4">
                                <div className="flex items-start justify-between gap-3">
                                    <div className="space-y-1">
                                        {ev.className && (
                                            <Badge variant="outline" className="text-xs">
                                                {ev.className}
                                            </Badge>
                                        )}
                                        <h3 className="font-bold text-base text-slate-900 pt-1">
                                            {ev.title}
                                        </h3>
                                    </div>
                                    <Badge
                                        variant={
                                            ev.status === "ONGOING"
                                                ? "success"
                                                : ev.status === "CANCELLED"
                                                ? "danger"
                                                : ev.status === "ENDED"
                                                ? "outline"
                                                : "info"
                                        }
                                    >
                                        {ev.status === "ONGOING"
                                            ? "Đang diễn ra"
                                            : ev.status === "CANCELLED"
                                            ? "Đã hủy"
                                            : ev.status === "ENDED"
                                            ? "Đã kết thúc"
                                            : "Sắp diễn ra"}
                                    </Badge>
                                </div>

                                {ev.description && (
                                    <p className="text-xs text-slate-600 line-clamp-2">{ev.description}</p>
                                )}

                                <div className="p-3 rounded-lg bg-slate-50 border border-border text-xs text-slate-700 space-y-1">
                                    <p className="flex items-center gap-1.5">
                                        <Calendar className="h-3.5 w-3.5 text-primary" />
                                        <span>
                                            {new Date(ev.startAt).toLocaleDateString("vi-VN", {
                                                weekday: "long",
                                                day: "2-digit",
                                                month: "2-digit",
                                                year: "numeric"
                                            })}
                                        </span>
                                    </p>
                                    <p className="flex items-center gap-1.5">
                                        <Clock className="h-3.5 w-3.5 text-slate-400" />
                                        <span>
                                            {new Date(ev.startAt).toLocaleTimeString("vi-VN", {
                                                hour: "2-digit",
                                                minute: "2-digit"
                                            })}{" "}
                                            -{" "}
                                            {new Date(ev.endAt).toLocaleTimeString("vi-VN", {
                                                hour: "2-digit",
                                                minute: "2-digit"
                                            })}
                                        </span>
                                    </p>
                                </div>

                                <div className="flex items-center justify-between gap-2 pt-2 border-t border-border">
                                    <div className="flex items-center gap-2">
                                        {ev.status !== "CANCELLED" && (
                                            <Button
                                                size="sm"
                                                variant="ghost"
                                                onClick={() => void handleCancelMeeting(ev)}
                                                className="h-8 text-xs text-rose-600 hover:bg-rose-50"
                                            >
                                                <Trash2 className="h-3.5 w-3.5 mr-1" />
                                                Hủy lịch
                                            </Button>
                                        )}
                                        {ev.classId && (
                                            <Button
                                                size="sm"
                                                variant="ghost"
                                                onClick={() => navigate(`/classes/${ev.classId}`)}
                                                className="h-8 text-xs"
                                            >
                                                Vào lớp
                                            </Button>
                                        )}
                                    </div>

                                    {ev.meetingUrl && (
                                        <Button
                                            size="sm"
                                            variant="secondary"
                                            onClick={() => openExternalBrowserUrl(ev.meetingUrl!)}
                                            className="h-8 text-xs gap-1.5"
                                        >
                                            <ExternalLink className="h-3.5 w-3.5" />
                                            <span>Mở Google Meet</span>
                                        </Button>
                                    )}
                                </div>
                            </CardContent>
                        </Card>
                    ))}
                </div>
            )}

            <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>Tạo cuộc họp lớp trực tuyến</DialogTitle>
                        <DialogDescription>
                            Dán đường dẫn Google Meet thủ công và chọn khung giờ để thông báo cho sinh viên.
                        </DialogDescription>
                    </DialogHeader>

                    <form onSubmit={(e) => void handleCreateMeeting(e)} className="space-y-4">
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Lớp học phần <span className="text-rose-500">*</span>
                            </label>
                            <select
                                value={classId}
                                onChange={(e) => setClassId(Number(e.target.value))}
                                required
                                disabled={isSaving}
                                className="w-full text-sm rounded-lg border border-border p-2.5"
                            >
                                {taughtClasses.map((cls) => (
                                    <option key={cls.id} value={cls.id}>
                                        {cls.name} ({cls.subject})
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Tiêu đề cuộc họp <span className="text-rose-500">*</span>
                            </label>
                            <Input
                                value={title}
                                onChange={(e) => setTitle(e.target.value)}
                                placeholder="Ví dụ: Giải đáp thắc mắc đồ án môn học"
                                required
                                disabled={isSaving}
                            />
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">Bắt đầu</label>
                                <Input
                                    type="datetime-local"
                                    value={startAt}
                                    onChange={(e) => setStartAt(e.target.value)}
                                    required
                                    disabled={isSaving}
                                    className="text-xs"
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-slate-700">Kết thúc</label>
                                <Input
                                    type="datetime-local"
                                    value={endAt}
                                    onChange={(e) => setEndAt(e.target.value)}
                                    required
                                    disabled={isSaving}
                                    className="text-xs"
                                />
                            </div>
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">
                                Dán link Google Meet
                            </label>
                            <Input
                                type="url"
                                value={meetingUrl}
                                onChange={(e) => setMeetingUrl(e.target.value)}
                                placeholder="https://meet.google.com/abc-defg-hij"
                                disabled={isSaving}
                            />
                        </div>

                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-slate-700">Nội dung</label>
                            <textarea
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                rows={3}
                                disabled={isSaving}
                                className="w-full text-sm rounded-lg border border-border p-2.5"
                            />
                        </div>

                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setIsCreateOpen(false)}
                                disabled={isSaving}
                            >
                                Hủy
                            </Button>
                            <Button type="submit" disabled={isSaving}>
                                {isSaving ? "Đang tạo..." : "Tạo cuộc họp"}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default TeacherMeetingsPage;
