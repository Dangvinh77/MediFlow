"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { CommandIntentError, prepareCommandIntent } from "@/lib/command-intent";
import { getSessionKey } from "@/lib/session";
import { formatDecimal } from "@/lib/format";
import { billingApi } from "../api";
import { clearLabPaymentRecovery, readLabPaymentRecovery, saveLabPaymentRecovery, type LabPaymentRecovery } from "../lab-payment-recovery";
import type { LedgerPaymentDTO, OutpatientQuoteDTO } from "../types";

interface LabQuotePaymentActionProps {
  paymentRequestId: string;
  quote: OutpatientQuoteDTO;
  quoteCurrent: boolean;
  onLockChange?: (locked: boolean) => void;
  onCompleted?: (payment: LedgerPaymentDTO) => void;
}

interface CommandError {
  message: string;
  correlationId: string | null;
  uncertain: boolean;
}

function hasUnknownOutcome(cause: unknown): boolean {
  return cause instanceof CommandIntentError
    || !(cause instanceof ApiRequestError)
    || cause.status < 400
    || cause.status === 408
    || cause.status >= 500
    || cause.code?.includes("IDEMPOTENCY_CONFLICT") === true
    || cause.code?.endsWith("IDEMPOTENCY_KEY_REUSED") === true;
}

function isPositiveDecimal(value: string): boolean {
  return /^\d+(?:\.\d+)?$/.test(value) && Number.isFinite(Number(value)) && Number(value) > 0;
}

export function LabQuotePaymentAction(props: LabQuotePaymentActionProps) {
  return <LabQuotePaymentActionSession key={`${props.paymentRequestId}:${props.quote.quoteId}`} {...props} />;
}

function LabQuotePaymentActionSession({ paymentRequestId, quote, quoteCurrent, onLockChange, onCompleted }: LabQuotePaymentActionProps) {
  const [method, setMethod] = useState<"CASH" | "BANK_TRANSFER">("CASH");
  const [recovery, setRecovery] = useState<LabPaymentRecovery | null>(null);
  const [recoveryLoading, setRecoveryLoading] = useState(true);
  const [result, setResult] = useState<LedgerPaymentDTO | null>(null);
  const [error, setError] = useState<CommandError | null>(null);
  const [busy, setBusy] = useState(false);
  const submitLock = useRef(false);

  useEffect(() => {
    let active = true;
    void readLabPaymentRecovery(paymentRequestId, getSessionKey()).then((pending) => {
      if (!active) return;
      setRecovery(pending);
      setRecoveryLoading(false);
      if (pending) onLockChange?.(true);
    }).catch((cause: unknown) => {
      if (!active) return;
      setError({ message: cause instanceof Error ? cause.message : "Không thể đọc giao dịch đang chờ.", correlationId: null, uncertain: true });
      setRecoveryLoading(false);
      onLockChange?.(true);
    });
    return () => { active = false; };
  }, [onLockChange, paymentRequestId]);

  async function submit(selected: LabPaymentRecovery) {
    if (submitLock.current || recoveryLoading) return;
    if (!recovery && !quoteCurrent) {
      setError({ message: "Báo giá đã hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi tiếp tục.", correlationId: null, uncertain: false });
      return;
    }
    if (!isPositiveDecimal(selected.amount) || !selected.quoteId || !selected.currency) {
      setError({ message: "Báo giá không có đủ số tiền, mã tiền và mã báo giá hợp lệ để thu.", correlationId: null, uncertain: false });
      return;
    }

    submitLock.current = true;
    setBusy(true);
    setError(null);
    onLockChange?.(true);
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | null = null;
    let requestSent = false;
    let recoveryWriteFailed = false;
    try {
      intent = await prepareCommandIntent(`lab-payment:${paymentRequestId}`, getSessionKey(), selected);
      try {
        await saveLabPaymentRecovery(paymentRequestId, getSessionKey(), selected);
      } catch (cause) {
        intent.cancelUnsent();
        recoveryWriteFailed = true;
        throw cause;
      }
      requestSent = true;
      const payment = await billingApi.createLabPayment(paymentRequestId, selected, {
        idempotencyKey: intent.key,
        correlationId: intent.correlationId,
      });
      if (payment.status === "PENDING") {
        setRecovery(selected);
        setResult(payment);
        setError({ message: "Giao dịch đang chờ xác nhận. Gửi lại cùng yêu cầu để kiểm tra trạng thái; không tạo giao dịch mới.", correlationId: null, uncertain: true });
        onLockChange?.(true);
        return;
      }
      await clearLabPaymentRecovery(paymentRequestId, getSessionKey());
      intent.settle();
      setRecovery(null);
      setResult(payment);
      if (payment.status === "COMPLETED") {
        onLockChange?.(false);
        onCompleted?.(payment);
      } else {
        onLockChange?.(false);
        setError({ message: "Giao dịch chưa hoàn tất. Tra cứu lại yêu cầu phí trước khi tạo giao dịch khác.", correlationId: null, uncertain: false });
      }
    } catch (cause: unknown) {
      const unresolved = recoveryWriteFailed ? false : hasUnknownOutcome(cause);
      if (intent) intent.settle(unresolved ? cause instanceof Error ? cause : new Error("Unknown Lab payment outcome") : cause);
      if (recoveryWriteFailed) {
        setRecovery(null);
        onLockChange?.(false);
      } else if (unresolved && requestSent) {
        setRecovery(selected);
        onLockChange?.(true);
      } else if (!unresolved) {
        try {
          await clearLabPaymentRecovery(paymentRequestId, getSessionKey());
          setRecovery(null);
          onLockChange?.(false);
        } catch {
          setRecovery(selected);
          onLockChange?.(true);
        }
      } else {
        onLockChange?.(true);
      }
      const message = recoveryWriteFailed
        ? cause instanceof Error ? cause.message : "Chưa gửi thanh toán vì không thể lưu yêu cầu đang chờ."
        : cause instanceof Error ? cause.message : "Chưa xác định được kết quả thanh toán.";
      setError({
        message,
        correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
        uncertain: unresolved || (!recoveryWriteFailed && requestSent),
      });
    } finally {
      submitLock.current = false;
      setBusy(false);
    }
  }

  if (result?.status === "COMPLETED") {
    return <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-3 text-sm text-success">
      Thanh toán thành công. Mã giao dịch: <span className="font-mono">{result.transactionId}</span>
    </p>;
  }

  if (result?.status === "FAILED") {
    return <div className="space-y-2 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">
      <p role="status">Giao dịch chưa hoàn tất. Tra cứu lại yêu cầu phí trước khi tạo giao dịch khác.</p>
      <p className="break-all font-mono text-xs">Mã giao dịch: {result.transactionId}</p>
    </div>;
  }

  const activeCommand = recovery ?? {
    quoteId: quote.quoteId,
    amount: quote.patientPayable,
    currency: quote.currency,
    paymentMethod: method,
  };
  const awaitingOutcome = recovery !== null || result?.status === "PENDING" || error?.uncertain === true;

  return <section className="space-y-4 rounded-lg border border-border p-4">
    <div>
      <h3 className="text-sm font-semibold">Thu nghĩa vụ theo báo giá</h3>
      <p className="mt-1 text-sm text-muted-foreground">Chỉ thu đúng số tiền người bệnh phải trả trong báo giá hiện hành; không cho phép nhập khoản thu một phần ở luồng LAB V2.</p>
    </div>
    <dl className="grid gap-3 text-sm sm:grid-cols-3">
      <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Số tiền</dt><dd className="mt-1 font-semibold">{formatDecimal(activeCommand.amount)} {activeCommand.currency}</dd></div>
      <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Mã báo giá</dt><dd className="mt-1 break-all font-mono">{activeCommand.quoteId}</dd></div>
      <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Hình thức</dt><dd className="mt-1">{activeCommand.paymentMethod === "CASH" ? "Tiền mặt" : "Chuyển khoản"}</dd></div>
    </dl>
    {error ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
      <p>{error.message}</p>
      {error.correlationId ? <p className="mt-1 text-xs">Mã theo dõi: <span className="font-mono">{error.correlationId}</span></p> : null}
      {error.uncertain ? <p className="mt-2">Giữ nguyên báo giá và yêu cầu thu; gửi lại đúng lệnh hiện tại để kiểm tra kết quả.</p> : null}
    </div> : null}
    {recoveryLoading ? <p role="status" className="text-sm text-muted-foreground">Đang kiểm tra giao dịch thu đang chờ…</p> : null}
    {!awaitingOutcome && !recoveryLoading ? <Field label="Hình thức thanh toán" htmlFor="lab-quote-payment-method" required>
      <select id="lab-quote-payment-method" value={method} onChange={(event) => { setMethod(event.target.value as "CASH" | "BANK_TRANSFER"); setError(null); }} disabled={busy} className={controlClassName}>
        <option value="CASH">Tiền mặt</option>
        <option value="BANK_TRANSFER">Chuyển khoản</option>
      </select>
    </Field> : null}
    {recovery ? <Button type="button" variant="primary" loading={busy} disabled={recoveryLoading || busy} onClick={() => void submit(recovery)}>
      {result?.status === "PENDING" ? "Kiểm tra lại cùng giao dịch" : "Gửi lại cùng yêu cầu thu"}
    </Button> : <Button type="button" variant="primary" loading={busy} disabled={!quoteCurrent || recoveryLoading || busy || awaitingOutcome} onClick={() => void submit(activeCommand)}>
      Xác nhận thu theo báo giá
    </Button>}
    {!recovery && !quoteCurrent && !recoveryLoading ? <p role="alert" className="text-sm text-danger">Báo giá đã hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi gửi lệnh thu.</p> : null}
  </section>;
}
