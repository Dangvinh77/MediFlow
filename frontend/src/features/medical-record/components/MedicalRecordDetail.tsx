"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { medicalRecordApi } from "../api";
import type { DiagnosisDTO, MedicalRecordDTO, RecordDisposition } from "../types";
import { AddDiagnosisForm } from "./AddDiagnosisForm";

interface MedicalRecordDetailProps {
  recordId: string;
  notice?: "created" | "updated";
}

interface RequestError {
  message: string;
  correlationId: string | null;
}

type DetailState =
  | { key: string; status: "idle" }
  | { key: string; status: "success"; record: MedicalRecordDTO }
  | { key: string; status: "not-found" }
  | { key: string; status: "error"; error: RequestError };

const dispositionLabels: Record<RecordDisposition, string> = {
  OUTPATIENT_FOLLOW_UP: "Theo dõi ngoại trú",
  PRESCRIPTION: "Kê đơn thuốc",
  ADMISSION: "Chuyển nội trú",
  TRANSFER: "Chuyển tuyến/khoa",
  OTHER: "Khác",
};

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể tải chi tiết hồ sơ khám.",
    correlationId: null,
  };
}

function Identifier({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-all font-mono text-sm">{value ?? "—"}</dd>
    </div>
  );
}

const getServerRole = () => null;

export function MedicalRecordDetail({ recordId, notice }: MedicalRecordDetailProps) {
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const [state, setState] = useState<DetailState>({ key: "", status: "idle" });
  const [retryToken, setRetryToken] = useState(0);
  const requestKey = `${recordId}\u0000${retryToken}`;
  const loading = state.key !== requestKey;

  const retry = useCallback(() => setRetryToken((value) => value + 1), []);

  function addCreatedDiagnosis(diagnosis: DiagnosisDTO) {
    setState((current) => current.status === "success"
      ? {
          ...current,
          record: {
            ...current.record,
            diagnoses: [...current.record.diagnoses, diagnosis],
          },
        }
      : current);
  }

  useEffect(() => {
    let active = true;
    medicalRecordApi.getById(recordId)
      .then((record) => {
        if (active) setState({ key: requestKey, status: "success", record });
      })
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }
        if (cause instanceof ApiRequestError && cause.status === 404) {
          setState({ key: requestKey, status: "not-found" });
          return;
        }
        setState({ key: requestKey, status: "error", error: getRequestError(cause) });
      });
    return () => {
      active = false;
    };
  }, [recordId, requestKey, router]);

  if (loading) return <AsyncState kind="loading" message="Đang tải chi tiết hồ sơ khám…" />;

  if (state.status === "not-found") {
    return (
      <section role="status" className="mt-6 rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold">Không tìm thấy hồ sơ khám</h2>
        <p className="mt-2 text-sm text-muted-foreground">Hồ sơ có thể đã bị xóa hoặc mã truy cập không còn hợp lệ.</p>
        <Link href="/records" className="mt-4 inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">Quay lại tra cứu</Link>
      </section>
    );
  }

  if (state.status === "error") {
    return <AsyncState kind="error" message={state.error.message} correlationId={state.error.correlationId} onRetry={retry} />;
  }

  if (state.status !== "success") return <AsyncState kind="empty" message="Không có dữ liệu hồ sơ khám." />;

  const record = state.record;
  const canMutate = role === "ADMIN" || role === "DOCTOR";
  return (
    <section className="mt-6 space-y-6">
      {notice ? (
        <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">
          {notice === "created" ? "Đã tạo hồ sơ khám thành công." : "Đã cập nhật hồ sơ khám thành công."}
        </p>
      ) : null}
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">Mã hồ sơ</p>
          <p className="mt-1 break-all font-mono text-sm">{record.recordId}</p>
        </div>
        <StatusBadge tone={record.status === "COMPLETED" ? "success" : "info"}>
          {record.status === "COMPLETED" ? "Đã hoàn tất" : "Đang khám"}
        </StatusBadge>
      </div>

      <dl className="grid gap-5 rounded-xl border border-border bg-surface p-6 sm:grid-cols-2 lg:grid-cols-3">
        <Identifier label="Bệnh nhân" value={record.patientId} />
        <Identifier label="Bác sĩ" value={record.doctorId} />
        <Identifier label="Khoa" value={record.departmentId} />
        <Identifier label="Lịch hẹn" value={record.appointmentId} />
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Ngày khám</dt><dd className="mt-1 text-sm">{formatLocalDate(record.examinationDate)}</dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Ngày tạo</dt><dd className="mt-1 text-sm">{formatInstant(record.createdAt)}</dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Cập nhật</dt><dd className="mt-1 text-sm">{formatInstant(record.updatedAt)}</dd></div>
        <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Hoàn tất</dt><dd className="mt-1 text-sm">{formatInstant(record.completedAt)}</dd></div>
      </dl>

      <section className="rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold">Nội dung khám</h2>
        <dl className="mt-4 grid gap-5 sm:grid-cols-2">
          <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Triệu chứng</dt><dd className="mt-1 whitespace-pre-wrap text-sm">{record.symptoms ?? "—"}</dd></div>
          <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Hướng xử trí</dt><dd className="mt-1 text-sm">{record.disposition ? dispositionLabels[record.disposition] : "—"}</dd>{record.dispositionNote ? <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">{record.dispositionNote}</p> : null}</div>
        </dl>
      </section>

      <section className="rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold">Chẩn đoán</h2>
        {record.diagnoses.length === 0 ? <p className="mt-3 text-sm text-muted-foreground">Chưa có chẩn đoán.</p> : (
          <ul className="mt-4 space-y-3">
            {record.diagnoses.map((diagnosis) => (
              <li key={diagnosis.diagnosisId} className="rounded-lg border border-border p-4">
                <div className="flex flex-wrap items-center gap-2"><p className="font-medium">{diagnosis.diagnosisName}</p>{diagnosis.icdCode ? <StatusBadge tone="neutral">ICD {diagnosis.icdCode}</StatusBadge> : null}</div>
                {diagnosis.description ? <p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">{diagnosis.description}</p> : null}
              </li>
            ))}
          </ul>
        )}
      </section>

      {canMutate && record.status === "OPEN" ? (
        <AddDiagnosisForm recordId={record.recordId} onCreated={addCreatedDiagnosis} />
      ) : null}

      <div className="flex flex-wrap gap-3">
        {canMutate && record.status === "OPEN" ? (
          <Link href={`/records/${record.recordId}/edit`} className="inline-flex min-h-10 items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90">Sửa triệu chứng</Link>
        ) : null}
        <Link href="/records" className="inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">Quay lại tra cứu</Link>
      </div>
    </section>
  );
}
