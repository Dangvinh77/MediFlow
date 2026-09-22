"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { appointmentApi } from "../api";
import { appointmentStatusPresentation } from "../presentation";
import type { AppointmentDTO } from "../types";

interface AppointmentDetailProps {
  appointmentId: string;
}

interface RequestError {
  message: string;
  correlationId: string | null;
}

type DetailRequestState =
  | { key: string; status: "idle" }
  | { key: string; status: "success"; appointment: AppointmentDTO }
  | { key: string; status: "not-found" }
  | { key: string; status: "error"; error: RequestError };

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể tải chi tiết lịch hẹn.",
    correlationId: null,
  };
}

function Identifier({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-all font-mono text-sm text-foreground">{value}</dd>
    </div>
  );
}

export function AppointmentDetail({ appointmentId }: AppointmentDetailProps) {
  const router = useRouter();
  const [requestState, setRequestState] = useState<DetailRequestState>({
    key: "",
    status: "idle",
  });
  const [retryToken, setRetryToken] = useState(0);
  const requestKey = `${appointmentId}\u0000${retryToken}`;
  const loading = requestState.key !== requestKey;
  const appointment = requestState.key === requestKey && requestState.status === "success"
    ? requestState.appointment
    : null;
  const notFound = requestState.key === requestKey && requestState.status === "not-found";
  const error = requestState.key === requestKey && requestState.status === "error"
    ? requestState.error
    : null;

  const retry = useCallback(() => {
    setRetryToken((value) => value + 1);
  }, []);

  useEffect(() => {
    let active = true;

    appointmentApi.getById(appointmentId)
      .then((result) => {
        if (active) {
          setRequestState({ key: requestKey, status: "success", appointment: result });
        }
      })
      .catch((cause: unknown) => {
        if (!active) return;
        if (
          cause instanceof ApiRequestError
          && (cause.status === 401 || cause.status === 403)
        ) {
          router.replace("/login");
          return;
        }
        if (cause instanceof ApiRequestError && cause.status === 404) {
          setRequestState({ key: requestKey, status: "not-found" });
          return;
        }
        setRequestState({ key: requestKey, status: "error", error: getRequestError(cause) });
      });

    return () => {
      active = false;
    };
  }, [appointmentId, requestKey, router]);

  if (loading) {
    return <AsyncState kind="loading" message="Đang tải chi tiết lịch hẹn…" />;
  }

  if (notFound) {
    return (
      <section role="status" className="mt-6 rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold">Không tìm thấy lịch hẹn</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Lịch hẹn có thể đã bị xóa hoặc mã truy cập không còn hợp lệ.
        </p>
        <Link href="/appointments" className="mt-4 inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">
          Quay lại danh sách
        </Link>
      </section>
    );
  }

  if (error) {
    return (
      <AsyncState
        kind="error"
        message={error.message}
        correlationId={error.correlationId}
        onRetry={retry}
      />
    );
  }

  if (!appointment) {
    return <AsyncState kind="empty" message="Không có dữ liệu lịch hẹn." />;
  }

  const status = appointmentStatusPresentation[appointment.status] ?? {
    label: "Không xác định",
    tone: "neutral" as const,
  };

  return (
    <section className="mt-6 space-y-6">
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-sm text-muted-foreground">Mã lịch hẹn</p>
          <p className="mt-1 break-all font-mono text-sm">{appointment.appointmentId}</p>
        </div>
        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
      </div>

      <dl className="grid gap-5 rounded-xl border border-border bg-surface p-6 sm:grid-cols-2 lg:grid-cols-3">
        <Identifier label="Bệnh nhân" value={appointment.patientId} />
        <Identifier label="Bác sĩ" value={appointment.doctorId} />
        <Identifier label="Khoa" value={appointment.departmentId} />
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Ngày hẹn</dt>
          <dd className="mt-1 text-sm">{formatLocalDate(appointment.appointmentDate)}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Giờ hẹn</dt>
          <dd className="mt-1 text-sm">{appointment.appointmentTime}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Lý do</dt>
          <dd className="mt-1 whitespace-pre-wrap text-sm">{appointment.reason ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Ngày tạo</dt>
          <dd className="mt-1 text-sm">{formatInstant(appointment.createdAt)}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Cập nhật</dt>
          <dd className="mt-1 text-sm">{formatInstant(appointment.updatedAt)}</dd>
        </div>
      </dl>

      <Link href="/appointments" className="inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">
        Quay lại danh sách
      </Link>
    </section>
  );
}
