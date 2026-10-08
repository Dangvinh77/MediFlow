import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { BedTable } from "@/features/inpatient/components/BedTable";

export const metadata: Metadata = { title: "Danh mục giường | MediFlow" };

export default function InpatientBedsPage() {
  return (
    <PageShell title="Danh mục giường" description="Theo dõi giường theo khoa, khu và trạng thái vận hành.">
      <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE"]}>
        <BedTable />
      </RoleGate>
    </PageShell>
  );
}
