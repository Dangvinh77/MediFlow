"use client";

import { Fragment, useSyncExternalStore, type ReactNode } from "react";
import { getRole } from "@/lib/auth";
import { getToken, subscribeToAuthChanges } from "@/lib/session";

export function canReadReports() {
  const role = getRole();
  return role === "ADMIN" || role === "MANAGER";
}

/** Keep Report's private query drafts and results within the session that requested them. */
export function ReportSessionBoundary({ children }: { children: ReactNode }) {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const token = useSyncExternalStore(subscribeToAuthChanges, getToken, () => null);
  if (role === null) return <p role="status" className="mt-6 text-sm text-muted-foreground">Đang kiểm tra quyền…</p>;
  if (role !== "ADMIN" && role !== "MANAGER") return <p role="alert" className="mt-6 text-sm text-danger">Bạn không có quyền xem báo cáo.</p>;
  return <Fragment key={`${role}:${token}`}>{children}</Fragment>;
}
