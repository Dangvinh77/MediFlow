import type { StatusTone } from "@/components/ui/StatusBadge";
import type { NotificationChannel, NotificationStatus } from "./types";

export const channelLabels: Record<NotificationChannel, string> = {
  EMAIL: "Email", SMS: "SMS", IN_APP: "Trong ứng dụng",
};
export const statusPresentation: Record<NotificationStatus, { label: string; tone: StatusTone }> = {
  PENDING: { label: "Chờ gửi", tone: "warning" },
  SENT: { label: "Đã gửi", tone: "success" },
  FAILED: { label: "Gửi thất bại", tone: "danger" },
};
