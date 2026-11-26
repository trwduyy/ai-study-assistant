import React from "react";
import { Loader2 } from "lucide-react";
import { cn } from "../../lib/utils";

export interface LoadingStateProps {
    label?: string;
    text?: string;
    className?: string;
}

export const LoadingState: React.FC<LoadingStateProps> = ({
    label,
    text,
    className
}) => {
    const displayText = text || label || "Đang tải dữ liệu...";

    return (
        <div
            className={cn(
                "flex min-h-48 items-center justify-center gap-2.5 rounded-lg border border-border bg-surface p-8 text-sm text-muted-foreground",
                className
            )}
            role="status"
        >
            <Loader2 className="h-5 w-5 animate-spin text-primary" aria-hidden="true" />
            <span>{displayText}</span>
        </div>
    );
};

export default LoadingState;
