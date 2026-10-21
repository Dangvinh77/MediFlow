import { RecordBreadcrumb } from "@/features/patient/components/RecordLayout";
import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PatientForm } from "@/features/patient/components/PatientForm";

export const metadata: Metadata = { title: "Thêm bệnh nhân | MediFlow" };

export default function NewPatientPage() {
  return <PageShell title="Thêm bệnh nhân" description="Tạo hồ sơ định danh bệnh nhân mới."><RecordBreadcrumb href="/patients" parent="Bệnh nhân" current="Thêm bệnh nhân" /><RoleGate allowed={["ADMIN", "NURSE"]}><PatientForm /></RoleGate></PageShell>;
}
