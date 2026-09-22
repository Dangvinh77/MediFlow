import type { StatusTone } from "@/components/ui/StatusBadge";
import type { AppointmentStatus } from "./types";

export const appointmentStatusPresentation: Record<
  AppointmentStatus,
  { label: string; tone: StatusTone }
> = {
  PENDING: { label: "Chờ tiếp nhận", tone: "warning" },
  ARRIVED: { label: "Đã đến", tone: "info" },
  CANCELLED: { label: "Đã hủy", tone: "neutral" },
};
