"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { appointmentApi } from "../api";
import type { AppointmentDTO } from "../types";

export function AppointmentTable() {
  const router = useRouter();
  const [appointments, setAppointments] = useState<AppointmentDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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

  if (loading) return <p className="mt-6 text-zinc-500">Đang tải lịch hẹn…</p>;
  if (error) return <p className="mt-6 text-red-600">{error}</p>;
  if (appointments.length === 0) return <p className="mt-6 text-zinc-500">Chưa có lịch hẹn.</p>;

  return (
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
  );
}
