import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  CompleteLedgerPaymentRequest,
  CreateInvoiceRequest,
  InvoiceDTO,
  LedgerPaymentDTO,
  LedgerRefundDTO,
  OutboxReplayResult,
  PaymentResultDTO,
  PayInvoiceRequest,
  PrescriptionAdjustmentDTO,
  RefundLedgerPaymentRequest,
  RevenueByDeptDTO,
} from "./types";

export const billingApi = {
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
  getPrescriptionAdjustment: (prescriptionId: string) =>
    api.get<PrescriptionAdjustmentDTO>(
      `/v1/billing/prescription-adjustments/${encodeURIComponent(prescriptionId)}/refunds-due`,
    ),
  refundLedgerPayment: (originalTransactionId: string, body: RefundLedgerPaymentRequest) =>
    api.post<LedgerRefundDTO>(`/v1/billing/transactions/${encodeURIComponent(originalTransactionId)}/refunds`, body),
  revenueByDepartment: (fromDate: string, toDate: string, departmentId?: string) => {
    const params = new URLSearchParams({ fromDate, toDate });
    if (departmentId) params.set("departmentId", departmentId);
    return api.get<RevenueByDeptDTO[]>(`/v1/billing/revenue?${params.toString()}`);
  },
  replayOutboxEvent: (eventId: string) =>
    api.post<OutboxReplayResult>(`/v1/billing/admin/outbox/${encodeURIComponent(eventId)}/replay`),
};
