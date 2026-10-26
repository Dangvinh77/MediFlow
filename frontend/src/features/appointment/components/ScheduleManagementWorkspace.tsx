"use client";

import { useState, useSyncExternalStore } from "react";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import { getSessionKey } from "@/lib/session";
import { CommandIntentError, prepareCommandIntent } from "@/lib/command-intent";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { formatLocalDate } from "@/lib/format";
import { organizationApi } from "@/features/organization/api";
import { appointmentApi } from "../api";
import type {
  ConsultationBreak,
  ConsultationRoomSummary,
  ConsultationSessionCreate,
  ConsultationSessionDTO,
  ConsultationSessionStatusCommand,
  ConsultationSessionUpdate,
  SchedulingScopeCommand,
  SchedulingScopeSnapshot,
} from "../types";

interface WorkspaceError { message: string; correlationId: string | null; uncertain?: boolean }

function errorFrom(cause: unknown, fallback: string): WorkspaceError {
  return cause instanceof ApiRequestError
    ? { message: cause.message, correlationId: cause.correlationId }
    : { message: cause instanceof Error ? cause.message : fallback, correlationId: null };
}

function outcomeUnknown(cause: unknown): boolean {
  return cause instanceof CommandIntentError || !(cause instanceof ApiRequestError)
    || cause.status < 400 || cause.status === 408 || cause.status >= 500;
}

function vietnamToday(): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts();
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function ScopeAdministration() {
  const [accountId, setAccountId] = useState("");
  const [scope, setScope] = useState<SchedulingScopeSnapshot | null>(null);
  const [departments, setDepartments] = useState<{ departmentId: string; departmentName: string }[]>([]);
  const [selectedDepartmentIds, setSelectedDepartmentIds] = useState<string[]>([]);
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<WorkspaceError | null>(null);
  const [lastCommand, setLastCommand] = useState<{ accountId: string; body: SchedulingScopeCommand } | null>(null);

  async function loadScope() {
    if (!accountId.trim() || loading) return;
    setLoading(true); setError(null); setScope(null); setDepartments([]); setSelectedDepartmentIds([]);
    try {
      const [snapshot, departmentList] = await Promise.all([
        appointmentApi.scheduleManagementScope(accountId.trim()), organizationApi.departments(true),
      ]);
      setScope(snapshot); setSelectedDepartmentIds(snapshot.departmentIds); setDepartments(departmentList);
    } catch (cause) {
      setError(errorFrom(cause, "Không thể tải phạm vi quản lý lịch."));
    } finally { setLoading(false); }
  }

  async function saveScope(command: { accountId: string; body: SchedulingScopeCommand }) {
    if (saving) return;
    setSaving(true); setError(null); setLastCommand(command);
    try {
      const intent = await prepareCommandIntent(`schedule-scope:${command.accountId}`, getSessionKey(), command.body);
      const result = await appointmentApi.replaceScheduleManagementScope(command.accountId, command.body, {
        idempotencyKey: intent.key, correlationId: intent.correlationId,
      });
      intent.settle(); setScope(result); setSelectedDepartmentIds(result.departmentIds); setError(null);
    } catch (cause) {
      if (!(cause instanceof CommandIntentError)) {
        try { const intent = await prepareCommandIntent(`schedule-scope:${command.accountId}`, getSessionKey(), command.body); intent.settle(cause); } catch { /* Keep unresolved intent locked. */ }
      }
      setError({ ...errorFrom(cause, "Không thể cập nhật phạm vi quản lý lịch."), uncertain: outcomeUnknown(cause) });
    } finally { setSaving(false); }
  }

  function toggleDepartment(departmentId: string, checked: boolean) {
    setSelectedDepartmentIds(current => checked ? [...new Set([...current, departmentId])] : current.filter(id => id !== departmentId));
  }

  function submitScope() {
    if (!scope || !reason.trim() || selectedDepartmentIds.length > 100) return;
    void saveScope({ accountId: scope.accountId, body: {
      expectedRevision: scope.revision,
      departmentIds: [...selectedDepartmentIds].sort(),
      reason: reason.trim(),
    } });
  }

  const retryCommand = error?.uncertain ? lastCommand : null;
  return <section className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <div><h2 className="text-lg font-semibold">Phạm vi của quản lý lịch</h2><p className="mt-1 text-sm text-muted-foreground">Chỉ quản lý đang hoạt động mới được cấp khoa. Danh sách khoa được gửi toàn bộ khi lưu; danh sách rỗng thu hồi quyền.</p></div>
    <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={event => { event.preventDefault(); void loadScope(); }}>
      <label className="flex-1 space-y-1 text-sm font-medium">Tài khoản quản lý
        <ReferenceInput kind="account" value={accountId} onValueChange={value => { setAccountId(value); setScope(null); setError(null); }} aria-label="Tài khoản quản lý" disabled={loading || saving || Boolean(retryCommand)} />
      </label>
      <Button type="submit" loading={loading}>Tải phạm vi</Button>
    </form>
    {error && !retryCommand ? <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={() => void loadScope()} /> : null}
    {scope ? <div className="space-y-4 border-t border-border pt-4">
      <p className="text-sm text-muted-foreground">Phiên bản phạm vi: {scope.revision} · Đã chọn {selectedDepartmentIds.length} khoa</p>
      {departments.length ? <fieldset disabled={saving || Boolean(retryCommand)} className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3"><legend className="mb-2 text-sm font-medium">Khoa được phép quản lý</legend>{departments.filter(item => item.departmentId).map(department => <label key={department.departmentId} className="flex min-h-11 items-center gap-3 rounded-lg border border-border px-3 text-sm"><input type="checkbox" checked={selectedDepartmentIds.includes(department.departmentId)} onChange={event => toggleDepartment(department.departmentId, event.target.checked)} />{department.departmentName}</label>)}</fieldset> : <p role="status" className="text-sm text-muted-foreground">Không có khoa đang hoạt động.</p>}
      <label className="block space-y-1 text-sm font-medium" htmlFor="schedule-scope-reason">Lý do cấp hoặc thu hồi quyền
        <textarea id="schedule-scope-reason" value={reason} maxLength={500} onChange={event => setReason(event.target.value)} disabled={saving || Boolean(retryCommand)} className="min-h-20 w-full rounded-lg border border-control-border bg-surface px-3 py-2" />
      </label>
      {error ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"><p>{error.message}</p>{error.correlationId ? <p className="mt-1 text-xs">Mã theo dõi: {error.correlationId}</p> : null}{retryCommand ? <p className="mt-2">Kết quả chưa xác định. Gửi lại cùng danh sách và lý do để giữ nguyên khóa idempotency.</p> : null}</div> : null}
      <Button type="button" loading={saving} disabled={Boolean(retryCommand) ? false : !reason.trim()} onClick={() => retryCommand ? void saveScope(retryCommand) : submitScope()}>{retryCommand ? "Gửi lại cùng yêu cầu" : selectedDepartmentIds.length ? "Lưu phạm vi" : "Thu hồi toàn bộ phạm vi"}</Button>
    </div> : null}
  </section>;
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

function SessionAdministration() {
  const [departmentId, setDepartmentId] = useState("");
  const [fromDate, setFromDate] = useState(vietnamToday);
  const [toDate, setToDate] = useState("");
  const [sessions, setSessions] = useState<ConsultationSessionDTO[]>([]);
  const [sessionPage, setSessionPage] = useState(0);
  const [sessionPages, setSessionPages] = useState(0);
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

  async function loadRooms() {
    if (!departmentId.trim()) { setRoomError({ message: "Chọn khoa trước khi tải phòng.", correlationId: null }); return; }
    setRoomError(null); setRooms([]);
    try {
      const result = await appointmentApi.consultationRooms(departmentId.trim(), 0, 50);
      setRooms(result.content);
    } catch (cause) { setRoomError(errorFrom(cause, "Không thể tải phòng tư vấn.")); }
  }

  async function loadSessions(page = 0) {
    if (!departmentId.trim() || listLoading) { if (!departmentId.trim()) setListError({ message: "Chọn khoa để xem các buổi khám.", correlationId: null }); return; }
    setListLoading(true); setListError(null);
    try {
      const result = await appointmentApi.listConsultationSessions({ departmentId: departmentId.trim(), fromDate: fromDate || undefined, toDate: toDate || undefined, page, size: 20 });
      setSessions(result.content); setSessionPage(result.number); setSessionPages(result.totalPages);
    } catch (cause) { setListError(errorFrom(cause, "Không thể tải buổi khám.")); }
    finally { setListLoading(false); }
  }

  function updateForm<K extends keyof SessionFormState>(key: K, value: SessionFormState[K]) {
    setForm(current => ({ ...current, [key]: value }));
  }

  async function executeMutation(command: SessionMutation) {
    if (mutating) return;
    setMutating(true); setMutationError(null); setLastMutation(command);
    try {
      const intent = await prepareCommandIntent(command.scope, getSessionKey(), command.body);
      const result = command.kind === "create"
        ? await appointmentApi.createSession(command.body, { idempotencyKey: intent.key, correlationId: intent.correlationId })
        : command.kind === "update"
          ? await appointmentApi.updateSession(command.sessionId, command.body, { idempotencyKey: intent.key, correlationId: intent.correlationId })
          : await appointmentApi.changeSessionStatus(command.sessionId, command.body, { idempotencyKey: intent.key, correlationId: intent.correlationId });
      intent.settle();
      setSessions(current => command.kind === "create" ? [result, ...current.filter(item => item.sessionId !== result.sessionId)] : current.map(item => item.sessionId === result.sessionId ? result : item));
      setMutationError(null); setEditing(null); setForm(emptySessionForm());
    } catch (cause) {
      if (!(cause instanceof CommandIntentError)) {
        try { const intent = await prepareCommandIntent(command.scope, getSessionKey(), command.body); intent.settle(cause); } catch { /* Keep unresolved intent locked. */ }
      }
      setMutationError({ ...errorFrom(cause, "Không thể lưu thay đổi buổi khám."), uncertain: outcomeUnknown(cause) });
    } finally { setMutating(false); }
  }

  function submitSession(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mutationError?.uncertain && lastMutation) { void executeMutation(lastMutation); return; }
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
    setEditing(session); setMutationError(null);
    setForm({ doctorId: session.doctorId, roomId: session.roomId, sessionDate: session.sessionDate, startTime: session.startTime, endTime: session.endTime, capacity: String(session.capacity), breaks: session.breaks.map(item => ({ ...item })), reason: "" });
    setDepartmentId(session.departmentId);
    setRooms(current => current.some(room => room.roomId === session.roomId) ? current : [...current, { roomId: session.roomId, departmentId: session.departmentId, roomName: session.roomId, roomType: "CONSULTATION" }]);
  }

  function changeStatus(session: ConsultationSessionDTO, status: "OPEN" | "STOPPED" | "CANCELLED") {
    if (!statusReason.trim()) { setMutationError({ message: "Nhập lý do trước khi đổi trạng thái buổi khám.", correlationId: null }); return; }
    const body: ConsultationSessionStatusCommand = { expectedRevision: session.revision, status, reason: statusReason.trim() };
    void executeMutation({ kind: "status", sessionId: session.sessionId, scope: `consultation-session:status:${session.sessionId}:${status}`, body });
  }

  function changeDepartment(value: string) {
    setDepartmentId(value); setRooms([]); setSessions([]); setSessionPage(0); setSessionPages(0); setEditing(null); setForm(emptySessionForm()); setListError(null); setRoomError(null); setMutationError(null);
  }

  const retryMutation = mutationError?.uncertain ? lastMutation : null;
  return <section className="space-y-6 rounded-xl border border-border bg-surface p-5">
    <div><h2 className="text-lg font-semibold">Lập và quản lý buổi khám</h2><p className="mt-1 text-sm text-muted-foreground">Chọn bác sĩ, khoa, phòng tư vấn và thời gian. Core và Clinical sẽ kiểm tra quyền khoa, nhân sự, phòng, giờ trùng và sức chứa.</p></div>
    <div className="grid gap-4 rounded-lg border border-border p-4 sm:grid-cols-2 xl:grid-cols-4">
      <label className="space-y-1 text-sm font-medium">Khoa buổi khám
        <ReferenceInput kind="department" value={departmentId} onValueChange={changeDepartment} aria-label="Khoa buổi khám" disabled={mutating || Boolean(retryMutation)} />
      </label>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-from-date">Từ ngày<input id="schedule-from-date" type="date" value={fromDate} onChange={event => setFromDate(event.target.value)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-to-date">Đến ngày<input id="schedule-to-date" type="date" value={toDate} onChange={event => setToDate(event.target.value)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
      <div className="flex items-end gap-2"><Button type="button" onClick={() => void loadSessions()} loading={listLoading}>Tải buổi đã lập</Button><Button type="button" variant="secondary" onClick={() => void loadRooms()}>Tải phòng tư vấn</Button></div>
    </div>
    {listError ? <AsyncState kind="error" message={listError.message} correlationId={listError.correlationId} onRetry={() => void loadSessions()} /> : null}
    {roomError ? <AsyncState kind="error" message={roomError.message} correlationId={roomError.correlationId} onRetry={() => void loadRooms()} /> : null}

    <form className="grid gap-4 border-t border-border pt-5 sm:grid-cols-2 xl:grid-cols-4" onSubmit={submitSession}>
      <div className="space-y-1 text-sm font-medium">Bác sĩ
        <ReferenceInput kind="doctor" scope={departmentId} value={form.doctorId} onValueChange={value => updateForm("doctorId", value)} aria-label="Bác sĩ" disabled={mutating || Boolean(retryMutation)} />
      </div>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-room">Phòng tư vấn
        <select id="schedule-room" required value={form.roomId} onChange={event => updateForm("roomId", event.target.value)} disabled={mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3"><option value="">Chọn phòng</option>{rooms.map(room => <option key={room.roomId} value={room.roomId}>{room.roomName}</option>)}</select>
      </label>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-session-date">Ngày khám<input id="schedule-session-date" type="date" required value={form.sessionDate} onChange={event => updateForm("sessionDate", event.target.value)} disabled={mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-start-time">Bắt đầu<input id="schedule-start-time" type="time" required value={form.startTime} onChange={event => updateForm("startTime", event.target.value)} disabled={mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-end-time">Kết thúc<input id="schedule-end-time" type="time" required value={form.endTime} onChange={event => updateForm("endTime", event.target.value)} disabled={mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
      <label className="space-y-1 text-sm font-medium" htmlFor="schedule-capacity">Sức chứa<input id="schedule-capacity" type="number" min={1} max={200} required value={form.capacity} onChange={event => updateForm("capacity", event.target.value)} disabled={mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
      {editing ? <label className="space-y-1 text-sm font-medium sm:col-span-2" htmlFor="schedule-edit-reason">Lý do sửa buổi<input id="schedule-edit-reason" required maxLength={500} value={form.reason} onChange={event => updateForm("reason", event.target.value)} disabled={mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label> : null}
      <fieldset disabled={mutating || Boolean(retryMutation)} className="space-y-2 sm:col-span-2 xl:col-span-4"><legend className="text-sm font-medium">Giờ nghỉ tùy chọn</legend>{form.breaks.map((item, index) => <div key={index} className="flex flex-wrap items-end gap-3"><label className="space-y-1 text-sm">Bắt đầu nghỉ<input type="time" required value={item.startTime} onChange={event => setForm(current => ({ ...current, breaks: current.breaks.map((value, row) => row === index ? { ...value, startTime: event.target.value } : value) }))} className="min-h-11 rounded-lg border border-control-border bg-surface px-3" /></label><label className="space-y-1 text-sm">Kết thúc nghỉ<input type="time" required value={item.endTime} onChange={event => setForm(current => ({ ...current, breaks: current.breaks.map((value, row) => row === index ? { ...value, endTime: event.target.value } : value) }))} className="min-h-11 rounded-lg border border-control-border bg-surface px-3" /></label><Button type="button" variant="danger" onClick={() => setForm(current => ({ ...current, breaks: current.breaks.filter((_, row) => row !== index) }))}>Xóa giờ nghỉ</Button></div>)}<Button type="button" variant="secondary" disabled={form.breaks.length >= 20} onClick={() => setForm(current => ({ ...current, breaks: [...current.breaks, { startTime: "10:00", endTime: "10:15" }] }))}>Thêm giờ nghỉ</Button></fieldset>
      {mutationError ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger sm:col-span-2 xl:col-span-4"><p>{mutationError.message}</p>{mutationError.correlationId ? <p className="mt-1 text-xs">Mã theo dõi: {mutationError.correlationId}</p> : null}{retryMutation ? <p className="mt-2">Kết quả chưa xác định. Gửi lại đúng nội dung để giữ nguyên khóa idempotency.</p> : null}</div> : null}
      <div className="flex flex-wrap gap-2 sm:col-span-2 xl:col-span-4"><Button type="submit" loading={mutating}>{retryMutation ? "Gửi lại cùng yêu cầu" : editing ? "Lưu thay đổi buổi" : "Tạo buổi khám"}</Button>{editing && !retryMutation ? <Button type="button" onClick={() => { setEditing(null); setForm(emptySessionForm()); setMutationError(null); }}>Hủy sửa</Button> : null}</div>
    </form>

    <section className="border-t border-border pt-5">
      <h3 className="font-semibold">Buổi trong bộ lọc</h3>
      {sessions.length ? <>
        <label className="mt-3 block max-w-xl space-y-1 text-sm font-medium" htmlFor="schedule-status-reason">Lý do ngừng hoặc mở lại buổi<input id="schedule-status-reason" maxLength={500} value={statusReason} onChange={event => setStatusReason(event.target.value)} disabled={mutating || Boolean(retryMutation)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" /></label>
        <div className="mt-3 overflow-x-auto rounded-lg border border-border"><table className="w-full min-w-[60rem] text-left text-sm"><thead className="bg-surface-muted text-xs uppercase tracking-wide text-muted-foreground"><tr><th className="p-3">Ngày</th><th className="p-3">Giờ</th><th className="p-3">Bác sĩ</th><th className="p-3">Phòng</th><th className="p-3">Trạng thái</th><th className="p-3">Sức chứa</th><th className="p-3"><span className="sr-only">Thao tác</span></th></tr></thead><tbody>{sessions.map(session => <tr key={session.sessionId} className="border-t border-border"><td className="p-3">{formatLocalDate(session.sessionDate)}</td><td className="p-3">{session.startTime}–{session.endTime}</td><td className="p-3 break-all font-mono">{session.doctorId}</td><td className="p-3 break-all font-mono">{session.roomId}</td><td className="p-3">{session.status}</td><td className="p-3">{session.capacity} total · {session.availableCount} còn · {session.confirmedCount} xác nhận</td><td className="space-x-2 p-3 text-right">{session.status !== "CANCELLED" ? <><Button type="button" disabled={mutating || Boolean(retryMutation)} onClick={() => beginEdit(session)}>Sửa</Button><Button type="button" disabled={mutating || Boolean(retryMutation)} onClick={() => changeStatus(session, session.status === "OPEN" ? "STOPPED" : "OPEN")}>{session.status === "OPEN" ? "Ngừng nhận" : "Mở lại"}</Button><Button type="button" variant="danger" disabled={mutating || Boolean(retryMutation)} onClick={() => changeStatus(session, "CANCELLED")}>Hủy buổi</Button></> : <span className="text-xs text-muted-foreground">Đã hủy</span>}</td></tr>)}</tbody></table></div>
        {sessionPages > 1 ? <nav aria-label="Trang buổi khám đã lập" className="mt-3 flex items-center justify-between text-sm"><Button type="button" disabled={listLoading || sessionPage === 0} onClick={() => void loadSessions(sessionPage - 1)}>Trang trước</Button><span>Trang {sessionPage + 1} / {sessionPages}</span><Button type="button" disabled={listLoading || sessionPage + 1 >= sessionPages} onClick={() => void loadSessions(sessionPage + 1)}>Trang sau</Button></nav> : null}
      </> : <p role="status" className="mt-2 text-sm text-muted-foreground">Chưa tải buổi khám hoặc chưa có kết quả.</p>}
    </section>
  </section>;
}

export function ScheduleManagementWorkspace({ enabled }: { enabled: boolean }) {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, () => null);
  if (!enabled) return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground">Quản lý lịch khám V2 chưa được mở.</p>;
  return <div className="space-y-6">{role === "ADMIN" ? <ScopeAdministration /> : null}<SessionAdministration /></div>;
}
