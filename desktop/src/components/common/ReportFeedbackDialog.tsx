import React, { useState } from "react";
import { Flag, Send } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "../../contexts/AuthContext";
import adminService from "../../services/adminService";
import { ReportType } from "../../types";
import { Button } from "../ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../ui/dialog";

interface ReportFeedbackDialogProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
}

export const ReportFeedbackDialog: React.FC<ReportFeedbackDialogProps> = ({
    open,
    onOpenChange
}) => {
    const { user } = useAuth();
    const [type, setType] = useState<ReportType>("OTHER");
    const [detail, setDetail] = useState<string>("");
    const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!detail.trim()) {
            toast.error("Vui lòng nhập nội dung báo cáo hoặc góp ý.");
            return;
        }

        const targetType =
            type === "AI_INACCURATE"
                ? "AI_FEATURE"
                : type === "CLASS_ABUSE"
                ? "CLASSROOM"
                : type === "CHAT_ABUSE"
                ? "CONVERSATION"
                : "SYSTEM";

        try {
            setIsSubmitting(true);
            await adminService.createReport({
                type,
                targetType,
                targetId: user?.id ?? 1,
                detail: detail.trim()
            });
            toast.success("Cảm ơn bạn! Báo cáo/góp ý đã được gửi tới Quản trị viên.");
            setDetail("");
            setType("OTHER");
            onOpenChange(false);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể gửi báo cáo.");
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-md">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <Flag className="h-5 w-5 text-primary" />
                        <span>Gửi báo cáo / Góp ý</span>
                    </DialogTitle>
                    <DialogDescription>
                        Báo cáo sự cố kỹ thuật, nội dung vi phạm hoặc góp ý cải thiện ứng dụng tới Quản trị viên.
                    </DialogDescription>
                </DialogHeader>

                <form onSubmit={(e) => void handleSubmit(e)} className="space-y-4 py-1">
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-slate-700">Phân loại</label>
                        <select
                            value={type}
                            onChange={(e) => setType(e.target.value as ReportType)}
                            disabled={isSubmitting}
                            className="w-full text-sm rounded-lg border border-border p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20"
                        >
                            <option value="OTHER">Góp ý chung / Sự cố hệ thống</option>
                            <option value="AI_INACCURATE">Phản hồi câu trả lời AI chưa chính xác</option>
                            <option value="CLASS_ABUSE">Báo cáo vi phạm trong lớp học</option>
                            <option value="CHAT_ABUSE">Báo cáo tin nhắn vi phạm</option>
                        </select>
                    </div>

                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-slate-700">
                            Nội dung chi tiết <span className="text-rose-500">*</span>
                        </label>
                        <textarea
                            value={detail}
                            onChange={(e) => setDetail(e.target.value)}
                            rows={4}
                            required
                            disabled={isSubmitting}
                            placeholder="Mô tả chi tiết vấn đề bạn gặp phải hoặc góp ý của bạn..."
                            className="w-full text-sm rounded-lg border border-border p-2.5 focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:opacity-50"
                        />
                    </div>

                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => onOpenChange(false)}
                            disabled={isSubmitting}
                        >
                            Hủy
                        </Button>
                        <Button
                            type="submit"
                            disabled={isSubmitting || !detail.trim()}
                            className="gap-1.5"
                        >
                            <Send className="h-3.5 w-3.5" />
                            <span>{isSubmitting ? "Đang gửi..." : "Gửi báo cáo"}</span>
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
};

export default ReportFeedbackDialog;
