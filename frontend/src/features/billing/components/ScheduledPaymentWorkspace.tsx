"use client";

import { useEffect, useRef, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/session";
import { prepareCommandIntent, CommandIntentError } from "@/lib/command-intent";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { formatDecimal, formatInstant } from "@/lib/format";
import { billingApi } from "../api";
import { clearScheduledPaymentRecovery, readScheduledPaymentRecovery, saveScheduledPaymentRecovery } from "../payment-recovery";
import { ReferenceText } from "@/components/references/ReferenceText";
import { ScheduledPaymentQueue } from "./ScheduledPaymentQueue";
import { OutpatientQuoteBreakdown } from "./OutpatientQuoteBreakdown";
import type { OutpatientQuoteDTO, RegistrationPaymentRequestDTO, ScheduledPaymentCommand, ScheduledPaymentResult, ZeroPayableEligibilityRequest } from "../types";

interface WorkspaceError {
  message: string;
  correlationId: string | null;
  uncertain?: boolean;
}

const positiveMoneyPattern = /^(?:[1-9][0-9]{0,16}\.\d{2}|0\.(?:0[1-9]|[1-9]\d))$/;

type ScheduledIntent =
  | { kind: "PATIENT_PAYMENT"; body: ScheduledPaymentCommand }
  | { kind: "ZERO_LIABILITY"; body: ZeroPayableEligibilityRequest };

interface ScheduledPaymentWorkspaceProps {
  enabled: boolean;
  quoteEnabled?: boolean;
  zeroLiabilityEnabled?: boolean;
}

function isZeroAmount(value: string): boolean {
  return /^0+(?:\.0+)?$/.test(value);
}

function errorFrom(cause: unknown, fallback: string): WorkspaceError {
  return cause instanceof ApiRequestError
    ? { message: cause.message, correlationId: cause.correlationId }
    : { message: cause instanceof Error ? cause.message : fallback, correlationId: null };
}

function isUncertain(cause: unknown): boolean {
  return !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500
    || cause.code?.includes("IDEMPOTENCY_CONFLICT") === true || cause.code?.endsWith("IDEMPOTENCY_KEY_REUSED") === true;
}

function terminalResult(result: ScheduledPaymentResult | null) {
  return result?.operation.status === "COMPLETED" || result?.operation.status === "CLOSED_NOT_PAID";
}

export function ScheduledPaymentWorkspace({ enabled, quoteEnabled = false, zeroLiabilityEnabled = false }: ScheduledPaymentWorkspaceProps) {
  const [registrationId, setRegistrationId] = useState("");
  const [manualLookupOpen, setManualLookupOpen] = useState(true);
  const [request, setRequest] = useState<RegistrationPaymentRequestDTO | null>(null);
  const [lookupError, setLookupError] = useState<WorkspaceError | null>(null);
  const [lookupBusy, setLookupBusy] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<"CASH" | "BANK_TRANSFER">("CASH");
  const [providerReference, setProviderReference] = useState("");
  const [paymentError, setPaymentError] = useState<WorkspaceError | null>(null);
  const [paymentBusy, setPaymentBusy] = useState(false);
  const [paymentResult, setPaymentResult] = useState<ScheduledPaymentResult | null>(null);
  const [paidFromRequest, setPaidFromRequest] = useState(false);
  const [checkingOperation, setCheckingOperation] = useState(false);
  const [lastIntent, setLastIntent] = useState<ScheduledIntent | null>(null);
  const [recoveredOperationId, setRecoveredOperationId] = useState<string | null>(null);
  const [quote, setQuote] = useState<OutpatientQuoteDTO | null>(null);
  const [quoteError, setQuoteError] = useState<WorkspaceError | null>(null);
  const [quoteUnavailable, setQuoteUnavailable] = useState(false);
  const [quoteBusy, setQuoteBusy] = useState(false);
  const quoteGeneration = useRef(0);
  const lookupGeneration = useRef(0);
  const lookupLock = useRef(false);
  const paymentLock = useRef(false);
  const operationCheckLock = useRef(false);
  const requestRef = useRef<RegistrationPaymentRequestDTO | null>(request);
  const [clientNow, setClientNow] = useState(0);
  const uncertain = paymentError?.uncertain === true;
  const pending = paymentResult?.operation.status === "PREPARING" || recoveredOperationId !== null;

  useEffect(() => {
    const firstTick = window.setTimeout(() => setClientNow(Date.now()), 0);
    const interval = window.setInterval(() => setClientNow(Date.now()), 15_000);
    return () => { window.clearTimeout(firstTick); window.clearInterval(interval); };
  }, []);

  async function loadQuote(paymentRequestId: string) {
    if (requestRef.current?.paymentRequestId !== paymentRequestId) return;
    const generation = ++quoteGeneration.current;
    setQuote(null);
    setQuoteError(null);
    setQuoteUnavailable(false);
    setQuoteBusy(true);
    try {
      const result = await billingApi.outpatientQuote(paymentRequestId);
      if (quoteGeneration.current === generation && requestRef.current?.paymentRequestId === paymentRequestId) setQuote(result);
    } catch (cause) {
      if (quoteGeneration.current !== generation || requestRef.current?.paymentRequestId !== paymentRequestId) return;
      if (cause instanceof ApiRequestError && cause.status === 404 && cause.code === "BILLING_QUOTE_NOT_FOUND") {
        setQuoteUnavailable(true);
      } else {
        setQuoteError(errorFrom(cause, "Không thể đọc báo giá Billing."));
      }
    } finally {
      if (quoteGeneration.current === generation) setQuoteBusy(false);
    }
  }

  function quoteIsCurrent(now: number): boolean {
    return quote !== null && Number.isFinite(Date.parse(quote.expiresAt)) && Date.parse(quote.expiresAt) > now;
  }

  async function lookup(selectedRegistrationId?: string) {
    const id = (selectedRegistrationId ?? registrationId).trim();
    if (!id || lookupBusy || lookupLock.current || paymentBusy || paymentLock.current || checkingOperation || operationCheckLock.current || pending || uncertain) return;
    lookupLock.current = true;
    const generation = ++lookupGeneration.current;
    setLookupBusy(true);
    setLookupError(null);
    setPaymentError(null);
    setPaymentResult(null);
    setLastIntent(null);
    setRecoveredOperationId(null);
    setPaidFromRequest(false);
    requestRef.current = null;
    setRequest(null);
    quoteGeneration.current += 1;
    setQuote(null);
    setQuoteError(null);
    setQuoteUnavailable(false);
    setQuoteBusy(false);
    try {
      const result = await billingApi.scheduledPaymentRequestByRegistration(id);
      if (lookupGeneration.current !== generation) return;
      const recovery = await readScheduledPaymentRecovery(result.paymentRequestId, getSessionKey());
      if (lookupGeneration.current !== generation) return;
      requestRef.current = result;
      setRequest(result);
      setRegistrationId(id);
      setPaidFromRequest(result.status === "PAID" && recovery === null);
      if (quoteEnabled) void loadQuote(result.paymentRequestId);
      if (recovery) {
        const recoveryKind = recovery.kind ?? "PATIENT_PAYMENT";
        if (recoveryKind === "ZERO_LIABILITY") {
          setLastIntent({ kind: recoveryKind, body: recovery.command as ZeroPayableEligibilityRequest });
        } else {
          const command = recovery.command as ScheduledPaymentCommand;
          setLastIntent({ kind: recoveryKind, body: command });
          setPaymentMethod(command.paymentMethod);
          setProviderReference(command.providerReference ?? "");
        }
        setRecoveredOperationId(recovery.paymentOperationId);
        if (!recovery.paymentOperationId) setPaymentError({ message: recoveryKind === "ZERO_LIABILITY"
          ? "Đã khôi phục yêu cầu xác nhận nghĩa vụ 0 chưa có kết quả. Gửi lại đúng lệnh hoặc không tiếp tục xử lý khoản khác."
          : "Đã khôi phục yêu cầu thu chưa có kết quả xác nhận. Gửi lại đúng yêu cầu hoặc không tiếp tục thu khoản khác.", correlationId: null, uncertain: true });
      } else {
        setProviderReference("");
        setPaymentMethod("CASH");
      }
    } catch (cause) {
      if (lookupGeneration.current === generation) {
        requestRef.current = null;
        setRequest(null);
        setPaidFromRequest(false);
        setLookupError(errorFrom(cause, "Không thể tra cứu khoản thu."));
      }
    } finally {
      lookupLock.current = false;
      setLookupBusy(false);
    }
  }

  function changeRegistrationId(value: string) {
    setRegistrationId(value);
    if (requestRef.current && value.trim() !== requestRef.current.registrationId) {
      lookupGeneration.current += 1;
      quoteGeneration.current += 1;
      requestRef.current = null;
      setRequest(null);
      setLookupError(null);
      setPaymentError(null);
      setPaymentResult(null);
      setLastIntent(null);
      setRecoveredOperationId(null);
      setPaidFromRequest(false);
      setQuote(null);
      setQuoteError(null);
      setQuoteUnavailable(false);
      setQuoteBusy(false);
    }
  }

  async function submit(intentToRun: ScheduledIntent) {
    const submittedRequest = request;
    if (!submittedRequest || requestRef.current?.paymentRequestId !== submittedRequest.paymentRequestId
      || lookupBusy || lookupLock.current || paymentBusy || paymentLock.current || checkingOperation || operationCheckLock.current || !enabled) return;
    paymentLock.current = true;
    const generation = lookupGeneration.current;
    const isCurrentRequest = () => lookupGeneration.current === generation
      && requestRef.current?.paymentRequestId === submittedRequest.paymentRequestId;
    setPaymentBusy(true);
    setPaymentError(null);
    const scope = intentToRun.kind === "PATIENT_PAYMENT"
      ? `scheduled-payment:${submittedRequest.registrationId}:${submittedRequest.paymentRequestId}`
      : `scheduled-zero-liability:${submittedRequest.registrationId}:${submittedRequest.paymentRequestId}`;
    const body = intentToRun.body;
    try {
      const intent = await prepareCommandIntent(scope, getSessionKey(), body);
      if (isCurrentRequest()) setLastIntent(intentToRun);
      try {
        await saveScheduledPaymentRecovery(submittedRequest.paymentRequestId, getSessionKey(), { kind: intentToRun.kind, command: body, paymentOperationId: null });
      } catch (cause) {
        intent.cancelUnsent();
        throw new CommandIntentError(cause instanceof Error ? cause.message : "Không thể lưu yêu cầu thu đang chờ.");
      }
      const options = {
        idempotencyKey: intent.key,
        correlationId: intent.correlationId,
      };
      const result = intentToRun.kind === "PATIENT_PAYMENT"
        ? await billingApi.payScheduledRequest(submittedRequest.paymentRequestId, intentToRun.body, options)
        : await billingApi.confirmScheduledZeroLiability(submittedRequest.paymentRequestId, intentToRun.body, options);
      if (isCurrentRequest()) setPaymentResult(result);
      if (result.operation.status === "PREPARING") {
        if (isCurrentRequest()) setRecoveredOperationId(result.operation.paymentOperationId);
        await saveScheduledPaymentRecovery(submittedRequest.paymentRequestId, getSessionKey(), { kind: intentToRun.kind, command: body, paymentOperationId: result.operation.paymentOperationId });
      }
      if (terminalResult(result)) {
        await clearScheduledPaymentRecovery(submittedRequest.paymentRequestId, getSessionKey());
        intent.settle();
        if (isCurrentRequest()) {
          setRecoveredOperationId(null);
          setPaidFromRequest(result.operation.status === "COMPLETED");
        }
      }
    } catch (cause) {
      if (!(cause instanceof CommandIntentError)) {
        try {
          const existing = await prepareCommandIntent(scope, getSessionKey(), body);
          existing.settle(cause);
        } catch { /* The original command remains locked to its persisted fingerprint. */ }
      }
      const unknownOutcome = isUncertain(cause) || cause instanceof CommandIntentError;
      if (!unknownOutcome) {
        try { await clearScheduledPaymentRecovery(submittedRequest.paymentRequestId, getSessionKey()); } catch { /* Keep the UI locked if recovery cleanup fails. */ }
        if (isCurrentRequest()) { setLastIntent(null); setRecoveredOperationId(null); }
      }
      if (isCurrentRequest()) setPaymentError({ ...errorFrom(cause, "Không thể ghi nhận khoản thu."), uncertain: unknownOutcome });
    } finally {
      paymentLock.current = false;
      setPaymentBusy(false);
    }
  }

  function startPayment() {
    if (!request || requestRef.current?.paymentRequestId !== request.paymentRequestId
      || lookupBusy || lookupLock.current || paymentBusy || paymentLock.current || pending || uncertain
      || request.status !== "PENDING" && request.status !== "PARTIALLY_PAID") return;
    if (quoteEnabled && (quoteBusy || quoteError || (!quote && !quoteUnavailable))) {
      setPaymentError({ message: "Chưa xác nhận được báo giá hiện hành; kiểm tra lại báo giá đang lưu trước khi thu.", correlationId: quoteError?.correlationId ?? null });
      return;
    }
    if (quote && !quoteIsCurrent(Date.now())) {
      setPaymentError({ message: "Báo giá đã hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi tiếp tục.", correlationId: null });
      return;
    }
    if (quote && isZeroAmount(quote.patientPayable)) {
      setPaymentError({ message: "Báo giá xác nhận người bệnh không phải trả tiền. Không thu tiền mặt; dùng nút xác nhận nghĩa vụ 0 khi chức năng này được mở.", correlationId: null });
      return;
    }
    const revision = request.reservationRevision;
    if (!Number.isSafeInteger(revision) || revision < 1) {
      setPaymentError({ message: "Billing chưa trả về phiên bản giữ chỗ hợp lệ; chưa thể thu khoản này.", correlationId: null });
      return;
    }
    if (paymentMethod === "BANK_TRANSFER" && !providerReference.trim()) {
      setPaymentError({ message: "Nhập mã đối chiếu chuyển khoản.", correlationId: null });
      return;
    }
    if (!positiveMoneyPattern.test(request.remainingAmount)) {
      setPaymentError({ message: "Billing chưa trả về số dư dương hợp lệ; chưa thể thu khoản này.", correlationId: null });
      return;
    }
    const body: ScheduledPaymentCommand = {
      reservationId: request.reservationId,
      expectedReservationRevision: revision,
      quoteId: quote?.quoteId ?? null,
      amount: request.remainingAmount,
      currency: request.currency,
      paymentMethod,
      providerReference: paymentMethod === "CASH" ? null : providerReference.trim(),
    };
    void submit({ kind: "PATIENT_PAYMENT", body });
  }

  function startZeroLiability() {
    if (!request || requestRef.current?.paymentRequestId !== request.paymentRequestId
      || lookupBusy || lookupLock.current || paymentBusy || paymentLock.current || pending || uncertain
      || !quote || !quoteEnabled || !zeroLiabilityEnabled || !isZeroAmount(quote.patientPayable)) return;
    if (quoteBusy || quoteError || !quoteIsCurrent(Date.now())) {
      setPaymentError({ message: "Báo giá chưa còn hiệu lực. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi xác nhận nghĩa vụ 0.", correlationId: quoteError?.correlationId ?? null });
      return;
    }
    if (request.status !== "PENDING" && request.status !== "PARTIALLY_PAID") {
      setPaymentError({ message: "Khoản yêu cầu không còn chờ xác nhận; tra cứu lại trạng thái Billing.", correlationId: null });
      return;
    }
    const revision = request.reservationRevision;
    if (!Number.isSafeInteger(revision) || revision < 1) {
      setPaymentError({ message: "Billing chưa trả về phiên bản giữ chỗ hợp lệ; chưa thể xác nhận nghĩa vụ 0.", correlationId: null });
      return;
    }
    void submit({ kind: "ZERO_LIABILITY", body: {
      reservationId: request.reservationId,
      expectedReservationRevision: revision,
      quoteId: quote.quoteId,
    } });
  }

  async function checkOperation() {
    const operationId = paymentResult?.operation.paymentOperationId ?? recoveredOperationId;
    const checkedRequest = request;
    if (!operationId || !checkedRequest || requestRef.current?.paymentRequestId !== checkedRequest.paymentRequestId
      || lookupBusy || lookupLock.current || paymentBusy || paymentLock.current || checkingOperation || operationCheckLock.current) return;
    operationCheckLock.current = true;
    const generation = lookupGeneration.current;
    const isCurrentRequest = () => lookupGeneration.current === generation
      && requestRef.current?.paymentRequestId === checkedRequest.paymentRequestId;
    setCheckingOperation(true);
    setPaymentError(null);
    try {
      const result = await billingApi.scheduledPaymentOperation(operationId);
      if (isCurrentRequest()) setPaymentResult(result);
      if (result.operation.status === "PREPARING" && lastIntent) {
        if (isCurrentRequest()) setRecoveredOperationId(result.operation.paymentOperationId);
        await saveScheduledPaymentRecovery(checkedRequest.paymentRequestId, getSessionKey(), { kind: lastIntent.kind, command: lastIntent.body, paymentOperationId: result.operation.paymentOperationId });
      }
      if (terminalResult(result)) {
        if (isCurrentRequest()) {
          setPaidFromRequest(result.operation.status === "COMPLETED");
          setRecoveredOperationId(null);
        }
        await clearScheduledPaymentRecovery(checkedRequest.paymentRequestId, getSessionKey());
        if (lastIntent) {
          const intent = await prepareCommandIntent(
            lastIntent.kind === "PATIENT_PAYMENT"
              ? `scheduled-payment:${checkedRequest.registrationId}:${checkedRequest.paymentRequestId}`
              : `scheduled-zero-liability:${checkedRequest.registrationId}:${checkedRequest.paymentRequestId}`,
            getSessionKey(),
            lastIntent.body,
          );
          intent.settle();
        }
      }
    } catch (cause) {
      if (isCurrentRequest()) setPaymentError(errorFrom(cause, "Chưa đọc được trạng thái giao dịch."));
    } finally {
      operationCheckLock.current = false;
      setCheckingOperation(false);
    }
  }

  if (!enabled) {
    return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground">Tính năng thu phí lịch khám V2 chưa được mở.</p>;
  }

  const hasDue = request !== null && (request.status === "PENDING" || request.status === "PARTIALLY_PAID") && positiveMoneyPattern.test(request.remainingAmount);
  const completed = paymentResult?.operation.status === "COMPLETED" || paidFromRequest;
  const closed = paymentResult?.operation.status === "CLOSED_NOT_PAID";
  const quoteZero = quote !== null && isZeroAmount(quote.patientPayable);
  const quoteExpired = quote !== null && !quoteIsCurrent(clientNow);
  const canConfirmZero = Boolean(request && quoteZero && quoteEnabled && zeroLiabilityEnabled && quoteIsCurrent(clientNow)
    && (request.status === "PENDING" || request.status === "PARTIALLY_PAID")
    && Number.isSafeInteger(request.reservationRevision) && request.reservationRevision > 0
    && !lookupBusy && !pending && !completed && !closed && !uncertain && !paymentBusy);
  const quoteBlocksAction = quoteEnabled && (quoteBusy || quoteError !== null || (!quote && !quoteUnavailable) || quoteExpired);
  const retryZero = uncertain && lastIntent?.kind === "ZERO_LIABILITY";
  const retryPatient = uncertain && lastIntent?.kind === "PATIENT_PAYMENT";
  const zeroConfirmed = paymentResult?.operation.status === "COMPLETED" && paymentResult.operation.basis === "ZERO_LIABILITY";
  const patientFormVisible = hasDue && !quoteZero && Number.isSafeInteger(request.reservationRevision) && request.reservationRevision > 0 && !lookupBusy && !pending && !completed && !closed;

  return <div className="space-y-6">
    <ScheduledPaymentQueue disabled={lookupBusy || paymentBusy || checkingOperation || pending || uncertain} onSelect={id => { setManualLookupOpen(false); changeRegistrationId(id); void lookup(id); }} />
    <details open={manualLookupOpen} onToggle={event => setManualLookupOpen(event.currentTarget.open)} className="rounded-xl border border-border bg-surface p-5">
      <summary className="cursor-pointer text-lg font-semibold">Tra cứu phiếu được cung cấp</summary>
      <p className="mt-1 text-sm text-muted-foreground">Có thể chọn phiếu từ danh sách phía trên hoặc dùng mã phiếu được cung cấp khi cần hỗ trợ tra cứu. Số tiền phải thu do hệ thống kiểm tra.</p>
      <form className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={event => { event.preventDefault(); void lookup(); }}>
        <label className="flex-1 space-y-1 text-sm font-medium" htmlFor="scheduled-registration-id">
          <span>Mã phiếu đăng ký</span>
          <input id="scheduled-registration-id" value={registrationId} onChange={event => changeRegistrationId(event.target.value)} disabled={lookupBusy || paymentBusy || checkingOperation || pending || uncertain} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" autoComplete="off" />
        </label>
        <Button type="submit" loading={lookupBusy} disabled={lookupBusy || paymentBusy || checkingOperation || pending || uncertain}>Tra cứu khoản thu</Button>
      </form>
    </details>
    {lookupBusy ? <p role="status" className="text-sm text-muted-foreground">Đang kiểm tra khoản thu hiện hành…</p> : null}
    {lookupError ? <AsyncState kind="error" message={lookupError.message} correlationId={lookupError.correlationId} onRetry={() => void lookup()} /> : null}

    {request ? <section className="space-y-4 rounded-xl border border-border bg-surface p-5">
      <div>
        <h2 className="text-lg font-semibold">{completed ? "Khoản đã xử lý" : "Khoản phải thu"}</h2>
        <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <div><dt className="text-muted-foreground">Khoa</dt><dd><ReferenceText kind="department" value={request.departmentId} fallback="Khoa" /></dd></div>
          <div><dt className="text-muted-foreground">Bệnh nhân</dt><dd><ReferenceText kind="patient" value={request.patientId} fallback="Bệnh nhân" /></dd></div>

          {!completed ? <>
          <div><dt className="text-muted-foreground">Còn phải thu</dt><dd className="font-semibold">{formatDecimal(request.remainingAmount)} {request.currency}</dd></div>
          <div><dt className="text-muted-foreground">Đã thu</dt><dd>{formatDecimal(request.paidAmount)} {request.currency}</dd></div>
          <div><dt className="text-muted-foreground">Hạn giữ chỗ</dt><dd>{formatInstant(request.expiresAt)}</dd></div>
          <div><dt className="text-muted-foreground">Trạng thái Billing</dt><dd>{{ PENDING: "Chờ thanh toán", PARTIALLY_PAID: "Đã thanh toán một phần", PAID: "Đã thanh toán", EXPIRED: "Hết hạn", CANCELLED: "Đã hủy" }[request.status] ?? "Cần kiểm tra"}</dd></div>
          </> : null}
        </dl>
        {completed ? <Button className="mt-3" type="button" loading={lookupBusy} onClick={() => void lookup()}>Cập nhật số dư</Button> : null}
      </div>

      {quoteEnabled && !completed ? <div className="space-y-3 border-t border-border pt-4">
        {quote ? <OutpatientQuoteBreakdown quote={quote} /> : null}
        {quoteBusy ? <p role="status" className="text-sm text-muted-foreground">Đang tải báo giá Billing…</p> : null}
        {quoteUnavailable ? <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">Billing chưa có báo giá cho yêu cầu này. Không xem đây là khoản miễn phí; chỉ có thể tiếp tục thu số dư dương do Billing xác nhận.</p> : null}
        {quoteError ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">{quoteError.message}{quoteError.correlationId ? <span className="mt-1 block text-xs">Mã theo dõi: <span className="font-mono">{quoteError.correlationId}</span></span> : null}</p> : null}
        {quoteExpired ? <p role="alert" className="text-sm text-danger">Báo giá đã hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi tiếp tục.</p> : null}
        <Button type="button" variant="secondary" loading={quoteBusy} disabled={quoteBusy || paymentBusy || pending} onClick={() => void loadQuote(request.paymentRequestId)}>Kiểm tra báo giá hiện hành</Button>
      </div> : null}

      {completed ? zeroConfirmed && paymentResult?.clearanceId
        ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm">Đã xác nhận nghĩa vụ 0 đồng. <ReferenceText value={paymentResult.clearanceId} fallback="Xác nhận đã lưu" technical /></p>
        : <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm">Billing đã xác nhận khoản thu hoặc nghĩa vụ của yêu cầu này.</p> : null}
      {pending ? <div className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
        <p>{paymentResult?.operation.basis === "ZERO_LIABILITY" ? "Đang chờ Billing hoàn tất xác nhận nghĩa vụ 0. Giữ nguyên yêu cầu để tránh thu trùng." : "Đang chờ Billing hoàn tất giao dịch. Giữ nguyên yêu cầu thu để tránh thu trùng."}</p>
        <Button className="mt-3" loading={checkingOperation} onClick={() => void checkOperation()}>Kiểm tra trạng thái giao dịch</Button>
      </div> : null}
      {closed ? <p role="status" className="rounded-lg border border-border bg-surface-muted p-4 text-sm">Billing đã xác nhận giao dịch này được đóng mà chưa thu tiền. Tra cứu lại khoản thu trước khi thao tác tiếp.</p> : null}
      {paymentResult?.operation.status === "COMPLETED" && paymentResult.payment ? <p className="break-all text-sm text-muted-foreground">Biên nhận: <ReferenceText value={paymentResult.payment.transactionId} fallback="Biên nhận đã lưu" technical /> · {formatDecimal(paymentResult.payment.amount)} {paymentResult.payment.currency}</p> : null}
      {paymentError ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
        <p>{paymentError.message}</p>
        {paymentError.correlationId ? <p className="mt-1 text-xs">Mã theo dõi: <span className="font-mono">{paymentError.correlationId}</span></p> : null}
        {uncertain ? <p className="mt-2">Chưa xác định kết quả thu tiền. Không tạo yêu cầu mới; hãy gửi lại chính yêu cầu này hoặc kiểm tra trạng thái.</p> : null}
      </div> : null}

      {patientFormVisible ? <form className="grid gap-4 border-t border-border pt-4 sm:grid-cols-2" onSubmit={event => { event.preventDefault(); if (uncertain && lastIntent?.kind === "PATIENT_PAYMENT") void submit(lastIntent); else startPayment(); }}>
        <label className="space-y-1 text-sm font-medium" htmlFor="scheduled-payment-method">
          <span>Hình thức thu</span>
          <select id="scheduled-payment-method" value={paymentMethod} onChange={event => setPaymentMethod(event.target.value as "CASH" | "BANK_TRANSFER")} disabled={paymentBusy || uncertain} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3">
            <option value="CASH">Tiền mặt</option>
            <option value="BANK_TRANSFER">Chuyển khoản, đối chiếu thủ công</option>
          </select>
        </label>
        {paymentMethod === "BANK_TRANSFER" ? <label className="space-y-1 text-sm font-medium sm:col-span-2" htmlFor="provider-reference">
          <span>Mã đối chiếu chuyển khoản</span>
          <input id="provider-reference" value={providerReference} onChange={event => setProviderReference(event.target.value)} maxLength={120} disabled={paymentBusy || uncertain} required className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" />
        </label> : null}
        <div className="sm:col-span-2">
          <Button type="submit" loading={paymentBusy} disabled={quoteBlocksAction || uncertain && lastIntent?.kind !== "PATIENT_PAYMENT"}>{uncertain ? "Gửi lại cùng yêu cầu" : "Thu phí lịch khám"}</Button>
          <p className="mt-2 text-xs text-muted-foreground">Số tiền được lấy nguyên văn từ Billing: {formatDecimal(request.remainingAmount)} {request.currency}. Giao dịch chưa hoàn tất sẽ giữ nguyên khóa để đối soát.</p>
        </div>
      </form> : null}
      {retryPatient && !patientFormVisible ? <Button type="button" variant="primary" loading={paymentBusy} onClick={() => void submit(lastIntent)}>Gửi lại cùng yêu cầu thu</Button> : null}
      {retryZero ? <Button type="button" variant="primary" loading={paymentBusy} onClick={() => void submit(lastIntent)}>Gửi lại cùng lệnh xác nhận nghĩa vụ 0</Button> : null}
      {canConfirmZero ? <div className="border-t border-border pt-4">
        <p className="mb-3 text-sm">Báo giá hiện hành cho thấy người bệnh phải trả 0. Xác nhận nghĩa vụ này ghi nhận điều kiện thanh toán riêng, không ghi nhận tiền mặt.</p>
        <Button type="button" variant="primary" loading={paymentBusy} onClick={startZeroLiability}>Xác nhận nghĩa vụ 0 đồng</Button>
      </div> : null}
      {quoteZero && quoteEnabled && !zeroLiabilityEnabled && !completed && !pending ? <p role="status" className="border-t border-border pt-4 text-sm text-warning">Báo giá là 0 đồng nhưng chức năng xác nhận nghĩa vụ 0 chưa được mở. Không thu tiền mặt.</p> : null}
      {hasDue && (!Number.isSafeInteger(request.reservationRevision) || request.reservationRevision < 1) ? <p role="alert" className="border-t border-border pt-4 text-sm text-danger">Billing chưa cung cấp phiên bản giữ chỗ cần thiết. Chưa thể gửi giao dịch.</p> : null}
      {!hasDue && !quoteZero && !completed && !pending && !closed ? <p role="status" className="border-t border-border pt-4 text-sm text-muted-foreground">Khoản này chưa sẵn sàng để thu hoặc không còn số dư dương.</p> : null}
    </section> : null}
  </div>;
}
