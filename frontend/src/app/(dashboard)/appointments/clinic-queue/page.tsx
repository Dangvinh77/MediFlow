import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { ClinicQueueWorkspace } from "@/features/appointment/components/ClinicQueueWorkspace";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Quầy điều dưỡng khoa | MediFlow" };

export default function ClinicQueuePage() {
  return <PageShell title="Quầy điều dưỡng khoa" description="Cấp số phòng, gọi bệnh nhân vào khám và ghi sinh hiệu.">
    <RoleGate allowed={["NURSE", "DOCTOR"]}><ClinicQueueWorkspace enabled={process.env.MEDIFLOW_CLINIC_INTAKE_UI_ENABLED === "true"} /></RoleGate>
  </PageShell>;
}
