"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";


import type { ReactNode } from "react";
import { useSyncExternalStore } from "react";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import type { Role } from "@/lib/roles";

const getServerRole = (): Role | null => null;

export interface RoleGateProps {
  allowed: readonly Role[];
  children: ReactNode;
}

export function RoleGate({ allowed, children }: RoleGateProps) {
  const role = useSyncExternalStore(
    subscribeToAuthChanges,
    getRole,
    getServerRole,
  );

  if (role === null || !allowed.includes(role)) {
    return (
      <section
        role="alert"
        className="mt-6 rounded-xl border border-border bg-surface p-6 text-sm text-muted-foreground"
      ><L10n>{"Tài khoản hiện tại không có quyền truy cập trang này."}</L10n></section>
    );
  }

  return <><L10nValue>{children}</L10nValue></>;
}
