"use client";

import { useSyncExternalStore } from "react";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { AccountDetail } from "@/features/organization/components/AccountDetail";
import { AccountPatientLink } from "@/features/patient/components/AccountPatientLink";

export function AccountDetailWorkspace({ accountId, schedulingEnabled = false }: { accountId: string; schedulingEnabled?: boolean }) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <AccountDetail key={`${session}:${accountId}`} accountId={accountId} schedulingEnabled={schedulingEnabled} renderPatientLink={patientId => <AccountPatientLink key={patientId} patientId={patientId} />} />;
}
