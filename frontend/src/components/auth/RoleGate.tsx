"use client";

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
      >
        Tài khoản hiện tại không có quyền truy cập trang này.
      </section>
    );
  }

  return <>{children}</>;
}
