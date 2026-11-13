import type { StatusTone } from "@/components/ui/StatusBadge";
import type { AppointmentDTO, AppointmentStatus, ConsultationSessionStatus, RegistrationStatus } from "./types";

export const appointmentStatusPresentation: Record<
  AppointmentStatus,
  { label: string; tone: StatusTone }
> = {
  PENDING: { label: "Chờ tiếp nhận", tone: "warning" },
  ARRIVED: { label: "Đã đến", tone: "info" },
  AWAITING_PAYMENT: { label: "Chờ thanh toán", tone: "warning" },
  READY_FOR_EXAM: { label: "Sẵn sàng khám", tone: "success" },
  IN_EXAM: { label: "Đang khám", tone: "info" },
  COMPLETED: { label: "Đã hoàn tất", tone: "success" },
  CANCELLED: { label: "Đã hủy", tone: "neutral" },
};

export function getAppointmentStatusPresentation(appointment: Pick<AppointmentDTO, "status" | "careContractVersion" | "checkedInAt">) {
  if (appointment.careContractVersion >= 2 && appointment.status === "READY_FOR_EXAM") {
    return { label: appointment.checkedInAt ? "Đã đến · Chờ khám" : "Đã thanh toán · Chờ đến", tone: "info" as const };
  }
  return appointmentStatusPresentation[appointment.status];
}

export const sessionStatusLabels: Record<ConsultationSessionStatus, string> = {
  OPEN: "Đang nhận đăng ký", STOPPED: "Tạm ngừng nhận", CANCELLED: "Đã hủy buổi",
};
export const registrationStatusLabels: Record<RegistrationStatus, string> = {
  AWAITING_PAYMENT: "Chờ thanh toán", CONFIRMED: "Đã xác nhận", IN_EXAM: "Đang khám",
  COMPLETED: "Đã hoàn tất", EXPIRED: "Hết hạn giữ chỗ", CANCELLED: "Đã hủy", RECONCILIATION_REQUIRED: "Cần đối soát thanh toán",
};

export const registrationStatusTones: Record<RegistrationStatus, StatusTone> = {
  AWAITING_PAYMENT: "warning", CONFIRMED: "info", IN_EXAM: "info",
  COMPLETED: "success", EXPIRED: "neutral", CANCELLED: "neutral", RECONCILIATION_REQUIRED: "danger",
};

export function registrationOutcomeLabel(item: { status: RegistrationStatus; checkedInAt: string | null; stage?: string | null }): string {
  if (item.stage === "ABSENT") return "Vắng mặt";
  if (item.status === "CONFIRMED") return item.checkedInAt ? "Đã đến · Chờ khám" : "Đã thanh toán · Chờ đến";
  return registrationStatusLabels[item.status];
}

/** The reception-counter number, as opposed to the per-room number issued by the department desk. */
export function queueNumberLabel(clinicIntakeEnabled: boolean): string {
  return clinicIntakeEnabled ? "Số quầy" : "Số thứ tự";
}

export function queueNumberText(queueNumber: number | null, clinicIntakeEnabled: boolean): string {
  if (queueNumber === null) return "Chưa có số";
  return clinicIntakeEnabled ? `Số quầy ${queueNumber}` : `Số ${queueNumber}`;
}

export function roomNumberText(roomQueueNumber: number | null | undefined): string | null {
  return roomQueueNumber === null || roomQueueNumber === undefined ? null : `Số phòng ${roomQueueNumber}`;
}
