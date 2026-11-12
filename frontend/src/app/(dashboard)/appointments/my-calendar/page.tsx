import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { DoctorCalendarWorkspace } from "@/features/appointment/components/DoctorCalendarWorkspace";
import { DoctorOpenVisitsPanel } from "@/features/appointment/components/DoctorOpenVisitsPanel";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Lịch khám của tôi | MediFlow" };

export default function DoctorCalendarPage() {
  const enabled = process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true";
  return <PageShell title="Lịch khám của tôi" description="Ca được phân công, buổi khám và phiếu bệnh nhân theo ngày hoặc tuần.">
    <RoleGate allowed={["DOCTOR"]}><DoctorOpenVisitsPanel enabled={enabled} /><DoctorCalendarWorkspace enabled={enabled} clinicIntakeEnabled={process.env.MEDIFLOW_CLINIC_INTAKE_UI_ENABLED === "true"} /></RoleGate>
  </PageShell>;
}
