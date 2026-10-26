import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PatientRegistrationWorkspace } from "@/features/appointment/components/PatientRegistrationWorkspace";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Phiếu khám của tôi | MediFlow" };

export default function PatientRegistrationPage() {
  const enabled = process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true";
  return <PageShell title="Phiếu khám của tôi" description="Theo dõi phiếu và giờ khám gắn với tài khoản bệnh nhân của bạn.">
    <RoleGate allowed={["PATIENT"]}><PatientRegistrationWorkspace enabled={enabled} /></RoleGate>
  </PageShell>;
}
