import { render, screen } from "@testing-library/react";
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
  it("plots separate cash and activity trends including negative net cash and zero periods", () => {
    render(<OutpatientReportResult report={report} />);
    expect(screen.getByRole("img", { name: /Dòng tiền ngoại trú/ })).toBeVisible();
    expect(screen.getByRole("img", { name: /Hoạt động ngoại trú/ })).toBeVisible();
    expect(screen.getAllByText("Tiền thu ròng")[0].nextElementSibling).toHaveTextContent("-20");
    expect(screen.getByText(/Tiền thu ròng không phải doanh thu/)).toBeVisible();
    expect(screen.getByRole("list", {name:"Các kỳ báo cáo"})).toBeInTheDocument();
  });
  it("does not draw an uncovered report as zero activity", () => {
    render(<OutpatientReportResult report={{...report, covered:false}} />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Chưa có dữ liệu");
  });
});
