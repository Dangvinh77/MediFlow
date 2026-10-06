"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { labApi } from "../api";
import {
  EMPTY_LAB_REQUEST_FORM,
  getLocalTodayIso,
  mapLabRequestFieldErrors,
  toCreateLabRequest,
  validateLabRequest,
  type LabRequestFieldErrors,
  type LabRequestFormField,
  type LabRequestFormValues,
} from "../form";
import { LabMutationError } from "./LabMutationError";

interface RequestError {
  status: number | null;
  message: string;
  correlationId: string | null;
}

export function CreateLabRequestForm() {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const [values, setValues] = useState<LabRequestFormValues>(EMPTY_LAB_REQUEST_FORM);
  const [fieldErrors, setFieldErrors] = useState<LabRequestFieldErrors>({});
  const [requestError, setRequestError] = useState<RequestError | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const today = getLocalTodayIso();

  function updateField(field: LabRequestFormField, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setRequestError(null);
  }

  function focusFirstError(errors: LabRequestFieldErrors) {
    const first = Object.keys(errors)[0];
    if (first) window.requestAnimationFrame(() => document.getElementById(first)?.focus());
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const errors = validateLabRequest(values, today);
    setFieldErrors(errors);
    setRequestError(null);
    if (Object.keys(errors).length > 0) {
      focusFirstError(errors);
      return;
    }

    setSubmitting(true);
    try {
      const created = await labApi.create(toCreateLabRequest(values));
      router.replace(`/lab/${created.testId}?notice=created`);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      if (cause instanceof ApiRequestError) {
        const serverErrors = mapLabRequestFieldErrors(cause.details);
        setFieldErrors(serverErrors);
        if (Object.keys(serverErrors).length > 0) focusFirstError(serverErrors);
        setRequestError({ status: cause.status, message: cause.message, correlationId: cause.correlationId });
      } else {
        setRequestError({ status: null, message: cause instanceof Error ? cause.message : "Không thể tạo yêu cầu xét nghiệm.", correlationId: null });
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form ref={formRef} onSubmit={submit} noValidate className="mt-6 space-y-6 rounded-xl border border-border bg-surface p-6">
      {requestError ? <LabMutationError {...requestError} onRetry={requestError.status === null || requestError.status >= 500 ? () => formRef.current?.requestSubmit() : undefined} /> : null}
      <div className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
        Yêu cầu này dùng hợp đồng tương thích hiện hành. Trình duyệt không suy luận trạng thái thanh toán hoặc tự tạo quyền tài chính.
      </div>
      <div className="grid gap-5 md:grid-cols-2">
        <Field label="Mã hồ sơ khám" name="recordId" error={fieldErrors.recordId} required hint="UUID hồ sơ Clinical đã phê duyệt.">
          <input id="recordId" value={values.recordId} disabled={submitting} onChange={(event) => updateField("recordId", event.target.value)} className={inputClass(Boolean(fieldErrors.recordId))} aria-invalid={fieldErrors.recordId ? true : undefined} aria-describedby={fieldErrors.recordId ? "recordId-error" : undefined} />
        </Field>
        <Field label="Mã bệnh nhân" name="patientId" error={fieldErrors.patientId} required>
          <input id="patientId" value={values.patientId} disabled={submitting} onChange={(event) => updateField("patientId", event.target.value)} className={inputClass(Boolean(fieldErrors.patientId))} aria-invalid={fieldErrors.patientId ? true : undefined} aria-describedby={fieldErrors.patientId ? "patientId-error" : undefined} />
        </Field>
        <Field label="Mã khoa yêu cầu" name="requestingDepartmentId" error={fieldErrors.requestingDepartmentId} required>
          <input id="requestingDepartmentId" value={values.requestingDepartmentId} disabled={submitting} onChange={(event) => updateField("requestingDepartmentId", event.target.value)} className={inputClass(Boolean(fieldErrors.requestingDepartmentId))} aria-invalid={fieldErrors.requestingDepartmentId ? true : undefined} aria-describedby={fieldErrors.requestingDepartmentId ? "requestingDepartmentId-error" : undefined} />
        </Field>
        <Field label="Loại xét nghiệm" name="labType" error={fieldErrors.labType} required hint={`${values.labType.length}/50 ký tự`}>
          <input id="labType" value={values.labType} maxLength={50} disabled={submitting} onChange={(event) => updateField("labType", event.target.value)} className={inputClass(Boolean(fieldErrors.labType))} aria-invalid={fieldErrors.labType ? true : undefined} aria-describedby={fieldErrors.labType ? "labType-error" : undefined} />
        </Field>
        <Field label="Ngày yêu cầu" name="requestedDate" error={fieldErrors.requestedDate} required>
          <input id="requestedDate" type="date" max={today} value={values.requestedDate} disabled={submitting} onChange={(event) => updateField("requestedDate", event.target.value)} className={inputClass(Boolean(fieldErrors.requestedDate))} aria-invalid={fieldErrors.requestedDate ? true : undefined} aria-describedby={fieldErrors.requestedDate ? "requestedDate-error" : undefined} />
        </Field>
      </div>
      <div className="flex flex-wrap gap-3">
        <button type="submit" disabled={submitting} className="min-h-11 rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50">{submitting ? "Đang tạo…" : "Tạo yêu cầu xét nghiệm"}</button>
        <Link href="/lab" className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">Hủy</Link>
      </div>
    </form>
  );
}

function inputClass(invalid: boolean): string {
  return `min-h-11 w-full rounded-lg border ${invalid ? "border-danger" : "border-border"} bg-surface px-3 py-2 text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60`;
}

function Field({ label, name, error, hint, required = false, children }: {
  label: string;
  name: LabRequestFormField;
  error?: string;
  hint?: string;
  required?: boolean;
  children: React.ReactNode;
}) {
  return <div className="flex flex-col gap-1"><label htmlFor={name} className="text-sm font-medium">{label}{required ? <span aria-hidden="true"> *</span> : null}</label>{children}{error ? <p id={`${name}-error`} className="text-xs text-danger">{error}</p> : null}{!error && hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}</div>;
}
