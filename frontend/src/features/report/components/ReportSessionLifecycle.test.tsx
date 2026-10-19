import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { clearSession, storeSession } from "@/lib/session";
import type { Role } from "@/lib/roles";
const mocks = vi.hoisted(() => ({ daily: vi.fn(), monthly: vi.fn(), topMedicines: vi.fn(), outpatientDaily: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ reportApi: { daily: mocks.daily, monthly: mocks.monthly, topMedicines: mocks.topMedicines, outpatientDaily: mocks.outpatientDaily } }));
import { DailyReportView } from "./DailyReportView";
import { MonthlyReportView } from "./MonthlyReportView";
import { TopMedicinesView } from "./TopMedicinesView";
import { OutpatientReportView } from "./OutpatientReportView";

const date = "2026-10-18";
const daily = { reportDate: date, departmentId: null, visitCount: 1, labCount: 2, prescriptionCount: 3, revenue: 12345 };
const outpatientQuery = { mode: "daily" as const, fromDate: date, toDate: date, year: 2026 };
const cases = [
  { name: "daily", api: mocks.daily, element: <DailyReportView initialQuery={{ date }} autoLoad />, response: daily, marker: "Báo cáo ngày 18/10/2026", draft: "Mã khoa (tùy chọn)" },
  { name: "monthly", api: mocks.monthly, element: <MonthlyReportView initialQuery={{ month: 10, year: 2026 }} autoLoad />, response: { month: 10, year: 2026, departmentId: null, totalRevenue: 12345, invoiceCount: 1, dailyDetails: [] }, marker: "Tháng 10/2026", draft: "Mã khoa (tùy chọn)" },
  { name: "medicines", api: mocks.topMedicines, element: <TopMedicinesView initialQuery={{ fromDate: date, toDate: date, limit: 10 }} autoLoad />, response: [{ drugId: "00000000-0000-4000-8000-000000000001", drugName: "Private medicine result", totalQuantity: 12 }], marker: "Private medicine result", draft: "Mã khoa (tùy chọn)" },
  { name: "outpatient", api: mocks.outpatientDaily, element: <OutpatientReportView initialQuery={outpatientQuery} autoLoad onQuerySubmit={() => false} />, response: { ...outpatientQuery, departmentId: null, currency: "VND", zoneId: "Asia/Bangkok", coverageId: "00000000-0000-4000-8000-000000000001", sourceObservedAt: "2026-10-18T00:00:00Z", reconciledAt: "2026-10-18T00:05:00Z", covered: true, periods: [] }, marker: "Đã đối soát", draft: "Mã khoa (tùy chọn)" },
];
function session(token = "session-a", role: Role = "ADMIN") { storeSession({ accessToken: token, refreshToken: "test", role }); }
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (cause: unknown) => void; const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; }); return { promise, resolve, reject }; }

describe("Report private session lifecycle", () => {
  beforeEach(() => { vi.resetAllMocks(); session(); });
  afterEach(() => clearSession());

  it.each(cases)("drops $name results and drafts on same-role account change", async item => {
    const pending = deferred<unknown>(); item.api.mockResolvedValueOnce(item.response).mockReturnValueOnce(pending.promise);
    render(item.element); await screen.findByText(item.marker);
    fireEvent.change(screen.getByLabelText(item.draft), { target: { value: "private department draft" } });
    act(() => session("session-b"));
    expect(screen.queryByText(item.marker)).not.toBeInTheDocument(); expect(screen.getByLabelText(item.draft)).toHaveValue("");
    await waitFor(() => expect(item.api).toHaveBeenCalledTimes(2));
    await act(async () => pending.resolve(item.response)); expect(await screen.findByText(item.marker)).toBeVisible();
  });

  it.each(cases)("denies $name for a forbidden role without issuing a read", item => {
    session("patient-session", "PATIENT"); render(item.element);
    expect(screen.getByRole("alert")).toHaveTextContent("không có quyền"); expect(item.api).not.toHaveBeenCalled();
  });

  it("ignores an obsolete request's 401 after a new account starts its own read", async () => {
    const old = deferred<unknown>(); mocks.daily.mockReturnValueOnce(old.promise).mockResolvedValueOnce(daily);
    render(cases[0].element); await waitFor(() => expect(mocks.daily).toHaveBeenCalledTimes(1));
    act(() => session("session-b")); await screen.findByText(cases[0].marker);
    await act(async () => old.reject(new ApiRequestError("old session", 401)));
    expect(mocks.router.replace).not.toHaveBeenCalled(); expect(screen.getByText(cases[0].marker)).toBeVisible();
  });

  it("clears an outdated outpatient request on invalid submission and focuses the invalid field", async () => {
    const pending = deferred<unknown>(); mocks.outpatientDaily.mockReturnValue(pending.promise);
    render(cases[3].element); await waitFor(() => expect(mocks.outpatientDaily).toHaveBeenCalledTimes(1));
    fireEvent.change(screen.getByLabelText("Mã khoa (tùy chọn)"), { target: { value: "invalid" } });
    fireEvent.submit(screen.getByLabelText("Mã khoa (tùy chọn)").closest("form")!);
    expect(screen.getByLabelText("Mã khoa (tùy chọn)")).toHaveFocus();
    await act(async () => pending.resolve(cases[3].response));
    expect(screen.queryByText("Đã đối soát")).not.toBeInTheDocument(); expect(mocks.outpatientDaily).toHaveBeenCalledTimes(1);
  });

  it.each(cases.slice(0, 3))("clears a pending $name request after invalid resubmission and focuses the department", async item => {
    const pending = deferred<unknown>(); item.api.mockReturnValue(pending.promise);
    render(item.element); await waitFor(() => expect(item.api).toHaveBeenCalledTimes(1));
    const input = screen.getByLabelText("Mã khoa (tùy chọn)");
    fireEvent.change(input, { target: { value: "invalid" } }); fireEvent.submit(input.closest("form")!);
    expect(input).toHaveFocus(); await act(async () => pending.resolve(item.response));
    expect(screen.queryByText(item.marker)).not.toBeInTheDocument(); expect(item.api).toHaveBeenCalledTimes(1);
  });

  it("never dispatches an auto-load queued in a microtask after unmount", async () => {
    const view = render(cases[0].element); view.unmount(); await act(async () => {});
    expect(mocks.daily).not.toHaveBeenCalled();
  });
});
