import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type {
  AdmissionDTO,
  AdmissionStatus,
  BedDTO,
  BedStatus,
  CreateAdmissionRequest,
} from "./types";

export interface AdmissionSearchParams {
  departmentId?: string;
  patientId?: string;
  status?: AdmissionStatus;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface BedSearchParams {
  departmentId?: string;
  wardCode?: string;
  status?: BedStatus;
  page?: number;
  size?: number;
}

function pagination(page?: number, size?: number) {
  return new URLSearchParams({
    page: String(page ?? 0),
    size: String(size ?? 20),
  });
}

export const inpatientApi = {
  createAdmission: (body: CreateAdmissionRequest) =>
    api.post<AdmissionDTO>("/v1/inpatient/admissions", body),
  searchAdmissions: (params: AdmissionSearchParams = {}) => {
    const query = pagination(params.page, params.size);
    if (params.departmentId) query.set("departmentId", params.departmentId);
    if (params.patientId) query.set("patientId", params.patientId);
    if (params.status) query.set("status", params.status);
    if (params.from) query.set("from", params.from);
    if (params.to) query.set("to", params.to);
    return api.get<PageResult<AdmissionDTO>>(`/v1/inpatient/admissions?${query}`);
  },
  getAdmission: (admissionId: string) =>
    api.get<AdmissionDTO>(`/v1/inpatient/admissions/${encodeURIComponent(admissionId)}`),
  searchBeds: (params: BedSearchParams = {}) => {
    const query = pagination(params.page, params.size);
    if (params.departmentId) query.set("departmentId", params.departmentId);
    if (params.wardCode) query.set("wardCode", params.wardCode);
    if (params.status) query.set("status", params.status);
    return api.get<PageResult<BedDTO>>(`/v1/inpatient/beds?${query}`);
  },
};
