"use client";

import { useSyncExternalStore } from "react";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { AccountDetail } from "@/features/organization/components/AccountDetail";

export function AccountDetailWorkspace({ accountId }: { accountId: string }) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <AccountDetail key={`${session}:${accountId}`} accountId={accountId} />;
}
