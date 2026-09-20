"use client";

import Link from "next/link";
import { useState } from "react";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import type { Role } from "@/lib/roles";

interface NavigationItem {
  href: string;
  label: string;
  roles: readonly Role[];
}

const navigation = [
  { href: "/organization", label: "Tổ chức", roles: ["ADMIN", "MANAGER", "DOCTOR", "NURSE"] },
  { href: "/patients", label: "Bệnh nhân", roles: ["ADMIN", "DOCTOR", "NURSE"] },
  { href: "/appointments", label: "Lịch hẹn", roles: ["ADMIN", "MANAGER", "DOCTOR", "NURSE"] },
  { href: "/records", label: "Hồ sơ", roles: ["ADMIN", "DOCTOR", "NURSE"] },
  { href: "/lab", label: "Xét nghiệm", roles: ["ADMIN", "MANAGER", "LAB_TECH"] },
  { href: "/pharmacy", label: "Dược", roles: ["ADMIN", "DOCTOR", "PHARMACIST"] },
  { href: "/billing", label: "Viện phí", roles: ["ADMIN", "CASHIER"] },
  { href: "/notifications", label: "Thông báo", roles: ["ADMIN", "NURSE", "PATIENT"] },
  { href: "/reports", label: "Báo cáo", roles: ["ADMIN", "MANAGER"] },
] satisfies readonly NavigationItem[];

interface DashboardHeaderProps {
  role: Role | null;
  onLogout: () => void;
}

export function DashboardHeader({ role, onLogout }: DashboardHeaderProps) {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const visibleNavigation = role
    ? navigation.filter((item) => item.roles.includes(role))
    : [];

  function closeMobileMenu() {
    setMobileMenuOpen(false);
  }

  return (
    <header className="border-b border-border bg-surface">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-3 px-4 py-3 sm:px-6 sm:py-4">
        <Link href="/" className="mr-auto text-lg font-bold">
          MediFlow
        </Link>

        <button
          type="button"
          aria-expanded={mobileMenuOpen}
          aria-controls="dashboard-navigation"
          onClick={() => setMobileMenuOpen((open) => !open)}
          className="inline-flex min-h-12 items-center rounded-lg border border-border bg-surface px-3 text-sm font-medium text-foreground transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary lg:hidden"
        >
          <span aria-hidden="true">☰</span>
          <span className="sr-only">Mở điều hướng</span>
        </button>

        <div className="flex min-h-12 items-center gap-2 text-sm sm:min-h-10 sm:gap-3">
          {role ? <span className="text-muted-foreground">{role}</span> : null}
          <ThemeToggle />
          <button
            type="button"
            onClick={onLogout}
            className="min-h-12 rounded-lg border border-border px-3 py-1.5 text-foreground transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:min-h-10"
          >
            Đăng xuất
          </button>
        </div>

        <nav
          id="dashboard-navigation"
          aria-label="Điều hướng chính"
          className={`${mobileMenuOpen ? "flex" : "hidden"} order-last w-full flex-col gap-1 text-sm lg:order-none lg:flex lg:w-auto lg:flex-1 lg:flex-row lg:flex-wrap lg:items-center lg:justify-end lg:gap-1`}
        >
          {visibleNavigation.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              onClick={closeMobileMenu}
              className="inline-flex min-h-12 items-center rounded-lg px-3 py-2 transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:min-h-10"
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </div>
    </header>
  );
}
