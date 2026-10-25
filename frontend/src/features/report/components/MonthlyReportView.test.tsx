import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { storeSession } from "@/lib/session";
beforeEach(() => storeSession({ accessToken: "report-test", refreshToken: "test", role: "ADMIN" }));
import type { MonthlyReportDTO } from "../types";

const mocks = vi.hoisted(() => ({ monthly: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ reportApi: { monthly: mocks.monthly } }));
import { MonthlyReportView } from "./MonthlyReportView";

const report: MonthlyReportDTO = { month: 2, year: 2024, departmentId: null, totalRevenue: 12345, invoiceCount: 7,
  dailyDetails: Array.from({ length: 29 }, (_, index) => ({ reportDate: "2024-02-" + String(index + 1).padStart(2, "0"), departmentId: null, visitCount: 0, labCount: 0, prescriptionCount: 0, revenue: 0 })) };
function submit() { fireEvent.submit(screen.getByLabelText("Tháng").closest("form")!); }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>((done) => { resolve = done; }); return { promise, resolve }; }

describe("MonthlyReportView", () => {
  beforeEach(() => { mocks.monthly.mockReset(); mocks.router.replace.mockReset(); });
  it("uses authoritative totals and every leap-year calendar row; zero bars stay finite", async () => {
    mocks.monthly.mockResolvedValue(report);
    render(<MonthlyReportView initialQuery={{ month: 2, year: 2024 }} autoLoad />);
    expect(await screen.findByText("12.345")).toBeVisible();
    expect(screen.getByRole("heading", { name: "Tháng 2/2024" })).toBeVisible();
    expect(within(screen.getByRole("table")).getAllByRole("row")).toHaveLength(30);
    expect(screen.getByText("29/02/2024")).toBeVisible();
    expect(document.querySelectorAll("svg rect")).toHaveLength(29);
    for (const bar of document.querySelectorAll("svg rect")) expect(Number(bar.getAttribute("height"))).toBe(0);
  });
  it("does not request invalid month or scope", () => {
    render(<MonthlyReportView initialQuery={{ month: 13, year: 2026 }} />); submit();
    expect(screen.getByRole("alert")).toHaveTextContent("Tháng phải");
    expect(mocks.monthly).not.toHaveBeenCalled();
  });
  it("renders large chart values without non-finite geometry or rewriting server totals", async () => {
    mocks.monthly.mockResolvedValue({ ...report, totalRevenue: 5000000000000, dailyDetails: report.dailyDetails.map(day => ({ ...day, revenue: 2000000000000 })) });
    render(<MonthlyReportView initialQuery={{ month: 2, year: 2024 }} autoLoad />);
    expect(await screen.findByText("5.000.000.000.000")).toBeVisible();
    for (const bar of document.querySelectorAll("svg rect")) {
      expect(Number.isFinite(Number(bar.getAttribute("y")))).toBe(true);
      expect(Number.isFinite(Number(bar.getAttribute("height")))).toBe(true);
    }
  });
  it("discards a response from an earlier manual request", async () => {
    const old = deferred<MonthlyReportDTO>(), current = deferred<MonthlyReportDTO>();
    mocks.monthly.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
    render(<MonthlyReportView initialQuery={{ month: 1, year: 2024 }} />); submit();
    await waitFor(() => expect(mocks.monthly).toHaveBeenCalledTimes(1));
    fireEvent.change(screen.getByLabelText("Tháng"), { target: { value: "2" } }); submit();
    await waitFor(() => expect(mocks.monthly).toHaveBeenCalledTimes(2));
    await act(async () => current.resolve(report));
    await act(async () => old.resolve({ ...report, month: 1, totalRevenue: 999 }));
    expect(screen.getByRole("heading", { name: "Tháng 2/2024" })).toBeVisible();
    expect(screen.queryByText("999")).not.toBeInTheDocument();
  });
  it("clears old results after failure and retries the submitted query, not the edited draft", async () => {
    mocks.monthly.mockResolvedValueOnce(report).mockRejectedValueOnce(new ApiRequestError("Tạm gián đoạn", 503, "UNAVAILABLE", [], "corr-report")).mockResolvedValueOnce({ ...report, month: 3 });
    render(<MonthlyReportView initialQuery={{ month: 2, year: 2024 }} />); submit();
    await screen.findByText("12.345");
    fireEvent.change(screen.getByLabelText("Tháng"), { target: { value: "3" } }); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-report");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Tháng"), { target: { value: "4" } });
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByRole("heading", { name: "Tháng 3/2024" });
    expect(mocks.monthly).toHaveBeenLastCalledWith(3, 2024, undefined);
  });
  it("keeps the loaded scope visible when draft filters change", async () => {
    mocks.monthly.mockResolvedValue(report); render(<MonthlyReportView initialQuery={{ month: 2, year: 2024 }} autoLoad />);
    await screen.findByText("12.345"); fireEvent.change(screen.getByLabelText("Tháng"), { target: { value: "10" } });
    expect(screen.getByRole("heading", { name: "Tháng 2/2024" })).toBeVisible();
    expect(mocks.monthly).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("status")).toHaveTextContent("Bạn đã đổi bộ lọc");
  });
  it("selects the previous month across the year boundary without silently reloading old results", async () => {
    vi.useFakeTimers({ toFake: ["Date"] }); vi.setSystemTime(new Date(2026, 0, 15));
    try {
      mocks.monthly.mockResolvedValue({ ...report, month: 1, year: 2026 });
      render(<MonthlyReportView initialQuery={{ month: 1, year: 2026 }} autoLoad />);
      await screen.findByRole("heading", { name: "Tháng 1/2026" });
      fireEvent.click(screen.getByRole("button", { name: "Tháng trước" }));
      expect(screen.getByLabelText("Tháng")).toHaveValue("12");
      expect(screen.getByLabelText("Năm")).toHaveValue(2025);
      expect(screen.getByRole("status")).toHaveTextContent("Bạn đã đổi bộ lọc");
      expect(mocks.monthly).toHaveBeenCalledTimes(1);
      submit();
      await waitFor(() => expect(mocks.monthly).toHaveBeenLastCalledWith(12, 2025, undefined));
    } finally { vi.useRealTimers(); }
  });
  it("redirects 401 and shows 403 correlation without a misleading retry button", async () => {
    mocks.monthly.mockRejectedValueOnce(new ApiRequestError("Hết phiên", 401));
    const view = render(<MonthlyReportView initialQuery={{ month: 2, year: 2024 }} autoLoad />);
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login")); view.unmount();
    mocks.monthly.mockRejectedValueOnce(new ApiRequestError("Không có quyền", 403, "FORBIDDEN", [], "corr-denied"));
    render(<MonthlyReportView initialQuery={{ month: 2, year: 2024 }} autoLoad />);
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-denied");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });
  it("hands a validated query to URL composition without requesting twice", () => {
    const onQuerySubmit = vi.fn(() => true);
    render(<MonthlyReportView initialQuery={{ month: 2, year: 2024 }} onQuerySubmit={onQuerySubmit} />); submit();
    expect(onQuerySubmit).toHaveBeenCalledWith({ month: 2, year: 2024, departmentId: undefined });
    expect(mocks.monthly).not.toHaveBeenCalled();
  });
  it("does not redirect from an obsolete request after leaving the report view", async () => {
    let reject!: (cause: unknown) => void;
    mocks.monthly.mockReturnValue(new Promise((_, fail) => { reject = fail; }));
    const view = render(<MonthlyReportView initialQuery={{ month: 2, year: 2024 }} autoLoad />);
    await waitFor(() => expect(mocks.monthly).toHaveBeenCalledTimes(1));
    view.unmount();
    await act(async () => reject(new ApiRequestError("Obsolete session response", 401)));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });
});
