import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { DoctorCalendarWorkspace } from "@/features/appointment/components/DoctorCalendarWorkspace";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Lịch khám của tôi | MediFlow" };

export default function DoctorCalendarPage() {
  const enabled = process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true";
  return <PageShell title="Lịch khám của tôi" description="Xem các buổi khám được giao theo ngày hoặc theo tuần.">
    <RoleGate allowed={["DOCTOR"]}><DoctorCalendarWorkspace enabled={enabled} /></RoleGate>
  </PageShell>;
}
