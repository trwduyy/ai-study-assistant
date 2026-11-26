import React from "react";
import { cn } from "../../lib/utils";

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
    hasError?: boolean;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
    ({ className, type = "text", hasError = false, ...props }, ref) => {
        return (
            <input
                ref={ref}
                type={type}
                className={cn(
                    "flex h-10 w-full rounded-md border bg-white px-3 py-2 text-sm text-[#0F172A] placeholder:text-[#64748B] transition-colors",
                    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#2563EB] focus-visible:border-[#2563EB]",
                    "disabled:cursor-not-allowed disabled:bg-[#F1F5F9] disabled:text-[#475569]",
                    hasError ? "border-[#B91C1C] focus-visible:ring-[#B91C1C]" : "border-[#E2E8F0]",
                    className
                )}
                {...props}
            />
        );
    }
);

Input.displayName = "Input";

export default Input;
