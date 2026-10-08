import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { NotificationDTO, SendNotificationRequest } from "./types";

export const notificationApi = {
  getById: (notificationId: string) => api.get<NotificationDTO>(`/v1/notifications/${encodeURIComponent(notificationId)}`),
  byPatient: (patientId: string, page = 0, size = 20) =>
    api.get<PageResult<NotificationDTO>>(
      `/v1/notifications/patient/${encodeURIComponent(patientId)}?page=${page}&size=${size}`,
    ),
  send: (body: SendNotificationRequest) => api.post<NotificationDTO>("/v1/notifications/send", body),
};
