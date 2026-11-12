"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { AsyncState } from "@/components/ui/AsyncState";
import { PharmacySessionBoundary, usePharmacyRequestScope } from "../PharmacySessionBoundary";
import { PharmacyIdentity } from "../PharmacyIdentity";
import { ReferenceText } from "@/components/references/ReferenceText";

import Link from "next/link";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";
import { useRouter } from "next/navigation";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Button } from "@/components/ui/Button";
import { ApiRequestError } from "@/lib/api";
import { getRole, subscribeToAuthChanges } from "@/lib/auth";
import type { Role } from "@/lib/roles";
import { pharmacyApi } from "../../api";
import {
  canCancelPrescription,
  canDispensePrescription,
  canEditPrescription,
  getPharmacyCapabilities,
} from "../../permissions";
import {
  dispenseStatusPresentation,
  prescriptionStatusPresentation,
} from "../../presentation";
import type { PrescriptionMode } from "../../prescriptionMode";
import type {
  CancelPrescriptionResult,
  DispenseDTO,
  PrescriptionDTO,
} from "../../types";
import { formatDate, formatDateTime, formatVnd, isUuid } from "../../utils";
import { CancelPrescriptionDialog } from "./CancelPrescriptionDialog";
import { DispensePrescriptionDialog } from "./DispensePrescriptionDialog";
import { EditPrescriptionForm } from "./EditPrescriptionForm";

interface PrescriptionDetailProps {
  prescriptionId: string;
  mode: PrescriptionMode | null;
  /** "record" when opened from a medical record: the clinical view hides all prices. */
  context?: "record";
}

type DetailRequestState =
  | { key: string; status: "idle" }
  | { key: string; status: "success"; prescription: PrescriptionDTO }
  | { key: string; status: "not-found" }
  | { key: string; status: "dispense-not-found" }
  | { key: string; status: "error"; message: string; forbidden?: boolean };

type ActiveAction = "cancel" | "dispense" | "edit" | null;

interface ActionFeedback {
  tone: "success" | "warning" | "danger";
  message: string;
}

function getServerRole(): Role | null {
  return null;
}

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 403) {
      const correlation = cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : "";
      return `Bạn không có quyền xem đơn thuốc này.${correlation}`;
    }
    if (cause.correlationId) return `${cause.message} (Mã tra cứu: ${cause.correlationId})`;
    return cause.message;
  }
  return cause instanceof Error ? cause.message : "Không thể tải thông tin đơn thuốc.";
}

function DetailLoading() {
  const { localize } = useLocale();
  return (
    <section className="mt-6 space-y-4" aria-busy="true" aria-label={localize("Đang tải thông tin đơn thuốc")}>
      <div className="h-28 animate-pulse rounded-xl bg-surface-muted" />
      <div className="h-64 animate-pulse rounded-xl bg-surface-muted" />
    </section>
  );
}

function DetailError({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
      <p><L10nValue>{message}</L10nValue></p>
      <L10nValue>{onRetry && <button type="button" onClick={onRetry} className="mt-3 rounded-lg border border-danger/40 px-3 py-2 font-medium hover:bg-danger/10"><L10n>{"Thử lại"}</L10n></button>}</L10nValue>
    </div>
  );
}

function PrescriptionField({ label, value, identity }: { label: string; value: string | null; identity?: "patient" | "doctor" | "department" }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground"><L10nValue>{label}</L10nValue></dt>
      <dd className="mt-1 flex items-center gap-2">
        <L10nValue>{!value ? "Không có" : identity ? <PharmacyIdentity kind={identity} id={value} /> : <ReferenceText kind="record" value={value} fallback="Hồ sơ khám" />}</L10nValue>
      </dd>
    </div>
  );
}

export function PrescriptionDetail({ prescriptionId, mode, context }: PrescriptionDetailProps) {
  return <PharmacySessionBoundary><PrescriptionDetailContent key={`${prescriptionId}\u0000${mode}`} prescriptionId={prescriptionId} mode={mode} context={context} /></PharmacySessionBoundary>;
}

function PrescriptionDetailContent({ prescriptionId, mode, context }: PrescriptionDetailProps) {
  const { localize } = useLocale();
  const isCurrent = usePharmacyRequestScope("canReadPrescription");
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const capabilities = getPharmacyCapabilities(role);
  // Prices belong to dispensing and billing, never to the clinical record: a doctor, or anyone
  // who opened the prescription from a medical record, sees no unit price, line or total amount.
  const showPricing = role !== "DOCTOR" && context !== "record";
  const [requestState, setRequestState] = useState<DetailRequestState>({ key: "", status: "idle" });
  const [retryToken, setRetryToken] = useState(0);
  const [activeAction, setActiveAction] = useState<ActiveAction>(null);
  const [actionFeedback, setActionFeedback] = useState<ActionFeedback | null>(null);
  const requestSequence = useRef(0);
  const feedbackRef = useRef<HTMLParagraphElement>(null);
  const actionTriggerRef = useRef<HTMLButtonElement | null>(null);
  const returnActionFocus = useRef(false);
  const requestKey = `${prescriptionId}\u0000${retryToken}`;
  const canRead = capabilities.canReadPrescription;
  const loading = canRead && requestState.key !== requestKey;
  const prescription = requestState.status === "success" && requestState.prescription.prescriptionId === prescriptionId
    ? requestState.prescription
    : null;
  const notFound = requestState.key === requestKey && requestState.status === "not-found";
  const dispenseNotFound = requestState.key === requestKey && requestState.status === "dispense-not-found";
  const error = requestState.key === requestKey && requestState.status === "error" ? requestState.message : null;
  const refresh = useCallback(() => {
    setRetryToken((value) => value + 1);
  }, []);

  const closeAction = useCallback(() => {
    returnActionFocus.current = true;
    setActiveAction(null);
  }, []);

  const handleActionConflict = useCallback((message: string) => {
    setActiveAction(null);
    setActionFeedback({ tone: "warning", message });
    setRetryToken((value) => value + 1);
  }, []);

  const handleCancelSuccess = useCallback((result: CancelPrescriptionResult) => {
    setActiveAction(null);
    setActionFeedback({
      tone: result.status === "CANCELLED" ? "success" : "warning",
      message: result.status === "CANCELLED"
        ? `Đã hủy đơn thuốc. Đã giải phóng ${result.releasedReservations} lượt giữ tồn kho.`
        : `Trạng thái đơn trả về: ${result.status}. Đang tải lại chi tiết.`,
    });
    setRetryToken((value) => value + 1);
  }, []);

  const handleDispenseSuccess = useCallback((result: DispenseDTO) => {
    setActiveAction(null);
    if (result.status === "FAILED" || result.failureReason) {
      setActionFeedback({
        tone: "danger",
        message: `Xuất thuốc thất bại${result.failureReason ? `: ${result.failureReason}` : "."}`,
      });
    } else if (result.status === "DISPENSED") {
      setActionFeedback({
        tone: "success",
        message: "Đã xuất thuốc thành công. Đang tải lại chi tiết đơn thuốc.",
      });
    } else {
      setActionFeedback({
        tone: "warning",
        message: `Trạng thái phiếu xuất trả về: ${result.status}. Đang tải lại chi tiết đơn.`,
      });
    }
    setRetryToken((value) => value + 1);
  }, []);

  const handleDispenseFailure = useCallback((message: string) => {
    setActiveAction(null);
    setActionFeedback({ tone: "danger", message });
    setRetryToken((value) => value + 1);
  }, []);

  useEffect(() => {
    if (!canRead || !isCurrent() || !isUuid(prescriptionId)) return undefined;

    let active = true;
    const requestId = ++requestSequence.current;

    pharmacyApi
      .getPrescription(prescriptionId)
      .then((nextPrescription) => {
        if (!active || !isCurrent() || requestId !== requestSequence.current) return;
        setRequestState({ key: requestKey, status: "success", prescription: nextPrescription });
      })
      .catch((cause: unknown) => {
        if (!active || !isCurrent() || requestId !== requestSequence.current) return;

        if (cause instanceof ApiRequestError && cause.status === 401) {
          setRequestState({ key: requestKey, status: "error", message: "Phiên đăng nhập đã hết hạn. Đang chuyển đến trang đăng nhập." });
          router.replace("/login");
          return;
        }

        if (cause instanceof ApiRequestError && cause.code === "DISPENSE_NOT_FOUND") {
          setRequestState({ key: requestKey, status: "dispense-not-found" });
          return;
        }

        if (cause instanceof ApiRequestError && (cause.status === 404 || cause.code === "PRESCRIPTION_NOT_FOUND")) {
          setRequestState({ key: requestKey, status: "not-found" });
          return;
        }

        setRequestState({ key: requestKey, status: "error", message: errorMessage(cause), forbidden: cause instanceof ApiRequestError && cause.status === 403 });
      });

    return () => {
      active = false;
    };
  }, [canRead, isCurrent, prescriptionId, requestKey, router]);

  useEffect(() => {
    if (actionFeedback) feedbackRef.current?.focus();
  }, [actionFeedback]);

  useEffect(() => {
    if (activeAction === null && returnActionFocus.current) {
      returnActionFocus.current = false;
      actionTriggerRef.current?.focus();
    }
  }, [activeAction]);

  const feedback = actionFeedback && (
    <p ref={feedbackRef} tabIndex={-1} role={actionFeedback.tone === "danger" ? "alert" : "status"}
      className={`rounded-lg border p-4 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
        actionFeedback.tone === "success" ? "border-success/40 bg-success/10 text-success"
          : actionFeedback.tone === "warning" ? "border-warning/40 bg-warning/10 text-warning"
            : "border-danger/40 bg-danger/10 text-danger"
      }`}><L10nValue>{actionFeedback.message}</L10nValue></p>
  );

  if (role === null) return <AsyncState kind="loading" message={localize("Đang kiểm tra quyền…")} />;
  if (!canRead) {
    return <p role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"><L10n>{"Tài khoản của bạn không có quyền xem đơn thuốc."}</L10n></p>;
  }
  if (!isUuid(prescriptionId)) return <p role="alert" className="mt-6 text-sm text-danger"><L10n>{"Mã đơn thuốc không hợp lệ. Hãy kiểm tra lại mã trên đơn."}</L10n></p>;
  if (loading && !prescription && !notFound && !dispenseNotFound && !error) return <DetailLoading />;

  if (notFound) {
    return (
      <section className="mt-6 space-y-4 rounded-xl border border-border bg-surface p-6">
        <L10nValue>{feedback}</L10nValue>
        <h2 className="text-lg font-semibold"><L10n>{"Không tìm thấy đơn thuốc"}</L10n></h2>
        <p className="mt-2 text-sm text-muted-foreground"><L10n>{"Không có đơn thuốc nào với mã "}</L10n><code className="break-all"><L10nValue>{prescriptionId}</L10nValue></code>.</p>
        <Link href="/pharmacy/prescriptions" className="mt-4 inline-flex rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground hover:opacity-90"><L10n>{"Quay lại tra cứu"}</L10n></Link>
      </section>
    );
  }

  if (dispenseNotFound) {
    return (
      <section className="mt-6 space-y-4 rounded-xl border border-warning/40 bg-warning/10 p-6">
        <L10nValue>{feedback}</L10nValue>
        <h2 className="text-lg font-semibold"><L10n>{"Chưa có phiếu xuất thuốc"}</L10n></h2>
        <p className="mt-2 text-sm"><L10n>{"Chưa tải được phiếu xuất của đơn này. Vui lòng tải lại sau hoặc liên hệ người phụ trách nhà thuốc."}</L10n></p>
        <Link href="/pharmacy/prescriptions" className="mt-4 inline-flex rounded-lg border border-border px-4 py-2 font-medium hover:bg-surface-muted"><L10n>{"Quay lại tra cứu"}</L10n></Link>
      </section>
    );
  }

  if (error) return <div className="mt-6 space-y-4"><L10nValue>{feedback}</L10nValue><DetailError message={localize(actionFeedback ? `Chưa tải được chi tiết mới. ${error}` : error)} onRetry={requestState.status === "error" && requestState.forbidden ? undefined : refresh} /></div>;
  if (!prescription) return null;

  const status = prescriptionStatusPresentation[prescription.status];
  const dispenseStatus = dispenseStatusPresentation[prescription.dispenseStatus];
  const showCancelSlot = canCancelPrescription(role, prescription);
  const showEditSlot = mode === "outpatient" && canEditPrescription(role, prescription);
  const showDispenseSlot = canDispensePrescription(role, prescription);
  // The runtime selects the contract. The read DTO intentionally has no version/grant fields.
  // V1 authorizes at the command; the legacy receipt boolean cannot block or authorize it.
  const canDispenseNow = showDispenseSlot && (mode === "outpatient"
    || (mode === "legacy" && prescription.paymentConfirmed));

  return (
    <section className="mt-6 min-w-0 space-y-6 [overflow-wrap:anywhere]">
      <L10nValue>{loading && <p role="status" className="rounded-lg border border-info/40 bg-info/10 p-3 text-sm text-info"><L10n>{"Đang tải trạng thái đơn thuốc mới nhất…"}</L10n></p>}</L10nValue>

      <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="mt-1 text-xl font-semibold"><L10n>{"Chi tiết đơn thuốc"}</L10n></h2>
          <p className="mt-1 text-sm text-muted-foreground"><L10n>{"Ngày kê: "}</L10n><L10nValue>{formatDate(prescription.prescribedDate)}</L10nValue></p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge tone={status.tone}><L10nValue>{status.label}</L10nValue></StatusBadge>
          <StatusBadge tone={dispenseStatus.tone}><L10nValue>{dispenseStatus.label}</L10nValue></StatusBadge>
          <L10nValue>{mode === "legacy" ? (
            <StatusBadge tone={prescription.paymentConfirmed ? "success" : "warning"}>
              <L10nValue>{prescription.paymentConfirmed ? "Đã xác nhận thanh toán" : "Chờ thanh toán"}</L10nValue>
            </StatusBadge>
          ) : (
            <StatusBadge tone="info">
              <L10nValue>{mode === "outpatient" ? "Thanh toán được kiểm tra khi xuất thuốc" : "Chế độ xuất thuốc chưa sẵn sàng"}</L10nValue>
            </StatusBadge>
          )}</L10nValue>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Button type="button" disabled={loading || activeAction !== null} onClick={refresh}><L10n>{"Tải lại trạng thái"}</L10n></Button>
        <p className="text-sm text-muted-foreground"><L10n>{"Thanh toán và kết quả xuất thuốc được cập nhật từ hệ thống."}</L10n></p>
      </div>

      <L10nValue>{feedback}</L10nValue>

      <L10nValue>{(showCancelSlot || showDispenseSlot || showEditSlot) && (
        <section className="rounded-xl border border-border bg-surface p-5">
          <h3 className="font-semibold"><L10n>{"Thao tác đơn thuốc"}</L10n></h3>
          <p className="mt-1 text-sm text-muted-foreground">
            <L10nValue>{mode === "outpatient"
              ? "Hủy đơn sẽ giải phóng số lượng đang giữ. Khi xác nhận xuất thuốc, hệ thống kiểm tra lại quyền, thanh toán và tồn kho."
              : "Hủy đơn sẽ giải phóng số lượng đang giữ. Chỉ xuất thuốc khi thanh toán đã được xác nhận."}</L10nValue>
          </p>
          <L10nValue>{showDispenseSlot && mode === "legacy" && !prescription.paymentConfirmed && (
            <p className="mt-3 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm text-warning"><L10n>{"Đơn đang chờ xác nhận thanh toán, nên chưa thể xuất thuốc."}</L10n></p>
          )}</L10nValue>
          <div className="mt-4 flex flex-wrap gap-3">
            {showEditSlot && <Button type="button" disabled={loading || activeAction !== null}
              onClick={event => { actionTriggerRef.current = event.currentTarget; setActiveAction("edit"); }}>
              <L10n>Sửa đơn thuốc</L10n>
            </Button>}
            <L10nValue>{showCancelSlot && (
              <Button
                type="button"
                onClick={(event) => {
                  actionTriggerRef.current = event.currentTarget;
                  setActiveAction("cancel");
                }}
                disabled={loading || activeAction !== null}
                className="border-danger/40 text-danger hover:bg-danger/10"
              ><L10n>{"Hủy đơn"}</L10n></Button>
            )}</L10nValue>
            <L10nValue>{canDispenseNow && (
              <Button
                type="button"
                onClick={(event) => {
                  actionTriggerRef.current = event.currentTarget;
                  setActiveAction("dispense");
                }}
                disabled={loading || activeAction !== null}
                variant="primary"
              ><L10n>{"Xuất thuốc"}</L10n></Button>
            )}</L10nValue>
          </div>

          {activeAction === "edit" && showEditSlot && !loading && <EditPrescriptionForm
            key={`${prescription.prescriptionId}:${prescription.revision}`}
            prescription={prescription} onClose={closeAction} onConflict={handleActionConflict}
            onSaved={updated => {
              setActiveAction(null);
              setRequestState({ key: requestKey, status: "success", prescription: updated });
              setActionFeedback({ tone: "success", message: "Đã cập nhật đơn thuốc. Khoản phải thu đang được đồng bộ." });
            }} />}

          <L10nValue>{activeAction === "cancel" && showCancelSlot && !loading && (
            <CancelPrescriptionDialog
              prescriptionId={prescription.prescriptionId}
              onClose={closeAction}
              onSuccess={handleCancelSuccess}
              onConflict={handleActionConflict}
            />
          )}</L10nValue>

          <L10nValue>{activeAction === "dispense" && canDispenseNow && !loading && (
            <DispensePrescriptionDialog
              prescriptionId={prescription.prescriptionId}
              totalAmount={prescription.totalAmount}
              lineCount={prescription.lines.length}
              onClose={closeAction}
              onSuccess={handleDispenseSuccess}
              onConflict={handleActionConflict}
              onFailure={handleDispenseFailure}
            />
          )}</L10nValue>
        </section>
      )}</L10nValue>

      <section className="rounded-xl border border-border bg-surface p-6">
        <h3 className="text-base font-semibold"><L10n>{"Thông tin kê đơn"}</L10n></h3>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <PrescriptionField label={localize("Hồ sơ khám")} value={prescription.recordId} />
          <PrescriptionField label={localize("Bệnh nhân")} value={prescription.patientId} identity="patient" />
          <PrescriptionField label={localize("Bác sĩ kê đơn")} value={prescription.doctorId} identity="doctor" />
          <PrescriptionField label="Khoa" value={prescription.departmentId} identity="department" />
          <L10nValue>{showPricing ? <div><dt className="text-xs text-muted-foreground"><L10n>{"Tổng tiền"}</L10n></dt><dd className="mt-1 font-semibold"><L10nValue>{formatVnd(prescription.totalAmount)}</L10nValue></dd></div> : null}</L10nValue>
          <div><dt className="text-xs text-muted-foreground"><L10n>{"Ngày kê đơn"}</L10n></dt><dd className="mt-1"><L10nValue>{formatDate(prescription.prescribedDate)}</L10nValue></dd></div>
        </dl>
        <L10nValue>{(role === "ADMIN" || role === "DOCTOR") && isUuid(prescription.recordId ?? "") ? <Link className="mt-4 inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-medium text-primary underline-offset-4 hover:bg-surface-muted hover:underline" href={`/records/${prescription.recordId}`}><L10n>{"Mở hồ sơ khám nguồn"}</L10n></Link> : null}</L10nValue>
      </section>

      <section className="rounded-xl border border-border bg-surface p-6">
        <h3 className="text-base font-semibold"><L10n>{"Các dòng thuốc"}</L10n></h3>
        <div className="mt-4 overflow-x-auto">
          <table className={`w-full text-left text-sm ${showPricing ? "min-w-[720px]" : "min-w-[560px]"}`}>
            <thead className="border-b border-border text-xs text-muted-foreground">
              <tr><th className="px-3 py-3"><L10n>{"Thuốc"}</L10n></th><th className="px-3 py-3"><L10n>{"Số lượng"}</L10n></th><L10nValue>{showPricing ? <th className="px-3 py-3"><L10n>{"Đơn giá"}</L10n></th> : null}</L10nValue><th className="px-3 py-3"><L10n>{"Cách dùng"}</L10n></th><th className="px-3 py-3"><L10n>{"Ghi chú"}</L10n></th><L10nValue>{showPricing ? <th className="px-3 py-3 text-right"><L10n>{"Thành tiền"}</L10n></th> : null}</L10nValue></tr>
            </thead>
            <tbody className="divide-y divide-border">
              <L10nValue>{prescription.lines.map((line) => (
                <tr key={line.lineId}>
                  <td className="px-3 py-3"><div><L10nValue>{line.drugName ?? "Chưa có thông tin tên thuốc"}</L10nValue></div></td>
                  <td className="px-3 py-3">{line.quantity}</td>
                  <L10nValue>{showPricing ? <td className="px-3 py-3"><L10nValue>{formatVnd(line.unitPrice)}</L10nValue></td> : null}</L10nValue>
                  <td className="px-3 py-3"><L10nValue>{line.dosage ?? "—"}</L10nValue></td>
                  <td className="px-3 py-3"><L10nValue>{line.note ?? "—"}</L10nValue></td>
                  <L10nValue>{showPricing ? <td className="px-3 py-3 text-right font-medium"><L10nValue>{formatVnd(line.lineTotal)}</L10nValue></td> : null}</L10nValue>
                </tr>
              ))}</L10nValue>
            </tbody>
          </table>
        </div>
      </section>

      <L10nValue>{(prescription.cancelledAt || prescription.cancelledBy || prescription.cancellationReason) && (
        <section className="rounded-xl border border-border bg-surface p-6">
          <h3 className="text-base font-semibold"><L10n>{"Thông tin hủy đơn"}</L10n></h3>
          <dl className="mt-4 grid gap-4 sm:grid-cols-3">
            <div><dt className="text-xs text-muted-foreground"><L10n>{"Thời điểm"}</L10n></dt><dd className="mt-1"><L10nValue>{formatDateTime(prescription.cancelledAt)}</L10nValue></dd></div>
            <div><dt className="text-xs text-muted-foreground"><L10n>{"Người hủy"}</L10n></dt><dd className="mt-1"><ReferenceText value={prescription.cancelledBy} fallback="Đã ghi nhận tài khoản thực hiện" /></dd></div>
            <div><dt className="text-xs text-muted-foreground"><L10n>{"Lý do"}</L10n></dt><dd className="mt-1"><L10nValue>{prescription.cancellationReason ?? "—"}</L10nValue></dd></div>
          </dl>
        </section>
      )}</L10nValue>

      <section className="rounded-xl border border-border bg-surface p-6">
        <h3 className="text-base font-semibold"><L10n>{"Lịch sử bản ghi"}</L10n></h3>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <div><dt className="text-xs text-muted-foreground"><L10n>{"Tạo lúc"}</L10n></dt><dd className="mt-1"><L10nValue>{formatDateTime(prescription.createdAt)}</L10nValue></dd></div>
          <div><dt className="text-xs text-muted-foreground"><L10n>{"Cập nhật lúc"}</L10n></dt><dd className="mt-1"><L10nValue>{formatDateTime(prescription.updatedAt)}</L10nValue></dd></div>
        </dl>
        <details className="mt-4 text-sm text-muted-foreground">
          <summary className="cursor-pointer"><L10n>{"Mã tham chiếu khi cần hỗ trợ"}</L10n></summary>
          <dl className="mt-3 grid gap-3 text-xs sm:grid-cols-2">
            <L10nValue>{[["Đơn thuốc", prescription.prescriptionId], ["Hồ sơ khám", prescription.recordId], ["Bệnh nhân", prescription.patientId], ["Bác sĩ", prescription.doctorId], ["Khoa", prescription.departmentId], ["Tài khoản hủy", prescription.cancelledBy]].filter(([, value]) => value).map(([label, value]) => <div key={label}><dt><L10nValue>{label}</L10nValue></dt><dd className="mt-1 break-all font-mono">{value}</dd></div>)}</L10nValue>
          </dl>
        </details>
      </section>
    </section>
  );
}
