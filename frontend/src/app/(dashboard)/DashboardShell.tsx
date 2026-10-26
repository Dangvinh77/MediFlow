"use client";

import { useRouter } from "next/navigation";
import { Fragment, useEffect, useState, useSyncExternalStore } from "react";
import { DashboardHeader } from "@/components/layout/DashboardHeader";
import { DashboardSidebar } from "@/components/layout/DashboardSidebar";
import { DashboardReferences } from "./_components/DashboardReferences";
import {
  getRole,
  getSessionKey,
  logout,
  subscribeToAuthChanges,
} from "@/lib/auth";
import type { ReactNode } from "react";

const getServerSnapshot = () => null;
const getServerTokenSnapshot = () => undefined;

export default function DashboardShell({ children, schedulingEnabled }: { children: ReactNode; schedulingEnabled: boolean }) {
  const router = useRouter();
  const [navigationOpen, setNavigationOpen] = useState(false);
  const token = useSyncExternalStore(
    subscribeToAuthChanges,
    getSessionKey,
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
        schedulingEnabled={schedulingEnabled}
      />
      <div className="min-w-0">
        <DashboardHeader
          role={role}
          onLogout={onLogout}
          onOpenNavigation={() => setNavigationOpen(true)}
        />
        <Fragment key={`${token}:${role}`}><DashboardReferences role={role}>{children}</DashboardReferences></Fragment>
      </div>
    </div>
  );
}
