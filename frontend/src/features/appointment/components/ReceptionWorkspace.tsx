"use client";

import { useRef, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/session";
import { prepareCommandIntent, CommandIntentError } from "@/lib/command-intent";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { appointmentApi } from "../api";
import type { ConsultationSessionDTO, FollowUpSourceSummary, RegistrationCreate, RegistrationDTO, RegistrationSummary, RevisionCommand } from "../types";

interface WorkspaceError { message: string; correlationId: string | null; uncertain?: boolean }
interface RegistrationActionError extends WorkspaceError { registrationId: string }
interface PendingArrival { registrationId: string; body: RevisionCommand }

function errorFrom(cause: unknown, fallback: string): WorkspaceError {
  return cause instanceof ApiRequestError
    ? { message: cause.message, correlationId: cause.correlationId }
    : { message: cause instanceof Error ? cause.message : fallback, correlationId: null };
}

function uncertain(cause: unknown) {
  return !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500;
}

export function ReceptionWorkspace({ enabled }: { enabled: boolean }) {
  const [departmentId, setDepartmentId] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [sessions, setSessions] = useState<ConsultationSessionDTO[]>([]);
  const [sessionPage, setSessionPage] = useState(0);
  const [sessionPages, setSessionPages] = useState(0);
  const [selectedSession, setSelectedSession] = useState<ConsultationSessionDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<WorkspaceError | null>(null);
  const [patientId, setPatientId] = useState("");
  const [receptionMode, setReceptionMode] = useState<"AT_COUNTER" | "PREBOOKED">("AT_COUNTER");
  const [visitType, setVisitType] = useState<"NEW_VISIT" | "FOLLOW_UP">("NEW_VISIT");
  const [reason, setReason] = useState("");
  const [replacementReason, setReplacementReason] = useState("");
  const [patientAgreedToDoctorReplacement, setPatientAgreedToDoctorReplacement] = useState(false);
  const [followUpSources, setFollowUpSources] = useState<FollowUpSourceSummary[] | null>(null);
  const [followUpSourcePage, setFollowUpSourcePage] = useState(0);
  const [followUpSourcePages, setFollowUpSourcePages] = useState(0);
  const [followUpSourceLoading, setFollowUpSourceLoading] = useState(false);
  const [followUpSourceError, setFollowUpSourceError] = useState<WorkspaceError | null>(null);
  const [selectedFollowUpSource, setSelectedFollowUpSource] = useState<FollowUpSourceSummary | null>(null);
  const [useFollowUpRecommendation, setUseFollowUpRecommendation] = useState(true);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<WorkspaceError | null>(null);
  const [createdRegistration, setCreatedRegistration] = useState<RegistrationDTO | null>(null);
  const [lastCreateBody, setLastCreateBody] = useState<RegistrationCreate | null>(null);
  const [registrationList, setRegistrationList] = useState<RegistrationSummary[] | null>(null);
  const [registrationPage, setRegistrationPage] = useState(0);
  const [registrationPages, setRegistrationPages] = useState(0);
  const [registrationError, setRegistrationError] = useState<WorkspaceError | null>(null);
  const [registrationLoading, setRegistrationLoading] = useState(false);
  const [arrivalError, setArrivalError] = useState<RegistrationActionError | null>(null);
  const [arrivalBusyId, setArrivalBusyId] = useState<string | null>(null);
  const [lastArrival, setLastArrival] = useState<PendingArrival | null>(null);
  const listGeneration = useRef(0);
  const followUpSourceGeneration = useRef(0);

  async function loadSessions(page = 0) {
    if (!enabled || loading) return;
    if (!departmentId.trim()) {
      setLoadError({ message: "Chọn khoa để xem các buổi khám.", correlationId: null });
      return;
    }
    const generation = ++listGeneration.current;
    setLoading(true); setLoadError(null); setSelectedSession(null); setRegistrationList(null);
    try {
      const result = await appointmentApi.listConsultationSessions({
        departmentId: departmentId.trim(), fromDate: fromDate || undefined, toDate: toDate || undefined, page, size: 20,
      });
      if (listGeneration.current === generation) { setSessions(result.content); setSessionPage(result.number); setSessionPages(result.totalPages); }
    } catch (cause) {
      if (listGeneration.current === generation) setLoadError(errorFrom(cause, "Không thể tải buổi khám."));
    } finally {
      if (listGeneration.current === generation) setLoading(false);
    }
  }

  async function loadRegistrations(session: ConsultationSessionDTO, page = 0) {
    if (registrationLoading) return;
    followUpSourceGeneration.current++;
    setSelectedSession(session); setRegistrationList(null); setRegistrationLoading(true); setRegistrationError(null);
    setFollowUpSources(null); setSelectedFollowUpSource(null); setFollowUpSourceError(null); setFollowUpSourceLoading(false);
    try {
      const result = await appointmentApi.registrationsBySession(session.sessionId, page, 20);
      setRegistrationList(result.content); setRegistrationPage(result.number); setRegistrationPages(result.totalPages);
    } catch (cause) {
      setRegistrationError(errorFrom(cause, "Không thể tải danh sách phiếu."));
    } finally {
      setRegistrationLoading(false);
    }
  }

  async function loadFollowUpSources(page = 0) {
    if (!enabled || followUpSourceLoading) return;
    if (!patientId.trim()) {
      setFollowUpSourceError({ message: "Chọn bệnh nhân trước khi tìm lượt khám trước.", correlationId: null });
      return;
    }
    const generation = ++followUpSourceGeneration.current;
    const requestedPatientId = patientId.trim();
    setFollowUpSourceLoading(true); setFollowUpSourceError(null);
    try {
      const result = await appointmentApi.followUpSources({ patientId: requestedPatientId, page, size: 20 });
      if (followUpSourceGeneration.current === generation) {
        setFollowUpSources(result.content); setFollowUpSourcePage(result.number); setFollowUpSourcePages(result.totalPages);
      }
    } catch (cause) {
      if (followUpSourceGeneration.current === generation) setFollowUpSourceError(errorFrom(cause, "Không thể tải lượt khám đủ điều kiện tái khám."));
    } finally {
      if (followUpSourceGeneration.current === generation) setFollowUpSourceLoading(false);
    }
  }

  async function submitRegistration(body: RegistrationCreate) {
    if (!enabled || !selectedSession || creating) return;
    setCreating(true); setCreateError(null);
    try {
      const intent = await prepareCommandIntent(`outpatient-registration:${selectedSession.sessionId}`, getSessionKey(), body);
      setLastCreateBody(body);
      const result = await appointmentApi.createRegistration(body, { idempotencyKey: intent.key, correlationId: intent.correlationId });
      intent.settle();
      setCreatedRegistration(result);
    } catch (cause) {
      if (!(cause instanceof CommandIntentError)) {
        try {
          const intent = await prepareCommandIntent(`outpatient-registration:${selectedSession.sessionId}`, getSessionKey(), body);
          intent.settle(cause);
        } catch { /* Retain the pending intent and its fingerprint. */ }
      }
      setCreateError({ ...errorFrom(cause, "Không thể đăng ký giữ chỗ."), uncertain: uncertain(cause) || cause instanceof CommandIntentError });
    } finally {
      setCreating(false);
    }
  }

  async function recordArrival(item: RegistrationSummary) {
    if (!enabled || arrivalBusyId) return;
    setArrivalBusyId(item.registrationId);
    setArrivalError(null);
    let body = lastArrival?.registrationId === item.registrationId && arrivalError?.uncertain
      ? lastArrival.body
      : null;
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | null = null;
    try {
      if (!body) {
        const current = await appointmentApi.registration(item.registrationId);
        if (current.checkedInAt) {
          setRegistrationList(rows => rows?.map(row => row.registrationId === item.registrationId ? {
            ...row, checkedInAt: current.checkedInAt, status: current.status,
            queueNumber: current.queueNumber, readyForExam: current.readyForExam,
          } : row) ?? null);
          setLastArrival(null);
          return;
        }
        if (current.status !== "CONFIRMED") {
          setArrivalError({
            registrationId: item.registrationId,
            message: "Phiếu không còn ở trạng thái đã xác nhận. Hãy tải lại danh sách trước khi ghi nhận đến.",
            correlationId: null,
          });
          return;
        }
        body = { expectedRevision: current.revision };
      }
      setLastArrival({ registrationId: item.registrationId, body });
      intent = await prepareCommandIntent(`outpatient-registration-arrival:${item.registrationId}`, getSessionKey(), body);
      const result = await appointmentApi.markRegistrationArrival(item.registrationId, body, {
        idempotencyKey: intent.key,
        correlationId: intent.correlationId,
      });
      intent.settle();
      setLastArrival(null);
      setRegistrationList(rows => rows?.map(row => row.registrationId === item.registrationId ? {
        ...row, checkedInAt: result.checkedInAt, status: result.status,
        queueNumber: result.queueNumber, readyForExam: result.readyForExam,
      } : row) ?? null);
    } catch (cause) {
      if (intent) intent.settle(cause);
      const isUncertain = uncertain(cause) || cause instanceof CommandIntentError;
      if (!isUncertain) setLastArrival(null);
      setArrivalError({
        registrationId: item.registrationId,
        ...errorFrom(cause, "Không thể ghi nhận khách đã đến."),
        uncertain: isUncertain,
      });
    } finally {
      setArrivalBusyId(null);
    }
  }

  function register() {
    if (!patientId.trim()) { setCreateError({ message: "Chọn bệnh nhân trước khi đăng ký.", correlationId: null }); return; }
    if (!selectedSession || selectedSession.status !== "OPEN" || selectedSession.availableCount < 1) return;
    const hasFollowUpRecommendation = visitType === "FOLLOW_UP" && Boolean(selectedFollowUpSource?.recommendation) && useFollowUpRecommendation;
    const hasDoctorReplacement = visitType === "FOLLOW_UP" && Boolean(selectedFollowUpSource)
      && selectedSession.doctorId !== selectedFollowUpSource?.doctorId;
    if (visitType === "FOLLOW_UP" && !selectedFollowUpSource) {
      setCreateError({ message: "Chọn một lượt khám đã hoàn tất trong cùng khoa để liên kết tái khám.", correlationId: null });
      return;
    }
    if (visitType === "FOLLOW_UP" && !hasFollowUpRecommendation && !reason.trim()) {
      setCreateError({ message: "Cần lý do tái khám khi không dùng đề xuất của bác sĩ.", correlationId: null });
      return;
    }
    if (hasDoctorReplacement && (!patientAgreedToDoctorReplacement || !replacementReason.trim())) {
      setCreateError({ message: "Cần lý do chọn bác sĩ khác và xác nhận bệnh nhân đã đồng ý.", correlationId: null });
      return;
    }
    const body: RegistrationCreate = {
      patientId: patientId.trim(),
      sessionId: selectedSession.sessionId,
      expectedSessionRevision: selectedSession.revision,
      receptionMode,
      visitType,
      previousRegistrationId: visitType === "FOLLOW_UP" ? selectedFollowUpSource!.registrationId : null,
      followUpRecommendationId: hasFollowUpRecommendation ? selectedFollowUpSource!.recommendation!.recommendationId : null,
      replacementReason: hasDoctorReplacement ? replacementReason.trim() : null,
      patientAgreedToDoctorReplacement: hasDoctorReplacement ? true : null,
      reason: reason.trim() || null,
    };
    void submitRegistration(body);
  }

  function onDepartmentChange(value: string) {
    listGeneration.current++;
    followUpSourceGeneration.current++;
    setDepartmentId(value); setSessions([]); setSessionPage(0); setSessionPages(0); setSelectedSession(null); setRegistrationList(null); setRegistrationPage(0); setRegistrationPages(0); setLoadError(null); setFollowUpSources(null); setSelectedFollowUpSource(null); setFollowUpSourceError(null); setFollowUpSourceLoading(false);
  }

  if (!enabled) return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground">Tính năng tiếp nhận theo lịch khám V2 chưa được mở.</p>;

  const submitBody = createError?.uncertain && lastCreateBody ? lastCreateBody : null;
  const selectedRecommendation = selectedFollowUpSource?.recommendation && useFollowUpRecommendation
    ? selectedFollowUpSource.recommendation
    : null;
  const doctorReplacement = Boolean(selectedSession && selectedFollowUpSource)
    && selectedSession!.doctorId !== selectedFollowUpSource!.doctorId;
  const recommendationOutOfWindow = Boolean(selectedSession && selectedRecommendation)
    && (selectedSession!.sessionDate < selectedRecommendation!.recommendedFrom
      || selectedSession!.sessionDate > selectedRecommendation!.recommendedThrough);

  return <div className="space-y-6">
    <section className="rounded-xl border border-border bg-surface p-5">
      <h2 className="text-lg font-semibold">Chọn buổi khám</h2>
      <form className="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4" onSubmit={event => { event.preventDefault(); void loadSessions(); }}>
        <label className="space-y-1 text-sm font-medium">Khoa
          <ReferenceInput kind="department" value={departmentId} onValueChange={onDepartmentChange} aria-label="Khoa" />
        </label>
        <label className="space-y-1 text-sm font-medium" htmlFor="reception-from-date">Từ ngày
          <input id="reception-from-date" type="date" value={fromDate} onChange={event => setFromDate(event.target.value)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" />
        </label>
        <label className="space-y-1 text-sm font-medium" htmlFor="reception-to-date">Đến ngày
          <input id="reception-to-date" type="date" value={toDate} onChange={event => setToDate(event.target.value)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" />
        </label>
        <div className="flex items-end"><Button type="submit" loading={loading}>Tải buổi khám</Button></div>
      </form>
      {loadError ? <AsyncState kind="error" message={loadError.message} correlationId={loadError.correlationId} onRetry={() => void loadSessions()} /> : null}
      {!loading && !loadError && sessions.length === 0 ? <p role="status" className="mt-4 text-sm text-muted-foreground">Chưa có buổi khám trong bộ lọc.</p> : null}
      {sessions.length > 0 ? <><div className="mt-4 overflow-x-auto rounded-lg border border-border">
        <table className="w-full min-w-[48rem] text-left text-sm">
          <thead className="bg-surface-muted text-xs uppercase tracking-wide text-muted-foreground"><tr><th className="p-3">Ngày</th><th className="p-3">Giờ</th><th className="p-3">Trạng thái</th><th className="p-3">Còn chỗ</th><th className="p-3"><span className="sr-only">Thao tác</span></th></tr></thead>
          <tbody>{sessions.map(session => <tr key={session.sessionId} className="border-t border-border">
            <td className="p-3">{formatLocalDate(session.sessionDate)}</td><td className="p-3">{session.startTime}–{session.endTime}</td><td className="p-3">{session.status}</td><td className="p-3">{session.availableCount} / {session.capacity}</td>
            <td className="p-3 text-right"><Button type="button" disabled={session.status !== "OPEN" || session.availableCount < 1} onClick={() => { setRegistrationPage(0); setRegistrationPages(0); void loadRegistrations(session); }}>Chọn buổi</Button></td>
          </tr>)}</tbody>
        </table>
      </div>{sessionPages > 1 ? <nav aria-label="Trang buổi khám" className="mt-3 flex items-center justify-between text-sm"><Button type="button" disabled={loading || sessionPage === 0} onClick={() => void loadSessions(sessionPage - 1)}>Trang trước</Button><span>Trang {sessionPage + 1} / {sessionPages}</span><Button type="button" disabled={loading || sessionPage + 1 >= sessionPages} onClick={() => void loadSessions(sessionPage + 1)}>Trang sau</Button></nav> : null}</> : null}
    </section>

    {selectedSession ? <section className="space-y-4 rounded-xl border border-border bg-surface p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><h2 className="text-lg font-semibold">Tiếp nhận vào buổi {formatLocalDate(selectedSession.sessionDate)} · {selectedSession.startTime}</h2><p className="mt-1 text-sm text-muted-foreground">Còn {selectedSession.availableCount} chỗ. AT_COUNTER ghi nhận đã đến; phiếu vẫn chờ thu tiền cho đến khi Billing xác nhận.</p></div>
        <Button type="button" variant="secondary" loading={registrationLoading} onClick={() => void loadRegistrations(selectedSession, registrationPage)}>Tải phiếu trong buổi</Button>
      </div>

      <form className="grid gap-4 border-t border-border pt-4 sm:grid-cols-2" onSubmit={event => { event.preventDefault(); if (submitBody) void submitRegistration(submitBody); else register(); }}>
        <label className="space-y-1 text-sm font-medium">Bệnh nhân
          <ReferenceInput kind="patient" value={patientId} onValueChange={value => {
            followUpSourceGeneration.current++;
            setPatientId(value); setCreateError(null); setFollowUpSources(null); setSelectedFollowUpSource(null); setFollowUpSourceError(null); setFollowUpSourceLoading(false);
          }} aria-label="Bệnh nhân" disabled={creating || Boolean(createError?.uncertain)} />
        </label>
        <label className="space-y-1 text-sm font-medium" htmlFor="reception-visit-type">Loại lượt khám
          <select id="reception-visit-type" value={visitType} onChange={event => {
            followUpSourceGeneration.current++;
            setVisitType(event.target.value as "NEW_VISIT" | "FOLLOW_UP"); setCreateError(null);
            setFollowUpSources(null); setSelectedFollowUpSource(null); setFollowUpSourceError(null);
            setUseFollowUpRecommendation(true); setReason(""); setReplacementReason(""); setPatientAgreedToDoctorReplacement(false);
          }} disabled={creating || Boolean(createError?.uncertain)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3">
            <option value="NEW_VISIT">Khám mới</option><option value="FOLLOW_UP">Tái khám từ lượt đã hoàn tất</option>
          </select>
        </label>
        <label className="space-y-1 text-sm font-medium" htmlFor="reception-mode">Hình thức tiếp nhận
          <select id="reception-mode" value={receptionMode} onChange={event => setReceptionMode(event.target.value as "AT_COUNTER" | "PREBOOKED")} disabled={creating || Boolean(createError?.uncertain)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3">
            <option value="AT_COUNTER">Đang có mặt tại quầy</option><option value="PREBOOKED">Đăng ký trước, sẽ đến sau</option>
          </select>
        </label>
        {visitType === "FOLLOW_UP" ? <section className="space-y-3 rounded-lg border border-border p-4 sm:col-span-2">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div><h3 className="font-semibold">Lượt khám trước</h3><p className="text-sm text-muted-foreground">Chỉ chọn lượt V2 đã hoàn tất của cùng bệnh nhân và khoa.</p></div>
            <Button type="button" loading={followUpSourceLoading} disabled={!patientId.trim() || creating || Boolean(createError?.uncertain)} onClick={() => void loadFollowUpSources(0)}>Tìm lượt khám trước</Button>
          </div>
          {followUpSourceError ? <AsyncState kind="error" message={followUpSourceError.message} correlationId={followUpSourceError.correlationId} onRetry={() => void loadFollowUpSources(followUpSourcePage)} /> : null}
          {followUpSources ? followUpSources.length ? <ul className="divide-y divide-border rounded-lg border border-border">{followUpSources.map(source => {
            const sameDepartment = source.departmentId === selectedSession.departmentId;
            const selected = selectedFollowUpSource?.registrationId === source.registrationId;
            return <li key={source.registrationId} className="flex flex-wrap items-center justify-between gap-3 p-3 text-sm">
              <div className="min-w-64 flex-1"><p className="font-medium">Hồ sơ <span className="font-mono">{source.recordId}</span></p><p className="mt-1 text-muted-foreground">Hoàn tất {formatInstant(source.completedAt)} · Khoa <span className="font-mono">{source.departmentId}</span> · Bác sĩ <span className="font-mono">{source.doctorId}</span></p><p className="mt-1 text-muted-foreground">{source.recommendation ? `Có đề xuất: ${formatLocalDate(source.recommendation.recommendedFrom)} – ${formatLocalDate(source.recommendation.recommendedThrough)}` : "Không có đề xuất bác sĩ; cần ghi lý do tự tái khám."}</p></div>
              <Button type="button" aria-pressed={selected} disabled={!sameDepartment || creating || Boolean(createError?.uncertain)} onClick={() => {
                setSelectedFollowUpSource(source); setUseFollowUpRecommendation(Boolean(source.recommendation)); setCreateError(null);
                setPatientAgreedToDoctorReplacement(false); setReplacementReason("");
              }}>{sameDepartment ? selected ? "Đã chọn" : "Chọn lượt khám trước" : "Khác khoa"}</Button>
            </li>;
          })}</ul> : <p role="status" className="rounded-lg border border-border p-3 text-sm text-muted-foreground">Không có lượt V2 đã hoàn tất trong trang này.</p> : null}
          {followUpSourcePages > 1 ? <nav aria-label="Trang lượt khám trước" className="flex items-center justify-between text-sm"><Button type="button" disabled={followUpSourceLoading || followUpSourcePage === 0} onClick={() => void loadFollowUpSources(followUpSourcePage - 1)}>Trang trước</Button><span>Trang {followUpSourcePage + 1} / {followUpSourcePages}</span><Button type="button" disabled={followUpSourceLoading || followUpSourcePage + 1 >= followUpSourcePages} onClick={() => void loadFollowUpSources(followUpSourcePage + 1)}>Trang sau</Button></nav> : null}
          {selectedFollowUpSource ? <div className="space-y-2 rounded-lg border border-primary/30 bg-primary/5 p-3 text-sm">
            <p role="status">Đã liên kết lượt <span className="font-mono">{selectedFollowUpSource.registrationId}</span>.</p>
            {selectedFollowUpSource.recommendation ? <>
              <label className="flex items-center gap-2"><input type="checkbox" checked={useFollowUpRecommendation} onChange={event => { setUseFollowUpRecommendation(event.target.checked); setCreateError(null); }} disabled={creating || Boolean(createError?.uncertain)} />Dùng đề xuất của bác sĩ</label>
              {selectedRecommendation ? <><p>Thời gian khuyến nghị: {formatLocalDate(selectedRecommendation.recommendedFrom)} – {formatLocalDate(selectedRecommendation.recommendedThrough)}</p><p className="whitespace-pre-wrap">{selectedRecommendation.reason}</p>{selectedRecommendation.preparationInstructions ? <p className="whitespace-pre-wrap text-muted-foreground">Chuẩn bị: {selectedRecommendation.preparationInstructions}</p> : null}</> : null}
            </> : <p>Không có đề xuất bác sĩ; cần ghi lý do tái khám để đăng ký tự quay lại.</p>}
            {recommendationOutOfWindow ? <p role="status" className="rounded-md border border-warning/40 bg-warning/10 p-3 text-warning">Ngày buổi khám nằm ngoài thời gian bác sĩ khuyến nghị. Có thể tiếp tục nếu bệnh nhân muốn đăng ký.</p> : null}
            {doctorReplacement ? <div className="space-y-2 border-t border-border pt-3">
              <label className="block space-y-1 font-medium" htmlFor="follow-up-replacement-reason">Lý do chọn bác sĩ khác
                <textarea id="follow-up-replacement-reason" value={replacementReason} maxLength={1000} onChange={event => { setReplacementReason(event.target.value); setCreateError(null); }} disabled={creating || Boolean(createError?.uncertain)} className="min-h-20 w-full rounded-lg border border-control-border bg-surface px-3 py-2" />
              </label>
              <label className="flex items-start gap-2"><input type="checkbox" checked={patientAgreedToDoctorReplacement} onChange={event => { setPatientAgreedToDoctorReplacement(event.target.checked); setCreateError(null); }} disabled={creating || Boolean(createError?.uncertain)} /><span>Bệnh nhân đồng ý được tiếp nhận bởi bác sĩ đã chọn.</span></label>
            </div> : null}
          </div> : null}
        </section> : null}
        <label className="space-y-1 text-sm font-medium sm:col-span-2" htmlFor="reception-reason">{visitType === "FOLLOW_UP" ? selectedRecommendation ? "Lý do đăng ký tái khám (nếu có)" : "Lý do đăng ký tái khám" : "Ghi chú tùy chọn"}
          <textarea id="reception-reason" value={reason} maxLength={1000} onChange={event => { setReason(event.target.value); setCreateError(null); }} disabled={creating || Boolean(createError?.uncertain)} className="min-h-20 w-full rounded-lg border border-control-border bg-surface px-3 py-2" />
        </label>
        {createError ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger sm:col-span-2">
          <p>{createError.message}</p>{createError.correlationId ? <p className="mt-1 text-xs">Mã theo dõi: <span className="font-mono">{createError.correlationId}</span></p> : null}
          {createError.uncertain ? <p className="mt-2">Chưa xác định phiếu đã được tạo hay chưa. Hãy gửi lại đúng yêu cầu để dùng cùng khóa idempotency.</p> : null}
        </div> : null}
        <div className="sm:col-span-2"><Button type="submit" loading={creating}>{submitBody ? "Gửi lại cùng yêu cầu" : "Đăng ký và giữ chỗ"}</Button><p className="mt-2 text-xs text-muted-foreground">Tái khám liên kết lượt V2 đã hoàn tất; ngày ngoài khuyến nghị được cảnh báo nhưng không tự động từ chối.</p></div>
      </form>

      {createdRegistration ? <div className="rounded-lg border border-primary/30 bg-primary/5 p-4 text-sm">
        <p role="status" className="font-semibold">{createdRegistration.status === "AWAITING_PAYMENT" ? "Đang chờ thu tiền" : `Trạng thái phiếu: ${createdRegistration.status}`}</p>
        <dl className="mt-3 grid gap-2 sm:grid-cols-2"><div><dt className="text-muted-foreground">Mã phiếu</dt><dd className="break-all font-mono">{createdRegistration.registrationId}</dd></div><div><dt className="text-muted-foreground">Hạn giữ chỗ</dt><dd>{formatInstant(createdRegistration.reservationExpiresAt)}</dd></div><div><dt className="text-muted-foreground">Phiên bản giữ chỗ</dt><dd>{createdRegistration.reservationRevision}</dd></div><div><dt className="text-muted-foreground">Số thứ tự</dt><dd>{createdRegistration.queueNumber ?? "Chưa cấp trước khi xác nhận"}</dd></div></dl>
      </div> : null}

      {registrationError ? <AsyncState kind="error" message={registrationError.message} correlationId={registrationError.correlationId} onRetry={() => void loadRegistrations(selectedSession, registrationPage)} /> : null}
      {registrationList ? <section className="border-t border-border pt-4"><h3 className="font-semibold">Phiếu trong buổi</h3>
        {registrationList.length ? <ul className="mt-3 divide-y divide-border">{registrationList.map(item => <li key={item.registrationId} className="py-3 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2"><span className="break-all font-mono">{item.registrationId}</span><span>Bệnh nhân: <span className="font-mono">{item.patientId}</span></span><span>{item.status}</span><span>{item.queueNumber === null ? "Chưa có số" : `Số ${item.queueNumber}`}</span>{item.readyForExam ? <span>Sẵn sàng khám</span> : null}
            {item.checkedInAt ? <span role="status">Đã ghi nhận khách đến</span> : item.status === "CONFIRMED" ? <Button type="button" loading={arrivalBusyId === item.registrationId} disabled={Boolean(arrivalBusyId)} onClick={() => void recordArrival(item)}>{arrivalError?.registrationId === item.registrationId && arrivalError.uncertain ? "Gửi lại cùng lệnh đến" : "Ghi nhận đã đến"}</Button> : null}
          </div>
          {arrivalError?.registrationId === item.registrationId ? <div role="alert" className="mt-2 rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger"><p>{arrivalError.message}</p>{arrivalError.correlationId ? <p className="mt-1 text-xs">Mã theo dõi: <span className="font-mono">{arrivalError.correlationId}</span></p> : null}{arrivalError.uncertain ? <p className="mt-2">Chưa xác định trạng thái đã đến. Gửi lại cùng lệnh để giữ nguyên khóa idempotency.</p> : null}</div> : null}
        </li>)}</ul> : <p className="mt-3 text-sm text-muted-foreground">Chưa có phiếu nào trong buổi này.</p>}
        {registrationPages > 1 ? <nav aria-label="Trang phiếu trong buổi" className="mt-3 flex items-center justify-between text-sm"><Button type="button" disabled={registrationLoading || registrationPage === 0} onClick={() => void loadRegistrations(selectedSession, registrationPage - 1)}>Trang trước</Button><span>Trang {registrationPage + 1} / {registrationPages}</span><Button type="button" disabled={registrationLoading || registrationPage + 1 >= registrationPages} onClick={() => void loadRegistrations(selectedSession, registrationPage + 1)}>Trang sau</Button></nav> : null}
      </section> : null}
    </section> : null}
  </div>;
}
