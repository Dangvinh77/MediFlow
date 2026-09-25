/**
 * Pharmacy wire contracts mirrored from the current backend DTO records.
 * UUID, LocalDate and Instant values cross the JSON boundary as strings;
 * BigDecimal values cross as numbers and must not be recalculated as source of truth.
 */
export type PrescriptionStatus =
  | "ACTIVE"
  | "FULFILLED"
  | "CANCELLED"
  | "EXPIRED"
  | "DISPENSE_FAILED";

export type DispenseStatus =
  | "PENDING"
  | "DISPENSED"
  | "FAILED"
  | "CANCELLED"
  | "EXPIRED";

export interface DrugDTO {
  drugId: string;
  drugName: string;
  activeIngredient: string | null;
  unit: string;
  price: number;
  stockQuantity: number;
  expiryDate: string;
  manufacturer: string | null;
  lowStockThreshold: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDrugRequest {
  drugName: string;
  activeIngredient?: string | null;
  unit: string;
  price: number;
  stockQuantity: number;
  expiryDate: string;
  manufacturer?: string | null;
  lowStockThreshold?: number | null;
}

export interface AdjustStockRequest {
  quantity: number;
  reason?: string | null;
}

export interface PrescriptionLineRequest {
  drugId: string;
  quantity: number;
  dosage?: string | null;
}

export interface CreatePrescriptionRequest {
  recordId: string;
  patientId: string;
  doctorId: string;
  departmentId: string;
  prescribedDate: string;
  lines: PrescriptionLineRequest[];
}

// Price fields are intentionally absent from prescription requests: the backend
// snapshots the official drug price and calculates every monetary total.

export interface PrescriptionLineDTO {
  lineId: string;
  drugId: string;
  drugName: string | null;
  quantity: number;
  unitPrice: number;
  dosage: string | null;
  lineTotal: number;
}

export interface PrescriptionDTO {
  prescriptionId: string;
  recordId: string;
  patientId: string;
  doctorId: string;
  departmentId: string;
  prescribedDate: string;
  totalAmount: number;
  lines: PrescriptionLineDTO[];
  status: PrescriptionStatus;
  dispenseStatus: DispenseStatus;
  paymentConfirmed: boolean;
  cancelledAt: string | null;
  cancelledBy: string | null;
  cancellationReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CancelPrescriptionRequest {
  reason: string;
}

export interface CancelPrescriptionResult {
  prescriptionId: string;
  status: PrescriptionStatus;
  releasedReservations: number;
  cancelledAt: string;
}

export interface DispenseDTO {
  dispenseId: string;
  prescriptionId: string;
  status: DispenseStatus;
  dispensedAt: string | null;
  dispensedBy: string | null;
  failureReason: string | null;
}

export interface OutboxReplayResult {
  eventId: string;
  replayed: boolean;
}
