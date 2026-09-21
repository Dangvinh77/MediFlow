"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { StatusBadge, type StatusTone } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatLocalDate } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { appointmentApi, type AppointmentSearchParams } from "../api";
import type { AppointmentDTO, AppointmentStatus } from "../types";

interface RequestError {
  message: string;
  correlationId: string | null;
}

interface AppointmentRequest {
  page: number;
  filters: AppointmentSearchParams;
}

const statusPresentation: Record<AppointmentStatus, { label: string; tone: StatusTone }> = {
  PENDING: { label: "Chờ tiếp nhận", tone: "warning" },
  ARRIVED: { label: "Đã đến", tone: "info" },
  CANCELLED: { label: "Đã hủy", tone: "neutral" },
};

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể tải lịch hẹn.",
    correlationId: null,
  };
}

export function AppointmentTable() {
  const router = useRouter();
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
    setLastRequest({ page, filters });
    setLoading(true);
    setError(null);
    try {
      const result = await appointmentApi.search({ ...filters, page });
      setAppointments(result.content);
      setPageNumber(result.number);
      setTotalPages(result.totalPages);
      setTotalElements(result.totalElements);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      setError(getRequestError(cause));
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    let active = true;
    const initialRequest: AppointmentRequest = { page: 0, filters: {} };

    appointmentApi.search({ ...initialRequest.filters, page: initialRequest.page })
      .then((result) => {
        if (!active) return;
        setAppointments(result.content);
        setPageNumber(result.number);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      })
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 401) {
          router.replace("/login");
          return;
        }
        setError(getRequestError(cause));
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [router]);

  function onFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedDepartmentId = departmentId.trim();
    if (normalizedDepartmentId && !isUuid(normalizedDepartmentId)) {
      setValidationError("Mã khoa phải là UUID hợp lệ.");
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
      <form onSubmit={onFilter} className="flex flex-col gap-3 lg:flex-row lg:items-end">
        <div className="min-w-0 flex-1">
          <label htmlFor="appointment-department" className="mb-1 block text-sm font-medium">Mã khoa</label>
          <input
            id="appointment-department"
            value={departmentId}
            onChange={(event) => setDepartmentId(event.target.value)}
            placeholder="UUID khoa"
            aria-describedby={validationError ? "appointment-department-error" : undefined}
            className="min-h-11 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          />
        </div>
        <div>
          <label htmlFor="appointment-date" className="mb-1 block text-sm font-medium">Ngày hẹn</label>
          <input
            id="appointment-date"
            type="date"
            value={appointmentDate}
            onChange={(event) => setAppointmentDate(event.target.value)}
            className="min-h-11 w-full rounded-lg border border-border bg-surface px-3 py-2 text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          />
        </div>
        <button type="submit" disabled={loading} className="min-h-11 rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50">Lọc</button>
        <button type="button" onClick={onReset} disabled={loading} className="min-h-11 rounded-lg border border-border bg-surface px-4 py-2 font-medium text-foreground hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50">Tất cả</button>
      </form>
      {validationError ? <p id="appointment-department-error" role="alert" className="mt-2 text-sm text-danger">{validationError}</p> : null}

      {loading && appointments.length === 0 ? <AsyncState kind="loading" message="Đang tải lịch hẹn…" /> : null}
      {!loading && error ? <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={() => void loadAppointments(lastRequest.page, lastRequest.filters)} /> : null}
      {!loading && !error && appointments.length === 0 ? <AsyncState kind="empty" message="Chưa có lịch hẹn phù hợp." /> : null}

      {!error && appointments.length > 0 ? (
        <>
          <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
            <table className="w-full min-w-4xl text-left text-sm">
              <thead className="border-b border-border bg-surface-muted">
                <tr><th scope="col" className="px-4 py-3">Ngày</th><th scope="col" className="px-4 py-3">Giờ</th><th scope="col" className="px-4 py-3">Bệnh nhân</th><th scope="col" className="px-4 py-3">Bác sĩ</th><th scope="col" className="px-4 py-3">Khoa</th><th scope="col" className="px-4 py-3">Trạng thái</th><th scope="col" className="px-4 py-3">Lý do</th></tr>
              </thead>
              <tbody>
                {appointments.map((appointment) => {
                  const status = statusPresentation[appointment.status] ?? { label: "Không xác định", tone: "neutral" as const };
                  return (
                    <tr key={appointment.appointmentId} className="border-b border-border last:border-0">
                      <td className="px-4 py-3">{formatLocalDate(appointment.appointmentDate)}</td>
                      <td className="px-4 py-3">{appointment.appointmentTime}</td>
                      <td className="px-4 py-3 font-mono text-xs">{appointment.patientId}</td>
                      <td className="px-4 py-3 font-mono text-xs">{appointment.doctorId}</td>
                      <td className="px-4 py-3 font-mono text-xs">{appointment.departmentId}</td>
                      <td className="px-4 py-3"><StatusBadge tone={status.tone}>{status.label}</StatusBadge></td>
                      <td className="px-4 py-3">{appointment.reason ?? "—"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <Pagination page={pageNumber} totalPages={totalPages} totalElements={totalElements} loading={loading} onPageChange={(page) => void loadAppointments(page, activeFilters)} />
        </>
      ) : null}
    </section>
  );
}
