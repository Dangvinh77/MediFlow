import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { LabTable } from "@/features/lab/components/LabTable";

export const metadata: Metadata = { title: "Xét nghiệm | MediFlow" };

export default function LabPage() {
  return (
    <PageShell
      title="Xét nghiệm"
      description="Danh sách 20 yêu cầu xét nghiệm gần nhất."
    >
      <RoleGate allowed={["ADMIN", "MANAGER", "LAB_TECH"]}>
        <LabTable />
      </RoleGate>
    </PageShell>
  );
}
