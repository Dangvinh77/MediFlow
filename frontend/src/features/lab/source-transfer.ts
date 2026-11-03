import { getSessionKey } from "@/lib/session";
import { ApiRequestError } from "@/lib/api";
import { labApi } from "./api";
import type { CreateLabRequest, LabTestDTO } from "./types";

// Keep the original in-flight result when navigating back. Never replay an
// uncertain write merely because React remounts the transfer component.
let session: string | null = null;
interface Transfer { body: string; pending: Promise<LabTestDTO>; failure?: unknown }
const transfers = new Map<string, Transfer>();
export function transferLabSource(request: CreateLabRequest, retryRejected = false) {
  const current = getSessionKey();
  if (current !== session) { transfers.clear(); session = current; }
  const key = request.sourceOrderId;
  if (!key) throw new Error("Chưa có chỉ định nguồn để chuyển sang Lab.");
  const body = JSON.stringify(request);
  const previous = transfers.get(key);
  if (previous && previous.body !== body) throw new Error("Thông tin chỉ định đã thay đổi. Cần đối soát chỉ định gốc trước khi tiếp tục.");
  const failure = previous?.failure;
  if (retryRejected && failure instanceof ApiRequestError && failure.status >= 400 && failure.status < 500 && failure.status !== 408 && failure.status !== 409) transfers.delete(key);
  let transfer = transfers.get(key);
  if (!transfer) {
    const next: Transfer = { body, pending: Promise.resolve().then(() => labApi.create(request)) };
    next.pending = next.pending.catch(cause => { next.failure = cause; throw cause; });
    transfer = next;
    transfers.set(key, transfer);
  }
  return transfer.pending;
}
