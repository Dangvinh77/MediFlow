"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Pagination } from "@/components/ui/Pagination";
import { ApiRequestError } from "@/lib/api";
import { appointmentApi, type AppointmentSearchParams } from "../api";
import type { AppointmentDTO } from "../types";

export function AppointmentTable() {
  const router = useRouter();
  const [appointments, setAppointments] = useState<AppointmentDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [departmentId, setDepartmentId] = useState("");
  const [appointmentDate, setAppointmentDate] = useState("");
  const [activeFilters, setActiveFilters] = useState<AppointmentSearchParams>({});
  const [pageNumber, setPageNumber] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const handleError = useCallback((cause: unknown) => {
    if (cause instanceof ApiRequestError && (cause.status === 401 || cause.status === 403)) {
      router.replace("/login");
      return;
    }
    setError(cause instanceof Error ? cause.message : "Không thể tải lịch hẹn.");
  }, [router]);

  async function loadAppointments(page: number, filters: AppointmentSearchParams) {
    setLoading(true);
    setError(null);
    try {
      const result = await appointmentApi.search({ ...filters, page });
      setAppointments(result.content);
      setPageNumber(result.number);
      setTotalPages(result.totalPages);
    } catch (cause: unknown) {
      handleError(cause);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    appointmentApi
      .search()
      .then((result) => {
        setAppointments(result.content);
        setPageNumber(result.number);
        setTotalPages(result.totalPages);
      })
      .catch(handleError)
      .finally(() => setLoading(false));
  }, [handleError]);

  function onFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const filters = {
      departmentId: departmentId.trim() || undefined,
      appointmentDate: appointmentDate || undefined,
    };
    setActiveFilters(filters);
    void loadAppointments(0, filters);
  }

  function onReset() {
    setDepartmentId("");
    setAppointmentDate("");
    setActiveFilters({});
    void loadAppointments(0, {});
  }

  return (
    <section className="mt-6">
      <form onSubmit={onFilter} className="flex flex-col gap-3 lg:flex-row">
        <input
          value={departmentId}
          onChange={(event) => setDepartmentId(event.target.value)}
          placeholder="UUID khoa"
          aria-label="UUID khoa"
          className="min-w-0 flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground"
        />
        <input
          type="date"
          value={appointmentDate}
          onChange={(event) => setAppointmentDate(event.target.value)}
          aria-label="Ngày hẹn"
          className="rounded-lg border border-border bg-surface px-3 py-2 text-foreground"
        />
        <button
          type="submit"
          disabled={loading}
          className="rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground transition-colors hover:opacity-90 disabled:opacity-50"
        >
          Lọc
        </button>
        <button
          type="button"
          onClick={onReset}
          disabled={loading}
          className="rounded-lg border border-border bg-surface px-4 py-2 font-medium text-foreground transition-colors hover:bg-surface-muted disabled:opacity-50"
        >
          Tất cả
        </button>
      </form>

      {loading && <p className="mt-4 text-muted-foreground">Đang tải lịch hẹn…</p>}
      {error && <p className="mt-4 text-danger">{error}</p>}
      {!loading && !error && appointments.length === 0 && (
        <p className="mt-4 text-muted-foreground">Chưa có lịch hẹn phù hợp.</p>
      )}

      {!error && appointments.length > 0 && (
        <>
          <div className="mt-6 overflow-x-auto rounded-xl border border-border bg-surface">
            <table className="w-full min-w-4xl text-left text-sm">
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th className="px-4 py-3">Ngày</th>
                  <th className="px-4 py-3">Giờ</th>
                  <th className="px-4 py-3">Bệnh nhân</th>
                  <th className="px-4 py-3">Bác sĩ</th>
                  <th className="px-4 py-3">Khoa</th>
                  <th className="px-4 py-3">Trạng thái</th>
                  <th className="px-4 py-3">Lý do</th>
                </tr>
              </thead>
              <tbody>
                {appointments.map((appointment) => (
                  <tr
                    key={appointment.appointmentId}
                    className="border-b border-border last:border-0"
                  >
                    <td className="px-4 py-3">{appointment.appointmentDate}</td>
                    <td className="px-4 py-3">{appointment.appointmentTime}</td>
                    <td className="px-4 py-3 font-mono text-xs">{appointment.patientId}</td>
                    <td className="px-4 py-3 font-mono text-xs">{appointment.doctorId}</td>
                    <td className="px-4 py-3 font-mono text-xs">{appointment.departmentId}</td>
                    <td className="px-4 py-3 font-medium">{appointment.status}</td>
                    <td className="px-4 py-3">{appointment.reason ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={pageNumber}
            totalPages={totalPages}
            loading={loading}
            onPageChange={(page) => void loadAppointments(page, activeFilters)}
          />
        </>
      )}
    </section>
  );
}
