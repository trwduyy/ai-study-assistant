import React from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Compass } from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";

export const NotFoundPage: React.FC = () => {
    const navigate = useNavigate();
    const { user } = useAuth();
    const homePath = user?.role === "ADMIN" ? "/admin/users" : "/dashboard";

    return (
        <div className="min-h-[70vh] flex items-center justify-center p-6">
            <Card className="max-w-md w-full border-border shadow-sm text-center">
                <CardContent className="p-8 space-y-4">
                    <div className="h-14 w-14 rounded-2xl bg-blue-50 text-primary flex items-center justify-center mx-auto">
                        <Compass className="h-8 w-8" />
                    </div>
                    <div className="space-y-1.5">
                        <p className="text-xs font-bold uppercase tracking-wider text-primary">
                            Mã lỗi 404
                        </p>
                        <h1 className="text-xl font-bold text-slate-900">
                            Không tìm thấy trang
                        </h1>
                        <p className="text-sm text-muted-foreground leading-relaxed">
                            Đường dẫn bạn truy cập không tồn tại hoặc nội dung đã được di chuyển.
                        </p>
                    </div>
                    <Button
                        onClick={() => navigate(homePath, { replace: true })}
                        className="gap-2 w-full"
                    >
                        <ArrowLeft className="h-4 w-4" />
                        <span>Quay về trang chính</span>
                    </Button>
                </CardContent>
            </Card>
        </div>
    );
};

export default NotFoundPage;
