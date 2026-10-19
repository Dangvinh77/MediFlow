import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { storeSession } from "@/lib/session";
beforeEach(() => storeSession({ accessToken: "report-test", refreshToken: "test", role: "ADMIN" }));
const mocks = vi.hoisted(() => ({ query: "", router: { replace: vi.fn() }, monthly: vi.fn(), daily: vi.fn(), topMedicines: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router, usePathname: () => "/reports", useSearchParams: () => new URLSearchParams(mocks.query) }));
vi.mock("../api", () => ({ reportApi: { monthly: mocks.monthly, daily: mocks.daily, topMedicines: mocks.topMedicines } }));
import { ReportWorkspace } from "./ReportWorkspace";

describe("ReportWorkspace", () => {
  beforeEach(() => { mocks.query = ""; mocks.router.replace.mockReset(); mocks.monthly.mockReset(); mocks.daily.mockReset(); mocks.topMedicines.mockReset(); vi.spyOn(window.history, "pushState").mockImplementation(() => {}); });
  it("defaults to a single idle monthly view with URL navigation", () => {
    render(<ReportWorkspace />);
    expect(screen.getByRole("heading", { name: "Báo cáo tháng" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Hàng tháng" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Hàng ngày" })).toHaveAttribute("href", "/reports?view=daily");
    expect(screen.queryByLabelText("Ngày báo cáo")).not.toBeInTheDocument();
    expect(mocks.monthly).not.toHaveBeenCalled();
    expect(screen.queryByRole("link", { name: "Báo cáo ngoại trú" })).not.toBeInTheDocument();
  });
  it("only exposes the outpatient entry after the server UI gate is enabled", () => {
    render(<ReportWorkspace outpatientEnabled />);
    expect(screen.getByRole("link", { name: "Báo cáo ngoại trú" })).toHaveAttribute("href", "/reports/outpatient");
    expect(mocks.monthly).not.toHaveBeenCalled(); expect(mocks.daily).not.toHaveBeenCalled();
  });
  it("reads monthly URL filters and does not request inactive report types", async () => {
    mocks.query = "view=monthly&month=2&year=2024";
    mocks.monthly.mockResolvedValue({ month: 2, year: 2024, departmentId: null, totalRevenue: 0, invoiceCount: 0, dailyDetails: [] });
    render(<ReportWorkspace />);
    await waitFor(() => expect(mocks.monthly).toHaveBeenCalledTimes(1));
    expect(mocks.monthly).toHaveBeenCalledWith(2, 2024, undefined);
    expect(mocks.daily).not.toHaveBeenCalled(); expect(mocks.topMedicines).not.toHaveBeenCalled();
  });
  it("navigates a changed submission and reloads an unchanged submitted query", async () => {
    mocks.query = "view=monthly&month=2&year=2024";
    mocks.monthly.mockResolvedValue({ month: 2, year: 2024, departmentId: null, totalRevenue: 0, invoiceCount: 0, dailyDetails: [] });
    render(<ReportWorkspace />);
    await screen.findByRole("heading", { name: "Tháng 2/2024" });
    fireEvent.submit(screen.getByLabelText("Tháng").closest("form")!);
    await waitFor(() => expect(mocks.monthly).toHaveBeenCalledTimes(2));
    await screen.findByRole("button", { name: "Xem báo cáo" });
    fireEvent.change(screen.getByLabelText("Tháng"), { target: { value: "3" } });
    fireEvent.submit(screen.getByLabelText("Tháng").closest("form")!);
    expect(window.history.pushState).toHaveBeenCalledWith(null, "", "/reports?view=monthly&month=3&year=2024");
    expect(mocks.monthly).toHaveBeenCalledTimes(2);
  });
  it("remounts monthly filters for browser back/forward URLs", () => {
    mocks.query = "view=daily"; const view = render(<ReportWorkspace />);
    expect(screen.getByLabelText("Ngày báo cáo")).toBeVisible();
    mocks.query = "view=monthly&month=10&year=2026&departmentId=invalid"; view.rerender(<ReportWorkspace />);
    expect(screen.getByLabelText("Tháng")).toHaveValue(10);
    expect(screen.getByLabelText("Mã khoa (tùy chọn)")).toHaveValue("invalid");
    expect(screen.queryByLabelText("Ngày báo cáo")).not.toBeInTheDocument();
    expect(mocks.monthly).not.toHaveBeenCalled();
  });
  it("auto-loads valid daily deep links, records submitted URL filters and restores them on back/forward", async () => {
    mocks.query = "view=daily&date=2024-02-29";
    mocks.daily.mockResolvedValue({ reportDate: "2024-02-29", departmentId: null, visitCount: 0, labCount: 0, prescriptionCount: 0, revenue: 0 });
    const view = render(<ReportWorkspace />);
    await screen.findByRole("heading", { name: "Báo cáo ngày 29/02/2024" });
    expect(mocks.daily).toHaveBeenCalledWith("2024-02-29", undefined);
    fireEvent.change(screen.getByLabelText("Ngày báo cáo"), { target: { value: "2024-03-01" } });
    fireEvent.submit(screen.getByLabelText("Ngày báo cáo").closest("form")!);
    expect(window.history.pushState).toHaveBeenCalledWith(null, "", "/reports?view=daily&date=2024-03-01");
    expect(mocks.daily).toHaveBeenCalledTimes(1);
    mocks.query = "view=daily&date=2024-02-28"; view.rerender(<ReportWorkspace />);
    expect(screen.getByLabelText("Ngày báo cáo")).toHaveValue("2024-02-28");
    await waitFor(() => expect(mocks.daily).toHaveBeenLastCalledWith("2024-02-28", undefined));
    expect(mocks.monthly).not.toHaveBeenCalled(); expect(mocks.topMedicines).not.toHaveBeenCalled();
  });
  it("restores ranking deep-link bounds, reloads an unchanged query and ignores invalid filters", async () => {
    mocks.query = "view=medicines&fromDate=2024-02-01&toDate=2024-02-29&limit=5";
    mocks.topMedicines.mockResolvedValue([]);
    const view = render(<ReportWorkspace />);
    await screen.findByText("Không có dữ liệu xuất thuốc trong khoảng đã chọn.");
    expect(screen.getByLabelText("Số kết quả")).toHaveValue(5);
    expect(mocks.topMedicines).toHaveBeenCalledWith("2024-02-01", "2024-02-29", 5, undefined);
    fireEvent.submit(screen.getByLabelText("Từ ngày").closest("form")!);
    await waitFor(() => expect(mocks.topMedicines).toHaveBeenCalledTimes(2));
    mocks.query = "view=medicines&fromDate=2024-02-01&toDate=2024-02-29&limit=51";
    view.rerender(<ReportWorkspace />);
    expect(screen.getByLabelText("Số kết quả")).toHaveValue(51);
    expect(mocks.topMedicines).toHaveBeenCalledTimes(2);
    expect(mocks.monthly).not.toHaveBeenCalled(); expect(mocks.daily).not.toHaveBeenCalled();
  });
});
