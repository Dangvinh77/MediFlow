"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { pharmacyApi } from "../../api";
import type { DrugDTO } from "../../types";
import { mapFieldErrors } from "../../utils";

interface AdjustStockFormProps {
  drug: DrugDTO;
  onSuccess: (updatedDrug: DrugDTO) => void;
  onConflict: () => void;
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
      return `Không được phép điều chỉnh tồn kho; kiểm tra quyền và staffId đã ký trong token.${correlation}`;
    }
    if (cause.correlationId) {
      return `${cause.message} (Mã tra cứu: ${cause.correlationId})`;
    }
    return cause.message;
  }
  return cause instanceof Error ? cause.message : "Không thể điều chỉnh tồn kho.";
}

function inputClass(invalid: boolean): string {
  return [
    "w-full rounded-lg border bg-surface px-3 py-2 text-foreground placeholder:text-muted-foreground",
    invalid ? "border-danger" : "border-border",
  ].join(" ");
}

/** Handles the non-optimistic stock adjustment flow and confirmation preview. */
export function AdjustStockForm({
  drug,
  onSuccess,
  onConflict,
}: AdjustStockFormProps) {
  const router = useRouter();
  const [quantity, setQuantity] = useState("");
  const [reason, setReason] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const parsedQuantity = parseQuantity(quantity);

  function clearFeedback() {
    setFieldErrors({});
    setFormError(null);
    setSuccessMessage(null);
  }

  function prepareConfirmation(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

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
      return;
    }

    setConfirming(true);
  }

  async function confirmAdjustment() {
    if (submitting || parsedQuantity === null || parsedQuantity === 0) return;

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

    setSubmitting(true);
    setFormError(null);
    setSuccessMessage(null);
    try {
      const updatedDrug = await pharmacyApi.adjustStock(drug.drugId, {
        quantity: parsedQuantity,
        reason: reason.trim() || undefined,
      });
      onSuccess(updatedDrug);
      setQuantity("");
      setReason("");
      setConfirming(false);
      setSuccessMessage("Đã cập nhật tồn kho từ snapshot do máy chủ trả về.");
    } catch (cause: unknown) {
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
        return;
      }

      if (
        cause instanceof ApiRequestError &&
        (cause.code === "DRUG_OUT_OF_STOCK" || cause.code === "STOCK_BELOW_RESERVED")
      ) {
        setFormError(errorMessage(cause));
        setConfirming(false);
        onConflict();
        return;
      }

      if (cause instanceof ApiRequestError && cause.details.length > 0) {
        const details = mapFieldErrors(cause.details);
        setFieldErrors({
          quantity: details.quantity,
          reason: details.reason,
        });
      }
      setFormError(errorMessage(cause));
      setConfirming(false);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="mt-6 border-t border-border pt-6" aria-labelledby="adjust-stock-heading">
      <h3 id="adjust-stock-heading" className="text-base font-semibold">Điều chỉnh tồn kho</h3>
      <p className="mt-1 text-sm text-muted-foreground">
        Delta dương là nhập thêm, delta âm là giảm tồn. Backend vẫn là nguồn sự thật khi có giao dịch đồng thời.
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

      <form onSubmit={prepareConfirmation} className="mt-4 space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1">
            <label htmlFor="stock-quantity" className="text-sm font-medium">Delta tồn kho</label>
            <input
              id="stock-quantity"
              type="number"
              inputMode="numeric"
              step="1"
              value={quantity}
              onChange={(event) => { setQuantity(event.target.value); setConfirming(false); clearFeedback(); }}
              aria-invalid={fieldErrors.quantity ? true : undefined}
              aria-describedby={fieldErrors.quantity ? "stock-quantity-error" : undefined}
              className={inputClass(Boolean(fieldErrors.quantity))}
              placeholder="Ví dụ: 20 hoặc -5"
            />
            {fieldErrors.quantity && <p id="stock-quantity-error" className="text-xs text-danger">{fieldErrors.quantity}</p>}
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="stock-reason" className="text-sm font-medium">Lý do</label>
            <textarea
              id="stock-reason"
              value={reason}
              maxLength={255}
              onChange={(event) => { setReason(event.target.value); setConfirming(false); clearFeedback(); }}
              aria-invalid={fieldErrors.reason ? true : undefined}
              aria-describedby={fieldErrors.reason ? "stock-reason-error" : undefined}
              className={`${inputClass(Boolean(fieldErrors.reason))} min-h-24 resize-y`}
              placeholder="Bắt buộc khi giảm tồn"
            />
            {fieldErrors.reason && <p id="stock-reason-error" className="text-xs text-danger">{fieldErrors.reason}</p>}
          </div>
        </div>

        {!confirming && (
          <button
            type="submit"
            disabled={submitting}
            className="rounded-lg border border-border bg-surface px-4 py-2 font-medium hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50"
          >
            Xem xác nhận
          </button>
        )}
      </form>

      {confirming && parsedQuantity !== null && (
        <div className="mt-4 rounded-lg border border-warning/40 bg-warning/10 p-4">
          <h4 className="font-semibold">Xác nhận điều chỉnh</h4>
          <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-3">
            <div><dt className="text-muted-foreground">Thuốc</dt><dd className="mt-1 font-medium">{drug.drugName}</dd></div>
            <div><dt className="text-muted-foreground">Tồn hiện tại</dt><dd className="mt-1">{drug.stockQuantity}</dd></div>
            <div><dt className="text-muted-foreground">Delta</dt><dd className="mt-1">{parsedQuantity > 0 ? `+${parsedQuantity}` : parsedQuantity}</dd></div>
          </dl>
          <p className="mt-3 text-sm">
            Tồn dự kiến theo snapshot: <strong>{drug.stockQuantity + parsedQuantity}</strong>
          </p>
          <p className="mt-2 text-xs text-muted-foreground">
            Đây chỉ là preview; tồn kho thực tế có thể thay đổi do reservation hoặc giao dịch đồng thời.
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => setConfirming(false)}
              disabled={submitting}
              className="rounded-lg border border-border bg-surface px-4 py-2 font-medium hover:bg-surface-muted disabled:opacity-50"
            >
              Hủy
            </button>
            <button
              type="button"
              onClick={() => void confirmAdjustment()}
              disabled={submitting}
              className="rounded-lg bg-primary px-4 py-2 font-medium text-primary-foreground hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting ? "Đang cập nhật…" : "Xác nhận điều chỉnh"}
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
