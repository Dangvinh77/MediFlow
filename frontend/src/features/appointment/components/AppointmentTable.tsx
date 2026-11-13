"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { ReferenceText } from "@/components/references/ReferenceText";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { useReferenceDirectory } from "@/components/references/ReferenceProvider";
import { containsUuid } from "@/components/references/referenceLabels";
import Link from "next/link";
import { FormEvent, useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { formatLocalDate } from "@/lib/format";
import type { Role } from "@/lib/roles";
import { isUuid } from "@/lib/validation";
import { appointmentApi, type AppointmentSearchParams } from "../api";
import { getAppointmentStatusPresentation } from "../presentation";
import type { AppointmentDTO } from "../types";

interface RequestError {
  message: string;
  correlationId: string | null;
}

interface AppointmentRequest {
  page: number;
  filters: AppointmentSearchParams;
}

const detailRoles: readonly Role[] = ["ADMIN", "DOCTOR", "NURSE"];
const createRoles: readonly Role[] = ["ADMIN", "NURSE"];
const getServerRole = (): Role | null => null;

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể tải lịch hẹn.",
    correlationId: null,
  };
}

/** Patient directory labels read "Họ tên · dd/mm/yyyy"; show the name and birth year in separate columns. */
function PatientCells({ patientId }: { patientId: string }) {
  const { localize } = useLocale();
  const directory = useReferenceDirectory();
  const [resolved, setResolved] = useState<{ id: string; label: string | null } | null>(null);
  useEffect(() => {
    let active = true;
    if (directory && (directory.canResolve?.("patient") ?? directory.canSearch("patient"))) {
      directory.resolve("patient", patientId)
        .then(label => { if (active) setResolved({ id: patientId, label }); })
        .catch(() => { if (active) setResolved({ id: patientId, label: null }); });
    }
    return () => { active = false; };
  }, [directory, patientId]);
  const label = resolved?.id === patientId ? resolved.label : null;
  const separator = label ? label.lastIndexOf(" · ") : -1;
  const name = label && separator > 0 ? label.slice(0, separator) : null;
  const birthYear = label && separator > 0 ? /(\d{4})\s*$/.exec(label.slice(separator + 3))?.[1] ?? null : null;
  return <>
    <td className="min-w-36 max-w-44 px-3 py-3">
      <L10nValue>{name && !containsUuid(name) ? <span className="block truncate font-medium" title={localize(name)}>{name}</span> : <ReferenceText kind="patient" value={patientId} fallback="Bệnh nhân" />}</L10nValue>
    </td>
    <td className="whitespace-nowrap px-3 py-3 tabular-nums"><L10nValue>{birthYear ?? "—"}</L10nValue></td>
  </>;
}

export function AppointmentTable() {
  const { localize } = useLocale();
  const router = useRouter();
  const requestSequence = useRef(0);
  const mounted = useRef(false);
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const canViewDetail = role !== null && detailRoles.includes(role);
  const canCreate = role !== null && createRoles.includes(role);
  const [appointments, setAppointments] = useState<AppointmentDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [departmentId, setDepartmentId] = useState("");
  const [appointmentDate, setAppointmentDate] = useState("");
  const [activeFilters, setActiveFilters] = useState<AppointmentSearchParams>({});
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [lastRequest, setLastRequest] = useState<AppointmentRequest>({
    page: 0,
    filters: {},
  });

  const loadAppointments = useCallback(async (page: number, filters: AppointmentSearchParams) => {
    const requestId = ++requestSequence.current;
    setLastRequest({ page, filters });
    setLoading(true);
    setError(null);
    try {
      const result = await appointmentApi.search({ ...filters, page });
      if (!mounted.current || requestId !== requestSequence.current) return;
      setAppointments(result.content);
      setPageNumber(result.number);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (cause: unknown) {
      if (!mounted.current || requestId !== requestSequence.current) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      setError(getRequestError(cause));
    } finally {
      if (mounted.current && requestId === requestSequence.current) setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    let active = true;
    mounted.current = true;
    void Promise.resolve().then(() => {
      if (active) void loadAppointments(0, {});
    });
    return () => {
      active = false;
      mounted.current = false;
      requestSequence.current += 1;
    };
  }, [loadAppointments]);

  function onFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedDepartmentId = departmentId.trim();
    if (normalizedDepartmentId && !isUuid(normalizedDepartmentId)) {
      setValidationError("Chọn khoa từ danh sách tìm kiếm.");
      return;
    }
    setValidationError(null);
    const filters: AppointmentSearchParams = {
      departmentId: normalizedDepartmentId || undefined,
      appointmentDate: appointmentDate || undefined,
    };
    setActiveFilters(filters);
    void loadAppointments(0, filters);
  }

  function onReset() {
    setDepartmentId("");
    setAppointmentDate("");
    setValidationError(null);
    setActiveFilters({});
    void loadAppointments(0, {});
  }

  return (
    <section className="mt-6">
      <L10nValue>{canCreate ? (
        <div className="mb-4 flex justify-end">
          <Link
            href="/appointments/new"
            className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-opacity hover:opacity-90"
          ><L10n>{"Tạo lịch hẹn"}</L10n></Link>
        </div>
      ) : null}</L10nValue>
      <form onSubmit={onFilter} className="flex flex-col gap-3 lg:flex-row lg:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="appointment-department" className="mb-1 block text-sm font-medium">Khoa</label>
          <ReferenceInput kind="department"
            id="appointment-department"
            value={departmentId}
            onValueChange={(selectedId) => setDepartmentId(selectedId)}
            placeholder={localize("Tìm theo tên khoa")}
            aria-describedby={validationError ? "appointment-department-error" : undefined}
            className="min-h-11 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          />
        </div>
        <div>
          <label htmlFor="appointment-date" className="mb-1 block text-sm font-medium"><L10n>{"Ngày hẹn"}</L10n></label>
          <input
            id="appointment-date"
            type="date"
            value={appointmentDate}
            onChange={(event) => setAppointmentDate(event.target.value)}
            className="min-h-11 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          />
        </div>
        <button type="submit" disabled={loading} className="min-h-11 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"><L10n>{"Lọc"}</L10n></button>
        <button type="button" onClick={onReset} disabled={loading} className="min-h-11 rounded-lg border border-border bg-surface px-4 py-2 font-medium text-foreground hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50"><L10n>{"Tất cả"}</L10n></button>
      </form>
      <L10nValue>{validationError ? <p id="appointment-department-error" role="alert" className="mt-2 text-sm text-danger"><L10nValue>{validationError}</L10nValue></p> : null}</L10nValue>

      <L10nValue>{loading && appointments.length === 0 ? <AsyncState kind="loading" message={localize("Đang tải lịch hẹn…")} /> : null}</L10nValue>
      <L10nValue>{!loading && error ? <AsyncState kind="error" message={localize(error.message)} correlationId={error.correlationId} onRetry={() => void loadAppointments(lastRequest.page, lastRequest.filters)} /> : null}</L10nValue>
      <L10nValue>{!loading && !error && appointments.length === 0 ? <AsyncState kind="empty" message={localize("Chưa có lịch hẹn phù hợp.")} /> : null}</L10nValue>

      <L10nValue>{!error && appointments.length > 0 ? (
        <>
          <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
            <table className="w-full min-w-4xl text-left text-sm">
              <thead className="border-b border-border bg-surface-muted">
                <tr className="whitespace-nowrap"><th scope="col" className="px-3 py-3"><L10n>{"Ngày"}</L10n></th><th scope="col" className="px-3 py-3"><L10n>{"Giờ"}</L10n></th><th scope="col" className="px-3 py-3"><L10n>{"Bệnh nhân"}</L10n></th><th scope="col" className="px-3 py-3"><L10n>{"Năm sinh"}</L10n></th><th scope="col" className="px-3 py-3"><L10n>{"Bác sĩ"}</L10n></th><th scope="col" className="px-3 py-3">Khoa</th><th scope="col" className="px-3 py-3"><L10n>{"Trạng thái"}</L10n></th><th scope="col" className="px-3 py-3"><L10n>{"Lý do"}</L10n></th><th scope="col" className="px-3 py-3 text-right"><L10n>{"Chi tiết"}</L10n></th></tr>
              </thead>
              <tbody>
                <L10nValue>{appointments.map((appointment) => {
                  const status = getAppointmentStatusPresentation(appointment);
                  return (
                    <tr key={appointment.appointmentId} className="border-b border-border align-top last:border-0">
                      <td className="whitespace-nowrap px-3 py-3 tabular-nums"><L10nValue>{formatLocalDate(appointment.appointmentDate)}</L10nValue></td>
                      <td className="whitespace-nowrap px-3 py-3 tabular-nums">{appointment.appointmentTime}</td>
                      <PatientCells patientId={appointment.patientId} />
                      <td className="min-w-28 px-3 py-3"><ReferenceText kind="doctor" value={appointment.doctorId} fallback="Bác sĩ" /></td>
                      <td className="min-w-32 px-3 py-3"><ReferenceText kind="department" value={appointment.departmentId} fallback="Khoa" /></td>
                      <td className="whitespace-nowrap px-3 py-3"><StatusBadge tone={status.tone}><L10nValue>{status.label}</L10nValue></StatusBadge></td>
                      <td className="w-full min-w-40 max-w-72 px-3 py-3"><span className="line-clamp-2 wrap-anywhere" title={localize(appointment.reason ?? undefined)}><L10nValue>{appointment.reason ?? "—"}</L10nValue></span></td>
                      <td className="whitespace-nowrap px-3 py-3 text-right">
                        <L10nValue>{canViewDetail ? (
                          <Link
                            href={`/appointments/${appointment.appointmentId}`}
                            className="inline-flex min-h-9 items-center rounded-lg border border-control-border px-3 text-sm font-semibold text-primary transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                          >
                            <L10n>{"Xem"}</L10n>
                          </Link>
                        ) : "—"}</L10nValue>
                      </td>
                    </tr>
                  );
                })}</L10nValue>
              </tbody>
            </table>
          </div>
          <Pagination page={pageNumber} totalPages={totalPages} totalElements={totalElements} loading={loading} onPageChange={(page) => void loadAppointments(page, activeFilters)} />
        </>
      ) : null}</L10nValue>
    </section>
  );
}
