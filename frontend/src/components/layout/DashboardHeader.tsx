"use client";

import Link from "next/link";
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
    <header className="border-b border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
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
          {role && <span className="text-zinc-500">{role}</span>}
          <button
            type="button"
            onClick={onLogout}
            className="rounded-lg border border-zinc-300 px-3 py-1.5 dark:border-zinc-700"
          >
            Đăng xuất
          </button>
        </div>
      </div>
    </header>
  );
}
