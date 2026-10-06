"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { medicalRecordApi } from "../api";
import {
  mapMedicalRecordFieldErrors,
  toUpdateMedicalRecordRequest,
  validateRecordUpdate,
  type MedicalRecordFieldErrors,
} from "../form";
import type { MedicalRecordDTO } from "../types";
import { inputClass } from "./DiagnosisFormFields";
import { MedicalRecordMutationError } from "./MedicalRecordMutationError";

interface EditMedicalRecordFormProps {
  recordId: string;
}

interface RequestError {
  status: number | null;
  message: string;
  correlationId: string | null;
}

type LoadState =
  | { status: "loading" }
  | { status: "success"; record: MedicalRecordDTO }
  | { status: "not-found" }
  | { status: "forbidden"; error: RequestError }
  | { status: "error"; error: RequestError };

export function EditMedicalRecordForm({ recordId }: EditMedicalRecordFormProps) {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const [retryToken, setRetryToken] = useState(0);
  const [loadState, setLoadState] = useState<LoadState>({ status: "loading" });
  const [symptoms, setSymptoms] = useState("");
  const [errors, setErrors] = useState<MedicalRecordFieldErrors>({});
  const [requestError, setRequestError] = useState<RequestError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let active = true;
    medicalRecordApi.getById(recordId)
      .then((record) => {
        if (!active) return;
        setSymptoms(record.symptoms ?? "");
        setLoadState({ status: "success", record });
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
        const error = {
          status: cause instanceof ApiRequestError ? cause.status : null,
          message: cause instanceof ApiRequestError
            ? cause.message
            : cause instanceof Error ? cause.message : "Không thể tải hồ sơ để chỉnh sửa.",
          correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
        };
        setLoadState(cause instanceof ApiRequestError && cause.status === 403
          ? { status: "forbidden", error }
          : { status: "error", error });
      });
    return () => { active = false; };
  }, [recordId, retryToken, router]);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting || loadState.status !== "success") return;
    const nextErrors = validateRecordUpdate(symptoms);
    setErrors(nextErrors);
    setRequestError(null);
    if (Object.keys(nextErrors).length > 0) {
      window.requestAnimationFrame(() => document.getElementById("symptoms")?.focus());
      return;
    }

    setSubmitting(true);
    try {
      const updated = await medicalRecordApi.update(recordId, toUpdateMedicalRecordRequest(symptoms));
      router.replace(`/records/${updated.recordId}?notice=updated`);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      if (cause instanceof ApiRequestError) {
        const serverErrors = mapMedicalRecordFieldErrors(cause.details);
        setErrors(serverErrors);
        setRequestError({ status: cause.status, message: cause.message, correlationId: cause.correlationId });
      } else {
        setRequestError({ status: null, message: cause instanceof Error ? cause.message : "Không thể cập nhật hồ sơ.", correlationId: null });
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (loadState.status === "loading") return <AsyncState kind="loading" message="Đang tải hồ sơ để chỉnh sửa…" />;
  if (loadState.status === "not-found") {
    return <section role="status" className="mt-6 rounded-xl border border-border bg-surface p-6"><h2 className="text-lg font-semibold">Không tìm thấy hồ sơ khám</h2><Link href="/records" className="mt-4 inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">Quay lại tra cứu</Link></section>;
  }
  if (loadState.status === "forbidden") return <div className="mt-6"><MedicalRecordMutationError {...loadState.error} /></div>;
  if (loadState.status === "error") {
    return <div className="mt-6"><MedicalRecordMutationError {...loadState.error} onRetry={() => { setLoadState({ status: "loading" }); setRetryToken((value) => value + 1); }} /></div>;
  }
  if (loadState.record.status === "COMPLETED") {
    return <section role="status" className="mt-6 rounded-xl border border-border bg-surface p-6"><h2 className="text-lg font-semibold">Hồ sơ đã hoàn tất</h2><p className="mt-2 text-sm text-muted-foreground">Hồ sơ hoàn tất là bất biến và không thể sửa triệu chứng.</p><Link href={`/records/${recordId}`} className="mt-4 inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">Xem chi tiết</Link></section>;
  }

  return (
    <form ref={formRef} onSubmit={submit} noValidate className="mt-6 space-y-6 rounded-xl border border-border bg-surface p-6">
      {requestError ? <MedicalRecordMutationError {...requestError} onRetry={requestError.status === null || requestError.status >= 500 ? () => formRef.current?.requestSubmit() : undefined} /> : null}
      <dl className="grid gap-4 rounded-lg bg-surface-muted p-4 text-sm sm:grid-cols-3">
        <Identifier label="Bệnh nhân" value={loadState.record.patientId} />
        <Identifier label="Bác sĩ" value={loadState.record.doctorId} />
        <Identifier label="Khoa" value={loadState.record.departmentId} />
      </dl>
      <div className="flex flex-col gap-1">
        <label htmlFor="symptoms" className="text-sm font-medium">Triệu chứng</label>
        <textarea id="symptoms" value={symptoms} maxLength={4000} rows={6} disabled={submitting} aria-invalid={errors.symptoms ? true : undefined} aria-describedby={errors.symptoms ? "symptoms-error" : undefined} onChange={(event) => { setSymptoms(event.target.value); setErrors({}); setRequestError(null); }} className={`${inputClass(Boolean(errors.symptoms))} resize-y`} />
        {errors.symptoms ? <p id="symptoms-error" className="text-xs text-danger">{errors.symptoms}</p> : <p className="text-xs text-muted-foreground">{symptoms.length}/4000 ký tự</p>}
      </div>
      <div className="flex flex-wrap gap-3">
        <button type="submit" disabled={submitting} className="min-h-11 rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50">{submitting ? "Đang lưu…" : "Lưu thay đổi"}</button>
        <Link href={`/records/${recordId}`} className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">Hủy</Link>
      </div>
    </form>
  );
}

function Identifier({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-all font-mono text-xs">{value}</dd></div>;
}
