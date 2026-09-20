import type { Metadata } from "next";
import { PageShell } from "@/components/layout/PageShell";
import { PrescriptionDetail } from "@/features/pharmacy/components/prescription/PrescriptionDetail";
import { isUuid } from "@/features/pharmacy/utils";

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

  return (
    <PageShell
      title="Chi tiết đơn thuốc"
      description="Thông tin đơn thuốc và trạng thái xuất thuốc."
    >
      {!isUuid(id) ? (
        <section className="mt-6 rounded-xl border border-danger/40 bg-danger/10 p-6 text-sm text-danger">
          <h2 className="text-lg font-semibold">Mã đơn không hợp lệ</h2>
          <p className="mt-2 break-all">Giá trị <code>{id}</code> không phải UUID nên chưa gọi API.</p>
        </section>
      ) : <PrescriptionDetail prescriptionId={id} />}
    </PageShell>
  );
}
