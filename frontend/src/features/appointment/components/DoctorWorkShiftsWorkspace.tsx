"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { formatLocalDate } from "@/lib/format";
import { getSessionKey } from "@/lib/session";
import { CommandIntentError, prepareCommandIntent } from "@/lib/command-intent";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { ReferenceText } from "@/components/references/ReferenceText";
import { Button } from "@/components/ui/Button";
import { DoctorWorkShiftWeekGrid } from "./DoctorWorkShiftWeekGrid";
import { ScheduleDateInput } from "./ScheduleDateInput";
import { ScheduleImpactPanel } from "./ScheduleImpactPanel";
import { appointmentApi } from "../api";
import { sessionDateErrors, shiftRange, vietnamToday } from "../session-dates";
import type { ConsultationBreak, ConsultationRoomSummary, ConsultationSessionCreate, DoctorWorkShiftCreate, DoctorWorkShiftDTO, DoctorWorkShiftUpdate } from "../types";
type Command = { kind: "create"; body: DoctorWorkShiftCreate } | { kind: "update"; id: string; body: DoctorWorkShiftUpdate } | { kind: "cancel"; id: string; body: { expectedRevision: number; reason: string } } | { kind: "session"; id: string; body: ConsultationSessionCreate };
function shiftError(cause: unknown, fallback: string) {
  if (cause instanceof CommandIntentError) return cause.message;
  if (!(cause instanceof ApiRequestError)) return fallback;
  const messages: Record<string, string> = {
    SHIFT_HAS_LINKED_SESSIONS: "Ca còn buổi khám đang liên kết nên chưa thể hủy. Hãy xử lý các phiếu bị ảnh hưởng và các buổi khám liên quan trước.",
    SHIFT_CANCELLED: "Ca đã bị hủy trước đó. Hãy tải lại danh sách.",
    SHIFT_HAS_SESSIONS: "Ca đã có buổi khám đang hoạt động nên chưa thể sửa hoặc hủy. Hãy kiểm tra các buổi khám liên quan trước khi điều chỉnh phân công.",
    SHIFT_OVERLAP: "Bác sĩ hoặc phòng đã có ca trùng giờ. Hãy chọn khoảng giờ hoặc phòng khác.",
    SESSION_OVERLAP: "Buổi khám trùng giờ với buổi đã lập của bác sĩ hoặc phòng. Hãy kiểm tra lịch và chọn khoảng giờ khác trong ca.",
    SHIFT_SESSION_MISMATCH: "Buổi khám phải nằm trong ca và giữ đúng bác sĩ, khoa, phòng đã phân công.",
    SHIFT_INACTIVE: "Ca đã bị hủy. Hãy tải lại danh sách trước khi thao tác.",
    REVISION_CONFLICT: "Ca đã được cập nhật bởi phiên khác. Hãy tải lại danh sách và kiểm tra thay đổi.",
    SCHEDULE_INELIGIBLE: "Bác sĩ, khoa hoặc phòng không còn đáp ứng điều kiện phân công. Hãy kiểm tra lại lựa chọn.",
    INVALID_REQUEST: "Thông tin chưa hợp lệ. Hãy kiểm tra ngày, giờ, sức chứa và lý do điều chỉnh.",
  };
  return messages[cause.code ?? ""] ?? (cause.status === 403 ? "Bạn không còn quyền quản lý khoa này. Hãy kiểm tra lại bổ nhiệm." : fallback);
}
export function DoctorWorkShiftsWorkspace({ departmentId, checking, clinicIntakeEnabled = false }: { departmentId: string; checking: boolean; clinicIntakeEnabled?: boolean }) {
  const initial = (): DoctorWorkShiftCreate => ({ departmentId, doctorId: "", roomId: "", shiftDate: vietnamToday(), startTime: "08:00", endTime: "12:00" });
  const [form, setForm] = useState(initial); const [reason, setReason] = useState("");
  const [cancelling, setCancelling] = useState<DoctorWorkShiftDTO | null>(null);
  const [editing, setEditing] = useState<DoctorWorkShiftDTO | null>(null); const [source, setSource] = useState<DoctorWorkShiftDTO | null>(null);
  const [breaks, setBreaks] = useState<ConsultationBreak[]>([]);
  const [capacity, setCapacity] = useState(20); const [sessionStart, setSessionStart] = useState(""); const [sessionEnd, setSessionEnd] = useState("");
  const [rows, setRows] = useState<DoctorWorkShiftDTO[]>([]); const [rooms, setRooms] = useState<ConsultationRoomSummary[]>([]);
  const [fromDate, setFromDate] = useState(vietnamToday); const [toDate, setToDate] = useState(vietnamToday);
  const [view, setView] = useState<"day" | "week">("day");
  const reads = useRef(0);
  const [page, setPage] = useState(0); const [pages, setPages] = useState(0); const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false); const [writing, setWriting] = useState(false); const [error, setError] = useState(""); const [notice, setNotice] = useState("");
  const [createdSessionDate, setCreatedSessionDate] = useState<string | null>(null);
  const editor = useRef<HTMLFormElement>(null);
  const errorTarget = useRef<HTMLParagraphElement>(null);
  const [pending, setPending] = useState<Command | null>(null); const lock = useRef(false); const mounted = useRef(true);
  const { markDirty, markSaved, confirmLeave } = useUnsavedChanges();
  useEffect(() => {
    if (!source && !editing) return;
    editor.current?.scrollIntoView?.({ behavior: "smooth", block: "start" });
    editor.current?.focus({ preventScroll: true });
  }, [source, editing]);
  useEffect(() => {
    if (!error) return;
    errorTarget.current?.scrollIntoView?.({ block: "start" });
    errorTarget.current?.focus({ preventScroll: true });
  }, [error]);
  useEffect(() => { mounted.current = true; const currentReads = reads; return () => { mounted.current = false; currentReads.current++; }; }, []);
  async function readShifts(range: { fromDate: string; toDate: string }, mode: "day" | "week", next: number, generation: number, session: string | null) {
    const current = () => mounted.current && generation === reads.current && session === getSessionKey();
    const result = await appointmentApi.workShifts({ departmentId, ...range, page: mode === "week" ? 0 : next, size: mode === "week" ? 100 : 20 });
    if (!current()) return;
    const content = [...result.content];
    if (mode === "week") {
      for (let nextPage = 1; nextPage < result.totalPages; nextPage++) {
        const value = await appointmentApi.workShifts({ departmentId, ...range, page: nextPage, size: 100 });
        if (!current()) return;
        content.push(...value.content);
      }
    }
    return { ...result, content };
  }
  async function load(next = 0) {
    if (lock.current) return;
    const errors = sessionDateErrors(fromDate, toDate); if (Object.keys(errors).length) { setError(Object.values(errors).join(" ")); return; }
    const generation = ++reads.current; setBusy(true); setError(""); setLoaded(false); setRows([]); const session = getSessionKey();
    try { const result = await readShifts({ fromDate, toDate }, view, next, generation, session); if (!result) return; setRows(result.content); setPages(result.totalPages); setPage(result.number); setLoaded(true); }
    catch (cause) { if (mounted.current && generation === reads.current && session === getSessionKey()) setError(shiftError(cause, "Không thể tải giờ làm việc. Vui lòng thử lại.")); }
    finally { if (mounted.current && generation === reads.current && session === getSessionKey()) setBusy(false); }
  }
  useEffect(() => { const timer = setTimeout(() => { if (mounted.current) void load(); }, 0); return () => clearTimeout(timer); /* Initial day load. */
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [departmentId]);
  async function loadRooms() {
    const session = getSessionKey(); setError("");
    try { const result: ConsultationRoomSummary[] = []; let next = 0; let total = 1; while (next < total) { const value = await appointmentApi.consultationRooms(departmentId, next, 50); result.push(...value.content); total = value.totalPages; next++; } if (mounted.current && session === getSessionKey()) setRooms(result); }
    catch (cause) { if (mounted.current && session === getSessionKey()) setError(shiftError(cause, "Không thể tải phòng. Vui lòng thử lại.")); }
  }
  async function execute(command: Command) {
    if (lock.current || checking) return;
    lock.current = true; setWriting(true); reads.current++; setBusy(true); setError(""); setNotice(""); setCreatedSessionDate(null); const session = getSessionKey();
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | undefined;
    try {
      intent = await prepareCommandIntent(`doctor-work-shift:${command.kind}:${"id" in command ? command.id : departmentId}`, session, command.body);
      if (session !== getSessionKey()) return;
      const options = { idempotencyKey: intent.key, correlationId: intent.correlationId };
      if (command.kind === "create") await appointmentApi.createWorkShift(command.body, options);
      else if (command.kind === "update") await appointmentApi.updateWorkShift(command.id, command.body, options);
      else if (command.kind === "cancel") await appointmentApi.cancelWorkShift(command.id, command.body, options);
      else await appointmentApi.createSessionFromShift(command.id, command.body, options);
      intent.settle(); if (!mounted.current || session !== getSessionKey()) return;
      const affectedDate = command.kind === "session" ? command.body.sessionDate : command.kind === "cancel" ? cancelling?.shiftDate ?? rows.find(row => row.shiftId === command.id)?.shiftDate ?? fromDate : command.body.shiftDate;
      const validRange = !Object.keys(sessionDateErrors(fromDate, toDate)).length;
      const range = validRange && affectedDate >= fromDate && affectedDate <= toDate ? {fromDate, toDate} : shiftRange(affectedDate, view);
      markSaved(); setPending(null); setCancelling(null); setSource(null); setEditing(null); setForm({...initial(), shiftDate:affectedDate}); setReason("");
      setFromDate(range.fromDate); setToDate(range.toDate);
      setLoaded(false); setRows([]);
      setNotice(command.kind === "session" ? "Đã tạo buổi khám." : "Đã lưu giờ làm việc.");
      if (command.kind === "session") setCreatedSessionDate(affectedDate);
      // A failed follow-up read must never turn a committed write into an uncertain command.
      const generation = ++reads.current;
      try {
        const result = await readShifts(range, view, 0, generation, session);
        if (!result) return;
        setRows(result.content); setPages(result.totalPages); setPage(result.number); setLoaded(true);
      } catch {
        if (mounted.current && generation === reads.current && session === getSessionKey()) {
          setRows([]); setLoaded(false);
          setError("Thay đổi đã được lưu, nhưng chưa thể tải lại danh sách. Chọn Tải giờ làm việc để cập nhật; không cần gửi lại lệnh lưu.");
        }
      }
    } catch (cause) {
      intent?.settle(cause); if (!mounted.current || session !== getSessionKey()) return;
      const uncertain = !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500 || cause.code === "IDEMPOTENCY_CONFLICT";
      if (uncertain) setPending(command);
      setError(shiftError(cause, uncertain ? "Chưa thể xác nhận kết quả lưu. Vui lòng kiểm tra thông báo đối soát bên dưới." : "Không thể lưu thay đổi. Hãy kiểm tra thông tin và tải lại danh sách nếu cần."));
    } finally { lock.current = false; if (mounted.current && session === getSessionKey()) { setBusy(false); setWriting(false); } }
  }
  function edit(row: DoctorWorkShiftDTO) { if (!confirmLeave()) return; setError(""); setNotice(""); setCreatedSessionDate(null); setEditing(row); setSource(null); setForm({ doctorId: row.doctorId, departmentId, roomId: row.roomId, shiftDate: row.shiftDate, startTime: row.startTime, endTime: row.endTime }); setReason(""); void loadRooms(); }
  function sessionFrom(row: DoctorWorkShiftDTO) { if (!confirmLeave()) return; setError(""); setNotice(""); setCreatedSessionDate(null); setSource(row); setEditing(null); setSessionStart(row.startTime); setSessionEnd(row.endTime); setCapacity(20); setBreaks([]); }
  function changeDates(from: string, to: string) { reads.current++; setBusy(false); setLoaded(false); setRows([]); setFromDate(from); setToDate(to); }
  function selectDate(date: string, other: string, start: boolean) {
    if (view === "week" && !Object.keys(sessionDateErrors(date, date)).length) {
      const range = shiftRange(date, "week"); changeDates(range.fromDate, range.toDate);
    } else changeDates(start ? date : other, start ? other : date);
  }
  function moveWeek(offset: number) {
    const date = new Date(`${fromDate}T12:00:00Z`); date.setUTCDate(date.getUTCDate() + offset * 7);
    const range = shiftRange(date.toISOString().slice(0, 10), "week"); changeDates(range.fromDate, range.toDate);
  }
  function cancel(row: DoctorWorkShiftDTO) { if (!confirmLeave()) return; setCancelling(row); setReason(""); }
  const filterDisabled = Boolean(pending) || checking || writing;
  const disabled = busy || Boolean(pending) || checking;
  const inputClass = "block min-h-11 w-full rounded-lg border border-control-border bg-surface px-3";
  return <div className="space-y-5">
    <section className="space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold">Bộ lọc lịch làm việc</h2><p className="text-sm text-muted-foreground">Phân công bác sĩ, giờ và phòng. Giờ làm việc chưa tạo chỗ đăng ký; cần tạo buổi khám riêng với sức chứa cụ thể.</p>
      <div className="flex flex-wrap gap-2"><Button variant="secondary" disabled={filterDisabled || !fromDate} onClick={() => { const range = shiftRange(fromDate,"day"); setView("day"); changeDates(range.fromDate,range.toDate); }}>Theo ngày</Button><Button variant="secondary" disabled={filterDisabled || !fromDate} onClick={() => { const range = shiftRange(fromDate,"week"); setView("week"); changeDates(range.fromDate,range.toDate); }}>Theo tuần</Button><span className="self-center text-sm text-muted-foreground">{view === "week" ? "Lịch tuần" : "Lịch ngày"}</span></div>
      <form onSubmit={event => { event.preventDefault(); void load(); }} className="flex flex-wrap items-end gap-3"><ScheduleDateInput label="Từ ngày" required disabled={filterDisabled} value={fromDate} onChange={event => selectDate(event.target.value,toDate,true)} className={inputClass} /><ScheduleDateInput label="Đến ngày" required disabled={filterDisabled} value={toDate} onChange={event => selectDate(event.target.value,fromDate,false)} className={inputClass} /><Button type="submit" disabled={disabled}>Tải giờ làm việc</Button></form>
      {view === "week" ? <div className="flex flex-wrap items-center gap-3"><Button variant="secondary" disabled={filterDisabled || !fromDate} onClick={() => moveWeek(-1)}>Tuần trước</Button><span className="text-sm">{fromDate && toDate ? `${formatLocalDate(fromDate)}–${formatLocalDate(toDate)}` : "Chọn ngày trong tuần"}</span><Button variant="secondary" disabled={filterDisabled || !fromDate} onClick={() => moveWeek(1)}>Tuần sau</Button></div> : null}
      {busy && !writing ? <AsyncState kind="loading" message="Đang tải giờ làm việc…" /> : null}
      {loaded && !rows.length ? <p role="status">Không có giờ làm việc trong khoảng đã chọn.</p> : null}
      {view === "week" && loaded ? <DoctorWorkShiftWeekGrid fromDate={fromDate} rows={rows} disabled={disabled} onEdit={edit} onSession={sessionFrom} onCancel={cancel} /> : null}
      {view === "day" ? <div className="space-y-3">{rows.map(row => <article key={row.shiftId} className="space-y-2 rounded-lg border border-border p-4 text-sm"><p className="font-semibold">{formatLocalDate(row.shiftDate)} · {row.startTime}–{row.endTime}</p><p><ReferenceText kind="doctor" value={row.doctorId} /> · <ReferenceText kind="room" value={row.roomId} /></p><p>{row.status === "ACTIVE" ? "Đang hiệu lực" : "Đã hủy"} · Phiên bản {row.revision}</p>{row.status === "ACTIVE" ? <div className="flex flex-wrap gap-2"><Button variant="secondary" disabled={disabled} onClick={() => edit(row)}>Sửa giờ làm việc</Button><Button disabled={disabled} onClick={() => sessionFrom(row)}>Tạo buổi khám từ giờ làm việc</Button><Button variant="danger" disabled={disabled} onClick={() => cancel(row)}>Hủy giờ làm việc</Button></div> : null}{cancelling?.shiftId === row.shiftId ? <form className="space-y-3 border-t border-border pt-3" onSubmit={event => { event.preventDefault(); void execute({kind:"cancel",id:row.shiftId,body:{expectedRevision:row.revision,reason:reason.trim()}}); }}><p>Hủy phân công {formatLocalDate(row.shiftDate)} · {row.startTime}–{row.endTime}. Buổi khám còn liên kết sẽ chặn hủy; hệ thống giữ nguyên người bệnh.</p>{clinicIntakeEnabled ? <ScheduleImpactPanel kind="shift" id={row.shiftId} /> : null}<label className="block text-sm">Lý do hủy giờ làm việc<textarea required maxLength={500} disabled={disabled} value={reason} onChange={event => { markDirty(); setReason(event.target.value); }} className="block min-h-20 w-full rounded-lg border border-control-border bg-surface p-3" /></label><Button variant="danger" type="submit" disabled={disabled || !reason.trim()}>Xác nhận hủy giờ làm việc</Button></form> : null}</article>)}</div> : null}
      {view === "week" && cancelling ? <form className="space-y-3 border-t border-border pt-3" onSubmit={event => { event.preventDefault(); void execute({kind:"cancel",id:cancelling.shiftId,body:{expectedRevision:cancelling.revision,reason:reason.trim()}}); }}><p>Hủy phân công {formatLocalDate(cancelling.shiftDate)} · {cancelling.startTime}–{cancelling.endTime}. Buổi khám còn liên kết sẽ chặn hủy; hệ thống giữ nguyên người bệnh.</p>{clinicIntakeEnabled ? <ScheduleImpactPanel kind="shift" id={cancelling.shiftId} /> : null}<label className="block text-sm">Lý do hủy giờ làm việc<textarea required maxLength={500} disabled={disabled} value={reason} onChange={event => { markDirty(); setReason(event.target.value); }} className="block min-h-20 w-full rounded-lg border border-control-border bg-surface p-3" /></label><Button variant="danger" type="submit" disabled={disabled || !reason.trim()}>Xác nhận hủy giờ làm việc</Button></form> : null}
      {view === "day" && pages > 1 ? <div className="flex items-center gap-3"><Button disabled={disabled || page === 0} onClick={() => void load(page - 1)}>Trang trước</Button><span className="text-sm">Trang {page + 1}/{pages}</span><Button disabled={disabled || page + 1 >= pages} onClick={() => void load(page + 1)}>Trang sau</Button></div> : null}
    </section>
    {notice ? <p role="status" className="text-sm text-success">{notice}</p> : null}{createdSessionDate ? <Link className="inline-flex min-h-11 items-center rounded-lg border border-control-border bg-surface px-4 text-sm font-semibold text-primary" href={`/appointments/department/sessions?date=${encodeURIComponent(createdSessionDate)}`}>Xem buổi khám vừa tạo</Link> : null}{error ? <p ref={errorTarget} tabIndex={-1} role="alert" className="scroll-mt-24 rounded-lg border border-danger/40 p-4 text-sm text-danger">{error}</p> : null}
    {pending ? <section className="space-y-3 rounded-lg border border-border p-4"><p role="status" className="text-sm">Kết quả chưa xác định. Nội dung đã khóa; gửi lại đúng yêu cầu để đối soát.</p><Button loading={busy} disabled={checking} onClick={() => void execute(pending)}>Gửi lại cùng yêu cầu</Button></section> : null}
    {source || editing ? <Button variant="secondary" disabled={disabled} onClick={() => { if (!confirmLeave()) return; setSource(null); setEditing(null); setForm(initial()); setReason(""); }}>Về phân công mới</Button> : null}
    {source ? <form ref={editor} tabIndex={-1} onChangeCapture={markDirty} onSubmit={event => { event.preventDefault(); void execute({ kind: "session", id: source.shiftId, body: { doctorId: source.doctorId, departmentId, roomId: source.roomId, sessionDate: source.shiftDate, startTime: sessionStart, endTime: sessionEnd, breaks, capacity } }); }} className="scroll-mt-24 space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold">Tạo buổi khám</h2><p className="text-sm">Buổi khám nằm trong giờ làm việc {formatLocalDate(source.shiftDate)} · {source.startTime}–{source.endTime}. Bác sĩ và phòng giữ theo phân công.</p><fieldset disabled={disabled} className="grid gap-4 sm:grid-cols-3"><label className="text-sm"><span className="mb-1 block font-medium">Giờ bắt đầu buổi khám</span><input type="time" required min={source.startTime} max={source.endTime} value={sessionStart} onChange={event => setSessionStart(event.target.value)} className={inputClass} /></label><label className="text-sm"><span className="mb-1 block font-medium">Giờ kết thúc buổi khám</span><input type="time" required min={sessionStart} max={source.endTime} value={sessionEnd} onChange={event => setSessionEnd(event.target.value)} className={inputClass} /></label><label className="text-sm"><span className="mb-1 block font-medium">Sức chứa buổi khám</span><input aria-label="Sức chứa buổi khám" type="number" min={1} max={200} required value={capacity} onChange={event => setCapacity(Number(event.target.value))} className={inputClass} /></label></fieldset><fieldset disabled={disabled} className="space-y-3"><legend className="text-sm font-medium">Giờ nghỉ tùy chọn</legend>{breaks.map((item,index) => <div key={index} className="flex flex-wrap gap-3"><label className="text-sm"><span className="mb-1 block font-medium">Bắt đầu nghỉ</span><input type="time" required min={sessionStart} max={sessionEnd} value={item.startTime} onChange={event => setBreaks(current => current.map((value,row) => row === index ? {...value,startTime:event.target.value} : value))} className={inputClass}/></label><label className="text-sm"><span className="mb-1 block font-medium">Kết thúc nghỉ</span><input type="time" required min={item.startTime} max={sessionEnd} value={item.endTime} onChange={event => setBreaks(current => current.map((value,row) => row === index ? {...value,endTime:event.target.value} : value))} className={inputClass}/></label><Button type="button" variant="secondary" onClick={() => { markDirty(); setBreaks(current => current.filter((_,row) => row !== index)); }}>Xóa giờ nghỉ</Button></div>)}<Button type="button" variant="secondary" disabled={breaks.length >= 20} onClick={() => { markDirty(); setBreaks(current => [...current,{startTime:sessionStart,endTime:sessionStart}]); }}>Thêm giờ nghỉ</Button></fieldset><Button type="submit" disabled={disabled}>Xác nhận tạo buổi khám</Button></form> : <form ref={editor} tabIndex={-1} onChangeCapture={markDirty} onSubmit={event => { event.preventDefault(); if (editing) void execute({ kind: "update", id: editing.shiftId, body: { ...form, expectedRevision: editing.revision, reason: reason.trim() } }); else void execute({ kind: "create", body: form }); }} className="scroll-mt-24 space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold">{editing ? "Sửa phân công giờ làm việc" : "Phân công giờ làm việc"}</h2><p className="text-sm text-muted-foreground">Giờ làm việc theo hệ 24 giờ: {form.startTime}–{form.endTime} (giờ Việt Nam).</p><fieldset disabled={disabled} className="grid gap-4 sm:grid-cols-2"><label className="text-sm"><span className="mb-1 block font-medium">Bác sĩ</span><ReferenceInput kind="doctor" scope={departmentId} value={form.doctorId} onValueChange={value => { markDirty(); setForm(current => ({ ...current, doctorId: value })); }} aria-label="Bác sĩ giờ làm việc" /></label><div className="text-sm"><label htmlFor="doctor-shift-room" className="mb-1 block font-medium">Phòng</label><div className="flex gap-2"><select id="doctor-shift-room" required value={form.roomId} onChange={event => setForm(current => ({ ...current, roomId: event.target.value }))} className={`${inputClass} min-w-0 flex-1`}><option value="">Chọn phòng</option>{rooms.map(room => <option key={room.roomId} value={room.roomId}>{room.roomName}</option>)}</select><Button variant="secondary" type="button" className="shrink-0" onClick={() => void loadRooms()}>Tải phòng của khoa</Button></div></div><ScheduleDateInput label="Ngày làm việc" required value={form.shiftDate} onChange={event => setForm(current => ({ ...current, shiftDate: event.target.value }))} className={inputClass} /><label className="text-sm"><span className="mb-1 block font-medium">Giờ bắt đầu</span><input required type="time" min="07:00" max="17:00" value={form.startTime} onChange={event => setForm(current => ({ ...current, startTime: event.target.value }))} className={inputClass} /></label><label className="text-sm"><span className="mb-1 block font-medium">Giờ kết thúc</span><input required type="time" min={form.startTime} max="17:00" value={form.endTime} onChange={event => setForm(current => ({ ...current, endTime: event.target.value }))} className={inputClass} /></label></fieldset><Button type="submit" disabled={disabled || !form.doctorId || !form.roomId || (Boolean(editing) && !reason.trim())}>{editing ? "Lưu sửa giờ làm việc" : "Lưu giờ làm việc"}</Button></form>}
    <label className="block space-y-1 text-sm">Lý do sửa hoặc hủy giờ làm việc<textarea aria-label="Lý do sửa hoặc hủy giờ làm việc" disabled={disabled} maxLength={500} value={reason} onChange={event => { markDirty(); setReason(event.target.value); }} className="block min-h-20 w-full rounded-lg border border-control-border bg-surface p-3" /></label>
  </div>;
}
