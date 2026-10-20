import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { clearSession, storeSession } from "@/lib/session";
import type { LedgerPaymentDTO } from "../types";

const mocks = vi.hoisted(() => ({ complete: vi.fn() }));
vi.mock("next/link", () => ({ default: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a> }));
vi.mock("../api", () => ({ billingApi: { completeLedgerPayment: mocks.complete } }));

import { LedgerPaymentForm } from "./LedgerPaymentForm";

const firstId = "00000000-0000-4000-8000-000000000001";
const secondId = "00000000-0000-4000-8000-000000000002";

function payment(paymentRequestId: string, status: LedgerPaymentDTO["status"] = "COMPLETED"): LedgerPaymentDTO {
  return {
    transactionId: "00000000-0000-4000-8000-000000000030",
    accountId: "00000000-0000-4000-8000-000000000020",
    paymentRequestId,
    classification: "SERVICE_PAYMENT",
    status,
    amount: 125.5,
    currency: "VND",
    paymentMethod: "CASH",
    completedAt: "2026-10-20T10:00:00Z",
  };
}

function enterValidPayment(amount = "125.50") {
  fireEvent.change(screen.getByLabelText("Số tiền", { exact: false }), { target: { value: amount } });
  fireEvent.change(screen.getByLabelText("Mã tiền (ISO 4217)", { exact: false }), { target: { value: "VND" } });
}

describe("LedgerPaymentForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    act(() => storeSession({ accessToken: "session-a", refreshToken: "refresh-a", role: "CASHIER" }));
  });

  afterEach(() => act(() => clearSession()));

  it.each([200, 408, 503])("retries an uncertain HTTP %s result only with the frozen payload and idempotency key", async (status) => {
    mocks.complete.mockRejectedValueOnce(new ApiRequestError("Unreadable or uncertain response", status)).mockResolvedValueOnce(payment(firstId));
    render(<LedgerPaymentForm requestId={firstId} remainingAmount={200} currency="VND" />);
    enterValidPayment();
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận thanh toán" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/thử lại đúng giao dịch/i);
    expect(screen.getByLabelText("Số tiền", { exact: false })).toBeDisabled();
    expect(screen.getByLabelText("Mã tiền (ISO 4217)", { exact: false })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Thử lại cùng giao dịch" })).toBeEnabled();
    const submitted = mocks.complete.mock.calls[0][1];

    fireEvent.click(screen.getByRole("button", { name: "Thử lại cùng giao dịch" }));
    expect(await screen.findByRole("status")).toHaveTextContent(/thanh toán thành công/i);
    expect(mocks.complete).toHaveBeenCalledTimes(2);
    expect(mocks.complete.mock.calls[1][0]).toBe(firstId);
    expect(mocks.complete.mock.calls[1][1]).toEqual(submitted);
  });

  it("creates a new idempotency key after a definitive rejection and edited payload", async () => {
    mocks.complete.mockRejectedValueOnce(new ApiRequestError("Currency mismatch", 422)).mockResolvedValueOnce(payment(firstId));
    render(<LedgerPaymentForm requestId={firstId} remainingAmount={200} currency="VND" />);
    enterValidPayment();
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận thanh toán" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Currency mismatch");

    fireEvent.change(screen.getByLabelText("Số tiền", { exact: false }), { target: { value: "130.00" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận thanh toán" }));
    expect(await screen.findByRole("status")).toHaveTextContent(/thanh toán thành công/i);

    const firstCommand = mocks.complete.mock.calls[0][1];
    const secondCommand = mocks.complete.mock.calls[1][1];
    expect(secondCommand.amount).toBe(130);
    expect(secondCommand.idempotencyKey).not.toBe(firstCommand.idempotencyKey);
  });

  it("does not report a non-completed transaction as successful", async () => {
    mocks.complete.mockResolvedValue(payment(firstId, "PENDING"));
    render(<LedgerPaymentForm requestId={firstId} remainingAmount={200} currency="VND" />);
    enterValidPayment();
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận thanh toán" }));

    expect(await screen.findByRole("status")).toHaveTextContent(/đang chờ xác nhận/i);
    expect(screen.queryByText("Thanh toán thành công.")).not.toBeInTheDocument();
  });

  it("clears the old payment result when request id changes", async () => {
    mocks.complete.mockResolvedValue(payment(firstId));
    const view = render(<LedgerPaymentForm requestId={firstId} remainingAmount={200} currency="VND" />);
    enterValidPayment();
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận thanh toán" }));
    expect(await screen.findByRole("status")).toHaveTextContent(/thanh toán thành công/i);

    view.rerender(<LedgerPaymentForm requestId={secondId} remainingAmount={300} currency="USD" />);

    expect(screen.queryByText(payment(firstId).transactionId)).not.toBeInTheDocument();
    expect(screen.getByLabelText("Số tiền", { exact: false })).toHaveValue("300");
    expect(screen.getByLabelText("Mã tiền", { exact: false })).toHaveValue("USD");
  });

  it("rejects decimal amounts with more than two places before calling the API", async () => {
    render(<LedgerPaymentForm requestId={firstId} remainingAmount={200} currency="VND" />);
    enterValidPayment("125.501");
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận thanh toán" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/tối đa 2 chữ số thập phân/i);
    expect(mocks.complete).not.toHaveBeenCalled();
  });

  it("locks synchronously to prevent duplicate submissions", async () => {
    let resolve!: (value: LedgerPaymentDTO) => void;
    mocks.complete.mockReturnValue(new Promise<LedgerPaymentDTO>((done) => { resolve = done; }));
    render(<LedgerPaymentForm requestId={firstId} remainingAmount={200} currency="VND" />);
    enterValidPayment();
    const submit = screen.getByRole("button", { name: "Xác nhận thanh toán" });

    fireEvent.submit(submit.closest("form")!);
    fireEvent.submit(submit.closest("form")!);

    expect(mocks.complete).toHaveBeenCalledTimes(1);
    await act(async () => resolve(payment(firstId)));
  });

  it("does not send another payment after a completed transaction", async () => {
    mocks.complete.mockResolvedValue(payment(firstId));
    render(<LedgerPaymentForm requestId={firstId} remainingAmount={200} currency="VND" />);
    const form = screen.getByRole("button", { name: "Xác nhận thanh toán" }).closest("form")!;
    fireEvent.submit(form);
    await screen.findByRole("status");
    fireEvent.submit(form);
    expect(mocks.complete).toHaveBeenCalledTimes(1);
  });

  it("blocks direct payment by request id without a verified server snapshot", () => {
    render(<LedgerPaymentForm requestId={firstId} />);

    expect(screen.getByRole("alert")).toHaveTextContent(/tra cứu khoản phí nguồn/i);
    expect(screen.queryByLabelText("Số tiền", { exact: false })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xác nhận thanh toán" })).not.toBeInTheDocument();
  });
});
