import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { CreateAppointmentForm } from "@/features/appointment/components/CreateAppointmentForm";

export const dynamic = "force-dynamic";
export const metadata: Metadata = { title: "Tạo lịch hẹn | MediFlow" };

export default function CreateAppointmentPage() {
  return (
    <PageShell
      title="Tạo lịch hẹn"
      description="Chọn bệnh nhân, bác sĩ và khoa để đăng ký khám."
    >
      <RoleGate allowed={["ADMIN", "NURSE"]}>
        <CreateAppointmentForm />
      </RoleGate>
    </PageShell>
  );
}
