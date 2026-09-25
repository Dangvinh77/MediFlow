"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { pharmacyApi } from "../../api";
import type { CancelPrescriptionResult } from "../../types";

interface CancelPrescriptionDialogProps {
  prescriptionId: string;
  onClose: () => void;
  onSuccess: (result: CancelPrescriptionResult) => void;
  onConflict: (message: string) => void;
}

const RACE_ERROR_CODES = new Set([
  "PRESCRIPTION_NOT_FOUND",
  "PRESCRIPTION_CANNOT_BE_CANCELLED",
  "PRESCRIPTION_DISPENSE_NOT_PENDING",
  "PRESCRIPTION_RESERVATION_MISSING",
  "PRESCRIPTION_RESERVATION_INCONSISTENT",
  "PRESCRIPTION_RESERVATION_QUANTITY_MISMATCH",
]);

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    const correlation = cause.correlationId
      ? ` (Mã tra cứu: ${cause.correlationId})`
      : "";
    if (cause.code === "PRESCRIPTION_CANCELLATION_FORBIDDEN") {
      return `Chỉ bác sĩ kê đơn hoặc ADMIN được phép hủy đơn thuốc này.${correlation}`;
    }
    if (cause.status === 403) {
      return `Bạn không có quyền hủy đơn thuốc này.${correlation}`;
    }
    return `${cause.message}${correlation}`;
  }

  return cause instanceof Error
    ? cause.message
    : "Không thể hủy đơn thuốc.";
}

function isRaceError(cause: unknown): cause is ApiRequestError {
  return (
    cause instanceof ApiRequestError &&
    (cause.status === 404 || RACE_ERROR_CODES.has(cause.code ?? ""))
  );
}

/** Confirmation form for the non-optimistic prescription cancellation flow. */
export function CancelPrescriptionDialog({
  prescriptionId,
  onClose,
  onSuccess,
  onConflict,
}: CancelPrescriptionDialogProps) {
  const router = useRouter();
  const dialogRef = useRef<HTMLDivElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const submittingRef = useRef(false);
  const [reason, setReason] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    submittingRef.current = submitting;
  }, [submitting]);

  useEffect(() => {
    const previousFocus = document.activeElement as HTMLElement | null;
    reasonRef.current?.focus();

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

  function validateReason(): string | null {
    const normalized = reason.trim();
    if (normalized.length < 1) return "Lý do hủy đơn thuốc là bắt buộc.";
    if (normalized.length > 500) return "Lý do hủy không được vượt quá 500 ký tự.";
    return null;
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const validationError = validateReason();
    if (validationError) {
      setFieldError(validationError);
      setFormError(null);
      reasonRef.current?.focus();
      return;
    }

    setFieldError(null);
    setFormError(null);
    setSubmitting(true);

    try {
      const result = await pharmacyApi.cancelPrescription(prescriptionId, {
        reason: reason.trim(),
      });
      onSuccess(result);
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      if (isRaceError(cause)) {
        onConflict(`${errorMessage(cause)} Đang tải lại trạng thái đơn thuốc.`);
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
      aria-labelledby="cancel-prescription-title"
      className="mt-4 rounded-xl border border-warning/40 bg-warning/10 p-5"
    >
      <h4 id="cancel-prescription-title" className="font-semibold">
        Xác nhận hủy đơn thuốc
      </h4>
      <p className="mt-2 text-sm">
        Đơn thuốc <code className="break-all">{prescriptionId}</code> sẽ kết thúc và các giữ chỗ
        tồn kho còn lại sẽ được giải phóng.
      </p>

      {formError && (
        <p role="alert" className="mt-3 rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
          {formError}
        </p>
      )}

      <form onSubmit={submit} className="mt-4 space-y-3">
        <div className="flex flex-col gap-1">
          <label htmlFor="cancel-prescription-reason" className="text-sm font-medium">
            Lý do hủy
          </label>
          <textarea
            ref={reasonRef}
            id="cancel-prescription-reason"
            value={reason}
            maxLength={500}
            aria-invalid={fieldError ? true : undefined}
            aria-describedby={fieldError ? "cancel-prescription-reason-error" : undefined}
            onChange={(event) => {
              setReason(event.target.value);
              setFieldError(null);
              setFormError(null);
            }}
            className={`min-h-24 w-full resize-y rounded-lg border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground ${fieldError ? "border-danger" : "border-border"}`}
            placeholder="Nhập lý do từ 1 đến 500 ký tự"
          />
          {fieldError && (
            <p id="cancel-prescription-reason-error" className="text-xs text-danger">
              {fieldError}
            </p>
          )}
          <p className="text-right text-xs text-muted-foreground">{reason.length}/500</p>
        </div>

        <div className="flex flex-wrap gap-3">
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
            disabled={submitting}
            className="rounded-lg bg-danger px-4 py-2 font-medium text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? "Đang hủy…" : "Xác nhận hủy đơn"}
          </button>
        </div>
      </form>
    </div>
  );
}
