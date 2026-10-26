import type { ScheduledPaymentCommand, ZeroPayableEligibilityRequest } from "./types";

export type ScheduledPaymentIntentKind = "PATIENT_PAYMENT" | "ZERO_LIABILITY";

export interface ScheduledPaymentRecovery {
  kind?: ScheduledPaymentIntentKind;
  command: ScheduledPaymentCommand | ZeroPayableEligibilityRequest;
  paymentOperationId: string | null;
}

async function storageKey(paymentRequestId: string, sessionId: string | null): Promise<string> {
  const value = `${sessionId ?? ""}\u0000${paymentRequestId}`;
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  const token = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
  return `mediflow:scheduled-payment-recovery:${token}`;
}

function isRecovery(value: unknown): value is ScheduledPaymentRecovery {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  const command = record.command;
  if (typeof command !== "object" || command === null) return false;
  const fields = command as Record<string, unknown>;
  const validCommand = record.kind === "ZERO_LIABILITY"
    ? typeof fields.reservationId === "string"
      && Number.isSafeInteger(fields.expectedReservationRevision)
      && typeof fields.quoteId === "string"
    : typeof fields.reservationId === "string"
    && Number.isSafeInteger(fields.expectedReservationRevision)
    && typeof fields.amount === "string"
    && fields.currency === "VND"
    && (fields.quoteId === undefined || fields.quoteId === null || typeof fields.quoteId === "string")
    && (fields.paymentMethod === "CASH" || fields.paymentMethod === "BANK_TRANSFER")
    && (fields.providerReference === null || typeof fields.providerReference === "string")
  return (record.kind === undefined || record.kind === "PATIENT_PAYMENT" || record.kind === "ZERO_LIABILITY")
    && validCommand
    && (record.paymentOperationId === null || typeof record.paymentOperationId === "string");
}

export async function readScheduledPaymentRecovery(paymentRequestId: string, sessionId: string | null): Promise<ScheduledPaymentRecovery | null> {
  try {
    const stored = sessionStorage.getItem(await storageKey(paymentRequestId, sessionId));
    if (!stored) return null;
    const value: unknown = JSON.parse(stored);
    if (!isRecovery(value)) throw new Error("Invalid payment recovery state");
    return value;
  } catch {
    throw new Error("Không thể đọc yêu cầu thu đang chờ trong phiên trình duyệt.");
  }
}

export async function saveScheduledPaymentRecovery(paymentRequestId: string, sessionId: string | null, recovery: ScheduledPaymentRecovery): Promise<void> {
  try {
    sessionStorage.setItem(await storageKey(paymentRequestId, sessionId), JSON.stringify(recovery));
  } catch {
    throw new Error("Chưa gửi giao dịch vì không thể lưu yêu cầu thu đang chờ trong phiên trình duyệt.");
  }
}

export async function clearScheduledPaymentRecovery(paymentRequestId: string, sessionId: string | null): Promise<void> {
  try {
    sessionStorage.removeItem(await storageKey(paymentRequestId, sessionId));
  } catch {
    throw new Error("Không thể xóa trạng thái thu đã hoàn tất khỏi phiên trình duyệt.");
  }
}
