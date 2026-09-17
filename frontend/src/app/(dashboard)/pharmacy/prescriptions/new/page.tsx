import type { Metadata } from "next";
import { PageShell } from "@/components/layout/PageShell";

export const metadata: Metadata = {
  title: "Kê đơn | MediFlow",
};

/**
 * Route tạo đơn thuốc.
 *
 * Page chỉ sở hữu metadata và khung trang.
 * Việc quản lý form và danh sách dòng thuốc thuộc feature component.
 *
 * TODO(PH-FE-06):
 * - Thay nội dung tạm bằng CreatePrescriptionForm.
 * - Form sử dụng PrescriptionLinesEditor để quản lý các dòng thuốc.
 * - Kiểm tra quyền UX ở component client.
 * - Payload tạo đơn không chứa giá hoặc tổng tiền do frontend tự tính.
 */
export default function CreatePrescriptionPage() {
  return (
    <PageShell
      title="Kê đơn"
      description="Tạo đơn thuốc và khai báo các dòng thuốc."
    >
      {/* Chưa có thao tác tạo đơn trong PH-FE-01. */}
      <p className="mt-6 text-sm text-muted-foreground">
        Biểu mẫu kê đơn sẽ được triển khai ở PH-FE-06.
      </p>
    </PageShell>
  );
}