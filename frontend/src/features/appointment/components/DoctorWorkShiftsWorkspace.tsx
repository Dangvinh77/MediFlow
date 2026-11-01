"use client";
import { useEffect, useRef, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/session";
import { prepareCommandIntent } from "@/lib/command-intent";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { ReferenceText } from "@/components/references/ReferenceText";
import { Button } from "@/components/ui/Button";
import { shiftRange } from "./MyWorkShiftsWorkspace";
import { appointmentApi } from "../api";
import { sessionDateErrors, vietnamToday } from "../session-dates";
import type { ConsultationBreak, ConsultationRoomSummary, ConsultationSessionCreate, DoctorWorkShiftCreate, DoctorWorkShiftDTO, DoctorWorkShiftUpdate } from "../types";
type Command = { kind: "create"; body: DoctorWorkShiftCreate } | { kind: "update"; id: string; body: DoctorWorkShiftUpdate } | { kind: "cancel"; id: string; body: { expectedRevision: number; reason: string } } | { kind: "session"; id: string; body: ConsultationSessionCreate };
export function DoctorWorkShiftsWorkspace({ departmentId, checking }: { departmentId: string; checking: boolean }) {
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
  const [busy, setBusy] = useState(false); const [error, setError] = useState(""); const [notice, setNotice] = useState("");
  const [pending, setPending] = useState<Command | null>(null); const lock = useRef(false); const mounted = useRef(true);
  const { markDirty, markSaved, confirmLeave } = useUnsavedChanges();
  useEffect(() => { mounted.current = true; const currentReads = reads; return () => { mounted.current = false; currentReads.current++; }; }, []);
  async function load(next = 0) {
    if (lock.current) return;
    const errors = sessionDateErrors(fromDate, toDate); if (Object.keys(errors).length) { setError(Object.values(errors).join(" ")); return; }
    const generation = ++reads.current; setBusy(true); setError(""); setLoaded(false); setRows([]); const session = getSessionKey();
    try { const result = await appointmentApi.workShifts({ departmentId, fromDate, toDate, page: next, size: 20 }); if (!mounted.current || generation !== reads.current || session !== getSessionKey()) return; setRows(result.content); setPages(result.totalPages); setPage(result.number); setLoaded(true); }
    catch (cause) { if (mounted.current && generation === reads.current && session === getSessionKey()) setError(cause instanceof Error ? cause.message : "Không thể tải giờ làm việc."); }
    finally { if (mounted.current && generation === reads.current && session === getSessionKey()) setBusy(false); }
  }
  useEffect(() => { const timer = setTimeout(() => { if (mounted.current) void load(); }, 0); return () => clearTimeout(timer); /* Initial day load. */
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [departmentId]);
  async function loadRooms() {
    const session = getSessionKey(); setError("");
    try { const result: ConsultationRoomSummary[] = []; let next = 0; let total = 1; while (next < total) { const value = await appointmentApi.consultationRooms(departmentId, next, 50); result.push(...value.content); total = value.totalPages; next++; } if (mounted.current && session === getSessionKey()) setRooms(result); }
    catch (cause) { if (mounted.current && session === getSessionKey()) setError(cause instanceof Error ? cause.message : "Không thể tải phòng."); }
  }
  async function execute(command: Command) {
    if (lock.current || checking) return;
    lock.current = true; setBusy(true); setError(""); setNotice(""); const session = getSessionKey();
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
      markSaved(); setPending(null); setCancelling(null); setSource(null); setEditing(null); setForm(initial()); setReason(""); setRows([]); setLoaded(false); setNotice(command.kind === "session" ? "Đã tạo buổi khám. Xem buổi khám trong tổng quan khoa." : "Đã lưu giờ làm việc. Tải lại danh sách để xem trạng thái mới nhất.");
    } catch (cause) {
      intent?.settle(cause); if (!mounted.current || session !== getSessionKey()) return;
      if (!(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500 || cause.code === "IDEMPOTENCY_CONFLICT") setPending(command);
      setError(cause instanceof Error ? cause.message : "Không thể lưu thay đổi.");
    } finally { lock.current = false; if (mounted.current && session === getSessionKey()) setBusy(false); }
  }
  function edit(row: DoctorWorkShiftDTO) { if (!confirmLeave()) return; setEditing(row); setSource(null); setForm({ doctorId: row.doctorId, departmentId, roomId: row.roomId, shiftDate: row.shiftDate, startTime: row.startTime, endTime: row.endTime }); setReason(""); void loadRooms(); }
  function sessionFrom(row: DoctorWorkShiftDTO) { if (!confirmLeave()) return; setSource(row); setEditing(null); setSessionStart(row.startTime); setSessionEnd(row.endTime); setCapacity(20); setBreaks([]); }
  function changeDates(from: string, to: string) { reads.current++; setBusy(false); setLoaded(false); setRows([]); setFromDate(from); setToDate(to); }
  const disabled = busy || Boolean(pending) || checking;
  const inputClass = "block min-h-11 w-full rounded-lg border border-control-border bg-surface px-3";
  return <div className="space-y-5">
    <section className="space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold">Giờ làm việc bác sĩ</h2><p className="text-sm text-muted-foreground">Phân công bác sĩ, giờ và phòng. Giờ làm việc chưa tạo chỗ đăng ký; cần tạo buổi khám riêng với sức chứa cụ thể.</p>
      <div className="flex flex-wrap gap-2"><Button variant="secondary" disabled={disabled} onClick={() => { const range = shiftRange(fromDate,"day"); setView("day"); changeDates(range.fromDate,range.toDate); }}>Theo ngày</Button><Button variant="secondary" disabled={disabled} onClick={() => { const range = shiftRange(fromDate,"week"); setView("week"); changeDates(range.fromDate,range.toDate); }}>Theo tuần</Button><span className="self-center text-sm text-muted-foreground">{view === "week" ? "Lịch tuần" : "Lịch ngày"}</span></div>
      <form onSubmit={event => { event.preventDefault(); void load(); }} className="flex flex-wrap items-end gap-3"><label className="text-sm">Từ ngày<input required type="date" value={fromDate} onChange={event => changeDates(event.target.value,toDate)} className={inputClass} /></label><label className="text-sm">Đến ngày<input required type="date" value={toDate} onChange={event => changeDates(fromDate,event.target.value)} className={inputClass} /></label><Button type="submit" disabled={disabled}>Tải giờ làm việc</Button></form>
      {loaded && !rows.length ? <p role="status">Không có giờ làm việc trong khoảng đã chọn.</p> : null}
      <div className="space-y-3">{rows.map(row => <article key={row.shiftId} className="space-y-2 rounded-lg border border-border p-4 text-sm"><p className="font-semibold">{row.shiftDate} · {row.startTime}–{row.endTime}</p><p><ReferenceText kind="doctor" value={row.doctorId} /> · <ReferenceText kind="room" value={row.roomId} /></p><p>{row.status === "ACTIVE" ? "Đang hiệu lực" : "Đã hủy"} · Phiên bản {row.revision}</p>{row.status === "ACTIVE" ? <div className="flex flex-wrap gap-2"><Button variant="secondary" disabled={disabled} onClick={() => edit(row)}>Sửa giờ làm việc</Button><Button disabled={disabled} onClick={() => sessionFrom(row)}>Tạo buổi khám từ giờ làm việc</Button><Button variant="danger" disabled={disabled} onClick={() => { if (!confirmLeave()) return; setCancelling(row); setReason(""); }}>Hủy giờ làm việc</Button></div> : null}{cancelling?.shiftId === row.shiftId ? <form className="space-y-3 border-t border-border pt-3" onSubmit={event => { event.preventDefault(); void execute({kind:"cancel",id:row.shiftId,body:{expectedRevision:row.revision,reason:reason.trim()}}); }}><p>Hủy phân công {row.shiftDate} · {row.startTime}–{row.endTime}. Buổi khám còn liên kết sẽ chặn hủy; hệ thống giữ nguyên người bệnh.</p><label className="block text-sm">Lý do hủy giờ làm việc<textarea required maxLength={500} disabled={disabled} value={reason} onChange={event => { markDirty(); setReason(event.target.value); }} className="block min-h-20 w-full rounded-lg border border-control-border bg-surface p-3" /></label><Button variant="danger" type="submit" disabled={disabled || !reason.trim()}>Xác nhận hủy giờ làm việc</Button></form> : null}</article>)}</div>
      {pages > 1 ? <div className="flex items-center gap-3"><Button disabled={disabled || page === 0} onClick={() => void load(page - 1)}>Trang trước</Button><span className="text-sm">Trang {page + 1}/{pages}</span><Button disabled={disabled || page + 1 >= pages} onClick={() => void load(page + 1)}>Trang sau</Button></div> : null}
    </section>
    {notice ? <p role="status" className="text-sm text-success">{notice}</p> : null}{error ? <p role="alert" className="rounded-lg border border-danger/40 p-4 text-sm text-danger">{error}</p> : null}
    {pending ? <section className="space-y-3 rounded-lg border border-border p-4"><p role="status" className="text-sm">Kết quả chưa xác định. Nội dung đã khóa; gửi lại đúng yêu cầu để đối soát.</p><Button loading={busy} disabled={checking} onClick={() => void execute(pending)}>Gửi lại cùng yêu cầu</Button></section> : null}
    {source || editing ? <Button variant="secondary" disabled={disabled} onClick={() => { if (!confirmLeave()) return; setSource(null); setEditing(null); setForm(initial()); setReason(""); }}>Về phân công mới</Button> : null}
    {source ? <form onChangeCapture={markDirty} onSubmit={event => { event.preventDefault(); void execute({ kind: "session", id: source.shiftId, body: { doctorId: source.doctorId, departmentId, roomId: source.roomId, sessionDate: source.shiftDate, startTime: sessionStart, endTime: sessionEnd, breaks, capacity } }); }} className="space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold">Tạo buổi khám</h2><p className="text-sm">Buổi khám nằm trong giờ làm việc {source.shiftDate} · {source.startTime}–{source.endTime}. Bác sĩ và phòng giữ theo phân công.</p><fieldset disabled={disabled} className="grid gap-4 sm:grid-cols-3"><label className="text-sm">Giờ bắt đầu buổi khám<input type="time" required min={source.startTime} max={source.endTime} value={sessionStart} onChange={event => setSessionStart(event.target.value)} className={inputClass} /></label><label className="text-sm">Giờ kết thúc buổi khám<input type="time" required min={sessionStart} max={source.endTime} value={sessionEnd} onChange={event => setSessionEnd(event.target.value)} className={inputClass} /></label><label className="text-sm">Sức chứa buổi khám<input aria-label="Sức chứa buổi khám" type="number" min={1} max={200} required value={capacity} onChange={event => setCapacity(Number(event.target.value))} className={inputClass} /></label></fieldset><fieldset disabled={disabled} className="space-y-3"><legend className="text-sm font-medium">Giờ nghỉ tùy chọn</legend>{breaks.map((item,index) => <div key={index} className="flex flex-wrap gap-3"><label className="text-sm">Bắt đầu nghỉ<input type="time" required min={sessionStart} max={sessionEnd} value={item.startTime} onChange={event => setBreaks(current => current.map((value,row) => row === index ? {...value,startTime:event.target.value} : value))} className={inputClass}/></label><label className="text-sm">Kết thúc nghỉ<input type="time" required min={item.startTime} max={sessionEnd} value={item.endTime} onChange={event => setBreaks(current => current.map((value,row) => row === index ? {...value,endTime:event.target.value} : value))} className={inputClass}/></label><Button type="button" variant="secondary" onClick={() => { markDirty(); setBreaks(current => current.filter((_,row) => row !== index)); }}>Xóa giờ nghỉ</Button></div>)}<Button type="button" variant="secondary" disabled={breaks.length >= 20} onClick={() => { markDirty(); setBreaks(current => [...current,{startTime:sessionStart,endTime:sessionStart}]); }}>Thêm giờ nghỉ</Button></fieldset><Button type="submit" disabled={disabled}>Xác nhận tạo buổi khám</Button></form> : <form onChangeCapture={markDirty} onSubmit={event => { event.preventDefault(); if (editing) void execute({ kind: "update", id: editing.shiftId, body: { ...form, expectedRevision: editing.revision, reason: reason.trim() } }); else void execute({ kind: "create", body: form }); }} className="space-y-4 rounded-xl border border-border bg-surface p-5"><h2 className="text-lg font-semibold">{editing ? "Sửa phân công giờ làm việc" : "Phân công giờ làm việc"}</h2><fieldset disabled={disabled} className="grid gap-4 sm:grid-cols-2"><label className="text-sm">Bác sĩ<ReferenceInput kind="doctor" scope={departmentId} value={form.doctorId} onValueChange={value => { markDirty(); setForm(current => ({ ...current, doctorId: value })); }} aria-label="Bác sĩ giờ làm việc" /></label><div><Button variant="secondary" type="button" onClick={() => void loadRooms()}>Tải phòng của khoa</Button><label className="mt-2 block text-sm">Phòng<select required value={form.roomId} onChange={event => setForm(current => ({ ...current, roomId: event.target.value }))} className={inputClass}><option value="">Chọn phòng</option>{rooms.map(room => <option key={room.roomId} value={room.roomId}>{room.roomName}</option>)}</select></label></div><label className="text-sm">Ngày làm việc<input required type="date" value={form.shiftDate} onChange={event => setForm(current => ({ ...current, shiftDate: event.target.value }))} className={inputClass} /></label><label className="text-sm">Giờ bắt đầu<input required type="time" min="07:00" max="17:00" value={form.startTime} onChange={event => setForm(current => ({ ...current, startTime: event.target.value }))} className={inputClass} /></label><label className="text-sm">Giờ kết thúc<input required type="time" min={form.startTime} max="17:00" value={form.endTime} onChange={event => setForm(current => ({ ...current, endTime: event.target.value }))} className={inputClass} /></label></fieldset><Button type="submit" disabled={disabled || !form.doctorId || !form.roomId || (Boolean(editing) && !reason.trim())}>{editing ? "Lưu sửa giờ làm việc" : "Lưu giờ làm việc"}</Button></form>}
    <label className="block space-y-1 text-sm">Lý do sửa hoặc hủy giờ làm việc<textarea aria-label="Lý do sửa hoặc hủy giờ làm việc" disabled={disabled} maxLength={500} value={reason} onChange={event => { markDirty(); setReason(event.target.value); }} className="block min-h-20 w-full rounded-lg border border-control-border bg-surface p-3" /></label>
  </div>;
}
