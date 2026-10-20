"use client";

import { useRouter } from "next/navigation";
import { Fragment, useEffect, useState, useSyncExternalStore } from "react";
import { DashboardHeader } from "@/components/layout/DashboardHeader";
import { DashboardSidebar } from "@/components/layout/DashboardSidebar";
import {
  getRole,
  getToken,
  logout,
  subscribeToAuthChanges,
} from "@/lib/auth";

const getServerSnapshot = () => null;
const getServerTokenSnapshot = () => undefined;

export default function DashboardLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const router = useRouter();
  const [navigationOpen, setNavigationOpen] = useState(false);
  const token = useSyncExternalStore(
    subscribeToAuthChanges,
    getToken,
    getServerTokenSnapshot,
  );
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerSnapshot);
  const authorized = token === undefined ? null : Boolean(token?.trim());

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
    return <main className="mx-auto max-w-6xl px-6 py-10 text-muted-foreground">Đang kiểm tra đăng nhập…</main>;
  }

  return (
    <div className="min-h-screen bg-background text-foreground lg:grid lg:grid-cols-[15rem_minmax(0,1fr)]">
      <DashboardSidebar
        role={role}
        open={navigationOpen}
        onClose={() => setNavigationOpen(false)}
      />
      <div className="min-w-0">
        <DashboardHeader
          role={role}
          onLogout={onLogout}
          onOpenNavigation={() => setNavigationOpen(true)}
        />
        <Fragment key={`${token}:${role}`}>{children}</Fragment>
      </div>
    </div>
  );
}
