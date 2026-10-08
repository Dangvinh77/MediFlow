import type { ButtonHTMLAttributes } from "react";

type ButtonVariant = "primary" | "secondary" | "danger" | "ghost";

const variantClasses: Record<ButtonVariant, string> = {
  primary: "border-primary bg-primary text-primary-foreground hover:bg-primary-hover",
  secondary: "border-control-border bg-surface text-foreground hover:bg-surface-muted",
  danger: "border-danger bg-danger text-danger-foreground hover:brightness-95",
  ghost: "border-transparent bg-transparent text-foreground hover:bg-surface-muted",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  loading?: boolean;
}

export function Button({ variant = "secondary", loading = false, disabled, className = "", children, ...props }: ButtonProps) {
  const blocked = disabled || loading;
  return (
    <button
      {...props}
      disabled={blocked}
      aria-busy={loading || undefined}
      className={`inline-flex min-h-11 items-center justify-center rounded-lg border px-4 py-2 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-55 ${variantClasses[variant]} ${className}`}
    >
      {loading ? "Đang xử lý…" : children}
    </button>
  );
}
