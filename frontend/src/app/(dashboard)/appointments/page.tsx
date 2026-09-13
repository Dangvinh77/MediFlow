import { AppointmentTable } from "@/features/appointment/components/AppointmentTable";

export default function AppointmentsPage() {
  return (
    <main className="mx-auto max-w-6xl px-6 py-10">
      <h1 className="text-2xl font-bold">Lịch hẹn</h1>
      <p className="mt-1 text-sm text-zinc-500">Danh sách 20 lịch hẹn gần nhất trong hệ thống.</p>
      <AppointmentTable />
    </main>
  );
}
