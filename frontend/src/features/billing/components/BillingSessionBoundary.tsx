"use client";

import { Fragment, type ReactNode, useSyncExternalStore } from "react";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/session";

export function BillingSessionBoundary({ identity, children }: { identity: string; children: ReactNode }) {
  const token = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);

  return <Fragment key={`${identity}:${role ?? "anonymous"}:${token ?? "anonymous"}`}>{children}</Fragment>;
}
