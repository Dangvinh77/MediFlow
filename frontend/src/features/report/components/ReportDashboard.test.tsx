import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { storeSession } from "@/lib/session";
import type { OutpatientStatisticsDTO } from "../types";

const mocks = vi.hoisted(() => ({ refreshCapabilities: vi.fn(), statistics: vi.fn(), router: { replace: vi.fn() }, query: "", capabilities: { data: null as unknown, error: null as string | null, loading: false } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router, usePathname: () => "/reports/outpatient", useSearchParams: () => new URLSearchParams(mocks.query) }));
vi.mock("../api", () => ({ reportApi: { outpatientStatistics: mocks.statistics } }));
vi.mock("@/lib/capabilities", () => ({ refreshCapabilities: mocks.refreshCapabilities, useCapabilities: () => mocks.capabilities }));
import { ReportDashboard } from "./ReportDashboard";

const year = new Date().getFullYear();
const department = "00000000-0000-4000-8000-000000000001";
const other = "00000000-0000-4000-8000-000000000002";
const figures = { completedVisits: 3, examRevenue: 100000, labRevenue: 36000, prescriptionRevenue: 30000, unclassifiedRevenue: 0, refunds: 10000 };
function statistics(fromDate: string, toDate: string): OutpatientStatisticsDTO {
  const date = fromDate;
  return { fromDate, toDate, departmentId: null, currency: "VND", zoneId: "Asia/Bangkok", reconciled: false, totals: figures, days: [{ ...figures, date }], departments: [{ ...figures, departmentId: department }, { ...figures, departmentId: other }] };
}
const managerCapabilities = (departments: { departmentId: string; departmentName: string }[]) => ({
  data: { accountId: "a", staffId: "s", role: "MANAGER", canPracticeAsDoctor: false, observedAt: "", managementDepartments: departments.map(item => ({ ...item, appointmentId: null, revision: 1 })) },
  error: null, loading: false,
});

describe("Report dashboard", () => {
  beforeEach(() => {
    mocks.query = "";
    mocks.capabilities = { data: null, error: null, loading: false };
    mocks.statistics.mockReset();
    mocks.statistics.mockImplementation(async (fromDate: string, toDate: string) => statistics(fromDate, toDate));
    storeSession({ accessToken: "t", refreshToken: "t", role: "ADMIN" });
    vi.spyOn(window.history, "pushState").mockImplementation(() => {});
  });

  it("opens on the running year for the whole hospital and ranks departments", async () => {
    render(<ReportDashboard />);
    const totals = await screen.findByLabelText("Tổng hợp năm");
    const [from, to, scope] = mocks.statistics.mock.calls[0];
    expect(from).toBe(`${year}-01-01`);
    expect(to >= from).toBe(true);
    expect(scope).toBeUndefined();
    expect(within(totals).getByText("Doanh thu")).toBeVisible();
    expect(screen.getByText("Doanh thu theo tháng")).toBeVisible();
    expect(screen.getByText("Mật độ lượt khám theo ngày")).toBeVisible();
    expect(screen.getByRole("list", { name: "Xếp hạng theo khoa" })).toBeVisible();
  });

  it("changes charts by changing the year in the URL", async () => {
    render(<ReportDashboard />);
    await screen.findByLabelText("Tổng hợp năm");
    fireEvent.change(screen.getByLabelText("Năm báo cáo"), { target: { value: String(year - 1) } });
    expect(window.history.pushState).toHaveBeenCalledWith(null, "", `/reports/outpatient?baseYear=${year - 1}`);
  });

  it("fixes a department head to their one department with no way to pick another", async () => {
    storeSession({ accessToken: "t", refreshToken: "t", role: "MANAGER" });
    mocks.capabilities = managerCapabilities([{ departmentId: department, departmentName: "Khoa Nội" }]);
    mocks.query = `dept=${other}`;
    render(<ReportDashboard />);
    await screen.findByLabelText("Tổng hợp năm");
    expect(mocks.refreshCapabilities).toHaveBeenCalled();
    expect(mocks.statistics.mock.calls[0][2]).toBe(department);
    expect(screen.getByText("Khoa Nội")).toBeVisible();
    expect(screen.queryByLabelText(/Khoa \(tùy chọn\)/)).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Khoa phụ trách" })).not.toBeInTheDocument();
  });

  it("offers a manager with several departments only those departments", async () => {
    storeSession({ accessToken: "t", refreshToken: "t", role: "MANAGER" });
    mocks.capabilities = managerCapabilities([{ departmentId: department, departmentName: "Khoa Nội" }, { departmentId: other, departmentName: "Khoa Nhi" }]);
    render(<ReportDashboard />);
    await screen.findByLabelText("Tổng hợp năm");
    expect(within(screen.getByLabelText("Khoa phụ trách")).getAllByRole("option")).toHaveLength(2);
  });

  it("makes no read for a manager without an assigned department", () => {
    storeSession({ accessToken: "t", refreshToken: "t", role: "MANAGER" });
    mocks.capabilities = managerCapabilities([]);
    render(<ReportDashboard />);
    expect(mocks.statistics).not.toHaveBeenCalled();
    expect(screen.getByText(/chưa được gán khoa phụ trách/)).toBeVisible();
  });

  it("compares two years like for like and rereads only the longer year", async () => {
    mocks.query = `tab=compare&compareYear=${year - 2}`;
    render(<ReportDashboard />);
    await screen.findByLabelText("So sánh tổng hợp");
    await waitFor(() => expect(screen.getByText("Doanh thu lũy kế")).toBeVisible());
    const reads = mocks.statistics.mock.calls.map(call => call[0] + ".." + call[1]);
    const running = `${year}-01-01..`;
    expect(reads.filter(read => read.startsWith(running))).toHaveLength(1);
    expect(reads).toContain(`${year - 2}-01-01..${year - 2}-12-31`);
    expect(reads.filter(read => read.startsWith(`${year - 2}-01-01`))).toHaveLength(new Date().getMonth() === 11 && new Date().getDate() === 31 ? 1 : 2);
    expect(screen.getByText("Chênh lệch doanh thu từng tháng")).toBeVisible();
    expect(screen.getByText("Bảng so sánh theo tháng")).toBeVisible();
  });

  it("never offers the viewed year as its own comparison", async () => {
    mocks.query = `tab=compare&baseYear=${year - 1}&compareYear=${year - 1}`;
    render(<ReportDashboard />);
    const select = await screen.findByLabelText("So với năm");
    expect(within(select).queryByRole("option", { name: String(year - 1) })).not.toBeInTheDocument();
    expect(select).toHaveValue(String(year - 2));
  });
});
