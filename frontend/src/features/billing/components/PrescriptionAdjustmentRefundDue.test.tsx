import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { LedgerRefundDTO, PrescriptionAdjustmentDTO } from "../types";

const mocks = vi.hoisted(() => ({
  getPrescriptionAdjustment: vi.fn(),
  refundLedgerPayment: vi.fn(),
  router: { replace: vi.fn() },
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({
  billingApi: {
    getPrescriptionAdjustment: mocks.getPrescriptionAdjustment,
    refundLedgerPayment: mocks.refundLedgerPayment,
  },
}));

import { PrescriptionAdjustmentRefundDue } from "./PrescriptionAdjustmentRefundDue";

const adjustment: PrescriptionAdjustmentDTO = {
  prescriptionId: "10000000-0000-4000-8000-000000000001",
  eventType: "prescription.rejected",
  status: "APPLIED",
  rejectionCode: null,
  refundsDue: [
    { originalTransactionId: "20000000-0000-4000-8000-000000000002", amount: 150000, currency: "VND" },
  ],
};

const refundResult: LedgerRefundDTO = {
  refundTransactionId: "30000000-0000-4000-8000-000000000003",
  originalTransactionId: "20000000-0000-4000-8000-000000000002",
  accountId: "40000000-0000-4000-8000-000000000004",
  paymentRequestId: null,
  classification: "SERVICE_PAYMENT",
  status: "COMPLETED",
  amount: 150000,
  currency: "VND",
  paymentMethod: "CASH",
  completedAt: "2026-10-19T10:00:00Z",
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

async function openRefundForm() {
  fireEvent.click(await screen.findByRole("button", { name: "Hoàn tiền" }));
}

describe("Prescription adjustment refund-due view", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getPrescriptionAdjustment.mockReset();
    mocks.refundLedgerPayment.mockReset();
  });

  it("shows a loading state before the adjustment resolves", () => {
    mocks.getPrescriptionAdjustment.mockReturnValue(deferred<PrescriptionAdjustmentDTO>().promise);
    render(<PrescriptionAdjustmentRefundDue prescriptionId={adjustment.prescriptionId} />);
    expect(screen.getByText("Đang tải điều chỉnh đơn thuốc…")).toBeVisible();
  });

  it("shows a neutral empty state when the prescription has no recorded adjustment", async () => {
    mocks.getPrescriptionAdjustment.mockRejectedValue(new ApiRequestError("Not found", 404, "BILLING_PRESCRIPTION_ADJUSTMENT_NOT_FOUND"));
    render(<PrescriptionAdjustmentRefundDue prescriptionId={adjustment.prescriptionId} />);
    expect(await screen.findByText(/Không tìm thấy điều chỉnh nào/)).toBeVisible();
  });

  it("redirects to login on an expired session", async () => {
    mocks.getPrescriptionAdjustment.mockRejectedValue(new ApiRequestError("Phiên hết hạn", 401, "UNAUTHORIZED"));
    render(<PrescriptionAdjustmentRefundDue prescriptionId={adjustment.prescriptionId} />);
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
  });

  it("shows a retryable error with correlation id on a server failure", async () => {
    mocks.getPrescriptionAdjustment.mockRejectedValue(new ApiRequestError("Lỗi máy chủ", 500, "INTERNAL", [], "corr-1"));
    render(<PrescriptionAdjustmentRefundDue prescriptionId={adjustment.prescriptionId} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Lỗi máy chủ");
    expect(screen.getByText("corr-1")).toBeVisible();

    mocks.getPrescriptionAdjustment.mockResolvedValue(adjustment);
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("prescription.rejected")).toBeVisible();
  });

  it("renders the adjustment status and refund-due rows", async () => {
    mocks.getPrescriptionAdjustment.mockResolvedValue(adjustment);
    render(<PrescriptionAdjustmentRefundDue prescriptionId={adjustment.prescriptionId} />);
    expect(await screen.findByText("prescription.rejected")).toBeVisible();
    expect(screen.getByText("150.000 VND")).toBeVisible();
    expect(screen.getByText("20000000-0000-4000-8000-000000000002")).toBeVisible();
  });

  it("shows a neutral message when an applied adjustment has nothing left to refund", async () => {
    mocks.getPrescriptionAdjustment.mockResolvedValue({ ...adjustment, refundsDue: [] });
    render(<PrescriptionAdjustmentRefundDue prescriptionId={adjustment.prescriptionId} />);
    expect(await screen.findByText("Không còn khoản nào cần hoàn tiền cho đơn thuốc này.")).toBeVisible();
  });

  it("rejects a refund amount above the due amount and a blank reason before submitting", async () => {
    mocks.getPrescriptionAdjustment.mockResolvedValue(adjustment);
    render(<PrescriptionAdjustmentRefundDue prescriptionId={adjustment.prescriptionId} />);
    await openRefundForm();

    const amountInput = screen.getByLabelText("Số tiền hoàn (VND)", { exact: false });
    fireEvent.change(amountInput, { target: { value: "999999" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận hoàn tiền" }));
    expect(screen.getByText(/không được vượt quá khoản còn phải trả/)).toBeVisible();
    expect(mocks.refundLedgerPayment).not.toHaveBeenCalled();

    fireEvent.change(amountInput, { target: { value: "150000" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận hoàn tiền" }));
    expect(screen.getByText("Lý do hoàn tiền là bắt buộc.")).toBeVisible();
    expect(mocks.refundLedgerPayment).not.toHaveBeenCalled();
  });

  it("records a refund, shows confirmation, and refetches the adjustment", async () => {
    mocks.getPrescriptionAdjustment.mockResolvedValueOnce(adjustment);
    mocks.refundLedgerPayment.mockResolvedValue(refundResult);
    render(<PrescriptionAdjustmentRefundDue prescriptionId={adjustment.prescriptionId} />);
    await openRefundForm();

    fireEvent.change(screen.getByLabelText("Số tiền hoàn (VND)", { exact: false }), { target: { value: "150000" } });
    fireEvent.change(screen.getByLabelText("Lý do hoàn tiền", { exact: false }), { target: { value: "Đơn thuốc bị từ chối" } });

    mocks.getPrescriptionAdjustment.mockResolvedValueOnce({ ...adjustment, refundsDue: [] });
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Xác nhận hoàn tiền" }));
    });

    expect(mocks.refundLedgerPayment).toHaveBeenCalledWith(
      "20000000-0000-4000-8000-000000000002",
      expect.objectContaining({ amount: 150000, reason: "Đơn thuốc bị từ chối", paymentMethod: "CASH" }),
    );
    expect(await screen.findByText(/Đã ghi nhận hoàn tiền/)).toBeVisible();
    await waitFor(() => expect(mocks.getPrescriptionAdjustment).toHaveBeenCalledTimes(2));
  });

  it("shows the backend error and correlation id when a refund fails", async () => {
    mocks.getPrescriptionAdjustment.mockResolvedValue(adjustment);
    mocks.refundLedgerPayment.mockRejectedValue(
      new ApiRequestError("Số tiền hoàn vượt quá khoản đã thanh toán", 409, "BILLING_REFUND_EXCEEDS_PAYMENT", [], "refund-corr-1"),
    );
    render(<PrescriptionAdjustmentRefundDue prescriptionId={adjustment.prescriptionId} />);
    await openRefundForm();

    fireEvent.change(screen.getByLabelText("Số tiền hoàn (VND)", { exact: false }), { target: { value: "150000" } });
    fireEvent.change(screen.getByLabelText("Lý do hoàn tiền", { exact: false }), { target: { value: "Đơn thuốc bị từ chối" } });
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Xác nhận hoàn tiền" }));
    });

    expect(await screen.findByText("Số tiền hoàn vượt quá khoản đã thanh toán")).toBeVisible();
    expect(screen.getByText("refund-corr-1")).toBeVisible();
  });
});
