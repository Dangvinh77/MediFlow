import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PatientTable } from "@/features/patient/components/PatientTable";

export default function PatientsPage() {
  return (
    <PageShell title="Bệnh nhân" description="Danh sách bệnh nhân trong hệ thống.">
      <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE"]}>
        <PatientTable />
      </RoleGate>
    </PageShell>
  );
}
