import { PageShell } from "@/components/layout/PageShell";
import { MedicalRecordTable } from "@/features/medical-record/components/MedicalRecordTable";

export default function RecordsPage() {
  return (
    <PageShell title="Hồ sơ khám" description="Tra cứu hồ sơ theo mã bệnh nhân.">
      <MedicalRecordTable />
    </PageShell>
  );
}
