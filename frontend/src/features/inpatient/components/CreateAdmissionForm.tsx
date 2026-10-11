"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type ReactNode } from "react";
import { ApiRequestError } from "@/lib/api";
import { inpatientApi } from "../api";
import {
  EMPTY_ADMISSION_FORM,
  mapAdmissionFieldErrors,
  toCreateAdmissionRequest,
  validateAdmissionForm,
  type AdmissionFieldErrors,
  type AdmissionFormField,
  type AdmissionFormValues,
} from "../form";
import type { AdmissionPriority } from "../types";
import { AdmissionMutationError } from "./AdmissionMutationError";

interface RequestError {
  status: number | null;
  message: string;
  correlationId: string | null;
}

const priorities: ReadonlyArray<{ value: AdmissionPriority; label: string }> = [
  { value: "ROUTINE", label: "Thông thường" },
  { value: "URGENT", label: "Khẩn" },
  { value: "EMERGENCY", label: "Cấp cứu" },
];

export function CreateAdmissionForm() {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const [values, setValues] = useState<AdmissionFormValues>(EMPTY_ADMISSION_FORM);
  const [fieldErrors, setFieldErrors] = useState<AdmissionFieldErrors>({});
  const [requestError, setRequestError] = useState<RequestError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function updateField<K extends AdmissionFormField>(field: K, value: AdmissionFormValues[K]) {
    setValues((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setRequestError(null);
  }

  function focusFirstError(errors: AdmissionFieldErrors) {
    const first = Object.keys(errors)[0];
    if (first) window.requestAnimationFrame(() => document.getElementById(first)?.focus());
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const errors = validateAdmissionForm(values);
    setFieldErrors(errors);
    setRequestError(null);
    if (Object.keys(errors).length > 0) {
      focusFirstError(errors);
      return;
    }

    setSubmitting(true);
    try {
      const created = await inpatientApi.createAdmission(toCreateAdmissionRequest(values));
      router.replace(`/inpatient/${created.maDotNoiTru}?notice=created`);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      if (cause instanceof ApiRequestError) {
        const serverErrors = mapAdmissionFieldErrors(cause.details);
        setFieldErrors(serverErrors);
        if (Object.keys(serverErrors).length > 0) focusFirstError(serverErrors);
        setRequestError({
          status: cause.status,
          message: cause.message,
          correlationId: cause.correlationId,
        });
      } else {
        setRequestError({
          status: null,
          message: cause instanceof Error ? cause.message : "Không thể tạo đợt nội trú.",
          correlationId: null,
        });
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      ref={formRef}
      onSubmit={submit}
      noValidate
      className="mt-6 space-y-6 rounded-xl border border-border bg-surface p-6"
    >
      {requestError ? (
        <AdmissionMutationError
          {...requestError}
          onRetry={requestError.status === null || requestError.status >= 500
            ? () => formRef.current?.requestSubmit()
            : undefined}
        />
      ) : null}

      <div className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
        Mã nhân viên yêu cầu phải trùng với danh tính nhân viên trong phiên đăng nhập. Backend sẽ từ
        chối mọi yêu cầu mạo danh; trình duyệt không tự suy luận mã nhân viên từ token.
      </div>

      <div className="grid gap-5 md:grid-cols-2">
        <FormField label="Mã yêu cầu nội trú" name="maYeuCauNoiTru" error={fieldErrors.maYeuCauNoiTru} required>
          <input id="maYeuCauNoiTru" value={values.maYeuCauNoiTru} disabled={submitting} onChange={(event) => updateField("maYeuCauNoiTru", event.target.value)} className={inputClass(Boolean(fieldErrors.maYeuCauNoiTru))} aria-invalid={fieldErrors.maYeuCauNoiTru ? true : undefined} />
        </FormField>
        <FormField label="Mã hồ sơ nguồn" name="maHoSoNguon" error={fieldErrors.maHoSoNguon} required>
          <input id="maHoSoNguon" value={values.maHoSoNguon} disabled={submitting} onChange={(event) => updateField("maHoSoNguon", event.target.value)} className={inputClass(Boolean(fieldErrors.maHoSoNguon))} aria-invalid={fieldErrors.maHoSoNguon ? true : undefined} />
        </FormField>
        <FormField label="Mã bệnh nhân" name="maBenhNhan" error={fieldErrors.maBenhNhan} required>
          <input id="maBenhNhan" value={values.maBenhNhan} disabled={submitting} onChange={(event) => updateField("maBenhNhan", event.target.value)} className={inputClass(Boolean(fieldErrors.maBenhNhan))} aria-invalid={fieldErrors.maBenhNhan ? true : undefined} />
        </FormField>
        <FormField label="Mã khoa" name="maKhoa" error={fieldErrors.maKhoa} required>
          <input id="maKhoa" value={values.maKhoa} disabled={submitting} onChange={(event) => updateField("maKhoa", event.target.value)} className={inputClass(Boolean(fieldErrors.maKhoa))} aria-invalid={fieldErrors.maKhoa ? true : undefined} />
        </FormField>
        <FormField label="Mã nhân viên yêu cầu" name="nguoiYeuCau" error={fieldErrors.nguoiYeuCau} required>
          <input id="nguoiYeuCau" value={values.nguoiYeuCau} disabled={submitting} onChange={(event) => updateField("nguoiYeuCau", event.target.value)} className={inputClass(Boolean(fieldErrors.nguoiYeuCau))} aria-invalid={fieldErrors.nguoiYeuCau ? true : undefined} />
        </FormField>
        <FormField label="Mức độ ưu tiên" name="doUuTien" error={fieldErrors.doUuTien} required>
          <select id="doUuTien" value={values.doUuTien} disabled={submitting} onChange={(event) => updateField("doUuTien", event.target.value as AdmissionPriority)} className={inputClass(Boolean(fieldErrors.doUuTien))}>
            {priorities.map((priority) => <option key={priority.value} value={priority.value}>{priority.label}</option>)}
          </select>
        </FormField>
        <FormField label="Thời gian yêu cầu" name="thoiGianYeuCau" error={fieldErrors.thoiGianYeuCau} required>
          <input id="thoiGianYeuCau" type="datetime-local" value={values.thoiGianYeuCau} disabled={submitting} onChange={(event) => updateField("thoiGianYeuCau", event.target.value)} className={inputClass(Boolean(fieldErrors.thoiGianYeuCau))} aria-invalid={fieldErrors.thoiGianYeuCau ? true : undefined} />
        </FormField>
        <label className="flex min-h-11 items-center gap-3 self-end rounded-lg border border-border px-3 py-2 text-sm font-medium">
          <input type="checkbox" checked={values.capCuu} disabled={submitting} onChange={(event) => updateField("capCuu", event.target.checked)} className="size-4 accent-primary" />
          Trường hợp cấp cứu
        </label>
        <div className="md:col-span-2">
          <FormField label="Tóm tắt chẩn đoán" name="tomTatChanDoan" error={fieldErrors.tomTatChanDoan} required hint={`${values.tomTatChanDoan.length}/4000 ký tự`}>
            <textarea id="tomTatChanDoan" rows={5} maxLength={4000} value={values.tomTatChanDoan} disabled={submitting} onChange={(event) => updateField("tomTatChanDoan", event.target.value)} className={inputClass(Boolean(fieldErrors.tomTatChanDoan))} aria-invalid={fieldErrors.tomTatChanDoan ? true : undefined} />
          </FormField>
        </div>
      </div>

      <div className="flex flex-wrap gap-3">
        <button type="submit" disabled={submitting} className="min-h-11 rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50">
          {submitting ? "Đang tạo…" : "Tạo đợt nội trú"}
        </button>
        <Link href="/inpatient" className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">
          Hủy
        </Link>
      </div>
    </form>
  );
}

function inputClass(invalid: boolean): string {
  return `min-h-11 w-full rounded-lg border ${invalid ? "border-danger" : "border-border"} bg-surface px-3 py-2 text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60`;
}

function FormField({
  label,
  name,
  error,
  hint,
  required = false,
  children,
}: {
  label: string;
  name: AdmissionFormField;
  error?: string;
  hint?: string;
  required?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={name} className="text-sm font-medium">
        {label}{required ? <span aria-hidden="true"> *</span> : null}
      </label>
      {children}
      {error ? <p id={`${name}-error`} className="text-xs text-danger">{error}</p> : null}
      {!error && hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  );
}
