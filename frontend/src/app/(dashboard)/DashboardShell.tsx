"use client";

import { mustChangePassword } from "@/lib/session";
import { usePathname, useRouter } from "next/navigation";
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
  const pathname = usePathname();
  const passwordChangeRequired = useSyncExternalStore(subscribeToAuthChanges, mustChangePassword, () => false);
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
    if (authorized && passwordChangeRequired && pathname !== "/organization/security") router.replace("/organization/security");
  }, [authorized, passwordChangeRequired, pathname, router]);

  function onLogout() {
    logout();
    router.replace("/login");
  }

  if (!authorized) {
    return <main className="mx-auto max-w-6xl px-6 py-10 text-muted-foreground">Đang kiểm tra đăng nhập…</main>;
  }

  if (passwordChangeRequired) {
    return <main className="mx-auto min-h-screen max-w-2xl px-5 py-10 sm:px-8">
      <div className="mb-8 flex items-center justify-between gap-4"><span className="font-semibold">MediFlow</span><button type="button" onClick={onLogout} className="inline-flex min-h-11 items-center rounded-lg px-2 text-sm text-primary underline underline-offset-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Đăng xuất</button></div>
      <h1 className="text-2xl font-semibold">Đổi mật khẩu để tiếp tục</h1>
      <p className="mt-3 max-w-prose text-sm text-muted-foreground">Tài khoản đang sử dụng mật khẩu ban đầu. Bạn cần đặt mật khẩu riêng trước khi sử dụng hệ thống. Nếu thoát lúc này, lần đăng nhập tiếp theo vẫn cần đổi mật khẩu.</p>
      {pathname === "/organization/security" ? children : <p role="status" className="mt-6 text-sm text-muted-foreground">Đang mở trang đổi mật khẩu…</p>}
    </main>;
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
