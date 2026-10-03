import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  AppointmentDTO,
  ChangeAppointmentStatusRequest,
  CreateAppointmentRequest,
  UpdateAppointmentRequest,
} from "./types";

export interface AppointmentSearchParams {
  departmentId?: string;
  appointmentDate?: string;
  page?: number;
  size?: number;
}

export const appointmentApi = {
  getById: (appointmentId: string) =>
    api.get<AppointmentDTO>(
      `/v1/appointments/${encodeURIComponent(appointmentId)}`,
    ),
  search: (params: AppointmentSearchParams = {}) => {
    const query = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
    });
    if (params.departmentId) query.set("departmentId", params.departmentId);
    if (params.appointmentDate) query.set("appointmentDate", params.appointmentDate);
    return api.get<PageResult<AppointmentDTO>>(`/v1/appointments?${query}`);
  },
  create: (body: CreateAppointmentRequest) =>
    api.post<AppointmentDTO>("/v1/appointments", body),
  update: (appointmentId: string, body: UpdateAppointmentRequest) =>
    api.put<AppointmentDTO>(
      `/v1/appointments/${encodeURIComponent(appointmentId)}`,
      body,
    ),
  changeStatus: (
    appointmentId: string,
    body: ChangeAppointmentStatusRequest,
  ) =>
    api.put<AppointmentDTO>(
      `/v1/appointments/${encodeURIComponent(appointmentId)}/status`,
      body,
    ),
};
