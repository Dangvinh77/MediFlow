"use client";

import { useSyncExternalStore } from "react";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { AccountAdmin } from "@/features/organization/components/AccountAdmin";
import { PatientPicker } from "@/features/patient/components/PatientPicker";

export function AccountCreateWorkspace() {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <AccountAdmin key={session} renderPatientPicker={(value, onChange, disabled) => <PatientPicker id="account-owner" value={value} onChange={onChange} disabled={disabled} />} />;
}
