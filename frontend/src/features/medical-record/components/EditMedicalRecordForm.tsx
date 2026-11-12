"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import Link from "next/link";
import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { prepareCommandIntent } from "@/lib/command-intent";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import { medicalRecordApi } from "../api";
import {
  mapMedicalRecordFieldErrors,
  validateRecordUpdate,
  type MedicalRecordFieldErrors,
} from "../form";
import type { MedicalRecordDTO, UpdateMedicalRecordRequest } from "../types";
import type { ClinicalContentValues } from "./ClinicalContentFields";
import { ClinicalContentFields, clinicalContentSections } from "./ClinicalContentFields";
import { inputClass } from "./DiagnosisFormFields";
import { MedicalRecordMutationError } from "./MedicalRecordMutationError";

interface EditMedicalRecordFormProps {
  recordId: string;
}

interface RequestError {
  status: number | null;
  message: string;
  correlationId: string | null;
  uncertain?: boolean;
}

function initialContent(record: MedicalRecordDTO): ClinicalContentValues {
  return {
    chiefComplaint: record.chiefComplaint ?? "",
    presentIllness: record.presentIllness ?? "",
    pastHistory: record.pastHistory ?? "",
    allergyNote: record.allergyNote ?? "",
    physicalExam: record.physicalExam ?? "",
    advice: record.advice ?? "",
  };
}

function isUncertainOutcome(cause: unknown): boolean {
  return !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500;
}

type LoadState =
  | { status: "loading" }
  | { status: "success"; record: MedicalRecordDTO }
  | { status: "not-found" }
  | { status: "forbidden"; error: RequestError }
  | { status: "error"; error: RequestError };

export function EditMedicalRecordForm({ recordId }: EditMedicalRecordFormProps) {
  const sessionKey = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <EditMedicalRecordFormContent key={`${recordId}:${sessionKey ?? "anonymous"}`} recordId={recordId} />;
}

function EditMedicalRecordFormContent({ recordId }: EditMedicalRecordFormProps) {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const active = useRef(true);
  const submissionLocked = useRef(false);
  const uncertainOutcome = useRef(false);
  const [retryToken, setRetryToken] = useState(0);
  const [loadState, setLoadState] = useState<LoadState>({ status: "loading" });
  const [symptoms, setSymptoms] = useState("");
  const [content, setContent] = useState<ClinicalContentValues>({
    chiefComplaint: "", presentIllness: "", pastHistory: "", allergyNote: "", physicalExam: "", advice: "",
  });
  const [errors, setErrors] = useState<MedicalRecordFieldErrors>({});
  const [requestError, setRequestError] = useState<RequestError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);

  useEffect(() => {
    let active = true;
    medicalRecordApi.getById(recordId)
      .then((record) => {
        if (!active) return;
        setSymptoms(record.symptoms ?? "");
        setContent(initialContent(record));
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
    if (submitting || (submissionLocked.current && !uncertainOutcome.current) || loadState.status !== "success") return;
    const nextErrors = validateRecordUpdate(symptoms);
    setErrors(nextErrors);
    setRequestError(null);
    if (Object.keys(nextErrors).length > 0) {
      window.requestAnimationFrame(() => document.getElementById("symptoms")?.focus());
      return;
    }

    const invalidContent = clinicalContentSections.find(([field]) => content[field].length > 4000);
    if (invalidContent) {
      setRequestError({ status: 422, message: `${invalidContent[1]} không được dài quá 4000 ký tự.`, correlationId: null });
      return;
    }

    const body: UpdateMedicalRecordRequest = {};
    if (symptoms !== (loadState.record.symptoms ?? "")) body.symptoms = symptoms.trim();
    for (const [field] of clinicalContentSections) {
      if (content[field] !== (loadState.record[field] ?? "")) body[field] = content[field];
    }
    if (Object.keys(body).length === 0) {
      setRequestError({ status: null, message: "Chưa có thay đổi để lưu.", correlationId: null });
      return;
    }

    submissionLocked.current = true;
    uncertainOutcome.current = false;
    setSubmitting(true);
    let leavingPage = false;
    try {
      const commandSession = getSessionKey();
      const intent = await prepareCommandIntent(`update-record:${recordId}`, commandSession, body);
      if (!active.current || getSessionKey() !== commandSession) {
        intent.cancelUnsent();
        return;
      }
      let updated: MedicalRecordDTO;
      try {
        updated = await medicalRecordApi.update(recordId, body, { idempotencyKey: intent.key, correlationId: intent.correlationId });
        intent.settle();
      } catch (cause) {
        intent.settle(cause);
        throw cause;
      }
      if (!active.current) return;
      leavingPage = true;
      router.replace(`/records/${updated.recordId}?notice=updated`);
    } catch (cause: unknown) {
      if (!active.current) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        leavingPage = true;
        router.replace("/login");
        return;
      }
      uncertainOutcome.current = isUncertainOutcome(cause);
      if (cause instanceof ApiRequestError) {
        const serverErrors = mapMedicalRecordFieldErrors(cause.details);
        setErrors(serverErrors);
        setRequestError({ status: cause.status, message: cause.message, correlationId: cause.correlationId, uncertain: uncertainOutcome.current });
      } else {
        setRequestError({ status: null, message: cause instanceof Error ? cause.message : "Không thể cập nhật hồ sơ.", correlationId: null, uncertain: uncertainOutcome.current });
      }
    } finally {
      if (active.current) {
        if (!leavingPage && !uncertainOutcome.current) submissionLocked.current = false;
        setSubmitting(leavingPage);
      }
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
  if (loadState.record.status !== "OPEN") {
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
        <textarea id="symptoms" value={symptoms} maxLength={4000} rows={6} disabled={submitting || requestError?.uncertain === true} aria-invalid={errors.symptoms ? true : undefined} aria-describedby={errors.symptoms ? "symptoms-error" : undefined} onChange={(event) => { setSymptoms(event.target.value); setErrors({}); setRequestError(null); }} className={`${inputClass(Boolean(errors.symptoms))} resize-y`} />
        {errors.symptoms ? <p id="symptoms-error" className="text-xs text-danger">{errors.symptoms}</p> : <p className="text-xs text-muted-foreground">{symptoms.length}/4000 ký tự</p>}
      </div>
      <ClinicalContentFields
        value={content}
        disabled={submitting || requestError?.uncertain === true}
        onChange={(field, next) => { setContent((current) => ({ ...current, [field]: next })); setErrors({}); setRequestError(null); }}
      />
      <div className="flex flex-wrap gap-3">
        <button type="submit" disabled={submitting || (requestError?.uncertain !== true && Boolean(Object.keys(errors).length))} className="min-h-11 rounded-lg bg-primary px-5 py-2.5 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50">{submitting ? "Đang lưu…" : requestError?.uncertain ? "Gửi lại cùng nội dung" : "Lưu thay đổi"}</button>
        <Link href={`/records/${recordId}`} className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">Hủy</Link>
      </div>
    </form>
  );
}

function Identifier({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 break-all text-sm"><ReferenceText kind={label === "Bệnh nhân" ? "patient" : label === "Bác sĩ" ? "doctor" : label.startsWith("Khoa") ? "department" : undefined} value={value} fallback={label} /></dd></div>;
}
