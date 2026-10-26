import "client-only";
import { ApiRequestError } from "./api";

export class CommandIntentError extends Error {}

interface PendingCommand {
  key: string;
  correlationId: string;
  fingerprint: string;
}

async function digest(value: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(bytes), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** Persists only opaque identifiers and a digest so an uncertain command keeps its key. */
export async function prepareCommandIntent(scope: string, session: string | null, command: unknown) {
  const [sessionDigest, scopeDigest, fingerprint] = await Promise.all([
    digest(session ?? ""),
    digest(scope),
    digest(JSON.stringify(command)),
  ]);
  const storageKey = `mediflow:pending-command:${sessionDigest}:${scopeDigest}`;
  let pending: PendingCommand;
  let isNew = false;

  try {
    const stored = sessionStorage.getItem(storageKey);
    const previous = stored ? JSON.parse(stored) as PendingCommand : null;
    if (previous && (typeof previous.key !== "string" || typeof previous.correlationId !== "string" || typeof previous.fingerprint !== "string")) {
      throw new Error("Invalid pending command receipt");
    }
    if (previous && previous.fingerprint !== fingerprint) {
      throw new CommandIntentError("Lệnh trước chưa được xác nhận. Hãy tải lại trạng thái và dùng đúng nội dung đã gửi; không gửi lệnh mới khi chưa biết kết quả.");
    }
    pending = previous ?? { key: crypto.randomUUID(), correlationId: crypto.randomUUID(), fingerprint };
    isNew = previous === null;
    sessionStorage.setItem(storageKey, JSON.stringify(pending));
  } catch (cause) {
    if (cause instanceof CommandIntentError) throw cause;
    throw new CommandIntentError("Chưa gửi lệnh vì không thể lưu khóa đối soát trong phiên trình duyệt.");
  }

  function clear() {
    try {
      const stored = sessionStorage.getItem(storageKey);
      if (stored && (JSON.parse(stored) as PendingCommand).key === pending.key) sessionStorage.removeItem(storageKey);
    } catch { /* Preserve an unresolved key if its storage state cannot be checked. */ }
  }

  return {
    key: pending.key,
    correlationId: pending.correlationId,
    cancelUnsent() { if (isNew) clear(); },
    settle(cause?: unknown) {
      if (cause !== undefined && (!(cause instanceof ApiRequestError)
        || cause.status < 400 || cause.status === 408 || cause.status >= 500
        || cause.code?.includes("IDEMPOTENCY_CONFLICT") || cause.code?.endsWith("IDEMPOTENCY_KEY_REUSED"))) return;
      clear();
    },
  };
}
