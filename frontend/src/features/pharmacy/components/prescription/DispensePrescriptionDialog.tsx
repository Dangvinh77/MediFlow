"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { ApiRequestError } from "@/lib/api";
import { getRole } from "@/lib/auth";
import { formatVnd } from "../../utils";
import { pharmacyApi } from "../../api";
import { getPharmacyCapabilities } from "../../permissions";
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
  "PRESCRIPTION_NOT_FOUND", "DISPENSE_NOT_FOUND", "DISPENSE_ALREADY_DONE",
  "DISPENSE_INVALID_TRANSITION", "PRESCRIPTION_DISPENSE_NOT_PENDING",
]);
const TERMINAL_FAILURE_CODES = new Set([
  "DRUG_OUT_OF_STOCK", "DRUG_EXPIRED", "DRUG_QUANTITY_INVALID", "RESERVATION_MISSING",
  "RESERVATION_INVALID_TRANSITION", "RESERVATION_EXPIRED", "RESERVATION_QUANTITY_MISMATCH",
  "RESERVATION_SET_MISMATCH", "PRESCRIPTION_NOT_ACTIVE", "PRESCRIPTION_LINE_MISSING",
]);

function errorMessage(cause: ApiRequestError): string {
  const correlation = cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : "";
  if (cause.status === 403) return `Bạn không có quyền xuất thuốc cho đơn này.${correlation}`;
  if (cause.code === "PAYMENT_PROOF_REQUIRED" || cause.code === "PHARMACY_CLEARANCE_REQUIRED") {
    return `Chưa có xác nhận thanh toán hợp lệ để xuất thuốc.${correlation}`;
  }
  return `${cause.message}${correlation}`;
}

/** Bodyless PUT; the server supplies actor identity, payment authority and stock effects. */
export function DispensePrescriptionDialog({ prescriptionId, totalAmount, lineCount, onClose, onSuccess, onConflict, onFailure }: DispensePrescriptionDialogProps) {
  const router = useRouter();
  const sectionRef = useRef<HTMLElement>(null);
  const submittingRef = useRef(false);
  const active = useRef(true);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [forbidden, setForbidden] = useState(false);

  useEffect(() => {
    active.current = true;
    const previousFocus = document.activeElement as HTMLElement | null;
    sectionRef.current?.querySelector<HTMLButtonElement>("button[type=button]")?.focus();
    return () => {
      active.current = false;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current || forbidden || !getPharmacyCapabilities(getRole()).canDispensePrescription) return;
    submittingRef.current = true;
    setFormError(null);
    setSubmitting(true);
    try {
      const result = await pharmacyApi.dispensePrescription(prescriptionId);
      if (active.current) onSuccess(result);
    } catch (cause: unknown) {
      if (!active.current) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      if (!(cause instanceof ApiRequestError) || cause.status >= 500 || cause.status < 400) {
        const correlation = cause instanceof ApiRequestError && cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : "";
        onConflict(`Chưa xác định được kết quả xuất thuốc.${correlation} Đang tải lại đơn để kiểm tra trước khi thao tác tiếp.`);
        return;
      }
      if (cause.status === 409 || cause.status === 404 || RACE_ERROR_CODES.has(cause.code ?? "")
          || cause.code === "PAYMENT_PROOF_REQUIRED" || cause.code === "PHARMACY_CLEARANCE_REQUIRED") {
        onConflict(`${errorMessage(cause)} Đang tải lại trạng thái đơn thuốc.`);
        return;
      }
      if (TERMINAL_FAILURE_CODES.has(cause.code ?? "")) {
        onFailure(`${errorMessage(cause)} Đang tải lại đơn để kiểm tra kết quả xuất thuốc.`);
        return;
      }
      if (cause.status === 403) setForbidden(true);
      setFormError(errorMessage(cause));
    } finally {
      submittingRef.current = false;
      if (active.current) setSubmitting(false);
    }
  }

  return (
    <section ref={sectionRef} aria-labelledby="dispense-prescription-title" aria-busy={submitting || undefined}
      className="mt-5 rounded-xl border border-border bg-surface-muted p-4 sm:p-5"
      onKeyDown={event => {
        if (event.key === "Escape" && !submittingRef.current) { event.preventDefault(); onClose(); }
      }}>
      <h4 id="dispense-prescription-title" className="font-semibold">Xác nhận xuất thuốc</h4>
      <p className="mt-2 text-sm text-muted-foreground">Kiểm tra đơn trước khi xác nhận. Hệ thống sẽ kiểm tra lại thanh toán và tồn kho khi xử lý.</p>
      {formError && <p role="alert" className="mt-4 rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">{formError}</p>}
      <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-3">
        <div><dt className="text-muted-foreground">Mã đơn thuốc</dt><dd className="mt-1 break-all font-mono text-xs">{prescriptionId}</dd></div>
        <div><dt className="text-muted-foreground">Tổng tiền</dt><dd className="mt-1 font-semibold tabular-nums">{formatVnd(totalAmount)}</dd></div>
        <div><dt className="text-muted-foreground">Số dòng thuốc</dt><dd className="mt-1">{lineCount}</dd></div>
      </dl>
      <form onSubmit={submit} className="mt-5 flex flex-wrap gap-3">
        <Button type="button" onClick={onClose} disabled={submitting}>Đóng</Button>
        <Button type="submit" variant="primary" loading={submitting} disabled={forbidden}>Xác nhận xuất thuốc</Button>
      </form>
    </section>
  );
}
