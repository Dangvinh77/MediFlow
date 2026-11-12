"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


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
  const { localize } = useLocale();
  const [method, setMethod] = useState<"CASH" | "BANK_TRANSFER">("CASH");
  const [recovery, setRecovery] = useState<LabPaymentRecovery | null>(null);
  const [recoveryLoading, setRecoveryLoading] = useState(true);
  const [result, setResult] = useState<LedgerPaymentDTO | null>(null);
  const [resultQuoteId, setResultQuoteId] = useState<string | null>(null);
  const [error, setError] = useState<CommandError | null>(null);
  const [busy, setBusy] = useState(false);
  const submitLock = useRef(false);
  const mounted = useRef(true);
  const originatingSession = useRef(getSessionKey());

  useEffect(() => {
    let active = true;
    mounted.current = true;
    void readLabPaymentRecovery(paymentRequestId, originatingSession.current).then((pending) => {
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
    return () => { active = false; mounted.current = false; };
  }, [onLockChange, paymentRequestId]);

  async function submit(selected: LabPaymentRecovery) {
    if (submitLock.current || recoveryLoading) return;
    const actorSession = originatingSession.current;
    const current = () => mounted.current && actorSession === getSessionKey();
    if (!current()) return;
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
      intent = await prepareCommandIntent(`lab-payment:${paymentRequestId}`, actorSession, selected);
      if (!current()) { intent.cancelUnsent(); return; }
      try {
        await saveLabPaymentRecovery(paymentRequestId, actorSession, selected);
      } catch (cause) {
        intent.cancelUnsent();
        recoveryWriteFailed = true;
        throw cause;
      }
      if (!current()) {
        // An aborted replay says nothing about the original command's outcome.
        if (!recovery) await clearLabPaymentRecovery(paymentRequestId, actorSession);
        intent.cancelUnsent(); return;
      }
      requestSent = true;
      const payment = await billingApi.createLabPayment(paymentRequestId, selected, {
        idempotencyKey: intent.key,
        correlationId: intent.correlationId,
      });
      if (payment.status === "PENDING") {
        if (!current()) return;
        setRecovery(selected);
        setResult(payment);
        setResultQuoteId(selected.quoteId);
        setError({ message: "Giao dịch đang chờ xác nhận. Gửi lại cùng yêu cầu để kiểm tra trạng thái; không tạo giao dịch mới.", correlationId: null, uncertain: true });
        onLockChange?.(true);
        return;
      }
      await clearLabPaymentRecovery(paymentRequestId, actorSession);
      intent.settle();
      if (!current()) return;
      setRecovery(null);
      setResult(payment);
      setResultQuoteId(selected.quoteId);
      if (payment.status === "COMPLETED") {
        onLockChange?.(false);
        onCompleted?.(payment);
      } else {
        onLockChange?.(false);
        setError({ message: "Giao dịch chưa hoàn tất. Tra cứu lại yêu cầu phí trước khi tạo giao dịch khác.", correlationId: null, uncertain: false });
      }
    } catch (cause: unknown) {
      const unresolved = recoveryWriteFailed ? Boolean(recovery) : hasUnknownOutcome(cause);
      if (intent) intent.settle(unresolved ? new Error("Unknown Lab payment outcome", { cause }) : cause);
      if (!current()) return;
      if (recoveryWriteFailed) {
        setRecovery(recovery);
        onLockChange?.(Boolean(recovery));
      } else if (unresolved && requestSent) {
        setRecovery(selected);
        onLockChange?.(true);
      } else if (!unresolved) {
        try {
          await clearLabPaymentRecovery(paymentRequestId, actorSession);
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
    return <div className="space-y-2 rounded-lg border border-success/40 bg-success/10 p-3 text-sm text-success">
      <p role="status"><L10n>{"Thanh toán thành công. Đã ghi nhận "}</L10n><L10nValue>{formatDecimal(result.amount)}</L10nValue> {result.currency}.</p>
      <PaymentSupport payment={result} paymentRequestId={paymentRequestId} quoteId={resultQuoteId ?? quote.quoteId} />
    </div>;
  }

  if (result?.status === "FAILED") {
    return <div className="space-y-2 rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">
      <p role="status"><L10n>{"Giao dịch chưa hoàn tất. Tra cứu lại yêu cầu phí trước khi tạo giao dịch khác."}</L10n></p>
      <PaymentSupport payment={result} paymentRequestId={paymentRequestId} quoteId={resultQuoteId ?? quote.quoteId} />
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
      <h3 className="text-sm font-semibold"><L10n>{"Thu nghĩa vụ theo báo giá"}</L10n></h3>
      <p className="mt-1 text-sm text-muted-foreground"><L10n>{"Chỉ thu đúng số tiền người bệnh phải trả trong báo giá hiện hành; không cho phép nhập khoản thu một phần ở luồng LAB V2."}</L10n></p>
    </div>
    <dl className="grid gap-3 text-sm sm:grid-cols-2">
      <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Số tiền"}</L10n></dt><dd className="mt-1 font-semibold"><L10nValue>{formatDecimal(activeCommand.amount)}</L10nValue> {activeCommand.currency}</dd></div>
      <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground"><L10n>{"Hình thức"}</L10n></dt><dd className="mt-1"><L10nValue>{activeCommand.paymentMethod === "CASH" ? "Tiền mặt" : "Chuyển khoản"}</L10nValue></dd></div>
    </dl>
    <L10nValue>{error ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">
      <p><L10nValue>{error.message}</L10nValue></p>
      <L10nValue>{error.correlationId ? <details className="mt-2 text-xs"><summary className="min-h-8 cursor-pointer"><L10n>{"Mã theo dõi hỗ trợ"}</L10n></summary><p className="mt-1 break-all font-mono">{error.correlationId}</p></details> : null}</L10nValue>
      <L10nValue>{error.uncertain ? <p className="mt-2"><L10n>{"Giữ nguyên báo giá và yêu cầu thu; gửi lại đúng lệnh hiện tại để kiểm tra kết quả."}</L10n></p> : null}</L10nValue>
    </div> : null}</L10nValue>
    <L10nValue>{result ? <PaymentSupport payment={result} paymentRequestId={paymentRequestId} quoteId={resultQuoteId ?? quote.quoteId} /> : null}</L10nValue>
    <L10nValue>{recoveryLoading ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Đang kiểm tra giao dịch thu đang chờ…"}</L10n></p> : null}</L10nValue>
    <L10nValue>{!awaitingOutcome && !recoveryLoading ? <Field label={localize("Hình thức thanh toán")} htmlFor="lab-quote-payment-method" required>
      <select id="lab-quote-payment-method" value={method} onChange={(event) => { setMethod(event.target.value as "CASH" | "BANK_TRANSFER"); setError(null); }} disabled={busy} className={controlClassName}>
        <option value="CASH"><L10n>{"Tiền mặt"}</L10n></option>
        <option value="BANK_TRANSFER"><L10n>{"Chuyển khoản"}</L10n></option>
      </select>
    </Field> : null}</L10nValue>
    <L10nValue>{recovery ? <Button type="button" variant="primary" loading={busy} disabled={recoveryLoading || busy} onClick={() => void submit(recovery)}>
      <L10nValue>{result?.status === "PENDING" ? "Kiểm tra lại cùng giao dịch" : "Gửi lại cùng yêu cầu thu"}</L10nValue>
    </Button> : <Button type="button" variant="primary" loading={busy} disabled={!quoteCurrent || recoveryLoading || busy || awaitingOutcome} onClick={() => void submit(activeCommand)}><L10n>{"Xác nhận thu theo báo giá"}</L10n></Button>}</L10nValue>
    <L10nValue>{!recovery && !quoteCurrent && !recoveryLoading ? <p role="alert" className="text-sm text-danger"><L10n>{"Báo giá đã hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi gửi lệnh thu."}</L10n></p> : null}</L10nValue>
  </section>;
}

function PaymentSupport({ payment, paymentRequestId, quoteId }: { payment: LedgerPaymentDTO; paymentRequestId: string; quoteId: string }) {
  return <details className="text-xs text-muted-foreground">
    <summary className="min-h-8 cursor-pointer"><L10n>{"Thông tin đối soát giao dịch"}</L10n></summary>
    <dl className="mt-2 space-y-1 break-all"><dt><L10n>{"Mã yêu cầu"}</L10n></dt><dd className="font-mono"><L10nValue>{paymentRequestId}</L10nValue></dd><dt><L10n>{"Mã báo giá"}</L10n></dt><dd className="font-mono"><L10nValue>{quoteId}</L10nValue></dd><dt><L10n>{"Mã giao dịch"}</L10n></dt><dd className="font-mono">{payment.transactionId}</dd></dl>
  </details>;
}
