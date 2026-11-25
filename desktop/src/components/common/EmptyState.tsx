import React from "react";
import { Inbox } from "lucide-react";

export interface EmptyStateProps {
    title: string;
    description: string;
    action?: React.ReactNode;
    icon?: React.ReactNode;
}

export const EmptyState: React.FC<EmptyStateProps> = ({ title, description, action, icon }) => (
    <div className="flex min-h-48 flex-col items-center justify-center rounded-lg border border-dashed border-border bg-surface p-8 text-center">
        {icon ? (
            <div className="mb-3 flex items-center justify-center text-muted-foreground" aria-hidden="true">
                {icon}
            </div>
        ) : (
            <Inbox className="mb-3 h-8 w-8 text-muted-foreground" aria-hidden="true" />
        )}
        <h3 className="font-semibold text-foreground">{title}</h3>
        <p className="mt-1 max-w-md text-sm text-muted-foreground">{description}</p>
        {action && <div className="mt-4">{action}</div>}
    </div>
);

export default EmptyState;
