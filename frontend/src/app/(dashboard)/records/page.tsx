import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { MedicalRecordTable } from "@/features/medical-record/components/MedicalRecordTable";

export const metadata: Metadata = { title: "Hồ sơ khám | MediFlow" };

export default function RecordsPage() {
  return (
    <PageShell title="Hồ sơ khám" description="Tra cứu hồ sơ theo mã bệnh nhân.">
      <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE"]}>
        <MedicalRecordTable />
      </RoleGate>
    </PageShell>
  );
}
