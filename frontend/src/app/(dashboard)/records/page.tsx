import { outpatientUiEnabled } from "@/lib/outpatient-ui";
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PendingAmendmentsPanel } from "@/features/medical-record/components/PendingAmendmentsPanel";
import { PatientTimeline } from "@/features/medical-record/components/PatientTimeline";

export const metadata: Metadata = { title: "Hồ sơ khám | MediFlow" };

export const dynamic = "force-dynamic";

export default function RecordsPage() {
  return (
    <PageShell title="Hồ sơ khám" description="Tra cứu hồ sơ theo mã bệnh nhân.">
      <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE"]}>
        <PendingAmendmentsPanel />
        <PatientTimeline outpatientEnabled={outpatientUiEnabled()} />
      </RoleGate>
    </PageShell>
  );
}
