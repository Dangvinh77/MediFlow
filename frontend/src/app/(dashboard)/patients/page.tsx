import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PatientTable } from "@/features/patient/components/PatientTable";

interface PatientsPageProps { searchParams: Promise<{ notice?: string }> }

export default async function PatientsPage({ searchParams }: PatientsPageProps) {
  const { notice } = await searchParams;
  return (
    <PageShell title="Bệnh nhân" description="Danh sách bệnh nhân trong hệ thống.">
      <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE"]}>
        <PatientTable notice={notice === "deleted" ? "deleted" : undefined} />
      </RoleGate>
    </PageShell>
  );
}
