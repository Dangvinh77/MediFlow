import type { ReactNode } from "react";

export type StatusTone = "neutral" | "info" | "success" | "warning" | "danger";

const toneClasses: Record<StatusTone, string> = {
  neutral: "bg-surface-muted text-foreground",
  info: "bg-info/15 text-info",
  success: "bg-success/15 text-success",
  warning: "bg-warning/20 text-warning",
  danger: "bg-danger/15 text-danger",
};

interface StatusBadgeProps {
  tone: StatusTone;
  children: ReactNode;
}

export function StatusBadge({ tone, children }: StatusBadgeProps) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${toneClasses[tone]}`}>
      {children}
    </span>
  );
}
