import React from "react";
import { AlertCircle, RefreshCw } from "lucide-react";
import { cn } from "../../lib/utils";
import { Button } from "../ui/button";

export interface ErrorBannerProps {
    message: string;
    title?: string;
    className?: string;
    onRetry?: () => void;
}

export const ErrorBanner: React.FC<ErrorBannerProps> = ({
    message,
    title,
    className,
    onRetry
}) => (
    <div
        className={cn(
            "flex items-start justify-between gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800",
            className
        )}
        role="alert"
    >
        <div className="flex items-start gap-2.5">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-600" aria-hidden="true" />
            <div className="space-y-0.5">
                {title && <p className="font-semibold text-red-900">{title}</p>}
                <p className="text-red-700">{message}</p>
            </div>
        </div>
        {onRetry && (
            <Button
                variant="outline"
                size="sm"
                onClick={onRetry}
                className="h-8 gap-1.5 border-red-200 text-xs text-red-700 hover:bg-red-100 shrink-0"
            >
                <RefreshCw className="h-3 w-3" />
                <span>Thử lại</span>
            </Button>
        )}
    </div>
);

export default ErrorBanner;
