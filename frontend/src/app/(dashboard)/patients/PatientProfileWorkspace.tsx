"use client";

import { useCallback, useState } from "react";
import { PatientDetail } from "@/features/patient/components/PatientDetail";
import { PatientAccountIssuance } from "@/features/organization/components/PatientAccountIssuance";

export function PatientProfileWorkspace({ patientId, notice }: { patientId: string; notice?: "created" | "updated" }) {
  const [profileRevision, setProfileRevision] = useState(0);
  const refreshOwner = useCallback(() => setProfileRevision(value => value + 1), []);
  return <PatientDetail
    key={`${patientId}:${profileRevision}`}
    patientId={patientId}
    notice={notice}
    onboarding={<PatientAccountIssuance patientId={patientId} onProfileChanged={refreshOwner} />}
  />;
}
