import { getSessionKey } from "@/lib/session";
import { ApiRequestError } from "@/lib/api";
import { medicalRecordApi } from "./api";
import type { ClinicalLabOrderDTO } from "./types";

let session: string | null = null;
const orders = new Map<string, ClinicalLabOrderDTO>();
const unresolved = new Set<string>();
function currentOrders() {
  const current = getSessionKey();
  if (session !== current) { orders.clear(); unresolved.clear(); session = current; }
  return orders;
}
export function pendingLabOrder(recordId: string) { return currentOrders().get(recordId) ?? null; }
export function rememberLabOrder(order: ClinicalLabOrderDTO) { currentOrders().set(order.recordId, order); }
export function forgetLabOrder(recordId: string) { currentOrders().delete(recordId); }
export function hasUnresolvedLabOrder(recordId: string) { currentOrders(); return unresolved.has(recordId); }
export async function createPendingLabOrder(recordId: string, request: Parameters<typeof medicalRecordApi.createLabOrder>[1]) {
  currentOrders();
  if (unresolved.has(recordId) || orders.has(recordId)) throw new Error("Chỉ định đang chờ đối soát. Không lập lại.");
  const ownerSession = session;
  unresolved.add(recordId);
  try {
    const order = await medicalRecordApi.createLabOrder(recordId, request);
    if (order.recordId !== recordId || !order.sourceOrderId) throw new Error("Chỉ định trả về không khớp hồ sơ. Cần đối soát trước khi tiếp tục.");
    if (getSessionKey() === ownerSession) { rememberLabOrder(order); unresolved.delete(recordId); }
    return order;
  } catch (error) {
    if (getSessionKey() === ownerSession && error instanceof ApiRequestError && error.status >= 400 && error.status < 500 && error.status !== 408) unresolved.delete(recordId);
    throw error;
  }
}
