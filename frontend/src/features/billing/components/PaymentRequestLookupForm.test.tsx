import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { clearSession, storeSession } from "@/lib/session";
import type { LedgerPaymentDTO, PaymentRequestDiscoveryDTO } from "../types";

const mocks = vi.hoisted(() => ({ discover: vi.fn(), complete: vi.fn(), labPayment: vi.fn(), quote: vi.fn(), labZero: vi.fn(), push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: mocks.push, replace: mocks.replace }) }));
vi.mock("next/link", () => ({ default: ({ href, children }: { href: string; children: import("react").ReactNode }) => <a href={href}>{children}</a> }));
vi.mock("../api", () => ({ billingApi: {
  discoverPaymentRequest: mocks.discover,
  completeLedgerPayment: mocks.complete,
  createLabPayment: mocks.labPayment,
  outpatientQuote: mocks.quote,
  confirmLabZeroLiability: mocks.labZero,
} }));

import { PaymentRequestLookupForm } from "./PaymentRequestLookupForm";

const requestId = "00000000-0000-4000-8000-000000000001";
const appointmentId = "00000000-0000-4000-8000-000000000002";
const patientId = "00000000-0000-4000-8000-000000000003";

function discovery(status: PaymentRequestDiscoveryDTO["status"] = "PENDING", remainingAmount = 75): PaymentRequestDiscoveryDTO {
  return {
    paymentRequestId: requestId,
    invoiceId: "00000000-0000-4000-8000-000000000005",
    accountId: "00000000-0000-4000-8000-000000000006",
    patientId,
    departmentId: "00000000-0000-4000-8000-000000000007",
    purpose: "EXAM",
    sourceType: "EXAM",
    sourceId: appointmentId,
    careEpisodeId: appointmentId,
    careEpisodeType: "OUTPATIENT_VISIT",
    appointmentId,
    recordId: null,
    prescriptionId: null,
    labTestIds: [],
    requestedAmount: 100,
    paidAmount: 25,
    remainingAmount,
    currency: "VND",
    status,
    accountStatus: "OPEN",
    expiresAt: null,
    observedAt: "2026-10-20T10:00:00Z",
  };
}

function fillExamIdentifiers() {
  fireEvent.change(screen.getByLabelText("Mã lịch khám", { exact: false }), { target: { value: appointmentId } });
  fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: patientId } });
}

function payment(): LedgerPaymentDTO {
  return {
    transactionId: "00000000-0000-4000-8000-000000000030",
    accountId: "00000000-0000-4000-8000-000000000006",
    paymentRequestId: requestId,
    classification: "SERVICE_PAYMENT",
    status: "COMPLETED",
    amount: 75,
    currency: "VND",
    paymentMethod: "CASH",
    completedAt: "2026-10-20T10:05:00Z",
  };
}

describe("PaymentRequestLookupForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    act(() => storeSession({ accessToken: "session-a", refreshToken: "refresh-a", role: "CASHIER" }));
    mocks.quote.mockResolvedValue({
      quoteId: "quote-id", paymentRequestId: requestId, patientId, careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: appointmentId,
      currency: "VND", lines: [{ sourceId: appointmentId, sourceType: "EXAM", priceCode: "CONSULT", catalogueVersion: "catalogue-1",
        policyVersion: "policy-1", description: "Khám chuyên khoa", quantity: "1.00", unitPrice: "100.00", grossAmount: "100.00",
        supportRate: "0.00", supportAmount: "0.00", patientPayable: "100.00" }],
      grossAmount: "100.00", supportAmount: "0.00", patientPayable: "100.00", issuedAt: "2027-10-26T10:00:00Z", expiresAt: "2027-10-26T10:15:00Z",
    });
  });

  afterEach(() => act(() => clearSession()));

  it("discovers an exact EXAM charge and pays the server-reported remaining amount and currency", async () => {
    mocks.discover.mockResolvedValue(discovery());
    mocks.complete.mockResolvedValue(payment());
    render(<PaymentRequestLookupForm />);
    fillExamIdentifiers();
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu khoản phí" }));

    expect(mocks.discover).toHaveBeenCalledWith({ purpose: "EXAM", sourceId: appointmentId, patientId, careEpisodeId: appointmentId });
    expect(await screen.findByRole("region", { name: "Chi tiết khoản phí" })).toBeVisible();
    expect(screen.getByText("Còn phải thu")).toBeVisible();
    expect(within(screen.getByRole("region", { name: "Chi tiết khoản phí" })).getAllByText(/75.*VND/).length).toBeGreaterThan(0);
    expect(screen.getByLabelText("Mã tiền", { exact: false })).toHaveValue("VND");
    expect(screen.getByLabelText("Số tiền", { exact: false })).toHaveValue("75");

    fireEvent.click(screen.getByRole("button", { name: "Xác nhận thanh toán" }));
    expect(await screen.findByText(/thanh toán thành công/i)).toBeVisible();
    expect(mocks.complete).toHaveBeenCalledWith(requestId, expect.objectContaining({ amount: 75, currency: "VND" }));
  });

  it("keeps the payment workspace mounted while payment is unresolved", async () => {
    mocks.discover.mockResolvedValue(discovery());
    mocks.complete.mockReturnValue(new Promise(() => {}));
    render(<PaymentRequestLookupForm />);
    fillExamIdentifiers();
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu khoản phí" }));
    fireEvent.click(await screen.findByRole("button", { name: "Xác nhận thanh toán" }));
    expect(screen.getByRole("button", { name: "Tra cứu khoản phí" })).toBeDisabled();
  });

  it("requires valid identifiers before making a discovery request", async () => {
    render(<PaymentRequestLookupForm />);
    fillExamIdentifiers();
    fireEvent.change(screen.getByLabelText("Mã lịch khám", { exact: false }), { target: { value: "not-a-uuid" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu khoản phí" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/UUID hợp lệ/i);
    expect(mocks.discover).not.toHaveBeenCalled();
  });

  it("does not offer payment for a terminal or closed request", async () => {
    mocks.discover.mockResolvedValue({ ...discovery("PAID", 0), accountStatus: "SETTLED" });
    render(<PaymentRequestLookupForm />);
    fillExamIdentifiers();
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu khoản phí" }));

    expect(await screen.findByRole("region", { name: "Chi tiết khoản phí" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Xác nhận thanh toán" })).not.toBeInTheDocument();
    expect(screen.getByText(/không còn ở trạng thái có thể thu/i)).toBeVisible();
  });

  it("discards an unresolved discovery result after the signed account changes", async () => {
    let resolveOld!: (value: PaymentRequestDiscoveryDTO) => void;
    mocks.discover.mockReturnValue(new Promise<PaymentRequestDiscoveryDTO>((resolve) => { resolveOld = resolve; }));
    render(<PaymentRequestLookupForm />);
    fillExamIdentifiers();
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu khoản phí" }));
    act(() => storeSession({ accessToken: "session-b", refreshToken: "refresh-b", role: "CASHIER" }));

    expect(await screen.findByRole("button", { name: "Tra cứu khoản phí" })).toBeVisible();
    await act(async () => resolveOld(discovery()));
    expect(screen.queryByRole("region", { name: "Chi tiết khoản phí" })).not.toBeInTheDocument();
  });

  it("treats a missing legacy quote as unavailable rather than zero payable", async () => {
    mocks.discover.mockResolvedValue(discovery());
    mocks.quote.mockRejectedValue(new ApiRequestError("No quote", 404, "BILLING_QUOTE_NOT_FOUND"));
    mocks.complete.mockResolvedValue(payment());
    render(<PaymentRequestLookupForm quoteEnabled zeroLiabilityEnabled />);
    fillExamIdentifiers();
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu khoản phí" }));

    expect(await screen.findByText(/Không xem đây là khoản miễn phí/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xác nhận nghĩa vụ 0 đồng" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xác nhận thanh toán" })).toBeEnabled();
  });

  it("does not fall back to the V1 LAB payment route when a V2 quote is unavailable", async () => {
    const labId = "00000000-0000-4000-8000-000000000011";
    const episodeId = "00000000-0000-4000-8000-000000000012";
    mocks.discover.mockResolvedValue({ ...discovery("PENDING", 80), purpose: "LAB_TEST", sourceType: "LAB_TEST", sourceId: labId, careEpisodeId: episodeId, requestedAmount: 80, paidAmount: 0 });
    mocks.quote.mockRejectedValue(new ApiRequestError("No quote", 404, "BILLING_QUOTE_NOT_FOUND"));
    render(<PaymentRequestLookupForm quoteEnabled zeroLiabilityEnabled />);

    fireEvent.change(screen.getByRole("combobox"), { target: { value: "LAB_TEST" } });
    fireEvent.change(screen.getByLabelText("Mã xét nghiệm", { exact: false }), { target: { value: labId } });
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: patientId } });
    fireEvent.change(screen.getByLabelText("Mã đợt điều trị", { exact: false }), { target: { value: episodeId } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu khoản phí" }));

    expect(await screen.findByText(/luồng thanh toán LAB V2 cần báo giá/i)).toBeVisible();
    expect(screen.getByText(/Không thể thu LAB V2 khi Billing chưa cấp báo giá/i)).toBeVisible();
    expect(screen.queryByRole("button", { name: "Xác nhận thanh toán" })).not.toBeInTheDocument();
    expect(mocks.complete).not.toHaveBeenCalled();
    expect(mocks.labPayment).not.toHaveBeenCalled();
  });

  it("confirms a zero-payable LAB quote with its own clearance command, never a cash payment", async () => {
    const labId = "00000000-0000-4000-8000-000000000011";
    const episodeId = "00000000-0000-4000-8000-000000000012";
    mocks.discover.mockResolvedValue({ ...discovery("PENDING", 0), purpose: "LAB_TEST", sourceType: "LAB_TEST", sourceId: labId, careEpisodeId: episodeId, careEpisodeType: "OUTPATIENT_VISIT", requestedAmount: 0, paidAmount: 0 });
    mocks.quote.mockResolvedValue({
      quoteId: "lab-quote-id", paymentRequestId: requestId, patientId, careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: episodeId,
      currency: "VND", lines: [{ sourceId: labId, sourceType: "LAB_TEST", priceCode: "CBC", catalogueVersion: "catalogue-1",
        policyVersion: "policy-1", description: "Công thức máu", quantity: "1.00", unitPrice: "100.00", grossAmount: "100.00",
        supportRate: "1.00", supportAmount: "100.00", patientPayable: "0.00" }],
      grossAmount: "100.00", supportAmount: "100.00", patientPayable: "0.00", issuedAt: "2027-10-26T10:00:00Z", expiresAt: "2027-10-26T10:15:00Z",
    });
    mocks.labZero.mockResolvedValue({ eligibilityOperationId: "lab-zero-operation", paymentRequestId: requestId, quoteId: "lab-quote-id", basis: "ZERO_LIABILITY", clearanceId: "lab-clearance-id", completedAt: "2027-10-26T10:05:00Z" });
    render(<PaymentRequestLookupForm quoteEnabled zeroLiabilityEnabled />);

    fireEvent.change(screen.getByRole("combobox"), { target: { value: "LAB_TEST" } });
    fireEvent.change(screen.getByLabelText("Mã xét nghiệm", { exact: false }), { target: { value: labId } });
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: patientId } });
    fireEvent.change(screen.getByLabelText("Mã đợt điều trị", { exact: false }), { target: { value: episodeId } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu khoản phí" }));
    await screen.findByRole("region", { name: "Báo giá ngoại trú" });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận nghĩa vụ 0 đồng" }));

    expect(await screen.findByText(/lab-clearance-id/)).toBeVisible();
    expect(mocks.labZero).toHaveBeenCalledWith(requestId, { quoteId: "lab-quote-id" }, expect.objectContaining({ idempotencyKey: expect.any(String), correlationId: expect.any(String) }));
    expect(mocks.complete).not.toHaveBeenCalled();
  });

  it("collects positive LAB liability with the full quote-bound V2 amount, never the V1 payment route", async () => {
    const labId = "00000000-0000-4000-8000-000000000011";
    const episodeId = "00000000-0000-4000-8000-000000000012";
    mocks.discover.mockResolvedValue({ ...discovery("PENDING", 10), purpose: "LAB_TEST", sourceType: "LAB_TEST", sourceId: labId, careEpisodeId: episodeId, requestedAmount: 120, paidAmount: 110 });
    mocks.quote.mockResolvedValue({
      quoteId: "lab-positive-quote", paymentRequestId: requestId, patientId, careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: episodeId,
      currency: "VND", lines: [{ sourceId: labId, sourceType: "LAB_TEST", priceCode: "CBC", catalogueVersion: "catalogue-1",
        policyVersion: "policy-1", description: "Công thức máu", quantity: "1.00", unitPrice: "100.00", grossAmount: "100.00",
        supportRate: "0.20", supportAmount: "20.00", patientPayable: "80.00" }],
      grossAmount: "100.00", supportAmount: "20.00", patientPayable: "80.00", issuedAt: "2027-10-26T10:00:00Z", expiresAt: "2027-10-26T10:15:00Z",
    });
    mocks.labPayment.mockResolvedValue({ ...payment(), amount: 80, paymentMethod: "TRANSFER" });
    render(<PaymentRequestLookupForm quoteEnabled zeroLiabilityEnabled />);

    fireEvent.change(screen.getByRole("combobox"), { target: { value: "LAB_TEST" } });
    fireEvent.change(screen.getByLabelText("Mã xét nghiệm", { exact: false }), { target: { value: labId } });
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: patientId } });
    fireEvent.change(screen.getByLabelText("Mã đợt điều trị", { exact: false }), { target: { value: episodeId } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu khoản phí" }));
    await screen.findByRole("region", { name: "Báo giá ngoại trú" });
    fireEvent.change(await screen.findByLabelText("Hình thức thanh toán", { exact: false }), { target: { value: "BANK_TRANSFER" } });
    const confirm = screen.getByRole("button", { name: "Xác nhận thu theo báo giá" });
    await waitFor(() => expect(confirm).toBeEnabled());
    fireEvent.click(confirm);

    expect(await screen.findByText(/thanh toán thành công/i)).toBeVisible();
    expect(mocks.labPayment).toHaveBeenCalledWith(requestId, {
      quoteId: "lab-positive-quote", amount: "80.00", currency: "VND", paymentMethod: "BANK_TRANSFER",
    }, { idempotencyKey: expect.any(String), correlationId: expect.any(String) });
    expect(mocks.complete).not.toHaveBeenCalled();
  });

  it("replays the exact persisted quote-bound LAB command after an uncertain outcome and remount", async () => {
    const labId = "00000000-0000-4000-8000-000000000011";
    const episodeId = "00000000-0000-4000-8000-000000000012";
    mocks.discover.mockResolvedValue({ ...discovery("PENDING", 80), purpose: "LAB_TEST", sourceType: "LAB_TEST", sourceId: labId, careEpisodeId: episodeId, requestedAmount: 80, paidAmount: 0 });
    const labQuote = {
      quoteId: "lab-retry-quote", paymentRequestId: requestId, patientId, careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: episodeId,
      currency: "VND", lines: [{ sourceId: labId, sourceType: "LAB_TEST", priceCode: "CBC", catalogueVersion: "catalogue-1",
        policyVersion: "policy-1", description: "Công thức máu", quantity: "1.00", unitPrice: "80.00", grossAmount: "80.00",
        supportRate: "0.00", supportAmount: "0.00", patientPayable: "80.00" }],
      grossAmount: "80.00", supportAmount: "0.00", patientPayable: "80.00", issuedAt: "2027-10-26T10:00:00Z", expiresAt: "2027-10-26T10:15:00Z",
    };
    mocks.quote.mockResolvedValue(labQuote);
    mocks.labPayment.mockRejectedValueOnce(new ApiRequestError("Outcome unknown", 503, "BILLING_UNAVAILABLE"))
      .mockResolvedValueOnce(payment());
    const first = render(<PaymentRequestLookupForm quoteEnabled zeroLiabilityEnabled />);
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "LAB_TEST" } });
    fireEvent.change(screen.getByLabelText("Mã xét nghiệm", { exact: false }), { target: { value: labId } });
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: patientId } });
    fireEvent.change(screen.getByLabelText("Mã đợt điều trị", { exact: false }), { target: { value: episodeId } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu khoản phí" }));
    await screen.findByRole("region", { name: "Báo giá ngoại trú" });
    const confirm = screen.getByRole("button", { name: "Xác nhận thu theo báo giá" });
    await waitFor(() => expect(confirm).toBeEnabled());
    fireEvent.click(confirm);
    expect(await screen.findByRole("alert")).toHaveTextContent("Outcome unknown");
    const firstBody = mocks.labPayment.mock.calls[0][1];
    const firstOptions = mocks.labPayment.mock.calls[0][2];
    first.unmount();

    render(<PaymentRequestLookupForm quoteEnabled zeroLiabilityEnabled />);
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "LAB_TEST" } });
    fireEvent.change(screen.getByLabelText("Mã xét nghiệm", { exact: false }), { target: { value: labId } });
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: patientId } });
    fireEvent.change(screen.getByLabelText("Mã đợt điều trị", { exact: false }), { target: { value: episodeId } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu khoản phí" }));
    await screen.findByRole("region", { name: "Báo giá ngoại trú" });
    const retry = await screen.findByRole("button", { name: "Gửi lại cùng yêu cầu thu" });
    await waitFor(() => expect(retry).toBeEnabled());
    fireEvent.click(retry);

    expect(await screen.findByText(/thanh toán thành công/i)).toBeVisible();
    expect(mocks.labPayment).toHaveBeenCalledTimes(2);
    expect(mocks.labPayment.mock.calls[1]).toEqual([requestId, firstBody, firstOptions]);
    expect(firstBody).toEqual({ quoteId: "lab-retry-quote", amount: "80.00", currency: "VND", paymentMethod: "CASH" });
    expect(mocks.complete).not.toHaveBeenCalled();
  });
});
