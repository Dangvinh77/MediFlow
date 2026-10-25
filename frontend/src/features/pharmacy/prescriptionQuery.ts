import { isUuid } from "./utils";

export const prescriptionStatuses: Record<string, string> = {
  ACTIVE: "Còn hiệu lực", FULFILLED: "Đã cấp phát", CANCELLED: "Đã hủy", EXPIRED: "Hết hạn", DISPENSE_FAILED: "Lỗi tồn kho",
};
export function readPrescriptionQuery(raw: string) {
  const params = new URLSearchParams(raw);
  const patientId = params.get("patientId") ?? "";
  const status = params.get("status") ?? "";
  const page = Number(params.get("page") ?? 0);
  return { patientId: isUuid(patientId) ? patientId : "",
    status: Object.hasOwn(prescriptionStatuses, status) ? status : "",
    page: Number.isSafeInteger(page) && page >= 0 && page <= 1_000_000 ? page : 0 };
}
export function buildPrescriptionQuery(query: ReturnType<typeof readPrescriptionQuery>) {
  const params = new URLSearchParams();
  if (query.patientId) params.set("patientId", query.patientId);
  if (query.status) params.set("status", query.status);
  if (query.page) params.set("page", String(query.page));
  return params.toString();
}
