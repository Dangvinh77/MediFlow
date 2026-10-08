import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PatientForm } from "@/features/patient/components/PatientForm";

export const metadata: Metadata = { title: "Thêm bệnh nhân | MediFlow" };

export default function NewPatientPage() {
  return <PageShell title="Thêm bệnh nhân" description="Tạo hồ sơ định danh bệnh nhân mới."><RoleGate allowed={["ADMIN", "NURSE"]}><PatientForm /></RoleGate></PageShell>;
}
