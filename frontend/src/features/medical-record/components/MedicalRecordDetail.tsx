"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { PatientContextBanner } from "@/components/references/PatientContextBanner";
import { ReferenceText } from "@/components/references/ReferenceText";
import { Button } from "@/components/ui/Button";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { TabPanel, Tabs, type TabItem } from "@/components/ui/Tabs";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { formatInstant, formatLocalDate } from "@/lib/format";
import type { Role } from "@/lib/roles";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { medicalRecordApi } from "../api";
import type { DiagnosisDTO, MedicalRecordDTO, RecordCapabilitiesDTO, RecordDisposition } from "../types";
import { AddDiagnosisForm } from "./AddDiagnosisForm";
import { CompleteRecordForm } from "./CompleteRecordForm";
import { CloseRecordForm } from "./CloseRecordForm";
import { RecordLabOrdersPanel } from "./RecordLabOrdersPanel";
import { closureReasonLabels } from "../closure";
import { FollowUpRecommendationPanel } from "./FollowUpRecommendationPanel";
import { RecordAccessPurposeForm } from "./RecordAccessPurposeForm";
import { ClinicalContentSummary } from "./ClinicalContentFields";
import { PatientAllergyPanel } from "./PatientAllergyPanel";
import { RecordDocumentsPanel } from "./RecordDocumentsPanel";
import { RecordCollaborationSection } from "./RecordCollaborationSection";
import type { RecordDocumentDTO } from "../types";

type PrescriptionWorkspaceSlot = ReactNode | ((record: MedicalRecordDTO, canCreate: boolean) => ReactNode);

interface MedicalRecordDetailProps {
  recordId: string;
  notice?: "created" | "updated";
  outpatientEnabled?: boolean;
  schedulingV2Enabled?: boolean;
  cancelLabOrder?: (testId: string, reason: string) => Promise<unknown>;
  prescriptionWorkspace?: PrescriptionWorkspaceSlot;
  documentHref?: (document: RecordDocumentDTO) => string | undefined;
}

interface RequestError {
  message: string;
  correlationId: string | null;
}

type DetailState =
  | { status: "idle" }
  | { status: "success"; record: MedicalRecordDTO }
  | { status: "not-found" }
  | { status: "purpose-required"; patientId: string | null }
  | { status: "denied" }
  | { status: "error"; error: RequestError };

const dispositionLabels: Record<RecordDisposition, string> = {
  OUTPATIENT_FOLLOW_UP: "Theo dõi ngoại trú",
  PRESCRIPTION: "Kê đơn thuốc",
  ADMISSION: "Chuyển nội trú",
  TRANSFER: "Chuyển tuyến/khoa",
  OTHER: "Khác",
};

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể tải chi tiết hồ sơ khám.",
    correlationId: null,
  };
}

function Identifier({ label, kind, value }: { label: string; kind?: string; value: string | null }) {
  return (
    <div>
      <dt className="text-xs font-medium text-muted-foreground"><L10nValue>{label}</L10nValue></dt>
      <dd className="mt-1 break-all">
        <ReferenceText kind={kind} value={value} fallback={label} technical />
      </dd>
    </div>
  );
}

const getServerRole = () => null;
const getServerToken = () => null;

export function MedicalRecordDetail(props: MedicalRecordDetailProps) {
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const token = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, getServerToken);
  return <MedicalRecordDetailContent key={`${props.recordId}:${role ?? "anonymous"}:${token ?? "anonymous"}`} {...props} role={role} />;
}

function MedicalRecordDetailContent({ recordId, notice, outpatientEnabled = false, schedulingV2Enabled = false, cancelLabOrder, prescriptionWorkspace, documentHref, role }: MedicalRecordDetailProps & { role: Role | null }) {
  const { localize } = useLocale();
  const router = useRouter();
  const [state, setState] = useState<DetailState>({ status: "idle" });
  const [retryToken, setRetryToken] = useState(0);
  const [refreshing, setRefreshing] = useState(true);
  const [writeInFlight, setWriteInFlight] = useState(false);
  const [refreshError, setRefreshError] = useState<RequestError | null>(null);
  const [capabilities, setCapabilities] = useState<RecordCapabilitiesDTO | null>(null);
  const [pendingRecord, setPendingRecord] = useState<MedicalRecordDTO | null>(null);
  // The open tab lives in ?tab= so a reload, a shared link or the back button keeps it.
  const [tab, setTab] = useState(() => typeof window === "undefined" ? "exam" : new URLSearchParams(window.location.search).get("tab") ?? "exam");
  const chooseTab = useCallback((next: string) => {
    setTab(next);
    const url = new URL(window.location.href);
    if (next === "exam") url.searchParams.delete("tab"); else url.searchParams.set("tab", next);
    window.history.replaceState(window.history.state, "", url);
  }, []);
  const record = state.status === "success" ? state.record : null;
  const recordRef = useRef<MedicalRecordDTO | null>(record);
  const activeForms = useRef({ diagnosis: false, completion: false });
  const inFlightWrites = useRef(new Set<"diagnosis" | "completion">());
  const refreshPendingRef = useRef(true);
  const pendingRecordRef = useRef(false);

  const setDiagnosisActive = useCallback((active: boolean) => {
    activeForms.current.diagnosis = active;
  }, []);
  const setCompletionActive = useCallback((active: boolean) => {
    activeForms.current.completion = active;
  }, []);
  const setDiagnosisBusy = useCallback((busy: boolean) => {
    if (busy) inFlightWrites.current.add("diagnosis");
    else inFlightWrites.current.delete("diagnosis");
    setWriteInFlight(inFlightWrites.current.size > 0);
  }, []);
  const setCompletionBusy = useCallback((busy: boolean) => {
    if (busy) inFlightWrites.current.add("completion");
    else inFlightWrites.current.delete("completion");
    setWriteInFlight(inFlightWrites.current.size > 0);
  }, []);
  const isInteractionBlocked = useCallback(() => (
    refreshPendingRef.current || pendingRecordRef.current || inFlightWrites.current.size > 0
  ), []);

  const retry = useCallback(() => {
    if (inFlightWrites.current.size > 0 || pendingRecordRef.current || refreshPendingRef.current) return;
    refreshPendingRef.current = true;
    setRefreshError(null);
    setRefreshing(true);
    if (!recordRef.current) setState({ status: "idle" });
    setRetryToken((value) => value + 1);
  }, []);

  function addCreatedDiagnosis(diagnosis: DiagnosisDTO) {
    setState((current) => current.status === "success"
      ? {
          ...current,
          record: {
            ...current.record,
            diagnoses: [...current.record.diagnoses, diagnosis],
          },
        }
      : current);
  }

  function applyRecord(updated: MedicalRecordDTO) {
    const current = recordRef.current;
    const activeWork = activeForms.current.diagnosis || activeForms.current.completion;
    if (current?.status === "OPEN" && updated.status !== current.status && activeWork) {
      pendingRecordRef.current = true;
      setPendingRecord(updated);
      return;
    }
    pendingRecordRef.current = false;
    setPendingRecord(null);
    setRefreshError(null);
    setState({ status: "success", record: updated });
  }

  function acceptPendingRecord() {
    if (!pendingRecord) return;
    pendingRecordRef.current = false;
    activeForms.current = { diagnosis: false, completion: false };
    setRefreshError(null);
    setState({ status: "success", record: pendingRecord });
    setPendingRecord(null);
  }

  useEffect(() => {
    recordRef.current = record;
  }, [record]);

  useEffect(() => {
    refreshPendingRef.current = true;
    let active = true;
    // A capability failure must leave the screen read only, never block reading.
    const capabilitiesRequest = medicalRecordApi.capabilities(recordId).catch(() => null);
    medicalRecordApi.getById(recordId)
      .then(async (updated) => {
        const loadedCapabilities = await capabilitiesRequest;
        if (!active) return;
        setCapabilities(loadedCapabilities);
        const current = recordRef.current;
        const activeWork = activeForms.current.diagnosis || activeForms.current.completion;
        if (current?.status === "OPEN" && updated.status !== current.status && activeWork) {
          pendingRecordRef.current = true;
          setPendingRecord(updated);
          setRefreshError(null);
        } else {
          pendingRecordRef.current = false;
          setPendingRecord(null);
          setRefreshError(null);
          setState({ status: "success", record: updated });
        }
        refreshPendingRef.current = false;
        setRefreshing(false);
      })
      .catch(async (cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 401) {
          refreshPendingRef.current = false;
          setRefreshing(false);
          router.replace("/login");
          return;
        }
        if (cause instanceof ApiRequestError && (cause.code === "RECORD_ACCESS_PURPOSE_REQUIRED" || cause.code === "RECORD_ACCESS_DENIED")) {
          const loadedCapabilities = await capabilitiesRequest;
          if (!active) return;
          recordRef.current = null;
          setCapabilities(null);
          setState(cause.code === "RECORD_ACCESS_DENIED" ? { status: "denied" } : { status: "purpose-required", patientId: loadedCapabilities?.patientId ?? null });
          refreshPendingRef.current = false;
          setRefreshing(false);
          return;
        }
        if (cause instanceof ApiRequestError && cause.status === 404) {
          if (recordRef.current) {
            setRefreshError({ message: "Không tìm thấy hồ sơ mới nhất. Bản đang xem được giữ lại để bảo toàn nội dung đang nhập.", correlationId: cause.correlationId });
          } else {
            setState({ status: "not-found" });
          }
          refreshPendingRef.current = false;
          setRefreshing(false);
          return;
        }
        if (recordRef.current) setRefreshError(getRequestError(cause));
        else setState({ status: "error", error: getRequestError(cause) });
        refreshPendingRef.current = false;
        setRefreshing(false);
      });
    return () => {
      active = false;
    };
  }, [recordId, retryToken, router]);

  if (!record) {
    if (state.status === "not-found") {
      return (
        <section role="status" className="mt-6 border-t border-border py-6">
          <h2 className="text-lg font-semibold"><L10n>{"Không tìm thấy hồ sơ khám"}</L10n></h2>
          <p className="mt-2 text-sm text-muted-foreground"><L10n>{"Hồ sơ có thể đã bị xóa hoặc mã truy cập không còn hợp lệ."}</L10n></p>
          <div className="mt-4 flex flex-wrap gap-3">
            <Button type="button" variant="secondary" onClick={retry}><L10n>{"Thử tải lại"}</L10n></Button>
            <Link href="/records" className="inline-flex min-h-11 items-center px-3 text-sm font-medium text-primary underline underline-offset-4"><L10n>{"Quay lại tra cứu"}</L10n></Link>
          </div>
        </section>
      );
    }
    if (state.status === "denied") {
      return (
        <section role="status" className="mt-6 border-t border-border py-6">
          <h2 className="text-lg font-semibold"><L10n>{"Không có quyền xem hồ sơ"}</L10n></h2>
          <p className="mt-2 text-sm text-muted-foreground"><L10n>{"Hồ sơ này nằm ngoài phạm vi chăm sóc của bạn."}</L10n></p>
          <Link href="/records" className="mt-4 inline-flex min-h-11 items-center text-sm font-medium text-primary underline underline-offset-4"><L10n>{"Quay lại tra cứu"}</L10n></Link>
        </section>
      );
    }
    if (state.status === "purpose-required") {
      return (
        <section aria-label={localize("Khai báo mục đích xem hồ sơ")} className="mt-6 border-t border-border py-6">
          <h2 className="text-lg font-semibold"><L10n>{"Cần khai báo mục đích xem hồ sơ"}</L10n></h2>
          <L10nValue>{state.patientId
            ? <RecordAccessPurposeForm patientId={state.patientId} role={role} onDeclared={retry} />
            : <p className="mt-2 text-sm text-muted-foreground"><L10n>{"Chưa xác định được bệnh nhân của hồ sơ. Hãy tra cứu lại từ danh sách hồ sơ."}</L10n></p>}</L10nValue>
        </section>
      );
    }
    if (state.status === "error") {
      return (
        <section role="alert" className="mt-4 flex flex-wrap items-center justify-between gap-3 border-l-2 border-danger px-4 py-3 text-sm text-danger">
          <div>
            <p><L10nValue>{state.error.message}</L10nValue></p>
            <L10nValue>{state.error.correlationId ? <details className="mt-2 text-xs"><summary className="min-h-8 cursor-pointer underline underline-offset-4"><L10n>{"Thông tin hỗ trợ"}</L10n></summary><p className="mt-1 break-all font-mono"><L10n>{"Mã theo dõi: "}</L10n>{state.error.correlationId}</p></details> : null}</L10nValue>
          </div>
          <Button type="button" variant="secondary" onClick={retry}><L10n>{"Thử tải lại"}</L10n></Button>
        </section>
      );
    }
    return <AsyncState kind="loading" message={localize("Đang tải chi tiết hồ sơ khám…")} />;
  }

  const isOpen = record.status === "OPEN";
  const isClosed = record.status === "CLOSED_WITHOUT_CONCLUSION";
  const effectiveCorrections = record.effectiveCorrections ?? [];
  const hasEffectiveCorrection = (section: string) => effectiveCorrections.some((correction) => correction.section === section);
  const canWrite = isOpen && capabilities?.canWriteContent === true;
  const canComplete = outpatientEnabled && isOpen && Boolean(record.appointmentId) && capabilities?.canComplete === true;
  const readOnlyNotice = !isOpen || refreshing ? null
    : capabilities === null ? "Chưa xác định được quyền ghi, hồ sơ đang ở chế độ chỉ đọc."
    : capabilities.writeBlockedReason === "NOT_ASSIGNED_DOCTOR" ? "Bạn đang xem hồ sơ do bác sĩ khác phụ trách. Chỉ bác sĩ phụ trách mới được ghi vào hồ sơ này."
    : capabilities.writeBlockedReason === "NOT_IN_EXAM" ? "Lượt khám chưa ở trạng thái đang khám nên chưa thể ghi vào hồ sơ."
    : null;
  const actionsBlocked = refreshing || pendingRecord !== null || writeInFlight;
  const actionClass = "inline-flex min-h-11 items-center rounded-lg px-3 py-2 text-sm font-medium underline decoration-border underline-offset-4 hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary";

  const showLab = outpatientEnabled && Boolean(record.appointmentId);
  const showPrescription = (role === "ADMIN" || role === "DOCTOR") && Boolean(prescriptionWorkspace);
  const showFinish = canComplete || record.status === "COMPLETED";
  const tabs: TabItem[] = [
    { id: "exam", label: "Khám & chẩn đoán", badge: record.diagnoses.length },
    ...(showLab ? [{ id: "lab", label: "Xét nghiệm" }] : []),
    ...(showPrescription ? [{ id: "prescription", label: "Đơn thuốc" }] : []),
    { id: "care", label: "Liệu trình & hội chẩn" },
    { id: "documents", label: "Tài liệu" },
    ...(showFinish ? [{ id: "finish", label: record.status === "COMPLETED" ? "Tái khám" : "Kết thúc khám" }] : []),
    { id: "info", label: "Thông tin hồ sơ" },
  ];
  const activeTab = tabs.some(item => item.id === tab) ? tab : "exam";
  const panel = { idPrefix: "record", active: activeTab };

  return (
    <section className="mt-6 space-y-5">
      <L10nValue>{notice ? (
        <p role="status" className="border-l-2 border-success px-4 py-2 text-sm text-success">
          <L10nValue>{notice === "created" ? "Đã tạo hồ sơ khám thành công." : "Đã cập nhật hồ sơ khám thành công."}</L10nValue>
        </p>
      ) : null}</L10nValue>

      <PatientAllergyPanel patientId={record.patientId} role={role} sourceRecordId={record.recordId} />

      <header>
        <PatientContextBanner label={localize("Ngữ cảnh hồ sơ khám")} patientId={record.patientId} departmentId={record.departmentId} doctorId={record.responsibleDoctorId ?? record.doctorId} date={record.examinationDate} dateLabel="Ngày khám">
          <StatusBadge tone={isOpen ? "info" : isClosed ? "warning" : "success"}><L10nValue>{isOpen ? "Đang khám" : isClosed ? "Kết thúc không kết luận" : "Đã hoàn tất khám"}</L10nValue></StatusBadge>
        </PatientContextBanner>
        <L10nValue>{record.status === "COMPLETED" ? <p className="mt-3 text-sm text-muted-foreground"><L10n>{"Hoàn tất khám không đồng nghĩa đã ký xác nhận hoặc chuyển lưu trữ."}</L10n></p> : null}</L10nValue>
        <L10nValue>{isClosed ? (
          <dl className="mt-3 border-l-2 border-warning px-4 py-2 text-sm">
            <dt className="text-xs font-medium text-muted-foreground"><L10n>{"Lý do kết thúc"}</L10n></dt>
            <dd><L10nValue>{record.closureReason ? closureReasonLabels[record.closureReason] : "Chưa ghi nhận"}</L10nValue></dd>
            <dt className="mt-2 text-xs font-medium text-muted-foreground"><L10n>{"Ghi chú và dặn dò"}</L10n></dt>
            <dd><L10nValue>{record.closureNote ?? "Chưa ghi nhận"}</L10nValue></dd>
            <dt className="mt-2 text-xs font-medium text-muted-foreground"><L10n>{"Thời điểm"}</L10n></dt>
            <dd><L10nValue>{formatInstant(record.closedAt ?? null)}</L10nValue></dd>
          </dl>
        ) : null}</L10nValue>
        <L10nValue>{record.previousRecordId ? <p className="mt-3 text-sm"><Link href={`/records/${encodeURIComponent(record.previousRecordId)}`} className="font-medium underline underline-offset-4"><L10n>{"Lượt trước — chỉ tham khảo"}</L10n></Link></p> : null}</L10nValue>
      </header>

      <L10nValue>{refreshing ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Đang cập nhật hồ sơ…"}</L10n></p> : null}</L10nValue>
      <L10nValue>{refreshError ? <div role="alert" className="border-l-2 border-danger px-4 py-2 text-sm text-danger"><p><L10nValue>{refreshError.message}</L10nValue></p><L10nValue>{refreshError.correlationId ? <details className="mt-1 text-xs"><summary className="min-h-8 cursor-pointer underline underline-offset-4"><L10n>{"Thông tin hỗ trợ"}</L10n></summary><p className="mt-1 break-all font-mono"><L10n>{"Mã theo dõi: "}</L10n>{refreshError.correlationId}</p></details> : null}</L10nValue></div> : null}</L10nValue>
      <L10nValue>{pendingRecord ? (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-3 border-l-2 border-warning px-4 py-3">
          <p className="text-sm text-foreground"><L10n>{"Hồ sơ đã thay đổi trạng thái khi bạn đang nhập. Bản nháp được giữ lại; hãy xem dữ liệu mới trước khi tiếp tục."}</L10n></p>
          <Button type="button" variant="secondary" onClick={acceptPendingRecord}><L10n>{"Xem dữ liệu mới"}</L10n></Button>
        </div>
      ) : null}</L10nValue>

      <L10nValue>{readOnlyNotice ? <p role="status" className="border-l-2 border-warning px-4 py-2 text-sm text-foreground"><L10nValue>{readOnlyNotice}</L10nValue></p> : null}</L10nValue>

      <div className="sticky top-18 z-10 bg-background">
        <Tabs idPrefix="record" label={localize("Các phần của hồ sơ khám")} tabs={tabs} active={activeTab} onChange={chooseTab} />
      </div>

      <TabPanel {...panel} id="exam" className="space-y-6 focus-visible:outline-none">
      <section aria-label={localize("Nội dung đã ghi")} className="space-y-4">
      <p className="text-sm text-muted-foreground"><L10n>{"Bác sĩ phụ trách: "}</L10n><ReferenceText kind="doctor" value={record.responsibleDoctorId ?? record.doctorId} fallback="Bác sĩ" /> <span aria-hidden="true">·</span><L10n>{" Cập nhật lần cuối: "}</L10n><L10nValue>{formatInstant(record.updatedAt ?? record.createdAt)}</L10nValue></p>
      <L10nValue>{effectiveCorrections.length ? <section aria-label={localize("Chỉnh sửa có hiệu lực")} className="space-y-3 rounded-xl border border-success bg-surface p-5">
        <div><h2 className="text-lg font-semibold"><L10n>{"Chỉnh sửa có hiệu lực"}</L10n></h2><p className="mt-1 text-sm text-muted-foreground"><L10n>{"Bản ghi ban đầu vẫn được lưu; nội dung bên dưới là giá trị đang có hiệu lực."}</L10n></p></div>
        {effectiveCorrections.map((correction) => <article key={correction.section} className="grid gap-3 border-t border-border pt-3 md:grid-cols-2">
          <div><h3 className="text-xs font-medium text-muted-foreground"><L10n>{"Nội dung gốc"}</L10n></h3><p className="mt-1 whitespace-pre-wrap text-sm">{correction.originalText}</p></div>
          <div><h3 className="text-xs font-medium text-success"><L10n>{"Nội dung đang có hiệu lực"}</L10n></h3><p className="mt-1 whitespace-pre-wrap text-sm font-medium">{correction.correctedText}</p></div>
          <p className="text-sm md:col-span-2"><span className="font-medium"><L10n>{"Lý do đính chính:"}</L10n></span> {correction.reason} <span className="text-muted-foreground">· <L10n>{"Thời điểm áp dụng: "}</L10n><L10nValue>{formatInstant(correction.appliedAt)}</L10nValue></span></p>
        </article>)}
      </section> : null}</L10nValue>
      <div className="grid gap-7 lg:grid-cols-[minmax(0,1fr)_minmax(18rem,0.8fr)]">
        <section aria-labelledby="exam-content-heading">
          <h2 id="exam-content-heading" className="text-lg font-semibold"><L10nValue>{effectiveCorrections.length ? "Nội dung khám ban đầu" : "Nội dung khám"}</L10nValue></h2>
          <dl className="mt-4 grid gap-5 sm:grid-cols-2">
            <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10nValue>{hasEffectiveCorrection("SYMPTOMS") ? "Triệu chứng ban đầu" : "Triệu chứng"}</L10nValue></dt><dd className="mt-1 whitespace-pre-wrap text-sm leading-6"><L10nValue>{record.symptoms ?? "—"}</L10nValue></dd></div>
            <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Hướng xử trí"}</L10n></dt><dd className="mt-1 text-sm leading-6"><L10nValue>{record.disposition ? dispositionLabels[record.disposition] : "—"}</L10nValue></dd><L10nValue>{record.dispositionNote ? <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground"><L10nValue>{hasEffectiveCorrection("DISPOSITION_NOTE") ? "Ghi chú ban đầu: " : ""}</L10nValue>{record.dispositionNote}</p> : null}</L10nValue></div>
          </dl>
          <ClinicalContentSummary sections={record} />
        </section>

        <section aria-labelledby="diagnoses-heading" className="border-t border-border pt-5 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 id="diagnoses-heading" className="text-lg font-semibold"><L10nValue>{hasEffectiveCorrection("DIAGNOSIS") ? "Chẩn đoán ban đầu" : "Chẩn đoán"}</L10nValue></h2>
            <span className="text-sm text-muted-foreground">{record.diagnoses.length}<L10n>{" chẩn đoán"}</L10n></span>
          </div>
          <L10nValue>{record.diagnoses.length === 0 ? <p className="mt-3 text-sm text-muted-foreground"><L10n>{"Chưa có chẩn đoán."}</L10n></p> : (
            <ul className="mt-3 divide-y divide-border">
              <L10nValue>{record.diagnoses.map((diagnosis) => (
                <li key={diagnosis.diagnosisId} className="py-3 first:pt-0 last:pb-0">
                  <div className="flex flex-wrap items-center gap-2"><p className="font-medium">{diagnosis.diagnosisName}</p><L10nValue>{diagnosis.icdCode ? <StatusBadge tone="neutral">ICD {diagnosis.icdCode}</StatusBadge> : null}</L10nValue></div>
                  <L10nValue>{diagnosis.description ? <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground"><L10nValue>{diagnosis.description}</L10nValue></p> : null}</L10nValue>
                </li>
              ))}</L10nValue>
            </ul>
          )}</L10nValue>
        </section>
      </div>
      </section>

      <L10nValue>{canWrite ? <section aria-label={localize("Ghi nhận lượt đang mở")} className="space-y-3">
        <details className="border-y border-border py-3">
          <summary className="min-h-10 cursor-pointer py-2 text-sm font-semibold text-primary underline decoration-border underline-offset-4"><L10n>{"Thêm chẩn đoán"}</L10n></summary>
          <AddDiagnosisForm recordId={record.recordId} disabled={actionsBlocked} isInteractionBlocked={isInteractionBlocked} onActivityChange={setDiagnosisActive} onBusyChange={setDiagnosisBusy} onCreated={addCreatedDiagnosis} />
        </details>
        <details>
          <summary className="min-h-10 cursor-pointer py-2 text-sm font-medium text-muted-foreground"><L10n>{"Tác vụ khác"}</L10n></summary>
          <div className="flex flex-wrap gap-1 pt-1">
            <Link aria-disabled={actionsBlocked || undefined} tabIndex={actionsBlocked ? -1 : undefined} onClick={(event) => { if (actionsBlocked) event.preventDefault(); }} className={`${actionClass} ${actionsBlocked ? "pointer-events-none opacity-55" : "text-foreground"}`} href={`/records/${record.recordId}/edit`}><L10n>{"Sửa triệu chứng"}</L10n></Link>
          </div>
        </details>
      </section> : null}</L10nValue>
      </TabPanel>

      <L10nValue>{showLab && record.appointmentId ? (
        <TabPanel {...panel} id="lab" className="focus-visible:outline-none">
          <section id="record-lab-orders" aria-label={localize("Xét nghiệm của lượt khám")}>
            <h2 className="text-lg font-semibold"><L10n>{"Xét nghiệm của lượt khám"}</L10n></h2>
            <p className="mt-1 text-sm text-muted-foreground"><L10n>{"Chỉ định và theo dõi xét nghiệm theo đúng lượt khám này."}</L10n></p>
            <div className="mt-2 flex flex-wrap items-center gap-1">
              <L10nValue>{canWrite ? (
                <Link aria-disabled={actionsBlocked || undefined} tabIndex={actionsBlocked ? -1 : undefined} onClick={(event) => { if (actionsBlocked) event.preventDefault(); }} className={`inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${actionsBlocked ? "pointer-events-none opacity-55" : ""}`} href={`/records/${record.recordId}/lab-order`}><L10n>{"Lập chỉ định xét nghiệm"}</L10n></Link>
              ) : null}</L10nValue>
              <Link aria-disabled={actionsBlocked || undefined} tabIndex={actionsBlocked ? -1 : undefined} onClick={(event) => { if (actionsBlocked) event.preventDefault(); }} className={`${actionClass} ${actionsBlocked ? "pointer-events-none opacity-55" : "text-primary"}`} href={`/lab?episodeId=${encodeURIComponent(record.appointmentId)}`}><L10n>{"Xem phiếu xét nghiệm"}</L10n></Link>
            </div>
            <L10nValue>{schedulingV2Enabled ? <RecordLabOrdersPanel recordId={record.recordId} cancelOrder={canWrite && !actionsBlocked ? cancelLabOrder : undefined} canWithdraw={canWrite && !actionsBlocked} /> : null}</L10nValue>
          </section>
        </TabPanel>
      ) : null}</L10nValue>

      <L10nValue>{showPrescription ? (
        <TabPanel {...panel} id="prescription" className="focus-visible:outline-none">
          <section aria-labelledby="record-prescription-heading">
            <h2 id="record-prescription-heading" className="text-lg font-semibold"><L10n>{"Đơn thuốc của hồ sơ khám"}</L10n></h2>
            <L10nValue>{typeof prescriptionWorkspace === "function"
              ? prescriptionWorkspace(record, outpatientEnabled && role === "DOCTOR" && canWrite && Boolean(record.appointmentId) && !actionsBlocked)
              : prescriptionWorkspace}</L10nValue>
          </section>
        </TabPanel>
      ) : null}</L10nValue>

      <TabPanel {...panel} id="care" className="focus-visible:outline-none">
        <RecordCollaborationSection record={record} capabilities={capabilities} onCommandSuccess={retry} />
      </TabPanel>

      <TabPanel {...panel} id="documents" className="focus-visible:outline-none">
        <RecordDocumentsPanel recordId={record.recordId} sourceHref={documentHref} />
      </TabPanel>

      <L10nValue>{showFinish ? (
        <TabPanel {...panel} id="finish" className="space-y-4 focus-visible:outline-none">
          <L10nValue>{canComplete ? (
            <details className="border-b border-border pb-3">
              <summary className="min-h-10 cursor-pointer py-2 text-sm font-semibold text-foreground underline decoration-border underline-offset-4"><L10n>{"Hoàn tất lần khám"}</L10n></summary>
              <p className="pb-2 text-sm text-muted-foreground"><L10n>{"Mở khi đã hoàn tất chẩn đoán và hướng xử trí. Hồ sơ sẽ không thể chỉnh sửa sau khi kết thúc."}</L10n></p>
              <CompleteRecordForm record={record} disabled={actionsBlocked} isInteractionBlocked={isInteractionBlocked} onActivityChange={setCompletionActive} onBusyChange={setCompletionBusy} onCompleted={applyRecord} />
            </details>
          ) : null}</L10nValue>
          <L10nValue>{canComplete && schedulingV2Enabled && role === "DOCTOR" ? (
            <details className="border-b border-border pb-3">
              <summary className="min-h-10 cursor-pointer py-2 text-sm font-semibold text-foreground underline decoration-border underline-offset-4"><L10n>{"Kết thúc lượt không kết luận"}</L10n></summary>
              <p className="pb-2 text-sm text-muted-foreground"><L10n>{"Dùng khi bệnh nhân tự ý về hoặc từ chối khám. Không cần chẩn đoán, nhưng mọi chỉ định xét nghiệm phải có kết quả hoặc đã hủy."}</L10n></p>
              <CloseRecordForm record={record} disabled={actionsBlocked} isInteractionBlocked={isInteractionBlocked} onActivityChange={setCompletionActive} onBusyChange={setCompletionBusy} onClosed={applyRecord} />
            </details>
          ) : null}</L10nValue>
          <L10nValue>{record.status === "COMPLETED" ? <FollowUpRecommendationPanel
            recordId={record.recordId}
            enabled={schedulingV2Enabled}
            canCreate={role === "DOCTOR"}
          /> : null}</L10nValue>
        </TabPanel>
      ) : null}</L10nValue>

      <TabPanel {...panel} id="info" className="text-sm focus-visible:outline-none">
        <h2 className="text-lg font-semibold"><L10n>{"Thông tin đối chiếu và thời điểm"}</L10n></h2>
        <dl className="mt-3 grid gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-3">
          <Identifier label={localize("Mã hồ sơ")} kind="record" value={record.recordId} />
          <Identifier label={localize("Bệnh nhân")} kind="patient" value={record.patientId} />
          <Identifier label={localize("Bác sĩ mở hồ sơ")} kind="doctor" value={record.doctorId} />
          <Identifier label="Khoa" kind="department" value={record.departmentId} />
          <Identifier label={localize("Lịch hẹn")} value={record.appointmentId} />
          <div><dt className="text-xs font-medium text-muted-foreground"><L10n>{"Ngày khám"}</L10n></dt><dd className="mt-1"><L10nValue>{formatLocalDate(record.examinationDate)}</L10nValue></dd></div>
          <div><dt className="text-xs font-medium text-muted-foreground"><L10n>{"Ngày tạo"}</L10n></dt><dd className="mt-1"><L10nValue>{formatInstant(record.createdAt)}</L10nValue></dd></div>
          <div><dt className="text-xs font-medium text-muted-foreground"><L10n>{"Cập nhật"}</L10n></dt><dd className="mt-1"><L10nValue>{formatInstant(record.updatedAt)}</L10nValue></dd></div>
          <div><dt className="text-xs font-medium text-muted-foreground"><L10n>{"Hoàn tất"}</L10n></dt><dd className="mt-1"><L10nValue>{formatInstant(record.completedAt)}</L10nValue></dd></div>
        </dl>
      </TabPanel>

      <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
        <Link href="/records" className="inline-flex min-h-11 items-center px-2 text-sm font-medium text-muted-foreground underline underline-offset-4 hover:text-foreground"><L10n>{"Quay lại tra cứu"}</L10n></Link>
        <Button type="button" variant="secondary" onClick={retry} disabled={refreshing || pendingRecord !== null || writeInFlight}><L10n>{"Cập nhật hồ sơ"}</L10n></Button>
      </footer>
    </section>
  );
}
