import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { storeSession } from "@/lib/session";
import type { OutpatientStatisticsDTO } from "../types";
const mocks = vi.hoisted(() => ({ statistics: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ reportApi: { outpatientStatistics: mocks.statistics } }));
import { OutpatientStatisticsView } from "./OutpatientStatisticsView";

const department = "00000000-0000-4000-8000-000000000001";
const zero = { completedVisits: 0, examRevenue: 0, labRevenue: 0, prescriptionRevenue: 0, unclassifiedRevenue: 0, refunds: 0 };
const report: OutpatientStatisticsDTO = {
  fromDate: "2026-11-01", toDate: "2026-11-02", departmentId: null, currency: "VND", zoneId: "Asia/Bangkok", reconciled: false,
  totals: { completedVisits: 3, examRevenue: 100000, labRevenue: 36000, prescriptionRevenue: 30000, unclassifiedRevenue: 0, refunds: 10000 },
  days: [{ ...zero, date: "2026-11-01" }, { date: "2026-11-02", completedVisits: 3, examRevenue: 100000, labRevenue: 36000, prescriptionRevenue: 30000, unclassifiedRevenue: 0, refunds: 10000 }],
  departments: [{ departmentId: department, completedVisits: 3, examRevenue: 100000, labRevenue: 36000, prescriptionRevenue: 30000, unclassifiedRevenue: 0, refunds: 10000 }],
};

describe("Outpatient statistics", () => {
  beforeEach(() => { mocks.statistics.mockReset(); storeSession({ accessToken: "t", refreshToken: "t", role: "ADMIN" }); });

  it("loads the current month and charts revenue by service line, visits and departments", async () => {
    mocks.statistics.mockResolvedValue(report);
    render(<OutpatientStatisticsView />);
    const totals = await screen.findByLabelText("Tổng hợp");
    expect(mocks.statistics).toHaveBeenCalledTimes(1);
    const [from, to, scope] = mocks.statistics.mock.calls[0];
    expect(from).toMatch(/^\d{4}-\d{2}-01$/); expect(to >= from).toBe(true); expect(scope).toBeUndefined();
    expect(within(totals).getByText("Khám bệnh").nextElementSibling).toHaveTextContent("100.000 VND");
    expect(within(totals).getByText("Thuốc").nextElementSibling).toHaveTextContent("30.000 VND");
    expect(within(totals).queryByText("Chưa phân loại")).not.toBeInTheDocument();
    expect(screen.getByText(/Chưa đối soát/)).toBeVisible();
    expect(screen.getByText("Doanh thu theo ngày và loại dịch vụ (VND)")).toBeVisible();
    expect(screen.getByText("Số lượt khám hoàn tất theo ngày")).toBeVisible();
    const departments = screen.getByRole("list", { name: "Thống kê theo khoa" });
    expect(within(departments).getByText("3 lượt · 166.000 VND")).toBeVisible();
  });

  it("reloads with the chosen range and rejects an inverted range without a request", async () => {
    mocks.statistics.mockResolvedValue(report);
    render(<OutpatientStatisticsView />);
    await screen.findByLabelText("Tổng hợp");
    fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-10-05" } });
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-10-01" } });
    fireEvent.click(screen.getByRole("button", { name: "Xem thống kê" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Đến ngày phải bằng hoặc sau từ ngày.");
    expect(mocks.statistics).toHaveBeenCalledTimes(1);
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-10-31" } });
    fireEvent.click(screen.getByRole("button", { name: "Xem thống kê" }));
    await waitFor(() => expect(mocks.statistics).toHaveBeenLastCalledWith("2026-10-05", "2026-10-31", undefined));
  });

  it("waits for a department before a manager read", () => {
    storeSession({ accessToken: "t", refreshToken: "t", role: "MANAGER" });
    render(<OutpatientStatisticsView />);
    expect(mocks.statistics).not.toHaveBeenCalled();
    expect(screen.getByText("Chọn khoa được phân quyền rồi bấm xem thống kê.")).toBeVisible();
  });
});
