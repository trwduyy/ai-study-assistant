import React from "react";
import { cn } from "../../lib/utils";

export const Avatar = React.forwardRef<HTMLSpanElement, React.HTMLAttributes<HTMLSpanElement>>(({ className, ...props }, ref) => (
    <span ref={ref} className={cn("relative flex h-9 w-9 shrink-0 overflow-hidden rounded-full", className)} {...props} />
));
Avatar.displayName = "Avatar";

export const AvatarImage = React.forwardRef<HTMLImageElement, React.ImgHTMLAttributes<HTMLImageElement>>(({ className, ...props }, ref) => (
    <img ref={ref} className={cn("relative z-10 aspect-square h-full w-full object-cover", className)} {...props} />
));
AvatarImage.displayName = "AvatarImage";

export const AvatarFallback = React.forwardRef<HTMLSpanElement, React.HTMLAttributes<HTMLSpanElement>>(({ className, ...props }, ref) => (
    <span ref={ref} className={cn("absolute inset-0 flex items-center justify-center bg-primary-100 text-sm font-semibold text-primary", className)} {...props} />
));
AvatarFallback.displayName = "AvatarFallback";
