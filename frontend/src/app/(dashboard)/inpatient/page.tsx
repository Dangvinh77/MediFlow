import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { AdmissionTable } from "@/features/inpatient/components/AdmissionTable";

export const metadata: Metadata = { title: "Nội trú | MediFlow" };

export default function InpatientPage() {
  return (
    <PageShell title="Quản lý nội trú" description="Theo dõi yêu cầu nhập viện, giường và trạng thái đợt điều trị.">
      <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE", "CASHIER"]}>
        <AdmissionTable />
      </RoleGate>
    </PageShell>
  );
}
