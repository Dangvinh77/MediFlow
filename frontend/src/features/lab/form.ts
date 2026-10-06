import type { ApiError } from "@/lib/types";
import { isUuid } from "@/lib/validation";
import type { CreateLabRequest } from "./types";

export interface LabRequestFormValues {
  recordId: string;
  patientId: string;
  requestingDepartmentId: string;
  labType: string;
  requestedDate: string;
}

export type LabRequestFormField = keyof LabRequestFormValues;
export type LabRequestFieldErrors = Partial<Record<LabRequestFormField, string>>;

export const EMPTY_LAB_REQUEST_FORM: LabRequestFormValues = {
  recordId: "",
  patientId: "",
  requestingDepartmentId: "",
  labType: "",
  requestedDate: "",
};

export function getLocalTodayIso(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function validateLabRequest(
  values: LabRequestFormValues,
  todayIso = getLocalTodayIso(),
): LabRequestFieldErrors {
  const errors: LabRequestFieldErrors = {};
  for (const [field, label] of [
    ["recordId", "Mã hồ sơ"],
    ["patientId", "Mã bệnh nhân"],
    ["requestingDepartmentId", "Mã khoa yêu cầu"],
  ] as const) {
    const value = values[field].trim();
    if (!value) errors[field] = `${label} là bắt buộc.`;
    else if (!isUuid(value)) errors[field] = `${label} phải là UUID hợp lệ.`;
  }
  const labType = values.labType.trim();
  if (!labType) errors.labType = "Loại xét nghiệm là bắt buộc.";
  else if (labType.length > 50) errors.labType = "Loại xét nghiệm không được dài quá 50 ký tự.";
  if (!values.requestedDate) errors.requestedDate = "Ngày yêu cầu là bắt buộc.";
  else if (values.requestedDate > todayIso) errors.requestedDate = "Ngày yêu cầu không được ở tương lai.";
  return errors;
}

export function toCreateLabRequest(values: LabRequestFormValues): CreateLabRequest {
  // The live compatibility contract intentionally omits V2 episode/price fields while
  // care-finance activation remains held. No financial authority is inferred in the browser.
  return {
    recordId: values.recordId.trim(),
    patientId: values.patientId.trim(),
    requestingDepartmentId: values.requestingDepartmentId.trim(),
    labType: values.labType.trim(),
    requestedDate: values.requestedDate,
  };
}

export function mapLabRequestFieldErrors(details: ApiError["details"]): LabRequestFieldErrors {
  const known: readonly LabRequestFormField[] = [
    "recordId",
    "patientId",
    "requestingDepartmentId",
    "labType",
    "requestedDate",
  ];
  const errors: LabRequestFieldErrors = {};
  for (const detail of details) {
    if (known.includes(detail.field as LabRequestFormField)) {
      errors[detail.field as LabRequestFormField] = detail.message;
    }
  }
  return errors;
}
