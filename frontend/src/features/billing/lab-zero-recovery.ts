export interface LabZeroRecovery {
  quoteId: string;
}

async function storageKey(paymentRequestId: string, sessionId: string | null): Promise<string> {
  const value = `${sessionId ?? ""}\u0000${paymentRequestId}`;
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  const token = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
  return `mediflow:lab-zero-recovery:${token}`;
}

function isRecovery(value: unknown): value is LabZeroRecovery {
  return typeof value === "object" && value !== null && typeof (value as Record<string, unknown>).quoteId === "string";
}

export async function readLabZeroRecovery(paymentRequestId: string, sessionId: string | null): Promise<LabZeroRecovery | null> {
  try {
    const stored = sessionStorage.getItem(await storageKey(paymentRequestId, sessionId));
    if (!stored) return null;
    const recovery: unknown = JSON.parse(stored);
    if (!isRecovery(recovery)) throw new Error("Invalid Lab zero-liability recovery state");
    return recovery;
  } catch {
    throw new Error("Không thể đọc yêu cầu xác nhận nghĩa vụ 0 đang chờ trong phiên trình duyệt.");
  }
}

export async function saveLabZeroRecovery(paymentRequestId: string, sessionId: string | null, recovery: LabZeroRecovery): Promise<void> {
  try {
    sessionStorage.setItem(await storageKey(paymentRequestId, sessionId), JSON.stringify(recovery));
  } catch {
    throw new Error("Chưa gửi xác nhận vì không thể lưu yêu cầu đang chờ trong phiên trình duyệt.");
  }
}

export async function clearLabZeroRecovery(paymentRequestId: string, sessionId: string | null): Promise<void> {
  try {
    sessionStorage.removeItem(await storageKey(paymentRequestId, sessionId));
  } catch {
    throw new Error("Không thể xóa trạng thái xác nhận đã hoàn tất khỏi phiên trình duyệt.");
  }
}
