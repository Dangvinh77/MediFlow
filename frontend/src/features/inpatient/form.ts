import type { ApiError } from "@/lib/types";
import { isUuid } from "@/lib/validation";
import type { AdmissionPriority, CreateAdmissionRequest } from "./types";

export interface AdmissionFormValues {
  maYeuCauNoiTru: string;
  maHoSoNguon: string;
  maBenhNhan: string;
  maKhoa: string;
  nguoiYeuCau: string;
  tomTatChanDoan: string;
  doUuTien: AdmissionPriority;
  capCuu: boolean;
  thoiGianYeuCau: string;
}

export type AdmissionFormField = keyof AdmissionFormValues;
export type AdmissionFieldErrors = Partial<Record<AdmissionFormField, string>>;

export const EMPTY_ADMISSION_FORM: AdmissionFormValues = {
  maYeuCauNoiTru: "",
  maHoSoNguon: "",
  maBenhNhan: "",
  maKhoa: "",
  nguoiYeuCau: "",
  tomTatChanDoan: "",
  doUuTien: "ROUTINE",
  capCuu: false,
  thoiGianYeuCau: "",
};

const uuidFields = [
  ["maYeuCauNoiTru", "Mã yêu cầu nội trú"],
  ["maHoSoNguon", "Mã hồ sơ nguồn"],
  ["maBenhNhan", "Mã bệnh nhân"],
  ["maKhoa", "Mã khoa"],
  ["nguoiYeuCau", "Mã nhân viên yêu cầu"],
] as const satisfies ReadonlyArray<readonly [AdmissionFormField, string]>;

export function validateAdmissionForm(values: AdmissionFormValues): AdmissionFieldErrors {
  const errors: AdmissionFieldErrors = {};

  for (const [field, label] of uuidFields) {
    const value = String(values[field]).trim();
    if (!value) errors[field] = `${label} là bắt buộc.`;
    else if (!isUuid(value)) errors[field] = `${label} phải là UUID hợp lệ.`;
  }

  const diagnosisSummary = values.tomTatChanDoan.trim();
  if (!diagnosisSummary) {
    errors.tomTatChanDoan = "Tóm tắt chẩn đoán là bắt buộc.";
  } else if (diagnosisSummary.length > 4000) {
    errors.tomTatChanDoan = "Tóm tắt chẩn đoán không được dài quá 4000 ký tự.";
  }

  if (!values.thoiGianYeuCau) {
    errors.thoiGianYeuCau = "Thời gian yêu cầu là bắt buộc.";
  } else if (Number.isNaN(new Date(values.thoiGianYeuCau).getTime())) {
    errors.thoiGianYeuCau = "Thời gian yêu cầu không hợp lệ.";
  }

  return errors;
}

export function toCreateAdmissionRequest(values: AdmissionFormValues): CreateAdmissionRequest {
  return {
    maYeuCauNoiTru: values.maYeuCauNoiTru.trim(),
    maHoSoNguon: values.maHoSoNguon.trim(),
    maBenhNhan: values.maBenhNhan.trim(),
    maKhoa: values.maKhoa.trim(),
    nguoiYeuCau: values.nguoiYeuCau.trim(),
    tomTatChanDoan: values.tomTatChanDoan.trim(),
    doUuTien: values.doUuTien,
    capCuu: values.capCuu,
    thoiGianYeuCau: new Date(values.thoiGianYeuCau).toISOString(),
  };
}

export function mapAdmissionFieldErrors(details: ApiError["details"]): AdmissionFieldErrors {
  const knownFields = new Set<AdmissionFormField>([
    "maYeuCauNoiTru",
    "maHoSoNguon",
    "maBenhNhan",
    "maKhoa",
    "nguoiYeuCau",
    "tomTatChanDoan",
    "doUuTien",
    "capCuu",
    "thoiGianYeuCau",
  ]);
  const errors: AdmissionFieldErrors = {};

  for (const detail of details) {
    if (knownFields.has(detail.field as AdmissionFormField)) {
      errors[detail.field as AdmissionFormField] = detail.message;
    }
  }

  return errors;
}
