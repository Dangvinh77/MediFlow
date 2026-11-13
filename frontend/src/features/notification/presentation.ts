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


/** Groups a stored raw amount ("36000", "150000.00") the Vietnamese way: 36.000, 150.000, 1.250,5. */
function groupedAmount(integer: string, fraction: string | undefined) {
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  const kept = fraction?.replace(/0+$/, "");
  return kept ? `${grouped},${kept}` : grouped;
}

/**
 * Older stored messages used the English role name and raw amounts ("36000 VND"). Rewrite only
 * those two shapes for display; the message is otherwise preserved.
 */
export function notificationText(value: string) {
  return value
    .replace(/\bnurse\b/gi, "điều dưỡng")
    .replace(/(?<![\d.,])(\d{4,})(?:\.(\d{1,2}))?(?=\s?(?:VND|VNĐ|đ)(?![\p{L}\d]))/gu,
      (_match, integer: string, fraction: string | undefined) => groupedAmount(integer, fraction));
}
