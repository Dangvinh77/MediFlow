"use client";

import { useEffect, useRef, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { CommandIntentError, prepareCommandIntent } from "@/lib/command-intent";
import { formatLocalDate } from "@/lib/format";
import { getSessionKey } from "@/lib/session";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { ReferenceText } from "@/components/references/ReferenceText";
import { appointmentApi } from "../api";
import { sessionAvailability, sessionAvailabilityLabels } from "../schedule-presentation";
import { vietnamToday } from "../session-dates";
import { ScheduleDateInput } from "./ScheduleDateInput";
import type { ConsultationSessionDTO, RegistrationDTO, RescheduleCommand } from "../types";

interface DialogError { message: string; correlationId: string | null }

const definiteMessages: Record<string, string> = {
  REVISION_CONFLICT: "Phiếu vừa được cập nhật ở nơi khác. Đã tải lại phiếu, hãy kiểm tra rồi gửi lại.",
  REGISTRATION_PAYMENT_IN_PROGRESS: "Phiếu đang được thanh toán. Hãy chờ thanh toán xong rồi đổi lịch.",
  RESCHEDULE_NOT_ALLOWED: "Phiếu ở trạng thái hiện tại không thể đổi lịch.",
  RESCHEDULE_TARGET_INVALID: "Buổi khám mới không hợp lệ hoặc đã hết chỗ. Hãy chọn buổi khác.",
  RESCHEDULE_CONFIRMATION_REQUIRED: "Cần xác nhận bệnh nhân đã đồng ý đổi lịch.",
};

function errorFrom(cause: unknown): DialogError {
  if (cause instanceof ApiRequestError) return { message: definiteMessages[cause.code ?? ""] ?? cause.message, correlationId: cause.correlationId };
  return { message: cause instanceof Error ? cause.message : "Không thể đổi lịch.", correlationId: null };
}

function outcomeUnknown(cause: unknown) {
  return cause instanceof CommandIntentError || !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500;
}

const inputClass = "min-h-11 w-full rounded-lg border border-control-border bg-surface px-3";

export function RescheduleDialog({ registrationId, onClose, onRescheduled }: { registrationId: string; onClose: () => void; onRescheduled: (result: RegistrationDTO) => void }) {
  const [attempt, setAttempt] = useState(0);
  const [registration, setRegistration] = useState<RegistrationDTO | null>(null);
  const [loadError, setLoadError] = useState<DialogError | null>(null);
  const [date, setDate] = useState(vietnamToday);
  const [sessions, setSessions] = useState<ConsultationSessionDTO[] | null>(null);
  const [sessionError, setSessionError] = useState<DialogError | null>(null);
  const [searching, setSearching] = useState(false);
  const [targetSessionId, setTargetSessionId] = useState("");
  const [reason, setReason] = useState("");
  const [patientConfirmed, setPatientConfirmed] = useState(false);
  const [submitError, setSubmitError] = useState<DialogError | null>(null);
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<RescheduleCommand | null>(null);
  const lock = useRef(false);
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    let live = true;
    appointmentApi.registration(registrationId)
      .then(value => { if (live) { setRegistration(value); setLoadError(null); } })
      .catch((cause: unknown) => { if (live) setLoadError(errorFrom(cause)); });
    return () => { live = false; };
  }, [registrationId, attempt]);

  async function search() {
    if (!registration || searching) return;
    setSearching(true); setSessionError(null); setSessions(null); setTargetSessionId("");
    try {
      const page = await appointmentApi.listConsultationSessions({ departmentId: registration.departmentId, fromDate: date, toDate: date, page: 0, size: 100 });
      if (alive.current) setSessions(page.content.filter(item => item.sessionId !== registration.sessionId));
    } catch (cause) {
      if (alive.current) setSessionError(errorFrom(cause));
    } finally {
      if (alive.current) setSearching(false);
    }
  }

  async function submit() {
    if (lock.current || !registration) return;
    const body: RescheduleCommand = pending ?? { expectedRevision: registration.revision, targetSessionId, reason: reason.trim(), patientConfirmed };
    if (!body.targetSessionId || !body.reason || !body.patientConfirmed) return;
    lock.current = true; setBusy(true); setSubmitError(null);
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | undefined;
    try {
      intent = await prepareCommandIntent(`outpatient-registration-reschedule:${registrationId}`, getSessionKey(), body);
      const result = await appointmentApi.reschedule(registrationId, body, { idempotencyKey: intent.key, correlationId: intent.correlationId });
      intent.settle();
      if (alive.current) { setPending(null); onRescheduled(result); }
    } catch (cause) {
      intent?.settle(cause);
      if (!alive.current) return;
      const unknown = outcomeUnknown(cause);
      setPending(unknown ? body : null);
      setSubmitError(errorFrom(cause));
      if (!unknown && cause instanceof ApiRequestError && cause.status === 409) setAttempt(value => value + 1);
    } finally {
      lock.current = false;
      if (alive.current) setBusy(false);
    }
  }

  const locked = busy || Boolean(pending);
  return <section aria-label="Đổi lịch khám" className="mt-3 space-y-3 rounded-lg border border-primary/30 bg-primary/5 p-4 text-sm">
    <h4 className="font-semibold">Đổi lịch khám</h4>
    {loadError ? <AsyncState kind="error" message={loadError.message} correlationId={loadError.correlationId} onRetry={() => setAttempt(value => value + 1)} /> : null}
    <form noValidate className="flex flex-wrap items-end gap-3" onSubmit={event => { event.preventDefault(); void search(); }}>
      <ScheduleDateInput label="Ngày khám mới" required disabled={locked} value={date} onChange={event => setDate(event.target.value)} className={inputClass} />
      <Button type="submit" loading={searching} disabled={locked || !registration || !date}>Tìm buổi khám</Button>
    </form>
    {sessionError ? <AsyncState kind="error" message={sessionError.message} correlationId={sessionError.correlationId} onRetry={() => void search()} /> : null}
    {sessions ? sessions.length ? <fieldset disabled={locked} className="space-y-2"><legend className="font-medium">Buổi khám mới</legend>{sessions.map(item => {
      const availability = sessionAvailability(item, null);
      return <label key={item.sessionId} className="flex items-start gap-2 rounded-lg border border-border bg-surface p-3">
        <input type="radio" name="reschedule-target" value={item.sessionId} checked={targetSessionId === item.sessionId} disabled={availability !== "AVAILABLE"} onChange={() => setTargetSessionId(item.sessionId)} />
        <span>{formatLocalDate(item.sessionDate)} · {item.startTime}–{item.endTime} · <ReferenceText kind="doctor" value={item.doctorId} fallback="Bác sĩ" /> · <ReferenceText kind="room" value={item.roomId} fallback="Phòng khám" /> · {availability === "AVAILABLE" ? `Còn ${item.availableCount} chỗ` : sessionAvailabilityLabels[availability]}</span>
      </label>;
    })}</fieldset> : <p role="status" className="text-muted-foreground">Không có buổi khám khác trong ngày đã chọn.</p> : null}
    <label className="block space-y-1 font-medium" htmlFor={`reschedule-reason-${registrationId}`}>Lý do đổi lịch
      <textarea id={`reschedule-reason-${registrationId}`} required maxLength={500} disabled={locked} value={reason} onChange={event => setReason(event.target.value)} className="min-h-20 w-full rounded-lg border border-control-border bg-surface px-3 py-2 font-normal" />
    </label>
    <label className="flex items-start gap-2"><input type="checkbox" checked={patientConfirmed} disabled={locked} onChange={event => setPatientConfirmed(event.target.checked)} /><span>Bệnh nhân đã đồng ý với lịch mới.</span></label>
    <p className="text-muted-foreground">Đổi sang ngày khác sẽ cấp số quầy mới và bệnh nhân phải đến quầy tiếp nhận lại. Đổi trong cùng ngày giữ nguyên số quầy.</p>
    {submitError ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-danger"><p>{submitError.message}</p>{submitError.correlationId ? <p className="mt-1 text-xs">Mã theo dõi: <span className="font-mono">{submitError.correlationId}</span></p> : null}{pending ? <p className="mt-2">Chưa xác định lịch đã được đổi hay chưa. Hãy gửi lại đúng yêu cầu để tránh đổi trùng.</p> : null}</div> : null}
    <div className="flex flex-wrap gap-2">
      <Button type="button" variant="primary" loading={busy} disabled={!registration || (!pending && (!targetSessionId || !reason.trim() || !patientConfirmed))} onClick={() => void submit()}>{pending ? "Gửi lại cùng yêu cầu" : "Xác nhận đổi lịch"}</Button>
      <Button type="button" variant="secondary" disabled={locked} onClick={onClose}>Đóng</Button>
    </div>
  </section>;
}
