import Link from "next/link";
import { StatusBadge } from "@/components/ui/StatusBadge";
import type { DrugDTO } from "../../types";
import {
  formatDate,
  formatVnd,
  getDrugExpiryState,
} from "../../utils";

interface DrugTableProps {
  drugs: DrugDTO[];
}

function expiryPresentation(expiryDate: string) {
  switch (getDrugExpiryState(expiryDate)) {
    case "EXPIRED":
      return { label: "Hết hạn", tone: "danger" as const };
    case "EXPIRING_SOON":
      return { label: "Sắp hết hạn", tone: "warning" as const };
    default:
      return null;
  }
}

/** Renders catalogue rows only; loading, fetching and pagination stay in DrugCatalog. */
export function DrugTable({ drugs }: DrugTableProps) {
  return (
    <div className="mt-5 overflow-x-auto">
      <table className="w-full min-w-[700px] text-left text-sm">
        <caption className="sr-only">Danh mục thuốc</caption>
        <thead className="border-b border-border bg-surface-muted">
          <tr>
            <th scope="col" className="px-4 py-3">Tên thuốc</th>
            <th scope="col" className="px-4 py-3">Đơn vị</th>
            <th scope="col" className="px-4 py-3 text-right">Tồn kho</th>
            <th scope="col" className="px-4 py-3">Hạn dùng</th>
            <th scope="col" className="px-4 py-3 text-right">Giá</th>
            <th scope="col" className="px-4 py-3">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {drugs.map((drug) => {
            const expiry = expiryPresentation(drug.expiryDate);
            const lowStock = drug.stockQuantity <= drug.lowStockThreshold;

            return (
              <tr
                key={drug.drugId}
                className="border-b border-border last:border-0"
              >
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-lg bg-surface-muted font-semibold text-primary">{drug.drugName.trim().slice(0, 1).toLocaleUpperCase("vi-VN")}</span>
                    <div>
                      <Link href={`/pharmacy/drugs/${drug.drugId}`} prefetch={false} className="font-medium underline-offset-4 hover:text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">{drug.drugName}</Link>
                      {drug.activeIngredient && <p className="mt-1 text-xs text-muted-foreground">{drug.activeIngredient}</p>}
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3">{drug.unit || "—"}</td>
                <td className="px-4 py-3 text-right">
                  <div className="flex flex-col items-end gap-1">
                    <span className="tabular-nums">{drug.stockQuantity.toLocaleString("vi-VN")}</span>
                    {drug.stockQuantity === 0 ? <StatusBadge tone="danger">Hết hàng</StatusBadge> : lowStock ? <StatusBadge tone="warning">Tồn thấp</StatusBadge> : null}
                    <span className="text-xs text-muted-foreground">Ngưỡng: {drug.lowStockThreshold}</span>
                  </div>
                </td>
                <td className="whitespace-nowrap px-4 py-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <span>{formatDate(drug.expiryDate)}</span>
                    {expiry && (
                      <StatusBadge tone={expiry.tone}>{expiry.label}</StatusBadge>
                    )}
                  </div>
                </td>
                <td className="whitespace-nowrap px-4 py-3 text-right tabular-nums">{formatVnd(drug.price)}</td>
                <td className="px-4 py-3"><Link href={`/pharmacy/drugs/${drug.drugId}`} prefetch={false} aria-label={`Chi tiết ${drug.drugName}`} className="inline-flex min-h-11 items-center text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Chi tiết</Link></td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
