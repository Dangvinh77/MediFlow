import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { OutpatientReportResult } from "./OutpatientReportResult";
import type { OutpatientReportDTO, OutpatientReportPeriod } from "../types";
const day = (date: string, values: Partial<OutpatientReportPeriod> = {}): OutpatientReportPeriod => ({
  fromDate: date, toDate: date, completedVisits: 0, labTests: 0, dispensedPrescriptions: 0, dispensedUnits: 0, cashReceived: 0, refunds: 0, netCash: 0, ...values,
});
const report: OutpatientReportDTO = {
  fromDate: "2025-01-01", toDate: "2025-01-02", departmentId: null, currency: "VND", zoneId: "Asia/Bangkok",
  coverageId: "coverage", sourceObservedAt: "2025-01-03T00:00:00Z", reconciledAt: "2025-01-03T01:00:00Z", covered: true,
  periods: [day("2025-01-01", { completedVisits: 1, labTests: 2, dispensedPrescriptions: 3, dispensedUnits: 9, refunds: 20, netCash: -20 }), day("2025-01-02")],
};
describe("outpatient reconciled result", () => {
  it("shows totals, charts and the exact per-day table without extra clicks", () => {
    render(<OutpatientReportResult report={report} />);
    expect(screen.getByText("Tổng lượt khám").nextElementSibling).toHaveTextContent(/^1$/);
    expect(screen.getByText("Đơn thuốc đã cấp").parentElement).toHaveTextContent("Số lượng đã cấp: 9");
    expect(screen.getByText("Thu ròng cả kỳ").nextElementSibling).toHaveTextContent("-20 VND");
    expect(screen.getByRole("img", { name: /Dòng tiền ngoại trú/ })).toBeVisible();
    expect(screen.getByRole("img", { name: /Hoạt động ngoại trú/ })).toBeVisible();

    const table = screen.getByRole("table", { name: "Các kỳ báo cáo" });
    expect(table).toBeVisible();
    const first = within(table).getByRole("rowheader", { name: "01/01/2025" }).closest("tr")!;
    expect(within(first).getAllByRole("cell").map(cell => cell.textContent)).toEqual(["1", "2", "3", "9", "0", "20", "-20"]);
    const total = within(table).getByRole("rowheader", { name: "Cả kỳ" }).closest("tr")!;
    expect(within(total).getAllByRole("cell").at(-1)).toHaveTextContent("-20");

    const evidence = screen.getByText("Nguồn và đối soát").closest("details");
    expect(evidence).not.toHaveAttribute("open");
    fireEvent.click(screen.getByText("Nguồn và đối soát"));
    expect(screen.getByText("Mã đối soát")).toBeVisible();
  });
  it("adds a visit calendar for longer daily ranges and folds the long table", () => {
    const periods = Array.from({ length: 40 }, (_, index) => {
      const date = new Date(Date.UTC(2025, 0, 1 + index)).toISOString().slice(0, 10);
      return day(date, { completedVisits: index % 7, cashReceived: index * 1000, netCash: index * 1000 });
    });
    render(<OutpatientReportResult report={{ ...report, toDate: periods[39].toDate, periods }} />);
    expect(screen.getByRole("img", { name: /Lượt khám hoàn tất từng ngày/ })).toBeInTheDocument();
    expect(screen.getByRole("list", { name: "Xếp hạng ngày theo thu ròng" })).toBeVisible();
    expect(screen.getByText("Bảng chi tiết theo ngày (40)").closest("details")).not.toHaveAttribute("open");
  });
  it("labels monthly periods by month", () => {
    render(<OutpatientReportResult report={{ ...report, fromDate: "2024-01-01", toDate: "2024-12-31", periods: [
      { ...day("2024-01-01", { completedVisits: 4, netCash: 10 }), toDate: "2024-01-31" },
      { ...day("2024-02-01", { completedVisits: 2, netCash: 5 }), toDate: "2024-02-29" },
    ] }} />);
    expect(screen.getByRole("rowheader", { name: "Tháng 1/2024" })).toBeVisible();
    expect(screen.getByText("Dòng tiền theo kỳ")).toBeVisible();
  });
  it("does not draw an uncovered report as zero activity", () => {
    render(<OutpatientReportResult report={{...report, covered:false}} />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Chưa có dữ liệu");
  });
});
