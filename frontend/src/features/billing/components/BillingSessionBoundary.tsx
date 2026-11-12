"use client";
import { L10nValue } from "@/components/i18n/LocaleProvider";


import { Fragment, type ReactNode, useSyncExternalStore } from "react";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/session";

export function BillingSessionBoundary({ identity, children }: { identity: string; children: ReactNode }) {
  const token = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);

  return <Fragment key={`${identity}:${role ?? "anonymous"}:${token ?? "anonymous"}`}><L10nValue>{children}</L10nValue></Fragment>;
}
