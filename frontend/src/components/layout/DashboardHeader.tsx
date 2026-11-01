"use client";

import Link from "next/link";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import type { Role } from "@/lib/roles";

const roleLabels: Partial<Record<Role, string>> = {
  ADMIN: "Quản trị viên",
  MANAGER: "Quản lý",
  DOCTOR: "Bác sĩ",
  NURSE: "Điều dưỡng",
  LAB_TECH: "Kỹ thuật viên xét nghiệm",
  PHARMACIST: "Dược sĩ",
  CASHIER: "Thu ngân",
  PATIENT: "Bệnh nhân",
};

interface DashboardHeaderProps {
  role: Role | null;
  onLogout: () => void;
  onOpenNavigation: () => void;
  workspaceHref?: string;
  workspaceLabel?: string;
  workspaceTitle?: string;
  onWorkspaceSwitch?: (event: React.MouseEvent<HTMLAnchorElement>) => void;
}

export function DashboardHeader({ role, onLogout, onOpenNavigation, workspaceHref, workspaceLabel, workspaceTitle, onWorkspaceSwitch }: DashboardHeaderProps) {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-surface/95 backdrop-blur supports-[backdrop-filter]:bg-surface/90">
      <div className="flex min-h-[4.5rem] flex-wrap items-center gap-3 px-4 py-3 sm:px-6 lg:px-8">
        <button
          type="button"
          aria-label="Mở điều hướng"
          onClick={onOpenNavigation}
          className="inline-flex size-11 items-center justify-center rounded-lg border border-border bg-surface text-foreground transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary lg:hidden"
        >
          <span aria-hidden="true" className="grid gap-1">
            <span className="block h-0.5 w-5 bg-current" />
            <span className="block h-0.5 w-5 bg-current" />
            <span className="block h-0.5 w-5 bg-current" />
          </span>
        </button>

        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold">{role === "PATIENT" ? "Sức khỏe của bạn · MediFlow" : "Không gian vận hành MediFlow"}</p>
          <p className="truncate text-xs text-muted-foreground">
            {workspaceTitle ?? (role ? roleLabels[role] ?? role : "Phiên làm việc")}
          </p>
        </div>

        <div className="ml-auto flex w-full flex-wrap items-center justify-end gap-2 text-sm sm:w-auto">
          {workspaceHref ? <Link href={workspaceHref} onClick={onWorkspaceSwitch} className="inline-flex min-h-11 items-center rounded-lg border border-primary px-3 py-2 font-medium text-primary">{workspaceLabel}</Link> : null}
          {role && role !== "SYSTEM" ? <Link href="/organization/profile" className="inline-flex min-h-11 items-center rounded-lg border border-border px-3 py-2 font-medium transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Hồ sơ cá nhân</Link> : null}
          <ThemeToggle />
          <button
            type="button"
            onClick={onLogout}
            className="min-h-11 rounded-lg border border-border px-3 py-2 font-medium text-foreground transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Đăng xuất
          </button>
        </div>
      </div>
    </header>
  );
}
