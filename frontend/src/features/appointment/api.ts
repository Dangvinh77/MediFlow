import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { AppointmentDTO } from "./types";

export interface AppointmentSearchParams {
  departmentId?: string;
  appointmentDate?: string;
  page?: number;
  size?: number;
}

export const appointmentApi = {
  search: (params: AppointmentSearchParams = {}) => {
    const query = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
    });
    if (params.departmentId) query.set("departmentId", params.departmentId);
    if (params.appointmentDate) query.set("appointmentDate", params.appointmentDate);
    return api.get<PageResult<AppointmentDTO>>(`/v1/appointments?${query}`);
  },
};
