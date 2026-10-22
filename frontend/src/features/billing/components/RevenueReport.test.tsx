import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal } from "@/lib/format";

const mocks = vi.hoisted(() => ({ revenueByDepartment: vi.fn() }));
vi.mock("../api", () => ({ billingApi: { revenueByDepartment: mocks.revenueByDepartment } }));

import { RevenueReport } from "./RevenueReport";

const deptA = "00000000-0000-4000-8000-000000000001";
const deptB = "00000000-0000-4000-8000-000000000002";

function submit() {
  fireEvent.click(screen.getByRole("button", { name: "Xem báo cáo" }));
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("RevenueReport", () => {
  it("renders server-reported department totals and a summed footer row", async () => {
    mocks.revenueByDepartment.mockResolvedValue([
      { departmentId: deptA, totalRevenue: 1250.5, invoiceCount: 3 },
      { departmentId: deptB, totalRevenue: 500, invoiceCount: 1 },
    ]);
    render(<RevenueReport />);
    submit();

    const table = await screen.findByRole("table");
    expect(within(table).getByText(deptA)).not.toBeVisible();
    expect(within(table).getByText(formatDecimal(1250.5))).toBeVisible();
    expect(within(table).getByText(formatDecimal(500))).toBeVisible();
    // footer sums invoice count (4) and revenue (1750.5) from server rows, not recomputed client totals
    expect(within(table).getByText("4")).toBeVisible();
    expect(within(table).getByText(formatDecimal(1750.5))).toBeVisible();
  });

  it("passes the trimmed department filter and date range to the API", async () => {
    mocks.revenueByDepartment.mockResolvedValue([]);
    render(<RevenueReport />);
    fireEvent.change(screen.getByLabelText("Từ ngày", { exact: false }), { target: { value: "2026-10-01" } });
    fireEvent.change(screen.getByLabelText("Đến ngày", { exact: false }), { target: { value: "2026-10-20" } });
    fireEvent.change(screen.getByLabelText("Khoa (tuỳ chọn)", { exact: false }), { target: { value: `  ${deptA}  ` } });
    submit();

    await screen.findByText("Không có doanh thu đã thanh toán trong khoảng ngày này.");
    expect(mocks.revenueByDepartment).toHaveBeenCalledWith("2026-10-01", "2026-10-20", deptA);
  });

  it("omits the department filter entirely when left blank", async () => {
    mocks.revenueByDepartment.mockResolvedValue([]);
    render(<RevenueReport />);
    submit();
    await screen.findByText("Không có doanh thu đã thanh toán trong khoảng ngày này.");
    expect(mocks.revenueByDepartment).toHaveBeenCalledWith(expect.any(String), expect.any(String), undefined);
  });

  it("rejects a from-date after the to-date without calling the API", async () => {
    render(<RevenueReport />);
    fireEvent.change(screen.getByLabelText("Từ ngày", { exact: false }), { target: { value: "2026-10-20" } });
    fireEvent.change(screen.getByLabelText("Đến ngày", { exact: false }), { target: { value: "2026-10-01" } });
    submit();

    expect(await screen.findByRole("alert")).toHaveTextContent("Ngày bắt đầu không được sau ngày kết thúc.");
    expect(mocks.revenueByDepartment).not.toHaveBeenCalled();
  });

  it("requires both dates before issuing a request", async () => {
    render(<RevenueReport />);
    fireEvent.change(screen.getByLabelText("Từ ngày", { exact: false }), { target: { value: "" } });
    submit();

    expect(await screen.findByRole("alert")).toHaveTextContent("Cần chọn đủ ngày bắt đầu và ngày kết thúc.");
    expect(mocks.revenueByDepartment).not.toHaveBeenCalled();
  });

  it("shows the server error with correlation id and retries the same query", async () => {
    mocks.revenueByDepartment
      .mockRejectedValueOnce(new ApiRequestError("Unavailable", 503, undefined, [], "trace-revenue"))
      .mockResolvedValueOnce([{ departmentId: deptA, totalRevenue: 100, invoiceCount: 1 }]);
    render(<RevenueReport />);
    submit();

    expect(await screen.findByText("trace-revenue")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));

    const table = await screen.findByRole("table");
    expect(within(table).getByText(deptA)).not.toBeVisible();
    expect(mocks.revenueByDepartment).toHaveBeenCalledTimes(2);
  });

  it("disables the submit button and shows a busy state while loading", async () => {
    let resolve!: (value: never[]) => void;
    mocks.revenueByDepartment.mockReturnValue(new Promise((done) => { resolve = done; }));
    render(<RevenueReport />);
    submit();

    const button = screen.getByRole("button", { name: /Đang xử lý/ });
    expect(button).toBeDisabled();
    expect(screen.getByRole("status", { name: "Đang tải báo cáo doanh thu…" })).toBeVisible();

    resolve([]);
    await screen.findByText("Không có doanh thu đã thanh toán trong khoảng ngày này.");
  });
});
