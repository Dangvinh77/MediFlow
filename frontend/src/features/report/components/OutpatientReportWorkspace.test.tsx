import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { storeSession } from "@/lib/session";
beforeEach(() => storeSession({ accessToken: "report-test", refreshToken: "test", role: "ADMIN" }));
import type { OutpatientReportDTO } from "../types";
const mocks = vi.hoisted(() => ({ query: "", router: { replace: vi.fn() }, daily: vi.fn(), monthly: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router, usePathname: () => "/reports/outpatient", useSearchParams: () => new URLSearchParams(mocks.query) }));
vi.mock("../api", () => ({ reportApi: { outpatientDaily: mocks.daily, outpatientMonthly: mocks.monthly } }));
import { OutpatientReportWorkspace } from "./OutpatientReportWorkspace";

const department = "00000000-0000-4000-8000-000000000001";
const zero: OutpatientReportDTO = {
  fromDate: "2024-02-29", toDate: "2024-02-29", departmentId: null, currency: "VND", zoneId: "Asia/Bangkok",
  coverageId: "00000000-0000-4000-8000-000000000002", sourceObservedAt: "2024-03-01T00:00:00Z", reconciledAt: "2024-03-01T01:00:00Z", covered: true,
  periods: [{ fromDate: "2024-02-29", toDate: "2024-02-29", completedVisits: 0, labTests: 0, dispensedPrescriptions: 0, dispensedUnits: 0, cashReceived: 0, refunds: 0, netCash: 0 }],
};
function submit() { fireEvent.submit(screen.getByRole("button", { name: "Xem báo cáo" }).closest("form")!); }
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

describe("Outpatient report workspace", () => {
  beforeEach(() => {
    mocks.query = "view=daily&fromDate=2024-02-29&toDate=2024-02-29";
    mocks.daily.mockReset(); mocks.monthly.mockReset(); mocks.router.replace.mockReset();
    vi.spyOn(window.history, "pushState").mockImplementation(() => {});
  });
  it("defaults closed even for a valid direct URL and makes no request", () => {
    render(<OutpatientReportWorkspace />);
    expect(screen.getByRole("status")).toHaveTextContent("chưa sẵn sàng");
    expect(screen.queryByRole("button", { name: "Xem báo cáo" })).not.toBeInTheDocument();
    expect(mocks.daily).not.toHaveBeenCalled(); expect(mocks.monthly).not.toHaveBeenCalled();
  });
  it("starts idle when enabled without explicit URL filters", () => {
    mocks.query = ""; render(<OutpatientReportWorkspace enabled />);
    expect(screen.getByRole("status")).toHaveTextContent("Chọn phạm vi");
    expect(mocks.daily).not.toHaveBeenCalled(); expect(mocks.monthly).not.toHaveBeenCalled();
  });
  it("renders accepted zero periods with response currency, zone, scope and provenance", async () => {
    mocks.daily.mockResolvedValue({ ...zero, departmentId: department });
    render(<OutpatientReportWorkspace enabled />);
    const result = await screen.findByRole("region", { name: "Kết quả báo cáo ngoại trú" });
    expect(mocks.daily).toHaveBeenCalledWith("2024-02-29", "2024-02-29", undefined);
    expect(mocks.monthly).not.toHaveBeenCalled();
    expect(within(result).getByText(`Phạm vi: ${department}`)).toBeVisible();
    expect(within(result).getByText("Tiền tệ: VND · Múi giờ: Asia/Bangkok")).toBeVisible();
    expect(within(result).getByText("Đã đối soát")).toBeVisible();
    expect(within(result).getByText("Dữ liệu nguồn cập nhật").nextElementSibling).toHaveTextContent("07:00");
    const periods = within(result).getByRole("list", { name: "Các kỳ báo cáo" });
    expect(within(periods).getAllByRole("definition")).toHaveLength(7);
    for (const value of within(periods).getAllByRole("definition")) expect(value).toHaveTextContent(/^0$/);
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2024-03-01" } });
    expect(within(result).getByRole("heading", { level: 2 })).toHaveTextContent("29/02/2024 đến 29/02/2024");
    expect(mocks.daily).toHaveBeenCalledTimes(1);
  });
  it("displays server net cash including a negative value without recomputing totals", async () => {
    mocks.daily.mockResolvedValue({ ...zero, periods: [{ ...zero.periods[0], cashReceived: 100, refunds: 200, netCash: -125.5 }] });
    render(<OutpatientReportWorkspace enabled />);
    await screen.findByRole("region", { name: "Kết quả báo cáo ngoại trú" });
    expect(screen.getByText("Tiền thu ròng").nextElementSibling).toHaveTextContent("-125,5");
  });
  it("uses the monthly year contract and preserves all response periods", async () => {
    mocks.query = `view=monthly&year=2024&departmentId=${department}`;
    mocks.monthly.mockResolvedValue({ ...zero, fromDate: "2024-01-01", toDate: "2024-12-31", periods: [
      { ...zero.periods[0], fromDate: "2024-01-01", toDate: "2024-01-31" },
      { ...zero.periods[0], fromDate: "2024-02-01", toDate: "2024-02-29" },
    ] });
    render(<OutpatientReportWorkspace enabled />);
    const periods = await screen.findByRole("list", { name: "Các kỳ báo cáo" });
    expect(within(periods).getAllByRole("listitem")).toHaveLength(2);
    expect(mocks.monthly).toHaveBeenCalledWith(2024, department); expect(mocks.daily).not.toHaveBeenCalled();
    expect(screen.queryByLabelText("Từ ngày")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Theo tháng" })).toHaveAttribute("aria-current", "page");
  });
  it("keeps uncovered or empty reports distinct from accepted zero data", async () => {
    mocks.daily.mockResolvedValueOnce({ ...zero, covered: false }).mockResolvedValueOnce({ ...zero, periods: [] });
    const view = render(<OutpatientReportWorkspace enabled />);
    await screen.findByText("Chưa có dữ liệu đã đối soát cho phạm vi này.");
    expect(screen.queryByText("Đã đối soát")).not.toBeInTheDocument();
    expect(screen.queryByText("Tiền thu ròng")).not.toBeInTheDocument();
    submit(); await screen.findByText("Báo cáo chưa có kỳ dữ liệu.");
    expect(screen.queryByText("Tiền thu ròng")).not.toBeInTheDocument();
    view.unmount();
  });
  it("shows missing coverage as unavailable with correlation and retries submitted scope", async () => {
    mocks.daily.mockRejectedValueOnce(new ApiRequestError("Report endpoint is unavailable", 404, "REPORT_NOT_FOUND", [], "coverage-check"))
      .mockResolvedValueOnce(zero);
    render(<OutpatientReportWorkspace enabled />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Chưa có báo cáo đã đối soát");
    expect(screen.getByRole("alert")).toHaveTextContent("coverage-check");
    expect(screen.queryByText("Tiền đã thu")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2024-03-01" } });
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByRole("region", { name: "Kết quả báo cáo ngoại trú" });
    expect(mocks.daily).toHaveBeenLastCalledWith("2024-02-29", "2024-02-29", undefined);
    expect(mocks.monthly).not.toHaveBeenCalled();
  });
  it("redirects expired sessions, while forbidden access stays visible without retry", async () => {
    mocks.daily.mockRejectedValueOnce(new ApiRequestError("Hết phiên", 401));
    const view = render(<OutpatientReportWorkspace enabled />);
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login")); view.unmount();
    mocks.daily.mockRejectedValueOnce(new ApiRequestError("Không có quyền", 403, "FORBIDDEN", [], "denied-report"));
    render(<OutpatientReportWorkspace enabled />);
    expect(await screen.findByRole("alert")).toHaveTextContent("denied-report");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });
  it("validates a deep link without calling an API and focuses the first invalid field", () => {
    mocks.query = "fromDate=2024-12-31&toDate=2024-01-01&departmentId=bad";
    render(<OutpatientReportWorkspace enabled />); submit();
    expect(screen.getByLabelText("Đến ngày")).toHaveFocus();
    expect(screen.getByLabelText("Đến ngày")).toHaveAttribute("aria-invalid", "true");
    expect(mocks.daily).not.toHaveBeenCalled();
  });
  it("navigates changed filters once and reloads an unchanged submitted scope", async () => {
    mocks.daily.mockResolvedValue(zero); render(<OutpatientReportWorkspace enabled />);
    await screen.findByRole("region", { name: "Kết quả báo cáo ngoại trú" }); submit();
    await waitFor(() => expect(mocks.daily).toHaveBeenCalledTimes(2));
    await screen.findByRole("region", { name: "Kết quả báo cáo ngoại trú" });
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2024-03-01" } }); submit();
    expect(window.history.pushState).toHaveBeenCalledWith(null, "", "/reports/outpatient?view=daily&fromDate=2024-02-29&toDate=2024-03-01");
    expect(mocks.daily).toHaveBeenCalledTimes(2);
  });
  it("restores filters on URL changes and ignores stale errors after switching views", async () => {
    const pending = deferred<OutpatientReportDTO>(); mocks.daily.mockReturnValue(pending.promise);
    mocks.monthly.mockResolvedValue(zero);
    const view = render(<OutpatientReportWorkspace enabled />);
    await waitFor(() => expect(mocks.daily).toHaveBeenCalledTimes(1));
    mocks.query = "view=monthly&year=2024"; view.rerender(<OutpatientReportWorkspace enabled />);
    await screen.findByRole("region", { name: "Kết quả báo cáo ngoại trú" });
    await act(async () => pending.reject(new ApiRequestError("Cũ", 401)));
    expect(mocks.router.replace).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Năm báo cáo")).toHaveValue(2024);
  });
  it("ignores late responses after the UI gate closes", async () => {
    const pending = deferred<OutpatientReportDTO>(); mocks.daily.mockReturnValue(pending.promise);
    const view = render(<OutpatientReportWorkspace enabled />);
    await waitFor(() => expect(mocks.daily).toHaveBeenCalledTimes(1)); view.rerender(<OutpatientReportWorkspace enabled={false} />);
    await act(async () => pending.resolve(zero));
    expect(screen.getByRole("status")).toHaveTextContent("chưa sẵn sàng");
    expect(screen.queryByText("Tiền đã thu")).not.toBeInTheDocument();
  });
});
