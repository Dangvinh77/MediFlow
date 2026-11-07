import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal } from "@/lib/format";
import { storeSession } from "@/lib/session";
const mocks = vi.hoisted(() => ({ paymentHistory: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks }));
vi.mock("../api", () => ({ billingApi: { paymentHistory: mocks.paymentHistory } }));
import { PaymentHistoryLookup } from "./PaymentHistoryLookup";
const patient = "00000000-0000-4000-8000-000000000001";
const paid = { transactionId: "t-1", paymentRequestId: "r-1", patientId: patient, departmentId: "d-1", transactionType: "PAYMENT", purpose: "LAB_TEST", amount: 80000, currency: "VND", paymentMethod: "CASH", completedAt: "2026-11-07T03:00:00Z" };
const page = (content: unknown[], number = 0, totalPages = 1, totalElements = content.length) => ({ content, number, totalPages, totalElements, size: 20 });
function search(value = patient) { fireEvent.change(screen.getByLabelText("Bệnh nhân"), { target: { value } }); fireEvent.click(screen.getByRole("button", { name: "Xem lịch sử thu" })); }
beforeEach(() => { vi.clearAllMocks(); act(() => storeSession({ accessToken: "a", refreshToken: "r", role: "CASHIER" })); });
describe("PaymentHistoryLookup", () => {
  it("opens on collected money from every patient and labels what was collected", async () => {
    mocks.paymentHistory.mockResolvedValueOnce(page([paid]));
    render(<PaymentHistoryLookup />);
    expect(await screen.findByText("Xét nghiệm")).toBeVisible();
    expect(screen.getByText(new RegExp(formatDecimal(80000)))).toBeVisible();
    expect(screen.getByText("Tiền mặt")).toBeVisible();
    expect(screen.getByText("Đã thu")).toBeVisible();
    expect(mocks.paymentHistory).toHaveBeenCalledWith(null, 0, 20);
  });
  it("shows a refund as money going back, not as a collection", async () => {
    mocks.paymentHistory.mockResolvedValueOnce(page([{ ...paid, transactionId: "t-2", transactionType: "REFUND", purpose: null }]));
    render(<PaymentHistoryLookup />);
    expect(await screen.findByText("Hoàn tiền")).toBeVisible();
    expect(screen.getByText(new RegExp(`−${formatDecimal(80000)}`))).toBeVisible();
    expect(screen.getByText("Khoản khác")).toBeVisible();
  });
  it("keeps the submitted patient while paging", async () => {
    mocks.paymentHistory.mockResolvedValueOnce(page([paid])).mockResolvedValueOnce(page([paid], 0, 2, 21)).mockResolvedValueOnce(page([{ ...paid, transactionId: "t-3" }], 1, 2, 21));
    render(<PaymentHistoryLookup />);
    await screen.findByText("Xét nghiệm");
    search();
    fireEvent.change(await screen.findByLabelText("Bệnh nhân"), { target: { value: "unsubmitted-draft" } });
    fireEvent.click(await screen.findByRole("button", { name: "Trang sau" }));
    await screen.findByText("Xét nghiệm");
    expect(mocks.paymentHistory).toHaveBeenLastCalledWith(patient, 1, 20);
  });
  it("retries a failed read and explains an empty result", async () => {
    mocks.paymentHistory.mockRejectedValueOnce(new ApiRequestError("Unavailable", 503, undefined, [], "trace-read")).mockResolvedValueOnce(page([]));
    render(<PaymentHistoryLookup />);
    fireEvent.click(await screen.findByText("Thông tin hỗ trợ"));
    expect(await screen.findByText("trace-read")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Chưa có khoản thu nào.")).toBeVisible();
  });
  it("rejects an invalid patient without a second read", async () => {
    mocks.paymentHistory.mockResolvedValueOnce(page([paid]));
    render(<PaymentHistoryLookup />);
    await screen.findByText("Xét nghiệm");
    search("invalid");
    expect(await screen.findByRole("alert")).toHaveTextContent("Chọn một bệnh nhân hợp lệ.");
    expect(mocks.paymentHistory).toHaveBeenCalledTimes(1);
  });
});
