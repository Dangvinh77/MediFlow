"use client";

import { mustChangePassword } from "@/lib/session";
import { refreshCapabilities, useCapabilities } from "@/lib/capabilities";
import { usePathname, useRouter } from "next/navigation";
import { Fragment, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { DashboardHeader } from "@/components/layout/DashboardHeader";
import { DashboardSidebar } from "@/components/layout/DashboardSidebar";
import { AsyncState } from "@/components/ui/AsyncState";
import { FullScreenLoader } from "@/components/ui/FullScreenLoader";
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

export default function DashboardShell({ children, schedulingEnabled, clinicIntakeEnabled = false }: { children: ReactNode; schedulingEnabled: boolean; clinicIntakeEnabled?: boolean }) {
  const router = useRouter();
  const pathname = usePathname();
  const passwordChangeRequired = useSyncExternalStore(subscribeToAuthChanges, mustChangePassword, () => false);
  const professionalDraft = useRef(false);
  useEffect(() => { professionalDraft.current = false; }, [pathname]);
  useEffect(() => {
    const detect = (event: Event) => { if (/^\/(?:records|appointments)\/(?:new|[0-9a-f-]{36}(?:\/(?:edit|lab-order))?)\/?$/i.test(pathname) && event.target instanceof Element && event.target.closest("form")) professionalDraft.current = true; };
    document.addEventListener("input", detect, true); document.addEventListener("change", detect, true);
    return () => { document.removeEventListener("input", detect, true); document.removeEventListener("change", detect, true); };
  }, [pathname]);
  const [navigationOpen, setNavigationOpen] = useState(false);
  const token = useSyncExternalStore(
    subscribeToAuthChanges,
    getSessionKey,
    getServerTokenSnapshot,
  );
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerSnapshot);
  const authorized = token === undefined ? null : Boolean(token?.trim());
  const capabilities = useCapabilities();
  const management = pathname.startsWith("/appointments/department") || pathname.startsWith("/reports");
  const canManage = role === "DOCTOR" && capabilities.data?.role === "DOCTOR" && capabilities.data.managementDepartments.length > 0;
  useEffect(() => {
    if (!authorized || passwordChangeRequired || role !== "DOCTOR" || !schedulingEnabled) return;
    const refresh = () => { void refreshCapabilities(); };
    refresh(); window.addEventListener("focus", refresh); window.addEventListener("mediflow:management-denied", refresh);
    return () => { window.removeEventListener("focus", refresh); window.removeEventListener("mediflow:management-denied", refresh); };
  }, [authorized, passwordChangeRequired, role, token, management, schedulingEnabled]);
  useEffect(() => {
    if (management && role === "DOCTOR" && capabilities.session === token && capabilities.data && !capabilities.error && !capabilities.loading && !canManage) router.replace("/appointments/my-calendar");
  }, [management, role, capabilities.session, capabilities.data, capabilities.error, capabilities.loading, token, canManage, router]);

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
    return <FullScreenLoader label="Đang kiểm tra đăng nhập…" />;
  }

  if (passwordChangeRequired) {
    return <main className="mx-auto min-h-screen max-w-2xl px-5 py-10 sm:px-8">
      <div className="mb-8 flex items-center justify-between gap-4"><span className="font-semibold">MediFlow</span><button type="button" onClick={onLogout} className="inline-flex min-h-11 items-center rounded-lg px-2 text-sm text-primary underline underline-offset-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Đăng xuất</button></div>
      <h1 className="text-2xl font-semibold">Đổi mật khẩu để tiếp tục</h1>
      <p className="mt-3 max-w-prose text-sm text-muted-foreground">Tài khoản đang sử dụng mật khẩu ban đầu. Bạn cần đặt mật khẩu riêng trước khi sử dụng hệ thống. Nếu thoát lúc này, lần đăng nhập tiếp theo vẫn cần đổi mật khẩu.</p>
      {pathname === "/organization/security" ? children : <AsyncState kind="loading" message="Đang mở trang đổi mật khẩu…" />}
    </main>;
  }

  return (
    <div className="min-h-screen bg-background text-foreground lg:grid lg:grid-cols-[15rem_minmax(0,1fr)]">
      <DashboardSidebar
        role={role}
        open={navigationOpen}
        onClose={() => setNavigationOpen(false)}
        schedulingEnabled={schedulingEnabled}
        clinicIntakeEnabled={clinicIntakeEnabled}
        managementWorkspace={management && canManage}
      />
      <div className="min-w-0">
        <DashboardHeader
          role={role}
          onLogout={onLogout}
          onOpenNavigation={() => setNavigationOpen(true)}
          workspaceHref={schedulingEnabled && canManage ? management ? "/appointments/my-calendar" : "/appointments/department" : undefined}
          onWorkspaceSwitch={event => { if (!management && professionalDraft.current && !window.confirm("Bạn có dữ liệu chưa lưu. Chuyển sang Quản lý khoa và bỏ các thay đổi?")) event.preventDefault(); }}
          workspaceTitle={management && canManage ? `Trưởng khoa · ${capabilities.data?.managementDepartments.map(item => item.departmentName).join(", ")}` : undefined}
          workspaceLabel={management ? "Về Khám bệnh" : "Sang Quản lý khoa"}
        />
        <Fragment key={`${token}:${role}:${management ? capabilities.data?.managementDepartments.map(item => `${item.appointmentId}:${item.revision}`).join() ?? "none" : "professional"}`}><DashboardReferences role={role}>{children}</DashboardReferences></Fragment>
      </div>
    </div>
  );
}
