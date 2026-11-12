"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/session";
import { prepareCommandIntent, CommandIntentError } from "@/lib/command-intent";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { ReferenceText } from "@/components/references/ReferenceText";
import { queueNumberLabel, queueNumberText, registrationOutcomeLabel, registrationStatusLabels, roomNumberText, sessionStatusLabels } from "../presentation";
import { sessionAvailability, sessionAvailabilityLabels } from "../schedule-presentation";
import { RescheduleDialog } from "./RescheduleDialog";
import { OpenVisitNotice } from "./OpenVisitNotice";
import { isUuid } from "@/lib/validation";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { sessionDateErrors, vietnamToday } from "../session-dates";
import { ScheduleDateInput } from "./ScheduleDateInput";
import { appointmentApi } from "../api";
import type { ConsultationSessionDTO, FollowUpSourceSummary, RegistrationCancellationCommand, RegistrationCreate, RegistrationDTO, RegistrationSummary, ReturnSourceRecord, RevisionCommand } from "../types";

interface WorkspaceError { message: string; correlationId: string | null; uncertain?: boolean }
interface RegistrationActionError extends WorkspaceError { registrationId: string }
interface PendingArrival { registrationId: string; body: RevisionCommand }
type RefreshKind = "created" | "released" | "rescheduled";
const refreshMessages: Record<RefreshKind, { failed: string; running: string }> = {
  created: { failed: "Phiếu đã được tạo; chưa cập nhật được danh sách và số chỗ. Không đăng ký lại phiếu vừa tạo.", running: "Phiếu đã được tạo. Đang cập nhật danh sách và số chỗ…" },
  released: { failed: "Đã trả chỗ; chưa cập nhật được danh sách và số chỗ.", running: "Đã trả chỗ. Đang cập nhật danh sách và số chỗ…" },
  rescheduled: { failed: "Đã đổi lịch; chưa cập nhật được danh sách và số chỗ. Không đổi lịch lại phiếu này.", running: "Đã đổi lịch. Đang cập nhật danh sách và số chỗ…" },
};

function errorFrom(cause: unknown, fallback: string): WorkspaceError {
  return cause instanceof ApiRequestError
    ? { message: cause.message, correlationId: cause.correlationId }
    : { message: cause instanceof Error ? cause.message : fallback, correlationId: null };
}

const dateInputClass = "min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 text-sm tabular-nums focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-55";
const linkButton ="inline-flex min-h-11 items-center justify-center rounded-lg border border-control-border bg-surface px-4 py-2 text-sm font-semibold text-foreground transition-colors hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary";

const returnSourceLabels: Record<string, string> = {
  COMPLETED: "Đã hoàn tất khám",
  CLOSED_WITHOUT_CONCLUSION: "Kết thúc không kết luận",
};

function uncertain(cause: unknown) {
  return !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500;
}

export function ReceptionWorkspace({ enabled, clinicIntakeEnabled = false, initialPatientId = "" }: { enabled: boolean; clinicIntakeEnabled?: boolean; initialPatientId?: string }) {
  const { localize } = useLocale();
  const [departmentId, setDepartmentId] = useState("");
  const [fromDate, setFromDate] = useState(vietnamToday);
  const [toDate, setToDate] = useState(vietnamToday);
  const [dateErrors, setDateErrors] = useState<Partial<Record<"fromDate" | "toDate", string>>>({});
  const [sessions, setSessions] = useState<ConsultationSessionDTO[]>([]);
  const [sessionsLoaded, setSessionsLoaded] = useState(false);
  const [sessionPage, setSessionPage] = useState(0);
  const [sessionPages, setSessionPages] = useState(0);
  const [selectedSession, setSelectedSession] = useState<ConsultationSessionDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<WorkspaceError | null>(null);
  const [patientId, setPatientId] = useState(initialPatientId);
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
  const [independentReason, setIndependentReason] = useState("");
  const [sameDepartmentOpen, setSameDepartmentOpen] = useState(false);
  const [forceIndependentReason, setForceIndependentReason] = useState(false);
  const [isReturnVisit, setIsReturnVisit] = useState(false);
  const [returnSources, setReturnSources] = useState<ReturnSourceRecord[] | null>(null);
  const [returnSourceError, setReturnSourceError] = useState<WorkspaceError | null>(null);
  const [returnSourceId, setReturnSourceId] = useState("");
  const [returnReason, setReturnReason] = useState("");
  const returnSourceGeneration = useRef(0);
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
  const registrationReads = useRef(0);
  const selectionGeneration = useRef(0);
  const alive = useRef(true);
  const patientField = useRef<HTMLInputElement>(null);
  const writeLock = useRef(false);
  const [needsRefresh, setNeedsRefresh] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState(false);
  const [refreshKind, setRefreshKind] = useState<RefreshKind>("created");
  const [rescheduling, setRescheduling] = useState<string | null>(null);
  const [rescheduleNotice, setRescheduleNotice] = useState<string | null>(null);
  const [releaseReason, setReleaseReason] = useState("");
  const [releasing, setReleasing] = useState(false);
  const [releaseError, setReleaseError] = useState<WorkspaceError | null>(null);
  const [pendingRelease, setPendingRelease] = useState<RegistrationCancellationCommand | null>(null);
  useEffect(() => {
    const generations = [listGeneration, registrationReads, selectionGeneration];
    alive.current = true;
    return () => { alive.current = false; generations.forEach(generation => generation.current++); };
  }, []);

  async function loadSessions(page = 0, dates = { fromDate, toDate }) {
    if (!enabled || loading || writeLock.current || createError?.uncertain) return;
    if (!departmentId.trim()) {
      setLoadError({ message: "Chọn khoa để xem các buổi khám.", correlationId: null });
      return;
    }
    const errors = sessionDateErrors(dates.fromDate, dates.toDate);
    setDateErrors(errors);
    if (Object.keys(errors).length) return;
    const generation = ++listGeneration.current;
    selectionGeneration.current++; registrationReads.current++;
    setRescheduling(null); setRescheduleNotice(null);
    setNeedsRefresh(false); setRefreshing(false); setRefreshError(false); setRegistrationLoading(false);
    setLoading(true); setSessions([]); setSessionPages(0); setCreatedRegistration(null); setSessionsLoaded(false); setLoadError(null); setSelectedSession(null); setRegistrationList(null);
    try {
      const result = await appointmentApi.listConsultationSessions({
        departmentId: departmentId.trim(), fromDate: dates.fromDate, toDate: dates.toDate, page, size: 20,
      });
      if (listGeneration.current === generation) { setSessions(result.content); setSessionsLoaded(true); setSessionPage(result.number); setSessionPages(result.totalPages); }
    } catch (cause) {
      if (listGeneration.current === generation) setLoadError(errorFrom(cause, "Không thể tải buổi khám."));
    } finally {
      if (listGeneration.current === generation) setLoading(false);
    }
  }

  async function loadRegistrations(session: ConsultationSessionDTO, page = 0) {
    if (creating || needsRefresh || createError?.uncertain) return;
    const generation = ++registrationReads.current;
    const auth = getSessionKey();
    selectionGeneration.current++;
    if (session.sessionId !== selectedSession?.sessionId) { setCreatedRegistration(null); setRescheduling(null); setRescheduleNotice(null); }
    followUpSourceGeneration.current++;
    setSelectedSession(session); setRegistrationList(null); setRegistrationLoading(true); setRegistrationError(null);
    setFollowUpSources(null); setSelectedFollowUpSource(null); setFollowUpSourceError(null); setFollowUpSourceLoading(false);
    try {
      const result = await appointmentApi.registrationsBySession(session.sessionId, page, 20);
      if (generation === registrationReads.current && auth === getSessionKey()) {
        setRegistrationList(result.content); setRegistrationPage(result.number); setRegistrationPages(result.totalPages);
      }
    } catch (cause) {
      if (generation === registrationReads.current && auth === getSessionKey()) setRegistrationError(errorFrom(cause, "Không thể tải danh sách phiếu."));
    } finally {
      if (generation === registrationReads.current && auth === getSessionKey()) setRegistrationLoading(false);
    }
  }

  async function refreshAfterRegistration(session: ConsultationSessionDTO, kind: RefreshKind = "created") {
    setRefreshKind(kind);
    const scope = listGeneration.current;
    const selection = selectionGeneration.current;
    const read = ++registrationReads.current;
    const auth = getSessionKey();
    const current = () => alive.current && scope === listGeneration.current && selection === selectionGeneration.current
      && read === registrationReads.current && auth === getSessionKey();
    setNeedsRefresh(true); setRefreshing(true); setRefreshError(false); setRegistrationList(null); setRegistrationError(null); setRegistrationLoading(false);
    try {
      const [page, queue] = await Promise.all([
        appointmentApi.listConsultationSessions({ departmentId: session.departmentId, fromDate, toDate, page: sessionPage, size: 20 }),
        appointmentApi.registrationsBySession(session.sessionId, 0, 20),
      ]);
      if (!current()) return;
      const updated = page.content.find(item => item.sessionId === session.sessionId);
      if (!updated || updated.departmentId !== session.departmentId || updated.sessionDate !== session.sessionDate) {
        throw new Error("Buổi khám không còn trong trang lịch hiện tại.");
      }
      setSessions(page.content); setSessionPage(page.number); setSessionPages(page.totalPages); setSelectedSession(updated);
      setRegistrationList(queue.content); setRegistrationPage(queue.number); setRegistrationPages(queue.totalPages);
      setNeedsRefresh(false);
    } catch {
      if (current()) setRefreshError(true);
    } finally {
      if (current()) setRefreshing(false);
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
    if (!enabled || !selectedSession || body.sessionId !== selectedSession.sessionId || writeLock.current || needsRefresh || createdRegistration) return;
    writeLock.current = true;
    const scope = listGeneration.current;
    const selection = selectionGeneration.current;
    const auth = getSessionKey();
    const current = () => alive.current && scope === listGeneration.current && selection === selectionGeneration.current && auth === getSessionKey();
    setCreating(true); setCreateError(null);
    try {
      const intent = await prepareCommandIntent(`outpatient-registration:${body.sessionId}`, auth, body);
      if (!current()) return;
      setLastCreateBody(body);
      const result = await appointmentApi.createRegistration(body, { idempotencyKey: intent.key, correlationId: intent.correlationId });
      intent.settle();
      if (!current()) return;
      setCreatedRegistration(result);
      void refreshAfterRegistration(selectedSession);
    } catch (cause) {
      if (!(cause instanceof CommandIntentError)) {
        try {
          const intent = await prepareCommandIntent(`outpatient-registration:${body.sessionId}`, auth, body);
          intent.settle(cause);
        } catch { /* Retain the pending intent and its fingerprint. */ }
      }
      if (current() && cause instanceof ApiRequestError && cause.code === "OPEN_VISIT_EXISTS") setForceIndependentReason(true);
      if (current()) setCreateError({ ...errorFrom(cause, "Không thể đăng ký giữ chỗ."), uncertain: uncertain(cause) || cause instanceof CommandIntentError });
    } finally {
      writeLock.current = false;
      if (alive.current) setCreating(false);
    }
  }

  function resetReturnVisit() {
    returnSourceGeneration.current++;
    setIsReturnVisit(false); setReturnSources(null); setReturnSourceError(null); setReturnSourceId(""); setReturnReason("");
  }

  async function loadReturnSources() {
    const generation = ++returnSourceGeneration.current;
    setReturnSources(null); setReturnSourceError(null);
    try {
      const records = await appointmentApi.returnSources(patientId.trim());
      if (returnSourceGeneration.current === generation) setReturnSources(records.filter(record => record.status in returnSourceLabels));
    } catch (cause) {
      if (returnSourceGeneration.current === generation) setReturnSourceError(errorFrom(cause, "Không thể tải các lượt đã kết thúc."));
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

  async function releaseHold() {
    if (!enabled || !createdRegistration || !selectedSession || writeLock.current) return;
    const body = pendingRelease ?? { expectedRevision: createdRegistration.revision, reason: releaseReason.trim() };
    if (!body.reason) return;
    writeLock.current = true; setReleasing(true); setReleaseError(null);
    const auth = getSessionKey();
    const registrationId = createdRegistration.registrationId;
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | null = null;
    try {
      intent = await prepareCommandIntent(`outpatient-registration-release:${registrationId}`, auth, body);
      const result = await appointmentApi.cancelRegistration(registrationId, body, { idempotencyKey: intent.key, correlationId: intent.correlationId });
      intent.settle();
      if (!alive.current || auth !== getSessionKey()) return;
      setPendingRelease(null); setReleaseReason(""); setCreatedRegistration(result);
      void refreshAfterRegistration(selectedSession, "released");
    } catch (cause) {
      if (intent) intent.settle(cause);
      if (!alive.current || auth !== getSessionKey()) return;
      const isUncertain = uncertain(cause) || cause instanceof CommandIntentError;
      setPendingRelease(isUncertain ? body : null);
      setReleaseError({ ...errorFrom(cause, "Không thể trả chỗ đã giữ."), uncertain: isUncertain });
    } finally {
      writeLock.current = false;
      if (alive.current) setReleasing(false);
    }
  }

  function afterReschedule(result: RegistrationDTO) {
    setRescheduling(null);
    setRescheduleNotice(result.checkedInAt
      ? "Đã đổi lịch trong ngày và giữ nguyên số quầy."
      : "Đã đổi lịch. Bệnh nhân cần đến quầy tiếp nhận vào ngày khám mới.");
    if (selectedSession) void refreshAfterRegistration(selectedSession, "rescheduled");
  }

  function register() {
    if (createdRegistration) return;
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
    const needsIndependentReason = sameDepartmentOpen || forceIndependentReason;
    if (needsIndependentReason && !independentReason.trim()) {
      setCreateError({ message: "Bệnh nhân còn lượt khám chưa kết thúc trong khoa này. Cần ghi lý do đăng ký thêm lượt.", correlationId: null });
      return;
    }
    const linksReturn = visitType === "NEW_VISIT" && isReturnVisit;
    if (linksReturn && (!returnSourceId || !returnReason.trim())) {
      setCreateError({ message: "Chọn lượt đã kết thúc và ghi lý do quay lại.", correlationId: null });
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
      ...(needsIndependentReason ? { independentVisitReason: independentReason.trim() } : {}),
      ...(linksReturn ? { returnSourceRecordId: returnSourceId, returnReason: returnReason.trim() } : {}),
    };
    void submitRegistration(body);
  }

  function onDepartmentChange(value: string) {
    if (writeLock.current || createError?.uncertain) return;
    setCreatedRegistration(null);
    listGeneration.current++;
    selectionGeneration.current++; registrationReads.current++;
    setNeedsRefresh(false); setRefreshing(false); setRefreshError(false); setRegistrationLoading(false);
    followUpSourceGeneration.current++;
    setSameDepartmentOpen(false); setForceIndependentReason(false); setIndependentReason("");
    setDepartmentId(value); setSessionsLoaded(false); setLoading(false); setSessions([]); setSessionPage(0); setSessionPages(0); setSelectedSession(null); setRegistrationList(null); setRegistrationPage(0); setRegistrationPages(0); setLoadError(null); setFollowUpSources(null); setSelectedFollowUpSource(null); setFollowUpSourceError(null); setFollowUpSourceLoading(false);
  }

  function changePatient(value: string) {
    followUpSourceGeneration.current++;
    setCreatedRegistration(null); setLastCreateBody(null);
    setReleaseReason(""); setReleaseError(null); setPendingRelease(null);
    setPatientId(value); setCreateError(null); setFollowUpSources(null);
    setSelectedFollowUpSource(null); setFollowUpSourceError(null); setFollowUpSourceLoading(false);
    setReason(""); setReplacementReason(""); setPatientAgreedToDoctorReplacement(false);
    setIndependentReason(""); setSameDepartmentOpen(false); setForceIndependentReason(false); resetReturnVisit();
  }

  function nextPatient() {
    if (writeLock.current || needsRefresh || !createdRegistration || pendingRelease) return;
    changePatient("");
    setVisitType("NEW_VISIT"); setReceptionMode("AT_COUNTER"); setUseFollowUpRecommendation(true);
    window.setTimeout(() => {
      if (alive.current) {
        patientField.current?.scrollIntoView?.({ block: "center", behavior: "smooth" });
        patientField.current?.focus();
      }
    }, 0);
  }

  if (!enabled) return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground"><L10n>{"Tính năng tiếp nhận theo lịch khám V2 chưa được mở."}</L10n></p>;

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
    <section aria-label={localize("Bệnh nhân đăng ký khám")} className="space-y-3 rounded-xl border border-border bg-surface p-5">
      <h2 className="text-lg font-semibold"><L10n>{"1. Bệnh nhân"}</L10n></h2>
      <ReferenceInput ref={patientField} kind="patient" value={patientId} onValueChange={changePatient} aria-label={localize("Bệnh nhân")} disabled={creating || Boolean(createError?.uncertain) || Boolean(createdRegistration)} />
      <L10nValue>{patientId ? <p className="text-sm"><ReferenceText kind="patient" value={patientId} fallback="Bệnh nhân" /></p> : null}</L10nValue>
      <nav aria-label={localize("Hồ sơ bệnh nhân tại quầy")} className="flex flex-wrap items-center gap-2 text-sm">
        <L10nValue>{patientId ? <Link href={`/patients/${encodeURIComponent(patientId)}`} className={linkButton}><L10n>{"Xem hồ sơ bệnh nhân"}</L10n></Link> : null}</L10nValue>
        <Link href="/patients/new" className={linkButton}><L10n>{"Tạo hồ sơ bệnh nhân"}</L10n></Link>
        <Link href="/patients" className={linkButton}><L10n>{"Tìm hồ sơ bệnh nhân"}</L10n></Link>
      </nav>
    </section>
    <section className="rounded-xl border border-border bg-surface p-5">
      <h2 className="text-lg font-semibold"><L10n>{"2. Chọn buổi khám"}</L10n></h2>
      <p className="mt-1 text-sm text-muted-foreground"><L10n>{"Mặc định xem lịch hôm nay. Chọn khoa, khoảng ngày rồi tải các buổi còn chỗ."}</L10n></p>
      <form noValidate aria-label={localize("Lọc buổi khám")} className="mt-4 rounded-lg border border-border bg-surface-muted p-4" onSubmit={event => { event.preventDefault(); void loadSessions(); }}>
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)_minmax(0,1fr)_auto]">
          <div className="min-w-0 text-sm">
            <span className="mb-1 block font-medium">Khoa</span>
            <ReferenceInput kind="department" value={departmentId} onValueChange={onDepartmentChange} aria-label="Khoa" disabled={creating || Boolean(createError?.uncertain)} />
          </div>
          <div>
            <ScheduleDateInput label={localize("Từ ngày")} id="reception-from-date" required disabled={creating || Boolean(createError?.uncertain)} aria-invalid={Boolean(dateErrors.fromDate)} aria-describedby={dateErrors.fromDate ? "reception-from-error" : undefined} value={fromDate} onChange={event => setFromDate(event.target.value)} className={dateInputClass} />
            <L10nValue>{dateErrors.fromDate ? <span id="reception-from-error" role="alert" className="mt-1 block text-sm text-danger">{dateErrors.fromDate}</span> : null}</L10nValue>
          </div>
          <div>
            <ScheduleDateInput label={localize("Đến ngày")} id="reception-to-date" required disabled={creating || Boolean(createError?.uncertain)} aria-invalid={Boolean(dateErrors.toDate)} aria-describedby={dateErrors.toDate ? "reception-to-error" : undefined} value={toDate} onChange={event => setToDate(event.target.value)} className={dateInputClass} />
            <L10nValue>{dateErrors.toDate ? <span id="reception-to-error" role="alert" className="mt-1 block text-sm text-danger">{dateErrors.toDate}</span> : null}</L10nValue>
          </div>
          <div className="flex items-start md:pt-6"><Button type="submit" variant="primary" className="w-full xl:w-auto" loading={loading} disabled={!departmentId || creating || Boolean(createError?.uncertain)}><L10n>{"Tải buổi khám"}</L10n></Button></div>
        </div>
        <L10nValue>{fromDate !== vietnamToday() || toDate !== vietnamToday() ? <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-border pt-3 text-sm">
          <span className="text-muted-foreground"><L10n>{"Đang xem "}</L10n><L10nValue>{formatLocalDate(fromDate)}</L10nValue> – <L10nValue>{formatLocalDate(toDate)}</L10nValue>.</span>
          <Button type="button" disabled={!departmentId || creating || loading || Boolean(createError?.uncertain)} onClick={() => {
            const today = vietnamToday(); setFromDate(today); setToDate(today);
            void loadSessions(0, { fromDate: today, toDate: today });
          }}><L10n>{"Tiếp nhận hôm nay"}</L10n></Button>
        </div> : null}</L10nValue>
      </form>
      <L10nValue>{loadError ? <AsyncState kind="error" message={localize(loadError.message)} correlationId={loadError.correlationId} onRetry={() => void loadSessions()} /> : null}</L10nValue>
      <L10nValue>{!loading && !loadError && !sessionsLoaded ? <p role="status" className="mt-4 text-sm text-muted-foreground"><L10n>{"Chọn khoa và khoảng ngày, sau đó bấm Tải buổi khám để xem lịch."}</L10n></p> : null}</L10nValue>
      <L10nValue>{!loading && !loadError && sessionsLoaded && sessions.length === 0 ? <p role="status" className="mt-4 text-sm text-muted-foreground"><L10n>{"Chưa có buổi khám trong bộ lọc."}</L10n></p> : null}</L10nValue>
      <L10nValue>{sessions.length > 0 ? <><ul className="mt-4 grid gap-3 lg:grid-cols-2">
        <L10nValue>{sessions.map(session => <li key={session.sessionId} className="space-y-3 rounded-lg border border-border p-4 text-sm">
          <div className="flex flex-wrap justify-between gap-2"><h3 className="font-semibold"><L10nValue>{formatLocalDate(session.sessionDate)}</L10nValue> · {session.startTime}–{session.endTime}</h3><span><L10nValue>{sessionStatusLabels[session.status]}</L10nValue></span></div>
          <p><ReferenceText kind="doctor" value={session.doctorId} fallback="Bác sĩ" /></p>
          <p><ReferenceText kind="department" value={session.departmentId} fallback="Khoa" /> · <ReferenceText kind="room" value={session.roomId} fallback="Phòng khám" /></p>
          <div className="flex flex-wrap items-center justify-between gap-3"><span><L10nValue>{needsRefresh ? "Số chỗ đang chờ cập nhật" : `Còn ${session.availableCount} / ${session.capacity} chỗ`}</L10nValue><L10nValue>{clinicIntakeEnabled && !needsRefresh && sessionAvailability(session, null) !== "AVAILABLE" ? <strong className="ml-2"><L10nValue>{sessionAvailabilityLabels[sessionAvailability(session, null)]}</L10nValue></strong> : null}</L10nValue></span><Button type="button" disabled={creating || needsRefresh || Boolean(createError?.uncertain) || sessionAvailability(session, null) !== "AVAILABLE"} onClick={() => { setRegistrationPage(0); setRegistrationPages(0); void loadRegistrations(session); }}><L10n>{"Chọn buổi"}</L10n></Button></div>
        </li>)}</L10nValue>
      </ul><L10nValue>{sessionPages > 1 ? <nav aria-label={localize("Trang buổi khám")} className="mt-3 flex items-center justify-between text-sm"><Button type="button" disabled={creating || Boolean(createError?.uncertain) || loading || sessionPage === 0} onClick={() => void loadSessions(sessionPage - 1)}><L10n>{"Trang trước"}</L10n></Button><span>Trang <L10nValue>{sessionPage + 1}</L10nValue> / <L10nValue>{sessionPages}</L10nValue></span><Button type="button" disabled={creating || Boolean(createError?.uncertain) || loading || sessionPage + 1 >= sessionPages} onClick={() => void loadSessions(sessionPage + 1)}>Trang sau</Button></nav> : null}</L10nValue></> : null}</L10nValue>
    </section>

    <L10nValue>{selectedSession && patientId ? <section aria-label={localize("Ngữ cảnh tiếp nhận")} className="sticky top-18 z-20 rounded-xl border border-border bg-surface p-3 text-sm shadow-sm"><p className="font-semibold"><ReferenceText kind="patient" value={patientId} fallback="Bệnh nhân" /></p><p className="mt-1 text-muted-foreground"><L10nValue>{formatLocalDate(selectedSession.sessionDate)}</L10nValue> · {selectedSession.startTime}–{selectedSession.endTime} · <ReferenceText kind="department" value={selectedSession.departmentId} fallback="Khoa" /></p></section> : null}</L10nValue>

    <L10nValue>{selectedSession ? <section className="space-y-4 rounded-xl border border-border bg-surface p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div><h2 className="text-lg font-semibold"><L10n>{"Tiếp nhận vào buổi "}</L10n><L10nValue>{formatLocalDate(selectedSession.sessionDate)}</L10nValue> · {selectedSession.startTime}</h2><p className="mt-1 text-sm text-muted-foreground"><L10nValue>{needsRefresh ? "Đang cập nhật số chỗ." : `Còn ${selectedSession.availableCount} chỗ.`}</L10nValue><L10n>{" Tiếp nhận tại quầy ghi nhận bệnh nhân có mặt. Phiếu được xác nhận sau khi hoàn tất thanh toán."}</L10n></p></div>
        <Button type="button" variant="secondary" disabled={creating || needsRefresh || Boolean(createError?.uncertain)} loading={registrationLoading} onClick={() => void loadRegistrations(selectedSession, registrationPage)}><L10n>{"Tải phiếu trong buổi"}</L10n></Button>
      </div>

      <form className="border-t border-border pt-4" onSubmit={event => { event.preventDefault(); if (submitBody) void submitRegistration(submitBody); else register(); }}>
        <fieldset disabled={Boolean(createdRegistration)} className="grid gap-4 sm:grid-cols-2">
        <label className="space-y-1 text-sm font-medium" htmlFor="reception-visit-type"><L10n>{"Loại lượt khám"}</L10n><select id="reception-visit-type" value={visitType} onChange={event => {
            followUpSourceGeneration.current++;
            setCreatedRegistration(null);
            setVisitType(event.target.value as "NEW_VISIT" | "FOLLOW_UP"); setCreateError(null);
            setFollowUpSources(null); setSelectedFollowUpSource(null); setFollowUpSourceError(null);
            setUseFollowUpRecommendation(true); setReason(""); setReplacementReason(""); setPatientAgreedToDoctorReplacement(false);
            resetReturnVisit();
          }} disabled={creating || Boolean(createError?.uncertain)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3">
            <option value="NEW_VISIT"><L10n>{"Khám đợt mới"}</L10n></option><option value="FOLLOW_UP"><L10n>{"Tái khám từ lượt đã hoàn tất"}</L10n></option>
          </select>
        </label>
        <label className="space-y-1 text-sm font-medium" htmlFor="reception-mode"><L10n>{"Hình thức tiếp nhận"}</L10n><select id="reception-mode" value={receptionMode} onChange={event => setReceptionMode(event.target.value as "AT_COUNTER" | "PREBOOKED")} disabled={creating || Boolean(createError?.uncertain)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3">
            <option value="AT_COUNTER"><L10n>{"Đang có mặt tại quầy"}</L10n></option><option value="PREBOOKED"><L10n>{"Đăng ký trước, sẽ đến sau"}</L10n></option>
          </select>
        </label>
        <L10nValue>{visitType === "FOLLOW_UP" ? <section className="space-y-3 rounded-lg border border-border p-4 sm:col-span-2">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div><h3 className="font-semibold"><L10n>{"Lượt khám trước"}</L10n></h3><p className="text-sm text-muted-foreground"><L10n>{"Chỉ chọn lượt khám theo lịch đã hoàn tất của cùng bệnh nhân và khoa."}</L10n></p></div>
            <Button type="button" loading={followUpSourceLoading} disabled={!patientId.trim() || creating || Boolean(createError?.uncertain)} onClick={() => void loadFollowUpSources(0)}><L10n>{"Tìm lượt khám trước"}</L10n></Button>
          </div>
          <L10nValue>{followUpSourceError ? <AsyncState kind="error" message={localize(followUpSourceError.message)} correlationId={followUpSourceError.correlationId} onRetry={() => void loadFollowUpSources(followUpSourcePage)} /> : null}</L10nValue>
          <L10nValue>{followUpSources ? followUpSources.length ? <ul className="divide-y divide-border rounded-lg border border-border"><L10nValue>{followUpSources.map(source => {
            const sameDepartment = source.departmentId === selectedSession.departmentId;
            const selected = selectedFollowUpSource?.registrationId === source.registrationId;
            return <li key={source.registrationId} className="flex flex-wrap items-center justify-between gap-3 p-3 text-sm">
              <div className="min-w-64 flex-1"><p className="font-medium"><L10n>{"Lượt khám hoàn tất "}</L10n><L10nValue>{formatInstant(source.completedAt)}</L10nValue></p><p className="mt-1 text-muted-foreground"><L10n>{"Hoàn tất "}</L10n><L10nValue>{formatInstant(source.completedAt)}</L10nValue> · Khoa <ReferenceText kind="department" value={source.departmentId} fallback="Khoa" /><L10n>{" · Bác sĩ "}</L10n><ReferenceText kind="doctor" value={source.doctorId} fallback="Bác sĩ" /></p><p className="mt-1 text-muted-foreground"><L10nValue>{source.recommendation ? `Có đề xuất: ${formatLocalDate(source.recommendation.recommendedFrom)} – ${formatLocalDate(source.recommendation.recommendedThrough)}` : "Không có đề xuất bác sĩ; cần ghi lý do tự tái khám."}</L10nValue></p></div>
              <Button type="button" aria-pressed={selected} disabled={!sameDepartment || creating || Boolean(createError?.uncertain)} onClick={() => {
                setSelectedFollowUpSource(source); setUseFollowUpRecommendation(Boolean(source.recommendation)); setCreateError(null);
                setPatientAgreedToDoctorReplacement(false); setReplacementReason("");
              }}><L10nValue>{sameDepartment ? selected ? "Đã chọn" : "Chọn lượt khám trước" : "Khác khoa"}</L10nValue></Button>
            </li>;
          })}</L10nValue></ul> : <p role="status" className="rounded-lg border border-border p-3 text-sm text-muted-foreground"><L10n>{"Không có lượt khám theo lịch đã hoàn tất trong trang này."}</L10n></p> : null}</L10nValue>
          <L10nValue>{followUpSourcePages > 1 ? <nav aria-label={localize("Trang lượt khám trước")} className="flex items-center justify-between text-sm"><Button type="button" disabled={followUpSourceLoading || followUpSourcePage === 0} onClick={() => void loadFollowUpSources(followUpSourcePage - 1)}><L10n>{"Trang trước"}</L10n></Button><span>Trang <L10nValue>{followUpSourcePage + 1}</L10nValue> / <L10nValue>{followUpSourcePages}</L10nValue></span><Button type="button" disabled={followUpSourceLoading || followUpSourcePage + 1 >= followUpSourcePages} onClick={() => void loadFollowUpSources(followUpSourcePage + 1)}>Trang sau</Button></nav> : null}</L10nValue>
          <L10nValue>{selectedFollowUpSource ? <div className="space-y-2 rounded-lg border border-primary/30 bg-primary/5 p-3 text-sm">
            <p role="status"><L10n>{"Đã chọn lượt nguồn hoàn tất "}</L10n><L10nValue>{formatInstant(selectedFollowUpSource.completedAt)}</L10nValue> · <ReferenceText kind="patient" value={patientId} fallback="Bệnh nhân" /> · <ReferenceText kind="department" value={selectedFollowUpSource.departmentId} fallback="Khoa" /> · <ReferenceText kind="doctor" value={selectedFollowUpSource.doctorId} fallback="Bác sĩ lượt trước" /><L10n>{". Chưa đăng ký tái khám cho đến khi gửi và được xác nhận."}</L10n></p>
            <L10nValue>{selectedFollowUpSource.recommendation ? <>
              <label className="flex items-center gap-2"><input type="checkbox" checked={useFollowUpRecommendation} onChange={event => { setUseFollowUpRecommendation(event.target.checked); setCreateError(null); }} disabled={creating || Boolean(createError?.uncertain)} /><L10n>{"Dùng đề xuất của bác sĩ"}</L10n></label>
              <L10nValue>{selectedRecommendation ? <><p><L10n>{"Thời gian khuyến nghị: "}</L10n><L10nValue>{formatLocalDate(selectedRecommendation.recommendedFrom)}</L10nValue> – <L10nValue>{formatLocalDate(selectedRecommendation.recommendedThrough)}</L10nValue></p><p className="whitespace-pre-wrap">{selectedRecommendation.reason}</p><L10nValue>{selectedRecommendation.preparationInstructions ? <p className="whitespace-pre-wrap text-muted-foreground"><L10n>{"Chuẩn bị: "}</L10n>{selectedRecommendation.preparationInstructions}</p> : null}</L10nValue></> : null}</L10nValue>
            </> : <p><L10n>{"Không có đề xuất bác sĩ; cần ghi lý do tái khám để đăng ký tự quay lại."}</L10n></p>}</L10nValue>
            <L10nValue>{recommendationOutOfWindow ? <p role="status" className="rounded-md border border-warning/40 bg-warning/10 p-3 text-warning"><L10n>{"Ngày buổi khám nằm ngoài thời gian bác sĩ khuyến nghị. Có thể tiếp tục nếu bệnh nhân muốn đăng ký."}</L10n></p> : null}</L10nValue>
            <L10nValue>{doctorReplacement ? <div className="space-y-2 border-t border-border pt-3">
              <label className="block space-y-1 font-medium" htmlFor="follow-up-replacement-reason"><L10n>{"Lý do chọn bác sĩ khác"}</L10n><textarea id="follow-up-replacement-reason" value={replacementReason} maxLength={1000} onChange={event => { setReplacementReason(event.target.value); setCreateError(null); }} disabled={creating || Boolean(createError?.uncertain)} className="min-h-20 w-full rounded-lg border border-control-border bg-surface px-3 py-2" />
              </label>
              <label className="flex items-start gap-2"><input type="checkbox" checked={patientAgreedToDoctorReplacement} onChange={event => { setPatientAgreedToDoctorReplacement(event.target.checked); setCreateError(null); }} disabled={creating || Boolean(createError?.uncertain)} /><span><L10n>{"Bệnh nhân đồng ý được tiếp nhận bởi bác sĩ đã chọn."}</L10n></span></label>
            </div> : null}</L10nValue>
          </div> : null}</L10nValue>
        </section> : null}</L10nValue>
        <label className="space-y-1 text-sm font-medium sm:col-span-2" htmlFor="reception-reason"><L10nValue>{visitType === "FOLLOW_UP" ? selectedRecommendation ? "Lý do đăng ký tái khám (nếu có)" : "Lý do đăng ký tái khám" : "Ghi chú tùy chọn"}</L10nValue>
          <textarea id="reception-reason" value={reason} maxLength={1000} onChange={event => { setReason(event.target.value); setCreateError(null); }} disabled={creating || Boolean(createError?.uncertain)} className="min-h-20 w-full rounded-lg border border-control-border bg-surface px-3 py-2" />
        </label>
        <L10nValue>{visitType === "NEW_VISIT" ? <section aria-label={localize("Quay lại sau lượt đã kết thúc")} className="space-y-3 rounded-lg border border-border p-4 text-sm sm:col-span-2">
          <label className="flex items-center gap-2 font-medium"><input type="checkbox" checked={isReturnVisit} disabled={!patientId.trim() || creating || Boolean(createError?.uncertain)} onChange={event => {
            setCreateError(null);
            if (event.target.checked) { setIsReturnVisit(true); void loadReturnSources(); } else resetReturnVisit();
          }} /><L10n>{"Bệnh nhân quay lại sau lượt đã kết thúc"}</L10n></label>
          <L10nValue>{returnSourceError ? <AsyncState kind="error" message={localize(returnSourceError.message)} correlationId={returnSourceError.correlationId} onRetry={() => void loadReturnSources()} /> : null}</L10nValue>
          <L10nValue>{isReturnVisit && !returnSources && !returnSourceError ? <AsyncState kind="loading" message={localize("Đang tải các lượt đã kết thúc…")} /> : null}</L10nValue>
          <L10nValue>{isReturnVisit && returnSources ? returnSources.length ? <>
            <label className="block space-y-1 font-medium" htmlFor="reception-return-source"><L10n>{"Lượt đã kết thúc"}</L10n><select id="reception-return-source" value={returnSourceId} onChange={event => { setReturnSourceId(event.target.value); setCreateError(null); }} disabled={creating || Boolean(createError?.uncertain)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 font-normal">
                <option value=""><L10n>{"Chọn lượt"}</L10n></option>
                <L10nValue>{returnSources.map(record => <option key={record.recordId} value={record.recordId}><L10nValue>{formatLocalDate(record.examinationDate)}</L10nValue> · <L10nValue>{returnSourceLabels[record.status]}</L10nValue></option>)}</L10nValue>
              </select>
            </label>
            <label className="block space-y-1 font-medium" htmlFor="reception-return-reason"><L10n>{"Lý do quay lại"}</L10n><textarea id="reception-return-reason" value={returnReason} maxLength={500} onChange={event => { setReturnReason(event.target.value); setCreateError(null); }} disabled={creating || Boolean(createError?.uncertain)} className="min-h-20 w-full rounded-lg border border-control-border bg-surface px-3 py-2 font-normal" />
            </label>
          </> : <p role="status" className="text-muted-foreground"><L10n>{"Bệnh nhân chưa có lượt nào đã kết thúc."}</L10n></p> : null}</L10nValue>
        </section> : null}</L10nValue>
        <L10nValue>{isUuid(patientId.trim()) && !createdRegistration ? <OpenVisitNotice key={patientId.trim()} patientId={patientId.trim()} departmentId={selectedSession.departmentId} reason={independentReason} onReasonChange={value => { setIndependentReason(value); setCreateError(null); }} onSameDepartmentChange={setSameDepartmentOpen} forceReason={forceIndependentReason} disabled={creating || Boolean(createError?.uncertain)} /> : null}</L10nValue>
        <L10nValue>{createError ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger sm:col-span-2">
          <p><L10nValue>{createError.message}</L10nValue></p><L10nValue>{createError.correlationId ? <p className="mt-1 text-xs"><L10n>{"Mã theo dõi: "}</L10n><span className="font-mono">{createError.correlationId}</span></p> : null}</L10nValue>
          <L10nValue>{createError.uncertain ? <p className="mt-2"><L10n>{"Chưa xác định phiếu đã được tạo hay chưa. Hãy gửi lại đúng yêu cầu để dùng cùng khóa idempotency."}</L10n></p> : null}</L10nValue>
        </div> : null}</L10nValue>
        <div className="sm:col-span-2"><Button type="submit" loading={creating} disabled={needsRefresh || Boolean(createdRegistration) || !patientId.trim()}><L10nValue>{submitBody ? "Gửi lại cùng yêu cầu" : "Đăng ký và giữ chỗ"}</L10nValue></Button><L10nValue>{visitType === "FOLLOW_UP" ? <p className="mt-2 text-xs text-muted-foreground"><L10n>{"Tái khám liên kết lượt đã hoàn tất; ngày ngoài khuyến nghị được cảnh báo nhưng không tự động từ chối."}</L10n></p> : null}</L10nValue></div>
        </fieldset>
      </form>

      <L10nValue>{createdRegistration ? <div className="rounded-lg border border-primary/30 bg-primary/5 p-4 text-sm">
        <p role="status" className="font-semibold"><L10nValue>{createdRegistration.status === "AWAITING_PAYMENT" ? "Đang chờ thu tiền" : registrationStatusLabels[createdRegistration.status]}</L10nValue></p>
        <Button type="button" disabled={needsRefresh || creating || releasing || Boolean(pendingRelease)} onClick={nextPatient}><L10n>{"Tiếp nhận bệnh nhân tiếp theo"}</L10n></Button>
        <p className="mt-2"><L10n>{"Chuyển bệnh nhân sang quầy thu ngân trước hạn giữ chỗ. Có thể bàn giao mã phiếu dưới đây để tra cứu đúng lượt."}</L10n></p>
        <label className="mt-3 block space-y-1" htmlFor="reception-handoff-id"><L10n>{"Mã phiếu bàn giao thu ngân"}</L10n><input id="reception-handoff-id" readOnly value={createdRegistration.registrationId} onFocus={event => event.currentTarget.select()} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 font-mono text-sm" />
        </label>
        <Link href={`/appointments/${encodeURIComponent(createdRegistration.registrationId)}`} className={`mt-3 ${linkButton}`}><L10n>{"Mở phiếu khám vừa đăng ký"}</L10n></Link>
        <dl className="mt-3 grid gap-2 sm:grid-cols-2"><div><dt className="text-muted-foreground"><L10n>{"Bệnh nhân"}</L10n></dt><dd><ReferenceText kind="patient" value={createdRegistration.patientId} fallback="Bệnh nhân" /></dd></div><div><dt className="text-muted-foreground"><L10n>{"Hạn giữ chỗ"}</L10n></dt><dd><L10nValue>{formatInstant(createdRegistration.reservationExpiresAt)}</L10nValue></dd></div><div><dt className="text-muted-foreground"><L10n>{"Bác sĩ"}</L10n></dt><dd><ReferenceText kind="doctor" value={createdRegistration.doctorId} fallback="Bác sĩ" /></dd></div><div><dt className="text-muted-foreground"><L10nValue>{queueNumberLabel(clinicIntakeEnabled)}</L10nValue></dt><dd><L10nValue>{createdRegistration.queueNumber ?? "Chưa cấp trước khi xác nhận"}</L10nValue></dd></div><L10nValue>{clinicIntakeEnabled && roomNumberText(createdRegistration.roomQueueNumber) ? <div><dt className="text-muted-foreground"><L10n>{"Số phòng"}</L10n></dt><dd>{createdRegistration.roomQueueNumber}</dd></div> : null}</L10nValue></dl>
        <L10nValue>{clinicIntakeEnabled && createdRegistration.reservationStatus === "PAYMENT_LOCKED" ? <p role="status" className="mt-3 rounded-lg border border-warning/40 bg-warning/10 p-3"><L10n>{"Phiếu đang được thanh toán nên chưa thể trả chỗ."}</L10n></p> : null}</L10nValue>
        <L10nValue>{clinicIntakeEnabled && createdRegistration.reservationStatus === "HELD" ? <div className="mt-3 space-y-2 border-t border-border pt-3">
          <label className="block space-y-1 font-medium" htmlFor="reception-release-reason"><L10n>{"Lý do trả chỗ"}</L10n><input id="reception-release-reason" maxLength={500} value={releaseReason} disabled={releasing || Boolean(pendingRelease)} onChange={event => setReleaseReason(event.target.value)} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 font-normal" />
          </label>
          <L10nValue>{releaseError ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-danger"><p><L10nValue>{releaseError.message}</L10nValue></p><L10nValue>{releaseError.correlationId ? <p className="mt-1 text-xs"><L10n>{"Mã theo dõi: "}</L10n><span className="font-mono">{releaseError.correlationId}</span></p> : null}</L10nValue><L10nValue>{releaseError.uncertain ? <p className="mt-2"><L10n>{"Chưa xác định chỗ đã được trả hay chưa. Gửi lại đúng yêu cầu để tránh xử lý trùng."}</L10n></p> : null}</L10nValue></div> : null}</L10nValue>
          <Button type="button" variant="danger" loading={releasing} disabled={needsRefresh || (!pendingRelease && !releaseReason.trim())} onClick={() => void releaseHold()}><L10nValue>{pendingRelease ? "Gửi lại cùng yêu cầu trả chỗ" : "Trả chỗ đã giữ"}</L10nValue></Button>
        </div> : null}</L10nValue>
      </div> : null}</L10nValue>

      <L10nValue>{needsRefresh ? <div role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
        <p><L10nValue>{refreshError ? refreshMessages[refreshKind].failed : refreshMessages[refreshKind].running}</L10nValue></p>
        <L10nValue>{refreshError ? <Button type="button" loading={refreshing} onClick={() => void refreshAfterRegistration(selectedSession, refreshKind)}><L10n>{"Cập nhật danh sách và số chỗ"}</L10n></Button> : null}</L10nValue>
      </div> : null}</L10nValue>
      <L10nValue>{rescheduleNotice ? <p role="status" className="rounded-lg border border-primary/30 bg-primary/5 p-3 text-sm"><L10nValue>{rescheduleNotice}</L10nValue></p> : null}</L10nValue>
      <L10nValue>{registrationError ? <AsyncState kind="error" message={localize(registrationError.message)} correlationId={registrationError.correlationId} onRetry={() => void loadRegistrations(selectedSession, registrationPage)} /> : null}</L10nValue>
      <L10nValue>{registrationList ? <section aria-label={localize("Phiếu trong buổi")} className="border-t border-border pt-4"><h3 className="font-semibold"><L10n>{"Phiếu trong buổi"}</L10n></h3>
        <L10nValue>{registrationList.length ? <ul className="mt-3 divide-y divide-border"><L10nValue>{registrationList.map(item => <li key={item.registrationId} className="py-3 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2"><span><ReferenceText kind="patient" value={item.patientId} fallback="Bệnh nhân" /></span><span><L10nValue>{item.visitType === "FOLLOW_UP" ? "Tái khám" : "Khám mới"}</L10nValue></span><span><L10nValue>{clinicIntakeEnabled ? registrationOutcomeLabel(item) : registrationStatusLabels[item.status]}</L10nValue></span><span><L10nValue>{queueNumberText(item.queueNumber, clinicIntakeEnabled)}</L10nValue></span><L10nValue>{clinicIntakeEnabled && roomNumberText(item.roomQueueNumber) ? <span><L10nValue>{roomNumberText(item.roomQueueNumber)}</L10nValue></span> : null}</L10nValue><L10nValue>{item.readyForExam ? <span><L10n>{"Sẵn sàng khám"}</L10n></span> : null}</L10nValue>
            <L10nValue>{item.checkedInAt ? <span role="status"><L10n>{"Đã ghi nhận khách đến"}</L10n></span> : item.status === "CONFIRMED" ? <Button type="button" loading={arrivalBusyId === item.registrationId} disabled={Boolean(arrivalBusyId)} onClick={() => void recordArrival(item)}><L10nValue>{arrivalError?.registrationId === item.registrationId && arrivalError.uncertain ? "Gửi lại cùng lệnh đến" : "Ghi nhận đã đến"}</L10nValue></Button> : null}</L10nValue>
            <L10nValue>{clinicIntakeEnabled && (item.status === "AWAITING_PAYMENT" || item.status === "CONFIRMED") ? <Button type="button" variant="secondary" disabled={needsRefresh || Boolean(arrivalBusyId) || Boolean(rescheduling)} onClick={() => { setRescheduleNotice(null); setRescheduling(item.registrationId); }}><L10n>{"Đổi lịch"}</L10n></Button> : null}</L10nValue>
          </div>
          <L10nValue>{rescheduling === item.registrationId ? <RescheduleDialog registrationId={item.registrationId} onClose={() => setRescheduling(null)} onRescheduled={afterReschedule} /> : null}</L10nValue>
          <L10nValue>{arrivalError?.registrationId === item.registrationId ? <div role="alert" className="mt-2 rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger"><p><L10nValue>{arrivalError.message}</L10nValue></p><L10nValue>{arrivalError.correlationId ? <p className="mt-1 text-xs"><L10n>{"Mã theo dõi: "}</L10n><span className="font-mono">{arrivalError.correlationId}</span></p> : null}</L10nValue><L10nValue>{arrivalError.uncertain ? <p className="mt-2"><L10n>{"Chưa xác định trạng thái đã đến. Gửi lại cùng lệnh để tránh ghi nhận trùng."}</L10n></p> : null}</L10nValue></div> : null}</L10nValue>
        </li>)}</L10nValue></ul> : <p className="mt-3 text-sm text-muted-foreground"><L10n>{"Chưa có phiếu nào trong buổi này."}</L10n></p>}</L10nValue>
        <L10nValue>{registrationPages > 1 ? <nav aria-label={localize("Trang phiếu trong buổi")} className="mt-3 flex items-center justify-between text-sm"><Button type="button" disabled={registrationLoading || registrationPage === 0} onClick={() => void loadRegistrations(selectedSession, registrationPage - 1)}><L10n>{"Trang trước"}</L10n></Button><span>Trang <L10nValue>{registrationPage + 1}</L10nValue> / <L10nValue>{registrationPages}</L10nValue></span><Button type="button" disabled={registrationLoading || registrationPage + 1 >= registrationPages} onClick={() => void loadRegistrations(selectedSession, registrationPage + 1)}>Trang sau</Button></nav> : null}</L10nValue>
      </section> : null}</L10nValue>
    </section> : null}</L10nValue>
  </div>;
}
