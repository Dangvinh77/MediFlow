import type { Metadata } from "next";
import { Suspense } from "react";
import { PageShell } from "@/components/layout/PageShell";
import {
  CatalogFallback,
  DrugCatalog,
} from "@/features/pharmacy/components/drug/DrugCatalog";

export const metadata: Metadata = {
  title: "Kho thuốc | MediFlow",
};

/**
 * Route trang danh mục thuốc.
 *
 * Page chỉ ghép khung route với Client Component dưới Suspense.
 * Toàn bộ query/API/loading/error nằm trong feature component.
 */
export default function DrugCatalogPage() {
  return (
    <PageShell
      title="Kho thuốc"
      description="Tra cứu danh mục thuốc, tồn kho và hạn sử dụng."
    >
      <Suspense fallback={<CatalogFallback />}>
        <DrugCatalog />
      </Suspense>
    </PageShell>
  );
}
