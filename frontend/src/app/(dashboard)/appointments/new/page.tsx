import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { CreateAppointmentForm } from "@/features/appointment/components/CreateAppointmentForm";

export const metadata: Metadata = { title: "Tạo lịch hẹn | MediFlow" };

export default function CreateAppointmentPage() {
  return (
    <PageShell
      title="Tạo lịch hẹn"
      description="Đặt lịch khám bằng mã bệnh nhân, bác sĩ và khoa đã được cấp."
    >
      <RoleGate allowed={["ADMIN", "NURSE"]}>
        <CreateAppointmentForm />
      </RoleGate>
    </PageShell>
  );
}
