"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getRole } from "@/lib/auth";
import { getPharmacyCapabilities } from "../../permissions";
import { pharmacyApi } from "../../api";
import { hasUncertainMutationStatus } from "../../mutationOutcome";
import type { DrugDTO } from "../../types";
import { mapFieldErrors } from "../../utils";
import { PharmacySessionBoundary, usePharmacyRequestScope } from "../PharmacySessionBoundary";

export interface StockAdjustmentUncertainty {
  message: string;
  quantity: number;
  unit: string;
}

interface AdjustStockFormProps {
  drug: DrugDTO;
  onSuccess: (updatedDrug: DrugDTO) => void;
  onConflict: () => void;
  onUncertain?: (outcome: StockAdjustmentUncertainty) => void;
  disabled?: boolean;
}

interface FieldErrors {
  quantity?: string;
  reason?: string;
}

const INTEGER_MIN = -2_147_483_648;
const INTEGER_MAX = 2_147_483_647;

function parseQuantity(value: string): number | null {
  const normalized = value.trim();
  if (!/^-?\d+$/.test(normalized)) return null;
  const quantity = Number(normalized);
  return Number.isInteger(quantity) && quantity >= INTEGER_MIN && quantity <= INTEGER_MAX
    ? quantity
    : null;
}

function errorMessage(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 403) {
      const correlation = cause.correlationId ? ` (Mã tra cứu: ${cause.correlationId})` : "";
      return `Bạn không có quyền điều chỉnh tồn kho. Hãy liên hệ quản trị viên.${correlation}`;
    }
    if (cause.correlationId) {
      return `${cause.message} (Mã tra cứu: ${cause.correlationId})`;
    }
    return cause.message;
  }
  return "Chưa xác định được kết quả điều chỉnh. Hãy kiểm tra tồn kho trước khi xác nhận lại.";
}

/** Handles the non-optimistic stock adjustment flow and confirmation preview. */
export function AdjustStockForm({
  ...props
}: AdjustStockFormProps) {
  return <PharmacySessionBoundary><AdjustStockFormContent key={props.drug.drugId} {...props} /></PharmacySessionBoundary>;
}

function AdjustStockFormContent({
  drug,
  onSuccess,
  onConflict,
  onUncertain,
  disabled = false,
}: AdjustStockFormProps) {
  const isCurrent = usePharmacyRequestScope("canAdjustStock");
  const router = useRouter();
  const [quantity, setQuantity] = useState("");
  const [reason, setReason] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [forbidden, setForbidden] = useState(false);
  const submittingRef = useRef(false);
  const quantityRef = useRef<HTMLInputElement>(null);
  const reasonRef = useRef<HTMLTextAreaElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);
  const wasConfirming = useRef(false);
  const active = useRef(true);
  const pendingErrorFocus = useRef<keyof FieldErrors | null>(null);
  const parsedQuantity = parseQuantity(quantity);

  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);

  useEffect(() => {
    if (confirming) confirmRef.current?.focus();
    else if (!submitting && pendingErrorFocus.current) {
      (pendingErrorFocus.current === "reason" ? reasonRef.current : quantityRef.current)?.focus();
      pendingErrorFocus.current = null;
    } else if (wasConfirming.current) quantityRef.current?.focus();
    wasConfirming.current = confirming;
  }, [confirming, submitting]);

  function focusErrors(errors: FieldErrors) {
    if (errors.quantity) quantityRef.current?.focus();
    else if (errors.reason) reasonRef.current?.focus();
  }

  function clearFeedback() {
    setFieldErrors({});
    setFormError(null);
    setSuccessMessage(null);
  }

  function prepareConfirmation(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current || disabled || forbidden || !isCurrent() || !getPharmacyCapabilities(getRole()).canAdjustStock) return;

    clearFeedback();
    const errors: FieldErrors = {};
    if (parsedQuantity === null || parsedQuantity === 0) {
      errors.quantity = "Số lượng phải là số nguyên khác 0.";
    }
    if (reason.trim().length > 255) {
      errors.reason = "Lý do không được dài quá 255 ký tự.";
    } else if ((parsedQuantity ?? 0) < 0 && !reason.trim()) {
      errors.reason = "Lý do là bắt buộc khi giảm tồn kho.";
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      focusErrors(errors);
      return;
    }

    setConfirming(true);
  }

  async function confirmAdjustment() {
    if (submittingRef.current || disabled || forbidden || !isCurrent() || !confirming || parsedQuantity === null || parsedQuantity === 0 || !getPharmacyCapabilities(getRole()).canAdjustStock) return;

    if (reason.trim().length > 255) {
      setFieldErrors({ reason: "Lý do không được dài quá 255 ký tự." });
      setConfirming(false);
      return;
    }
    if (parsedQuantity < 0 && !reason.trim()) {
      setFieldErrors({ reason: "Lý do là bắt buộc khi giảm tồn kho." });
      setConfirming(false);
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    setFormError(null);
    setSuccessMessage(null);
    try {
      const updatedDrug = await pharmacyApi.adjustStock(drug.drugId, {
        quantity: parsedQuantity,
        reason: reason.trim() || undefined,
      });
      if (!active.current || !isCurrent()) return;
      onSuccess(updatedDrug);
      setQuantity("");
      setReason("");
      setConfirming(false);
      setSuccessMessage(`Đã cập nhật tồn kho: ${updatedDrug.stockQuantity.toLocaleString("vi-VN")} ${updatedDrug.unit}.`);
    } catch (cause: unknown) {
      if (!active.current || !isCurrent()) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }
      if (cause instanceof ApiRequestError && cause.status === 403) setForbidden(true);

      // A timeout, server failure or unreadable success cannot prove rollback.
      const uncertain = !(cause instanceof ApiRequestError) || hasUncertainMutationStatus(cause);
      if (uncertain) {
        const message = cause instanceof ApiRequestError
          ? `${errorMessage(cause)} Chưa xác định được kết quả điều chỉnh. Hãy kiểm tra tồn kho trước khi xác nhận lại.`
          : errorMessage(cause);
        setFormError(onUncertain ? null : message);
        setConfirming(false);
        setQuantity("");
        setReason("");
        // The detail owns this notice so failed refreshes cannot discard it with the form.
        onUncertain?.({ message, quantity: parsedQuantity, unit: drug.unit });
        onConflict();
        return;
      }

      if (
        cause instanceof ApiRequestError &&
        (cause.status === 409 || cause.code === "DRUG_OUT_OF_STOCK" || cause.code === "STOCK_BELOW_RESERVED")
      ) {
        setFormError(errorMessage(cause));
        setConfirming(false);
        onConflict();
        return;
      }

      if (cause instanceof ApiRequestError && cause.details.length > 0) {
        const details = mapFieldErrors(cause.details);
        pendingErrorFocus.current = details.quantity ? "quantity" : details.reason ? "reason" : null;
        setFieldErrors({
          quantity: details.quantity,
          reason: details.reason,
        });
      }
      setFormError(errorMessage(cause));
      setConfirming(false);
    } finally {
      submittingRef.current = false;
      if (active.current && isCurrent()) setSubmitting(false);
    }
  }

  return (
    <section className="mt-6 border-t border-border pt-6" aria-labelledby="adjust-stock-heading" onKeyDown={(event) => {
      if (event.key === "Escape" && confirming && !submittingRef.current) {
        event.preventDefault();
        setConfirming(false);
      }
    }}>
      <h3 id="adjust-stock-heading" className="text-base font-semibold">Điều chỉnh tồn kho</h3>
      <p className="mt-1 text-sm text-muted-foreground">
        Nhập số dương để thêm hàng hoặc số âm để giảm tồn. Cần ghi lý do khi giảm tồn kho.
      </p>

      {formError && (
        <div role="alert" className="mt-4 rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
          {formError}
        </div>
      )}
      {successMessage && (
        <p role="status" className="mt-4 rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">
          {successMessage}
        </p>
      )}

      <form onSubmit={prepareConfirmation} noValidate aria-busy={submitting || undefined} className="mt-4 space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Số lượng điều chỉnh" htmlFor="stock-quantity" error={fieldErrors.quantity} required>
            <input
              id="stock-quantity"
              ref={quantityRef}
              disabled={submitting || disabled || forbidden}
              aria-required="true"
              type="number"
              inputMode="numeric"
              step="1"
              value={quantity}
              onChange={(event) => { setQuantity(event.target.value); wasConfirming.current = false; setConfirming(false); clearFeedback(); }}
              aria-invalid={fieldErrors.quantity ? true : undefined}
              aria-describedby={fieldErrors.quantity ? "stock-quantity-error" : undefined}
              className={`${controlClassName}${fieldErrors.quantity ? " border-danger" : ""}`}
              placeholder="Ví dụ: 20 hoặc -5"
            />
          </Field>
          <Field label="Lý do" htmlFor="stock-reason" error={fieldErrors.reason}>
            <textarea
              id="stock-reason"
              ref={reasonRef}
              disabled={submitting || disabled || forbidden}
              value={reason}
              maxLength={255}
              onChange={(event) => { setReason(event.target.value); wasConfirming.current = false; setConfirming(false); clearFeedback(); }}
              aria-invalid={fieldErrors.reason ? true : undefined}
              aria-describedby={fieldErrors.reason ? "stock-reason-error" : undefined}
              className={`${controlClassName} min-h-24 resize-y${fieldErrors.reason ? " border-danger" : ""}`}
              placeholder="Bắt buộc khi giảm tồn"
            />
          </Field>
        </div>

        {!confirming && (
          <Button
            type="submit"
            disabled={submitting || disabled || forbidden}
          >
            Xem xác nhận
          </Button>
        )}
      </form>

      {confirming && parsedQuantity !== null && (
        <section aria-labelledby="stock-confirm-heading" aria-describedby="stock-confirm-description" className="mt-4 rounded-lg border border-warning/40 bg-warning/10 p-4">
          <h4 id="stock-confirm-heading" className="font-semibold">Xác nhận điều chỉnh</h4>
          <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
            <div className="sm:col-span-2"><dt className="text-muted-foreground">Thuốc</dt><dd className="mt-1 break-words font-medium">{drug.drugName}</dd></div>
            <div><dt className="text-muted-foreground">Tồn hiện tại</dt><dd className="mt-1">{drug.stockQuantity}</dd></div>
            <div><dt className="text-muted-foreground">Thay đổi</dt><dd className="mt-1">{parsedQuantity > 0 ? `+${parsedQuantity}` : parsedQuantity}</dd></div>
          </dl>
          <p className="mt-3 text-sm">
            Tồn dự kiến: <strong>{drug.stockQuantity + parsedQuantity}</strong>
          </p>
          <p id="stock-confirm-description" className="mt-2 text-xs text-muted-foreground">
            Tồn thực tế có thể thay đổi khi có giao dịch khác. Hệ thống sẽ kiểm tra lượng hàng đang được giữ trước khi cập nhật.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            <Button
              type="button"
              onClick={() => setConfirming(false)}
              disabled={submitting}
            >
              Hủy
            </Button>
            <button
              ref={confirmRef}
              type="button"
              onClick={() => void confirmAdjustment()}
              disabled={submitting || disabled || forbidden}
              className="min-h-11 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting ? "Đang cập nhật…" : "Xác nhận điều chỉnh"}
            </button>
          </div>
        </section>
      )}
    </section>
  );
}
