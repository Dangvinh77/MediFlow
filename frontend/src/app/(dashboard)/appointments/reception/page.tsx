import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { ReceptionWorkspace } from "@/features/appointment/components/ReceptionWorkspace";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Tiếp nhận lịch khám | MediFlow" };

export default function ReceptionPage() {
  const enabled = process.env.MEDIFLOW_SCHEDULING_UI_ENABLED === "true";
  return <PageShell title="Tiếp nhận lịch khám" description="Chọn buổi, đăng ký giữ chỗ và theo dõi trạng thái thanh toán.">
    <RoleGate allowed={["ADMIN", "NURSE"]}><ReceptionWorkspace enabled={enabled} /></RoleGate>
  </PageShell>;
}
