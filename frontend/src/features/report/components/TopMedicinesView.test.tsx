import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { storeSession } from "@/lib/session";
beforeEach(() => storeSession({ accessToken: "report-test", refreshToken: "test", role: "ADMIN" }));
import type { TopMedicineDTO } from "../types";
const mocks = vi.hoisted(() => ({ topMedicines: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ reportApi: { topMedicines: mocks.topMedicines } }));
import { TopMedicinesView } from "./TopMedicinesView";

const query = { fromDate: "2024-02-01", toDate: "2024-02-29", limit: 10 };
const items: TopMedicineDTO[] = [
  { drugId: "00000000-0000-4000-8000-000000000002", drugName: "Thuốc Z", totalQuantity: 1234 },
  { drugId: "00000000-0000-4000-8000-000000000001", drugName: "Thuốc A", totalQuantity: 1000 },
];
function submit() { fireEvent.submit(screen.getByLabelText("Từ ngày").closest("form")!); }
function change(label: string, value: string) { fireEvent.change(screen.getByLabelText(label), { target: { value } }); }
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

describe("TopMedicinesView", () => {
  beforeEach(() => { mocks.topMedicines.mockReset(); mocks.router.replace.mockReset(); });
  it.each(["0", "51", "1.5", ""])("rejects invalid rank limit %s", (limit) => {
    render(<TopMedicinesView initialQuery={query} />); change("Số kết quả", limit); submit();
    expect(screen.getByRole("alert")).toHaveTextContent("Số kết quả");
    expect(mocks.topMedicines).not.toHaveBeenCalled();
  });
  it("rejects reversed dates and invalid department UUID", () => {
    render(<TopMedicinesView initialQuery={query} />); change("Từ ngày", "2024-03-01"); submit();
    expect(screen.getByRole("alert")).toHaveTextContent("Ngày bắt đầu");
    change("Từ ngày", query.fromDate); change("Mã khoa (tùy chọn)", "invalid"); submit();
    expect(screen.getByRole("alert")).toHaveTextContent("UUID");
    expect(mocks.topMedicines).not.toHaveBeenCalled();
  });
  it("preserves server ranking, names, exact IDs and quantities without inventing a unit or currency", async () => {
    mocks.topMedicines.mockResolvedValue(items);
    render(<TopMedicinesView initialQuery={query} autoLoad />);
    expect(await screen.findByText("1.234")).toBeVisible();
    const rows = screen.getAllByRole("listitem");
    expect(rows[0]).toHaveTextContent(items[0].drugName); expect(rows[1]).toHaveTextContent(items[1].drugName);
    expect(rows[0]).toHaveTextContent(items[0].drugId);
    expect(screen.queryByText(/VND|Viên/)).not.toBeInTheDocument();
    change("Đến ngày", "2024-03-31");
    expect(screen.getByText("Từ 01/02/2024 đến 29/02/2024 · Tối đa 10 kết quả")).toBeVisible();
    expect(mocks.topMedicines).toHaveBeenCalledTimes(1);
  });
  it("renders an empty response against the submitted range", async () => {
    mocks.topMedicines.mockResolvedValue([]);
    render(<TopMedicinesView initialQuery={query} autoLoad />);
    expect(await screen.findByText("Không có dữ liệu xuất thuốc trong khoảng đã chọn.")).toBeVisible();
    expect(screen.getByText(/Từ 01\/02\/2024 đến 29\/02\/2024/)).toBeVisible();
    expect(screen.queryByRole("list")).not.toBeInTheDocument();
  });
  it("clears an old ranking on failure and retries the failed submission rather than the new draft", async () => {
    mocks.topMedicines.mockResolvedValueOnce(items).mockRejectedValueOnce(new ApiRequestError("Mất kết nối", 503, "UNAVAILABLE", [], "corr-top")).mockResolvedValueOnce([]);
    render(<TopMedicinesView initialQuery={query} autoLoad />); await screen.findByText("Thuốc Z");
    change("Đến ngày", "2024-03-31"); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-top");
    expect(screen.queryByText("Thuốc Z")).not.toBeInTheDocument();
    change("Đến ngày", "2024-04-30"); fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("Không có dữ liệu xuất thuốc trong khoảng đã chọn.");
    expect(mocks.topMedicines).toHaveBeenLastCalledWith("2024-02-01", "2024-03-31", 10, undefined);
  });
  it("ignores an obsolete rejection after a newer successful query", async () => {
    const old = deferred<TopMedicineDTO[]>(), latest = deferred<TopMedicineDTO[]>();
    mocks.topMedicines.mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
    render(<TopMedicinesView initialQuery={query} autoLoad />);
    await waitFor(() => expect(mocks.topMedicines).toHaveBeenCalledTimes(1));
    change("Số kết quả", "5"); submit();
    await waitFor(() => expect(mocks.topMedicines).toHaveBeenCalledTimes(2));
    await act(async () => latest.resolve(items));
    await act(async () => old.reject(new ApiRequestError("Cũ", 401)));
    expect(screen.getByText("Thuốc Z")).toBeVisible();
    expect(screen.getByText(/Tối đa 5 kết quả/)).toBeVisible();
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });
  it("redirects 401 and shows a permission denial without retry", async () => {
    mocks.topMedicines.mockRejectedValueOnce(new ApiRequestError("Hết phiên", 401));
    const view = render(<TopMedicinesView initialQuery={query} autoLoad />);
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login")); view.unmount();
    mocks.topMedicines.mockRejectedValueOnce(new ApiRequestError("Không có quyền", 403, "FORBIDDEN", [], "corr-permission"));
    render(<TopMedicinesView initialQuery={query} autoLoad />);
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-permission");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });
  it("ignores pending session errors after switching away from the view", async () => {
    const pending = deferred<TopMedicineDTO[]>(); mocks.topMedicines.mockReturnValue(pending.promise);
    const view = render(<TopMedicinesView initialQuery={query} autoLoad />);
    await waitFor(() => expect(mocks.topMedicines).toHaveBeenCalledTimes(1)); view.unmount();
    await act(async () => pending.reject(new ApiRequestError("Hết phiên", 401)));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });
  it("passes valid filters to URL composition without a second request", () => {
    const onQuerySubmit = vi.fn(() => true);
    render(<TopMedicinesView initialQuery={query} onQuerySubmit={onQuerySubmit} />); submit();
    expect(onQuerySubmit).toHaveBeenCalledWith({ ...query, departmentId: undefined });
    expect(mocks.topMedicines).not.toHaveBeenCalled();
  });
});
