import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { AppointmentDTO } from "./types";

export const appointmentApi = {
  search: (page = 0, size = 20) =>
    api.get<PageResult<AppointmentDTO>>(`/v1/appointments?page=${page}&size=${size}`),
};
