"use client";

import Link from "next/link";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import type { Role } from "@/lib/roles";

const navigation = [
  { href: "/patients", label: "Bệnh nhân" },
  { href: "/appointments", label: "Lịch hẹn" },
  { href: "/records", label: "Hồ sơ" },
  { href: "/lab", label: "Xét nghiệm" },
];

interface DashboardHeaderProps {
  role: Role | null;
  onLogout: () => void;
}

export function DashboardHeader({ role, onLogout }: DashboardHeaderProps) {
  return (
    <header className="border-b border-border bg-surface">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-4">
        <Link href="/" className="text-lg font-bold">
          MediFlow
        </Link>
        <nav className="flex flex-wrap items-center gap-4 text-sm">
          {navigation.map((item) => (
            <Link key={item.href} href={item.href} className="hover:text-blue-600">
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="flex items-center gap-3 text-sm">
          {role && <span className="text-muted-foreground">{role}</span>}
          <ThemeToggle />
          <button
            type="button"
            onClick={onLogout}
            className="rounded-lg border border-border px-3 py-1.5"
          >
            Đăng xuất
          </button>
        </div>
      </div>
    </header>
  );
}
