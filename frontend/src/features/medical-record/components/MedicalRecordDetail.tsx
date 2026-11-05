"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import { Button } from "@/components/ui/Button";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { getRole, getSessionKey, subscribeToAuthChanges } from "@/lib/auth";
import { formatInstant, formatLocalDate } from "@/lib/format";
import type { Role } from "@/lib/roles";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";
import { medicalRecordApi } from "../api";
import type { DiagnosisDTO, MedicalRecordDTO, RecordDisposition } from "../types";
import { AddDiagnosisForm } from "./AddDiagnosisForm";
import { CompleteRecordForm } from "./CompleteRecordForm";
import { FollowUpRecommendationPanel } from "./FollowUpRecommendationPanel";

type PrescriptionWorkspaceSlot = ReactNode | ((record: MedicalRecordDTO, canCreate: boolean) => ReactNode);

interface MedicalRecordDetailProps {
  recordId: string;
  notice?: "created" | "updated";
  outpatientEnabled?: boolean;
  schedulingV2Enabled?: boolean;
  prescriptionWorkspace?: PrescriptionWorkspaceSlot;
}

interface RequestError {
  message: string;
  correlationId: string | null;
}

type DetailState =
  | { status: "idle" }
  | { status: "success"; record: MedicalRecordDTO }
  | { status: "not-found" }
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
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
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

function MedicalRecordDetailContent({ recordId, notice, outpatientEnabled = false, schedulingV2Enabled = false, prescriptionWorkspace, role }: MedicalRecordDetailProps & { role: Role | null }) {
  const router = useRouter();
  const [state, setState] = useState<DetailState>({ status: "idle" });
  const [retryToken, setRetryToken] = useState(0);
  const [refreshing, setRefreshing] = useState(true);
  const [writeInFlight, setWriteInFlight] = useState(false);
  const [refreshError, setRefreshError] = useState<RequestError | null>(null);
  const [pendingRecord, setPendingRecord] = useState<MedicalRecordDTO | null>(null);
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
    medicalRecordApi.getById(recordId)
      .then((updated) => {
        if (!active) return;
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
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 401) {
          refreshPendingRef.current = false;
          setRefreshing(false);
          router.replace("/login");
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
          <h2 className="text-lg font-semibold">Không tìm thấy hồ sơ khám</h2>
          <p className="mt-2 text-sm text-muted-foreground">Hồ sơ có thể đã bị xóa hoặc mã truy cập không còn hợp lệ.</p>
          <div className="mt-4 flex flex-wrap gap-3">
            <Button type="button" variant="secondary" onClick={retry}>Thử tải lại</Button>
            <Link href="/records" className="inline-flex min-h-11 items-center px-3 text-sm font-medium text-primary underline underline-offset-4">Quay lại tra cứu</Link>
          </div>
        </section>
      );
    }
    if (state.status === "error") {
      return (
        <section role="alert" className="mt-4 flex flex-wrap items-center justify-between gap-3 border-l-2 border-danger px-4 py-3 text-sm text-danger">
          <div>
            <p>{state.error.message}</p>
            {state.error.correlationId ? <details className="mt-2 text-xs"><summary className="min-h-8 cursor-pointer underline underline-offset-4">Thông tin hỗ trợ</summary><p className="mt-1 break-all font-mono">Mã theo dõi: {state.error.correlationId}</p></details> : null}
          </div>
          <Button type="button" variant="secondary" onClick={retry}>Thử tải lại</Button>
        </section>
      );
    }
    return <AsyncState kind="loading" message="Đang tải chi tiết hồ sơ khám…" />;
  }

  const canMutate = role === "ADMIN" || role === "DOCTOR";
  const isOpen = record.status === "OPEN";
  const actionsBlocked = refreshing || pendingRecord !== null || writeInFlight;
  const actionClass = "inline-flex min-h-11 items-center rounded-lg px-3 py-2 text-sm font-medium underline decoration-border underline-offset-4 hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary";

  return (
    <section className="mt-6 space-y-7">
      {notice ? (
        <p role="status" className="border-l-2 border-success px-4 py-2 text-sm text-success">
          {notice === "created" ? "Đã tạo hồ sơ khám thành công." : "Đã cập nhật hồ sơ khám thành công."}
        </p>
      ) : null}

      <header aria-label="Ngữ cảnh hồ sơ khám" className="border-b border-border pb-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Bệnh nhân</p>
            <h2 className="mt-1 text-xl font-semibold tracking-tight sm:text-2xl"><ReferenceText kind="patient" value={record.patientId} fallback="Bệnh nhân" /></h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">{formatLocalDate(record.examinationDate)} <span aria-hidden="true">·</span> <ReferenceText kind="department" value={record.departmentId} fallback="Khoa" /> <span aria-hidden="true">·</span> <ReferenceText kind="doctor" value={record.doctorId} fallback="Bác sĩ" /></p>
          </div>
          <StatusBadge tone={isOpen ? "info" : "success"}>{isOpen ? "Đang khám" : "Đã hoàn tất"}</StatusBadge>
        </div>

        {outpatientEnabled && record.appointmentId ? (
          <section aria-label="Xét nghiệm của lượt khám" className="mt-5 border-t border-border pt-4">
            <h3 className="text-sm font-semibold">Xét nghiệm của lượt khám</h3>
            <p className="mt-1 text-sm text-muted-foreground">Chỉ định và theo dõi xét nghiệm theo đúng lượt khám này.</p>
            <div className="mt-2 flex flex-wrap items-center gap-1">
              {canMutate && isOpen ? (
                <Link aria-disabled={actionsBlocked || undefined} tabIndex={actionsBlocked ? -1 : undefined} onClick={(event) => { if (actionsBlocked) event.preventDefault(); }} className={`inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${actionsBlocked ? "pointer-events-none opacity-55" : ""}`} href={`/records/${record.recordId}/lab-order`}>Lập chỉ định xét nghiệm</Link>
              ) : null}
              <Link aria-disabled={actionsBlocked || undefined} tabIndex={actionsBlocked ? -1 : undefined} onClick={(event) => { if (actionsBlocked) event.preventDefault(); }} className={`${actionClass} ${actionsBlocked ? "pointer-events-none opacity-55" : "text-primary"}`} href={`/lab?episodeId=${encodeURIComponent(record.appointmentId)}`}>Xem phiếu xét nghiệm</Link>
            </div>
          </section>
        ) : null}
      </header>

      {refreshing ? <p role="status" className="text-sm text-muted-foreground">Đang cập nhật hồ sơ…</p> : null}
      {refreshError ? <div role="alert" className="border-l-2 border-danger px-4 py-2 text-sm text-danger"><p>{refreshError.message}</p>{refreshError.correlationId ? <details className="mt-1 text-xs"><summary className="min-h-8 cursor-pointer underline underline-offset-4">Thông tin hỗ trợ</summary><p className="mt-1 break-all font-mono">Mã theo dõi: {refreshError.correlationId}</p></details> : null}</div> : null}
      {pendingRecord ? (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-3 border-l-2 border-warning px-4 py-3">
          <p className="text-sm text-foreground">Hồ sơ đã thay đổi trạng thái khi bạn đang nhập. Bản nháp được giữ lại; hãy xem dữ liệu mới trước khi tiếp tục.</p>
          <Button type="button" variant="secondary" onClick={acceptPendingRecord}>Xem dữ liệu mới</Button>
        </div>
      ) : null}

      <div className="grid gap-7 lg:grid-cols-[minmax(0,1fr)_minmax(18rem,0.8fr)]">
        <section aria-labelledby="exam-content-heading">
          <h2 id="exam-content-heading" className="text-lg font-semibold">Nội dung khám</h2>
          <dl className="mt-4 grid gap-5 sm:grid-cols-2">
            <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Triệu chứng</dt><dd className="mt-1 whitespace-pre-wrap text-sm leading-6">{record.symptoms ?? "—"}</dd></div>
            <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Hướng xử trí</dt><dd className="mt-1 text-sm leading-6">{record.disposition ? dispositionLabels[record.disposition] : "—"}</dd>{record.dispositionNote ? <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{record.dispositionNote}</p> : null}</div>
          </dl>
        </section>

        <section aria-labelledby="diagnoses-heading" className="border-t border-border pt-5 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 id="diagnoses-heading" className="text-lg font-semibold">Chẩn đoán</h2>
            <span className="text-sm text-muted-foreground">{record.diagnoses.length} chẩn đoán</span>
          </div>
          {record.diagnoses.length === 0 ? <p className="mt-3 text-sm text-muted-foreground">Chưa có chẩn đoán.</p> : (
            <ul className="mt-3 divide-y divide-border">
              {record.diagnoses.map((diagnosis) => (
                <li key={diagnosis.diagnosisId} className="py-3 first:pt-0 last:pb-0">
                  <div className="flex flex-wrap items-center gap-2"><p className="font-medium">{diagnosis.diagnosisName}</p>{diagnosis.icdCode ? <StatusBadge tone="neutral">ICD {diagnosis.icdCode}</StatusBadge> : null}</div>
                  {diagnosis.description ? <p className="mt-1 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{diagnosis.description}</p> : null}
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      {canMutate && isOpen ? (
        <details className="border-y border-border py-3">
          <summary className="min-h-10 cursor-pointer py-2 text-sm font-semibold text-primary underline decoration-border underline-offset-4">Thêm chẩn đoán</summary>
          <AddDiagnosisForm recordId={record.recordId} disabled={actionsBlocked} isInteractionBlocked={isInteractionBlocked} onActivityChange={setDiagnosisActive} onBusyChange={setDiagnosisBusy} onCreated={addCreatedDiagnosis} />
        </details>
      ) : null}

      {(role === "ADMIN" || role === "DOCTOR") && prescriptionWorkspace ? (
        <section className="border-b border-border pb-5" aria-labelledby="record-prescription-heading">
          <h2 id="record-prescription-heading" className="text-lg font-semibold">Đơn thuốc của hồ sơ khám</h2>
          {typeof prescriptionWorkspace === "function"
            ? prescriptionWorkspace(record, outpatientEnabled && role === "DOCTOR" && isOpen && Boolean(record.appointmentId) && !actionsBlocked)
            : prescriptionWorkspace}
        </section>
      ) : null}

      {outpatientEnabled && canMutate && isOpen && record.appointmentId ? (
        <details className="border-b border-border pb-3">
          <summary className="min-h-10 cursor-pointer py-2 text-sm font-semibold text-foreground underline decoration-border underline-offset-4">Hoàn tất lần khám</summary>
          <p className="pb-2 text-sm text-muted-foreground">Mở khi đã hoàn tất chẩn đoán và hướng xử trí. Hồ sơ sẽ không thể chỉnh sửa sau khi kết thúc.</p>
          <CompleteRecordForm record={record} disabled={actionsBlocked} isInteractionBlocked={isInteractionBlocked} onActivityChange={setCompletionActive} onBusyChange={setCompletionBusy} onCompleted={applyRecord} />
        </details>
      ) : null}

      {canMutate && isOpen ? (
        <details className="border-t border-border pt-3">
          <summary className="min-h-10 cursor-pointer py-2 text-sm font-medium text-muted-foreground">Tác vụ khác</summary>
          <div className="flex flex-wrap gap-1 pt-1">
            {canMutate && isOpen ? <Link aria-disabled={actionsBlocked || undefined} tabIndex={actionsBlocked ? -1 : undefined} onClick={(event) => { if (actionsBlocked) event.preventDefault(); }} className={`${actionClass} ${actionsBlocked ? "pointer-events-none opacity-55" : "text-foreground"}`} href={`/records/${record.recordId}/edit`}>Sửa triệu chứng</Link> : null}
          </div>
        </details>
      ) : null}

      {record.status === "COMPLETED" ? <FollowUpRecommendationPanel
        recordId={record.recordId}
        enabled={schedulingV2Enabled}
        canCreate={role === "DOCTOR"}
      /> : null}

      <details className="border-t border-border pt-4 text-sm">
        <summary className="min-h-10 cursor-pointer py-2 font-medium text-muted-foreground">Thông tin đối chiếu và thời điểm</summary>
        <dl className="mt-3 grid gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-3">
          <Identifier label="Mã hồ sơ" kind="record" value={record.recordId} />
          <Identifier label="Bệnh nhân" kind="patient" value={record.patientId} />
          <Identifier label="Bác sĩ" kind="doctor" value={record.doctorId} />
          <Identifier label="Khoa" kind="department" value={record.departmentId} />
          <Identifier label="Lịch hẹn" value={record.appointmentId} />
          <div><dt className="text-xs font-medium text-muted-foreground">Ngày khám</dt><dd className="mt-1">{formatLocalDate(record.examinationDate)}</dd></div>
          <div><dt className="text-xs font-medium text-muted-foreground">Ngày tạo</dt><dd className="mt-1">{formatInstant(record.createdAt)}</dd></div>
          <div><dt className="text-xs font-medium text-muted-foreground">Cập nhật</dt><dd className="mt-1">{formatInstant(record.updatedAt)}</dd></div>
          <div><dt className="text-xs font-medium text-muted-foreground">Hoàn tất</dt><dd className="mt-1">{formatInstant(record.completedAt)}</dd></div>
        </dl>
      </details>

      <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
        <Link href="/records" className="inline-flex min-h-11 items-center px-2 text-sm font-medium text-muted-foreground underline underline-offset-4 hover:text-foreground">Quay lại tra cứu</Link>
        <Button type="button" variant="secondary" onClick={retry} disabled={refreshing || pendingRecord !== null || writeInFlight}>Cập nhật hồ sơ</Button>
      </footer>
    </section>
  );
}
