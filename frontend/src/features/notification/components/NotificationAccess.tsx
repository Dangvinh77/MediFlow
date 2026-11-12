"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { Fragment, useCallback, useEffect, useRef, useSyncExternalStore, type ReactNode } from "react";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { AsyncState } from "@/components/ui/AsyncState";
import type { Role } from "@/lib/roles";

export type NotificationPermission = "lookup" | "detail" | "send";
const roles: Record<NotificationPermission, readonly Role[]> = {
  lookup: ["ADMIN", "NURSE", "PATIENT"], detail: ["ADMIN", "PATIENT"], send: ["ADMIN"],
};
export function canUseNotification(permission: NotificationPermission): boolean {
  const role = getRole();
  return role !== null && roles[permission].includes(role);
}

export function NotificationDenied({ message, correlationId }: { message: string; correlationId: string | null }) {
  return <div role="alert" className="mt-4 rounded-xl border border-danger/40 bg-surface p-4 text-sm text-danger"><p><L10nValue>{message}</L10nValue></p><L10nValue>{correlationId && <p className="mt-1 break-all text-xs text-muted-foreground"><L10n>{"Mã theo dõi: "}</L10n>{correlationId}</p>}</L10nValue></div>;
}

export function NotificationAccess({ permission, children }: { permission: NotificationPermission; children: ReactNode }) {
  const { localize } = useLocale();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  const token = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  if (role === null) return <AsyncState kind="loading" message={localize("Đang kiểm tra quyền…")} />;
  if (!roles[permission].includes(role)) return <p role="alert" className="mt-6 text-sm text-danger"><L10n>{"Bạn không có quyền thực hiện thao tác thông báo này."}</L10n></p>;
  // Drop private results, drafts and pending callbacks on any session change.
  return <Fragment key={`${role}:${token}`}><L10nValue>{children}</L10nValue></Fragment>;
}

export function useNotificationRequestScope(permission: NotificationPermission) {
  const token = useRef(getSessionKey());
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);
  return useCallback(() => mounted.current && getSessionKey() === token.current && canUseNotification(permission), [permission]);
}
