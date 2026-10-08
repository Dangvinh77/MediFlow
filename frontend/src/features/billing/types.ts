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
