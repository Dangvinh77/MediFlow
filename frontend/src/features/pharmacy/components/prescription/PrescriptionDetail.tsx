"use client";

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
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import { getRole } from "@/lib/session";
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
import type {
  CancelPrescriptionResult,
  DispenseDTO,
  PrescriptionDTO,
} from "../../types";
import { formatDate, formatDateTime, formatVnd } from "../../utils";
import { CancelPrescriptionDialog } from "./CancelPrescriptionDialog";
import { DispensePrescriptionDialog } from "./DispensePrescriptionDialog";

interface PrescriptionDetailProps {
  prescriptionId: string;
}

type DetailRequestState =
  | { key: string; status: "idle" }
  | { key: string; status: "success"; prescription: PrescriptionDTO }
  | { key: string; status: "not-found" }
  | { key: string; status: "dispense-not-found" }
  | { key: string; status: "error"; message: string };

type ActiveAction = "cancel" | "dispense" | null;

interface ActionFeedback {
  tone: "success" | "warning" | "danger";
  message: string;
}

function subscribeToRoleChanges(onStoreChange: () => void) {
  window.addEventListener("storage", onStoreChange);
  window.addEventListener("focus", onStoreChange);

  return () => {
    window.removeEventListener("storage", onStoreChange);
    window.removeEventListener("focus", onStoreChange);
  };
}

function getServerRole(): Role | null {
  return null;
}

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 403) return "Bạn không có quyền xem đơn thuốc này.";
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

function DetailError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
      <p>{message}</p>
      <button type="button" onClick={onRetry} className="mt-3 rounded-lg border border-danger/40 px-3 py-2 font-medium hover:bg-danger/10">
        Thử lại
      </button>
    </div>
  );
}

function CopyValue({ label, value }: { label: string; value: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 flex items-center gap-2">
        <code className="break-all text-xs">{value}</code>
        <button type="button" onClick={copy} className="shrink-0 rounded border border-border px-2 py-1 text-xs hover:bg-surface-muted">
          {copied ? "Đã chép" : "Chép"}
        </button>
      </dd>
    </div>
  );
}

export function PrescriptionDetail({ prescriptionId }: PrescriptionDetailProps) {
  const router = useRouter();
  const role = useSyncExternalStore(subscribeToRoleChanges, getRole, getServerRole);
  const capabilities = getPharmacyCapabilities(role);
  const [requestState, setRequestState] = useState<DetailRequestState>({ key: "", status: "idle" });
  const [retryToken, setRetryToken] = useState(0);
  const [activeAction, setActiveAction] = useState<ActiveAction>(null);
  const [actionFeedback, setActionFeedback] = useState<ActionFeedback | null>(null);
  const requestSequence = useRef(0);
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
      tone: result.releasedReservations === 0 ? "warning" : "success",
      message:
        result.releasedReservations === 0
          ? "Đơn thuốc đã được hủy trước đó; không còn reservation nào cần giải phóng."
          : `Đã hủy đơn thuốc và giải phóng ${result.releasedReservations} reservation tồn kho.`,
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
        message: "Đã xuất thuốc thành công; chi tiết đơn và tồn kho đang được làm mới từ máy chủ.",
      });
    } else {
      setActionFeedback({
        tone: "warning",
        message: `Backend trả về trạng thái phiếu xuất ${result.status}; đang làm mới chi tiết đơn.`,
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
    if (!canRead) return undefined;

    let active = true;
    const requestId = ++requestSequence.current;

    pharmacyApi
      .getPrescription(prescriptionId)
      .then((nextPrescription) => {
        if (!active || requestId !== requestSequence.current) return;
        setRequestState({ key: requestKey, status: "success", prescription: nextPrescription });
      })
      .catch((cause: unknown) => {
        if (!active || requestId !== requestSequence.current) return;

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

        setRequestState({ key: requestKey, status: "error", message: errorMessage(cause) });
      });

    return () => {
      active = false;
    };
  }, [canRead, prescriptionId, requestKey, router]);

  if (role === null) return <p className="mt-6 text-sm text-muted-foreground">Đang kiểm tra quyền…</p>;
  if (!canRead) {
    return <p role="alert" className="mt-6 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">Tài khoản của bạn không có quyền xem đơn thuốc.</p>;
  }
  if (loading && !prescription && !notFound && !dispenseNotFound && !error) return <DetailLoading />;

  if (notFound) {
    return (
      <section className="mt-6 rounded-xl border border-border bg-surface p-6">
        <h2 className="text-lg font-semibold">Không tìm thấy đơn thuốc</h2>
        <p className="mt-2 text-sm text-muted-foreground">Không có đơn thuốc nào với mã <code className="break-all">{prescriptionId}</code>.</p>
        <Link href="/pharmacy/prescriptions" className="mt-4 inline-flex rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground hover:opacity-90">Quay lại tra cứu</Link>
      </section>
    );
  }

  if (dispenseNotFound) {
    return (
      <section className="mt-6 rounded-xl border border-warning/40 bg-warning/10 p-6">
        <h2 className="text-lg font-semibold">Chưa có phiếu xuất thuốc</h2>
        <p className="mt-2 text-sm">Đơn thuốc tồn tại nhưng backend chưa trả về phiếu xuất tương ứng. Đây là trạng thái khác với việc không tìm thấy đơn thuốc.</p>
        <Link href="/pharmacy/prescriptions" className="mt-4 inline-flex rounded-lg border border-border px-4 py-2 font-medium hover:bg-surface-muted">Quay lại tra cứu</Link>
      </section>
    );
  }

  if (error) return <DetailError message={error} onRetry={refresh} />;
  if (!prescription) return null;

  const status = prescriptionStatusPresentation[prescription.status];
  const dispenseStatus = dispenseStatusPresentation[prescription.dispenseStatus];
  const showCancelSlot = canCancelPrescription(role, prescription);
  const showDispenseSlot = canDispensePrescription(role, prescription);

  return (
    <section className="mt-6 space-y-6">
      {loading && <p role="status" className="rounded-lg border border-info/40 bg-info/10 p-3 text-sm text-info">Đang xác nhận snapshot đơn thuốc mới nhất…</p>}

      <div className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-6 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p className="font-mono text-xs text-muted-foreground">{prescription.prescriptionId}</p>
          <h2 className="mt-1 text-xl font-semibold">Chi tiết đơn thuốc</h2>
          <p className="mt-1 text-sm text-muted-foreground">Ngày kê: {formatDate(prescription.prescribedDate)}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          <StatusBadge tone={dispenseStatus.tone}>{dispenseStatus.label}</StatusBadge>
        </div>
      </div>

      {actionFeedback && (
        <p
          role={actionFeedback.tone === "danger" ? "alert" : "status"}
          aria-live="polite"
          className={`rounded-lg border p-4 text-sm ${
            actionFeedback.tone === "success"
              ? "border-success/40 bg-success/10 text-success"
              : actionFeedback.tone === "warning"
                ? "border-warning/40 bg-warning/10 text-warning"
                : "border-danger/40 bg-danger/10 text-danger"
          }`}
        >
          {actionFeedback.message}
        </p>
      )}

      {(showCancelSlot || showDispenseSlot) && (
        <section className="rounded-xl border border-border bg-surface p-5">
          <h3 className="font-semibold">Thao tác đơn thuốc</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            Hủy đơn sẽ giải phóng reservation. Xuất thuốc chỉ được thực hiện khi Billing đã xác nhận
            thanh toán; backend vẫn là lớp quyết định cuối cùng.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            {showCancelSlot && (
              <button
                type="button"
                onClick={() => setActiveAction("cancel")}
                disabled={activeAction !== null}
                className="rounded-lg border border-danger/40 px-4 py-2 text-sm font-medium text-danger hover:bg-danger/10 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Hủy đơn
              </button>
            )}
            {showDispenseSlot && (
              <button
                type="button"
                onClick={() => setActiveAction("dispense")}
                disabled={activeAction !== null}
                className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Xuất thuốc
              </button>
            )}
          </div>

          {activeAction === "cancel" && showCancelSlot && (
            <CancelPrescriptionDialog
              prescriptionId={prescription.prescriptionId}
              onClose={closeAction}
              onSuccess={handleCancelSuccess}
              onConflict={handleActionConflict}
            />
          )}

          {activeAction === "dispense" && showDispenseSlot && (
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
        <h3 className="text-base font-semibold">Ngữ cảnh nghiệp vụ</h3>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <CopyValue label="Mã hồ sơ bệnh án" value={prescription.recordId} />
          <CopyValue label="Mã bệnh nhân" value={prescription.patientId} />
          <CopyValue label="Mã bác sĩ" value={prescription.doctorId} />
          <CopyValue label="Mã khoa" value={prescription.departmentId} />
          <div><dt className="text-xs text-muted-foreground">Tổng tiền từ backend</dt><dd className="mt-1 font-semibold">{formatVnd(prescription.totalAmount)}</dd></div>
          <div><dt className="text-xs text-muted-foreground">Ngày kê đơn</dt><dd className="mt-1">{formatDate(prescription.prescribedDate)}</dd></div>
        </dl>
      </section>

      <section className="rounded-xl border border-border bg-surface p-6">
        <h3 className="text-base font-semibold">Các dòng thuốc</h3>
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="border-b border-border text-xs text-muted-foreground">
              <tr><th className="px-3 py-3">Thuốc</th><th className="px-3 py-3">Số lượng</th><th className="px-3 py-3">Đơn giá snapshot</th><th className="px-3 py-3">Cách dùng</th><th className="px-3 py-3 text-right">Thành tiền</th></tr>
            </thead>
            <tbody className="divide-y divide-border">
              {prescription.lines.map((line) => (
                <tr key={line.lineId}>
                  <td className="px-3 py-3"><div>{line.drugName ?? "(Không còn tên thuốc)"}</div><code className="text-xs text-muted-foreground">{line.drugId}</code></td>
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
          <h3 className="text-base font-semibold">Kiểm toán hủy đơn</h3>
          <dl className="mt-4 grid gap-4 sm:grid-cols-3">
            <div><dt className="text-xs text-muted-foreground">Thời điểm</dt><dd className="mt-1">{formatDateTime(prescription.cancelledAt)}</dd></div>
            <div><dt className="text-xs text-muted-foreground">Người hủy</dt><dd className="mt-1 break-all">{prescription.cancelledBy ?? "—"}</dd></div>
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
      </section>
    </section>
  );
}
