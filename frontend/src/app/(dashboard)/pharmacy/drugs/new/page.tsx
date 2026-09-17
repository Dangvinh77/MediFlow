import type { Metadata } from "next";
import { PageShell } from "@/components/layout/PageShell";

export const metadata: Metadata = {
  title: "Tạo thuốc | MediFlow",
};

/**
 * Route tạo thuốc mới.
 *
 * Page giữ là Server Component và chỉ làm composition.
 * Không xử lý form, validation hoặc submit ngay tại route.
 *
 * TODO(PH-FE-04):
 * - Thay nội dung tạm bằng CreateDrugForm.
 * - Form phía client kiểm tra quyền UX và quản lý validation.
 * - Gọi API thông qua pharmacyApi, không gọi HTTP trực tiếp tại page.
 */
export default function CreateDrugPage() {
  return (
    <PageShell
      title="Tạo thuốc"
      description="Bổ sung thuốc mới vào danh mục."
    >
      {/* Chưa render form hoặc action tạo thuốc trong PH-FE-01. */}
      <p className="mt-6 text-sm text-muted-foreground">
        Biểu mẫu tạo thuốc sẽ được triển khai ở PH-FE-04.
      </p>
    </PageShell>
  );
}