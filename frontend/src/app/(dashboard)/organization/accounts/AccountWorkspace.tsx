"use client";
import { AccountAdmin } from "@/features/organization/components/AccountAdmin";
import { PatientPicker } from "@/features/patient/components/PatientPicker";
export function AccountWorkspace() {
  return <AccountAdmin renderPatientPicker={(value, onChange, disabled) => <PatientPicker id="account-owner" value={value} onChange={onChange} disabled={disabled} />} />;
}
