import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { CreateInvoiceRequest, InvoiceDTO, PaymentResultDTO, PayInvoiceRequest } from "./types";

export const billingApi = {
  getById: (invoiceId: string) => api.get<InvoiceDTO>(`/v1/billing/invoices/${encodeURIComponent(invoiceId)}`),
  byPatient: (patientId: string, page = 0, size = 20) =>
    api.get<PageResult<InvoiceDTO>>(
      `/v1/billing/patient/${encodeURIComponent(patientId)}?page=${page}&size=${size}`,
    ),
  create: (body: CreateInvoiceRequest) => api.post<InvoiceDTO>("/v1/billing/invoices", body),
  pay: (invoiceId: string, body: PayInvoiceRequest) =>
    api.put<PaymentResultDTO>(`/v1/billing/invoices/${encodeURIComponent(invoiceId)}/pay`, body),
};
