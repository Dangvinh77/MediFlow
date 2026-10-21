import { ApiRequestError } from "@/lib/api";

export class CreationIntentError extends Error {}

interface PendingCreation { key: string; fingerprint: string }

async function digest(value: string) {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(bytes), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** Persist only opaque keys/digests, never the bearer, draft or password. */
export async function prepareCreation(kind: "staff" | "account", session: string | null, identity: object, businessIdentity?: string) {
  const scope = businessIdentity === undefined ? "" : `:${await digest(businessIdentity)}`;
  const storageKey = `organization:pending-${kind}:${await digest(session ?? "")}${scope}`;
  const fingerprint = await digest(JSON.stringify(identity));
  let pending: PendingCreation;
  let isNew = false;
  try {
    const stored = sessionStorage.getItem(storageKey);
    const previous = stored ? JSON.parse(stored) as PendingCreation : null;
    if (previous && (typeof previous.key !== "string" || typeof previous.fingerprint !== "string")) {
      throw new Error("Invalid pending creation");
    }
    if (previous && previous.fingerprint !== fingerprint) {
      throw new CreationIntentError("Lệnh tạo trước chưa được xác nhận. Hãy khôi phục thông tin đã gửi và thử lại để đối soát; không gửi một lệnh tạo mới khi chưa biết kết quả cũ.");
    }
    pending = previous ?? { key: crypto.randomUUID(), fingerprint };
    isNew = previous === null;
    sessionStorage.setItem(storageKey, JSON.stringify(pending));
  } catch (cause) {
    if (cause instanceof CreationIntentError) throw cause;
    throw new CreationIntentError("Không thể lưu khóa đối soát trong phiên trình duyệt. Chưa gửi lệnh tạo; hãy kiểm tra quyền lưu trữ của trình duyệt.");
  }
  function clear() {
    try {
      const stored = sessionStorage.getItem(storageKey);
      if (stored && (JSON.parse(stored) as PendingCreation).key === pending.key) sessionStorage.removeItem(storageKey);
    } catch { /* Keep the receipt rather than risk replacing an unresolved key. */ }
  }
  return {
    key: pending.key,
    cancelUnsent() { if (isNew) clear(); },
    settle(cause?: unknown) {
      // A malformed success also has an unknown outcome. Conflicting keys must
      // remain pending so changing the draft cannot bypass the original command.
      if (cause !== undefined && (!(cause instanceof ApiRequestError)
        || cause.status < 400 || cause.status === 408 || cause.status >= 500
        || cause.code?.endsWith("IDEMPOTENCY_KEY_REUSED"))) return;
      clear();
    },
  };
}
