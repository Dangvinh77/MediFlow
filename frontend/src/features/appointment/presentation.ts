import type { StatusTone } from "@/components/ui/StatusBadge";
import type { AppointmentStatus } from "./types";

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
