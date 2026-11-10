import { sessionDateErrors, vietnamToday } from "./session-dates";
import type { ConsultationSessionDTO } from "./types";

export type SessionAvailability = "AVAILABLE" | "FULL" | "NOT_OPEN" | "LOAD_ERROR";

export const sessionAvailabilityLabels: Record<SessionAvailability, string> = {
  AVAILABLE: "Còn chỗ", FULL: "Đã hết chỗ", NOT_OPEN: "Buổi không nhận đăng ký", LOAD_ERROR: "Không tải được lịch",
};

export function sessionAvailability(session: Pick<ConsultationSessionDTO, "status" | "availableCount"> | null, loadError: unknown): SessionAvailability {
  if (loadError) return "LOAD_ERROR";
  if (!session || session.status !== "OPEN") return "NOT_OPEN";
  return session.availableCount < 1 ? "FULL" : "AVAILABLE";
}

export function initialScheduleDate(value?: string): string {
  return value && !Object.keys(sessionDateErrors(value,value)).length ? value : vietnamToday();
}

export function formatScheduleObservedAt(value: string): string {
  const date=new Date(value);
  if (Number.isNaN(date.getTime())) return "Chưa có thời điểm cập nhật";
  const parts=new Intl.DateTimeFormat("vi-VN",{timeZone:"Asia/Ho_Chi_Minh",year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hourCycle:"h23"}).formatToParts(date);
  const fields=Object.fromEntries(parts.map(part=>[part.type,part.value]));
  return `${fields.hour}:${fields.minute} ${fields.day}/${fields.month}/${fields.year} (giờ Việt Nam)`;
}
