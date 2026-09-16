import { PageShell } from "@/components/layout/PageShell";
import { LabTable } from "@/features/lab/components/LabTable";

export default function LabPage() {
  return (
    <PageShell
      title="Xét nghiệm"
      description="Danh sách 20 yêu cầu xét nghiệm gần nhất."
    >
      <LabTable />
    </PageShell>
  );
}
