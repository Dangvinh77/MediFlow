import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { NotificationDTO } from "./types";

export const notificationApi = {
  byPatient: (patientId: string, page = 0, size = 20) =>
    api.get<PageResult<NotificationDTO>>(
      `/v1/notifications/patient/${encodeURIComponent(patientId)}?page=${page}&size=${size}`,
    ),
};
