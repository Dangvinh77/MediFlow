import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { LabWorklist } from "@/features/lab/components/LabWorklist";

export const metadata: Metadata = { title: "Hàng đợi xét nghiệm | MediFlow" };

export default function LabWorklistPage() {
  return <PageShell title="Hàng đợi xét nghiệm" description="Theo dõi tiến độ bệnh phẩm và phiếu kết quả cần xử lý.">
    <RoleGate allowed={["ADMIN", "LAB_TECH"]}><LabWorklist /></RoleGate>
  </PageShell>;
}
