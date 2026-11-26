import React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "../../lib/utils";

export const badgeVariants = cva(
    "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors",
    {
        variants: {
            variant: {
                default: "border-transparent bg-[#EFF6FF] text-[#2563EB]",
                secondary: "border-transparent bg-[#F0FDFA] text-[#0F766E]",
                success: "border-[#BBF7D0] bg-[#F0FDF4] text-[#15803D]",
                warning: "border-[#FDE68A] bg-[#FFFBEB] text-[#B45309]",
                danger: "border-[#FECACA] bg-[#FEF2F2] text-[#B91C1C]",
                info: "border-[#BAE6FD] bg-[#F0F9FF] text-[#0369A1]",
                outline: "border-[#E2E8F0] bg-white text-[#475569]"
            }
        },
        defaultVariants: {
            variant: "default"
        }
    }
);

export interface BadgeProps
    extends React.HTMLAttributes<HTMLSpanElement>,
        VariantProps<typeof badgeVariants> {
    icon?: React.ReactNode;
}

export const Badge: React.FC<BadgeProps> = ({
    className,
    variant,
    icon,
    children,
    ...props
}) => {
    return (
        <span className={cn(badgeVariants({ variant }), className)} {...props}>
            {icon && (
                <span className="inline-flex shrink-0 items-center" aria-hidden="true">
                    {icon}
                </span>
            )}
            <span>{children}</span>
        </span>
    );
};

export default Badge;
