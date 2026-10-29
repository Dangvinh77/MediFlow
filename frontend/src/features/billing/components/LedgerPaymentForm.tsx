"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import Link from "next/link";
import { useRef, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal } from "@/lib/format";
import { BillingSessionBoundary } from "./BillingSessionBoundary";
import { billingApi } from "../api";
import type { CompleteLedgerPaymentRequest, LedgerPaymentDTO, LedgerPaymentMethod } from "../types";

const methodLabels: Record<LedgerPaymentMethod, string> = { CASH: "Tiền mặt", TRANSFER: "Chuyển khoản" };

interface LedgerPaymentFormProps {
  requestId: string;
  remainingAmount?: number;
  currency?: string;
  onLockChange?: (locked: boolean) => void;
  onCompleted?: (payment: LedgerPaymentDTO) => void;
}

export function LedgerPaymentForm(props: LedgerPaymentFormProps) {
  return (
    <BillingSessionBoundary identity={`ledger:${props.requestId}`}>
      <LedgerPaymentFormSession {...props} />
    </BillingSessionBoundary>
  );
}

function LedgerPaymentFormSession({ requestId, remainingAmount, currency, onLockChange, onCompleted }: LedgerPaymentFormProps) {
  const [amount, setAmount] = useState(remainingAmount === undefined ? "" : String(remainingAmount));
  const [method, setMethod] = useState<LedgerPaymentMethod>("CASH");
  const [providerReference, setProviderReference] = useState("");
  const [pendingRetry, setPendingRetry] = useState<CompleteLedgerPaymentRequest | null>(null);
  const [result, setResult] = useState<LedgerPaymentDTO | null>(null);
  const [error, setError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submitLock = useRef(false);

  const hasSnapshot = Number.isFinite(remainingAmount) && (remainingAmount ?? 0) > 0 && Boolean(currency?.trim());

  async function runPayment(body: CompleteLedgerPaymentRequest) {
    if (submitLock.current) return;
    submitLock.current = true;
    onLockChange?.(true);
    setSubmitting(true);
    setError(null);
    try {
      const payment = await billingApi.completeLedgerPayment(requestId, body);
      setPendingRetry(null);
      setResult(payment);
      onLockChange?.(payment.status === "PENDING");
      if (payment.status === "COMPLETED") {
        setError(null);
        onCompleted?.(payment);
      } else if (payment.status === "PENDING") {
        setError({ message: "Giao dịch đang chờ xác nhận. Hãy tra cứu lại khoản phí để kiểm tra trạng thái trước khi thanh toán tiếp.", correlationId: null });
      } else {
        setError({ message: "Giao dịch chưa hoàn tất. Hãy tra cứu lại khoản phí trước khi tạo giao dịch mới.", correlationId: null });
      }
    } catch (cause: unknown) {
      const uncertain = !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500;
      setPendingRetry(uncertain ? body : null);
      onLockChange?.(uncertain);
      const message = uncertain
        ? "Chưa xác nhận được kết quả thanh toán. Hãy thử lại đúng giao dịch đã gửi để kiểm tra kết quả."
        : cause instanceof Error ? cause.message : "Không thể thanh toán khoản phí.";
      setError({ message, correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    } finally {
      submitLock.current = false;
      setSubmitting(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitLock.current || pendingRetry || result !== null || !hasSnapshot) return;
    const amountText = amount.trim();
    if (!/^\d+(?:\.\d{1,2})?$/.test(amountText)) {
      setError({ message: "Số tiền phải lớn hơn 0 và có tối đa 2 chữ số thập phân.", correlationId: null });
      return;
    }
    const cents = Math.round(Number(amountText) * 100);
    const remainingCents = Math.round((remainingAmount as number) * 100);
    if (!Number.isSafeInteger(cents) || cents <= 0 || !Number.isSafeInteger(remainingCents) || cents > remainingCents) {
      setError({ message: `Số tiền phải lớn hơn 0 và không vượt quá số dư ${formatDecimal(remainingAmount)} ${currency}.`, correlationId: null });
      return;
    }
    const reference = providerReference.trim();
    void runPayment({
      idempotencyKey: crypto.randomUUID(),
      amount: cents / 100,
      currency: currency as string,
      paymentMethod: method,
      providerReference: reference || null,
    });
  }

  if (!hasSnapshot) {
    return (
      <p role="alert" className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
        Cần tra cứu khoản phí nguồn để lấy số dư và mã tiền do Billing xác nhận trước khi thanh toán. <Link className="font-semibold underline" href="/billing/payment-requests">Tra cứu khoản phí</Link>
      </p>
    );
  }

  const lockedToRetry = pendingRetry !== null;

  return (
    <form onSubmit={submit} noValidate className="mt-4 space-y-4 border-t border-border pt-4">
      <p className="text-sm text-muted-foreground">Số dư theo Billing: <strong>{formatDecimal(remainingAmount)} {currency}</strong></p>
      {error ? (
        <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
          {error.message}{error.correlationId ? <span className="mt-1 block text-xs">Mã theo dõi: <span className="font-mono">{error.correlationId}</span></span> : null}
        </p>
      ) : null}
      {result?.status === "COMPLETED" ? (
        <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">
          Thanh toán thành công. Mã giao dịch: <span className="font-mono"><ReferenceText value={result.transactionId} fallback="Giao dịch" /></span>
        </p>
      ) : null}
      {result?.status === "PENDING" ? (
        <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
          Giao dịch đang chờ xác nhận. Mã giao dịch: <span className="font-mono"><ReferenceText value={result.transactionId} fallback="Giao dịch" /></span>
        </p>
      ) : null}
      {result && result.status !== "COMPLETED" ? (
        <p className="text-sm text-muted-foreground"><Link className="font-semibold underline" href="/billing/payment-requests">Tra cứu lại trạng thái khoản phí</Link> trước khi gửi giao dịch khác.</p>
      ) : null}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Số tiền" htmlFor="ledger-payment-amount" required hint={`Tối đa ${formatDecimal(remainingAmount)} ${currency}`}>
          <input id="ledger-payment-amount" inputMode="decimal" value={amount} disabled={submitting || lockedToRetry || result !== null} onChange={(event) => { setAmount(event.target.value); setError(null); setResult(null); }} className={controlClassName} />
        </Field>
        <Field label="Mã tiền (ISO 4217)" htmlFor="ledger-payment-currency" required>
          <input id="ledger-payment-currency" value={currency} readOnly disabled={submitting || lockedToRetry || result !== null} className={controlClassName} />
        </Field>
        <Field label="Hình thức" htmlFor="ledger-payment-method" required>
          <select id="ledger-payment-method" value={method} disabled={submitting || lockedToRetry || result !== null} onChange={(event) => { setMethod(event.target.value as LedgerPaymentMethod); setError(null); setResult(null); }} className={controlClassName}>
            {Object.entries(methodLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </Field>
        <Field label="Mã tham chiếu" htmlFor="ledger-payment-reference" hint="Dùng cho chuyển khoản nếu có.">
          <input id="ledger-payment-reference" value={providerReference} disabled={submitting || lockedToRetry || result !== null} onChange={(event) => { setProviderReference(event.target.value); setError(null); setResult(null); }} className={controlClassName} />
        </Field>
      </div>
      {pendingRetry ? (
        <Button type="button" variant="primary" loading={submitting} onClick={() => void runPayment(pendingRetry)}>Thử lại cùng giao dịch</Button>
      ) : result !== null ? null : (
        <Button type="submit" variant="primary" loading={submitting}>Xác nhận thanh toán</Button>
      )}
    </form>
  );
}
