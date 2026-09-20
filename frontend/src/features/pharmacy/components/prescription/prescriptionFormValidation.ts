import type {
  CreatePrescriptionRequest,
  DrugDTO,
  PrescriptionLineRequest,
} from "../../types";
import { getLocalTodayIso, isUuid } from "../../utils";

export interface PrescriptionLineFormValue {
  rowKey: string;
  drugId: string;
  quantity: string;
  dosage: string;
  drug: DrugDTO | null;
}

export interface CreatePrescriptionFormValues {
  recordId: string;
  patientId: string;
  doctorId: string;
  departmentId: string;
  prescribedDate: string;
  lines: PrescriptionLineFormValue[];
}

export type PrescriptionFormField =
  | "recordId"
  | "patientId"
  | "doctorId"
  | "departmentId"
  | "prescribedDate";

export interface PrescriptionLineFieldErrors {
  drugId?: string;
  quantity?: string;
  dosage?: string;
}

export type CreatePrescriptionFieldErrors = Partial<
  Record<PrescriptionFormField, string>
>;

export interface CreatePrescriptionValidationErrors {
  fields: CreatePrescriptionFieldErrors;
  lines: Record<string, PrescriptionLineFieldErrors>;
}

const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const INTEGER_PATTERN = /^\d+$/;
const INTEGER_MAX = 2_147_483_647;

let nextRowKey = 0;

export function createPrescriptionLine(): PrescriptionLineFormValue {
  nextRowKey += 1;
  return {
    rowKey: `prescription-line-${nextRowKey}`,
    drugId: "",
    quantity: "1",
    dosage: "",
    drug: null,
  };
}

export function createEmptyPrescriptionForm(): CreatePrescriptionFormValues {
  return {
    recordId: "",
    patientId: "",
    doctorId: "",
    departmentId: "",
    prescribedDate: getLocalTodayIso(),
    lines: [createPrescriptionLine()],
  };
}

function isValidIsoDate(value: string): boolean {
  if (!ISO_DATE_PATTERN.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

export function validateCreatePrescriptionForm(
  values: CreatePrescriptionFormValues,
  todayIso = getLocalTodayIso(),
): CreatePrescriptionValidationErrors {
  const fields: CreatePrescriptionFieldErrors = {};
  const lines: Record<string, PrescriptionLineFieldErrors> = {};

  (['recordId', 'patientId', 'doctorId', 'departmentId'] as const).forEach((field) => {
    if (!isUuid(values[field])) {
      fields[field] = "Nhập UUID hợp lệ (đúng định dạng 8-4-4-4-12).";
    }
  });

  if (!isValidIsoDate(values.prescribedDate) || values.prescribedDate > todayIso) {
    fields.prescribedDate = "Ngày kê đơn phải hợp lệ và không ở tương lai.";
  }

  if (values.lines.length === 0) {
    fields.recordId = fields.recordId ?? "Đơn thuốc phải có ít nhất một dòng thuốc.";
  }

  const seenDrugIds = new Set<string>();
  values.lines.forEach((line) => {
    const lineErrors: PrescriptionLineFieldErrors = {};
    const drugId = line.drugId.trim().toLowerCase();
    if (!isUuid(line.drugId)) {
      lineErrors.drugId = "Chọn một thuốc hợp lệ từ danh mục.";
    } else if (seenDrugIds.has(drugId)) {
      lineErrors.drugId = "Không được chọn trùng thuốc trong cùng đơn.";
    } else {
      seenDrugIds.add(drugId);
    }

    const quantity = line.quantity.trim();
    if (!quantity) {
      lineErrors.quantity = "Số lượng là bắt buộc.";
    } else if (!INTEGER_PATTERN.test(quantity) || Number(quantity) < 1 || Number(quantity) > INTEGER_MAX) {
      lineErrors.quantity = "Số lượng phải là số nguyên dương trong giới hạn cho phép.";
    }

    if (line.dosage.trim().length > 255) {
      lineErrors.dosage = "Cách dùng không được dài quá 255 ký tự.";
    }

    if (Object.keys(lineErrors).length > 0) {
      lines[line.rowKey] = lineErrors;
    }
  });

  return { fields, lines };
}

export function hasCreatePrescriptionErrors(
  errors: CreatePrescriptionValidationErrors,
): boolean {
  return Object.keys(errors.fields).length > 0 || Object.keys(errors.lines).length > 0;
}

/** Serializes only fields accepted by the backend request contract. */
export function toCreatePrescriptionRequest(
  values: CreatePrescriptionFormValues,
): CreatePrescriptionRequest {
  const lines: PrescriptionLineRequest[] = values.lines.map((line) => ({
    drugId: line.drugId.trim(),
    quantity: Number(line.quantity),
    ...(line.dosage.trim() ? { dosage: line.dosage.trim() } : {}),
  }));

  return {
    recordId: values.recordId.trim(),
    patientId: values.patientId.trim(),
    doctorId: values.doctorId.trim(),
    departmentId: values.departmentId.trim(),
    prescribedDate: values.prescribedDate,
    lines,
  };
}
