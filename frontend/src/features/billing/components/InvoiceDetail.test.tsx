import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { clearSession, storeSession } from "@/lib/session";
import type { InvoiceDTO } from "../types";

const mocks = vi.hoisted(() => ({
  getById: vi.fn(),
  pay: vi.fn(),
  router: { replace: vi.fn() },
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ billingApi: { getById: mocks.getById, pay: mocks.pay } }));

import { InvoiceDetail } from "./InvoiceDetail";

const firstId = "00000000-0000-4000-8000-000000000001";
const secondId = "00000000-0000-4000-8000-000000000002";

function invoice(invoiceId: string, totalAmount: number): InvoiceDTO {
  return {
    invoiceId,
    patientId: "00000000-0000-4000-8000-000000000010",
    createdDate: "2026-10-20",
    totalAmount,
    isPaid: false,
    paymentMethod: null,
    prescriptionId: null,
    sagaStatus: "NONE",
    paidAt: null,
    fees: [],
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

describe("InvoiceDetail", () => {
  it("refreshes authoritative fee rows after confirmed payment", async () => {
    const initial = invoice(firstId, 100);
    initial.fees = [{ feeId: secondId, feeType: "EXAM", departmentId: secondId, incurredDate: "2026-10-20", amount: 100, isPaid: false }];
    mocks.getById.mockResolvedValue(initial).mockResolvedValueOnce(initial).mockResolvedValueOnce({ ...initial, isPaid: true, fees: initial.fees.map((fee) => ({ ...fee, isPaid: true })) });
    mocks.pay.mockResolvedValue({ success: true, paymentMethod: "CASH", paidAt: "2026-10-20T09:00:00Z", sagaStatus: "PAID" });
    render(<InvoiceDetail invoiceId={firstId} />);
    fireEvent.click(await screen.findByRole("button", { name: "Xác nhận thanh toán" }));
    expect(await screen.findByText("Đã trả")).toBeVisible();
    expect(screen.queryByText("Chưa trả")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xác nhận thanh toán" })).not.toBeInTheDocument();
  });

  it("keeps confirmed payment locked if refreshing the fee rows fails", async () => {
    mocks.getById.mockResolvedValue(invoice(firstId, 100)).mockResolvedValueOnce(invoice(firstId, 100)).mockRejectedValueOnce(new ApiRequestError("Không thể tải chi tiết mới", 503));
    mocks.pay.mockResolvedValue({ success: true, paymentMethod: "CASH", paidAt: "2026-10-20T09:00:00Z", sagaStatus: "PAID" });
    render(<InvoiceDetail invoiceId={firstId} />);
    fireEvent.click(await screen.findByRole("button", { name: "Xác nhận thanh toán" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/đã thanh toán.*chi tiết/i);
    expect(screen.getByText("Thanh toán thành công.")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Xác nhận thanh toán" })).not.toBeInTheDocument();
    expect(mocks.pay).toHaveBeenCalledTimes(1);
  });

  beforeEach(() => {
    vi.clearAllMocks();
    act(() => storeSession({ accessToken: "session-a", refreshToken: "refresh-a", role: "CASHIER" }));
  });

  afterEach(() => act(() => clearSession()));

  it("clears the previous invoice immediately when the route id changes", async () => {
    const nextInvoice = deferred<InvoiceDTO>();
    mocks.getById.mockResolvedValueOnce(invoice(firstId, 100)).mockReturnValueOnce(nextInvoice.promise).mockResolvedValue(invoice(secondId, 200));
    const view = render(<InvoiceDetail invoiceId={firstId} />);
    expect(await screen.findByText(firstId)).toBeVisible();

    view.rerender(<InvoiceDetail invoiceId={secondId} />);

    expect(screen.queryByText(firstId)).not.toBeInTheDocument();
    await act(async () => nextInvoice.resolve(invoice(secondId, 200)));
    expect(await screen.findByText(secondId)).toBeVisible();
  });

  it("does not offer blind retry after an invoice payment response is uncertain", async () => {
    mocks.getById.mockResolvedValue(invoice(firstId, 100));
    mocks.pay.mockRejectedValue(new ApiRequestError("Gateway timeout", 503));
    render(<InvoiceDetail invoiceId={firstId} />);
    fireEvent.click(await screen.findByRole("button", { name: "Xác nhận thanh toán" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/chưa xác nhận được kết quả/i);
    expect(screen.queryByRole("button", { name: "Xác nhận thanh toán" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Kiểm tra trạng thái hóa đơn" })).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Kiểm tra trạng thái hóa đơn" }));
    expect(await screen.findByText(/vẫn chưa xác định được trạng thái/i)).toBeVisible();
    expect(screen.queryByRole("button", { name: "Xác nhận thanh toán" })).not.toBeInTheDocument();
    expect(mocks.pay).toHaveBeenCalledTimes(1);
  });

  it("ignores a late 401 from the previous account after the session changes", async () => {
    const oldRead = deferred<InvoiceDTO>();
    mocks.getById.mockReturnValueOnce(oldRead.promise).mockResolvedValueOnce(invoice(secondId, 200)).mockResolvedValue(invoice(secondId, 200));
    const view = render(<InvoiceDetail invoiceId={firstId} />);
    view.rerender(<InvoiceDetail invoiceId={secondId} />);
    expect(await screen.findByText(secondId)).toBeVisible();

    act(() => storeSession({ accessToken: "session-b", refreshToken: "refresh-b", role: "CASHIER" }));
    expect(await screen.findByText(secondId)).toBeVisible();
    await act(async () => oldRead.reject(new ApiRequestError("Expired old token", 401)));

    expect(mocks.router.replace).not.toHaveBeenCalled();
    expect(screen.getByText(secondId)).toBeVisible();
  });

  it("locks synchronously while a payment request is in flight", async () => {
    const pending = deferred<never>();
    mocks.getById.mockResolvedValue(invoice(firstId, 100));
    mocks.pay.mockReturnValue(pending.promise);
    render(<InvoiceDetail invoiceId={firstId} />);
    const payButton = await screen.findByRole("button", { name: "Xác nhận thanh toán" });

    fireEvent.click(payButton);
    fireEvent.click(payButton);

    expect(mocks.pay).toHaveBeenCalledTimes(1);
  });
});
