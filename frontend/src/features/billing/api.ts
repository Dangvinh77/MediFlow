import { api, type MutationOptions } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  PrescriptionAdjustmentDTO,
  RefundLedgerPaymentRequest,
  LedgerRefundDTO,
  CompleteLedgerPaymentRequest,
  CreateInvoiceRequest,
  InvoiceDTO,
  LabZeroPayableEligibilityDTO,
  LabZeroPayableEligibilityRequest,
  LedgerPaymentDTO,
  LabPaymentCommand,
  OutboxReplayResult,
  OutpatientQuoteDTO,
  PaymentResultDTO,
  PayInvoiceRequest,
  PaymentRequestDiscoveryDTO,
  PaymentRequestSourceQuery,
  RegistrationPaymentRequestDTO,
  RevenueByDeptDTO,
  ScheduledPaymentCommand,
  ScheduledPaymentQueueItem,
  ScheduledPaymentResult,
  ZeroPayableEligibilityRequest,
} from "./types";

export type ScheduledPaymentCommandOptions = Required<Pick<MutationOptions, "idempotencyKey" | "correlationId">>;

export const billingApi = {
  scheduledPaymentQueue: (patientId: string, page = 0) => {
    const query = new URLSearchParams({ page: String(page), size: "10" });
    if (patientId) query.set("patientId", patientId);
    return api.get<PageResult<ScheduledPaymentQueueItem>>(`/v2/billing/payment-requests?${query}`);
  },
  refundsDue: (prescriptionId: string) => api.get<PrescriptionAdjustmentDTO>(`/v1/billing/prescription-adjustments/${encodeURIComponent(prescriptionId)}/refunds-due`),
  recordRefund: (transactionId: string, body: RefundLedgerPaymentRequest) => api.post<LedgerRefundDTO>(`/v1/billing/transactions/${encodeURIComponent(transactionId)}/refunds`, body),
  getById: (invoiceId: string) => api.get<InvoiceDTO>(`/v1/billing/invoices/${encodeURIComponent(invoiceId)}`),
  byPatient: (patientId: string, page = 0, size = 20) =>
    api.get<PageResult<InvoiceDTO>>(
      `/v1/billing/patient/${encodeURIComponent(patientId)}?page=${page}&size=${size}`,
    ),
  create: (body: CreateInvoiceRequest) => api.post<InvoiceDTO>("/v1/billing/invoices", body),
  pay: (invoiceId: string, body: PayInvoiceRequest) =>
    api.put<PaymentResultDTO>(`/v1/billing/invoices/${encodeURIComponent(invoiceId)}/pay`, body),
  completeLedgerPayment: (requestId: string, body: CompleteLedgerPaymentRequest) =>
    api.post<LedgerPaymentDTO>(`/v1/billing/payment-requests/${encodeURIComponent(requestId)}/payments`, body),
  discoverPaymentRequest: (query: PaymentRequestSourceQuery) => {
    const params = new URLSearchParams({
      purpose: query.purpose,
      sourceId: query.sourceId,
      patientId: query.patientId,
      careEpisodeId: query.careEpisodeId,
    });
    return api.get<PaymentRequestDiscoveryDTO>(`/v1/billing/payment-requests/by-source?${params.toString()}`);
  },
  revenueByDepartment: (fromDate: string, toDate: string, departmentId?: string) => {
    const params = new URLSearchParams({ fromDate, toDate });
    if (departmentId) params.set("departmentId", departmentId);
    return api.get<RevenueByDeptDTO[]>(`/v1/billing/revenue?${params.toString()}`);
  },
  replayOutboxEvent: (eventId: string) =>
    api.post<OutboxReplayResult>(`/v1/billing/admin/outbox/${encodeURIComponent(eventId)}/replay`),
  scheduledPaymentRequestByRegistration: (registrationId: string) =>
    api.get<RegistrationPaymentRequestDTO>(`/v2/billing/payment-requests/by-registration?registrationId=${encodeURIComponent(registrationId)}`),
  outpatientQuote: (paymentRequestId: string) =>
    api.get<OutpatientQuoteDTO>(`/v2/billing/payment-requests/${encodeURIComponent(paymentRequestId)}/quote`),
  payScheduledRequest: (paymentRequestId: string, body: ScheduledPaymentCommand, options: ScheduledPaymentCommandOptions) =>
    api.post<ScheduledPaymentResult>(`/v2/billing/payment-requests/${encodeURIComponent(paymentRequestId)}/payments`, body, options),
  confirmScheduledZeroLiability: (paymentRequestId: string, body: ZeroPayableEligibilityRequest, options: ScheduledPaymentCommandOptions) =>
    api.post<ScheduledPaymentResult>(`/v2/billing/payment-requests/${encodeURIComponent(paymentRequestId)}/zero-payable-eligibility`, body, options),
  confirmLabZeroLiability: (paymentRequestId: string, body: LabZeroPayableEligibilityRequest, options: ScheduledPaymentCommandOptions) =>
    api.post<LabZeroPayableEligibilityDTO>(`/v2/billing/payment-requests/${encodeURIComponent(paymentRequestId)}/lab-zero-payable-eligibility`, body, options),
  createLabPayment: (paymentRequestId: string, body: LabPaymentCommand, options: ScheduledPaymentCommandOptions) =>
    api.post<LedgerPaymentDTO>(`/v2/billing/payment-requests/${encodeURIComponent(paymentRequestId)}/lab-payments`, body, options),
  scheduledPaymentOperation: (operationId: string) =>
    api.get<ScheduledPaymentResult>(`/v2/billing/payment-operations/${encodeURIComponent(operationId)}`),
};
