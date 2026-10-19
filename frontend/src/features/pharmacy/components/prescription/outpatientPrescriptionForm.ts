import type { CreateOutpatientPrescriptionRequest } from "../../types";
import { getLocalTodayIso, isUuid } from "../../utils";
import {
  createEmptyPrescriptionForm,
  hasCreatePrescriptionErrors,
  toCreatePrescriptionRequest,
  validateCreatePrescriptionForm,
  type CreatePrescriptionFieldErrors,
  type CreatePrescriptionFormValues,
  type CreatePrescriptionValidationErrors,
} from "./prescriptionFormValidation";

export interface OutpatientPrescriptionFormValues extends CreatePrescriptionFormValues {
  careEpisodeId: string;
  priceCode: string;
}

export interface OutpatientPrescriptionValidationErrors
  extends Omit<CreatePrescriptionValidationErrors, "fields"> {
  fields: CreatePrescriptionFieldErrors & {
    careEpisodeId?: string;
    priceCode?: string;
  };
}

export function createEmptyOutpatientPrescriptionForm(): OutpatientPrescriptionFormValues {
  return { ...createEmptyPrescriptionForm(), careEpisodeId: "", priceCode: "" };
}

export function validateOutpatientPrescriptionForm(
  values: OutpatientPrescriptionFormValues,
  todayIso = getLocalTodayIso(),
): OutpatientPrescriptionValidationErrors {
  // The broad V1 DTO permits null, but the public outpatient use case requires an exact record.
  const errors = validateCreatePrescriptionForm(values, todayIso);
  const fields: OutpatientPrescriptionValidationErrors["fields"] = { ...errors.fields };
  if (!isUuid(values.careEpisodeId)) {
    fields.careEpisodeId = "Nhập UUID của lượt khám ngoại trú đã được xác nhận.";
  }
  if (!values.priceCode.trim() || values.priceCode.length > 64) {
    fields.priceCode = "Nhập mã giá đã được xác nhận, tối đa 64 ký tự.";
  }
  return { ...errors, fields };
}

/** Builds only the OUTPATIENT wire shape; all authority and prices remain server-owned. */
export function toOutpatientPrescriptionRequest(
  values: OutpatientPrescriptionFormValues,
  todayIso = getLocalTodayIso(),
): CreateOutpatientPrescriptionRequest {
  const errors = validateOutpatientPrescriptionForm(values, todayIso);
  if (hasCreatePrescriptionErrors(errors)) {
    throw new Error("Thông tin kê đơn ngoại trú chưa hợp lệ.");
  }
  return {
    ...toCreatePrescriptionRequest(values),
    recordId: values.recordId.trim() || null,
    careContractVersion: 1,
    careContext: "OUTPATIENT",
    careEpisodeType: "OUTPATIENT_VISIT",
    careEpisodeId: values.careEpisodeId.trim(),
    admissionId: null,
    // Preserve the catalog key exactly: no guessed default or case conversion.
    priceCode: values.priceCode,
  };
}
