import type { LabPaymentCommand } from "./types";

export type LabPaymentRecovery = LabPaymentCommand;

async function storageKey(paymentRequestId: string, sessionId: string | null): Promise<string> {
  const value = `${sessionId ?? ""}\u0000${paymentRequestId}`;
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  const token = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
  return `mediflow:lab-payment-recovery:${token}`;
}

function isRecovery(value: unknown): value is LabPaymentRecovery {
  if (typeof value !== "object" || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.quoteId === "string"
    && typeof record.amount === "string"
    && typeof record.currency === "string"
    && (record.paymentMethod === "CASH" || record.paymentMethod === "BANK_TRANSFER");
}

export async function readLabPaymentRecovery(paymentRequestId: string, sessionId: string | null): Promise<LabPaymentRecovery | null> {
  try {
    const stored = sessionStorage.getItem(await storageKey(paymentRequestId, sessionId));
    if (!stored) return null;
    const recovery: unknown = JSON.parse(stored);
    if (!isRecovery(recovery)) throw new Error("Invalid Lab payment recovery state");
    return recovery;
  } catch {
    throw new Error("Không thể đọc yêu cầu thu LAB đang chờ trong phiên trình duyệt.");
  }
}

export async function saveLabPaymentRecovery(paymentRequestId: string, sessionId: string | null, recovery: LabPaymentRecovery): Promise<void> {
  try {
    sessionStorage.setItem(await storageKey(paymentRequestId, sessionId), JSON.stringify(recovery));
  } catch {
    throw new Error("Chưa gửi thanh toán vì không thể lưu lệnh thu LAB trong phiên trình duyệt.");
  }
}

export async function clearLabPaymentRecovery(paymentRequestId: string, sessionId: string | null): Promise<void> {
  try {
    sessionStorage.removeItem(await storageKey(paymentRequestId, sessionId));
  } catch {
    throw new Error("Không thể xóa trạng thái thu LAB đã hoàn tất khỏi phiên trình duyệt.");
  }
}
