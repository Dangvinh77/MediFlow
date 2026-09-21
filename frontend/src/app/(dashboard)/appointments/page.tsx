import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { AppointmentTable } from "@/features/appointment/components/AppointmentTable";

export const metadata: Metadata = { title: "Lịch hẹn | MediFlow" };

export default function AppointmentsPage() {
  return (
    <PageShell
      title="Lịch hẹn"
      description="Danh sách 20 lịch hẹn gần nhất trong hệ thống."
    >
      <RoleGate allowed={["ADMIN", "MANAGER", "DOCTOR", "NURSE"]}>
        <AppointmentTable />
      </RoleGate>
    </PageShell>
  );
}
