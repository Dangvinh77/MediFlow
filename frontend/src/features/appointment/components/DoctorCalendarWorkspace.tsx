"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { ApiRequestError } from "@/lib/api";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { formatLocalDate } from "@/lib/format";
import Link from "next/link";
import { ScheduleDateInput } from "./ScheduleDateInput";
import { ReferenceText } from "@/components/references/ReferenceText";
import { queueNumberText, roomNumberText, registrationStatusLabels, sessionStatusLabels } from "../presentation";
import { appointmentApi } from "../api";
import type { DoctorCalendarDTO, DoctorWorkShiftDTO, RegistrationSummary } from "../types";

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

export function DoctorCalendarWorkspace({ enabled, clinicIntakeEnabled = false }: { enabled: boolean; clinicIntakeEnabled?: boolean }) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <DoctorCalendarWorkspaceContent key={session ?? "anonymous"} enabled={enabled} clinicIntakeEnabled={clinicIntakeEnabled} />;
}

function DoctorCalendarWorkspaceContent({ enabled, clinicIntakeEnabled }: { enabled: boolean; clinicIntakeEnabled: boolean }) {
  const { localize } = useLocale();
  const [view, setView] = useState<"day" | "week">("day");
  const [date, setDate] = useState(todayInVietnam);
  const [calendar, setCalendar] = useState<DoctorCalendarDTO | null>(null);
  const [shifts, setShifts] = useState<DoctorWorkShiftDTO[] | null>(null);
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
      setCalendar(result); void loadShifts(result, generation);
    }).catch(cause => {
      if (generation === readGeneration.current) setError(errorFrom(cause, "Không thể tải dữ liệu của bạn."));
    }).finally(() => { if (generation === readGeneration.current) setLoading(false); });
    return () => { reads.current++; registrationReads.current++; };
    // loadShifts only reads refs and setters; this is the initial load.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);

  /** Shifts are context for the sessions, so a failed read never hides the calendar. */
  async function loadShifts(range: DoctorCalendarDTO, generation: number) {
    try {
      const rows = await appointmentApi.myWorkShifts(range.fromDate, range.toDate);
      if (generation === readGeneration.current) setShifts(rows.filter(row => row.status === "ACTIVE"));
    } catch {
      if (generation === readGeneration.current) setShifts(null);
    }
  }

  async function loadCalendar() {
    if (!enabled || loading || !date) return;
    const generation = ++readGeneration.current;
    registrationGeneration.current++;
    setRegistrationLoading(false); setRegistrationError(null);
    setLoading(true); setError(null); setCalendar(null); setShifts(null); setSelectedSession(null); setRegistrations(null);
    try {
      const result = await appointmentApi.myCalendar(view === "week" ? { weekStart: mondayOfWeek(date) } : { date });
      if (generation === readGeneration.current) { setCalendar(result); void loadShifts(result, generation); }
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

  if (!enabled) return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground"><L10n>{"Lịch khám chưa được mở."}</L10n></p>;

  return <div className="space-y-6">
    <section className="rounded-xl border border-border bg-surface p-5">
      <h2 className="text-lg font-semibold"><L10n>{"Bộ lọc lịch khám"}</L10n></h2>
      <p className="mt-1 text-sm text-muted-foreground"><L10n>{"Ca được phân công cho biết bạn trực giờ nào, phòng nào. Buổi khám là phần trong ca nhận bệnh nhân; mở từng buổi để xem phiếu."}</L10n></p>
      <form className="mt-4 flex flex-col gap-4 sm:flex-row sm:items-end" onSubmit={event => { event.preventDefault(); void loadCalendar(); }}>
        <label className="text-sm font-medium" htmlFor="doctor-calendar-view"><span className="mb-1 block"><L10n>{"Chế độ lịch"}</L10n></span>
          <select id="doctor-calendar-view" value={view} onChange={event => setView(event.target.value as "day" | "week")} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 font-normal">
            <option value="day"><L10n>{"Theo ngày"}</L10n></option><option value="week"><L10n>{"Theo tuần"}</L10n></option>
          </select>
        </label>
        <ScheduleDateInput label={localize("Ngày tham chiếu")} id="doctor-calendar-date" required value={date} onChange={event => setDate(event.target.value)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 font-normal" />
        <Button type="submit" loading={loading}><L10n>{"Tải lịch của tôi"}</L10n></Button>
      </form>
      <L10nValue>{error ? <AsyncState kind="error" message={localize(error.message)} correlationId={error.correlationId} onRetry={() => void loadCalendar()} /> : null}</L10nValue>
      <L10nValue>{calendar ? <>
        <p role="status" className="mt-4 text-sm text-muted-foreground"><L10nValue>{formatLocalDate(calendar.fromDate)}</L10nValue> – <L10nValue>{formatLocalDate(calendar.toDate)}</L10nValue> · {calendar.timezone}</p>
        <L10nValue>{shifts?.length ? <div className="mt-3 rounded-lg bg-surface-muted p-3 text-sm"><h3 className="font-semibold"><L10n>{"Ca được phân công"}</L10n></h3><ul className="mt-2 flex flex-wrap gap-x-6 gap-y-1"><L10nValue>{shifts.map(shift => <li key={shift.shiftId}><L10nValue>{formatLocalDate(shift.shiftDate)}</L10nValue> · {shift.startTime}–{shift.endTime} · <ReferenceText kind="room" value={shift.roomId} fallback="Phòng khám" /></li>)}</L10nValue></ul></div> : null}</L10nValue>
        <L10nValue>{calendar.sessions.length ? <ul className="mt-3 grid gap-3 lg:grid-cols-2">
          <L10nValue>{calendar.sessions.map(session => <li key={session.sessionId} className="space-y-3 rounded-lg border border-border p-4">
            <div className="flex flex-wrap justify-between gap-2"><h3 className="font-semibold"><L10nValue>{formatLocalDate(session.sessionDate)}</L10nValue> · {session.startTime}–{session.endTime}</h3><span className="text-sm"><L10nValue>{sessionStatusLabels[session.status]}</L10nValue></span></div>
            <p className="text-sm"><ReferenceText kind="department" value={session.departmentId} fallback="Khoa khám" /> · <ReferenceText kind="room" value={session.roomId} fallback="Phòng khám" /></p>
            <div className="flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-muted-foreground">{session.confirmedCount}<L10n>{" đã xác nhận · "}</L10n>{session.availableCount}<L10n>{" còn chỗ"}</L10n></p><Button type="button" aria-pressed={selectedSession === session.sessionId} onClick={() => void loadRegistrations(session.sessionId)}><L10n>{"Xem phiếu"}</L10n></Button></div>
          </li>)}</L10nValue>
        </ul> : <p role="status" className="mt-3 text-sm text-muted-foreground"><L10n>{"Không có buổi khám trong khoảng này."}</L10n></p>}</L10nValue>
      </> : null}</L10nValue>
    </section>

    <L10nValue>{selectedSession ? <section className="rounded-xl border border-border bg-surface p-5">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold"><L10n>{"Phiếu của buổi "}</L10n><L10nValue>{calendar?.sessions.filter(session => session.sessionId === selectedSession).map(session => `${formatLocalDate(session.sessionDate)} · ${session.startTime}–${session.endTime}`)}</L10nValue></h2><Button type="button" loading={registrationLoading} onClick={() => void loadRegistrations(selectedSession, registrationPage)}><L10n>{"Tải lại"}</L10n></Button></div>
      <L10nValue>{registrationError ? <AsyncState kind="error" message={localize(registrationError.message)} correlationId={registrationError.correlationId} onRetry={() => void loadRegistrations(selectedSession)} /> : null}</L10nValue>
      <L10nValue>{registrationLoading ? <AsyncState kind="loading" message={localize("Đang tải phiếu khám…")} /> : null}</L10nValue>
      <L10nValue>{registrations ? registrations.length ? <ul className="mt-3 divide-y divide-border"><L10nValue>{registrations.map(item => <li key={item.registrationId} className="flex flex-wrap items-center justify-between gap-3 py-4 text-sm">
        <div className="space-y-1"><p className="font-medium"><ReferenceText kind="patient" value={item.patientId} fallback="Bệnh nhân" /></p><p className="text-muted-foreground"><L10nValue>{item.visitType === "FOLLOW_UP" ? "Tái khám" : "Khám mới"}</L10nValue> · <L10nValue>{registrationStatusLabels[item.status]}</L10nValue></p></div>
        <div className="space-y-1"><p><L10nValue>{item.checkedInAt ? "Đã đến" : "Chưa đến"}</L10nValue> · <L10nValue>{queueNumberText(item.queueNumber, clinicIntakeEnabled)}</L10nValue><L10nValue>{clinicIntakeEnabled && roomNumberText(item.roomQueueNumber) ? ` · ${roomNumberText(item.roomQueueNumber)}` : ""}</L10nValue></p><p className="text-muted-foreground"><L10nValue>{item.readyForExam ? "Đủ điều kiện khám" : item.status === "CONFIRMED" && !item.checkedInAt ? "Chờ bệnh nhân đến quầy tiếp nhận" : item.status === "AWAITING_PAYMENT" ? "Chờ thanh toán" : item.status === "CONFIRMED" ? "Chờ đến giờ khám hoặc kiểm tra điều kiện khám" : ""}</L10nValue></p></div>
        <Link href={`/appointments/${encodeURIComponent(item.registrationId)}`} className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 py-2 font-medium hover:bg-surface-muted"><L10n>{"Xem phiếu khám"}</L10n></Link>
      </li>)}</L10nValue></ul> : <p role="status" className="mt-3 text-sm text-muted-foreground"><L10n>{"Buổi này chưa có phiếu."}</L10n></p> : null}</L10nValue>
      <L10nValue>{registrations && registrationPages > 1 ? <nav aria-label={localize("Trang phiếu buổi khám")} className="mt-4 flex items-center justify-between gap-3"><Button type="button" disabled={registrationLoading || registrationPage === 0} onClick={() => void loadRegistrations(selectedSession, registrationPage - 1)}><L10n>{"Trang trước"}</L10n></Button><span className="text-sm"><L10n>{"Trang"}</L10n> <L10nValue>{registrationPage + 1}</L10nValue> / <L10nValue>{registrationPages}</L10nValue></span><Button type="button" disabled={registrationLoading || registrationPage + 1 >= registrationPages} onClick={() => void loadRegistrations(selectedSession, registrationPage + 1)}><L10n>{"Trang sau"}</L10n></Button></nav> : null}</L10nValue>
    </section> : null}</L10nValue>
  </div>;
}
