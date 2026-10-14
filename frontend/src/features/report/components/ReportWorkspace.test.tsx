import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
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
});
