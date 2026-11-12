"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { ReferenceText } from "@/components/references/ReferenceText";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { formatInstant, formatLocalDate } from "@/lib/format";
import type { Role } from "@/lib/roles";
import { appointmentApi } from "../api";
import { getAppointmentStatusPresentation } from "../presentation";
import type { AppointmentDTO, LegacyAppointmentStatus } from "../types";
import { AppointmentMutationError } from "./AppointmentMutationError";
import { OutpatientExamActions } from "./OutpatientExamActions";

interface AppointmentDetailProps {
  appointmentId: string;
  outpatientEnabled?: boolean;
  clinicIntakeEnabled?: boolean;
  renderRecordLink?: (appointment: AppointmentDTO) => ReactNode;
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
const getServerToken = (): string | null => null;

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
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10nValue>{label}</L10nValue></dt>
      <dd className="mt-1 break-all font-mono text-sm text-foreground"><ReferenceText kind={label === "Bệnh nhân" ? "patient" : label === "Bác sĩ" ? "doctor" : label.startsWith("Khoa") ? "department" : undefined} value={value} fallback={label} /></dd>
    </div>
  );
}

export function AppointmentDetail(props: AppointmentDetailProps) {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const token = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, getServerToken);
  return <AppointmentDetailContent key={`${props.appointmentId}:${role ?? "anonymous"}:${token ?? "anonymous"}`} {...props} role={role} />;
}

function AppointmentDetailContent({ appointmentId, outpatientEnabled = false, clinicIntakeEnabled = false, renderRecordLink, role }: AppointmentDetailProps & { role: Role | null }) {
  const { localize } = useLocale();
  const router = useRouter();
  const active = useRef(false);
  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);
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
      if (!active.current) return;
      setRequestState({ key: requestKey, status: "success", appointment: updated });
      setProposedStatus(null);
    } catch (cause: unknown) {
      if (!active.current) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      setActionError(getRequestError(cause));
    } finally {
      if (active.current) setChangingStatus(false);
    }
  }

  if (loading) {
    return <AsyncState kind="loading" message={localize("Đang tải chi tiết lịch hẹn…")} />;
  }

  if (notFound) {
    return (
      <section role="status" className="mt-6 rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold"><L10n>{"Không tìm thấy lịch hẹn"}</L10n></h2>
        <p className="mt-2 text-sm text-muted-foreground"><L10n>{"Lịch hẹn có thể đã bị xóa hoặc mã truy cập không còn hợp lệ."}</L10n></p>
        <Link href="/appointments" className="mt-4 inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted"><L10n>{"Quay lại danh sách"}</L10n></Link>
      </section>
    );
  }

  if (error) {
    return (
      <AsyncState
        kind="error"
        message={localize(error.message)}
        correlationId={error.correlationId}
        onRetry={retry}
      />
    );
  }

  if (!appointment) {
    return <AsyncState kind="empty" message={localize("Không có dữ liệu lịch hẹn.")} />;
  }

  const status = getAppointmentStatusPresentation(appointment);
  const canAct = role !== null && actionRoles.includes(role) && appointment.status === "PENDING";
  const hasLifecycleInformation = appointment.careContractVersion > 0
    || appointment.checkedInAt !== null
    || appointment.examClearanceAt !== null
    || appointment.examinationStartedAt !== null
    || appointment.completedAt !== null;

  return (
    <section className="mt-6 space-y-6">
      <section aria-label={localize("Ngữ cảnh lượt khám")} className="sticky top-18 z-20 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-surface p-3 shadow-sm">
        <div className="min-w-0 text-sm"><p className="font-semibold"><ReferenceText kind="patient" value={appointment.patientId} fallback="Bệnh nhân" /></p><p className="mt-1 text-muted-foreground"><L10nValue>{formatLocalDate(appointment.appointmentDate)}</L10nValue> · {appointment.appointmentTime} · <ReferenceText kind="department" value={appointment.departmentId} fallback="Khoa" /></p></div>
        <L10nValue>{outpatientEnabled && (appointment.status === "IN_EXAM" || appointment.status === "COMPLETED") ? renderRecordLink?.(appointment) : null}</L10nValue>
      </section>
      <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="text-sm text-muted-foreground"><L10n>{"Phiếu khám"}</L10n></p>
          <p className="mt-1 break-all font-mono text-sm"><ReferenceText kind="appointment" value={appointment.appointmentId} fallback="Lịch hẹn" /></p>
        </div>
        <StatusBadge tone={status.tone}><L10nValue>{status.label}</L10nValue></StatusBadge>
      </div>

      <dl className="grid gap-5 rounded-xl border border-border bg-surface p-6 sm:grid-cols-2 lg:grid-cols-3">
        <Identifier label={localize("Bệnh nhân")} value={appointment.patientId} />
        <Identifier label={localize("Bác sĩ")} value={appointment.doctorId} />
        <Identifier label="Khoa" value={appointment.departmentId} />
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Ngày hẹn"}</L10n></dt>
          <dd className="mt-1 text-sm"><L10nValue>{formatLocalDate(appointment.appointmentDate)}</L10nValue></dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Giờ hẹn"}</L10n></dt>
          <dd className="mt-1 text-sm">{appointment.appointmentTime}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Lý do"}</L10n></dt>
          <dd className="mt-1 whitespace-pre-wrap text-sm"><L10nValue>{appointment.reason ?? "—"}</L10nValue></dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Ngày tạo"}</L10n></dt>
          <dd className="mt-1 text-sm"><L10nValue>{formatInstant(appointment.createdAt)}</L10nValue></dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Cập nhật"}</L10n></dt>
          <dd className="mt-1 text-sm"><L10nValue>{formatInstant(appointment.updatedAt)}</L10nValue></dd>
        </div>
      </dl>

      <L10nValue>{hasLifecycleInformation ? (
        <section className="rounded-xl border border-border bg-surface p-6">
          <h2 className="text-lg font-semibold"><L10n>{"Tiến trình khám"}</L10n></h2>
          <p className="mt-1 text-sm text-muted-foreground"><L10n>{"Các mốc nghiệp vụ do quy trình tiếp nhận, tài chính và khám bệnh ghi nhận."}</L10n></p>
          <dl className="mt-5 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            <LifecycleTimestamp label={localize("Tiếp nhận")} value={appointment.checkedInAt} />
            <LifecycleTimestamp label={localize("Xác nhận thanh toán")} value={appointment.examClearanceAt} />
            <LifecycleTimestamp label={localize("Bắt đầu khám")} value={appointment.examinationStartedAt} />
            <LifecycleTimestamp label={localize("Hoàn tất")} value={appointment.completedAt} />
          </dl>
        </section>
      ) : null}</L10nValue>

      <L10nValue>{outpatientEnabled ? <OutpatientExamActions key={requestKey} appointment={appointment} role={role} clinicIntakeEnabled={clinicIntakeEnabled}
        onUpdated={(updated) => setRequestState({ key: requestKey, status: "success", appointment: updated })}
        onRefresh={retry} /> : null}</L10nValue>


      <L10nValue>{actionError ? <AppointmentMutationError {...actionError} /> : null}</L10nValue>

      <L10nValue>{canAct && !outpatientEnabled ? (
        <section className="rounded-xl border border-border bg-surface p-6">
          <h2 className="text-lg font-semibold"><L10n>{"Thao tác lịch hẹn"}</L10n></h2>
          <p className="mt-1 text-sm text-muted-foreground"><L10n>{"Chỉ lịch hẹn đang chờ tiếp nhận mới có thể được chỉnh sửa, ghi nhận đã đến hoặc hủy."}</L10n></p>
          <div className="mt-4 flex flex-wrap gap-3">
            <Link href={`/appointments/${appointment.appointmentId}/edit`} className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted"><L10n>{"Chỉnh sửa"}</L10n></Link>
            <L10nValue>{!outpatientEnabled ? <button type="button" disabled={changingStatus} onClick={() => setProposedStatus("ARRIVED")} className="min-h-11 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"><L10n>{"Ghi nhận đã đến"}</L10n></button> : null}</L10nValue>
            <button type="button" disabled={changingStatus} onClick={() => setProposedStatus("CANCELLED")} className="min-h-11 rounded-lg border border-danger/40 px-4 py-2 text-sm font-medium text-danger hover:bg-danger/10 disabled:cursor-not-allowed disabled:opacity-50"><L10n>{"Hủy lịch hẹn"}</L10n></button>
          </div>

          <L10nValue>{proposedStatus ? (
            <div role="alertdialog" aria-labelledby="appointment-status-confirm-title" className="mt-4 rounded-lg border border-warning/40 bg-warning/10 p-4">
              <h3 id="appointment-status-confirm-title" className="font-semibold">
                <L10nValue>{proposedStatus === "ARRIVED" ? "Xác nhận bệnh nhân đã đến?" : "Xác nhận hủy lịch hẹn?"}</L10nValue>
              </h3>
              <p className="mt-1 text-sm text-muted-foreground"><L10n>{"Thao tác này thay đổi trạng thái và không thể hoàn tác bằng màn hình hiện tại."}</L10n></p>
              <div className="mt-3 flex flex-wrap gap-3">
                <button type="button" disabled={changingStatus} onClick={() => void changeStatus(proposedStatus)} className="min-h-10 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-50">
                  <L10nValue>{changingStatus ? "Đang cập nhật…" : "Xác nhận"}</L10nValue>
                </button>
                <button type="button" disabled={changingStatus} onClick={() => setProposedStatus(null)} className="min-h-10 rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50"><L10n>{"Quay lại"}</L10n></button>
              </div>
            </div>
          ) : null}</L10nValue>
        </section>
      ) : null}</L10nValue>

      <Link href="/appointments" className="inline-flex min-h-10 items-center rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-surface-muted"><L10n>{"Quay lại danh sách"}</L10n></Link>
    </section>
  );
}

function LifecycleTimestamp({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10nValue>{label}</L10nValue></dt>
      <dd className="mt-1 text-sm"><L10nValue>{formatInstant(value)}</L10nValue></dd>
    </div>
  );
}
