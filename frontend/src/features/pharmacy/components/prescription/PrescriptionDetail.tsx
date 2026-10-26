"use client";

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

interface PrescriptionDetailProps {
  prescriptionId: string;
  mode: PrescriptionMode | null;
}

type DetailRequestState =
  | { key: string; status: "idle" }
  | { key: string; status: "success"; prescription: PrescriptionDTO }
  | { key: string; status: "not-found" }
  | { key: string; status: "dispense-not-found" }
  | { key: string; status: "error"; message: string; forbidden?: boolean };

type ActiveAction = "cancel" | "dispense" | null;

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
  return (
    <section className="mt-6 space-y-4" aria-busy="true" aria-label="Đang tải thông tin đơn thuốc">
      <div className="h-28 animate-pulse rounded-xl bg-surface-muted" />
      <div className="h-64 animate-pulse rounded-xl bg-surface-muted" />
    </section>
  );
}

function DetailError({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
      <p>{message}</p>
      {onRetry && <button type="button" onClick={onRetry} className="mt-3 rounded-lg border border-danger/40 px-3 py-2 font-medium hover:bg-danger/10">
        Thử lại
      </button>}
    </div>
  );
}

function PrescriptionField({ label, value, identity }: { label: string; value: string | null; identity?: "patient" | "doctor" | "department" }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 flex items-center gap-2">
        {!value ? "Không có" : identity ? <PharmacyIdentity kind={identity} id={value} /> : <ReferenceText kind="record" value={value} fallback="Hồ sơ khám" />}
      </dd>
    </div>
  );
}

export function PrescriptionDetail({ prescriptionId, mode }: PrescriptionDetailProps) {
  return <PharmacySessionBoundary><PrescriptionDetailContent key={`${prescriptionId}\u0000${mode}`} prescriptionId={prescriptionId} mode={mode} /></PharmacySessionBoundary>;
}

function PrescriptionDetailContent({ prescriptionId, mode }: PrescriptionDetailProps) {
  const isCurrent = usePharmacyRequestScope("canReadPrescription");
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToAuthChanges, getRole, getServerRole);
  const capabilities = getPharmacyCapabilities(role);
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
      }`}>{actionFeedback.message}</p>
  );

  if (role === null) return <p className="mt-6 text-sm text-muted-foreground">Đang kiểm tra quyền…</p>;
  if (!canRead) {
    return <p role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">Tài khoản của bạn không có quyền xem đơn thuốc.</p>;
  }
  if (!isUuid(prescriptionId)) return <p role="alert" className="mt-6 text-sm text-danger">Mã đơn thuốc không hợp lệ. Hãy kiểm tra lại mã trên đơn.</p>;
  if (loading && !prescription && !notFound && !dispenseNotFound && !error) return <DetailLoading />;

  if (notFound) {
    return (
      <section className="mt-6 space-y-4 rounded-xl border border-border bg-surface p-6">
        {feedback}
        <h2 className="text-lg font-semibold">Không tìm thấy đơn thuốc</h2>
        <p className="mt-2 text-sm text-muted-foreground">Không có đơn thuốc nào với mã <code className="break-all">{prescriptionId}</code>.</p>
        <Link href="/pharmacy/prescriptions" className="mt-4 inline-flex rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground hover:opacity-90">Quay lại tra cứu</Link>
      </section>
    );
  }

  if (dispenseNotFound) {
    return (
      <section className="mt-6 space-y-4 rounded-xl border border-warning/40 bg-warning/10 p-6">
        {feedback}
        <h2 className="text-lg font-semibold">Chưa có phiếu xuất thuốc</h2>
        <p className="mt-2 text-sm">Chưa tải được phiếu xuất của đơn này. Vui lòng tải lại sau hoặc liên hệ người phụ trách nhà thuốc.</p>
        <Link href="/pharmacy/prescriptions" className="mt-4 inline-flex rounded-lg border border-border px-4 py-2 font-medium hover:bg-surface-muted">Quay lại tra cứu</Link>
      </section>
    );
  }

  if (error) return <div className="mt-6 space-y-4">{feedback}<DetailError message={actionFeedback ? `Chưa tải được chi tiết mới. ${error}` : error} onRetry={requestState.status === "error" && requestState.forbidden ? undefined : refresh} /></div>;
  if (!prescription) return null;

  const status = prescriptionStatusPresentation[prescription.status];
  const dispenseStatus = dispenseStatusPresentation[prescription.dispenseStatus];
  const showCancelSlot = canCancelPrescription(role, prescription);
  const showDispenseSlot = canDispensePrescription(role, prescription);
  // The runtime selects the contract. The read DTO intentionally has no version/grant fields.
  // V1 authorizes at the command; the legacy receipt boolean cannot block or authorize it.
  const canDispenseNow = showDispenseSlot && (mode === "outpatient"
    || (mode === "legacy" && prescription.paymentConfirmed));

  return (
    <section className="mt-6 min-w-0 space-y-6 [overflow-wrap:anywhere]">
      {loading && <p role="status" className="rounded-lg border border-info/40 bg-info/10 p-3 text-sm text-info">Đang tải trạng thái đơn thuốc mới nhất…</p>}

      <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="mt-1 text-xl font-semibold">Chi tiết đơn thuốc</h2>
          <p className="mt-1 text-sm text-muted-foreground">Ngày kê: {formatDate(prescription.prescribedDate)}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          <StatusBadge tone={dispenseStatus.tone}>{dispenseStatus.label}</StatusBadge>
          {mode === "legacy" ? (
            <StatusBadge tone={prescription.paymentConfirmed ? "success" : "warning"}>
              {prescription.paymentConfirmed ? "Đã xác nhận thanh toán" : "Chờ thanh toán"}
            </StatusBadge>
          ) : (
            <StatusBadge tone="info">
              {mode === "outpatient" ? "Thanh toán được kiểm tra khi xuất thuốc" : "Chế độ xuất thuốc chưa sẵn sàng"}
            </StatusBadge>
          )}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Button type="button" disabled={loading || activeAction !== null} onClick={refresh}>Tải lại trạng thái</Button>
        <p className="text-sm text-muted-foreground">Thanh toán và kết quả xuất thuốc được cập nhật từ hệ thống.</p>
      </div>

      {feedback}

      {(showCancelSlot || showDispenseSlot) && (
        <section className="rounded-xl border border-border bg-surface p-5">
          <h3 className="font-semibold">Thao tác đơn thuốc</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {mode === "outpatient"
              ? "Hủy đơn sẽ giải phóng số lượng đang giữ. Khi xác nhận xuất thuốc, hệ thống kiểm tra lại quyền, thanh toán và tồn kho."
              : "Hủy đơn sẽ giải phóng số lượng đang giữ. Chỉ xuất thuốc khi thanh toán đã được xác nhận."}
          </p>
          {showDispenseSlot && mode === "legacy" && !prescription.paymentConfirmed && (
            <p className="mt-3 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm text-warning">
              Đơn đang chờ xác nhận thanh toán, nên chưa thể xuất thuốc.
            </p>
          )}
          <div className="mt-4 flex flex-wrap gap-3">
            {showCancelSlot && (
              <Button
                type="button"
                onClick={(event) => {
                  actionTriggerRef.current = event.currentTarget;
                  setActiveAction("cancel");
                }}
                disabled={loading || activeAction !== null}
                className="border-danger/40 text-danger hover:bg-danger/10"
              >
                Hủy đơn
              </Button>
            )}
            {canDispenseNow && (
              <Button
                type="button"
                onClick={(event) => {
                  actionTriggerRef.current = event.currentTarget;
                  setActiveAction("dispense");
                }}
                disabled={loading || activeAction !== null}
                variant="primary"
              >
                Xuất thuốc
              </Button>
            )}
          </div>

          {activeAction === "cancel" && showCancelSlot && !loading && (
            <CancelPrescriptionDialog
              prescriptionId={prescription.prescriptionId}
              onClose={closeAction}
              onSuccess={handleCancelSuccess}
              onConflict={handleActionConflict}
            />
          )}

          {activeAction === "dispense" && canDispenseNow && !loading && (
            <DispensePrescriptionDialog
              prescriptionId={prescription.prescriptionId}
              totalAmount={prescription.totalAmount}
              lineCount={prescription.lines.length}
              onClose={closeAction}
              onSuccess={handleDispenseSuccess}
              onConflict={handleActionConflict}
              onFailure={handleDispenseFailure}
            />
          )}
        </section>
      )}

      <section className="rounded-xl border border-border bg-surface p-6">
        <h3 className="text-base font-semibold">Thông tin kê đơn</h3>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <PrescriptionField label="Hồ sơ khám" value={prescription.recordId} />
          <PrescriptionField label="Bệnh nhân" value={prescription.patientId} identity="patient" />
          <PrescriptionField label="Bác sĩ kê đơn" value={prescription.doctorId} identity="doctor" />
          <PrescriptionField label="Khoa" value={prescription.departmentId} identity="department" />
          <div><dt className="text-xs text-muted-foreground">Tổng tiền</dt><dd className="mt-1 font-semibold">{formatVnd(prescription.totalAmount)}</dd></div>
          <div><dt className="text-xs text-muted-foreground">Ngày kê đơn</dt><dd className="mt-1">{formatDate(prescription.prescribedDate)}</dd></div>
        </dl>
      </section>

      <section className="rounded-xl border border-border bg-surface p-6">
        <h3 className="text-base font-semibold">Các dòng thuốc</h3>
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="border-b border-border text-xs text-muted-foreground">
              <tr><th className="px-3 py-3">Thuốc</th><th className="px-3 py-3">Số lượng</th><th className="px-3 py-3">Đơn giá</th><th className="px-3 py-3">Cách dùng</th><th className="px-3 py-3 text-right">Thành tiền</th></tr>
            </thead>
            <tbody className="divide-y divide-border">
              {prescription.lines.map((line) => (
                <tr key={line.lineId}>
                  <td className="px-3 py-3"><div>{line.drugName ?? "Chưa có thông tin tên thuốc"}</div></td>
                  <td className="px-3 py-3">{line.quantity}</td>
                  <td className="px-3 py-3">{formatVnd(line.unitPrice)}</td>
                  <td className="px-3 py-3">{line.dosage ?? "—"}</td>
                  <td className="px-3 py-3 text-right font-medium">{formatVnd(line.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {(prescription.cancelledAt || prescription.cancelledBy || prescription.cancellationReason) && (
        <section className="rounded-xl border border-border bg-surface p-6">
          <h3 className="text-base font-semibold">Thông tin hủy đơn</h3>
          <dl className="mt-4 grid gap-4 sm:grid-cols-3">
            <div><dt className="text-xs text-muted-foreground">Thời điểm</dt><dd className="mt-1">{formatDateTime(prescription.cancelledAt)}</dd></div>
            <div><dt className="text-xs text-muted-foreground">Người hủy</dt><dd className="mt-1"><ReferenceText value={prescription.cancelledBy} fallback="Đã ghi nhận tài khoản thực hiện" /></dd></div>
            <div><dt className="text-xs text-muted-foreground">Lý do</dt><dd className="mt-1">{prescription.cancellationReason ?? "—"}</dd></div>
          </dl>
        </section>
      )}

      <section className="rounded-xl border border-border bg-surface p-6">
        <h3 className="text-base font-semibold">Lịch sử bản ghi</h3>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <div><dt className="text-xs text-muted-foreground">Tạo lúc</dt><dd className="mt-1">{formatDateTime(prescription.createdAt)}</dd></div>
          <div><dt className="text-xs text-muted-foreground">Cập nhật lúc</dt><dd className="mt-1">{formatDateTime(prescription.updatedAt)}</dd></div>
        </dl>
        <details className="mt-4 text-sm text-muted-foreground">
          <summary className="cursor-pointer">Mã tham chiếu khi cần hỗ trợ</summary>
          <dl className="mt-3 grid gap-3 text-xs sm:grid-cols-2">
            {[["Đơn thuốc", prescription.prescriptionId], ["Hồ sơ khám", prescription.recordId], ["Bệnh nhân", prescription.patientId], ["Bác sĩ", prescription.doctorId], ["Khoa", prescription.departmentId], ["Tài khoản hủy", prescription.cancelledBy]].filter(([, value]) => value).map(([label, value]) => <div key={label}><dt>{label}</dt><dd className="mt-1 break-all font-mono">{value}</dd></div>)}
          </dl>
        </details>
      </section>
    </section>
  );
}
