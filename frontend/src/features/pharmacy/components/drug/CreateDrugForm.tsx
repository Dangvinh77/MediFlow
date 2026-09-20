"use client";

import { useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import { getRole } from "@/lib/session";
import { pharmacyApi } from "../../api";
import { getPharmacyCapabilities } from "../../permissions";
import {
  EMPTY_CREATE_DRUG_FORM,
  toCreateDrugRequest,
  validateCreateDrugForm,
  type CreateDrugField,
  type CreateDrugFieldErrors,
  type CreateDrugFormValues,
} from "./drugFormValidation";
import { getLocalTodayIso, mapFieldErrors } from "../../utils";

function subscribeToRoleChanges(onStoreChange: () => void) {
  window.addEventListener("storage", onStoreChange);
  window.addEventListener("focus", onStoreChange);

  return () => {
    window.removeEventListener("storage", onStoreChange);
    window.removeEventListener("focus", onStoreChange);
  };
}

function getServerRole(): Role | null {
  return null;
}

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 403) return "Bạn không có quyền tạo thuốc.";
    if (cause.correlationId) {
      return `${cause.message} (Mã tra cứu: ${cause.correlationId})`;
    }
    return cause.message;
  }
  return cause instanceof Error ? cause.message : "Không thể tạo thuốc.";
}

export function CreateDrugForm() {
  const router = useRouter();
  const role = useSyncExternalStore(
    subscribeToRoleChanges,
    getRole,
    getServerRole,
  );
  const [values, setValues] = useState<CreateDrugFormValues>(EMPTY_CREATE_DRUG_FORM);
  const [fieldErrors, setFieldErrors] = useState<CreateDrugFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const capabilities = getPharmacyCapabilities(role);
  const today = getLocalTodayIso();

  function focusFirstError(errors: CreateDrugFieldErrors) {
    const firstField = Object.keys(errors)[0] as CreateDrugField | undefined;
    if (!firstField) return;
    window.requestAnimationFrame(() => {
      document.getElementById(firstField)?.focus();
    });
  }

  function updateField(field: CreateDrugField, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setFormError(null);
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    setFormError(null);
    const errors = validateCreateDrugForm(values, today);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      focusFirstError(errors);
      return;
    }

    setSubmitting(true);
    try {
      const created = await pharmacyApi.createDrug(toCreateDrugRequest(values));
      router.replace(`/pharmacy/drugs/${created.drugId}`);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      if (cause instanceof ApiRequestError && cause.details.length > 0) {
        const mapped = mapFieldErrors(cause.details);
        setFieldErrors(mapped);
        focusFirstError(mapped);
      }
      setFormError(errorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  }

  if (role === null) {
    return <p className="mt-6 text-sm text-muted-foreground">Đang kiểm tra quyền…</p>;
  }

  if (!capabilities.canCreateDrug) {
    return (
      <p role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
        Tài khoản của bạn không có quyền tạo thuốc.
      </p>
    );
  }

  const field = (name: CreateDrugField) => ({
    "aria-invalid": fieldErrors[name] ? true : undefined,
    "aria-describedby": fieldErrors[name] ? `${name}-error` : undefined,
  });

  return (
    <form onSubmit={submit} noValidate className="mt-6 space-y-6 rounded-xl border border-border bg-surface p-6">
      {formError && (
        <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
          {formError}
        </div>
      )}

      <div className="grid gap-5 md:grid-cols-2">
        <FormField label="Tên thuốc" name="drugName" error={fieldErrors.drugName} required>
          <input
            {...field("drugName")}
            id="drugName"
            value={values.drugName}
            maxLength={150}
            onChange={(event) => updateField("drugName", event.target.value)}
            className={inputClass(Boolean(fieldErrors.drugName))}
          />
        </FormField>
        <FormField label="Đơn vị" name="unit" error={fieldErrors.unit} required hint="Tối đa 20 ký tự">
          <input
            {...field("unit")}
            id="unit"
            value={values.unit}
            maxLength={20}
            onChange={(event) => updateField("unit", event.target.value)}
            className={inputClass(Boolean(fieldErrors.unit))}
          />
        </FormField>
        <FormField label="Hoạt chất" name="activeIngredient" error={fieldErrors.activeIngredient} hint="Không bắt buộc, tối đa 150 ký tự">
          <input
            {...field("activeIngredient")}
            id="activeIngredient"
            value={values.activeIngredient}
            maxLength={150}
            onChange={(event) => updateField("activeIngredient", event.target.value)}
            className={inputClass(Boolean(fieldErrors.activeIngredient))}
          />
        </FormField>
        <FormField label="Nhà sản xuất" name="manufacturer" error={fieldErrors.manufacturer} hint="Không bắt buộc, tối đa 150 ký tự">
          <input
            {...field("manufacturer")}
            id="manufacturer"
            value={values.manufacturer}
            maxLength={150}
            onChange={(event) => updateField("manufacturer", event.target.value)}
            className={inputClass(Boolean(fieldErrors.manufacturer))}
          />
        </FormField>
        <FormField label="Giá thuốc (VND)" name="price" error={fieldErrors.price} required hint="Không âm, tối đa 2 chữ số thập phân">
          <input
            {...field("price")}
            id="price"
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            value={values.price}
            onChange={(event) => updateField("price", event.target.value)}
            className={inputClass(Boolean(fieldErrors.price))}
          />
        </FormField>
        <FormField label="Tồn kho ban đầu" name="stockQuantity" error={fieldErrors.stockQuantity} required>
          <input
            {...field("stockQuantity")}
            id="stockQuantity"
            type="number"
            inputMode="numeric"
            min="0"
            step="1"
            value={values.stockQuantity}
            onChange={(event) => updateField("stockQuantity", event.target.value)}
            className={inputClass(Boolean(fieldErrors.stockQuantity))}
          />
        </FormField>
        <FormField label="Hạn sử dụng" name="expiryDate" error={fieldErrors.expiryDate} required hint="Không được trước ngày hiện tại">
          <input
            {...field("expiryDate")}
            id="expiryDate"
            type="date"
            min={today}
            value={values.expiryDate}
            onChange={(event) => updateField("expiryDate", event.target.value)}
            className={inputClass(Boolean(fieldErrors.expiryDate))}
          />
        </FormField>
        <FormField label="Ngưỡng tồn thấp" name="lowStockThreshold" error={fieldErrors.lowStockThreshold} hint="Không bắt buộc; bỏ trống để dùng mặc định 10">
          <input
            {...field("lowStockThreshold")}
            id="lowStockThreshold"
            type="number"
            inputMode="numeric"
            min="0"
            step="1"
            value={values.lowStockThreshold}
            onChange={(event) => updateField("lowStockThreshold", event.target.value)}
            className={inputClass(Boolean(fieldErrors.lowStockThreshold))}
          />
        </FormField>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="submit"
          disabled={submitting}
          className="rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {submitting ? "Đang lưu…" : "Tạo thuốc"}
        </button>
        <p className="text-sm text-muted-foreground">Các trường có dấu * là bắt buộc.</p>
      </div>
    </form>
  );
}

function inputClass(invalid: boolean): string {
  return [
    "w-full rounded-lg border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground",
    invalid ? "border-danger" : "border-border",
  ].join(" ");
}

function FormField({
  label,
  name,
  error,
  required = false,
  hint,
  children,
}: {
  label: string;
  name: string;
  error?: string;
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={name} className="text-sm font-medium">
        {label}{required && <span aria-hidden="true"> *</span>}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-muted-foreground">{hint}</p>}
      {error && <p id={`${name}-error`} className="text-xs text-danger">{error}</p>}
    </div>
  );
}
