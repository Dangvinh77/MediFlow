import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { PageShell } from "@/components/layout/PageShell";
import { isUuid } from "@/features/pharmacy/utils";

export const metadata: Metadata = {
  title: "Chi tiết thuốc | MediFlow",
};

interface DrugDetailPageProps {
  // Dynamic params được đọc bất đồng bộ trong route page.
  params: Promise<{ drugId: string }>;
}

/**
 * Route chi tiết của một thuốc.
 *
 * Trách nhiệm của page:
 * - Đọc drugId từ URL.
 * - Kiểm tra định dạng UUID trước khi chuyển xuống component.
 * - Ghép component chi tiết với khung trang.
 *
 * isUuid chỉ xác thực định dạng, không xác nhận thuốc tồn tại.
 *
 * TODO(PH-FE-03):
 * Thay phần nội dung tạm bằng DrugDetail và truyền id đã kiểm tra.
 * DrugDetail sẽ gọi API và xử lý DRUG_NOT_FOUND phía client.
 */
export default async function DrugDetailPage({
  params,
}: DrugDetailPageProps) {
  const { drugId } = await params;
  const id = drugId.trim();

  // Chặn URL có ID sai định dạng ngay tại route server.
  // Đây không phải xử lý lỗi 404 từ một request API.
  if (!isUuid(id)) {
    notFound();
  }

  return (
    <PageShell
      title="Chi tiết thuốc"
      description="Thông tin thuốc và các thao tác tồn kho."
    >
      {/* Hiện tại chỉ hiển thị ID của URL, chưa phải dữ liệu thuốc. */}
      <p className="mt-6 break-all text-sm">
        Mã thuốc: <code>{id}</code>
      </p>

      <p className="mt-2 text-sm text-muted-foreground">
        Dữ liệu chi tiết sẽ được triển khai ở PH-FE-03;
        điều chỉnh tồn kho ở PH-FE-05.
      </p>
    </PageShell>
  );
}