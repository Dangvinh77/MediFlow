import type { Metadata } from "next";
import { PageShell } from "@/components/layout/PageShell";

export const metadata: Metadata = {
  title: "Kho thuốc | MediFlow",
};

/**
 * Route trang danh mục thuốc.
 *
 * PH-FE-01 chỉ tạo khung trang và metadata.
 * Không đưa logic tìm kiếm, phân trang hoặc gọi API vào page.
 *
 * TODO(PH-FE-02):
 * - Thay nội dung tạm bằng DrugCatalog.
 * - Bọc DrugCatalog bằng Suspense khi component dùng useSearchParams.
 * - Để DrugCatalog quản lý API/loading/error/empty.
 * - Để DrugTable tập trung render dữ liệu.
 */
export default function DrugCatalogPage() {
  return (
    <PageShell
      title="Kho thuốc"
      description="Tra cứu danh mục thuốc, tồn kho và hạn sử dụng."
    >
      {/*
        Đây là nội dung tạm của route shell,
        không phải thông báo danh mục thuốc đang rỗng.
      */}
      <p className="mt-6 text-sm text-muted-foreground">
        Danh mục thuốc, tìm kiếm và phân trang sẽ được triển khai
        ở PH-FE-02.
      </p>
    </PageShell>
  );
}