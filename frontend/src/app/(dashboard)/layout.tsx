"use client";

import { useRouter } from "next/navigation";
import { useEffect, useSyncExternalStore } from "react";
import { DashboardHeader } from "@/components/layout/DashboardHeader";
import { getRole, isAuthenticated, logout } from "@/lib/auth";

const subscribeToAuth = () => () => undefined;
const getAuthSnapshot = (): boolean | null => isAuthenticated();
const getServerAuthSnapshot = (): boolean | null => null;

export default function DashboardLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const router = useRouter();
  const authorized = useSyncExternalStore(
    subscribeToAuth,
    getAuthSnapshot,
    getServerAuthSnapshot,
  );
  const role = authorized ? getRole() : null;

  useEffect(() => {
    if (authorized === false) {
      router.replace("/login");
    }
  }, [authorized, router]);

  function onLogout() {
    logout();
    router.replace("/login");
  }

  if (!authorized) {
    return <main className="mx-auto max-w-6xl px-6 py-10 text-zinc-500">Đang kiểm tra đăng nhập…</main>;
  }

  return (
    <div className="min-h-screen bg-zinc-50 text-zinc-950 dark:bg-zinc-950 dark:text-zinc-50">
      <DashboardHeader role={role} onLogout={onLogout} />
      {children}
    </div>
  );
}
