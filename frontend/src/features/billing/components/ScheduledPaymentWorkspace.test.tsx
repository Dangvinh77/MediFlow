import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { billingApi } from "../api";
import { ScheduledPaymentWorkspace } from "./ScheduledPaymentWorkspace";

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  pay: vi.fn(),
  quote: vi.fn(),
  zero: vi.fn(),
  operation: vi.fn(),
  queue: vi.fn(),
}));
vi.mock("../api", () => ({ billingApi: {
  scheduledPaymentRequestByRegistration: mocks.request,
  outpatientQuote: mocks.quote,
  payScheduledRequest: mocks.pay,
  confirmScheduledZeroLiability: mocks.zero,
  scheduledPaymentOperation: mocks.operation,
  scheduledPaymentQueue: mocks.queue,
} }));

const paymentRequest = {
  paymentRequestId: "request-id", invoiceId: "invoice-id", accountId: "account-id",
  registrationId: "registration-id", reservationId: "reservation-id", reservationRevision: 4, patientId: "patient-id",
  departmentId: "department-id", sourceType: "EXAM" as const, sourceId: "registration-id",
  careEpisodeType: "OUTPATIENT_VISIT" as const, careEpisodeId: "registration-id",
  requestedAmount: "120000.00", paidAmount: "0.00", remainingAmount: "120000.00",
  currency: "VND" as const, status: "PENDING" as const, expiresAt: "2026-10-26T04:15:00Z",
  observedAt: "2026-10-26T04:00:00Z",
};

function result(status: "PREPARING" | "COMPLETED") {
  const completed = status === "COMPLETED";
  return {
    operation: {
      paymentOperationId: "operation-id", paymentRequestId: "request-id", registrationId: "registration-id",
      reservationId: "reservation-id", status, transactionId: completed ? "transaction-id" : null,
      basis: "PATIENT_PAYMENT" as const, quoteId: null,
      amount: "120000.00", currency: "VND" as const,
      createdAt: "2026-10-26T04:00:00Z", updatedAt: "2026-10-26T04:00:05Z",
      completedAt: completed ? "2026-10-26T04:00:05Z" : null,
    },
    payment: completed ? {
      transactionId: "transaction-id", accountId: "account-id", paymentRequestId: "request-id",
      classification: "SERVICE_PAYMENT" as const, status: "COMPLETED" as const, amount: "120000.00",
      currency: "VND" as const, paymentMethod: "CASH" as const, completedAt: "2026-10-26T04:00:05Z",
    } : null,
    clearanceId: completed ? "clearance-id" : null,
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

const outpatientQuote = {
  quoteId: "quote-id", paymentRequestId: "request-id", patientId: "patient-id",
  careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: "registration-id", currency: "VND",
  lines: [{ sourceId: "registration-id", sourceType: "EXAM", priceCode: "CONSULT", catalogueVersion: "catalogue-1",
    policyVersion: "policy-1", description: "Khám chuyên khoa", quantity: "1.00", unitPrice: "120000.00",
    grossAmount: "120000.00", supportRate: "0.00", supportAmount: "0.00", patientPayable: "120000.00" }],
  grossAmount: "120000.00", supportAmount: "0.00", patientPayable: "120000.00",
  issuedAt: "2027-10-26T04:00:00Z", expiresAt: "2027-10-26T04:15:00Z",
};

describe("scheduled outpatient cashier workflow", () => {
  beforeEach(() => { vi.resetAllMocks(); sessionStorage.clear(); mocks.queue.mockResolvedValue({ content: [], number: 0, totalPages: 0 }); mocks.request.mockResolvedValue(paymentRequest); mocks.quote.mockResolvedValue(outpatientQuote); });

  it("removes a completed payment from the waiting queue even when its loaded snapshot is stale", async () => {
    const user = userEvent.setup();
    mocks.queue.mockResolvedValue({ content: [{ registrationId: "registration-id", patientId: "patient-id", departmentId: "department-id", createdAt: "2026-10-26T04:00:00Z", expiresAt: "2026-10-26T04:15:00Z" }], number: 0, totalPages: 1 });
    mocks.pay.mockResolvedValue(result("COMPLETED"));
    render(<ScheduledPaymentWorkspace enabled />);
    await user.click(await screen.findByRole("button", { name: "Tải khoản chờ thu" }));
    await user.click(await screen.findByRole("button", { name: "Chọn khoản thu" }));
    await user.click(await screen.findByRole("button", { name: "Thu phí lịch khám" }));
    await screen.findByText(/Billing đã xác nhận khoản thu/);
    expect(screen.queryByRole("button", { name: "Chọn khoản thu" })).not.toBeInTheDocument();
    await user.click(await screen.findByRole("button", { name: "Tải khoản chờ thu" }));
    expect(screen.queryByRole("button", { name: "Chọn khoản thu" })).not.toBeInTheDocument();
  });

  it("keeps queue selection lookup failures visible outside the collapsed technical form and retries the same registration", async () => {
    const user = userEvent.setup();
    mocks.queue.mockResolvedValue({ content: [{ registrationId: "registration-id", patientId: "patient-id", departmentId: "department-id", createdAt: "2026-10-26T04:00:00Z", expiresAt: "2026-10-26T04:15:00Z" }], number: 0, totalPages: 1 });
    mocks.request.mockRejectedValueOnce(new ApiRequestError("Chưa đọc được phiếu hiện hành", 503, "UNAVAILABLE", [], "queue-retry")).mockResolvedValue(paymentRequest);
    render(<ScheduledPaymentWorkspace enabled />);
    await user.click(await screen.findByRole("button", { name: "Tải khoản chờ thu" }));
    await user.click(await screen.findByRole("button", { name: "Chọn khoản thu" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("queue-retry");
    expect(screen.getByLabelText("Mã phiếu đăng ký")).not.toBeVisible();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("Còn phải thu");
    expect(mocks.request.mock.calls.map(call => call[0])).toEqual(["registration-id", "registration-id"]);
  });

  it("retains the same idempotency key after an uncertain result and waits for Clinical confirmation", async () => {
    const user = userEvent.setup();
    mocks.pay.mockRejectedValueOnce(new TypeError("Connection lost")).mockResolvedValueOnce(result("PREPARING"));
    mocks.operation.mockResolvedValue(result("COMPLETED"));
    render(<ScheduledPaymentWorkspace enabled />);

    await user.type(screen.getByLabelText("Mã phiếu đăng ký"), "registration-id");
    await user.click(screen.getByRole("button", { name: "Tra cứu khoản thu" }));
    expect(await screen.findByText("120.000 VND")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thu phí lịch khám" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Chưa xác định");

    await user.click(screen.getByRole("button", { name: "Gửi lại cùng yêu cầu" }));
    await screen.findByText(/Đang chờ Billing hoàn tất giao dịch/);
    expect(mocks.pay).toHaveBeenCalledTimes(2);
    expect(mocks.pay.mock.calls[0][2]).toEqual(mocks.pay.mock.calls[1][2]);
    expect(mocks.pay.mock.calls[0][1]).toMatchObject({ amount: "120000.00", currency: "VND", reservationId: "reservation-id", expectedReservationRevision: 4 });

    await user.click(screen.getByRole("button", { name: "Kiểm tra trạng thái giao dịch" }));
    await waitFor(() => expect(screen.getByText("Billing đã xác nhận khoản thu hoặc nghĩa vụ của yêu cầu này.")).toBeInTheDocument());
    expect(screen.queryByText("Còn phải thu")).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Khoản đã xử lý" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cập nhật số dư" })).toBeEnabled();
    expect(screen.queryByText(/Số thứ tự/)).not.toBeInTheDocument();
    expect(mocks.operation).toHaveBeenCalledWith("operation-id");
    expect(billingApi).toBeDefined();
  });

  it("keeps the page unavailable without calling the V2 API while the UI gate is off", () => {
    render(<ScheduledPaymentWorkspace enabled={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("chưa được mở");
    expect(mocks.request).not.toHaveBeenCalled();
    expect(mocks.pay).not.toHaveBeenCalled();
  });

  it("keeps a partially paid request payable for the exact remaining balance", async () => {
    const user = userEvent.setup();
    mocks.request.mockResolvedValue({ ...paymentRequest, status: "PARTIALLY_PAID", paidAmount: "20000.00", remainingAmount: "100000.00" });
    mocks.pay.mockResolvedValue(result("PREPARING"));
    render(<ScheduledPaymentWorkspace enabled />);
    await user.type(screen.getByLabelText("Mã phiếu đăng ký"), "registration-id");
    await user.click(screen.getByRole("button", { name: "Tra cứu khoản thu" }));
    expect(await screen.findByText("100.000 VND")).toBeInTheDocument();
    expect(screen.queryByText("Đã thu, đang chờ xác nhận lịch khám.")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thu phí lịch khám" }));
    await waitFor(() => expect(mocks.pay).toHaveBeenCalledTimes(1));
    expect(mocks.pay.mock.calls[0][1]).toMatchObject({ amount: "100000.00", reservationId: "reservation-id", expectedReservationRevision: 4 });
  });

  it("clears the previous request and blocks its payment while a different registration is loading", async () => {
    const user = userEvent.setup();
    const next = deferred<typeof paymentRequest>();
    const payment = deferred<ReturnType<typeof result>>();
    const nextRequest = { ...paymentRequest, paymentRequestId: "request-b", registrationId: "registration-b", reservationId: "reservation-b", patientId: "patient-b" };
    mocks.request.mockResolvedValueOnce(paymentRequest).mockReturnValueOnce(next.promise);
    mocks.pay.mockReturnValue(payment.promise);
    render(<ScheduledPaymentWorkspace enabled />);

    const registration = screen.getByLabelText("Mã phiếu đăng ký");
    await user.type(registration, "registration-a");
    await user.click(screen.getByRole("button", { name: "Tra cứu khoản thu" }));
    expect(await screen.findByText("Còn phải thu")).toBeInTheDocument();
    await user.clear(registration);
    await user.type(registration, "registration-b");
    await user.click(screen.getByRole("button", { name: "Tra cứu khoản thu" }));
    await waitFor(() => expect(mocks.request).toHaveBeenCalledTimes(2));

    expect(screen.queryByText("request-id")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Thu phí lịch khám" })).not.toBeInTheDocument();
    const stalePayment = screen.queryByRole("button", { name: "Thu phí lịch khám" });
    if (stalePayment) await user.click(stalePayment);
    expect(mocks.pay).not.toHaveBeenCalled();

    await act(async () => next.resolve(nextRequest));
    expect(await screen.findByText("Còn phải thu")).toBeInTheDocument();
    expect(mocks.request).toHaveBeenLastCalledWith("registration-b");
    await act(async () => payment.resolve(result("COMPLETED")));
    expect(screen.queryByText("Billing đã xác nhận khoản thu hoặc nghĩa vụ của yêu cầu này.")).not.toBeInTheDocument();
  });

  it("rejects a payment callback rendered for the old registration after the input changes", async () => {
    const user = userEvent.setup();
    render(<ScheduledPaymentWorkspace enabled />);
    const registration = screen.getByLabelText("Mã phiếu đăng ký");
    await user.type(registration, "registration-a");
    await user.click(screen.getByRole("button", { name: "Tra cứu khoản thu" }));
    const stalePayButton = await screen.findByRole("button", { name: "Thu phí lịch khám" });

    await act(async () => {
      fireEvent.change(registration, { target: { value: "registration-b" } });
      fireEvent.click(stalePayButton);
      await Promise.resolve();
    });

    expect(mocks.pay).not.toHaveBeenCalled();
    expect(screen.queryByText("request-id")).not.toBeInTheDocument();
  });

  it("shows the simulated support quote and binds its current quote id to positive cash collection", async () => {
    const user = userEvent.setup();
    mocks.pay.mockResolvedValue(result("PREPARING"));
    render(<ScheduledPaymentWorkspace enabled quoteEnabled zeroLiabilityEnabled />);
    await user.type(screen.getByLabelText("Mã phiếu đăng ký"), "registration-id");
    await user.click(screen.getByRole("button", { name: "Tra cứu khoản thu" }));
    expect(await screen.findByRole("region", { name: "Báo giá ngoại trú" })).toHaveTextContent("Hỗ trợ BHYT mô phỏng");
    await user.click(screen.getByRole("button", { name: "Thu phí lịch khám" }));
    await waitFor(() => expect(mocks.pay).toHaveBeenCalledTimes(1));
    expect(mocks.pay.mock.calls[0][1]).toMatchObject({ quoteId: "quote-id", amount: "120000.00" });
    expect(mocks.zero).not.toHaveBeenCalled();
  });

  it("allows the legacy positive path after the explicit quote-not-found response without treating it as free", async () => {
    const user = userEvent.setup();
    mocks.quote.mockRejectedValue(new ApiRequestError("No quote", 404, "BILLING_QUOTE_NOT_FOUND"));
    mocks.pay.mockResolvedValue(result("PREPARING"));
    render(<ScheduledPaymentWorkspace enabled quoteEnabled zeroLiabilityEnabled />);
    await user.type(screen.getByLabelText("Mã phiếu đăng ký"), "registration-id");
    await user.click(screen.getByRole("button", { name: "Tra cứu khoản thu" }));
    expect(await screen.findByText(/Không xem đây là khoản miễn phí/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xác nhận nghĩa vụ 0 đồng" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thu phí lịch khám" }));
    await waitFor(() => expect(mocks.pay).toHaveBeenCalledTimes(1));
    expect(mocks.pay.mock.calls[0][1]).toMatchObject({ quoteId: null, amount: "120000.00" });
  });

  it("fails closed on an unexpected quote service error", async () => {
    const user = userEvent.setup();
    mocks.quote.mockRejectedValue(new ApiRequestError("Billing unavailable", 503, "BILLING_UNAVAILABLE"));
    render(<ScheduledPaymentWorkspace enabled quoteEnabled zeroLiabilityEnabled />);
    await user.type(screen.getByLabelText("Mã phiếu đăng ký"), "registration-id");
    await user.click(screen.getByRole("button", { name: "Tra cứu khoản thu" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Billing unavailable");
    expect(screen.getByRole("button", { name: "Thu phí lịch khám" })).toBeDisabled();
    expect(mocks.pay).not.toHaveBeenCalled();
  });

  it("keeps zero liability separate from cash and retries an uncertain request with the same persistent keys", async () => {
    const user = userEvent.setup();
    const zeroQuote = { ...outpatientQuote, patientPayable: "0.00", supportAmount: "120000.00", lines: [{
      ...outpatientQuote.lines[0], supportRate: "1.00", supportAmount: "120000.00", patientPayable: "0.00",
    }] };
    mocks.quote.mockResolvedValue(zeroQuote);
    const zeroResult = {
      ...result("COMPLETED"), operation: { ...result("COMPLETED").operation, basis: "ZERO_LIABILITY" as const, quoteId: "quote-id", transactionId: null },
      payment: null, clearanceId: "zero-clearance-id",
    };
    mocks.zero.mockRejectedValueOnce(new TypeError("Connection lost")).mockResolvedValueOnce(zeroResult);
    render(<ScheduledPaymentWorkspace enabled quoteEnabled zeroLiabilityEnabled />);
    await user.type(screen.getByLabelText("Mã phiếu đăng ký"), "registration-id");
    await user.click(screen.getByRole("button", { name: "Tra cứu khoản thu" }));
    await screen.findByRole("region", { name: "Báo giá ngoại trú" });
    expect(screen.queryByRole("button", { name: "Thu phí lịch khám" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Xác nhận nghĩa vụ 0 đồng" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Chưa xác định");
    expect(mocks.zero).toHaveBeenCalledTimes(1);
    const firstOptions = mocks.zero.mock.calls[0][2];
    expect(mocks.zero.mock.calls[0][1]).toEqual({ reservationId: "reservation-id", expectedReservationRevision: 4, quoteId: "quote-id" });
    await user.click(screen.getByRole("button", { name: "Gửi lại cùng lệnh xác nhận nghĩa vụ 0" }));
    expect(await screen.findByText(/Đã xác nhận nghĩa vụ 0 đồng/)).toHaveTextContent("Xác nhận đã lưu");
    expect(mocks.zero).toHaveBeenCalledTimes(2);
    expect(mocks.zero.mock.calls[1][2]).toEqual(firstOptions);
    expect(mocks.pay).not.toHaveBeenCalled();
  });

  it("restores a pending operation after the workspace remounts and checks it without another payment", async () => {
    const user = userEvent.setup();
    mocks.pay.mockResolvedValue(result("PREPARING"));
    mocks.operation.mockResolvedValue(result("COMPLETED"));
    const firstRender = render(<ScheduledPaymentWorkspace enabled />);
    await user.type(screen.getByLabelText("Mã phiếu đăng ký"), "registration-id");
    await user.click(screen.getByRole("button", { name: "Tra cứu khoản thu" }));
    await screen.findByText("120.000 VND");
    await user.click(screen.getByRole("button", { name: "Thu phí lịch khám" }));
    expect(await screen.findByText(/Đang chờ Billing hoàn tất giao dịch/)).toBeInTheDocument();
    firstRender.unmount();

    render(<ScheduledPaymentWorkspace enabled />);
    await user.type(screen.getByLabelText("Mã phiếu đăng ký"), "registration-id");
    await user.click(screen.getByRole("button", { name: "Tra cứu khoản thu" }));
    expect(await screen.findByText(/Đang chờ Billing hoàn tất giao dịch/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Kiểm tra trạng thái giao dịch" }));
    expect(await screen.findByText("Billing đã xác nhận khoản thu hoặc nghĩa vụ của yêu cầu này.")).toBeInTheDocument();
    expect(mocks.pay).toHaveBeenCalledTimes(1);
    expect(mocks.operation).toHaveBeenCalledWith("operation-id");
  });
});
