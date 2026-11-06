"use client";

import Link from "next/link";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import type { Role } from "@/lib/roles";
import { useEffect, useRef } from "react";

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
  const accountMenu = useRef<HTMLDetailsElement>(null);
  useEffect(() => {
    const closeOutside = (event: PointerEvent) => {
      if (event.target instanceof Node && accountMenu.current && !accountMenu.current.contains(event.target)) accountMenu.current.open = false;
    };
    document.addEventListener("pointerdown", closeOutside);
    return () => document.removeEventListener("pointerdown", closeOutside);
  }, []);
  return (
    <header className="sticky top-0 z-30 bg-brand-deep text-white">
      <div className="flex min-h-[4.5rem] flex-wrap items-center gap-3 px-4 py-3 sm:px-6 lg:px-8">
        <button
          type="button"
          aria-label="Mở điều hướng"
          onClick={onOpenNavigation}
          className="inline-flex size-11 items-center justify-center rounded-lg border border-white/50 text-white transition-colors hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white lg:hidden"
        >
          <span aria-hidden="true" className="grid gap-1">
            <span className="block h-0.5 w-5 bg-current" />
            <span className="block h-0.5 w-5 bg-current" />
            <span className="block h-0.5 w-5 bg-current" />
          </span>
        </button>

        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold">{role === "PATIENT" ? "Sức khỏe của bạn · MediFlow" : "MediFlow"}</p>
          <p className="truncate text-xs text-white/70">
            {workspaceTitle ?? (role ? roleLabels[role] ?? role : "Phiên làm việc")}
          </p>
        </div>

        <div className="ml-auto flex shrink-0 items-center justify-end gap-2 text-sm">
          {workspaceHref ? <Link href={workspaceHref} onClick={onWorkspaceSwitch} title={workspaceLabel} className="inline-flex min-h-11 max-w-32 items-center rounded-lg border border-white/50 px-3 py-2 font-medium text-white transition-colors hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white sm:max-w-none"><span className="truncate">{workspaceLabel}</span></Link> : null}
          <details ref={accountMenu} className="relative" onKeyDown={event => {
            if (event.key === "Escape") { event.currentTarget.open = false; event.currentTarget.querySelector("summary")?.focus(); }
          }}>
          <summary className="min-h-11 cursor-pointer select-none rounded-lg border border-white/50 px-3 py-3 font-medium hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white">Tài khoản</summary>
          <div className="absolute right-0 top-full z-40 mt-2 grid min-w-60 gap-3 rounded-xl border border-border bg-surface p-4 text-foreground shadow-lg">
          {role && role !== "SYSTEM" ? <Link href="/organization/profile" onClick={() => { if (accountMenu.current) accountMenu.current.open = false; }} className="inline-flex min-h-11 items-center text-sm font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">Hồ sơ cá nhân</Link> : null}
          <div className="flex items-center justify-between gap-3"><span className="text-xs text-muted-foreground">Giao diện</span><ThemeToggle /></div>
          <button
            type="button"
            onClick={onLogout}
            className="min-h-11 rounded-lg border border-border px-3 py-2 font-medium text-foreground transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Đăng xuất
          </button>
          </div>
          </details>
        </div>
      </div>
    </header>
  );
}
