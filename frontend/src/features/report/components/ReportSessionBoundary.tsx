"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { AsyncState } from "@/components/ui/AsyncState";
import { Fragment, useSyncExternalStore, type ReactNode } from "react";
import { getRole } from "@/lib/auth";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import { ReportDepartmentProvider } from "./ReportDepartmentName";

export function canReadReports() {
  const role = getRole();
  return role === "ADMIN" || role === "DOCTOR";
}

/** Keep Report's private query drafts and results within the session that requested them. */
export function ReportSessionBoundary({ children }: { children: ReactNode }) {
  const { localize } = useLocale();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const token = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  if (role === null) return <AsyncState kind="loading" message={localize("Đang kiểm tra quyền…")} />;
  if (role !== "ADMIN" && role !== "DOCTOR") return <p role="alert" className="mt-6 text-sm text-danger"><L10n>{"Bạn không có quyền xem báo cáo."}</L10n></p>;
  return <Fragment key={`${role}:${token}`}><ReportDepartmentProvider><L10nValue>{children}</L10nValue></ReportDepartmentProvider></Fragment>;
}
