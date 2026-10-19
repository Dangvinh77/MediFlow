"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal, formatInstant } from "@/lib/format";
import { billingApi } from "../api";
import type { LedgerPaymentMethod, LedgerRefundDTO, PrescriptionAdjustmentDTO, RefundDueDTO } from "../types";

const paymentMethodLabels: Record<LedgerPaymentMethod, string> = {
  CASH: "Tiền mặt",
  TRANSFER: "Chuyển khoản",
};

const statusLabels: Record<string, string> = {
  PENDING: "Đang chờ áp dụng",
  APPLIED: "Đã áp dụng",
  REJECTED: "Bị từ chối",
};

interface RequestError {
  message: string;
  correlationId: string | null;
}

function getRequestError(cause: unknown): RequestError {
  if (cause instanceof ApiRequestError) {
    return { message: cause.message, correlationId: cause.correlationId };
  }
  return {
    message: cause instanceof Error ? cause.message : "Không thể tải điều chỉnh đơn thuốc.",
    correlationId: null,
  };
}

export function PrescriptionAdjustmentRefundDue({ prescriptionId }: { prescriptionId: string }) {
  const router = useRouter();
  const [adjustment, setAdjustment] = useState<PrescriptionAdjustmentDTO | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState<RequestError | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const [lastRefund, setLastRefund] = useState<LedgerRefundDTO | null>(null);

  const retry = useCallback(() => setRetryToken((value) => value + 1), []);

  useEffect(() => {
    let mounted = true;
    setTimeout(() => {
      if (!mounted) return;
      setError(null);
      setNotFound(false);
      billingApi.getPrescriptionAdjustment(prescriptionId)
        .then((value) => {
          if (!mounted) return;
          setAdjustment(value);
        })
        .catch((cause: unknown) => {
          if (!mounted) return;
          if (cause instanceof ApiRequestError && cause.status === 401) {
            router.replace("/login");
            return;
          }
          if (cause instanceof ApiRequestError && cause.status === 404) {
            setAdjustment(null);
            setNotFound(true);
            return;
          }
          setAdjustment(null);
          setError(getRequestError(cause));
        });
    }, 0);
    return () => {
      mounted = false;
    };
  }, [prescriptionId, retryToken, router]);

  if (error) {
    return <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={retry} />;
  }

  if (notFound) {
    return (
      <section className="mt-6 max-w-xl space-y-4 rounded-xl border border-border bg-surface p-6">
        <p role="status" className="text-sm text-muted-foreground">
          Không tìm thấy điều chỉnh nào cho đơn thuốc này — đơn thuốc chưa bị từ chối hoặc huỷ sau
          khi thu tiền.
        </p>
        <Link href="/billing/prescription-adjustments" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold">
          Tra cứu đơn khác
        </Link>
      </section>
    );
  }

  if (!adjustment) {
    return <AsyncState kind="loading" message="Đang tải điều chỉnh đơn thuốc…" />;
  }

  return (
    <section className="mt-6 space-y-6">
      {lastRefund ? (
        <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">
          Đã ghi nhận hoàn tiền {formatDecimal(lastRefund.amount)} {lastRefund.currency} lúc{" "}
          {formatInstant(lastRefund.completedAt)}.
        </p>
      ) : null}

      <dl className="grid gap-5 rounded-xl border border-border bg-surface p-6 sm:grid-cols-2 lg:grid-cols-3">
        <Info label="Đơn thuốc" value={adjustment.prescriptionId} mono />
        <Info label="Loại sự kiện" value={adjustment.eventType} />
        <div>
          <dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Trạng thái</dt>
          <dd className="mt-1">
            <StatusBadge tone={adjustment.status === "REJECTED" ? "danger" : adjustment.status === "APPLIED" ? "success" : "warning"}>
              {statusLabels[adjustment.status] ?? adjustment.status}
            </StatusBadge>
          </dd>
        </div>
        <Info label="Mã từ chối" value={adjustment.rejectionCode} mono />
      </dl>

      {adjustment.refundsDue.length === 0 ? (
        <p className="rounded-xl border border-border bg-surface p-6 text-sm text-muted-foreground">
          Không còn khoản nào cần hoàn tiền cho đơn thuốc này.
        </p>
      ) : (
        <ul className="space-y-4">
          {adjustment.refundsDue.map((due) => (
            <RefundDueRow key={due.originalTransactionId} due={due} onRefunded={(refund) => { setLastRefund(refund); retry(); }} />
          ))}
        </ul>
      )}

      <Link href="/billing/prescription-adjustments" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold">
        Tra cứu đơn khác
      </Link>
    </section>
  );
}

function RefundDueRow({ due, onRefunded }: { due: RefundDueDTO; onRefunded: (refund: LedgerRefundDTO) => void }) {
  const idempotencyKey = useMemo(() => crypto.randomUUID(), []);
  const [open, setOpen] = useState(false);
  const [amount, setAmount] = useState(String(due.amount));
  const [reason, setReason] = useState("");
  const [paymentMethod, setPaymentMethod] = useState<LedgerPaymentMethod>("CASH");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<RequestError | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsedAmount = Number(amount);
    const trimmedReason = reason.trim();

    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      setValidationError("Số tiền hoàn phải lớn hơn 0.");
      return;
    }
    if (parsedAmount > due.amount) {
      setValidationError(`Số tiền hoàn không được vượt quá khoản còn phải trả (${formatDecimal(due.amount)} ${due.currency}).`);
      return;
    }
    if (!trimmedReason) {
      setValidationError("Lý do hoàn tiền là bắt buộc.");
      return;
    }
    if (trimmedReason.length > 500) {
      setValidationError("Lý do hoàn tiền tối đa 500 ký tự.");
      return;
    }

    setValidationError(null);
    setSaving(true);
    setError(null);

    try {
      const refund = await billingApi.refundLedgerPayment(due.originalTransactionId, {
        idempotencyKey,
        amount: parsedAmount,
        reason: trimmedReason,
        paymentMethod,
      });
      onRefunded(refund);
    } catch (cause: unknown) {
      setError(getRequestError(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <li className="rounded-xl border border-border bg-surface p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Giao dịch gốc</p>
          <p className="mt-1 break-all font-mono text-xs">{due.originalTransactionId}</p>
          <p className="mt-2 text-lg font-semibold">{formatDecimal(due.amount)} {due.currency}</p>
        </div>
        {!open ? (
          <Button type="button" variant="secondary" onClick={() => setOpen(true)}>Hoàn tiền</Button>
        ) : null}
      </div>

      {open ? (
        <form onSubmit={submit} noValidate className="mt-5 max-w-lg space-y-4 border-t border-border pt-5">
          {error ? (
            <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
              {error.message}
              {error.correlationId ? (
                <span className="mt-1 block text-xs text-muted-foreground">Mã theo dõi: <span className="font-mono">{error.correlationId}</span></span>
              ) : null}
            </p>
          ) : null}
          {validationError ? (
            <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{validationError}</p>
          ) : null}

          <Field label={`Số tiền hoàn (${due.currency})`} htmlFor={`refund-amount-${due.originalTransactionId}`} required>
            <input
              id={`refund-amount-${due.originalTransactionId}`}
              required
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              className={controlClassName}
            />
          </Field>

          <Field label="Hình thức" htmlFor={`refund-method-${due.originalTransactionId}`} required>
            <select
              id={`refund-method-${due.originalTransactionId}`}
              value={paymentMethod}
              onChange={(event) => setPaymentMethod(event.target.value as LedgerPaymentMethod)}
              className={controlClassName}
            >
              {Object.entries(paymentMethodLabels).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </Field>

          <Field label="Lý do hoàn tiền" htmlFor={`refund-reason-${due.originalTransactionId}`} required hint="Tối đa 500 ký tự.">
            <textarea
              id={`refund-reason-${due.originalTransactionId}`}
              required
              maxLength={500}
              rows={3}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              className={controlClassName}
            />
          </Field>

          <div className="flex gap-3">
            <Button type="submit" variant="primary" loading={saving}>Xác nhận hoàn tiền</Button>
            <Button type="button" variant="secondary" onClick={() => setOpen(false)} disabled={saving}>Huỷ</Button>
          </div>
        </form>
      ) : null}
    </li>
  );
}

function Info({ label, value, mono = false }: { label: string; value: string | null; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt>
      <dd className={`mt-1 break-all text-sm ${mono ? "font-mono" : ""}`}>{value ?? "—"}</dd>
    </div>
  );
}
