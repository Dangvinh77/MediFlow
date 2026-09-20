import type { CreateDrugRequest } from "../../types";

export interface CreateDrugFormValues {
  drugName: string;
  activeIngredient: string;
  unit: string;
  price: string;
  stockQuantity: string;
  expiryDate: string;
  manufacturer: string;
  lowStockThreshold: string;
}

export type CreateDrugField = keyof CreateDrugFormValues;
export type CreateDrugFieldErrors = Partial<Record<CreateDrugField, string>>;

const INTEGER_MAX = 2_147_483_647;
const PRICE_PATTERN = /^(?:\d{1,13}(?:\.\d{0,2})?|\.\d{1,2})$/;
const INTEGER_PATTERN = /^\d+$/;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function isValidIsoDate(value: string): boolean {
  if (!ISO_DATE_PATTERN.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function validateOptionalText(
  value: string,
  maxLength: number,
  field: CreateDrugField,
  errors: CreateDrugFieldErrors,
) {
  const normalized = value.trim();
  if (normalized.length > maxLength) {
    errors[field] = `Không được dài quá ${maxLength} ký tự.`;
  }
}

/** Mirrors the request boundary without replacing backend validation. */
export function validateCreateDrugForm(
  values: CreateDrugFormValues,
  todayIso: string,
): CreateDrugFieldErrors {
  const errors: CreateDrugFieldErrors = {};
  const drugName = values.drugName.trim();
  const unit = values.unit.trim();

  if (!drugName) errors.drugName = "Tên thuốc là bắt buộc.";
  else if (drugName.length > 150) errors.drugName = "Tên thuốc không được dài quá 150 ký tự.";

  validateOptionalText(values.activeIngredient, 150, "activeIngredient", errors);

  if (!unit) errors.unit = "Đơn vị là bắt buộc.";
  else if (unit.length > 20) errors.unit = "Đơn vị không được dài quá 20 ký tự.";

  validateOptionalText(values.manufacturer, 150, "manufacturer", errors);

  if (!values.price.trim()) {
    errors.price = "Giá thuốc là bắt buộc.";
  } else if (!PRICE_PATTERN.test(values.price.trim())) {
    errors.price = "Giá phải không âm, tối đa 13 chữ số nguyên và 2 chữ số thập phân.";
  }

  if (!values.stockQuantity.trim()) {
    errors.stockQuantity = "Tồn kho ban đầu là bắt buộc.";
  } else if (
    !INTEGER_PATTERN.test(values.stockQuantity.trim()) ||
    Number(values.stockQuantity) > INTEGER_MAX
  ) {
    errors.stockQuantity = "Tồn kho phải là số nguyên không âm trong giới hạn cho phép.";
  }

  if (!values.expiryDate) {
    errors.expiryDate = "Hạn sử dụng là bắt buộc.";
  } else if (
    !isValidIsoDate(values.expiryDate) ||
    values.expiryDate < todayIso
  ) {
    errors.expiryDate = "Hạn sử dụng không được ở quá khứ.";
  }

  const threshold = values.lowStockThreshold.trim();
  if (
    threshold &&
    (!INTEGER_PATTERN.test(threshold) || Number(threshold) > INTEGER_MAX)
  ) {
    errors.lowStockThreshold = "Ngưỡng phải là số nguyên không âm trong giới hạn cho phép.";
  }

  return errors;
}

/** Converts UI strings to the exact wire request after validation succeeds. */
export function toCreateDrugRequest(
  values: CreateDrugFormValues,
): CreateDrugRequest {
  const request: CreateDrugRequest = {
    drugName: values.drugName.trim(),
    activeIngredient: values.activeIngredient.trim() || undefined,
    unit: values.unit.trim(),
    price: Number(values.price),
    stockQuantity: Number(values.stockQuantity),
    expiryDate: values.expiryDate,
    manufacturer: values.manufacturer.trim() || undefined,
  };

  const threshold = values.lowStockThreshold.trim();
  if (threshold) {
    request.lowStockThreshold = Number(threshold);
  }

  return request;
}

export const EMPTY_CREATE_DRUG_FORM: CreateDrugFormValues = {
  drugName: "",
  activeIngredient: "",
  unit: "",
  price: "",
  stockQuantity: "",
  expiryDate: "",
  manufacturer: "",
  lowStockThreshold: "",
};
