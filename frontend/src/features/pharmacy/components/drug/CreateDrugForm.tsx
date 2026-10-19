"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import type { Role } from "@/lib/roles";
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
import Link from "next/link";
import { PharmacySessionBoundary, usePharmacyRequestScope } from "../PharmacySessionBoundary";

function getServerRole(): Role | null {
  return null;
}

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 403) {
      const correlation = cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : "";
      return `Bạn không có quyền tạo thuốc.${correlation}`;
    }
    if (cause.correlationId) {
      return `${cause.message} (Mã tra cứu: ${cause.correlationId})`;
    }
    return cause.message;
  }
  return "Chưa xác định được kết quả tạo thuốc. Hãy kiểm tra danh mục trước khi gửi lại.";
}

export function CreateDrugForm() {
  return <PharmacySessionBoundary><CreateDrugFormContent /></PharmacySessionBoundary>;
}

function CreateDrugFormContent() {
  const isCurrent = usePharmacyRequestScope("canCreateDrug");
  const router = useRouter();
  const role = useSyncExternalStore(
    subscribeToAuthChanges,
    getRole,
    getServerRole,
  );
  const [values, setValues] = useState<CreateDrugFormValues>(EMPTY_CREATE_DRUG_FORM);
  const [fieldErrors, setFieldErrors] = useState<CreateDrugFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const completed = useRef(false);
  const [uncertain, setUncertain] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const focusField = useRef<CreateDrugField | undefined>(undefined);
  const active = useRef(true);
  const capabilities = getPharmacyCapabilities(role);
  const today = getLocalTodayIso();

  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);

  useEffect(() => {
    if (!submitting && focusField.current) {
      document.getElementById(focusField.current)?.focus();
      focusField.current = undefined;
    }
  }, [fieldErrors, submitting]);

  function focusFirstError(errors: CreateDrugFieldErrors) {
    focusField.current = Object.keys(errors)[0] as CreateDrugField | undefined;
  }

  function updateField(field: CreateDrugField, value: string) {
    if (submitting || uncertain || forbidden || completed.current) return;
    setValues((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setFormError(null);
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current || completed.current || uncertain || forbidden || !isCurrent()) return;

    setFormError(null);
    const errors = validateCreateDrugForm(values, today);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      focusFirstError(errors);
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    try {
      const created = await pharmacyApi.createDrug(toCreateDrugRequest(values));
      if (!active.current || !isCurrent()) return;
      completed.current = true;
      router.replace(`/pharmacy/drugs/${created.drugId}`);
    } catch (cause: unknown) {
      if (!active.current || !isCurrent()) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      if (cause instanceof ApiRequestError && cause.details.length > 0) {
        const details = mapFieldErrors(cause.details);
        const mapped: CreateDrugFieldErrors = {};
        for (const key of Object.keys(EMPTY_CREATE_DRUG_FORM) as CreateDrugField[]) {
          if (details[key]) mapped[key] = details[key];
        }
        setFieldErrors(mapped);
        focusFirstError(mapped);
      }
      const unknownOutcome = !(cause instanceof ApiRequestError) || cause.status >= 500 || cause.status < 400;
      if (cause instanceof ApiRequestError && cause.status === 403) setForbidden(true);
      setUncertain(unknownOutcome);
      setFormError(unknownOutcome ? `${errorMessage(cause)} Chưa xác định kết quả; hãy kiểm tra danh mục trước khi lập yêu cầu mới.` : errorMessage(cause));
    } finally {
      submittingRef.current = false;
      if (active.current && isCurrent() && !completed.current) setSubmitting(false);
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

  const field = (name: CreateDrugField, hasHint = false) => ({
    disabled: submitting || uncertain || forbidden,
    "aria-required": ["drugName", "unit", "price", "stockQuantity", "expiryDate"].includes(name) || undefined,
    "aria-invalid": fieldErrors[name] ? true : undefined,
    "aria-describedby": fieldErrors[name] ? `${name}-error` : hasHint ? `${name}-hint` : undefined,
  });

  return (
    <form onSubmit={submit} noValidate aria-busy={submitting || undefined} className="mt-6 space-y-6 rounded-xl border border-border bg-surface p-6">
      {formError && (
        <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
          {formError}
        </div>
      )}
      {uncertain && <Link href="/pharmacy/drugs" className="inline-flex min-h-11 items-center text-sm text-primary underline">Kiểm tra danh mục thuốc</Link>}

      <div className="grid gap-5 md:grid-cols-2">
        <Field label="Tên thuốc" htmlFor="drugName" error={fieldErrors.drugName} required>
          <input
            {...field("drugName")}
            id="drugName"
            value={values.drugName}
            maxLength={150}
            onChange={(event) => updateField("drugName", event.target.value)}
            className={inputClass(Boolean(fieldErrors.drugName))}
          />
        </Field>
        <Field label="Đơn vị" htmlFor="unit" error={fieldErrors.unit} required hint="Tối đa 20 ký tự">
          <input
            {...field("unit", true)}
            id="unit"
            value={values.unit}
            maxLength={20}
            onChange={(event) => updateField("unit", event.target.value)}
            className={inputClass(Boolean(fieldErrors.unit))}
          />
        </Field>
        <Field label="Hoạt chất" htmlFor="activeIngredient" error={fieldErrors.activeIngredient} hint="Không bắt buộc, tối đa 150 ký tự">
          <input
            {...field("activeIngredient", true)}
            id="activeIngredient"
            value={values.activeIngredient}
            maxLength={150}
            onChange={(event) => updateField("activeIngredient", event.target.value)}
            className={inputClass(Boolean(fieldErrors.activeIngredient))}
          />
        </Field>
        <Field label="Nhà sản xuất" htmlFor="manufacturer" error={fieldErrors.manufacturer} hint="Không bắt buộc, tối đa 150 ký tự">
          <input
            {...field("manufacturer", true)}
            id="manufacturer"
            value={values.manufacturer}
            maxLength={150}
            onChange={(event) => updateField("manufacturer", event.target.value)}
            className={inputClass(Boolean(fieldErrors.manufacturer))}
          />
        </Field>
        <Field label="Giá thuốc (VND)" htmlFor="price" error={fieldErrors.price} required hint="Không âm, tối đa 2 chữ số thập phân">
          <input
            {...field("price", true)}
            id="price"
            type="number"
            inputMode="decimal"
            min="0"
            step="0.01"
            value={values.price}
            onChange={(event) => updateField("price", event.target.value)}
            className={inputClass(Boolean(fieldErrors.price))}
          />
        </Field>
        <Field label="Tồn kho ban đầu" htmlFor="stockQuantity" error={fieldErrors.stockQuantity} required>
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
        </Field>
        <Field label="Hạn sử dụng" htmlFor="expiryDate" error={fieldErrors.expiryDate} required hint="Không được trước ngày hiện tại">
          <input
            {...field("expiryDate", true)}
            id="expiryDate"
            type="date"
            min={today}
            value={values.expiryDate}
            onChange={(event) => updateField("expiryDate", event.target.value)}
            className={inputClass(Boolean(fieldErrors.expiryDate))}
          />
        </Field>
        <Field label="Ngưỡng tồn thấp" htmlFor="lowStockThreshold" error={fieldErrors.lowStockThreshold} hint="Không bắt buộc; bỏ trống để dùng mặc định 10">
          <input
            {...field("lowStockThreshold", true)}
            id="lowStockThreshold"
            type="number"
            inputMode="numeric"
            min="0"
            step="1"
            value={values.lowStockThreshold}
            onChange={(event) => updateField("lowStockThreshold", event.target.value)}
            className={inputClass(Boolean(fieldErrors.lowStockThreshold))}
          />
        </Field>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="submit"
          loading={submitting}
          disabled={uncertain || forbidden}
          variant="primary"
        >
          Tạo thuốc
        </Button>
        <p className="text-sm text-muted-foreground">Các trường có dấu * là bắt buộc.</p>
      </div>
    </form>
  );
}

function inputClass(invalid: boolean): string {
  return `${controlClassName}${invalid ? " border-danger" : ""}`;
}
