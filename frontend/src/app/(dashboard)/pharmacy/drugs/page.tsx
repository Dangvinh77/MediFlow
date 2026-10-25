import type { Metadata } from "next";
import { Suspense } from "react";
import { DrugCatalogActions } from "@/features/pharmacy/components/drug/DrugCatalogActions";
import { RoleGate } from "@/components/auth/RoleGate";
import { PharmacyPageShell } from "@/features/pharmacy/components/PharmacyPageShell";
import {
  CatalogFallback,
  DrugCatalog,
} from "@/features/pharmacy/components/drug/DrugCatalog";

export const metadata: Metadata = {
  title: "Danh mục thuốc | MediFlow",
};

/**
 * Route trang danh mục thuốc.
 *
 * Page chỉ ghép khung route với Client Component dưới Suspense.
 * Toàn bộ query/API/loading/error nằm trong feature component.
 */
export default function DrugCatalogPage() {
  return (
    <PharmacyPageShell
      title="Danh mục thuốc"
      description="Tra cứu thuốc và quản lý tồn kho."
      actions={<DrugCatalogActions />}
    >
      <RoleGate allowed={["ADMIN", "DOCTOR", "PHARMACIST"]}>
        <Suspense fallback={<CatalogFallback />}>
          <DrugCatalog />
        </Suspense>
      </RoleGate>
    </PharmacyPageShell>
  );
}
