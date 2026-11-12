"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";

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
  const { localize } = useLocale();
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
    <section className="space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold"><L10n>{"Bộ lọc lịch làm việc"}</L10n></h2><p className="text-sm text-muted-foreground"><L10n>{"Phân công bác sĩ, giờ và phòng. Giờ làm việc chưa tạo chỗ đăng ký; cần tạo buổi khám riêng với sức chứa cụ thể."}</L10n></p>
      <div className="flex flex-wrap gap-2"><Button variant="secondary" disabled={filterDisabled || !fromDate} onClick={() => { const range = shiftRange(fromDate,"day"); setView("day"); changeDates(range.fromDate,range.toDate); }}><L10n>{"Theo ngày"}</L10n></Button><Button variant="secondary" disabled={filterDisabled || !fromDate} onClick={() => { const range = shiftRange(fromDate,"week"); setView("week"); changeDates(range.fromDate,range.toDate); }}><L10n>{"Theo tuần"}</L10n></Button><span className="self-center text-sm text-muted-foreground"><L10nValue>{view === "week" ? "Lịch tuần" : "Lịch ngày"}</L10nValue></span></div>
      <form onSubmit={event => { event.preventDefault(); void load(); }} className="flex flex-wrap items-end gap-3"><ScheduleDateInput label={localize("Từ ngày")} required disabled={filterDisabled} value={fromDate} onChange={event => selectDate(event.target.value,toDate,true)} className={inputClass} /><ScheduleDateInput label={localize("Đến ngày")} required disabled={filterDisabled} value={toDate} onChange={event => selectDate(event.target.value,fromDate,false)} className={inputClass} /><Button type="submit" disabled={disabled}><L10n>{"Tải giờ làm việc"}</L10n></Button></form>
      <L10nValue>{view === "week" ? <div className="flex flex-wrap items-center gap-3"><Button variant="secondary" disabled={filterDisabled || !fromDate} onClick={() => moveWeek(-1)}><L10n>{"Tuần trước"}</L10n></Button><span className="text-sm"><L10nValue>{fromDate && toDate ? `${formatLocalDate(fromDate)}–${formatLocalDate(toDate)}` : "Chọn ngày trong tuần"}</L10nValue></span><Button variant="secondary" disabled={filterDisabled || !fromDate} onClick={() => moveWeek(1)}><L10n>{"Tuần sau"}</L10n></Button></div> : null}</L10nValue>
      <L10nValue>{busy && !writing ? <AsyncState kind="loading" message={localize("Đang tải giờ làm việc…")} /> : null}</L10nValue>
      <L10nValue>{loaded && !rows.length ? <p role="status"><L10n>{"Không có giờ làm việc trong khoảng đã chọn."}</L10n></p> : null}</L10nValue>
      <L10nValue>{view === "week" && loaded ? <DoctorWorkShiftWeekGrid fromDate={fromDate} rows={rows} disabled={disabled} onEdit={edit} onSession={sessionFrom} onCancel={cancel} /> : null}</L10nValue>
      <L10nValue>{view === "day" ? <div className="space-y-3"><L10nValue>{rows.map(row => <article key={row.shiftId} className="space-y-2 rounded-lg border border-border p-4 text-sm"><p className="font-semibold"><L10nValue>{formatLocalDate(row.shiftDate)}</L10nValue> · {row.startTime}–{row.endTime}</p><p><ReferenceText kind="doctor" value={row.doctorId} /> · <ReferenceText kind="room" value={row.roomId} /></p><p><L10nValue>{row.status === "ACTIVE" ? "Đang hiệu lực" : "Đã hủy"}</L10nValue><L10n>{" · Phiên bản "}</L10n>{row.revision}</p><L10nValue>{row.status === "ACTIVE" ? <div className="flex flex-wrap gap-2"><Button variant="secondary" disabled={disabled} onClick={() => edit(row)}><L10n>{"Sửa giờ làm việc"}</L10n></Button><Button disabled={disabled} onClick={() => sessionFrom(row)}><L10n>{"Tạo buổi khám từ giờ làm việc"}</L10n></Button><Button variant="danger" disabled={disabled} onClick={() => cancel(row)}><L10n>{"Hủy giờ làm việc"}</L10n></Button></div> : null}</L10nValue><L10nValue>{cancelling?.shiftId === row.shiftId ? <form className="space-y-3 border-t border-border pt-3" onSubmit={event => { event.preventDefault(); void execute({kind:"cancel",id:row.shiftId,body:{expectedRevision:row.revision,reason:reason.trim()}}); }}><p><L10n>{"Hủy phân công "}</L10n><L10nValue>{formatLocalDate(row.shiftDate)}</L10nValue> · {row.startTime}–{row.endTime}. <L10nValue>{clinicIntakeEnabled ? "Các buổi khám còn mở của ca sẽ bị dừng; phiếu của người bệnh được giữ nguyên để đổi lịch." : "Buổi khám còn liên kết sẽ chặn hủy; hệ thống giữ nguyên người bệnh."}</L10nValue></p><L10nValue>{clinicIntakeEnabled ? <ScheduleImpactPanel kind="shift" id={row.shiftId} /> : null}</L10nValue><label className="block text-sm"><L10n>{"Lý do hủy giờ làm việc"}</L10n><textarea required maxLength={500} disabled={disabled} value={reason} onChange={event => { markDirty(); setReason(event.target.value); }} className="block min-h-20 w-full rounded-lg border border-control-border bg-surface p-3" /></label><Button variant="danger" type="submit" disabled={disabled || !reason.trim()}><L10n>{"Xác nhận hủy giờ làm việc"}</L10n></Button></form> : null}</L10nValue></article>)}</L10nValue></div> : null}</L10nValue>
      <L10nValue>{view === "week" && cancelling ? <form className="space-y-3 border-t border-border pt-3" onSubmit={event => { event.preventDefault(); void execute({kind:"cancel",id:cancelling.shiftId,body:{expectedRevision:cancelling.revision,reason:reason.trim()}}); }}><p><L10n>{"Hủy phân công "}</L10n><L10nValue>{formatLocalDate(cancelling.shiftDate)}</L10nValue> · {cancelling.startTime}–{cancelling.endTime}. <L10nValue>{clinicIntakeEnabled ? "Các buổi khám còn mở của ca sẽ bị dừng; phiếu của người bệnh được giữ nguyên để đổi lịch." : "Buổi khám còn liên kết sẽ chặn hủy; hệ thống giữ nguyên người bệnh."}</L10nValue></p><L10nValue>{clinicIntakeEnabled ? <ScheduleImpactPanel kind="shift" id={cancelling.shiftId} /> : null}</L10nValue><label className="block text-sm"><L10n>{"Lý do hủy giờ làm việc"}</L10n><textarea required maxLength={500} disabled={disabled} value={reason} onChange={event => { markDirty(); setReason(event.target.value); }} className="block min-h-20 w-full rounded-lg border border-control-border bg-surface p-3" /></label><Button variant="danger" type="submit" disabled={disabled || !reason.trim()}><L10n>{"Xác nhận hủy giờ làm việc"}</L10n></Button></form> : null}</L10nValue>
      <L10nValue>{view === "day" && pages > 1 ? <div className="flex items-center gap-3"><Button disabled={disabled || page === 0} onClick={() => void load(page - 1)}><L10n>{"Trang trước"}</L10n></Button><span className="text-sm">Trang <L10nValue>{page + 1}</L10nValue>/<L10nValue>{pages}</L10nValue></span><Button disabled={disabled || page + 1 >= pages} onClick={() => void load(page + 1)}>Trang sau</Button></div> : null}</L10nValue>
    </section>
    <L10nValue>{notice ? <p role="status" className="text-sm text-success"><L10nValue>{notice}</L10nValue></p> : null}</L10nValue><L10nValue>{createdSessionDate ? <Link className="inline-flex min-h-11 items-center rounded-lg border border-control-border bg-surface px-4 text-sm font-semibold text-primary" href={`/appointments/department/sessions?date=${encodeURIComponent(createdSessionDate)}`}><L10n>{"Xem buổi khám vừa tạo"}</L10n></Link> : null}</L10nValue><L10nValue>{error ? <p ref={errorTarget} tabIndex={-1} role="alert" className="scroll-mt-24 rounded-lg border border-danger/40 p-4 text-sm text-danger"><L10nValue>{error}</L10nValue></p> : null}</L10nValue>
    <L10nValue>{pending ? <section className="space-y-3 rounded-lg border border-border p-4"><p role="status" className="text-sm"><L10n>{"Kết quả chưa xác định. Nội dung đã khóa; gửi lại đúng yêu cầu để đối soát."}</L10n></p><Button loading={busy} disabled={checking} onClick={() => void execute(pending)}><L10n>{"Gửi lại cùng yêu cầu"}</L10n></Button></section> : null}</L10nValue>
    <L10nValue>{source || editing ? <Button variant="secondary" disabled={disabled} onClick={() => { if (!confirmLeave()) return; setSource(null); setEditing(null); setForm(initial()); setReason(""); }}><L10n>{"Về phân công mới"}</L10n></Button> : null}</L10nValue>
    <L10nValue>{source ? <form ref={editor} tabIndex={-1} onChangeCapture={markDirty} onSubmit={event => { event.preventDefault(); void execute({ kind: "session", id: source.shiftId, body: { doctorId: source.doctorId, departmentId, roomId: source.roomId, sessionDate: source.shiftDate, startTime: sessionStart, endTime: sessionEnd, breaks, capacity } }); }} className="scroll-mt-24 space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold"><L10n>{"Tạo buổi khám"}</L10n></h2><p className="text-sm"><L10n>{"Buổi khám nằm trong giờ làm việc "}</L10n><L10nValue>{formatLocalDate(source.shiftDate)}</L10nValue> · {source.startTime}–{source.endTime}<L10n>{". Bác sĩ và phòng giữ theo phân công."}</L10n></p><fieldset disabled={disabled} className="grid gap-4 sm:grid-cols-3"><label className="text-sm"><span className="mb-1 block font-medium"><L10n>{"Giờ bắt đầu buổi khám"}</L10n></span><input type="time" required min={source.startTime} max={source.endTime} value={sessionStart} onChange={event => setSessionStart(event.target.value)} className={inputClass} /></label><label className="text-sm"><span className="mb-1 block font-medium"><L10n>{"Giờ kết thúc buổi khám"}</L10n></span><input type="time" required min={sessionStart} max={source.endTime} value={sessionEnd} onChange={event => setSessionEnd(event.target.value)} className={inputClass} /></label><label className="text-sm"><span className="mb-1 block font-medium"><L10n>{"Sức chứa buổi khám"}</L10n></span><input aria-label={localize("Sức chứa buổi khám")} type="number" min={1} max={200} required value={capacity} onChange={event => setCapacity(Number(event.target.value))} className={inputClass} /></label></fieldset><fieldset disabled={disabled} className="space-y-3"><legend className="text-sm font-medium"><L10n>{"Giờ nghỉ tùy chọn"}</L10n></legend><L10nValue>{breaks.map((item,index) => <div key={index} className="flex flex-wrap gap-3"><label className="text-sm"><span className="mb-1 block font-medium"><L10n>{"Bắt đầu nghỉ"}</L10n></span><input type="time" required min={sessionStart} max={sessionEnd} value={item.startTime} onChange={event => setBreaks(current => current.map((value,row) => row === index ? {...value,startTime:event.target.value} : value))} className={inputClass}/></label><label className="text-sm"><span className="mb-1 block font-medium"><L10n>{"Kết thúc nghỉ"}</L10n></span><input type="time" required min={item.startTime} max={sessionEnd} value={item.endTime} onChange={event => setBreaks(current => current.map((value,row) => row === index ? {...value,endTime:event.target.value} : value))} className={inputClass}/></label><Button type="button" variant="secondary" onClick={() => { markDirty(); setBreaks(current => current.filter((_,row) => row !== index)); }}><L10n>{"Xóa giờ nghỉ"}</L10n></Button></div>)}</L10nValue><Button type="button" variant="secondary" disabled={breaks.length >= 20} onClick={() => { markDirty(); setBreaks(current => [...current,{startTime:sessionStart,endTime:sessionStart}]); }}><L10n>{"Thêm giờ nghỉ"}</L10n></Button></fieldset><Button type="submit" disabled={disabled}><L10n>{"Xác nhận tạo buổi khám"}</L10n></Button></form> : <form ref={editor} tabIndex={-1} onChangeCapture={markDirty} onSubmit={event => { event.preventDefault(); if (editing) void execute({ kind: "update", id: editing.shiftId, body: { ...form, expectedRevision: editing.revision, reason: reason.trim() } }); else void execute({ kind: "create", body: form }); }} className="scroll-mt-24 space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold"><L10nValue>{editing ? "Sửa phân công giờ làm việc" : "Phân công giờ làm việc"}</L10nValue></h2><p className="text-sm text-muted-foreground"><L10n>{"Giờ làm việc theo hệ 24 giờ: "}</L10n>{form.startTime}–{form.endTime}<L10n>{" (giờ Việt Nam)."}</L10n></p><fieldset disabled={disabled} className="grid gap-4 sm:grid-cols-2"><label className="text-sm"><span className="mb-1 block font-medium"><L10n>{"Bác sĩ"}</L10n></span><ReferenceInput kind="doctor" scope={departmentId} value={form.doctorId} onValueChange={value => { markDirty(); setForm(current => ({ ...current, doctorId: value })); }} aria-label={localize("Bác sĩ giờ làm việc")} /></label><div className="text-sm"><label htmlFor="doctor-shift-room" className="mb-1 block font-medium"><L10n>{"Phòng"}</L10n></label><div className="flex gap-2"><select id="doctor-shift-room" required value={form.roomId} onChange={event => setForm(current => ({ ...current, roomId: event.target.value }))} className={`${inputClass} min-w-0 flex-1`}><option value=""><L10n>{"Chọn phòng"}</L10n></option><L10nValue>{rooms.map(room => <option key={room.roomId} value={room.roomId}>{room.roomName}</option>)}</L10nValue></select><Button variant="secondary" type="button" className="shrink-0" onClick={() => void loadRooms()}><L10n>{"Tải phòng của khoa"}</L10n></Button></div></div><ScheduleDateInput label={localize("Ngày làm việc")} required value={form.shiftDate} onChange={event => setForm(current => ({ ...current, shiftDate: event.target.value }))} className={inputClass} /><label className="text-sm"><span className="mb-1 block font-medium"><L10n>{"Giờ bắt đầu"}</L10n></span><input required type="time" min="07:00" max="17:00" value={form.startTime} onChange={event => setForm(current => ({ ...current, startTime: event.target.value }))} className={inputClass} /></label><label className="text-sm"><span className="mb-1 block font-medium"><L10n>{"Giờ kết thúc"}</L10n></span><input required type="time" min={form.startTime} max="17:00" value={form.endTime} onChange={event => setForm(current => ({ ...current, endTime: event.target.value }))} className={inputClass} /></label></fieldset><Button type="submit" disabled={disabled || !form.doctorId || !form.roomId || (Boolean(editing) && !reason.trim())}><L10nValue>{editing ? "Lưu sửa giờ làm việc" : "Lưu giờ làm việc"}</L10nValue></Button></form>}</L10nValue>
    <label className="block space-y-1 text-sm"><L10n>{"Lý do sửa hoặc hủy giờ làm việc"}</L10n><textarea aria-label={localize("Lý do sửa hoặc hủy giờ làm việc")} disabled={disabled} maxLength={500} value={reason} onChange={event => { markDirty(); setReason(event.target.value); }} className="block min-h-20 w-full rounded-lg border border-control-border bg-surface p-3" /></label>
  </div>;
}
