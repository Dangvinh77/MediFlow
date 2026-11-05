import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { OutpatientReportResult } from "./OutpatientReportResult";
import type { OutpatientReportDTO } from "../types";
const report: OutpatientReportDTO = {
  fromDate: "2025-01-01", toDate: "2025-01-02", departmentId: null, currency: "VND", zoneId: "Asia/Bangkok",
  coverageId: "coverage", sourceObservedAt: "2025-01-03T00:00:00Z", reconciledAt: "2025-01-03T01:00:00Z", covered: true,
  periods: [{ fromDate: "2025-01-01", toDate: "2025-01-01", completedVisits: 1, labTests: 2, dispensedPrescriptions: 3, dispensedUnits: 9, cashReceived: 0, refunds: 20, netCash: -20 },
    { fromDate: "2025-01-02", toDate: "2025-01-02", completedVisits: 0, labTests: 0, dispensedPrescriptions: 0, dispensedUnits: 0, cashReceived: 0, refunds: 0, netCash: 0 }],
};
describe("outpatient statistics", () => {
  it("keeps trends, evidence and exact period metrics available through compact disclosures", () => {
    render(<OutpatientReportResult report={report} />);
    expect(screen.getByText("Tổng lượt khám").nextElementSibling).toHaveTextContent(/^1$/);
    expect(screen.getByText("Số lượng đã cấp").nextElementSibling).toHaveTextContent(/^9$/);
    expect(screen.getByText("Thu ròng cả kỳ").nextElementSibling).toHaveTextContent("-20 VND");
    const trends = screen.getByText("Xu hướng theo kỳ").closest("details");
    expect(trends).toBeInTheDocument();
    expect(trends).not.toHaveAttribute("open");
    expect(screen.getByRole("img", { name: /Dòng tiền ngoại trú/ })).not.toBeVisible();
    expect(screen.getByRole("img", { name: /Hoạt động ngoại trú/ })).not.toBeVisible();

    fireEvent.click(screen.getByText("Xu hướng theo kỳ"));
    expect(screen.getByRole("img", { name: /Dòng tiền ngoại trú/ })).toBeVisible();
    expect(screen.getByRole("img", { name: /Hoạt động ngoại trú/ })).toBeVisible();
    expect(screen.getByText(/Tiền thu ròng không phải doanh thu/)).toBeVisible();

    const periods = screen.getByRole("list", { name: "Các kỳ báo cáo" });
    const firstPeriod = within(periods).getByText("1 lượt · 2 xét nghiệm · 3 đơn · Thu ròng: -20 VND");
    expect(firstPeriod).toBeVisible();
    const dispensedUnits = within(periods).getAllByText("Số lượng thuốc đã xuất");
    expect(dispensedUnits[0]).not.toBeVisible();
    expect(dispensedUnits[1]).not.toBeVisible();
    fireEvent.click(firstPeriod);
    expect(within(periods).getAllByText("Số lượng thuốc đã xuất")[0]).toBeVisible();
    expect(within(periods).getAllByText("Tiền đã hoàn")[0].nextElementSibling).toHaveTextContent("20");

    const evidence = screen.getByText("Nguồn và đối soát").closest("details");
    expect(evidence).not.toHaveAttribute("open");
    fireEvent.click(screen.getByText("Nguồn và đối soát"));
    expect(screen.getByText("Mã đối soát")).toBeVisible();
  });
  it("does not draw an uncovered report as zero activity", () => {
    render(<OutpatientReportResult report={{...report, covered:false}} />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Chưa có dữ liệu");
  });
});
