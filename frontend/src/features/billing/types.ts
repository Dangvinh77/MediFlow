export type FeeType = "EXAM" | "LAB" | "DRUG" | "SERVICE";

export type PaymentMethod = "CASH" | "TRANSFER" | "INSURANCE";

export type SagaStatus =
  | "NONE"
  | "AWAITING_PAYMENT"
  | "PAID"
  | "AWAITING_DISPENSE"
  | "COMPLETED"
  | "REFUNDED";

export interface FeeDTO {
  feeId: string;
  feeType: FeeType;
  departmentId: string;
  incurredDate: string;
  amount: number;
  isPaid: boolean;
}

export interface InvoiceDTO {
  invoiceId: string;
  patientId: string;
  createdDate: string;
  totalAmount: number;
  isPaid: boolean;
  paymentMethod: PaymentMethod | null;
  prescriptionId: string | null;
  sagaStatus: SagaStatus;
  paidAt: string | null;
  fees: FeeDTO[];
}

export interface CreateInvoiceRequest { patientId: string; createdDate: string }
export interface PayInvoiceRequest { paymentMethod: PaymentMethod }
export interface PaymentResultDTO {
  invoiceId: string;
  success: boolean;
  totalAmount: number;
  paymentMethod: PaymentMethod;
  paidAt: string;
  sagaStatus: SagaStatus;
}

/** Hình thức thanh toán hợp lệ cho một khoản phí đã chọn (ledger V1) — backend từ chối INSURANCE. */
export type LedgerPaymentMethod = "CASH" | "TRANSFER";

export type PaymentClassification = "SERVICE_PAYMENT" | "ADMISSION_DEPOSIT" | "SETTLEMENT_PAYMENT";

export type PaymentTransactionStatus = "PENDING" | "COMPLETED" | "FAILED";

export interface CompleteLedgerPaymentRequest {
  idempotencyKey: string;
  amount: number;
  currency: string;
  paymentMethod: LedgerPaymentMethod;
  providerReference: string | null;
}

export interface LedgerPaymentDTO {
  transactionId: string;
  accountId: string;
  paymentRequestId: string;
  classification: PaymentClassification;
  status: PaymentTransactionStatus;
  amount: number;
  currency: string;
  paymentMethod: string;
  completedAt: string | null;
}

export type PaymentRequestSourcePurpose = "EXAM" | "LAB_TEST" | "PRESCRIPTION";

export interface PaymentRequestSourceQuery {
  purpose: PaymentRequestSourcePurpose;
  sourceId: string;
  patientId: string;
  careEpisodeId: string;
}

/** Exact Billing response for GET /payment-requests/by-source. Amounts and state come from Billing. */
export interface PaymentRequestDiscoveryDTO {
  paymentRequestId: string;
  invoiceId: string;
  accountId: string;
  patientId: string;
  departmentId: string;
  purpose: PaymentRequestSourcePurpose;
  sourceType: string;
  sourceId: string;
  careEpisodeId: string;
  careEpisodeType: string;
  appointmentId: string | null;
  recordId: string | null;
  prescriptionId: string | null;
  labTestIds: string[];
  requestedAmount: number;
  paidAmount: number;
  remainingAmount: number;
  currency: string;
  status: "PENDING" | "PARTIALLY_PAID" | "PAID" | "EXPIRED" | "CANCELLED";
  accountStatus: "OPEN" | "CHARGE_CLOSED" | "SETTLEMENT_PENDING" | "SETTLED" | "CLOSED";
  expiresAt: string | null;
  observedAt: string;
}

export interface RefundDueDTO {
  originalTransactionId: string;
  amount: number;
  currency: string;
}

export interface PrescriptionAdjustmentDTO {
  prescriptionId: string;
  eventType: string;
  status: string;
  rejectionCode: string | null;
  refundsDue: RefundDueDTO[];
}

export interface RefundLedgerPaymentRequest {
  idempotencyKey: string;
  amount: number;
  reason: string;
  paymentMethod: LedgerPaymentMethod;
}

export interface LedgerRefundDTO {
  refundTransactionId: string;
  originalTransactionId: string;
  accountId: string;
  paymentRequestId: string | null;
  classification: PaymentClassification;
  status: PaymentTransactionStatus;
  amount: number;
  currency: string;
  paymentMethod: string;
  completedAt: string;
}

export interface RevenueByDeptDTO {
  departmentId: string;
  totalRevenue: number;
  invoiceCount: number;
}

export interface OutboxReplayResult {
  eventId: string;
  replayed: boolean;
}
