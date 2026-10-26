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

// Scheduled outpatient payment V2 DTOs. Money remains a decimal string on this wire.
export interface RegistrationPaymentRequestDTO {
  paymentRequestId: string;
  invoiceId: string;
  accountId: string;
  registrationId: string;
  reservationId: string;
  reservationRevision: number;
  patientId: string;
  departmentId: string;
  sourceType: "EXAM";
  sourceId: string;
  careEpisodeType: "OUTPATIENT_VISIT";
  careEpisodeId: string;
  requestedAmount: string;
  paidAmount: string;
  remainingAmount: string;
  currency: "VND";
  status: "PENDING" | "PARTIALLY_PAID" | "PAID" | "EXPIRED" | "CANCELLED";
  expiresAt: string | null;
  observedAt: string;
}

export interface ScheduledPaymentCommand {
  reservationId: string;
  expectedReservationRevision: number;
  quoteId: string | null;
  amount: string;
  currency: "VND";
  paymentMethod: "CASH" | "BANK_TRANSFER";
  providerReference: string | null;
}

export type ScheduledPaymentBasis = "PATIENT_PAYMENT" | "ZERO_LIABILITY";

export interface ZeroPayableEligibilityRequest {
  reservationId: string;
  expectedReservationRevision: number;
  quoteId: string;
}

export interface LabZeroPayableEligibilityRequest {
  quoteId: string;
}

export interface LabPaymentCommand {
  quoteId: string;
  amount: string;
  currency: string;
  paymentMethod: "CASH" | "BANK_TRANSFER";
}

export interface LabZeroPayableEligibilityDTO {
  eligibilityOperationId: string;
  paymentRequestId: string;
  quoteId: string;
  basis: "ZERO_LIABILITY";
  clearanceId: string;
  completedAt: string;
}

export interface OutpatientQuoteLineDTO {
  sourceId: string;
  sourceType: string;
  priceCode: string;
  catalogueVersion: string;
  policyVersion: string;
  description: string;
  quantity: string;
  unitPrice: string;
  grossAmount: string;
  supportRate: string;
  supportAmount: string;
  patientPayable: string;
}

export interface OutpatientQuoteDTO {
  quoteId: string;
  paymentRequestId: string;
  patientId: string;
  careEpisodeType: string;
  careEpisodeId: string;
  currency: string;
  lines: OutpatientQuoteLineDTO[];
  grossAmount: string;
  supportAmount: string;
  patientPayable: string;
  issuedAt: string;
  expiresAt: string;
}

export interface ScheduledPaymentOperation {
  paymentOperationId: string;
  paymentRequestId: string;
  registrationId: string;
  reservationId: string;
  status: "PREPARING" | "COMPLETED" | "CLOSED_NOT_PAID";
  basis: ScheduledPaymentBasis;
  quoteId: string | null;
  transactionId: string | null;
  amount: string;
  currency: "VND";
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export interface LedgerPaymentV2DTO {
  transactionId: string;
  accountId: string;
  paymentRequestId: string;
  classification: "SERVICE_PAYMENT";
  status: "COMPLETED";
  amount: string;
  currency: "VND";
  paymentMethod: "CASH" | "BANK_TRANSFER";
  completedAt: string;
}

export interface ScheduledPaymentResult {
  operation: ScheduledPaymentOperation;
  payment: LedgerPaymentV2DTO | null;
  clearanceId: string | null;
}

export interface OutboxReplayResult {
  eventId: string;
  replayed: boolean;
}

/** Selection only; payment details must be reloaded from the by-registration endpoint. */
export interface ScheduledPaymentQueueItem {
  registrationId: string;
  patientId: string;
  departmentId: string;
  createdAt: string;
  expiresAt: string;
}
