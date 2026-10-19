"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getRole } from "@/lib/auth";
import { pharmacyApi } from "../../api";
import { hasUncertainMutationStatus } from "../../mutationOutcome";
import { getPharmacyCapabilities } from "../../permissions";
import type { CancelPrescriptionResult } from "../../types";
import { usePharmacyRequestScope } from "../PharmacySessionBoundary";

interface CancelPrescriptionDialogProps {
  prescriptionId: string;
  onClose: () => void;
  onSuccess: (result: CancelPrescriptionResult) => void;
  onConflict: (message: string) => void;
}

const RACE_ERROR_CODES = new Set([
  "PRESCRIPTION_NOT_FOUND", "PRESCRIPTION_CANNOT_BE_CANCELLED", "PRESCRIPTION_DISPENSE_NOT_PENDING",
  "PRESCRIPTION_RESERVATION_MISSING", "PRESCRIPTION_RESERVATION_INCONSISTENT",
  "PRESCRIPTION_RESERVATION_QUANTITY_MISMATCH",
]);

function errorMessage(cause: ApiRequestError): string {
  const correlation = cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : "";
  if (cause.code === "PRESCRIPTION_CANCELLATION_FORBIDDEN") {
    return `Chỉ bác sĩ kê đơn hoặc quản trị viên được phép hủy đơn thuốc này.${correlation}`;
  }
  if (cause.status === 403) return `Bạn không có quyền hủy đơn thuốc này.${correlation}`;
  return `${cause.message}${correlation}`;
}

/** Inline confirmation; it leaves the rest of the page available to keyboard users. */
export function CancelPrescriptionDialog({ prescriptionId, onClose, onSuccess, onConflict }: CancelPrescriptionDialogProps) {
  const isCurrent = usePharmacyRequestScope("canCancelPrescription");
  const router = useRouter();
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const submittingRef = useRef(false);
  const active = useRef(true);
  const focusError = useRef(false);
  const [reason, setReason] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [forbidden, setForbidden] = useState(false);

  useEffect(() => {
    active.current = true;
    const previousFocus = document.activeElement as HTMLElement | null;
    reasonRef.current?.focus();
    return () => {
      active.current = false;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);

  useEffect(() => {
    if (!submitting && focusError.current) {
      reasonRef.current?.focus();
      focusError.current = false;
    }
  }, [fieldError, submitting]);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current || forbidden || !isCurrent() || !getPharmacyCapabilities(getRole()).canCancelPrescription) return;
    const normalized = reason.trim();
    if (!normalized || normalized.length > 500) {
      setFieldError(!normalized ? "Lý do hủy đơn thuốc là bắt buộc." : "Lý do hủy không được vượt quá 500 ký tự.");
      reasonRef.current?.focus();
      return;
    }

    submittingRef.current = true;
    setFieldError(null);
    setFormError(null);
    setSubmitting(true);
    try {
      const result = await pharmacyApi.cancelPrescription(prescriptionId, { reason: normalized });
      if (active.current && isCurrent()) onSuccess(result);
    } catch (cause: unknown) {
      if (!active.current || !isCurrent()) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      if (!(cause instanceof ApiRequestError) || hasUncertainMutationStatus(cause)) {
        const correlation = cause instanceof ApiRequestError && cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : "";
        onConflict(`Chưa xác định được kết quả hủy đơn.${correlation} Đang tải lại đơn để kiểm tra trước khi thao tác tiếp.`);
        return;
      }
      if (cause.status === 409 || cause.status === 404 || RACE_ERROR_CODES.has(cause.code ?? "")) {
        onConflict(`${errorMessage(cause)} Đang tải lại trạng thái đơn thuốc.`);
        return;
      }
      const reasonError = cause.details.find(detail => detail.field === "reason")?.message;
      if (reasonError) {
        setFieldError(reasonError);
        focusError.current = true;
      }
      if (cause.status === 403) setForbidden(true);
      setFormError(errorMessage(cause));
    } finally {
      submittingRef.current = false;
      if (active.current && isCurrent()) setSubmitting(false);
    }
  }

  return (
    <section aria-labelledby="cancel-prescription-title" aria-busy={submitting || undefined}
      className="mt-5 rounded-xl border border-border bg-surface-muted p-4 sm:p-5"
      onKeyDown={event => {
        if (event.key === "Escape" && !submittingRef.current) { event.preventDefault(); onClose(); }
      }}>
      <h4 id="cancel-prescription-title" className="font-semibold">Xác nhận hủy đơn thuốc</h4>
      <p className="mt-2 text-sm text-muted-foreground">
        Đơn <code className="break-all">{prescriptionId}</code> sẽ được hủy. Số lượng đang giữ cho đơn sẽ được giải phóng theo kết quả xử lý.
      </p>
      {formError && <p role="alert" className="mt-4 rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">{formError}</p>}
      <form onSubmit={submit} noValidate className="mt-4 space-y-4">
        <Field label="Lý do hủy" htmlFor="cancel-prescription-reason" error={fieldError ?? undefined} required>
          <textarea ref={reasonRef} id="cancel-prescription-reason" value={reason} maxLength={500}
            disabled={submitting} aria-required="true" aria-invalid={Boolean(fieldError) || undefined}
            aria-describedby={fieldError ? "cancel-prescription-reason-error" : "cancel-prescription-reason-hint"}
            onChange={event => { setReason(event.target.value); setFieldError(null); setFormError(null); }}
            className={`${controlClassName} min-h-24 resize-y${fieldError ? " border-danger" : ""}`}
            placeholder="Nhập lý do từ 1 đến 500 ký tự" />
          <p id="cancel-prescription-reason-hint" className="text-right text-xs text-muted-foreground">{reason.length}/500</p>
        </Field>
        <div className="flex flex-wrap gap-3">
          <Button type="button" onClick={onClose} disabled={submitting}>Đóng</Button>
          <Button type="submit" variant="danger" loading={submitting} disabled={forbidden}>Xác nhận hủy đơn</Button>
        </div>
      </form>
    </section>
  );
}
