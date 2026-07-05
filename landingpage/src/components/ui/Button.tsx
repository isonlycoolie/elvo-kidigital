import Link from "next/link";
import type { ComponentProps, ReactNode } from "react";
import { cn } from "@/lib/utils";

const variantStyles = {
  primary: "bg-[#CC1E1E] text-white hover:bg-[#B91C1C]",
  dark: "bg-[#162A2C] text-white hover:bg-[#1e383b]",
} as const;

const sizeStyles = {
  sm: "px-5 py-2.5 text-[14px]",
  md: "px-5 py-3 text-[14px]",
  lg: "px-6 py-3.5 text-[15px]",
} as const;

type ButtonProps = {
  href: string;
  children: ReactNode;
  variant?: keyof typeof variantStyles;
  size?: keyof typeof sizeStyles;
  className?: string;
  external?: boolean;
} & Pick<ComponentProps<typeof Link>, "onClick">;

export function Button({
  href,
  children,
  variant = "primary",
  size = "md",
  className,
  external,
  onClick,
}: ButtonProps) {
  return (
    <Link
      href={href}
      onClick={onClick}
      {...(external
        ? { target: "_blank", rel: "noopener noreferrer" }
        : {})}
      className={cn(
        "interact-press inline-flex items-center justify-center gap-2 rounded-full font-medium",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#CC1E1E]/40 focus-visible:ring-offset-2",
        variantStyles[variant],
        sizeStyles[size],
        className
      )}
    >
      {children}
    </Link>
  );
}
