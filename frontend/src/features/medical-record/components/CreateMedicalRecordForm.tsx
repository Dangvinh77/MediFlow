"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { medicalRecordApi } from "../api";
import {
  EMPTY_MEDICAL_RECORD_FORM,
  getLocalTodayIso,
  mapMedicalRecordFieldErrors,
  toCreateMedicalRecordRequest,
  validateCreateMedicalRecord,
  type MedicalRecordFieldErrors,
  type MedicalRecordFormField,
  type MedicalRecordFormValues,
} from "../form";
import { DiagnosisFormFields, inputClass } from "./DiagnosisFormFields";
import { MedicalRecordMutationError } from "./MedicalRecordMutationError";

interface RequestError {
  status: number | null;
  message: string;
  correlationId: string | null;
}

export function CreateMedicalRecordForm() {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const [values, setValues] = useState<MedicalRecordFormValues>(EMPTY_MEDICAL_RECORD_FORM);
  const [fieldErrors, setFieldErrors] = useState<MedicalRecordFieldErrors>({});
  const [requestError, setRequestError] = useState<RequestError | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const today = getLocalTodayIso();

  function updateField(field: MedicalRecordFormField, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setRequestError(null);
  }

  function focusFirstError(errors: MedicalRecordFieldErrors) {
    const firstField = Object.keys(errors)[0] as MedicalRecordFormField | undefined;
    if (firstField) window.requestAnimationFrame(() => document.getElementById(firstField)?.focus());
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const errors = validateCreateMedicalRecord(values, today);
    setFieldErrors(errors);
    setRequestError(null);
    if (Object.keys(errors).length > 0) {
      focusFirstError(errors);
      return;
    }

    setSubmitting(true);
    try {
      const created = await medicalRecordApi.create(toCreateMedicalRecordRequest(values));
      router.replace(`/records/${created.recordId}?notice=created`);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      if (cause instanceof ApiRequestError) {
        const serverErrors = mapMedicalRecordFieldErrors(cause.details);
        if (Object.keys(serverErrors).length > 0) {
          setFieldErrors(serverErrors);
          focusFirstError(serverErrors);
        }
        setRequestError({ status: cause.status, message: cause.message, correlationId: cause.correlationId });
      } else {
        setRequestError({
          status: null,
          message: cause instanceof Error ? cause.message : "Không thể tạo hồ sơ khám.",
          correlationId: null,
        });
      }
    } finally {
      setSubmitting(false);
    }
  }

  const field = (name: MedicalRecordFormField) => ({
    "aria-invalid": fieldErrors[name] ? true : undefined,
    "aria-describedby": fieldErrors[name] ? `${name}-error` : undefined,
  });

  return (
    <form ref={formRef} onSubmit={submit} noValidate className="mt-6 space-y-6 rounded-xl border border-border bg-surface p-6">
      {requestError ? (
        <MedicalRecordMutationError
          {...requestError}
          onRetry={requestError.status === null || requestError.status >= 500
            ? () => formRef.current?.requestSubmit()
            : undefined}
        />
      ) : null}
      <div className="grid gap-5 md:grid-cols-2">
        <RecordInput label="Mã bệnh nhân" name="patientId" error={fieldErrors.patientId} required hint="UUID bệnh nhân đã được Patient xác nhận.">
          <input {...field("patientId")} id="patientId" value={values.patientId} disabled={submitting} onChange={(event) => updateField("patientId", event.target.value)} className={inputClass(Boolean(fieldErrors.patientId))} />
        </RecordInput>
        <RecordInput label="Mã bác sĩ" name="doctorId" error={fieldErrors.doctorId} required hint="Bác sĩ phải thuộc đúng khoa.">
          <input {...field("doctorId")} id="doctorId" value={values.doctorId} disabled={submitting} onChange={(event) => updateField("doctorId", event.target.value)} className={inputClass(Boolean(fieldErrors.doctorId))} />
        </RecordInput>
        <RecordInput label="Mã khoa" name="departmentId" error={fieldErrors.departmentId} required>
          <input {...field("departmentId")} id="departmentId" value={values.departmentId} disabled={submitting} onChange={(event) => updateField("departmentId", event.target.value)} className={inputClass(Boolean(fieldErrors.departmentId))} />
        </RecordInput>
        <RecordInput label="Mã lịch hẹn" name="appointmentId" error={fieldErrors.appointmentId} hint="Không bắt buộc. Nếu có, backend cập nhật lịch hẹn trong cùng giao dịch.">
          <input {...field("appointmentId")} id="appointmentId" value={values.appointmentId} disabled={submitting} onChange={(event) => updateField("appointmentId", event.target.value)} className={inputClass(Boolean(fieldErrors.appointmentId))} />
        </RecordInput>
        <RecordInput label="Ngày khám" name="examinationDate" error={fieldErrors.examinationDate} required>
          <input {...field("examinationDate")} id="examinationDate" type="date" max={today} value={values.examinationDate} disabled={submitting} onChange={(event) => updateField("examinationDate", event.target.value)} className={inputClass(Boolean(fieldErrors.examinationDate))} />
        </RecordInput>
        <RecordInput label="Triệu chứng" name="symptoms" error={fieldErrors.symptoms} hint={`${values.symptoms.length}/4000 ký tự`} wide>
          <textarea {...field("symptoms")} id="symptoms" value={values.symptoms} maxLength={4000} rows={4} disabled={submitting} onChange={(event) => updateField("symptoms", event.target.value)} className={`${inputClass(Boolean(fieldErrors.symptoms))} resize-y`} />
        </RecordInput>
      </div>
      <fieldset className="space-y-4 rounded-lg border border-border p-4">
        <legend className="px-2 text-sm font-semibold">Chẩn đoán ban đầu *</legend>
        <DiagnosisFormFields values={values} errors={fieldErrors} disabled={submitting} onChange={updateField} />
      </fieldset>
      <div className="flex flex-wrap items-center gap-3">
        <button type="submit" disabled={submitting} className="min-h-11 rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50">
          {submitting ? "Đang tạo…" : "Tạo hồ sơ khám"}
        </button>
        <Link href="/records" className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">Hủy</Link>
      </div>
    </form>
  );
}

function RecordInput({ label, name, error, hint, required = false, wide = false, children }: {
  label: string;
  name: MedicalRecordFormField;
  error?: string;
  hint?: string;
  required?: boolean;
  wide?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className={`flex flex-col gap-1${wide ? " md:col-span-2" : ""}`}>
      <label htmlFor={name} className="text-sm font-medium">{label}{required ? <span aria-hidden="true"> *</span> : null}</label>
      {children}
      {error ? <p id={`${name}-error`} className="text-xs text-danger">{error}</p> : null}
      {!error && hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  );
}
