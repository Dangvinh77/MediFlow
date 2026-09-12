"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { appointmentApi } from "../api";
import type { AppointmentDTO } from "../types";

export function AppointmentTable() {
  const router = useRouter();
  const [appointments, setAppointments] = useState<AppointmentDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [departmentId, setDepartmentId] = useState("");
  const [appointmentDate, setAppointmentDate] = useState("");

  async function loadAppointments(filters: { departmentId?: string; appointmentDate?: string } = {}) {
    setLoading(true);
    setError(null);
    try {
      const page = await appointmentApi.search(filters);
      setAppointments(page.content);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && (cause.status === 401 || cause.status === 403)) {
        router.replace("/login");
        return;
      }
      setError(cause instanceof Error ? cause.message : "Không thể tải lịch hẹn.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    appointmentApi
      .search()
      .then((page) => setAppointments(page.content))
      .catch((cause: unknown) => {
        if (cause instanceof ApiRequestError && (cause.status === 401 || cause.status === 403)) {
          router.replace("/login");
          return;
        }
        setError(cause instanceof Error ? cause.message : "Không thể tải lịch hẹn.");
      })
      .finally(() => setLoading(false));
  }, [router]);

  function onFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void loadAppointments({
      departmentId: departmentId.trim() || undefined,
      appointmentDate: appointmentDate || undefined,
    });
  }

  function onReset() {
    setDepartmentId("");
    setAppointmentDate("");
    void loadAppointments();
  }

  return (
    <section className="mt-6">
      <form onSubmit={onFilter} className="flex flex-col gap-3 lg:flex-row">
        <input
          value={departmentId}
          onChange={(event) => setDepartmentId(event.target.value)}
          placeholder="UUID khoa"
          aria-label="UUID khoa"
          className="min-w-0 flex-1 rounded-lg border border-zinc-300 bg-white px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
        />
        <input
          type="date"
          value={appointmentDate}
          onChange={(event) => setAppointmentDate(event.target.value)}
          aria-label="Ngày hẹn"
          className="rounded-lg border border-zinc-300 bg-white px-3 py-2 dark:border-zinc-700 dark:bg-zinc-900"
        />
        <button type="submit" disabled={loading} className="rounded-lg bg-blue-600 px-4 py-2 font-medium text-white disabled:opacity-50">
          Lọc
        </button>
        <button type="button" onClick={onReset} disabled={loading} className="rounded-lg border border-zinc-300 px-4 py-2 font-medium disabled:opacity-50 dark:border-zinc-700">
          Tất cả
        </button>
      </form>

      {loading && <p className="mt-4 text-zinc-500">Đang tải lịch hẹn…</p>}
      {error && <p className="mt-4 text-red-600">{error}</p>}
      {!loading && !error && appointments.length === 0 && <p className="mt-4 text-zinc-500">Chưa có lịch hẹn phù hợp.</p>}

      {!loading && !error && appointments.length > 0 && (
      <div className="mt-6 overflow-x-auto rounded-xl border border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-900">
      <table className="w-full min-w-4xl text-left text-sm">
        <thead className="border-b border-zinc-200 bg-zinc-100 dark:border-zinc-800 dark:bg-zinc-950">
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
            <tr key={appointment.appointmentId} className="border-b border-zinc-100 last:border-0 dark:border-zinc-800">
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
      )}
    </section>
  );
}
