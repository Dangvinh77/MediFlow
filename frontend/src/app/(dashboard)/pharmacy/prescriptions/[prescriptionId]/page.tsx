import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PharmacyPageShell } from "@/features/pharmacy/components/PharmacyPageShell";
import { PrescriptionDetail } from "@/features/pharmacy/components/prescription/PrescriptionDetail";
import { resolvePrescriptionMode } from "@/features/pharmacy/prescriptionMode";
import { isUuid } from "@/features/pharmacy/utils";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Chi tiết đơn thuốc | MediFlow",
};

interface PrescriptionDetailPageProps {
  params: Promise<{ prescriptionId: string }>;
}

export default async function PrescriptionDetailPage({
  params,
}: PrescriptionDetailPageProps) {
  const { prescriptionId } = await params;
  const id = prescriptionId.trim();
  const mode = resolvePrescriptionMode(process.env.MEDIFLOW_PHARMACY_PRESCRIPTION_MODE);

  return (
    <PharmacyPageShell
      title="Chi tiết đơn thuốc"
      description="Thông tin đơn thuốc và trạng thái xuất thuốc."
    >
      {!isUuid(id) ? (
        <section className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
          <h2 className="text-lg font-semibold">Mã đơn không hợp lệ</h2>
          <p className="mt-2">Vui lòng kiểm tra mã trên đơn thuốc hoặc quay lại trang tra cứu.</p>
        </section>
      ) : (
        <RoleGate allowed={["ADMIN", "DOCTOR", "PHARMACIST"]}>
          <PrescriptionDetail prescriptionId={id} mode={mode} />
        </RoleGate>
      )}
    </PharmacyPageShell>
  );
}
