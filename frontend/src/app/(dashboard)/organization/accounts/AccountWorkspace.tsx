"use client";

import { useSyncExternalStore } from "react";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { AccountDirectory } from "@/features/organization/components/AccountDirectory";

export function AccountWorkspace() {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <section className="mt-6"><AccountDirectory key={session} /></section>;
}
