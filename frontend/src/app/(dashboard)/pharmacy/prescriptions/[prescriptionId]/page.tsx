import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PageShell } from "@/components/layout/PageShell";
import { isUuid } from "@/features/pharmacy/utils";

export const metadata: Metadata = {
  title: "Chi tiết đơn thuốc | MediFlow",
};

interface PrescriptionDetailPageProps {
  params: Promise<{ prescriptionId: string }>;
}

/**
 * Route chi tiết đơn thuốc.
 *
 * Page chỉ đọc và kiểm tra định dạng prescriptionId.
 * Không lấy dữ liệu đơn thuốc tại server trong phase session localStorage.
 *
 * TODO(PH-FE-07):
 * - Render PrescriptionDetail với id đã kiểm tra.
 * - Component client tải dữ liệu và xử lý trạng thái không tìm thấy.
 * - Hiển thị riêng status của đơn và dispenseStatus của phiếu xuất.
 *
 * TODO(PH-FE-08/09):
 * Các thao tác hủy/xuất được ghép trong PrescriptionDetail,
 * không đặt logic mutation trực tiếp vào route page này.
 */
export default async function PrescriptionDetailPage({
  params,
}: PrescriptionDetailPageProps) {
  const { prescriptionId } = await params;
  const id = prescriptionId.trim();

  // Chỉ kiểm tra định dạng URL.
  // Một UUID hợp lệ vẫn có thể không tồn tại trong backend.
  if (!isUuid(id)) {
    notFound();
  }

  return (
    <PageShell
      title="Chi tiết đơn thuốc"
      description="Thông tin đơn thuốc và trạng thái xuất thuốc."
    >
      {/* ID đang hiển thị được lấy từ URL, chưa phải snapshot từ API. */}
      <p className="mt-6 break-all text-sm">
        Mã đơn thuốc: <code>{id}</code>
      </p>

      <p className="mt-2 text-sm text-muted-foreground">
        Dữ liệu chi tiết sẽ được triển khai ở PH-FE-07;
        hủy đơn ở PH-FE-08 và xuất thuốc ở PH-FE-09.
      </p>
    </PageShell>
  );
}