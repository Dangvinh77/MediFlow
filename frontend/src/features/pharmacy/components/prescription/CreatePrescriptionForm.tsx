"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import type { Role } from "@/lib/roles";
import { pharmacyApi } from "../../api";
import { getPharmacyCapabilities } from "../../permissions";
import type { DrugDTO } from "../../types";
import { getLocalTodayIso } from "../../utils";
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

function getServerRole(): Role | null {
  return null;
}

function correlationSuffix(cause: ApiRequestError): string {
  return cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : "";
}

function apiErrorMessage(cause: unknown, fallback: string): string {
  if (cause instanceof ApiRequestError) {
    if (cause.code === "PRESCRIPTION_CREATION_FORBIDDEN") {
      return `Danh tính đăng nhập chưa được xác nhận là bác sĩ kê đơn này. Hãy kiểm tra mã bác sĩ hoặc liên hệ quản trị viên.${correlationSuffix(cause)}`;
    }
    if (cause.status === 403) return `Bạn không có quyền thực hiện thao tác này.${correlationSuffix(cause)}`;
    return `${cause.message}${correlationSuffix(cause)}`;
  }
  return fallback;
}

function fieldInputClass(invalid: boolean): string {
  return `${controlClassName}${invalid ? " border-danger" : ""}`;
}

function mapPrescriptionValidationDetails(
  details: { field: string; message: string }[],
  lines: CreatePrescriptionFormValues["lines"],
): CreatePrescriptionValidationErrors {
  const fields: CreatePrescriptionFieldErrors = {};
  const lineErrors: Record<string, CreatePrescriptionValidationErrors["lines"][string]> = {};
  const lineFieldPattern = /^lines(?:\[(\d+)\]|\.(\d+))\.(drugId|quantity|dosage)$/;

  for (const detail of details) {
    const match = lineFieldPattern.exec(detail.field);
    if (match) {
      const lineIndex = Number(match[1] ?? match[2]);
      const row = lines[lineIndex];
      if (row) {
        const field = match[3] as keyof CreatePrescriptionValidationErrors["lines"][string];
        lineErrors[row.rowKey] = {
          ...lineErrors[row.rowKey],
          [field]: detail.message,
        };
      }
      continue;
    }

    if (["recordId", "patientId", "doctorId", "departmentId", "prescribedDate"].includes(detail.field)) {
      fields[detail.field as PrescriptionFormField] = detail.message;
    }
  }

  return { fields, lines: lineErrors };
}

export function CreatePrescriptionForm() {
  const router = useRouter();
  const role = useSyncExternalStore(
    subscribeToAuthChanges,
    getRole,
    getServerRole,
  );
  const capabilities = getPharmacyCapabilities(role);
  const [values, setValues] = useState<CreatePrescriptionFormValues>(createEmptyPrescriptionForm);
  const [validationErrors, setValidationErrors] = useState<CreatePrescriptionValidationErrors>({ fields: {}, lines: {} });
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const active = useRef(true);
  const focusTarget = useRef<string | undefined>(undefined);
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
    active.current = true;
    return () => { active.current = false; };
  }, []);

  useEffect(() => {
    if (!submitting && focusTarget.current) {
      document.getElementById(focusTarget.current)?.focus();
      focusTarget.current = undefined;
    }
  }, [validationErrors, submitting]);

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
    const lineEntry = Object.entries(errors.lines)[0];
    const lineKey = lineEntry?.[0];
    const lineField = lineEntry?.[1]
      ? (Object.keys(lineEntry[1])[0] as keyof CreatePrescriptionValidationErrors["lines"][string] | undefined)
      : undefined;
    focusTarget.current = field
      ? field
      : lineKey && lineField
        ? `${lineField}-${lineKey}`
        : errors.lineList ? "prescription-add-line" : undefined;
  }

  function submitDrugSearch() {
    setDrugSearchError(null);
    setActiveDrugKeyword(drugKeyword.trim());
    setDrugRetryToken((value) => value + 1);
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current || !getPharmacyCapabilities(getRole()).canCreatePrescription) return;

    setFormError(null);
    const errors = validateCreatePrescriptionForm(values, today);
    setValidationErrors(errors);
    if (hasCreatePrescriptionErrors(errors)) {
      focusFirstError(errors);
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    try {
      const created = await pharmacyApi.createPrescription(toCreatePrescriptionRequest(values));
      if (!active.current) return;
      router.replace(`/pharmacy/prescriptions/${created.prescriptionId}`);
    } catch (cause: unknown) {
      if (!active.current) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      if (cause instanceof ApiRequestError && cause.details.length > 0) {
        const mapped = mapPrescriptionValidationDetails(cause.details, values.lines);
        setValidationErrors((current) => ({
          fields: { ...current.fields, ...mapped.fields },
          lines: { ...current.lines, ...mapped.lines },
        }));
        focusFirstError(mapped);
      }

      if (cause instanceof ApiRequestError && cause.code === "PRESCRIPTION_DUPLICATE_DRUG") {
        setFormError(`Đơn thuốc không được chứa thuốc trùng nhau. Hãy kiểm tra các dòng thuốc.${correlationSuffix(cause)}`);
      } else if (cause instanceof ApiRequestError && cause.code === "INSUFFICIENT_AVAILABLE_STOCK") {
        setFormError(`Một hoặc nhiều thuốc không đủ tồn khả dụng. Hãy kiểm tra lại tồn kho rồi thử lại.${correlationSuffix(cause)}`);
      } else {
        const uncertain = !(cause instanceof ApiRequestError) || cause.status >= 500 || cause.status < 400;
        setFormError(uncertain
          ? "Chưa xác định được kết quả tạo đơn. Hãy kiểm tra đơn đã tạo trước khi gửi lại để tránh tạo trùng."
            + (cause instanceof ApiRequestError ? correlationSuffix(cause) : "")
          : apiErrorMessage(cause, "Không thể tạo đơn thuốc."));
      }
    } finally {
      submittingRef.current = false;
      if (active.current) setSubmitting(false);
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
    disabled: submitting,
    "aria-required": true,
    "aria-invalid": validationErrors.fields[name] ? true : undefined,
    "aria-describedby": validationErrors.fields[name] ? `${name}-error` : undefined,
  });

  return (
    <form onSubmit={submit} noValidate aria-busy={submitting || undefined} className="mt-6 space-y-6">
      {formError && (
        <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
          {formError}
        </div>
      )}

      <section className="rounded-xl border border-border bg-surface p-5 sm:p-6" aria-labelledby="prescription-context-title">
        <h2 id="prescription-context-title" className="text-base font-semibold">Thông tin kê đơn</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Nhập mã từ hồ sơ khám của bệnh nhân và thông tin bác sĩ phụ trách.
        </p>
        <div className="mt-5 grid gap-5 md:grid-cols-2">
          <Field label="Mã hồ sơ bệnh án" htmlFor="recordId" error={validationErrors.fields.recordId} required>
            <input id="recordId" {...field("recordId")} value={values.recordId} onChange={(event) => updateField("recordId", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.recordId))} placeholder="UUID hồ sơ" />
          </Field>
          <Field label="Mã bệnh nhân" htmlFor="patientId" error={validationErrors.fields.patientId} required>
            <input id="patientId" {...field("patientId")} value={values.patientId} onChange={(event) => updateField("patientId", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.patientId))} placeholder="UUID bệnh nhân" />
          </Field>
          <Field label="Mã bác sĩ" htmlFor="doctorId" error={validationErrors.fields.doctorId} required>
            <input id="doctorId" {...field("doctorId")} value={values.doctorId} onChange={(event) => updateField("doctorId", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.doctorId))} placeholder="UUID bác sĩ" />
          </Field>
          <Field label="Mã khoa" htmlFor="departmentId" error={validationErrors.fields.departmentId} required>
            <input id="departmentId" {...field("departmentId")} value={values.departmentId} onChange={(event) => updateField("departmentId", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.departmentId))} placeholder="UUID khoa" />
          </Field>
          <Field label="Ngày kê đơn" htmlFor="prescribedDate" error={validationErrors.fields.prescribedDate} required>
            <input id="prescribedDate" type="date" max={today} {...field("prescribedDate")} value={values.prescribedDate} onChange={(event) => updateField("prescribedDate", event.target.value)} className={fieldInputClass(Boolean(validationErrors.fields.prescribedDate))} />
          </Field>
        </div>
      </section>

      <PrescriptionLinesEditor
        lines={values.lines}
        disabled={submitting}
        listError={validationErrors.lineList}
        errors={validationErrors.lines}
        drugOptions={drugLoading || drugSearchError ? [] : drugOptions}
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
        <Button
          type="submit"
          loading={submitting}
          variant="primary"
        >
          Tạo đơn thuốc
        </Button>
        <Link href="/pharmacy/prescriptions" className="inline-flex min-h-11 items-center rounded-lg px-3 text-sm font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-primary">Tra cứu đơn thuốc</Link>
        <p className="text-sm text-muted-foreground">Giá chính thức được xác định khi tạo đơn.</p>
      </div>
    </form>
  );
}
