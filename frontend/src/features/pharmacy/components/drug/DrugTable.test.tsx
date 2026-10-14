import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { DrugDTO } from "../../types";
import { DrugTable } from "./DrugTable";

const drug: DrugDTO = { drugId: "00000000-0000-4000-8000-000000000001", drugName: "Thuốc kiểm thử", activeIngredient: "Hoạt chất kiểm thử", unit: "Viên", price: 2500, stockQuantity: 0, lowStockThreshold: 10, expiryDate: "2000-01-01", manufacturer: "Nhà sản xuất", createdAt: "2026-10-12T00:00:00Z", updatedAt: "2026-10-12T00:00:00Z" };

describe("DrugTable", () => {
  it("prioritizes out-of-stock over low-stock without hiding expiry or ingredient", () => {
    render(<DrugTable drugs={[drug]} />);
    expect(screen.getByText("Hết hàng")).toBeVisible();
    expect(screen.queryByText("Tồn thấp")).not.toBeInTheDocument();
    expect(screen.getByText("Hết hạn")).toBeVisible();
    expect(screen.getByText(drug.activeIngredient!)).toBeVisible();
    expect(screen.getByRole("link", { name: "Chi tiết " + drug.drugName })).toHaveAttribute("href", "/pharmacy/drugs/" + drug.drugId);
  });
  it("uses the server threshold, including equality, instead of a fixed UI cutoff", () => {
    render(<DrugTable drugs={[{ ...drug, stockQuantity: 15, lowStockThreshold: 15 }]} />);
    expect(screen.getByText("Tồn thấp")).toBeVisible();
    expect(screen.queryByText("Hết hàng")).not.toBeInTheDocument();
    expect(within(screen.getByRole("table")).getAllByRole("columnheader")).toHaveLength(6);
  });
});
