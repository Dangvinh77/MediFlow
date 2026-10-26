import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearSession, storeSession } from "@/lib/session";
import type { LedgerPaymentDTO, PaymentRequestDiscoveryDTO } from "../types";

const mocks = vi.hoisted(() => ({ discover: vi.fn(), complete: vi.fn(), push: vi.fn(), replace: vi.fn(), back: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: mocks.push, replace: mocks.replace, back: mocks.back }) }));
vi.mock("next/link", () => ({ default: ({ href, children }: { href: string; children: import("react").ReactNode }) => <a href={href}>{children}</a> }));
vi.mock("../api", () => ({ billingApi: { discoverPaymentRequest: mocks.discover, completeLedgerPayment: mocks.complete } }));

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
  });

  afterEach(() => act(() => clearSession()));

  it("lets the cashier leave the screen without looking up a charge", () => {
    render(<PaymentRequestLookupForm />);
    fireEvent.click(screen.getByRole("button", { name: "Quay lại" }));
    expect(mocks.back).toHaveBeenCalledOnce();
    expect(mocks.discover).not.toHaveBeenCalled();
  });

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
});
