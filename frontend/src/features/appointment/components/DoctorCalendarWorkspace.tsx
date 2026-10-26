"use client";

import { useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { formatLocalDate } from "@/lib/format";
import { appointmentApi } from "../api";
import type { DoctorCalendarDTO, RegistrationSummary } from "../types";

interface WorkspaceError { message: string; correlationId: string | null }

function errorFrom(cause: unknown, fallback: string): WorkspaceError {
  return cause instanceof ApiRequestError
    ? { message: cause.message, correlationId: cause.correlationId }
    : { message: cause instanceof Error ? cause.message : fallback, correlationId: null };
}

function todayInVietnam(): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts();
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

export function mondayOfWeek(dateValue: string): string {
  const date = new Date(`${dateValue}T12:00:00`);
  const day = date.getDay();
  date.setDate(date.getDate() - (day === 0 ? 6 : day - 1));
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function DoctorCalendarWorkspace({ enabled }: { enabled: boolean }) {
  const [view, setView] = useState<"day" | "week">("day");
  const [date, setDate] = useState(todayInVietnam);
  const [calendar, setCalendar] = useState<DoctorCalendarDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<WorkspaceError | null>(null);
  const [selectedSession, setSelectedSession] = useState<string | null>(null);
  const [registrations, setRegistrations] = useState<RegistrationSummary[] | null>(null);
  const [registrationLoading, setRegistrationLoading] = useState(false);
  const [registrationError, setRegistrationError] = useState<WorkspaceError | null>(null);

  async function loadCalendar() {
    if (!enabled || loading || !date) return;
    setLoading(true); setError(null); setCalendar(null); setSelectedSession(null); setRegistrations(null);
    try {
      const result = await appointmentApi.myCalendar(view === "week" ? { weekStart: mondayOfWeek(date) } : { date });
      setCalendar(result);
    } catch (cause) {
      setError(errorFrom(cause, "Không thể tải lịch khám của bạn."));
    } finally {
      setLoading(false);
    }
  }

  async function loadRegistrations(sessionId: string) {
    if (registrationLoading) return;
    setSelectedSession(sessionId); setRegistrations(null); setRegistrationError(null); setRegistrationLoading(true);
    try {
      const result = await appointmentApi.registrationsBySession(sessionId, 0, 20);
      setRegistrations(result.content);
    } catch (cause) {
      setRegistrationError(errorFrom(cause, "Không thể tải phiếu của buổi này."));
    } finally {
      setRegistrationLoading(false);
    }
  }

  if (!enabled) return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground">Lịch khám V2 chưa được mở.</p>;

  return <div className="space-y-6">
    <section className="rounded-xl border border-border bg-surface p-5">
      <h2 className="text-lg font-semibold">Lịch khám của tôi</h2>
      <p className="mt-1 text-sm text-muted-foreground">Lịch được tải theo tài khoản bác sĩ đang đăng nhập.</p>
      <form className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-end" onSubmit={event => { event.preventDefault(); void loadCalendar(); }}>
        <label className="space-y-1 text-sm font-medium" htmlFor="doctor-calendar-view">Chế độ lịch
          <select id="doctor-calendar-view" value={view} onChange={event => setView(event.target.value as "day" | "week")} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3">
            <option value="day">Theo ngày</option><option value="week">Theo tuần</option>
          </select>
        </label>
        <label className="space-y-1 text-sm font-medium" htmlFor="doctor-calendar-date">Ngày tham chiếu
          <input id="doctor-calendar-date" type="date" required value={date} onChange={event => setDate(event.target.value)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" />
        </label>
        <Button type="submit" loading={loading}>Tải lịch của tôi</Button>
      </form>
      {error ? <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={() => void loadCalendar()} /> : null}
      {calendar ? <>
        <p role="status" className="mt-4 text-sm text-muted-foreground">{formatLocalDate(calendar.fromDate)} – {formatLocalDate(calendar.toDate)} · {calendar.timezone}</p>
        {calendar.sessions.length ? <div className="mt-3 overflow-x-auto rounded-lg border border-border">
          <table className="w-full min-w-[48rem] text-left text-sm">
            <thead className="bg-surface-muted text-xs uppercase tracking-wide text-muted-foreground"><tr><th className="p-3">Ngày</th><th className="p-3">Giờ</th><th className="p-3">Phòng</th><th className="p-3">Trạng thái</th><th className="p-3">Chỗ</th><th className="p-3"><span className="sr-only">Thao tác</span></th></tr></thead>
            <tbody>{calendar.sessions.map(session => <tr key={session.sessionId} className="border-t border-border">
              <td className="p-3">{formatLocalDate(session.sessionDate)}</td><td className="p-3">{session.startTime}–{session.endTime}</td><td className="p-3 break-all font-mono">{session.roomId}</td><td className="p-3">{session.status}</td><td className="p-3">{session.confirmedCount} đã xác nhận · {session.availableCount} còn</td>
              <td className="p-3 text-right"><Button type="button" onClick={() => void loadRegistrations(session.sessionId)}>Xem phiếu</Button></td>
            </tr>)}</tbody>
          </table>
        </div> : <p role="status" className="mt-3 text-sm text-muted-foreground">Không có buổi khám trong khoảng này.</p>}
      </> : null}
    </section>

    {selectedSession ? <section className="rounded-xl border border-border bg-surface p-5">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold">Phiếu của buổi <span className="font-mono">{selectedSession}</span></h2><Button type="button" loading={registrationLoading} onClick={() => void loadRegistrations(selectedSession)}>Tải lại</Button></div>
      {registrationError ? <AsyncState kind="error" message={registrationError.message} correlationId={registrationError.correlationId} onRetry={() => void loadRegistrations(selectedSession)} /> : null}
      {registrations ? registrations.length ? <ul className="mt-3 divide-y divide-border">{registrations.map(item => <li key={item.registrationId} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm"><span className="break-all font-mono">{item.patientId}</span><span>{item.status}</span><span>{item.checkedInAt ? "Đã đến" : "Chưa đến"}</span><span>{item.queueNumber === null ? "Chưa có số" : `Số ${item.queueNumber}`}</span><span>{item.readyForExam ? "Đủ điều kiện khám" : "Đang chờ điều kiện"}</span></li>)}</ul> : <p role="status" className="mt-3 text-sm text-muted-foreground">Buổi này chưa có phiếu.</p> : null}
    </section> : null}
  </div>;
}
