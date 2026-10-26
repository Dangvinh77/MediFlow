import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import type { CreatePatientRequest, PatientDTO, UpdatePatientRequest } from "./types";

export const patientPermissions = (role: Role | null) => ({
  read: role === "ADMIN" || role === "DOCTOR" || role === "NURSE",
  write: role === "ADMIN" || role === "NURSE",
  delete: role === "ADMIN",
});

export interface PatientDraft {
  hoTen: string; ngaySinh: string; gioiTinh: string; soCmnd: string;
  diaChi: string; soDienThoai: string; email: string; bhytSo: string;
}
export type PatientFieldErrors = Partial<Record<keyof PatientDraft, string>>;
export const emptyPatientDraft = (): PatientDraft => ({
  hoTen: "", ngaySinh: "", gioiTinh: "M", soCmnd: "", diaChi: "",
  soDienThoai: "", email: "", bhytSo: "",
});

export function patientToDraft(patient: PatientDTO): PatientDraft {
  return { ...emptyPatientDraft(), hoTen: patient.hoTen, ngaySinh: patient.ngaySinh,
    gioiTinh: patient.gioiTinh, soCmnd: patient.soCmnd, diaChi: patient.diaChi ?? "",
    soDienThoai: patient.soDienThoai ?? "", email: patient.email ?? "", bhytSo: patient.bhytSo ?? "" };
}

/** Calendar date in the browser's timezone, rather than UTC's date. Server validation remains authoritative. */
export function patientToday(now = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

function validDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

export function validatePatientDraft(draft: PatientDraft, today = patientToday()): PatientFieldErrors {
  const errors: PatientFieldErrors = {};
  if (!draft.hoTen.trim() || draft.hoTen.trim().length > 100) errors.hoTen = "Họ tên phải có từ 1 đến 100 ký tự.";
  if (!validDate(draft.ngaySinh) || draft.ngaySinh > today) errors.ngaySinh = "Ngày sinh phải hợp lệ và không ở tương lai.";
  if (draft.gioiTinh !== "M" && draft.gioiTinh !== "F") errors.gioiTinh = "Giới tính phải là M hoặc F.";
  if (!draft.soCmnd.trim() || draft.soCmnd.trim().length > 20) errors.soCmnd = "Số CMND/CCCD phải có từ 1 đến 20 ký tự.";
  if (draft.diaChi.trim().length > 255) errors.diaChi = "Địa chỉ tối đa 255 ký tự.";
  if (draft.soDienThoai.trim() && !/^\d{10,15}$/.test(draft.soDienThoai.trim())) errors.soDienThoai = "Số điện thoại phải gồm 10 đến 15 chữ số.";
  if (draft.email.trim() && (draft.email.trim().length > 100 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(draft.email.trim()))) errors.email = "Email không đúng định dạng hoặc vượt quá 100 ký tự.";
  if (draft.bhytSo.trim() && !/^\d{2}-\d{8}-\d$/.test(draft.bhytSo.trim())) errors.bhytSo = "Số BHYT phải có định dạng 2 số-8 số-1 số.";
  return errors;
}

export function patientUpdateBody(draft: PatientDraft): UpdatePatientRequest {
  if (draft.gioiTinh !== "M" && draft.gioiTinh !== "F") throw new Error("Giới tính không hợp lệ.");
  return { hoTen: draft.hoTen.trim(), ngaySinh: draft.ngaySinh, gioiTinh: draft.gioiTinh, soCmnd: draft.soCmnd.trim(),
    diaChi: draft.diaChi.trim() || null, soDienThoai: draft.soDienThoai.trim() || null,
    email: draft.email.trim() || null, bhytSo: draft.bhytSo.trim() || null };
}
export function patientCreateBody(draft: PatientDraft): CreatePatientRequest {
  return { ...patientUpdateBody(draft), soCmnd: draft.soCmnd.trim() };
}

export interface PatientFailure {
  message: string;
  correlationId: string | null;
  status: number | null;
  code?: string;
  fieldErrors: PatientFieldErrors;
  outcomeUnknown: boolean;
}

/** Recognize only actual Patient field names; never turn arbitrary server details into form fields. */
export function patientFailure(cause: unknown, mutation = false): PatientFailure {
  const apiError = cause instanceof ApiRequestError ? cause : null;
  const outcomeUnknown = mutation && (!apiError || apiError.status === 408 || apiError.status >= 500 || apiError.status < 400);
  const fieldErrors: PatientFieldErrors = {};
  for (const detail of apiError?.details ?? []) {
    if (Object.hasOwn(emptyPatientDraft(), detail.field)) fieldErrors[detail.field as keyof PatientDraft] = detail.message;
  }
  const fieldsByCode: Record<string, keyof PatientDraft> = {
    PATIENT_CMND_DUPLICATE: "soCmnd", PATIENT_HOTEN_REQUIRED: "hoTen", PATIENT_NGAYSINH_FUTURE: "ngaySinh",
    PATIENT_GIOITINH_REQUIRED: "gioiTinh", PATIENT_SOCMND_REQUIRED: "soCmnd", PATIENT_SDT_INVALID: "soDienThoai",
    PATIENT_EMAIL_INVALID: "email", PATIENT_BHYT_INVALID: "bhytSo",
  };
  if (apiError?.code && fieldsByCode[apiError.code]) fieldErrors[fieldsByCode[apiError.code]] = apiError.message;
  return {
    message: outcomeUnknown
      ? "Chưa xác định được kết quả lưu/lưu trữ. Hãy tra cứu lại bệnh nhân trước khi thực hiện yêu cầu mới."
      : cause instanceof Error ? cause.message : "Không thể xử lý bệnh nhân.",
    correlationId: apiError?.correlationId ?? null, status: apiError?.status ?? null,
    code: apiError?.code, fieldErrors, outcomeUnknown,
  };
}
