import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PharmacyPageShell } from "@/features/pharmacy/components/PharmacyPageShell";
import { CreatePrescriptionForm } from "@/features/pharmacy/components/prescription/CreatePrescriptionForm";
import { resolvePrescriptionMode } from "@/features/pharmacy/prescriptionMode";

export const metadata: Metadata = {
  title: "Kê đơn | MediFlow",
};

export const dynamic = "force-dynamic";

export default async function CreatePrescriptionPage({ searchParams }: { searchParams: Promise<{ patientId?: string | string[]; recordId?: string | string[] }> }) {
  const mode = resolvePrescriptionMode(process.env.MEDIFLOW_PHARMACY_PRESCRIPTION_MODE);
  const params = await searchParams;
  return (
    <PharmacyPageShell
      title="Kê đơn"
      description="Tạo đơn thuốc và khai báo các dòng thuốc."
    >
      <RoleGate allowed={["ADMIN", "DOCTOR"]}>
        {mode ? <CreatePrescriptionForm mode={mode} initialPatientId={typeof params.patientId === "string" ? params.patientId : undefined} initialRecordId={typeof params.recordId === "string" ? params.recordId : undefined} /> : <p role="alert" className="mt-6 text-sm text-danger">Chức năng kê đơn chưa sẵn sàng. Vui lòng liên hệ quản trị viên.</p>}
      </RoleGate>
    </PharmacyPageShell>
  );
}
