import type { ApiError } from "@/lib/types";
import { isUuid } from "@/lib/validation";
import type {
  CreateAppointmentRequest,
  UpdateAppointmentRequest,
} from "./types";

export interface AppointmentFormValues {
  patientId: string;
  doctorId: string;
  departmentId: string;
  appointmentDate: string;
  appointmentTime: string;
  reason: string;
}

export type AppointmentFormField = keyof AppointmentFormValues;
export type AppointmentFieldErrors = Partial<
  Record<AppointmentFormField, string>
>;

export const EMPTY_APPOINTMENT_FORM: AppointmentFormValues = {
  patientId: "",
  doctorId: "",
  departmentId: "",
  appointmentDate: "",
  appointmentTime: "",
  reason: "",
};

function validateUuid(
  field: "patientId" | "doctorId" | "departmentId",
  label: string,
  value: string,
  errors: AppointmentFieldErrors,
) {
  const normalized = value.trim();
  if (!normalized) {
    errors[field] = `${label} là bắt buộc.`;
  } else if (!isUuid(normalized)) {
    errors[field] = `${label} phải là UUID hợp lệ.`;
  }
}

export function validateAppointmentForm(
  values: AppointmentFormValues,
  includeReferences: boolean,
): AppointmentFieldErrors {
  const errors: AppointmentFieldErrors = {};

  if (includeReferences) {
    validateUuid("patientId", "Mã bệnh nhân", values.patientId, errors);
    validateUuid("doctorId", "Mã bác sĩ", values.doctorId, errors);
    validateUuid("departmentId", "Mã khoa", values.departmentId, errors);
  }

  if (!values.appointmentDate) {
    errors.appointmentDate = "Ngày hẹn là bắt buộc.";
  }
  if (!values.appointmentTime) {
    errors.appointmentTime = "Giờ hẹn là bắt buộc.";
  }
  if (values.reason.length > 1000) {
    errors.reason = "Lý do không được dài quá 1000 ký tự.";
  }

  return errors;
}

export function toCreateAppointmentRequest(
  values: AppointmentFormValues,
): CreateAppointmentRequest {
  return {
    patientId: values.patientId.trim(),
    doctorId: values.doctorId.trim(),
    departmentId: values.departmentId.trim(),
    appointmentDate: values.appointmentDate,
    appointmentTime: values.appointmentTime,
    reason: values.reason.trim() || null,
  };
}

export function toUpdateAppointmentRequest(
  values: AppointmentFormValues,
): UpdateAppointmentRequest {
  return {
    appointmentDate: values.appointmentDate,
    appointmentTime: values.appointmentTime,
    reason: values.reason.trim() || null,
  };
}

export function mapAppointmentFieldErrors(
  details: ApiError["details"],
): AppointmentFieldErrors {
  const knownFields: readonly AppointmentFormField[] = [
    "patientId",
    "doctorId",
    "departmentId",
    "appointmentDate",
    "appointmentTime",
    "reason",
  ];
  const errors: AppointmentFieldErrors = {};

  for (const detail of details) {
    if (knownFields.includes(detail.field as AppointmentFormField)) {
      errors[detail.field as AppointmentFormField] = detail.message;
    }
  }
  return errors;
}
