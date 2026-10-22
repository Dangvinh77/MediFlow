import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { storeSession } from "@/lib/session";
beforeEach(() => storeSession({ accessToken: "report-test", refreshToken: "test", role: "ADMIN" }));
import type { DailyReportDTO } from "../types";
const mocks = vi.hoisted(() => ({ daily: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ reportApi: { daily: mocks.daily } }));
import { DailyReportView } from "./DailyReportView";

const zero: DailyReportDTO = { reportDate: "2024-02-29", departmentId: null, visitCount: 0, labCount: 0, prescriptionCount: 0, revenue: 0 };
const department = "00000000-0000-4000-8000-000000000001";
function submit() { fireEvent.submit(screen.getByLabelText("Ngày báo cáo").closest("form")!); }
function date(value: string) { fireEvent.change(screen.getByLabelText("Ngày báo cáo"), { target: { value } }); }
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

describe("DailyReportView", () => {
  beforeEach(() => { mocks.daily.mockReset(); mocks.router.replace.mockReset(); });
  it("starts idle and rejects missing date or malformed department without an API request", () => {
    render(<DailyReportView />); submit();
    expect(screen.getByRole("alert")).toHaveTextContent("Ngày báo cáo là bắt buộc");
    date("2024-02-29");
    fireEvent.change(screen.getByLabelText("Mã khoa (tùy chọn)"), { target: { value: "wrong" } }); submit();
    expect(screen.getByRole("alert")).toHaveTextContent("UUID");
    expect(mocks.daily).not.toHaveBeenCalled();
  });
  it("does not auto-load an invalid deep link", () => {
    render(<DailyReportView initialQuery={{ date: "2023-02-29" }} autoLoad />);
    expect(mocks.daily).not.toHaveBeenCalled();
  });
  it("renders zero as successful data and uses the response scope, including the full department UUID", async () => {
    mocks.daily.mockResolvedValue({ ...zero, departmentId: department });
    render(<DailyReportView initialQuery={{ date: zero.reportDate, departmentId: department }} autoLoad />);
    expect(await screen.findByRole("heading", { name: "Báo cáo ngày 29/02/2024" })).toBeVisible();
    expect(screen.getByText(department)).toBeVisible();
    expect(screen.getByText(department).closest("p")).toHaveTextContent("Phạm vi:");
    expect(screen.getAllByRole("definition")).toHaveLength(4);
    for (const value of screen.getAllByRole("definition")) expect(value).toHaveTextContent(/^0$/);
    date("2024-03-01");
    expect(screen.getByRole("heading", { name: "Báo cáo ngày 29/02/2024" })).toBeVisible();
    expect(mocks.daily).toHaveBeenCalledTimes(1);
  });
  it("clears old data after a failed query and retries the submitted filters instead of the draft", async () => {
    mocks.daily.mockResolvedValueOnce(zero).mockRejectedValueOnce(new ApiRequestError("Tạm gián đoạn", 503, "UNAVAILABLE", [], "corr-daily")).mockResolvedValueOnce({ ...zero, reportDate: "2024-03-01" });
    render(<DailyReportView initialQuery={{ date: zero.reportDate }} autoLoad />);
    await screen.findByRole("heading", { name: /29\/02\/2024/ });
    date("2024-03-01"); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-daily");
    expect(screen.getByText("Mã lỗi: UNAVAILABLE")).toBeVisible();
    expect(screen.queryByRole("definition")).not.toBeInTheDocument();
    date("2024-03-02"); fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByRole("heading", { name: /01\/03\/2024/ });
    expect(mocks.daily).toHaveBeenLastCalledWith("2024-03-01", undefined);
  });
  it("ignores an older response that arrives after the latest submission", async () => {
    const first = deferred<DailyReportDTO>(), latest = deferred<DailyReportDTO>();
    mocks.daily.mockReturnValueOnce(first.promise).mockReturnValueOnce(latest.promise);
    render(<DailyReportView initialQuery={{ date: "2024-02-28" }} autoLoad />);
    await waitFor(() => expect(mocks.daily).toHaveBeenCalledTimes(1));
    date(zero.reportDate); submit();
    await waitFor(() => expect(mocks.daily).toHaveBeenCalledTimes(2));
    await act(async () => latest.resolve(zero));
    await act(async () => first.resolve({ ...zero, reportDate: "2024-02-28", revenue: 999 }));
    expect(screen.getByRole("heading", { name: /29\/02\/2024/ })).toBeVisible();
    expect(screen.queryByText("999")).not.toBeInTheDocument();
  });
  it("redirects an active 401 but preserves a forbidden error without retry", async () => {
    mocks.daily.mockRejectedValueOnce(new ApiRequestError("Hết phiên", 401));
    const view = render(<DailyReportView initialQuery={{ date: zero.reportDate }} autoLoad />);
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login")); view.unmount();
    mocks.daily.mockRejectedValueOnce(new ApiRequestError("Không có quyền", 403, "FORBIDDEN", [], "corr-denied"));
    render(<DailyReportView initialQuery={{ date: zero.reportDate }} autoLoad />);
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-denied");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });
  it("does not redirect from a pending request after the view unmounts", async () => {
    const pending = deferred<DailyReportDTO>(); mocks.daily.mockReturnValue(pending.promise);
    const view = render(<DailyReportView initialQuery={{ date: zero.reportDate }} autoLoad />);
    await waitFor(() => expect(mocks.daily).toHaveBeenCalledTimes(1)); view.unmount();
    await act(async () => pending.reject(new ApiRequestError("Hết phiên", 401)));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });
  it("submits normalized URL filters without issuing a duplicate request", () => {
    const onQuerySubmit = vi.fn(() => true);
    render(<DailyReportView initialQuery={{ date: zero.reportDate, departmentId: "  " + department + "  " }} onQuerySubmit={onQuerySubmit} />); submit();
    expect(onQuerySubmit).toHaveBeenCalledWith({ date: zero.reportDate, departmentId: department });
    expect(mocks.daily).not.toHaveBeenCalled();
  });
});
