"use client";

import Link from "next/link";
import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { formatInstant, formatLocalDate } from "@/lib/format";
import type { Role } from "@/lib/roles";
import { appointmentApi } from "../api";
import { appointmentStatusPresentation } from "../presentation";
import type { AppointmentDTO, LegacyAppointmentStatus } from "../types";
import { AppointmentMutationError } from "./AppointmentMutationError";

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

const actionRoles: readonly Role[] = ["ADMIN", "DOCTOR", "NURSE"];
const getServerRole = (): Role | null => null;

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
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const [requestState, setRequestState] = useState<DetailRequestState>({
    key: "",
    status: "idle",
  });
  const [retryToken, setRetryToken] = useState(0);
  const [proposedStatus, setProposedStatus] = useState<LegacyAppointmentStatus | null>(null);
  const [changingStatus, setChangingStatus] = useState(false);
  const [actionError, setActionError] = useState<RequestError | null>(null);
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
        if (cause instanceof ApiRequestError && cause.status === 401) {
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

  async function changeStatus(status: LegacyAppointmentStatus) {
    if (!appointment || appointment.status !== "PENDING" || changingStatus) return;

    setChangingStatus(true);
    setActionError(null);
    try {
      const updated = await appointmentApi.changeStatus(appointmentId, { status });
      setRequestState({ key: requestKey, status: "success", appointment: updated });
      setProposedStatus(null);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      setActionError(getRequestError(cause));
    } finally {
      setChangingStatus(false);
    }
  }

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

  const status = appointmentStatusPresentation[appointment.status];
  const canAct = role !== null && actionRoles.includes(role) && appointment.status === "PENDING";
  const hasLifecycleInformation = appointment.careContractVersion > 0
    || appointment.checkedInAt !== null
    || appointment.examClearanceAt !== null
    || appointment.examinationStartedAt !== null
    || appointment.completedAt !== null;

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

      {hasLifecycleInformation ? (
        <section className="rounded-xl border border-border bg-surface p-6">
          <h2 className="text-lg font-semibold">Tiến trình khám</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Các mốc nghiệp vụ do quy trình tiếp nhận, tài chính và khám bệnh ghi nhận.
          </p>
          <dl className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            <LifecycleTimestamp label="Tiếp nhận" value={appointment.checkedInAt} />
            <LifecycleTimestamp label="Đủ điều kiện khám" value={appointment.examClearanceAt} />
            <LifecycleTimestamp label="Bắt đầu khám" value={appointment.examinationStartedAt} />
            <LifecycleTimestamp label="Hoàn tất" value={appointment.completedAt} />
          </dl>
        </section>
      ) : null}

      {actionError ? <AppointmentMutationError {...actionError} /> : null}

      {canAct ? (
        <section className="rounded-xl border border-border bg-surface p-6">
          <h2 className="text-lg font-semibold">Thao tác lịch hẹn</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Chỉ lịch hẹn đang chờ tiếp nhận mới có thể được chỉnh sửa, ghi nhận đã đến hoặc hủy.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            <Link href={`/appointments/${appointment.appointmentId}/edit`} className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">
              Chỉnh sửa
            </Link>
            <button type="button" disabled={changingStatus} onClick={() => setProposedStatus("ARRIVED")} className="min-h-11 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50">
              Ghi nhận đã đến
            </button>
            <button type="button" disabled={changingStatus} onClick={() => setProposedStatus("CANCELLED")} className="min-h-11 rounded-lg border border-danger/40 px-4 py-2 text-sm font-medium text-danger hover:bg-danger/10 disabled:cursor-not-allowed disabled:opacity-50">
              Hủy lịch hẹn
            </button>
          </div>

          {proposedStatus ? (
            <div role="alertdialog" aria-labelledby="appointment-status-confirm-title" className="mt-4 rounded-lg border border-warning/40 bg-warning/10 p-4">
              <h3 id="appointment-status-confirm-title" className="font-semibold">
                {proposedStatus === "ARRIVED" ? "Xác nhận bệnh nhân đã đến?" : "Xác nhận hủy lịch hẹn?"}
              </h3>
              <p className="mt-1 text-sm text-muted-foreground">
                Thao tác này thay đổi trạng thái và không thể hoàn tác bằng màn hình hiện tại.
              </p>
              <div className="mt-3 flex flex-wrap gap-3">
                <button type="button" disabled={changingStatus} onClick={() => void changeStatus(proposedStatus)} className="min-h-10 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50">
                  {changingStatus ? "Đang cập nhật…" : "Xác nhận"}
                </button>
                <button type="button" disabled={changingStatus} onClick={() => setProposedStatus(null)} className="min-h-10 rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50">
                  Quay lại
                </button>
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

      <Link href="/appointments" className="inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted">
        Quay lại danh sách
      </Link>
    </section>
  );
}

function LifecycleTimestamp({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className="mt-1 text-sm">{formatInstant(value)}</dd>
    </div>
  );
}
