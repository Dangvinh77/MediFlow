import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PatientForm } from "@/features/patient/components/PatientForm";
import { isUuid } from "@/lib/validation";

export const metadata: Metadata = { title: "Sửa bệnh nhân | MediFlow" };
interface Props { params: Promise<{ patientId: string }> }

export default async function EditPatientPage({ params }: Props) {
  const { patientId } = await params; const id = patientId.trim();
  return <PageShell title="Sửa bệnh nhân" description="Cập nhật thông tin liên hệ; số định danh được giữ nguyên.">{!isUuid(id) ? <section role="alert" className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">Mã bệnh nhân không phải UUID hợp lệ.</section> : <RoleGate allowed={["ADMIN", "NURSE"]}><PatientForm patientId={id} /></RoleGate>}</PageShell>;
}
