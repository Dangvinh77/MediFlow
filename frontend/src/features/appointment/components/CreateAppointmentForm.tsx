"use client";

import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { appointmentApi } from "../api";
import {
  EMPTY_APPOINTMENT_FORM,
  mapAppointmentFieldErrors,
  toCreateAppointmentRequest,
  validateAppointmentForm,
  type AppointmentFieldErrors,
  type AppointmentFormField,
  type AppointmentFormValues,
} from "../form";
import { AppointmentFormFields } from "./AppointmentFormFields";
import { AppointmentMutationError } from "./AppointmentMutationError";

interface RequestError {
  message: string;
  correlationId: string | null;
}

export function CreateAppointmentForm() {
  const router = useRouter();
  const [values, setValues] = useState<AppointmentFormValues>(EMPTY_APPOINTMENT_FORM);
  const [fieldErrors, setFieldErrors] = useState<AppointmentFieldErrors>({});
  const [requestError, setRequestError] = useState<RequestError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function updateField(field: AppointmentFormField, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setRequestError(null);
  }

  function focusFirstError(errors: AppointmentFieldErrors) {
    const firstField = Object.keys(errors)[0] as AppointmentFormField | undefined;
    if (!firstField) return;
    window.requestAnimationFrame(() => document.getElementById(firstField)?.focus());
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    setRequestError(null);
    const errors = validateAppointmentForm(values, true);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      focusFirstError(errors);
      return;
    }

    setSubmitting(true);
    try {
      const created = await appointmentApi.create(toCreateAppointmentRequest(values));
      router.replace(`/appointments/${created.appointmentId}`);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      if (cause instanceof ApiRequestError) {
        const serverErrors = mapAppointmentFieldErrors(cause.details);
        if (Object.keys(serverErrors).length > 0) {
          setFieldErrors(serverErrors);
          focusFirstError(serverErrors);
        }
        setRequestError({
          message: cause.message,
          correlationId: cause.correlationId,
        });
      } else {
        setRequestError({
          message: cause instanceof Error ? cause.message : "Không thể tạo lịch hẹn.",
          correlationId: null,
        });
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate className="mt-6 space-y-6 rounded-xl border border-border bg-surface p-6">
      {requestError ? <AppointmentMutationError {...requestError} /> : null}
      <AppointmentFormFields
        values={values}
        errors={fieldErrors}
        includeReferences
        disabled={submitting}
        onChange={updateField}
      />
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="submit"
          disabled={submitting}
          className="min-h-11 rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {submitting ? "Đang tạo…" : "Tạo lịch hẹn"}
        </button>
        <Link href="/appointments" className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">
          Hủy
        </Link>
        <p className="text-sm text-muted-foreground">Các trường có dấu * là bắt buộc.</p>
      </div>
    </form>
  );
}
