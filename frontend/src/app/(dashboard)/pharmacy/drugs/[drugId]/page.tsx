import type { Metadata } from "next";
import { RoleGate } from "@/components/auth/RoleGate";
import { PageShell } from "@/components/layout/PageShell";
import { DrugDetail } from "@/features/pharmacy/components/drug/DrugDetail";
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
 * Page đọc params và validate định dạng UUID trước khi mount Client Component.
 * isUuid chỉ xác thực định dạng, không xác nhận thuốc tồn tại.
 */
export default async function DrugDetailPage({
  params,
}: DrugDetailPageProps) {
  const { drugId } = await params;
  const id = drugId.trim();

  if (!isUuid(id)) {
    return (
      <PageShell
        title="Chi tiết thuốc"
        description="Thông tin thuốc và các thao tác tồn kho."
      >
        <section className="mt-6 rounded-xl border border-border bg-surface p-6">
          <h2 className="text-lg font-semibold">Mã thuốc không hợp lệ</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            URL cần chứa một UUID hợp lệ để mở chi tiết thuốc.
          </p>
        </section>
      </PageShell>
    );
  }

  return (
    <PageShell
      title="Chi tiết thuốc"
      description="Thông tin thuốc và các thao tác tồn kho."
    >
      <RoleGate allowed={["ADMIN", "DOCTOR", "PHARMACIST"]}>
        <DrugDetail drugId={id} />
      </RoleGate>
    </PageShell>
  );
}
