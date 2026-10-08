import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { PatientDetail } from "@/features/patient/components/PatientDetail";
import { isUuid } from "@/lib/validation";

export const metadata: Metadata = { title: "Chi tiết bệnh nhân | MediFlow" };
interface Props { params: Promise<{ patientId: string }>; searchParams: Promise<{ notice?: string }> }

export default async function PatientDetailPage({ params, searchParams }: Props) {
  const { patientId } = await params; const { notice } = await searchParams; const id = patientId.trim();
  return <PageShell title="Chi tiết bệnh nhân" description="Hồ sơ định danh và thông tin liên hệ.">{!isUuid(id) ? <section role="alert" className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">Mã bệnh nhân không phải UUID hợp lệ.</section> : <RoleGate allowed={["ADMIN", "DOCTOR", "NURSE"]}><PatientDetail patientId={id} notice={notice === "created" || notice === "updated" ? notice : undefined} /></RoleGate>}</PageShell>;
}
