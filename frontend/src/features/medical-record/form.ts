import type { ApiError } from "@/lib/types";
import { isUuid } from "@/lib/validation";
import type {
  AddDiagnosisRequest,
  CreateMedicalRecordRequest,
  UpdateMedicalRecordRequest,
} from "./types";

const ICD_PATTERN = /^[A-Z]\d{2}(\.\d{1,2})?$/;

export interface DiagnosisFormValues {
  diagnosisName: string;
  description: string;
  icdCode: string;
}

export interface MedicalRecordFormValues extends DiagnosisFormValues {
  patientId: string;
  doctorId: string;
  departmentId: string;
  examinationDate: string;
  symptoms: string;
  appointmentId: string;
}

export type MedicalRecordFormField = keyof MedicalRecordFormValues;
export type MedicalRecordFieldErrors = Partial<Record<MedicalRecordFormField, string>>;

export const EMPTY_DIAGNOSIS_FORM: DiagnosisFormValues = {
  diagnosisName: "",
  description: "",
  icdCode: "",
};

export const EMPTY_MEDICAL_RECORD_FORM: MedicalRecordFormValues = {
  patientId: "",
  doctorId: "",
  departmentId: "",
  examinationDate: "",
  symptoms: "",
  appointmentId: "",
  ...EMPTY_DIAGNOSIS_FORM,
};

function validateUuid(
  field: "patientId" | "doctorId" | "departmentId" | "appointmentId",
  label: string,
  value: string,
  errors: MedicalRecordFieldErrors,
  required = true,
) {
  const normalized = value.trim();
  if (!normalized) {
    if (required) errors[field] = `${label} là bắt buộc.`;
  } else if (!isUuid(normalized)) {
    errors[field] = `${label} phải là UUID hợp lệ.`;
  }
}

export function getLocalTodayIso(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function validateDiagnosis(
  values: DiagnosisFormValues,
): MedicalRecordFieldErrors {
  const errors: MedicalRecordFieldErrors = {};
  const name = values.diagnosisName.trim();
  const description = values.description.trim();
  const icdCode = values.icdCode.trim();

  if (!name) errors.diagnosisName = "Tên chẩn đoán là bắt buộc.";
  else if (name.length > 255) errors.diagnosisName = "Tên chẩn đoán không được dài quá 255 ký tự.";
  if (description.length > 2000) errors.description = "Mô tả không được dài quá 2000 ký tự.";
  if (icdCode && !ICD_PATTERN.test(icdCode)) {
    errors.icdCode = "Mã ICD phải có dạng A00 hoặc A00.0.";
  }

  return errors;
}

export function validateCreateMedicalRecord(
  values: MedicalRecordFormValues,
  todayIso = getLocalTodayIso(),
): MedicalRecordFieldErrors {
  const errors = validateDiagnosis(values);
  validateUuid("patientId", "Mã bệnh nhân", values.patientId, errors);
  validateUuid("doctorId", "Mã bác sĩ", values.doctorId, errors);
  validateUuid("departmentId", "Mã khoa", values.departmentId, errors);
  validateUuid("appointmentId", "Mã lịch hẹn", values.appointmentId, errors, false);

  if (!values.examinationDate) errors.examinationDate = "Ngày khám là bắt buộc.";
  else if (values.examinationDate > todayIso) errors.examinationDate = "Ngày khám không được ở tương lai.";
  if (values.symptoms.length > 4000) errors.symptoms = "Triệu chứng không được dài quá 4000 ký tự.";
  return errors;
}

export function validateRecordUpdate(symptoms: string): MedicalRecordFieldErrors {
  return symptoms.length > 4000
    ? { symptoms: "Triệu chứng không được dài quá 4000 ký tự." }
    : {};
}

export function toDiagnosisRequest(values: DiagnosisFormValues): AddDiagnosisRequest {
  return {
    diagnosisName: values.diagnosisName.trim(),
    description: values.description.trim() || null,
    icdCode: values.icdCode.trim().toUpperCase() || null,
  };
}

export function toCreateMedicalRecordRequest(
  values: MedicalRecordFormValues,
): CreateMedicalRecordRequest {
  return {
    patientId: values.patientId.trim(),
    doctorId: values.doctorId.trim(),
    departmentId: values.departmentId.trim(),
    examinationDate: values.examinationDate,
    symptoms: values.symptoms.trim() || null,
    appointmentId: values.appointmentId.trim() || null,
    diagnoses: [toDiagnosisRequest(values)],
  };
}

export function toUpdateMedicalRecordRequest(symptoms: string): UpdateMedicalRecordRequest {
  return { symptoms: symptoms.trim() || null };
}

export function mapMedicalRecordFieldErrors(
  details: ApiError["details"],
): MedicalRecordFieldErrors {
  const aliases: Record<string, MedicalRecordFormField> = {
    patientId: "patientId",
    doctorId: "doctorId",
    departmentId: "departmentId",
    examinationDate: "examinationDate",
    symptoms: "symptoms",
    appointmentId: "appointmentId",
    diagnosisName: "diagnosisName",
    description: "description",
    icdCode: "icdCode",
    "diagnoses[0].diagnosisName": "diagnosisName",
    "diagnoses[0].description": "description",
    "diagnoses[0].icdCode": "icdCode",
  };
  const errors: MedicalRecordFieldErrors = {};
  for (const detail of details) {
    const field = aliases[detail.field];
    if (field) errors[field] = detail.message;
  }
  return errors;
}
