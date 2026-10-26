import { api } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { CreatePatientRequest, PatientDTO, UpdatePatientRequest } from "./types";

export interface PatientSearchParams {
  keyword?: string;
  page?: number;
  size?: number;
}

function mutableFields(body: UpdatePatientRequest): UpdatePatientRequest {
  return { hoTen: body.hoTen, ngaySinh: body.ngaySinh, gioiTinh: body.gioiTinh,
    diaChi: body.diaChi, soDienThoai: body.soDienThoai, email: body.email, bhytSo: body.bhytSo };
}

export const patientApi = {
  getById: (patientId: string) =>
    api.get<PatientDTO>(`/v1/patients/${encodeURIComponent(patientId)}`),
  search: (params: PatientSearchParams = {}) => {
    const query = new URLSearchParams({
      page: String(Number.isInteger(params.page) && params.page! >= 0 && params.page! <= 2147483647 ? params.page : 0),
      size: String(Number.isInteger(params.size) && params.size! > 0 ? Math.min(params.size!, 100) : 20),
    });
    if (params.keyword?.trim()) query.set("keyword", params.keyword.trim());
    return api.get<PageResult<PatientDTO>>(`/v1/patients?${query}`);
  },
  create: (body: CreatePatientRequest) => api.post<PatientDTO>("/v1/patients", { ...mutableFields(body), soCmnd: body.soCmnd }),
  update: (patientId: string, body: UpdatePatientRequest) =>
    api.put<PatientDTO>(`/v1/patients/${encodeURIComponent(patientId)}`, { ...mutableFields(body), ...(body.soCmnd === undefined ? {} : { soCmnd: body.soCmnd }), ...(body.lyDoThayDoiDinhDanh === undefined ? {} : { lyDoThayDoiDinhDanh: body.lyDoThayDoiDinhDanh }) }),
  delete: (patientId: string) =>
    api.delEmpty(`/v1/patients/${encodeURIComponent(patientId)}`),
};
