"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import { getRole } from "@/lib/session";
import { pharmacyApi } from "../../api";
import { getPharmacyCapabilities } from "../../permissions";
import type { DrugDTO } from "../../types";
import { getLocalTodayIso, mapFieldErrors } from "../../utils";
import {
  createEmptyPrescriptionForm,
  createPrescriptionLine,
  hasCreatePrescriptionErrors,
  toCreatePrescriptionRequest,
  validateCreatePrescriptionForm,
  type CreatePrescriptionFieldErrors,
  type CreatePrescriptionFormValues,
  type CreatePrescriptionValidationErrors,
  type PrescriptionFormField,
} from "./prescriptionFormValidation";
import { PrescriptionLinesEditor } from "./PrescriptionLinesEditor";

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

function apiErrorMessage(cause: unknown, fallback: string): string {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 403) return "Bạn không có quyền thực hiện thao tác này.";
    if (cause.correlationId) return `${cause.message} (Mã tra cứu: ${cause.correlationId})`;
    return cause.message;
  }
  return cause instanceof Error ? cause.message : fallback;
}

function isLineErrorKey(field: string): boolean {
  return field.startsWith("lines[") || field.startsWith("lines.");
}

function fieldInputClass(invalid: boolean): string {
  return [
    "w-full rounded-lg border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground",
    invalid ? "border-danger" : "border-border",
  ].join(" ");
}

function FormField({
  label,
  name,
  error,
  required = true,
  children,
}: {
  label: string;
  name: string;
  error?: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={name} className="text-sm font-medium">
        {label}{required && <span aria-hidden="true"> *</span>}
      </label>
      {children}
      {error && <p id={`${name}-error`} className="text-xs text-danger">{error}</p>}
    </div>
  );
}

export function CreatePrescriptionForm() {
  const router = useRouter();
  const role = useSyncExternalStore(
    subscribeToRoleChanges,
    getRole,
    getServerRole,
  );
  const capabilities = getPharmacyCapabilities(role);
  const [values, setValues] = useState<CreatePrescriptionFormValues>(createEmptyPrescriptionForm);
  const [validationErrors, setValidationErrors] = useState<CreatePrescriptionValidationErrors>({ fields: {}, lines: {} });
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [drugKeyword, setDrugKeyword] = useState("");
  const [activeDrugKeyword, setActiveDrugKeyword] = useState("");
  const [drugOptions, setDrugOptions] = useState<DrugDTO[]>([]);
  const [drugSearchError, setDrugSearchError] = useState<string | null>(null);
  const [settledDrugSearchKey, setSettledDrugSearchKey] = useState<string | null>(null);
  const [drugRetryToken, setDrugRetryToken] = useState(0);
  const requestSequence = useRef(0);
  const drugSearchKey = `${activeDrugKeyword}\u0000${drugRetryToken}`;
  const drugLoading = capabilities.canCreatePrescription && settledDrugSearchKey !== drugSearchKey;
  const today = getLocalTodayIso();

  useEffect(() => {
    if (!capabilities.canCreatePrescription) return undefined;

    let active = true;
    const requestId = ++requestSequence.current;

    pharmacyApi
      .searchDrugs({ keyword: activeDrugKeyword, page: 0, size: 20 })
      .then((result) => {
        if (!active || requestId !== requestSequence.current) return;
        setDrugOptions(result.content);
      })
      .catch((cause: unknown) => {
        if (!active || requestId !== requestSequence.current) return;
        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }
        setDrugSearchError(apiErrorMessage(cause, "Không thể tải danh mục thuốc."));
      })
      .finally(() => {
        if (active && requestId === requestSequence.current) {
          setSettledDrugSearchKey(drugSearchKey);
        }
      });

    return () => {
      active = false;
    };
  }, [activeDrugKeyword, capabilities.canCreatePrescription, drugSearchKey, router]);

  function updateField(field: keyof Omit<CreatePrescriptionFormValues, "lines">, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setValidationErrors((current) => ({
      ...current,
      fields: { ...current.fields, [field]: undefined },
    }));
    setFormError(null);
  }

  function updateLine(rowKey: string, patch: Partial<CreatePrescriptionFormValues["lines"][number]>) {
    setValues((current) => ({
      ...current,
      lines: current.lines.map((line) => (line.rowKey === rowKey ? { ...line, ...patch } : line)),
    }));
    setValidationErrors((current) => {
      const nextLines = { ...current.lines };
      delete nextLines[rowKey];
      return { ...current, lines: nextLines };
    });
    setFormError(null);
  }

  function focusFirstError(errors: CreatePrescriptionValidationErrors) {
    const field = Object.keys(errors.fields)[0] as PrescriptionFormField | undefined;
    const lineKey = Object.keys(errors.lines)[0];
    const targetId = field ?? (lineKey ? `drug-${lineKey}` : undefined);
    if (!targetId) return;
    window.requestAnimationFrame(() => document.getElementById(targetId)?.focus());
  }

  function submitDrugSearch() {
    setDrugSearchError(null);
    setActiveDrugKeyword(drugKeyword.trim());
    setDrugRetryToken((value) => value + 1);
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    setFormError(null);
    const errors = validateCreatePrescriptionForm(values, today);
    setValidationErrors(errors);
    if (hasCreatePrescriptionErrors(errors)) {
      focusFirstError(errors);
      return;
    }

    setSubmitting(true);
    try {
      const created = await pharmacyApi.createPrescription(toCreatePrescriptionRequest(values));
      router.replace(`/pharmacy/prescriptions/${created.prescriptionId}`);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      if (cause instanceof ApiRequestError && cause.details.length > 0) {
        const mapped = mapFieldErrors(cause.details);
        const fieldErrors: CreatePrescriptionFieldErrors = {};
        Object.entries(mapped).forEach(([field, message]) => {
          if (field in values && !isLineErrorKey(field)) {
            fieldErrors[field as PrescriptionFormField] = message;
          }
        });
        setValidationErrors((current) => ({ ...current, fields: { ...current.fields, ...fieldErrors } }));
      }

      if (cause instanceof ApiRequestError && cause.code === "PRESCRIPTION_DUPLICATE_DRUG") {
        setFormError("Đơn thuốc không được chứa thuốc trùng nhau. Hãy kiểm tra các dòng thuốc.");
      } else if (cause instanceof ApiRequestError && cause.code === "INSUFFICIENT_AVAILABLE_STOCK") {
        setFormError("Một hoặc nhiều thuốc không đủ tồn khả dụng. Hãy kiểm tra lại tồn kho rồi thử lại.");
      } else {
        setFormError(apiErrorMessage(cause, "Không thể tạo đơn thuốc."));
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (role === null) {
    return <p className="mt-6 text-sm text-muted-foreground">Đang kiểm tra quyền…</p>;
  }

  if (!capabilities.canCreatePrescription) {
    return (
      <p role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
        Tài khoản của bạn không có quyền tạo đơn thuốc.
      </p>
    );
  }

  const field = (name: PrescriptionFormField) => ({
    "aria-invalid": validationErrors.fields[name] ? true : undefined,
    "aria-describedby": validationErrors.fields[name] ? `${name}-error` : undefined,
  });

  return (
    <form onSubmit={submit} noValidate className="mt-6 space-y-6">
      {formError && (
        <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
          {formError}
        </div>
      )}

      <section className="rounded-xl border border-border bg-surface p-5">
        <h2 className="text-base font-semibold">Ngữ cảnh đơn thuốc</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Các mã dưới đây phải là UUID đã tồn tại trong hệ thống. Frontend không tự tạo hoặc suy diễn danh tính.
        </p>
        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <FormField label="Mã hồ sơ bệnh án" name="recordId" error={validationErrors.fields.recordId}>
            <input id="recordId" {...field("recordId")} value={values.recordId} onChange={(event) => updateField("recordId", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.recordId))} placeholder="UUID hồ sơ" />
          </FormField>
          <FormField label="Mã bệnh nhân" name="patientId" error={validationErrors.fields.patientId}>
            <input id="patientId" {...field("patientId")} value={values.patientId} onChange={(event) => updateField("patientId", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.patientId))} placeholder="UUID bệnh nhân" />
          </FormField>
          <FormField label="Mã bác sĩ" name="doctorId" error={validationErrors.fields.doctorId}>
            <input id="doctorId" {...field("doctorId")} value={values.doctorId} onChange={(event) => updateField("doctorId", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.doctorId))} placeholder="UUID bác sĩ" />
          </FormField>
          <FormField label="Mã khoa" name="departmentId" error={validationErrors.fields.departmentId}>
            <input id="departmentId" {...field("departmentId")} value={values.departmentId} onChange={(event) => updateField("departmentId", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.departmentId))} placeholder="UUID khoa" />
          </FormField>
          <FormField label="Ngày kê đơn" name="prescribedDate" error={validationErrors.fields.prescribedDate}>
            <input id="prescribedDate" type="date" max={today} {...field("prescribedDate")} value={values.prescribedDate} onChange={(event) => updateField("prescribedDate", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.prescribedDate))} />
          </FormField>
        </div>
      </section>

      <PrescriptionLinesEditor
        lines={values.lines}
        errors={validationErrors.lines}
        drugOptions={drugOptions}
        drugKeyword={drugKeyword}
        drugLoading={drugLoading}
        drugError={drugSearchError}
        onDrugKeywordChange={setDrugKeyword}
        onSearchDrugs={submitDrugSearch}
        onChange={updateLine}
        onAdd={() => setValues((current) => ({ ...current, lines: [...current.lines, createPrescriptionLine()] }))}
        onRemove={(rowKey) => setValues((current) => ({ ...current, lines: current.lines.filter((line) => line.rowKey !== rowKey) }))}
      />

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="submit"
          disabled={submitting}
          className="rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {submitting ? "Đang tạo đơn…" : "Tạo đơn thuốc"}
        </button>
        <p className="text-sm text-muted-foreground">Giá/tổng tiền không được gửi từ form.</p>
      </div>
    </form>
  );
}
