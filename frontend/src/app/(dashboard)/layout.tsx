"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { getRole, isAuthenticated, logout } from "@/lib/auth";

const navigation = [
  { href: "/appointments", label: "Lịch hẹn" },
  { href: "/records", label: "Hồ sơ" },
  { href: "/lab", label: "Xét nghiệm" },
];

export default function DashboardLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const router = useRouter();
  const [authorized, setAuthorized] = useState(false);
  const [role, setRole] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated()) {
      router.replace("/login");
      return;
    }
    setRole(getRole());
    setAuthorized(true);
  }, [router]);

  function onLogout() {
    logout();
    router.replace("/login");
  }

  if (!authorized) {
    return <main className="mx-auto max-w-6xl px-6 py-10 text-zinc-500">Đang kiểm tra đăng nhập…</main>;
  }

  return (
    <div className="min-h-screen bg-zinc-50 text-zinc-950 dark:bg-zinc-950 dark:text-zinc-50">
      <header className="border-b border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-4">
          <Link href="/" className="text-lg font-bold">MediFlow</Link>
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
      {children}
    </div>
  );
}
