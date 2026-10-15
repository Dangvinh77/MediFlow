"use client";

import Link from "next/link";
import { useMemo, useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal, formatInstant } from "@/lib/format";
import { billingApi } from "../api";
import type { LedgerPaymentDTO, LedgerPaymentMethod } from "../types";

const paymentMethodLabels: Record<LedgerPaymentMethod, string> = {
  CASH: "Tiền mặt",
  TRANSFER: "Chuyển khoản",
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
    message: cause instanceof Error ? cause.message : "Không thể hoàn tất thanh toán.",
    correlationId: null,
  };
}

export function LedgerPaymentForm({ requestId }: { requestId: string }) {
  // Giữ cùng một idempotencyKey cho mọi lần thử lại của ĐÚNG lượt thanh toán này — bấm lại sau
  // lỗi mạng tái dùng key cũ (server coi là cùng một giao dịch); tải lại trang mới sinh key mới.
  const idempotencyKey = useMemo(() => crypto.randomUUID(), []);
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("");
  const [paymentMethod, setPaymentMethod] = useState<LedgerPaymentMethod>("CASH");
  const [providerReference, setProviderReference] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<RequestError | null>(null);
  const [result, setResult] = useState<LedgerPaymentDTO | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedCurrency = currency.trim().toUpperCase();
    const parsedAmount = Number(amount);

    if (!/^[A-Z]{3}$/.test(normalizedCurrency)) {
      setValidationError("Mã tiền phải gồm đúng 3 chữ cái (ví dụ VND).");
      return;
    }
    if (!Number.isFinite(parsedAmount) || parsedAmount <= 0) {
      setValidationError("Số tiền phải lớn hơn 0.");
      return;
    }

    setValidationError(null);
    setSaving(true);
    setError(null);

    try {
      const payment = await billingApi.completeLedgerPayment(requestId, {
        idempotencyKey,
        amount: parsedAmount,
        currency: normalizedCurrency,
        paymentMethod,
        providerReference: providerReference.trim() ? providerReference.trim() : null,
      });
      setResult(payment);
    } catch (cause: unknown) {
      setError(getRequestError(cause));
    } finally {
      setSaving(false);
    }
  }

  if (result) {
    return (
      <section className="mt-6 max-w-xl space-y-4 rounded-xl border border-success/40 bg-success/10 p-6">
        <p role="status" className="text-sm font-semibold text-success">Thanh toán thành công.</p>
        <dl className="grid gap-4 text-sm sm:grid-cols-2">
          <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Mã giao dịch</dt><dd className="mt-1 break-all font-mono text-xs">{result.transactionId}</dd></div>
          <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Số tiền</dt><dd className="mt-1">{formatDecimal(result.amount)} {result.currency}</dd></div>
          <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Trạng thái</dt><dd className="mt-1">{result.status}</dd></div>
          <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Hoàn tất lúc</dt><dd className="mt-1">{formatInstant(result.completedAt)}</dd></div>
        </dl>
        <Link href="/billing/payment-requests" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold">Thanh toán khoản phí khác</Link>
      </section>
    );
  }

  return (
    <form onSubmit={submit} noValidate className="mt-6 max-w-xl space-y-5 rounded-xl border border-border bg-surface p-6">
      <p className="break-all text-xs text-muted-foreground">
        Khoản phí (payment request): <span className="font-mono">{requestId}</span>
      </p>

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

      <Field label="Số tiền" htmlFor="ledger-amount" required hint="Khớp đúng số tiền của khoản phí đã chọn.">
        <input
          id="ledger-amount"
          required
          inputMode="decimal"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          className={controlClassName}
        />
      </Field>

      <Field label="Mã tiền (ISO 4217)" htmlFor="ledger-currency" required hint="Ví dụ VND — nhập đúng mã tiền của tài khoản bệnh nhân.">
        <input
          id="ledger-currency"
          required
          maxLength={3}
          value={currency}
          onChange={(event) => setCurrency(event.target.value.toUpperCase())}
          className={controlClassName}
        />
      </Field>

      <Field label="Hình thức" htmlFor="ledger-payment-method" required>
        <select
          id="ledger-payment-method"
          value={paymentMethod}
          onChange={(event) => setPaymentMethod(event.target.value as LedgerPaymentMethod)}
          className={controlClassName}
        >
          {Object.entries(paymentMethodLabels).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
      </Field>

      <Field label="Tham chiếu thanh toán" htmlFor="ledger-provider-reference" hint="Tuỳ chọn — mã biên lai/chuyển khoản, tối đa 120 ký tự.">
        <input
          id="ledger-provider-reference"
          maxLength={120}
          value={providerReference}
          onChange={(event) => setProviderReference(event.target.value)}
          className={controlClassName}
        />
      </Field>

      <Button type="submit" variant="primary" loading={saving}>Xác nhận thanh toán</Button>
    </form>
  );
}
