"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { appointmentApi } from "../api";
import {
  EMPTY_APPOINTMENT_FORM,
  mapAppointmentFieldErrors,
  toUpdateAppointmentRequest,
  validateAppointmentForm,
  type AppointmentFieldErrors,
  type AppointmentFormField,
  type AppointmentFormValues,
} from "../form";
import type { AppointmentDTO } from "../types";
import { AppointmentFormFields } from "./AppointmentFormFields";
import { AppointmentMutationError } from "./AppointmentMutationError";

interface EditAppointmentFormProps {
  appointmentId: string;
}

interface RequestError {
  message: string;
  correlationId: string | null;
  retryable: boolean;
}

type LoadState =
  | { status: "loading" }
  | { status: "success"; appointment: AppointmentDTO }
  | { status: "not-found" }
  | { status: "error"; error: RequestError };

export function EditAppointmentForm({ appointmentId }: EditAppointmentFormProps) {
  const router = useRouter();
  const [retryToken, setRetryToken] = useState(0);
  const [loadState, setLoadState] = useState<LoadState>({ status: "loading" });
  const [values, setValues] = useState<AppointmentFormValues>(EMPTY_APPOINTMENT_FORM);
  const [fieldErrors, setFieldErrors] = useState<AppointmentFieldErrors>({});
  const [requestError, setRequestError] = useState<Omit<RequestError, "retryable"> | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let active = true;

    appointmentApi.getById(appointmentId)
      .then((appointment) => {
        if (!active) return;
        setValues({
          patientId: appointment.patientId,
          doctorId: appointment.doctorId,
          departmentId: appointment.departmentId,
          appointmentDate: appointment.appointmentDate,
          appointmentTime: appointment.appointmentTime,
          reason: appointment.reason ?? "",
        });
        setLoadState({ status: "success", appointment });
      })
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }
        if (cause instanceof ApiRequestError && cause.status === 404) {
          setLoadState({ status: "not-found" });
          return;
        }
        setLoadState({
          status: "error",
          error: {
            message: cause instanceof ApiRequestError
              ? cause.message
              : cause instanceof Error
                ? cause.message
                : "Không thể tải lịch hẹn để chỉnh sửa.",
            correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
            retryable: !(cause instanceof ApiRequestError && cause.status === 403),
          },
        });
      });

    return () => {
      active = false;
    };
  }, [appointmentId, retryToken, router]);

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
    if (submitting || loadState.status !== "success") return;

    setRequestError(null);
    const errors = validateAppointmentForm(values, false);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      focusFirstError(errors);
      return;
    }

    setSubmitting(true);
    try {
      const updated = await appointmentApi.update(
        appointmentId,
        toUpdateAppointmentRequest(values),
      );
      router.replace(`/appointments/${updated.appointmentId}`);
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
          message: cause instanceof Error ? cause.message : "Không thể cập nhật lịch hẹn.",
          correlationId: null,
        });
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (loadState.status === "loading") {
    return <AsyncState kind="loading" message="Đang tải lịch hẹn để chỉnh sửa…" />;
  }

  if (loadState.status === "not-found") {
    return (
      <section role="status" className="mt-6 rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold">Không tìm thấy lịch hẹn</h2>
        <Link href="/appointments" className="mt-4 inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">
          Quay lại danh sách
        </Link>
      </section>
    );
  }

  if (loadState.status === "error") {
    return (
      <div className="mt-6 space-y-3">
        <AppointmentMutationError
          message={loadState.error.message}
          correlationId={loadState.error.correlationId}
        />
        {loadState.error.retryable ? (
          <button
            type="button"
            onClick={() => {
              setLoadState({ status: "loading" });
              setRetryToken((value) => value + 1);
            }}
            className="min-h-10 rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted"
          >
            Thử lại
          </button>
        ) : null}
      </div>
    );
  }

  if (loadState.appointment.status !== "PENDING") {
    return (
      <section role="status" className="mt-6 rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold">Không thể chỉnh sửa lịch hẹn</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Chỉ lịch hẹn đang chờ tiếp nhận mới được đổi ngày, giờ hoặc lý do.
        </p>
        <Link href={`/appointments/${appointmentId}`} className="mt-4 inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">
          Xem chi tiết
        </Link>
      </section>
    );
  }

  return (
    <form onSubmit={submit} noValidate className="mt-6 space-y-6 rounded-xl border border-border bg-surface p-6">
      {requestError ? <AppointmentMutationError {...requestError} /> : null}
      <dl className="grid gap-4 rounded-lg bg-surface-muted p-4 text-sm sm:grid-cols-3">
        <Identifier label="Bệnh nhân" value={loadState.appointment.patientId} />
        <Identifier label="Bác sĩ" value={loadState.appointment.doctorId} />
        <Identifier label="Khoa" value={loadState.appointment.departmentId} />
      </dl>
      <AppointmentFormFields
        values={values}
        errors={fieldErrors}
        includeReferences={false}
        disabled={submitting}
        onChange={updateField}
      />
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="submit"
          disabled={submitting}
          className="min-h-11 rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {submitting ? "Đang lưu…" : "Lưu thay đổi"}
        </button>
        <Link href={`/appointments/${appointmentId}`} className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">
          Hủy
        </Link>
      </div>
    </form>
  );
}

function Identifier({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-all font-mono text-xs">{value}</dd>
    </div>
  );
}
