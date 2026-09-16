import { PageShell } from "@/components/layout/PageShell";
import { AppointmentTable } from "@/features/appointment/components/AppointmentTable";

export default function AppointmentsPage() {
  return (
    <PageShell
      title="Lịch hẹn"
      description="Danh sách 20 lịch hẹn gần nhất trong hệ thống."
    >
      <AppointmentTable />
    </PageShell>
  );
}
