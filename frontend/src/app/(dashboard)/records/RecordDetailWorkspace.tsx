"use client";

import { useCallback } from "react";
import { labApi } from "@/features/lab/api";
import { MedicalRecordDetail } from "@/features/medical-record/components/MedicalRecordDetail";
import { RecordPrescriptionWorkspace } from "@/features/pharmacy/components/prescription/RecordPrescriptionWorkspace";
import type { MedicalRecordDTO } from "@/features/medical-record/types";
import type { PrescriptionMode } from "@/features/pharmacy/prescriptionMode";

interface RecordDetailWorkspaceProps {
  recordId: string;
  notice?: "created" | "updated";
  outpatientEnabled: boolean;
  schedulingV2Enabled: boolean;
  prescriptionMode: PrescriptionMode | null;
}

export function RecordDetailWorkspace({ recordId, notice, outpatientEnabled, schedulingV2Enabled, prescriptionMode }: RecordDetailWorkspaceProps) {
  const renderPrescriptions = useCallback((record: MedicalRecordDTO, canCreate: boolean) => (
    <RecordPrescriptionWorkspace
      recordId={record.recordId}
      patientId={record.patientId}
      canCreate={canCreate}
      mode={prescriptionMode}
    />
  ), [prescriptionMode]);

  // The route composes the two contexts; neither feature imports the other.
  const cancelLabOrder = useCallback((testId: string, reason: string) => labApi.cancel(testId, { reason }), []);

  return <MedicalRecordDetail
    recordId={recordId}
    notice={notice}
    outpatientEnabled={outpatientEnabled}
    schedulingV2Enabled={schedulingV2Enabled}
    prescriptionWorkspace={renderPrescriptions}
    cancelLabOrder={cancelLabOrder}
  />;
}
