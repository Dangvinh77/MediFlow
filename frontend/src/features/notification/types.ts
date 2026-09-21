export type NotificationChannel = "EMAIL" | "SMS" | "IN_APP";

export type NotificationStatus = "PENDING" | "SENT" | "FAILED";

export interface NotificationDTO {
  notificationId: string;
  patientId: string;
  title: string;
  content: string;
  channel: NotificationChannel;
  status: NotificationStatus;
  failureReason: string | null;
  createdAt: string;
  sentAt: string | null;
}
