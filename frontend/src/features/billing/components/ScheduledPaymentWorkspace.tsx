"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


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
import { BillingSessionBoundary } from "./BillingSessionBoundary";
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

export function ScheduledPaymentWorkspace(props: ScheduledPaymentWorkspaceProps) {
  return <BillingSessionBoundary identity="scheduled-payments"><ScheduledPaymentSession {...props} /></BillingSessionBoundary>;
}

function ScheduledPaymentSession({ enabled, quoteEnabled = false, zeroLiabilityEnabled = false }: ScheduledPaymentWorkspaceProps) {
  const { localize } = useLocale();
  const [registrationId, setRegistrationId] = useState("");
  const [manualLookupOpen, setManualLookupOpen] = useState(false);
  const [request, setRequest] = useState<RegistrationPaymentRequestDTO | null>(null);
  const [lookupError, setLookupError] = useState<WorkspaceError | null>(null);
  const [lookupBusy, setLookupBusy] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<"CASH" | "BANK_TRANSFER">("CASH");
  const [providerReference, setProviderReference] = useState("");
  const [paymentError, setPaymentError] = useState<WorkspaceError | null>(null);
  const [paymentBusy, setPaymentBusy] = useState(false);
  const [paymentResult, setPaymentResult] = useState<ScheduledPaymentResult | null>(null);
  const [paidFromRequest, setPaidFromRequest] = useState(false);
  const [completedRegistrationIds, setCompletedRegistrationIds] = useState<string[]>([]);
  const [checkingOperation, setCheckingOperation] = useState(false);
  const [lastIntent, setLastIntent] = useState<ScheduledIntent | null>(null);
  const [recoveredOperationId, setRecoveredOperationId] = useState<string | null>(null);
  const [quote, setQuote] = useState<OutpatientQuoteDTO | null>(null);
  const [quoteError, setQuoteError] = useState<WorkspaceError | null>(null);
  const [quoteUnavailable, setQuoteUnavailable] = useState(false);
  const [quoteBusy, setQuoteBusy] = useState(false);
  const [refreshBusy, setRefreshBusy] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const [queueRevision, setQueueRevision] = useState(0);
  const refreshLock = useRef(false);
  const quoteGeneration = useRef(0);
  const lookupGeneration = useRef(0);
  const lookupLock = useRef(false);
  const paymentLock = useRef(false);
  const operationCheckLock = useRef(false);
  const requestRef = useRef<RegistrationPaymentRequestDTO | null>(request);
  const [clientNow, setClientNow] = useState(0);
  const uncertain = paymentError?.uncertain === true;
  const pending = paymentResult?.operation.status === "PREPARING" || recoveredOperationId !== null;

  function rememberCompleted(registration: string) {
    setCompletedRegistrationIds(ids => ids.includes(registration) ? ids : [...ids, registration]);
  }

  useEffect(() => {
    const firstTick = window.setTimeout(() => setClientNow(Date.now()), 0);
    const interval = window.setInterval(() => setClientNow(Date.now()), 15_000);
    return () => {
      window.clearTimeout(firstTick); window.clearInterval(interval);
      lookupGeneration.current++;
      quoteGeneration.current++;
      requestRef.current = null;
    };
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

  async function refreshCompleted(completedRequest: RegistrationPaymentRequestDTO) {
    if (refreshLock.current) return;
    refreshLock.current = true;
    const generation = lookupGeneration.current;
    const session = getSessionKey();
    const isCurrent = () => generation === lookupGeneration.current && session === getSessionKey()
      && requestRef.current?.paymentRequestId === completedRequest.paymentRequestId;
    setRefreshBusy(true);
    setRefreshError(null);
    try {
      const updated = await billingApi.scheduledPaymentRequestByRegistration(completedRequest.registrationId);
      if (!isCurrent()) return;
      if (updated.paymentRequestId !== completedRequest.paymentRequestId || updated.registrationId !== completedRequest.registrationId
        || updated.patientId !== completedRequest.patientId || updated.reservationId !== completedRequest.reservationId
        || updated.currency !== completedRequest.currency || updated.status !== "PAID" || !isZeroAmount(updated.remainingAmount)) {
        setRefreshError("Trạng thái đọc lại chưa phản ánh khoản đã xử lý. Đã giữ biên nhận; không thu lại.");
        return;
      }
      requestRef.current = updated;
      setRequest(updated);
    } catch {
      if (isCurrent()) setRefreshError("Khoản thu đã được ghi nhận; chưa tải được trạng thái mới. Đã giữ biên nhận; không thu lại.");
    } finally {
      refreshLock.current = false;
      if (isCurrent()) setRefreshBusy(false);
    }
  }

  function nextCollection() {
    if (lookupLock.current || paymentLock.current || operationCheckLock.current || refreshLock.current || pending || uncertain) return;
    changeRegistrationId("");
    setManualLookupOpen(false);
    setRefreshError(null);
    setQueueRevision(value => value + 1);
  }

  async function lookup(selectedRegistrationId?: string) {
    const id = (selectedRegistrationId ?? registrationId).trim();
    if (!id || lookupBusy || lookupLock.current || paymentBusy || paymentLock.current || checkingOperation || operationCheckLock.current || refreshLock.current || pending || uncertain) return;
    if (requestRef.current?.registrationId === id && (paymentResult?.operation.status === "COMPLETED" || paidFromRequest)) {
      await refreshCompleted(requestRef.current);
      return;
    }
    lookupLock.current = true;
    const generation = ++lookupGeneration.current;
    setLookupBusy(true);
    setLookupError(null);
    setRefreshError(null);
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
      setPaidFromRequest((result.status === "PAID" || completedRegistrationIds.includes(result.registrationId)) && recovery === null);
      if (result.status === "PAID") rememberCompleted(result.registrationId);
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
    if (lookupLock.current || paymentLock.current || operationCheckLock.current || refreshLock.current || pending || uncertain) return;
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
      if (result.operation.status === "COMPLETED") rememberCompleted(submittedRequest.registrationId);
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
        if (result.operation.status === "COMPLETED" && isCurrentRequest()) await refreshCompleted(submittedRequest);
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
      if (result.operation.status === "COMPLETED") rememberCompleted(checkedRequest.registrationId);
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
        if (result.operation.status === "COMPLETED" && isCurrentRequest()) await refreshCompleted(checkedRequest);
      }
    } catch (cause) {
      if (isCurrentRequest()) setPaymentError(errorFrom(cause, "Chưa đọc được trạng thái giao dịch."));
    } finally {
      operationCheckLock.current = false;
      setCheckingOperation(false);
    }
  }

  if (!enabled) {
    return <p role="status" className="rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground"><L10n>{"Tính năng thu phí lịch khám V2 chưa được mở."}</L10n></p>;
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

  return <div className="flex flex-col gap-6">
    <ScheduledPaymentQueue key={queueRevision} completedRegistrationIds={completedRegistrationIds} disabled={lookupBusy || paymentBusy || checkingOperation || refreshBusy || pending || uncertain} onSelect={id => { setManualLookupOpen(false); changeRegistrationId(id); void lookup(id); }} />
    <details open={manualLookupOpen} onToggle={event => setManualLookupOpen(event.currentTarget.open)} className="order-last rounded-xl border border-border bg-surface p-4">
      <summary className="min-h-11 cursor-pointer py-2 text-sm font-medium"><L10n>{"Tra cứu phiếu được cung cấp"}</L10n></summary>
      <form className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={event => { event.preventDefault(); void lookup(); }}>
        <label className="flex-1 space-y-1 text-sm font-medium" htmlFor="scheduled-registration-id">
          <span><L10n>{"Mã phiếu đăng ký"}</L10n></span>
          <input id="scheduled-registration-id" value={registrationId} onChange={event => changeRegistrationId(event.target.value)} disabled={lookupBusy || paymentBusy || checkingOperation || refreshBusy || pending || uncertain} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" autoComplete="off" />
        </label>
        <Button type="submit" loading={lookupBusy} disabled={lookupBusy || paymentBusy || checkingOperation || pending || uncertain}><L10n>{"Tra cứu khoản thu"}</L10n></Button>
      </form>
    </details>
    <L10nValue>{lookupBusy ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Đang kiểm tra khoản thu hiện hành…"}</L10n></p> : null}</L10nValue>
    <L10nValue>{lookupError ? <AsyncState kind="error" message={localize(lookupError.message)} correlationId={lookupError.correlationId} onRetry={() => void lookup()} /> : null}</L10nValue>

    <L10nValue>{request ? <section className="space-y-4 rounded-xl border border-border bg-surface p-5">
      <div>
        <h2 className="text-lg font-semibold"><L10nValue>{completed ? "Khoản đã xử lý" : "Khoản phải thu"}</L10nValue></h2>
        <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <div><dt className="text-muted-foreground">Khoa</dt><dd><ReferenceText kind="department" value={request.departmentId} fallback="Khoa" /></dd></div>
          <div><dt className="text-muted-foreground"><L10n>{"Bệnh nhân"}</L10n></dt><dd><ReferenceText kind="patient" value={request.patientId} fallback="Bệnh nhân" /></dd></div>

          <L10nValue>{!completed ? <>
          <div><dt className="text-muted-foreground"><L10n>{"Còn phải thu"}</L10n></dt><dd className="font-semibold"><L10nValue>{formatDecimal(request.remainingAmount)}</L10nValue> {request.currency}</dd></div>
          <div><dt className="text-muted-foreground"><L10n>{"Đã thu"}</L10n></dt><dd><L10nValue>{formatDecimal(request.paidAmount)}</L10nValue> {request.currency}</dd></div>
          <div><dt className="text-muted-foreground"><L10n>{"Hạn giữ chỗ"}</L10n></dt><dd><L10nValue>{formatInstant(request.expiresAt)}</L10nValue></dd></div>
          <div><dt className="text-muted-foreground"><L10n>{"Trạng thái"}</L10n></dt><dd><L10nValue>{{ PENDING: "Chờ thanh toán", PARTIALLY_PAID: "Đã thanh toán một phần", PAID: "Đã thanh toán", EXPIRED: "Hết hạn", CANCELLED: "Đã hủy" }[request.status] ?? "Cần kiểm tra"}</L10nValue></dd></div>
          </> : null}</L10nValue>
        </dl>
      </div>

      <L10nValue>{quoteEnabled && !completed ? <div className="space-y-3 border-t border-border pt-4">
        <L10nValue>{quote ? <details className="rounded-lg border border-border p-3"><summary className="min-h-11 cursor-pointer py-2 text-sm font-medium"><L10n>{"Chi tiết giá và hỗ trợ mô phỏng"}</L10n></summary><OutpatientQuoteBreakdown quote={quote} /></details> : null}</L10nValue>
        <L10nValue>{quoteBusy ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Đang tải báo giá Billing…"}</L10n></p> : null}</L10nValue>
        <L10nValue>{quoteUnavailable ? <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm"><L10n>{"Billing chưa có báo giá cho yêu cầu này. Không xem đây là khoản miễn phí; chỉ có thể tiếp tục thu số dư dương do Billing xác nhận."}</L10n></p> : null}</L10nValue>
        <L10nValue>{quoteError ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger"><L10nValue>{quoteError.message}</L10nValue><L10nValue>{quoteError.correlationId ? <span className="mt-1 block text-xs"><L10n>{"Mã theo dõi: "}</L10n><span className="font-mono">{quoteError.correlationId}</span></span> : null}</L10nValue></p> : null}</L10nValue>
        <L10nValue>{quoteExpired ? <p role="alert" className="text-sm text-danger"><L10n>{"Báo giá đã hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi tiếp tục."}</L10n></p> : null}</L10nValue>
        <L10nValue>{quoteError ? <Button type="button" variant="secondary" loading={quoteBusy} disabled={quoteBusy || paymentBusy || pending} onClick={() => void loadQuote(request.paymentRequestId)}><L10n>{"Thử tải lại báo giá"}</L10n></Button> : null}</L10nValue>
      </div> : null}</L10nValue>

      <L10nValue>{completed ? <div className="space-y-3">
        <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm">
          <L10nValue>{zeroConfirmed ? "Đã xác nhận nghĩa vụ 0 đồng." : paymentResult?.payment
            ? `Đã thu ${formatDecimal(paymentResult.payment.amount)} ${paymentResult.payment.currency} · ${paymentResult.payment.paymentMethod === "CASH" ? "Tiền mặt" : "Chuyển khoản"}.`
            : "Khoản phí đã được thanh toán."}</L10nValue>
        </p>
        <L10nValue>{refreshBusy ? <p className="text-sm text-muted-foreground"><L10n>{"Đang cập nhật trạng thái khoản thu…"}</L10n></p> : null}</L10nValue>
        <L10nValue>{refreshError ? <div className="space-y-2"><p role="alert" className="text-sm text-warning"><L10nValue>{refreshError}</L10nValue></p><Button type="button" disabled={refreshBusy} onClick={() => void refreshCompleted(request)}><L10n>{"Thử kiểm tra lại"}</L10n></Button></div> : null}</L10nValue>
        <Button type="button" variant="primary" disabled={refreshBusy || paymentBusy || checkingOperation} onClick={nextCollection}><L10n>{"Thu khoản tiếp theo"}</L10n></Button>
      </div> : null}</L10nValue>
      <L10nValue>{pending ? <div className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
        <p><L10nValue>{paymentResult?.operation.basis === "ZERO_LIABILITY" ? "Đang chờ Billing hoàn tất xác nhận nghĩa vụ 0. Giữ nguyên yêu cầu để tránh thu trùng." : "Đang chờ Billing hoàn tất giao dịch. Giữ nguyên yêu cầu thu để tránh thu trùng."}</L10nValue></p>
        <Button className="mt-3" loading={checkingOperation} onClick={() => void checkOperation()}><L10n>{"Kiểm tra trạng thái giao dịch"}</L10n></Button>
      </div> : null}</L10nValue>
      <L10nValue>{closed ? <p role="status" className="rounded-lg border border-border bg-surface-muted p-4 text-sm"><L10n>{"Billing đã xác nhận giao dịch này được đóng mà chưa thu tiền. Tra cứu lại khoản thu trước khi thao tác tiếp."}</L10n></p> : null}</L10nValue>
      <L10nValue>{paymentError ? <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
        <p><L10nValue>{paymentError.message}</L10nValue></p>
        <L10nValue>{paymentError.correlationId ? <p className="mt-1 text-xs"><L10n>{"Mã theo dõi: "}</L10n><span className="font-mono">{paymentError.correlationId}</span></p> : null}</L10nValue>
        <L10nValue>{uncertain ? <p className="mt-2"><L10n>{"Chưa xác định kết quả thu tiền. Không tạo yêu cầu mới; hãy gửi lại chính yêu cầu này hoặc kiểm tra trạng thái."}</L10n></p> : null}</L10nValue>
      </div> : null}</L10nValue>

      <details className="text-sm text-muted-foreground">
        <summary className="min-h-8 cursor-pointer"><L10n>{"Thông tin đối soát khoản thu"}</L10n></summary>
        <dl className="mt-2 grid gap-2 break-all sm:grid-cols-2">
          <SupportInfo label={localize("Mã đăng ký")} value={request.registrationId} />
          <SupportInfo label={localize("Mã yêu cầu")} value={request.paymentRequestId} />
          <SupportInfo label={localize("Mã hóa đơn")} value={request.invoiceId} />
          <SupportInfo label={localize("Mã tài khoản")} value={request.accountId} />
          <SupportInfo label={localize("Mã giữ chỗ")} value={request.reservationId} />
          <SupportInfo label={localize("Mã báo giá")} value={paymentResult?.operation.quoteId ?? quote?.quoteId ?? null} />
          <SupportInfo label={localize("Mã thao tác")} value={paymentResult?.operation.paymentOperationId ?? recoveredOperationId} />
          <SupportInfo label={localize("Mã giao dịch")} value={paymentResult?.payment?.transactionId ?? paymentResult?.operation.transactionId ?? null} />
          <SupportInfo label={localize("Mã xác nhận 0 đồng")} value={paymentResult?.clearanceId ?? null} />
        </dl>
      </details>

      <L10nValue>{patientFormVisible ? <form className="grid gap-4 border-t border-border pt-4 sm:grid-cols-2" onSubmit={event => { event.preventDefault(); if (uncertain && lastIntent?.kind === "PATIENT_PAYMENT") void submit(lastIntent); else startPayment(); }}>
        <label className="space-y-1 text-sm font-medium" htmlFor="scheduled-payment-method">
          <span><L10n>{"Hình thức thu"}</L10n></span>
          <select id="scheduled-payment-method" value={paymentMethod} onChange={event => setPaymentMethod(event.target.value as "CASH" | "BANK_TRANSFER")} disabled={paymentBusy || uncertain} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3">
            <option value="CASH"><L10n>{"Tiền mặt"}</L10n></option>
            <option value="BANK_TRANSFER"><L10n>{"Chuyển khoản, đối chiếu thủ công"}</L10n></option>
          </select>
        </label>
        <L10nValue>{paymentMethod === "BANK_TRANSFER" ? <label className="space-y-1 text-sm font-medium sm:col-span-2" htmlFor="provider-reference">
          <span><L10n>{"Mã đối chiếu chuyển khoản"}</L10n></span>
          <input id="provider-reference" value={providerReference} onChange={event => setProviderReference(event.target.value)} maxLength={120} disabled={paymentBusy || uncertain} required className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3" />
        </label> : null}</L10nValue>
        <div className="sm:col-span-2">
          <Button type="submit" loading={paymentBusy} disabled={quoteBlocksAction || uncertain && lastIntent?.kind !== "PATIENT_PAYMENT"}><L10nValue>{uncertain ? "Gửi lại cùng yêu cầu" : "Thu phí lịch khám"}</L10nValue></Button>
          <p className="mt-2 text-xs text-muted-foreground"><L10n>{"Số tiền được lấy nguyên văn từ Billing: "}</L10n><L10nValue>{formatDecimal(request.remainingAmount)}</L10nValue> {request.currency}<L10n>{". Giao dịch chưa hoàn tất sẽ giữ nguyên khóa để đối soát."}</L10n></p>
        </div>
      </form> : null}</L10nValue>
      <L10nValue>{retryPatient && !patientFormVisible ? <Button type="button" variant="primary" loading={paymentBusy} onClick={() => void submit(lastIntent)}><L10n>{"Gửi lại cùng yêu cầu thu"}</L10n></Button> : null}</L10nValue>
      <L10nValue>{retryZero ? <Button type="button" variant="primary" loading={paymentBusy} onClick={() => void submit(lastIntent)}><L10n>{"Gửi lại cùng lệnh xác nhận nghĩa vụ 0"}</L10n></Button> : null}</L10nValue>
      <L10nValue>{canConfirmZero ? <div className="border-t border-border pt-4">
        <p className="mb-3 text-sm"><L10n>{"Báo giá hiện hành cho thấy người bệnh phải trả 0. Xác nhận nghĩa vụ này ghi nhận điều kiện thanh toán riêng, không ghi nhận tiền mặt."}</L10n></p>
        <Button type="button" variant="primary" loading={paymentBusy} onClick={startZeroLiability}><L10n>{"Xác nhận nghĩa vụ 0 đồng"}</L10n></Button>
      </div> : null}</L10nValue>
      <L10nValue>{quoteZero && quoteEnabled && !zeroLiabilityEnabled && !completed && !pending ? <p role="status" className="border-t border-border pt-4 text-sm text-warning"><L10n>{"Báo giá là 0 đồng nhưng chức năng xác nhận nghĩa vụ 0 chưa được mở. Không thu tiền mặt."}</L10n></p> : null}</L10nValue>
      <L10nValue>{hasDue && (!Number.isSafeInteger(request.reservationRevision) || request.reservationRevision < 1) ? <p role="alert" className="border-t border-border pt-4 text-sm text-danger"><L10n>{"Billing chưa cung cấp phiên bản giữ chỗ cần thiết. Chưa thể gửi giao dịch."}</L10n></p> : null}</L10nValue>
      <L10nValue>{!hasDue && !quoteZero && !completed && !pending && !closed ? <p role="status" className="border-t border-border pt-4 text-sm text-muted-foreground"><L10n>{"Khoản này chưa sẵn sàng để thu hoặc không còn số dư dương."}</L10n></p> : null}</L10nValue>
    </section> : null}</L10nValue>
  </div>;
}

function SupportInfo({ label, value }: { label: string; value: string | null }) {
  return value ? <div><dt className="text-xs"><L10nValue>{label}</L10nValue></dt><dd className="font-mono">{value}</dd></div> : null;
}
