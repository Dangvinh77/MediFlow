"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useRef, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/session";
import { CommandIntentError, prepareCommandIntent } from "@/lib/command-intent";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { ReferenceText } from "@/components/references/ReferenceText";
import { sessionStatusLabels } from "../presentation";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { formatLocalDate } from "@/lib/format";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";
import { formatScheduleObservedAt, initialScheduleDate } from "../schedule-presentation";
import { ScheduleDateInput } from "./ScheduleDateInput";
import { ScheduleImpactPanel } from "./ScheduleImpactPanel";
import { sessionDateErrors, shiftRange } from "../session-dates";
import { appointmentApi } from "../api";
import type {
  ConsultationBreak,
  ConsultationRoomSummary,
  ConsultationSessionCreate,
  ConsultationSessionDTO,
  ConsultationSessionStatusCommand,
  ConsultationSessionUpdate,
} from "../types";

interface WorkspaceError { message: string; correlationId: string | null; uncertain?: boolean }

const dateInputClass = "min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 text-sm tabular-nums focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary";

function errorFrom(cause: unknown, fallback: string): WorkspaceError {
  return cause instanceof ApiRequestError
    ? { message: cause.message, correlationId: cause.correlationId }
    : { message: cause instanceof Error ? cause.message : fallback, correlationId: null };
}

function outcomeUnknown(cause: unknown): boolean {
  return cause instanceof CommandIntentError || !(cause instanceof ApiRequestError)
    || cause.status < 400 || cause.status === 408 || cause.status >= 500;
}

type SessionMutation =
  | { kind: "create"; scope: string; body: ConsultationSessionCreate }
  | { kind: "update"; scope: string; sessionId: string; body: ConsultationSessionUpdate }
  | { kind: "status"; scope: string; sessionId: string; body: ConsultationSessionStatusCommand };

interface SessionFormState {
  doctorId: string;
  roomId: string;
  sessionDate: string;
  startTime: string;
  endTime: string;
  capacity: string;
  breaks: ConsultationBreak[];
  reason: string;
}

function emptySessionForm(): SessionFormState {
  return { doctorId: "", roomId: "", sessionDate: "", startTime: "08:00", endTime: "12:00", capacity: "20", breaks: [], reason: "" };
}

export function SessionAdministration({ fixedDepartmentId = "", checking = false, initialDate, clinicIntakeEnabled = false }: { fixedDepartmentId?: string; checking?: boolean; initialDate?: string; clinicIntakeEnabled?: boolean }) {
  const { localize } = useLocale();
  const [departmentId, setDepartmentId] = useState(fixedDepartmentId);
  const { markDirty, markSaved, confirmLeave } = useUnsavedChanges();
  const reads = useRef(0);
  const roomReads = useRef(0);
  const active = useRef(true);
  const mutationLock = useRef(false);
  useEffect(() => { active.current = true; const listReads = reads; const roomsReads = roomReads; return () => { active.current = false; listReads.current++; roomsReads.current++; }; }, []);
  const [fromDate, setFromDate] = useState(() => initialScheduleDate(initialDate));
  const [toDate, setToDate] = useState(() => initialScheduleDate(initialDate));
  const [dateErrors, setDateErrors] = useState<Partial<Record<"fromDate" | "toDate", string>>>({});
  const [sessions, setSessions] = useState<ConsultationSessionDTO[]>([]);
  const [sessionPage, setSessionPage] = useState(0);
  const [sessionPages, setSessionPages] = useState(0);
  const [listLoaded, setListLoaded] = useState(false);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState<WorkspaceError | null>(null);
  const [rooms, setRooms] = useState<ConsultationRoomSummary[]>([]);
  const [roomError, setRoomError] = useState<WorkspaceError | null>(null);
  const [form, setForm] = useState<SessionFormState>(emptySessionForm);
  const [editing, setEditing] = useState<ConsultationSessionDTO | null>(null);
  const [statusReason, setStatusReason] = useState("");
  const [mutationError, setMutationError] = useState<WorkspaceError | null>(null);
  const [mutating, setMutating] = useState(false);
  const [lastMutation, setLastMutation] = useState<SessionMutation | null>(null);
  const [lockNotice, setLockNotice] = useState<string | null>(null);
  const [roomsLoaded, setRoomsLoaded] = useState(false);
  const [impactSessionId, setImpactSessionId] = useState<string | null>(null);

  async function loadRooms() {
    if (!departmentId.trim()) { setRoomError({ message: "Chọn khoa trước khi tải phòng.", correlationId: null }); return; }
    const generation = ++roomReads.current; const session = getSessionKey();
    setRoomError(null); setRooms([]); setRoomsLoaded(false);
    try {
      const result: ConsultationRoomSummary[] = []; let page = 0; let pages = 1;
      while (page < pages) { const value = await appointmentApi.consultationRooms(departmentId.trim(), page, 50); result.push(...value.content); pages = value.totalPages; page++; }
      if (active.current && generation === roomReads.current && session === getSessionKey()) { setRooms(result); setRoomsLoaded(true); }
    } catch (cause) { if (active.current && generation === roomReads.current && session === getSessionKey()) setRoomError(errorFrom(cause, "Không thể tải phòng tư vấn.")); }
  }

  async function loadSessions(page = 0) {
    if (!departmentId.trim() || listLoading) { if (!departmentId.trim()) setListError({ message: "Chọn khoa để xem các buổi khám.", correlationId: null }); return; }
    const errors = sessionDateErrors(fromDate, toDate);
    setDateErrors(errors);
    if (Object.keys(errors).length) return;
    const generation = ++reads.current; const session = getSessionKey();
    setListLoading(true); setListError(null); setListLoaded(false); setSessions([]);
    try {
      const result = await appointmentApi.listConsultationSessions({ departmentId: departmentId.trim(), fromDate, toDate, page, size: 20 });
      if (!active.current || generation !== reads.current || session !== getSessionKey()) return;
      setListLoaded(true); setSessions(result.content); setSessionPage(result.number); setSessionPages(result.totalPages);
    } catch (cause) { if (active.current && generation === reads.current && session === getSessionKey()) setListError(errorFrom(cause, "Không thể tải buổi khám.")); }
    finally { if (active.current && generation === reads.current && session === getSessionKey()) setListLoading(false); }
  }

  function updateForm<K extends keyof SessionFormState>(key: K, value: SessionFormState[K]) {
    markDirty(); setForm(current => ({ ...current, [key]: value }));
  }

  async function executeMutation(command: SessionMutation) {
    if (mutationLock.current || checking) return;
    mutationLock.current = true; reads.current++; setListLoading(false); const session = getSessionKey();
    setMutating(true); setMutationError(null); setLastMutation(command); setImpactSessionId(null);
    try {
      const intent = await prepareCommandIntent(command.scope, session, command.body);
      if (session !== getSessionKey()) return;
      const result = command.kind === "create"
        ? await appointmentApi.createSession(command.body, { idempotencyKey: intent.key, correlationId: intent.correlationId })
        : command.kind === "update"
          ? await appointmentApi.updateSession(command.sessionId, command.body, { idempotencyKey: intent.key, correlationId: intent.correlationId })
          : await appointmentApi.changeSessionStatus(command.sessionId, command.body, { idempotencyKey: intent.key, correlationId: intent.correlationId });
      intent.settle();
      if (!active.current || session !== getSessionKey()) return;
      markSaved();
      setSessions(current => command.kind === "create" ? [result, ...current.filter(item => item.sessionId !== result.sessionId)] : current.map(item => item.sessionId === result.sessionId ? result : item));
      setMutationError(null); setEditing(null); setForm(emptySessionForm());
    } catch (cause) {
      if (!active.current || session !== getSessionKey()) return;
      if (!(cause instanceof CommandIntentError)) {
        try { const intent = await prepareCommandIntent(command.scope, session, command.body); intent.settle(cause); } catch { /* Keep unresolved intent locked. */ }
      }
      setMutationError({ ...errorFrom(cause, "Không thể lưu thay đổi buổi khám."), uncertain: outcomeUnknown(cause) });
      if (clinicIntakeEnabled && command.kind !== "create" && cause instanceof ApiRequestError && cause.code === "SESSION_HAS_REGISTRATIONS") setImpactSessionId(command.sessionId);
    } finally { mutationLock.current = false; if (active.current && session === getSessionKey()) setMutating(false); }
  }

  function submitSession(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mutationError?.uncertain && lastMutation) { void executeMutation(lastMutation); return; }
    if (!departmentId.trim()) { setLockNotice("Chọn khoa buổi khám trước khi tạo buổi khám."); return; }
    if (!departmentId.trim() || !form.doctorId.trim() || !form.roomId || !form.sessionDate || !form.startTime || !form.endTime) {
      setMutationError({ message: "Chọn khoa, bác sĩ, phòng và thời gian đầy đủ trước khi lưu.", correlationId: null });
      return;
    }
    const base: ConsultationSessionCreate = {
      doctorId: form.doctorId.trim(), departmentId: departmentId.trim(), roomId: form.roomId,
      sessionDate: form.sessionDate, startTime: form.startTime, endTime: form.endTime,
      breaks: form.breaks.map(item => ({ startTime: item.startTime, endTime: item.endTime })), capacity: Number(form.capacity),
    };
    if (editing) {
      if (!form.reason.trim()) return;
      const body: ConsultationSessionUpdate = { ...base, expectedRevision: editing.revision, reason: form.reason.trim() };
      void executeMutation({ kind: "update", sessionId: editing.sessionId, scope: `consultation-session:update:${editing.sessionId}`, body });
    } else {
      void executeMutation({ kind: "create", scope: `consultation-session:create:${base.departmentId}:${base.sessionDate}`, body: base });
    }
  }

  function beginEdit(session: ConsultationSessionDTO) {
    if (!confirmLeave()) return;
    setEditing(session); setMutationError(null); setImpactSessionId(null);
    setForm({ doctorId: session.doctorId, roomId: session.roomId, sessionDate: session.sessionDate, startTime: session.startTime, endTime: session.endTime, capacity: String(session.capacity), breaks: session.breaks.map(item => ({ ...item })), reason: "" });
    setDepartmentId(session.departmentId);
    setRooms(current => current.some(room => room.roomId === session.roomId) ? current : [...current, { roomId: session.roomId, departmentId: session.departmentId, roomName: "Phòng hiện tại", roomType: "CONSULTATION" }]);
  }

  function changeStatus(session: ConsultationSessionDTO, status: "OPEN" | "STOPPED" | "CANCELLED") {
    if (!statusReason.trim()) { setMutationError({ message: "Nhập lý do trước khi đổi trạng thái buổi khám.", correlationId: null }); return; }
    const body: ConsultationSessionStatusCommand = { expectedRevision: session.revision, status, reason: statusReason.trim() };
    void executeMutation({ kind: "status", sessionId: session.sessionId, scope: `consultation-session:status:${session.sessionId}:${status}`, body });
  }

  function changeDepartment(value: string) {
    if (!confirmLeave()) return;
    reads.current++; roomReads.current++; setListLoading(false);
    setDepartmentId(value); setListLoaded(false); setRooms([]); setRoomsLoaded(false); setLockNotice(null); setSessions([]); setSessionPage(0); setSessionPages(0); setEditing(null); setForm(emptySessionForm()); setListError(null); setRoomError(null); setMutationError(null); setImpactSessionId(null);
  }

  // Rooms only serve the create/edit form; load them as soon as a department is chosen.
  useEffect(() => {
    if (!departmentId.trim()) return;
    const timer = setTimeout(() => { if (active.current) void loadRooms(); }, 0);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [departmentId]);

  useEffect(() => {
    if (!fixedDepartmentId) return;
    const timer = setTimeout(() => { if (active.current) void loadSessions(); }, 0);
    return () => clearTimeout(timer);
    // Initial appointed-scope load; filter changes are submitted explicitly.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fixedDepartmentId]);

  function changeDates(from: string, to: string) { reads.current++; setListLoading(false); setListLoaded(false); setSessions([]); setFromDate(from); setToDate(to); }
  const retryMutation = mutationError?.uncertain ? lastMutation : null;
  return <section className="space-y-6 rounded-xl border border-border bg-surface p-5">
    <p className="text-sm text-muted-foreground"><L10n>{"Chọn khoa và khoảng ngày để xem buổi đã lập. Giờ khám từ 07:00 đến 17:00; hệ thống kiểm tra phân công khoa, lịch trùng và số chỗ trước khi lưu."}</L10n></p>
    <L10nValue>{fixedDepartmentId ? <div className="flex gap-3"><Button variant="secondary" onClick={() => changeDates(fromDate, fromDate)}><L10n>{"Theo ngày"}</L10n></Button><Button variant="secondary" disabled={!fromDate} onClick={() => { const range = shiftRange(fromDate,"week"); changeDates(range.fromDate, range.toDate); }}><L10n>{"Theo tuần"}</L10n></Button></div> : null}</L10nValue>
    <form noValidate aria-label={localize("Lọc buổi khám đã lập")} onSubmit={event => { event.preventDefault(); void loadSessions(); }} className={`grid gap-4 rounded-lg border border-border bg-surface-muted p-4 md:grid-cols-2 ${fixedDepartmentId ? "xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]" : "xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)_minmax(0,1fr)_auto]"}`}>
      <L10nValue>{!fixedDepartmentId ? <div className="min-w-0 text-sm"><span className="mb-1 block font-medium"><L10n>{"Khoa buổi khám"}</L10n></span>
        <ReferenceInput kind="department" scope="CLINICAL" value={departmentId} onValueChange={changeDepartment} aria-label={localize("Khoa buổi khám")} disabled={checking || mutating || Boolean(retryMutation)} />
      </div> : null}</L10nValue>
      <div><ScheduleDateInput label={localize("Từ ngày")} id="schedule-from-date" required aria-invalid={Boolean(dateErrors.fromDate)} aria-describedby={dateErrors.fromDate ? "schedule-from-error" : undefined} value={fromDate} onChange={event => changeDates(event.target.value,toDate)} className={dateInputClass} /><L10nValue>{dateErrors.fromDate ? <span id="schedule-from-error" role="alert" className="mt-1 block text-sm text-danger">{dateErrors.fromDate}</span> : null}</L10nValue></div>
      <div><ScheduleDateInput label={localize("Đến ngày")} id="schedule-to-date" required aria-invalid={Boolean(dateErrors.toDate)} aria-describedby={dateErrors.toDate ? "schedule-to-error" : undefined} value={toDate} onChange={event => changeDates(fromDate,event.target.value)} className={dateInputClass} /><L10nValue>{dateErrors.toDate ? <span id="schedule-to-error" role="alert" className="mt-1 block text-sm text-danger">{dateErrors.toDate}</span> : null}</L10nValue></div>
      <div className="flex items-start md:pt-6"><Button type="submit" variant="primary" className="w-full xl:w-auto" loading={listLoading}><L10n>{"Tải buổi đã lập"}</L10n></Button></div>
    </form>
    <L10nValue>{listError ? <AsyncState kind="error" message={localize(listError.message)} correlationId={listError.correlationId} onRetry={() => void loadSessions()} /> : null}</L10nValue>
    <L10nValue>{roomError ? <AsyncState kind="error" message={localize(roomError.message)} correlationId={roomError.correlationId} onRetry={() => void loadRooms()} /> : null}</L10nValue>
    <L10nValue>{mutationError && lastMutation?.kind === "status" ? <div role="alert" className="space-y-3 rounded-lg border border-danger/40 p-4 text-sm text-danger"><p><L10nValue>{mutationError.message}</L10nValue></p><L10nValue>{mutationError.uncertain ? <><p><L10n>{"Kết quả chưa xác định. Gửi lại đúng yêu cầu để đối soát."}</L10n></p><Button disabled={checking || mutating} onClick={() => void executeMutation(lastMutation)}><L10n>{"Gửi lại thay đổi trạng thái"}</L10n></Button></> : null}</L10nValue></div> : null}</L10nValue>
    <L10nValue>{impactSessionId ? <ScheduleImpactPanel kind="session" id={impactSessionId} /> : null}</L10nValue>


    <section className="border-t border-border pt-5">
      <div className="flex flex-wrap items-baseline justify-between gap-2"><h3 className="font-semibold"><L10n>{"Buổi trong bộ lọc"}</L10n></h3><L10nValue>{sessions.length ? <span className="text-xs text-muted-foreground"><L10n>{"Cập nhật: "}</L10n><L10nValue>{formatScheduleObservedAt(sessions[0].observedAt)}</L10nValue></span> : null}</L10nValue></div>
      <L10nValue>{sessions.length ? <>
        <label className="mt-3 block max-w-xl space-y-1 text-sm font-medium" htmlFor="schedule-status-reason"><L10n>{"Lý do ngừng hoặc mở lại buổi"}</L10n><input id="schedule-status-reason" maxLength={500} value={statusReason} onChange={event => { markDirty(); setStatusReason(event.target.value); }} disabled={checking || mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
        <div className="mt-3 overflow-x-auto rounded-lg border border-border"><table className="w-full min-w-[60rem] text-left text-sm"><thead className="bg-surface-muted text-xs uppercase tracking-wide text-muted-foreground"><tr className="whitespace-nowrap"><th className="p-3"><L10n>{"Ngày"}</L10n></th><th className="p-3"><L10n>{"Giờ"}</L10n></th><th className="p-3"><L10n>{"Bác sĩ"}</L10n></th><th className="p-3"><L10n>{"Phòng"}</L10n></th><th className="p-3"><L10n>{"Trạng thái"}</L10n></th><th className="p-3"><L10n>{"Sức chứa"}</L10n></th><th className="p-3"><span className="sr-only"><L10n>{"Thao tác"}</L10n></span></th></tr></thead><tbody><L10nValue>{sessions.map(session => <tr key={session.sessionId} className="border-t border-border align-middle"><td className="whitespace-nowrap p-3 tabular-nums"><L10nValue>{formatLocalDate(session.sessionDate)}</L10nValue></td><td className="whitespace-nowrap p-3 tabular-nums">{session.startTime}–{session.endTime}</td><td className="p-3"><ReferenceText kind="doctor" value={session.doctorId} fallback="Bác sĩ" /></td><td className="p-3"><ReferenceText kind="room" value={session.roomId} fallback="Phòng khám" /></td><td className="whitespace-nowrap p-3"><L10nValue>{sessionStatusLabels[session.status]}</L10nValue></td><td className="whitespace-nowrap p-3 tabular-nums"><span className="font-medium"><L10n>{"Còn "}</L10n>{session.availableCount}/{session.capacity}</span><span className="block text-xs text-muted-foreground">{session.confirmedCount}<L10n>{" đã xác nhận"}</L10n></span></td><td className="p-3"><div className="flex flex-nowrap items-center justify-end gap-2 whitespace-nowrap"><L10nValue>{session.status !== "CANCELLED" ? <><Button type="button" disabled={checking || mutating || Boolean(retryMutation)} onClick={() => beginEdit(session)}><L10n>{"Sửa"}</L10n></Button><Button type="button" disabled={checking || mutating || Boolean(retryMutation)} onClick={() => changeStatus(session, session.status === "OPEN" ? "STOPPED" : "OPEN")}><L10nValue>{session.status === "OPEN" ? "Ngừng nhận" : "Mở lại"}</L10nValue></Button><Button type="button" variant="danger" disabled={checking || mutating || Boolean(retryMutation)} onClick={() => changeStatus(session, "CANCELLED")}><L10n>{"Hủy buổi"}</L10n></Button></> : <span className="text-xs text-muted-foreground"><L10n>{"Đã hủy"}</L10n></span>}</L10nValue></div></td></tr>)}</L10nValue></tbody></table></div>
        <L10nValue>{sessionPages > 1 ? <nav aria-label={localize("Trang buổi khám đã lập")} className="mt-3 flex items-center justify-between text-sm"><Button type="button" disabled={listLoading || sessionPage === 0} onClick={() => void loadSessions(sessionPage - 1)}><L10n>{"Trang trước"}</L10n></Button><span><L10n>{"Trang"}</L10n> <L10nValue>{sessionPage + 1}</L10nValue> / <L10nValue>{sessionPages}</L10nValue></span><Button type="button" disabled={listLoading || sessionPage + 1 >= sessionPages} onClick={() => void loadSessions(sessionPage + 1)}><L10n>{"Trang sau"}</L10n></Button></nav> : null}</L10nValue>
      </> : listLoading ? <AsyncState kind="loading" message={localize("Đang tải buổi khám…")} /> : <p role="status" className="mt-2 text-sm text-muted-foreground"><L10nValue>{listLoaded ? "Chưa có buổi khám trong bộ lọc." : "Chọn khoa và khoảng ngày để tải buổi khám đã lập."}</L10nValue></p>}</L10nValue>
    </section>
    <details open={editing ? true : undefined} className="rounded-lg border border-border p-4"><summary className="cursor-pointer"><h3 className="inline font-semibold"><L10nValue>{editing ? "Sửa buổi khám đã chọn" : "Tạo buổi khám"}</L10nValue></h3></summary>
    <L10nValue>{lockNotice ? <p role="alert" className="mt-4 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm"><L10nValue>{lockNotice}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{!departmentId.trim() ? <p className="mt-3 text-sm text-muted-foreground"><L10n>{"Thứ tự lập buổi: chọn khoa → chọn bác sĩ của khoa → chọn phòng khám được hoạch định cho khoa."}</L10n></p> : null}</L10nValue>
    <div className="relative">
    <L10nValue>{!departmentId.trim() ? <button type="button" aria-label={localize("Tạo buổi khám đang khóa: chọn khoa trước")} onClick={() => setLockNotice("Chọn khoa buổi khám trước, sau đó chọn bác sĩ rồi mới chọn phòng khám của khoa.")} className="absolute inset-0 z-10 cursor-not-allowed rounded-lg focus-visible:outline-2 focus-visible:outline-primary" /> : null}</L10nValue>
    <form aria-label={localize(editing ? "Sửa buổi khám" : "Tạo buổi khám")} className={`grid gap-4 border-t border-border pt-5 sm:grid-cols-2 xl:grid-cols-4 ${!departmentId.trim() ? "opacity-55" : ""}`} onChangeCapture={markDirty} onSubmit={submitSession}>
    <fieldset disabled={!departmentId.trim()} className="contents">
      <div className="space-y-1 text-sm font-medium"><L10n>{"Bác sĩ"}</L10n><ReferenceInput kind="doctor" scope={departmentId} value={form.doctorId} onValueChange={value => { updateForm("doctorId", value); if (!value) updateForm("roomId", ""); }} aria-label={localize("Bác sĩ")} disabled={!departmentId.trim() || checking || mutating || Boolean(retryMutation)} />
      </div>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-room"><L10n>{"Phòng khám"}</L10n><select id="schedule-room" required value={form.roomId} onChange={event => updateForm("roomId", event.target.value)} disabled={!departmentId.trim() || !form.doctorId.trim() || checking || mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3"><option value=""><L10nValue>{form.doctorId.trim() ? "Chọn phòng khám" : "Chọn bác sĩ trước"}</L10nValue></option><L10nValue>{rooms.map(room => <option key={room.roomId} value={room.roomId}><L10nValue>{room.roomNumber ? `P.${room.roomNumber} · ${room.roomName}${room.zone ? ` · ${room.zone}` : ""}` : room.roomName}</L10nValue></option>)}</L10nValue></select>
        <L10nValue>{roomsLoaded && rooms.length === 0 ? <span role="status" className="block text-xs font-normal text-danger"><L10n>{"Khoa chưa có phòng khám được hoạch định. Quản trị viên thêm phòng tại Tổ chức → Phòng làm việc."}</L10n></span> : null}</L10nValue>
      </label>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-session-date"><L10n>{"Ngày khám"}</L10n><input id="schedule-session-date" type="date" required value={form.sessionDate} onChange={event => updateForm("sessionDate", event.target.value)} disabled={checking || mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-start-time"><L10n>{"Bắt đầu"}</L10n><input id="schedule-start-time" type="time" required value={form.startTime} onChange={event => updateForm("startTime", event.target.value)} disabled={checking || mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-end-time"><L10n>{"Kết thúc"}</L10n><input id="schedule-end-time" type="time" required value={form.endTime} onChange={event => updateForm("endTime", event.target.value)} disabled={checking || mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-capacity"><L10n>{"Sức chứa"}</L10n><input id="schedule-capacity" type="number" min={1} max={200} required value={form.capacity} onChange={event => updateForm("capacity", event.target.value)} disabled={checking || mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
      <L10nValue>{editing ? <label className="space-y-1 text-sm font-medium sm:col-span-2" htmlFor="schedule-edit-reason"><L10n>{"Lý do sửa buổi"}</L10n><input id="schedule-edit-reason" required maxLength={500} value={form.reason} onChange={event => updateForm("reason", event.target.value)} disabled={checking || mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label> : null}</L10nValue>
      <fieldset disabled={checking || mutating || Boolean(retryMutation)} className="space-y-2 sm:col-span-2 xl:col-span-4"><legend className="text-sm font-medium"><L10n>{"Giờ nghỉ tùy chọn"}</L10n></legend><L10nValue>{form.breaks.map((item, index) => <div key={index} className="flex flex-wrap items-end gap-3"><label className="space-y-1 text-sm"><L10n>{"Bắt đầu nghỉ"}</L10n><input type="time" required value={item.startTime} onChange={event => setForm(current => ({ ...current, breaks: current.breaks.map((value, row) => row === index ? { ...value, startTime: event.target.value } : value) }))} className="min-h-11 rounded-lg border border-control-border bg-surface px-3" /></label><label className="space-y-1 text-sm"><L10n>{"Kết thúc nghỉ"}</L10n><input type="time" required value={item.endTime} onChange={event => setForm(current => ({ ...current, breaks: current.breaks.map((value, row) => row === index ? { ...value, endTime: event.target.value } : value) }))} className="min-h-11 rounded-lg border border-control-border bg-surface px-3" /></label><Button type="button" variant="danger" onClick={() => { markDirty(); setForm(current => ({ ...current, breaks: current.breaks.filter((_, row) => row !== index) })); }}><L10n>{"Xóa giờ nghỉ"}</L10n></Button></div>)}</L10nValue><Button type="button" variant="secondary" disabled={form.breaks.length >= 20} onClick={() => { markDirty(); setForm(current => ({ ...current, breaks: [...current.breaks, { startTime: "10:00", endTime: "10:15" }] })); }}><L10n>{"Thêm giờ nghỉ"}</L10n></Button></fieldset>
      <L10nValue>{mutationError ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger sm:col-span-2 xl:col-span-4"><p><L10nValue>{mutationError.message}</L10nValue></p><L10nValue>{mutationError.correlationId ? <p className="mt-1 text-xs"><L10n>{"Mã theo dõi: "}</L10n>{mutationError.correlationId}</p> : null}</L10nValue><L10nValue>{retryMutation ? <p className="mt-2"><L10n>{"Kết quả chưa xác định. Gửi lại đúng nội dung để giữ nguyên khóa idempotency."}</L10n></p> : null}</L10nValue></div> : null}</L10nValue>
      <div className="flex flex-wrap gap-2 sm:col-span-2 xl:col-span-4"><Button type="submit" loading={mutating}><L10nValue>{retryMutation ? "Gửi lại cùng yêu cầu" : editing ? "Lưu thay đổi buổi" : "Tạo buổi khám"}</L10nValue></Button><L10nValue>{editing && !retryMutation ? <Button type="button" onClick={() => { if (!confirmLeave()) return; setEditing(null); setForm(emptySessionForm()); setMutationError(null); }}><L10n>{"Hủy sửa"}</L10n></Button> : null}</L10nValue></div>
    </fieldset>
    </form>
    </div>
    </details>

  </section>;
}

export function ScheduleManagementWorkspace({ enabled, clinicIntakeEnabled = false }: { enabled: boolean; clinicIntakeEnabled?: boolean }) {
  if (!enabled) return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground"><L10n>{"Quản lý lịch khám V2 chưa được mở."}</L10n></p>;
  return <div className="space-y-6"><SessionAdministration clinicIntakeEnabled={clinicIntakeEnabled} /></div>;
}
