"use client";

import { useSearchParams } from "next/navigation";
import { patientApi } from "@/features/patient/api";
import { useSyncExternalStore } from "react";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { AccountAdmin } from "@/features/organization/components/AccountAdmin";
import { PatientPicker } from "@/features/patient/components/PatientPicker";

async function loadPatientIdentity(id: string) {
  const patient = await patientApi.getById(id);
  if (patient.daLuuTru) throw new Error("Hồ sơ bệnh nhân đã lưu trữ. Hãy chọn hồ sơ khác.");
  return patient.soCmnd;
}

export function AccountCreateWorkspace() {
  const params = useSearchParams();
  const initialOwnerType = params.get("ownerType") === "patient" ? "patient" : "staff";
  const initialOwnerId = params.get("ownerId") ?? "";
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <AccountAdmin key={`${session}:${initialOwnerType}:${initialOwnerId}`} initialOwnerType={initialOwnerType} initialOwnerId={initialOwnerId} loadPatientIdentity={loadPatientIdentity} renderPatientPicker={(value, onChange, disabled) => <PatientPicker id="account-owner" value={value} onChange={onChange} disabled={disabled} />} />;
}
