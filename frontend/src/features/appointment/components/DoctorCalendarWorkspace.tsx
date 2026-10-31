"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { ApiRequestError } from "@/lib/api";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { formatLocalDate } from "@/lib/format";
import Link from "next/link";
import { ReferenceText } from "@/components/references/ReferenceText";
import { registrationStatusLabels, sessionStatusLabels } from "../presentation";
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
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <DoctorCalendarWorkspaceContent key={session ?? "anonymous"} enabled={enabled} />;
}

function DoctorCalendarWorkspaceContent({ enabled }: { enabled: boolean }) {
  const [view, setView] = useState<"day" | "week">("day");
  const [date, setDate] = useState(todayInVietnam);
  const [calendar, setCalendar] = useState<DoctorCalendarDTO | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<WorkspaceError | null>(null);
  const [selectedSession, setSelectedSession] = useState<string | null>(null);
  const [registrations, setRegistrations] = useState<RegistrationSummary[] | null>(null);
  const [registrationLoading, setRegistrationLoading] = useState(false);
  const [registrationError, setRegistrationError] = useState<WorkspaceError | null>(null);

  const registrationGeneration = useRef(0);
  const [registrationPage, setRegistrationPage] = useState(0);
  const [registrationPages, setRegistrationPages] = useState(0);

  const readGeneration = useRef(0);
  useEffect(() => {
    if (!enabled) return;
    const reads = readGeneration;
    const generation = ++reads.current;
    const registrationReads = registrationGeneration;
    appointmentApi.myCalendar({ date: todayInVietnam() }).then(result => {
      if (generation !== readGeneration.current) return;
      setCalendar(result);
    }).catch(cause => {
      if (generation === readGeneration.current) setError(errorFrom(cause, "Không thể tải dữ liệu của bạn."));
    }).finally(() => { if (generation === readGeneration.current) setLoading(false); });
    return () => { reads.current++; registrationReads.current++; };
  }, [enabled]);

  async function loadCalendar() {
    if (!enabled || loading || !date) return;
    const generation = ++readGeneration.current;
    registrationGeneration.current++;
    setRegistrationLoading(false); setRegistrationError(null);
    setLoading(true); setError(null); setCalendar(null); setSelectedSession(null); setRegistrations(null);
    try {
      const result = await appointmentApi.myCalendar(view === "week" ? { weekStart: mondayOfWeek(date) } : { date });
      if (generation === readGeneration.current) setCalendar(result);
    } catch (cause) {
      if (generation === readGeneration.current) setError(errorFrom(cause, "Không thể tải lịch khám của bạn."));
    } finally {
      if (generation === readGeneration.current) setLoading(false);
    }
  }

  async function loadRegistrations(sessionId: string, page = 0) {
    const generation = ++registrationGeneration.current;
    setSelectedSession(sessionId); setRegistrations(null); setRegistrationError(null); setRegistrationLoading(true);
    try {
      const result = await appointmentApi.registrationsBySession(sessionId, page, 20);
      if (generation !== registrationGeneration.current) return;
      setRegistrations(result.content); setRegistrationPage(result.number); setRegistrationPages(result.totalPages);
    } catch (cause) {
      if (generation !== registrationGeneration.current) return;
      setRegistrationError(errorFrom(cause, "Không thể tải phiếu của buổi này."));
    } finally {
      if (generation === registrationGeneration.current) setRegistrationLoading(false);
    }
  }

  if (!enabled) return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground">Lịch khám chưa được mở.</p>;

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
        {calendar.sessions.length ? <ul className="mt-3 grid gap-3 lg:grid-cols-2">
          {calendar.sessions.map(session => <li key={session.sessionId} className="space-y-3 rounded-lg border border-border p-4">
            <div className="flex flex-wrap justify-between gap-2"><h3 className="font-semibold">{formatLocalDate(session.sessionDate)} · {session.startTime}–{session.endTime}</h3><span className="text-sm">{sessionStatusLabels[session.status]}</span></div>
            <p className="text-sm"><ReferenceText kind="department" value={session.departmentId} fallback="Khoa khám" /> · <ReferenceText kind="room" value={session.roomId} fallback="Phòng khám" /></p>
            <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-muted-foreground">{session.confirmedCount} đã xác nhận · {session.availableCount} còn chỗ</p><Button type="button" aria-pressed={selectedSession === session.sessionId} onClick={() => void loadRegistrations(session.sessionId)}>Xem phiếu</Button></div>
          </li>)}
        </ul> : <p role="status" className="mt-3 text-sm text-muted-foreground">Không có buổi khám trong khoảng này.</p>}
      </> : null}
    </section>

    {selectedSession ? <section className="rounded-xl border border-border bg-surface p-5">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold">Phiếu của buổi {calendar?.sessions.filter(session => session.sessionId === selectedSession).map(session => `${formatLocalDate(session.sessionDate)} · ${session.startTime}–${session.endTime}`)}</h2><Button type="button" loading={registrationLoading} onClick={() => void loadRegistrations(selectedSession, registrationPage)}>Tải lại</Button></div>
      {registrationError ? <AsyncState kind="error" message={registrationError.message} correlationId={registrationError.correlationId} onRetry={() => void loadRegistrations(selectedSession)} /> : null}
      {registrationLoading ? <p role="status" className="mt-3 text-sm">Đang tải phiếu khám…</p> : null}
      {registrations ? registrations.length ? <ul className="mt-3 divide-y divide-border">{registrations.map(item => <li key={item.registrationId} className="flex flex-wrap items-center justify-between gap-3 py-4 text-sm">
        <div className="space-y-1"><p className="font-medium"><ReferenceText kind="patient" value={item.patientId} fallback="Bệnh nhân" /></p><p className="text-muted-foreground">{item.visitType === "FOLLOW_UP" ? "Tái khám" : "Khám mới"} · {registrationStatusLabels[item.status]}</p></div>
        <div className="space-y-1"><p>{item.checkedInAt ? "Đã đến" : "Chưa đến"} · {item.queueNumber === null ? "Chưa có số" : `Số ${item.queueNumber}`}</p><p className="text-muted-foreground">{item.readyForExam ? "Đủ điều kiện khám" : item.status === "CONFIRMED" && !item.checkedInAt ? "Chờ bệnh nhân đến quầy tiếp nhận" : item.status === "AWAITING_PAYMENT" ? "Chờ thanh toán" : item.status === "CONFIRMED" ? "Chờ đến giờ khám hoặc kiểm tra điều kiện khám" : ""}</p></div>
        <Link href={`/appointments/${encodeURIComponent(item.registrationId)}`} className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 font-medium hover:bg-surface-muted">Xem phiếu khám</Link>
      </li>)}</ul> : <p role="status" className="mt-3 text-sm text-muted-foreground">Buổi này chưa có phiếu.</p> : null}
      {registrations && registrationPages > 1 ? <nav aria-label="Trang phiếu buổi khám" className="mt-4 flex items-center justify-between gap-3"><Button type="button" disabled={registrationLoading || registrationPage === 0} onClick={() => void loadRegistrations(selectedSession, registrationPage - 1)}>Trang trước</Button><span className="text-sm">Trang {registrationPage + 1} / {registrationPages}</span><Button type="button" disabled={registrationLoading || registrationPage + 1 >= registrationPages} onClick={() => void loadRegistrations(selectedSession, registrationPage + 1)}>Trang sau</Button></nav> : null}
    </section> : null}
  </div>;
}
