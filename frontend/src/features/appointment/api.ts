import { api, type MutationOptions } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  AppointmentDTO,
  ChangeAppointmentStatusRequest,
  CreateAppointmentRequest,
  StartExamRequest,
  ConsultationRoomSummary,
  ConsultationSessionDTO,
  ConsultationSessionCreate,
  ConsultationSessionStatusCommand,
  ConsultationSessionUpdate,
  DoctorCalendarDTO,
  FollowUpSourceSummary,
  RegistrationCancellationCommand,
  RegistrationCreate,
  RegistrationDTO,
  RegistrationSummary,
  RevisionCommand,
  SchedulingScopeCommand,
  SchedulingScopeSnapshot,
  UpdateAppointmentRequest,
} from "./types";

export type SchedulingCommandOptions = Required<Pick<MutationOptions, "idempotencyKey" | "correlationId">>;

export interface AppointmentSearchParams {
  departmentId?: string;
  appointmentDate?: string;
  page?: number;
  size?: number;
}

export interface ConsultationSessionSearchParams {
  departmentId: string;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export interface DoctorCalendarQuery {
  weekStart?: string;
  date?: string;
}

export interface OwnRegistrationSearchParams {
  page?: number;
  size?: number;
}

export interface FollowUpSourceSearchParams {
  patientId: string;
  page?: number;
  size?: number;
}

export const appointmentApi = {
  // Commands are exposed only by the server-controlled outpatient UI gate.
  checkIn: (appointmentId: string) =>
    api.put<AppointmentDTO>(
      `/v1/appointments/${encodeURIComponent(appointmentId)}/check-in`,
    ),
  startExam: (appointmentId: string, body: StartExamRequest = { emergencyOverride: null }) =>
    api.put<AppointmentDTO>(
      `/v1/appointments/${encodeURIComponent(appointmentId)}/start-exam`,
      body,
    ),
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
  consultationRooms: (departmentId: string, page = 0, size = 20) => {
    const query = new URLSearchParams({ departmentId, page: String(page), size: String(size) });
    return api.get<PageResult<ConsultationRoomSummary>>(`/v2/org/consultation-rooms?${query}`);
  },
  scheduleManagementScope: (accountId: string) =>
    api.get<SchedulingScopeSnapshot>(`/v2/org/accounts/${encodeURIComponent(accountId)}/schedule-management-scope`),
  replaceScheduleManagementScope: (accountId: string, body: SchedulingScopeCommand, options: SchedulingCommandOptions) =>
    api.put<SchedulingScopeSnapshot>(`/v2/org/accounts/${encodeURIComponent(accountId)}/schedule-management-scope`, body, options),
  listConsultationSessions: (params: ConsultationSessionSearchParams) => {
    const query = new URLSearchParams({ departmentId: params.departmentId });
    if (params.fromDate) query.set("fromDate", params.fromDate);
    if (params.toDate) query.set("toDate", params.toDate);
    query.set("page", String(params.page ?? 0));
    query.set("size", String(params.size ?? 20));
    return api.get<PageResult<ConsultationSessionDTO>>(`/v2/consultation-sessions?${query}`);
  },
  myCalendar: (query: DoctorCalendarQuery) => {
    if (Boolean(query.weekStart) === Boolean(query.date)) {
      throw new Error("Chỉ chọn một ngày hoặc ngày đầu tuần.");
    }
    const params = new URLSearchParams();
    if (query.weekStart) params.set("weekStart", query.weekStart);
    if (query.date) params.set("date", query.date);
    return api.get<DoctorCalendarDTO>(`/v2/consultation-sessions/mine?${params}`);
  },
  registrationsBySession: (sessionId: string, page = 0, size = 20) =>
    api.get<PageResult<RegistrationSummary>>(`/v2/consultation-sessions/${encodeURIComponent(sessionId)}/registrations?page=${page}&size=${size}`),
  createSession: (body: ConsultationSessionCreate, options: SchedulingCommandOptions) =>
    api.post<ConsultationSessionDTO>("/v2/consultation-sessions", body, options),
  updateSession: (sessionId: string, body: ConsultationSessionUpdate, options: SchedulingCommandOptions) =>
    api.put<ConsultationSessionDTO>(`/v2/consultation-sessions/${encodeURIComponent(sessionId)}`, body, options),
  changeSessionStatus: (sessionId: string, body: ConsultationSessionStatusCommand, options: SchedulingCommandOptions) =>
    api.put<ConsultationSessionDTO>(`/v2/consultation-sessions/${encodeURIComponent(sessionId)}/status`, body, options),
  createRegistration: (body: RegistrationCreate, options: SchedulingCommandOptions) =>
    api.post<RegistrationDTO>("/v2/outpatient-registrations", body, options),
  myRegistrations: (params: OwnRegistrationSearchParams = {}) => {
    const page = Number.isInteger(params.page) && (params.page ?? -1) >= 0 ? params.page! : 0;
    const size = Number.isInteger(params.size) && (params.size ?? 0) > 0 ? Math.min(params.size!, 100) : 20;
    return api.get<PageResult<RegistrationDTO>>(`/v2/outpatient-registrations/mine?page=${page}&size=${size}`);
  },
  followUpSources: (params: FollowUpSourceSearchParams) => {
    if (!params.patientId.trim()) throw new Error("A patient ID is required for the follow-up source lookup.");
    const page = Number.isInteger(params.page) && (params.page ?? -1) >= 0 ? params.page! : 0;
    const size = Number.isInteger(params.size) && (params.size ?? 0) > 0 ? Math.min(params.size!, 100) : 20;
    const query = new URLSearchParams({ patientId: params.patientId.trim(), page: String(page), size: String(size) });
    return api.get<PageResult<FollowUpSourceSummary>>(`/v2/outpatient-registrations/follow-up-sources?${query}`);
  },
  registration: (registrationId: string) =>
    api.get<RegistrationDTO>(`/v2/outpatient-registrations/${encodeURIComponent(registrationId)}`),
  markRegistrationArrival: (registrationId: string, body: RevisionCommand, options: SchedulingCommandOptions) =>
    api.put<RegistrationDTO>(`/v2/outpatient-registrations/${encodeURIComponent(registrationId)}/arrival`, body, options),
  cancelRegistration: (registrationId: string, body: RegistrationCancellationCommand, options: SchedulingCommandOptions) =>
    api.put<RegistrationDTO>(`/v2/outpatient-registrations/${encodeURIComponent(registrationId)}/cancellation`, body, options),
};
