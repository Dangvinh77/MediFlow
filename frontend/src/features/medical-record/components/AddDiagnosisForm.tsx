"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { medicalRecordApi } from "../api";
import {
  EMPTY_DIAGNOSIS_FORM,
  mapMedicalRecordFieldErrors,
  toDiagnosisRequest,
  validateDiagnosis,
  type DiagnosisFormValues,
  type MedicalRecordFieldErrors,
  type MedicalRecordFormField,
} from "../form";
import type { DiagnosisDTO } from "../types";
import { DiagnosisFormFields } from "./DiagnosisFormFields";
import { MedicalRecordMutationError } from "./MedicalRecordMutationError";

interface AddDiagnosisFormProps {
  recordId: string;
  onCreated: (diagnosis: DiagnosisDTO) => void;
}

interface RequestError {
  status: number | null;
  message: string;
  correlationId: string | null;
}

export function AddDiagnosisForm({ recordId, onCreated }: AddDiagnosisFormProps) {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const [values, setValues] = useState<DiagnosisFormValues>(EMPTY_DIAGNOSIS_FORM);
  const [errors, setErrors] = useState<MedicalRecordFieldErrors>({});
  const [requestError, setRequestError] = useState<RequestError | null>(null);
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  function updateField(field: MedicalRecordFormField, value: string) {
    if (field !== "diagnosisName" && field !== "description" && field !== "icdCode") return;
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    setRequestError(null);
    setSuccess(false);
  }

  function focusFirstError(nextErrors: MedicalRecordFieldErrors) {
    const first = Object.keys(nextErrors)[0];
    if (first) window.requestAnimationFrame(() => document.getElementById(first)?.focus());
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const nextErrors = validateDiagnosis(values);
    setErrors(nextErrors);
    setRequestError(null);
    setSuccess(false);
    if (Object.keys(nextErrors).length > 0) {
      focusFirstError(nextErrors);
      return;
    }

    setSubmitting(true);
    try {
      const diagnosis = await medicalRecordApi.addDiagnosis(recordId, toDiagnosisRequest(values));
      onCreated(diagnosis);
      setValues(EMPTY_DIAGNOSIS_FORM);
      setSuccess(true);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      if (cause instanceof ApiRequestError) {
        const serverErrors = mapMedicalRecordFieldErrors(cause.details);
        setErrors(serverErrors);
        if (Object.keys(serverErrors).length > 0) focusFirstError(serverErrors);
        setRequestError({ status: cause.status, message: cause.message, correlationId: cause.correlationId });
      } else {
        setRequestError({
          status: null,
          message: cause instanceof Error ? cause.message : "Không thể thêm chẩn đoán.",
          correlationId: null,
        });
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form ref={formRef} onSubmit={submit} noValidate className="mt-6 space-y-4 rounded-xl border border-border bg-surface p-6">
      <div>
        <h2 className="text-lg font-semibold">Thêm chẩn đoán</h2>
        <p className="mt-1 text-sm text-muted-foreground">Chẩn đoán được ghi trực tiếp vào hồ sơ đang mở.</p>
      </div>
      {success ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">Đã thêm chẩn đoán thành công.</p> : null}
      {requestError ? (
        <MedicalRecordMutationError
          {...requestError}
          onRetry={requestError.status === null || requestError.status >= 500
            ? () => formRef.current?.requestSubmit()
            : undefined}
        />
      ) : null}
      <DiagnosisFormFields values={values} errors={errors} disabled={submitting} onChange={updateField} />
      <button type="submit" disabled={submitting} className="min-h-11 rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50">
        {submitting ? "Đang thêm…" : "Thêm chẩn đoán"}
      </button>
    </form>
  );
}
