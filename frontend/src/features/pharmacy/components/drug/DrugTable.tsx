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
    <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
      <table className="w-full min-w-[960px] text-left text-sm">
        <caption className="sr-only">Danh mục thuốc</caption>
        <thead className="border-b border-border bg-surface-muted">
          <tr>
            <th scope="col" className="px-4 py-3">Tên thuốc</th>
            <th scope="col" className="px-4 py-3">Hoạt chất</th>
            <th scope="col" className="px-4 py-3">Đơn vị</th>
            <th scope="col" className="px-4 py-3">Giá</th>
            <th scope="col" className="px-4 py-3">Tồn kho</th>
            <th scope="col" className="px-4 py-3">Ngưỡng</th>
            <th scope="col" className="px-4 py-3">Hạn dùng</th>
            <th scope="col" className="px-4 py-3">Nhà sản xuất</th>
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
                  <Link
                    href={`/pharmacy/drugs/${drug.drugId}`}
                    prefetch={false}
                    className="font-semibold text-primary underline-offset-4 hover:underline"
                  >
                    {drug.drugName}
                  </Link>
                </td>
                <td className="px-4 py-3">{drug.activeIngredient ?? "—"}</td>
                <td className="px-4 py-3">{drug.unit || "—"}</td>
                <td className="whitespace-nowrap px-4 py-3">{formatVnd(drug.price)}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <span>{drug.stockQuantity}</span>
                    {lowStock && <StatusBadge tone="warning">Tồn thấp</StatusBadge>}
                  </div>
                </td>
                <td className="px-4 py-3">{drug.lowStockThreshold}</td>
                <td className="whitespace-nowrap px-4 py-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <span>{formatDate(drug.expiryDate)}</span>
                    {expiry && (
                      <StatusBadge tone={expiry.tone}>{expiry.label}</StatusBadge>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3">{drug.manufacturer ?? "—"}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
