import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { LabTable } from "@/features/lab/components/LabTable";

export const metadata: Metadata = { title: "Xét nghiệm | MediFlow" };

export default function LabPage() {
  return (
    <PageShell
      title="Xét nghiệm"
      description="Hàng đợi xét nghiệm theo khoa, trạng thái và đợt chăm sóc."
    >
      <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE", "LAB_TECH"]}>
        <LabTable />
      </RoleGate>
    </PageShell>
  );
}
