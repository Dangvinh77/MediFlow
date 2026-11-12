"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";


import { useEffect, useRef, useState } from "react";
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
  disabled?: boolean;
  isInteractionBlocked?: () => boolean;
  onActivityChange?: (active: boolean) => void;
  onBusyChange?: (busy: boolean) => void;
}

interface RequestError {
  status: number | null;
  message: string;
  correlationId: string | null;
}

export function AddDiagnosisForm({ recordId, onCreated, disabled = false, isInteractionBlocked, onActivityChange, onBusyChange }: AddDiagnosisFormProps) {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const active = useRef(false);
  const submissionLocked = useRef(false);
  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);
  const [values, setValues] = useState<DiagnosisFormValues>(EMPTY_DIAGNOSIS_FORM);
  const [errors, setErrors] = useState<MedicalRecordFieldErrors>({});
  const [requestError, setRequestError] = useState<RequestError | null>(null);
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const draft = useRef(false);

  function updateField(field: MedicalRecordFormField, value: string) {
    if (field !== "diagnosisName" && field !== "description" && field !== "icdCode") return;
    setValues((current) => ({ ...current, [field]: value }));
    draft.current = true;
    onActivityChange?.(true);
    setErrors((current) => ({ ...current, [field]: undefined }));
    setRequestError(null);
    setSuccess(false);
  }

  function focusFirstError(nextErrors: MedicalRecordFieldErrors) {
    const first = Object.keys(nextErrors)[0];
    if (first) window.requestAnimationFrame(() => {
      if (active.current) document.getElementById(first)?.focus();
    });
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled || isInteractionBlocked?.() || submitting || submissionLocked.current) return;
    const nextErrors = validateDiagnosis(values);
    setErrors(nextErrors);
    setRequestError(null);
    setSuccess(false);
    if (Object.keys(nextErrors).length > 0) {
      focusFirstError(nextErrors);
      return;
    }

    submissionLocked.current = true;
    setSubmitting(true);
    onBusyChange?.(true);
    onActivityChange?.(true);
    try {
      const diagnosis = await medicalRecordApi.addDiagnosis(recordId, toDiagnosisRequest(values));
      if (!active.current) return;
      onCreated(diagnosis);
      setValues(EMPTY_DIAGNOSIS_FORM);
      draft.current = false;
      onActivityChange?.(false);
      setSuccess(true);
    } catch (cause: unknown) {
      if (!active.current) return;
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
      onBusyChange?.(false);
      if (active.current) {
        submissionLocked.current = false;
        setSubmitting(false);
        if (!draft.current) onActivityChange?.(false);
      }
    }
  }

  return (
    <form ref={formRef} onSubmit={submit} noValidate className="mt-4 space-y-4">
      <div>
        <p className="mt-1 text-sm text-muted-foreground"><L10n>{"Chẩn đoán được ghi trực tiếp vào hồ sơ đang mở."}</L10n></p>
      </div>
      <L10nValue>{success ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success"><L10n>{"Đã thêm chẩn đoán thành công."}</L10n></p> : null}</L10nValue>
      <L10nValue>{requestError ? (
        <MedicalRecordMutationError
          {...requestError}
          disabled={disabled || submitting}
          onRetry={requestError.status === null || requestError.status >= 500
            ? () => formRef.current?.requestSubmit()
            : undefined}
        />
      ) : null}</L10nValue>
      <DiagnosisFormFields values={values} errors={errors} disabled={submitting || disabled} onChange={updateField} />
      <button type="submit" disabled={submitting || disabled} className="min-h-11 rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50">
        <L10nValue>{submitting ? "Đang thêm…" : "Thêm chẩn đoán"}</L10nValue>
      </button>
    </form>
  );
}
