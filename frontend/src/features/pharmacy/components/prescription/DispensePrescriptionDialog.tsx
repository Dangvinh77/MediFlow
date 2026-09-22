"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { formatVnd } from "../../utils";
import { pharmacyApi } from "../../api";
import type { DispenseDTO } from "../../types";

interface DispensePrescriptionDialogProps {
  prescriptionId: string;
  totalAmount: number;
  lineCount: number;
  onClose: () => void;
  onSuccess: (result: DispenseDTO) => void;
  onConflict: (message: string) => void;
  onFailure: (message: string) => void;
}

const RACE_ERROR_CODES = new Set([
  "PRESCRIPTION_NOT_FOUND",
  "DISPENSE_NOT_FOUND",
  "DISPENSE_ALREADY_DONE",
  "DISPENSE_INVALID_TRANSITION",
  "PRESCRIPTION_DISPENSE_NOT_PENDING",
]);

const TERMINAL_FAILURE_CODES = new Set([
  "DRUG_OUT_OF_STOCK",
  "DRUG_EXPIRED",
  "DRUG_QUANTITY_INVALID",
  "RESERVATION_MISSING",
  "RESERVATION_INVALID_TRANSITION",
  "RESERVATION_EXPIRED",
  "RESERVATION_QUANTITY_MISMATCH",
  "RESERVATION_SET_MISMATCH",
  "PRESCRIPTION_NOT_ACTIVE",
  "PRESCRIPTION_LINE_MISSING",
]);

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    const correlation = cause.correlationId
      ? ` (Mã tra cứu: ${cause.correlationId})`
      : "";
    if (cause.status === 403) {
      return `Bạn không có quyền xuất thuốc cho đơn này.${correlation}`;
    }
    if (cause.code === "PAYMENT_PROOF_REQUIRED") {
      return `Billing chưa xác nhận thanh toán; đơn thuốc vẫn giữ trạng thái chờ xuất.${correlation}`;
    }
    return `${cause.message}${correlation}`;
  }

  return cause instanceof Error
    ? cause.message
    : "Không thể xuất thuốc.";
}

function isRaceError(cause: unknown): cause is ApiRequestError {
  return (
    cause instanceof ApiRequestError &&
    (cause.status === 404 || RACE_ERROR_CODES.has(cause.code ?? ""))
  );
}

/** Confirmation dialog for the bodyless, payment-gated dispensing mutation. */
export function DispensePrescriptionDialog({
  prescriptionId,
  totalAmount,
  lineCount,
  onClose,
  onSuccess,
  onConflict,
  onFailure,
}: DispensePrescriptionDialogProps) {
  const router = useRouter();
  const dialogRef = useRef<HTMLDivElement>(null);
  const submittingRef = useRef(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [paymentProofRequired, setPaymentProofRequired] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    submittingRef.current = submitting;
  }, [submitting]);

  useEffect(() => {
    const previousFocus = document.activeElement as HTMLElement | null;
    dialogRef.current?.querySelector<HTMLElement>("button[type=\"submit\"]")?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !submittingRef.current) {
        event.preventDefault();
        onClose();
        return;
      }

      if (event.key !== "Tab" || !dialogRef.current) return;

      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>(
          "button, textarea, input, select, [tabindex]:not([tabindex=\"-1\"])",
        ),
      ).filter((element) => !element.hasAttribute("disabled"));

      if (focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      previousFocus?.focus();
    };
  }, [onClose]);

  async function submit() {
    if (submitting) return;

    setFormError(null);
    setSubmitting(true);

    try {
      // The backend takes the verified actor and payment proof from the session;
      // this request intentionally has no payment or invoice fields/body.
      const result = await pharmacyApi.dispensePrescription(prescriptionId);
      onSuccess(result);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      if (cause instanceof ApiRequestError && cause.code === "PAYMENT_PROOF_REQUIRED") {
        setPaymentProofRequired(true);
        setFormError(errorMessage(cause));
        return;
      }

      if (isRaceError(cause)) {
        onConflict(`${errorMessage(cause)} Đang tải lại trạng thái đơn thuốc.`);
        return;
      }

      if (
        cause instanceof ApiRequestError &&
        TERMINAL_FAILURE_CODES.has(cause.code ?? "")
      ) {
        onFailure(`${errorMessage(cause)} Backend đã ghi nhận phiếu xuất thất bại; đang tải lại trạng thái đơn thuốc.`);
        return;
      }

      setFormError(errorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      ref={dialogRef}
      role="dialog"
      aria-modal="true"
      aria-labelledby="dispense-prescription-title"
      className="mt-4 rounded-xl border border-warning/40 bg-warning/10 p-5"
    >
      <h4 id="dispense-prescription-title" className="font-semibold">
        Xác nhận xuất thuốc
      </h4>
      <p className="mt-2 text-sm">
        Chỉ tiếp tục khi Billing đã xác nhận thanh toán. Hệ thống sẽ xuất theo snapshot server và
        cập nhật tồn kho, không cho nhập tay trạng thái thanh toán.
      </p>

      {formError && (
        <p role="alert" className="mt-3 rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
          {formError}
        </p>
      )}

      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-muted-foreground">Mã đơn thuốc</dt>
          <dd className="mt-1 break-all font-mono text-xs">{prescriptionId}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Tổng tiền</dt>
          <dd className="mt-1 font-semibold">{formatVnd(totalAmount)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Số dòng thuốc</dt>
          <dd className="mt-1">{lineCount}</dd>
        </div>
      </dl>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
        className="mt-4 flex flex-wrap gap-3"
      >
        <button
          type="button"
          onClick={onClose}
          disabled={submitting}
          className="rounded-lg border border-border bg-surface px-4 py-2 font-medium hover:bg-surface-muted disabled:opacity-50"
        >
          Đóng
        </button>
        <button
          type="submit"
          disabled={submitting || paymentProofRequired}
          className="rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {submitting
            ? "Đang xuất thuốc…"
            : paymentProofRequired
              ? "Chờ Billing xác nhận"
              : "Xác nhận xuất thuốc"}
        </button>
      </form>
    </div>
  );
}
