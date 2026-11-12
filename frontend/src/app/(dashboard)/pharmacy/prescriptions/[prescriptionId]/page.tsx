
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
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
  searchParams?: Promise<{ context?: string | string[] }>;
}

export default async function PrescriptionDetailPage({
  params,
  searchParams,
}: PrescriptionDetailPageProps) {
  const { prescriptionId } = await params;
  // Opened from a medical record: the clinical view never shows prices.
  const context = (await searchParams)?.context === "record" ? "record" : undefined;
  const id = prescriptionId.trim();
  const mode = resolvePrescriptionMode(process.env.MEDIFLOW_PHARMACY_PRESCRIPTION_MODE);

  return (
    <PharmacyPageShell
      title="Chi tiết đơn thuốc"
      description="Thông tin đơn thuốc và trạng thái xuất thuốc."
    >
      <L10nValue>{!isUuid(id) ? (
        <section className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
          <h2 className="text-lg font-semibold"><L10n>{"Mã đơn không hợp lệ"}</L10n></h2>
          <p className="mt-2"><L10n>{"Vui lòng kiểm tra mã trên đơn thuốc hoặc quay lại trang tra cứu."}</L10n></p>
        </section>
      ) : (
        <RoleGate allowed={["ADMIN", "DOCTOR", "PHARMACIST"]}>
          <PrescriptionDetail prescriptionId={id} mode={mode} context={context} />
        </RoleGate>
      )}</L10nValue>
    </PharmacyPageShell>
  );
}
