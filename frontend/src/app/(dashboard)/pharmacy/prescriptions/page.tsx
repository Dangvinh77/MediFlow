import type { Metadata } from "next";
import { PageShell } from "@/components/layout/PageShell";

export const metadata: Metadata = {
  title: "Tra đơn thuốc | MediFlow",
};

/**
 * Route tra cứu đơn thuốc bằng prescriptionId đã biết.
 *
 * Không phải trang liệt kê hoặc tìm kiếm toàn bộ đơn thuốc.
 * Không tự bổ sung request list/search khi chưa có contract backend.
 *
 * TODO(PH-FE-07):
 * - Thay nội dung tạm bằng PrescriptionLookup.
 * - Component cho nhập UUID, kiểm tra định dạng và điều hướng
 *   sang /pharmacy/prescriptions/{prescriptionId}.
 */
export default function PrescriptionLookupPage() {
  return (
    <PageShell
      title="Tra đơn thuốc"
      description="Mở chi tiết đơn thuốc bằng mã UUID đã biết."
    >
      {/* Chưa có dữ liệu hoặc biểu mẫu tra cứu trong task route shell. */}
      <p className="mt-6 text-sm text-muted-foreground">
        Biểu mẫu tra cứu sẽ được triển khai ở PH-FE-07.
        Trang này không phải danh sách tất cả đơn thuốc.
      </p>
    </PageShell>
  );
}