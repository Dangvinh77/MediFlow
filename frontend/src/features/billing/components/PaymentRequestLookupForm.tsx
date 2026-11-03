"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal, formatInstant } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { getSessionKey } from "@/lib/session";
import { BillingSessionBoundary } from "./BillingSessionBoundary";
import { LedgerPaymentForm } from "./LedgerPaymentForm";
import { LabQuotePaymentAction } from "./LabQuotePaymentAction";
import { OutpatientQuoteBreakdown } from "./OutpatientQuoteBreakdown";
import { LabZeroLiabilityAction } from "./LabZeroLiabilityAction";
import { billingApi } from "../api";
import type { LabZeroPayableEligibilityDTO, LedgerPaymentDTO, OutpatientQuoteDTO, PaymentRequestDiscoveryDTO, PaymentRequestSelection, PaymentRequestSourcePurpose } from "../types";

const purposes: Record<PaymentRequestSourcePurpose, { label: string; sourceLabel: string }> = {
  EXAM: { label: "Khám", sourceLabel: "Mã lịch khám" },
  LAB_TEST: { label: "Xét nghiệm", sourceLabel: "Mã xét nghiệm" },
  PRESCRIPTION: { label: "Đơn thuốc", sourceLabel: "Mã đơn thuốc" },
};

const requestStatusLabels: Record<PaymentRequestDiscoveryDTO["status"], string> = {
  PENDING: "Chờ thanh toán",
  PARTIALLY_PAID: "Đã thanh toán một phần",
  PAID: "Đã thanh toán",
  EXPIRED: "Hết hạn",
  CANCELLED: "Đã hủy",
};

const accountStatusLabels: Record<PaymentRequestDiscoveryDTO["accountStatus"], string> = {
  OPEN: "Đang mở",
  CHARGE_CLOSED: "Đã chốt phí",
  SETTLEMENT_PENDING: "Đang quyết toán",
  SETTLED: "Đã quyết toán",
  CLOSED: "Đã đóng",
};

interface PaymentRequestLookupFormProps {
  quoteEnabled?: boolean;
  zeroLiabilityEnabled?: boolean;
  initialSelection?: PaymentRequestSelection;
  collapsedLookup?: boolean;
  defaultPurpose?: PaymentRequestSourcePurpose;
  onLockChange?: (locked: boolean) => void;
  onCompleted?: () => void;
  onNext?: () => void;
}

export function PaymentRequestLookupForm(props: PaymentRequestLookupFormProps) {
  return (
    <BillingSessionBoundary identity="payment-request-discovery">
      <PaymentRequestLookupSession key={props.initialSelection?.paymentRequestId ?? "manual"} {...props} />
    </BillingSessionBoundary>
  );
}

function isZeroAmount(value: string): boolean {
  return /^0+(?:\.0+)?$/.test(value);
}

function PaymentRequestLookupSession({ quoteEnabled = false, zeroLiabilityEnabled = false, initialSelection,
  collapsedLookup = false, defaultPurpose = "EXAM", onLockChange, onCompleted, onNext }: PaymentRequestLookupFormProps) {
  const [lookupOpen, setLookupOpen] = useState(!collapsedLookup);
  const [purpose, setPurpose] = useState<PaymentRequestSourcePurpose>(initialSelection?.purpose ?? defaultPurpose);
  const [sourceId, setSourceId] = useState(initialSelection?.sourceId ?? "");
  const [patientId, setPatientId] = useState(initialSelection?.patientId ?? "");
  const [careEpisodeId, setCareEpisodeId] = useState(initialSelection?.careEpisodeId ?? "");
  const [discovery, setDiscovery] = useState<PaymentRequestDiscoveryDTO | null>(null);
  const [error, setError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const [loading, setLoading] = useState(Boolean(initialSelection));
  const [paymentLocked, setPaymentLocked] = useState(false);
  const [completedPayment, setCompletedPayment] = useState<LedgerPaymentDTO | null>(null);
  const [zeroReceipt, setZeroReceipt] = useState<LabZeroPayableEligibilityDTO | null>(null);
  const alive = useRef(true);
  const sessionIdentity = useRef(getSessionKey());
  const confirmedSnapshot = useRef<{ paymentRequestId: string; paidAmount: number; zero: boolean } | null>(null);
  const searchLock = useRef(Boolean(initialSelection));
  const generation = useRef(0);
  const quoteGeneration = useRef(0);
  const [quote, setQuote] = useState<OutpatientQuoteDTO | null>(null);
  const [quoteError, setQuoteError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const [quoteUnavailable, setQuoteUnavailable] = useState(false);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [clientNow, setClientNow] = useState(0);
  const reportLock = useCallback((value: boolean) => {
    if (alive.current && sessionIdentity.current === getSessionKey()) { setPaymentLocked(value); onLockChange?.(value); }
  }, [onLockChange]);

  useEffect(() => {
    const firstTick = window.setTimeout(() => setClientNow(Date.now()), 0);
    const interval = window.setInterval(() => setClientNow(Date.now()), 15_000);
    return () => { window.clearTimeout(firstTick); window.clearInterval(interval); };
  }, []);

  const showCareEpisode = purpose !== "EXAM";

  const loadQuote = useCallback(async (paymentRequestId: string) => {
    const current = ++quoteGeneration.current;
    setQuote(null);
    setQuoteError(null);
    setQuoteUnavailable(false);
    setQuoteLoading(true);
    try {
      const result = await billingApi.outpatientQuote(paymentRequestId);
      if (quoteGeneration.current === current) setQuote(result);
    } catch (cause: unknown) {
      if (quoteGeneration.current !== current) return;
      if (cause instanceof ApiRequestError && cause.status === 404 && cause.code === "BILLING_QUOTE_NOT_FOUND") {
        setQuoteUnavailable(true);
      } else {
        setQuoteError({ message: cause instanceof Error ? cause.message : "Không thể đọc báo giá Billing.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
      }
    } finally {
      if (quoteGeneration.current === current) setQuoteLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!initialSelection) return;
    let active = true;
    const { purpose, sourceId, patientId, careEpisodeId, paymentRequestId } = initialSelection;
    billingApi.discoverPaymentRequest({ purpose, sourceId, patientId, careEpisodeId }).then(result => {
      if (!active) return;
      if (result.paymentRequestId !== paymentRequestId || result.purpose !== purpose || result.sourceId !== sourceId
        || result.patientId !== patientId || result.careEpisodeId !== careEpisodeId) {
        throw new Error("Thông tin khoản phí đọc lại không khớp khoản đã chọn. Chưa gửi thanh toán; chọn lại khoản hoặc liên hệ hỗ trợ.");
      }
      setDiscovery(result);
      if (quoteEnabled) void loadQuote(result.paymentRequestId);
    }).catch(cause => { if (active) setError({ message: cause instanceof Error ? cause.message : "Không thể xác minh khoản đã chọn.",
      correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    }).finally(() => { if (active) { setLoading(false); searchLock.current = false; } });
    return () => { active = false; };
  }, [initialSelection, quoteEnabled, loadQuote]);

  useEffect(() => {
    const reads = [generation, quoteGeneration];
    alive.current = true;
    return () => { alive.current = false; reads.forEach(read => read.current++); };
  }, []);

  function quoteIsCurrent(now: number) {
    return quote !== null && Number.isFinite(Date.parse(quote.expiresAt)) && Date.parse(quote.expiresAt) > now;
  }

  // A failed read must never turn a confirmed payment into an uncertain write.
  async function refreshAfterPayment(payment: LedgerPaymentDTO) {
    if (!alive.current || sessionIdentity.current !== getSessionKey()) return;
    const previous = discovery;
    if (!previous || previous.paymentRequestId !== payment.paymentRequestId) return;
    confirmedSnapshot.current = { paymentRequestId: previous.paymentRequestId, paidAmount: previous.paidAmount, zero: false };
    setCompletedPayment(payment);
    onCompleted?.();
    setDiscovery(null);
    quoteGeneration.current++;
    setQuote(null);
    setQuoteError(null);
    setQuoteLoading(false);
    setError(null);
    setLoading(true);
    searchLock.current = true;
    const current = ++generation.current;
    try {
      const updated = await billingApi.discoverPaymentRequest({ purpose: previous.purpose, sourceId: previous.sourceId, patientId: previous.patientId, careEpisodeId: previous.careEpisodeId });
      if (generation.current !== current) return;
      if (updated.paymentRequestId !== previous.paymentRequestId || updated.patientId !== previous.patientId
        || updated.sourceId !== previous.sourceId || updated.careEpisodeId !== previous.careEpisodeId
        || updated.paidAmount <= previous.paidAmount) {
        throw new Error("Số dư đọc lại chưa phản ánh khoản thu vừa hoàn tất.");
      }
      setDiscovery(updated);
      if (quoteEnabled && updated.status !== "PAID") void loadQuote(updated.paymentRequestId);
    } catch {
      if (generation.current === current) setError({ message: "Chưa cập nhật được số dư sau thanh toán. Khoản thu đã được ghi nhận; không thu lại. Bấm Thử kiểm tra lại để đọc trạng thái.", correlationId: null });
    } finally {
      if (generation.current === current) { setLoading(false); searchLock.current = false; }
    }
  }

  async function refreshAfterZero(receipt: LabZeroPayableEligibilityDTO) {
    if (!alive.current || sessionIdentity.current !== getSessionKey() || !discovery) return;
    const previous = discovery;
    confirmedSnapshot.current = { paymentRequestId: previous.paymentRequestId, paidAmount: previous.paidAmount, zero: true };
    setZeroReceipt(receipt); onCompleted?.();
    setDiscovery(null); setQuote(null); quoteGeneration.current++;
    setLoading(true); searchLock.current = true;
    const current = ++generation.current;
    try {
      const updated = await billingApi.discoverPaymentRequest({ purpose: previous.purpose, sourceId: previous.sourceId,
        patientId: previous.patientId, careEpisodeId: previous.careEpisodeId });
      if (generation.current !== current) return;
      if (updated.paymentRequestId !== previous.paymentRequestId || updated.patientId !== previous.patientId
        || updated.sourceId !== previous.sourceId || updated.careEpisodeId !== previous.careEpisodeId || updated.status !== "PAID") {
        throw new Error("Nghĩa vụ 0 chưa phản ánh trong snapshot.");
      }
      setDiscovery(updated);
    } catch {
      if (generation.current === current) setError({ message: "Đã xác nhận nghĩa vụ 0 đồng; chưa cập nhật được trạng thái. Không xác nhận lại; tải lại dữ liệu để đối soát.", correlationId: null });
    } finally {
      if (generation.current === current) { setLoading(false); searchLock.current = false; }
    }
  }

  async function lookup() {
    if (searchLock.current || paymentLocked) return;
    searchLock.current = true;
    const thisSearch = ++generation.current;
    setLoading(true);
    setError(null);
    setDiscovery(null);
    quoteGeneration.current += 1;
    setQuote(null);
    setQuoteError(null);
    setQuoteUnavailable(false);
    try {
      const requested = {
        purpose,
        sourceId: sourceId.trim(),
        patientId: patientId.trim(),
        // The Billing contract binds EXAM to appointmentId for both values.
        careEpisodeId: showCareEpisode ? careEpisodeId.trim() : sourceId.trim(),
      };
      const result = await billingApi.discoverPaymentRequest(requested);
      if (generation.current === thisSearch) {
        if (result.purpose !== requested.purpose || result.patientId !== requested.patientId
          || result.sourceId !== requested.sourceId || result.careEpisodeId !== requested.careEpisodeId
          || (initialSelection && initialSelection.purpose === requested.purpose && initialSelection.patientId === requested.patientId
            && initialSelection.sourceId === requested.sourceId && initialSelection.careEpisodeId === requested.careEpisodeId
            && result.paymentRequestId !== initialSelection.paymentRequestId)) {
          throw new Error("Thông tin khoản phí đọc lại không khớp yêu cầu đối chiếu. Chưa gửi thanh toán.");
        }
        const confirmed = confirmedSnapshot.current;
        if (confirmed?.paymentRequestId === result.paymentRequestId
          && (confirmed.zero ? result.status !== "PAID" : result.paidAmount <= confirmed.paidAmount)) {
          throw new Error("Số dư đọc lại chưa phản ánh khoản thu đã xác nhận. Không thu lại; tiếp tục đối soát.");
        }
        setDiscovery(result);
        if (quoteEnabled) void loadQuote(result.paymentRequestId);
      }
    } catch (cause: unknown) {
      if (generation.current !== thisSearch) return;
      const message = cause instanceof ApiRequestError && cause.status === 404
        ? "Không tìm thấy khoản phí khớp với đầy đủ mã nguồn, bệnh nhân và đợt điều trị. Kiểm tra lại các mã rồi tra cứu lại."
        : cause instanceof Error ? cause.message : "Không thể tra cứu khoản phí.";
      setError({ message, correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    } finally {
      if (generation.current === thisSearch) {
        searchLock.current = false;
        setLoading(false);
      }
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (searchLock.current || paymentLocked) return;
    const source = sourceId.trim();
    const patient = patientId.trim();
    const episode = careEpisodeId.trim();
    if (!patient && isUuid(source) && (!showCareEpisode || isUuid(episode))) {
      setError({ message: "Bấm Tìm và chọn đúng bệnh nhân từ kết quả. Gõ hoặc dán mã vào ô tìm kiếm chưa phải là đã chọn bệnh nhân.", correlationId: null });
      setDiscovery(null);
      return;
    }
    if (!isUuid(source) || !isUuid(patient) || (showCareEpisode && !isUuid(episode))) {
      setError({ message: "Mã nguồn, mã bệnh nhân và mã đợt điều trị phải là UUID hợp lệ.", correlationId: null });
      setDiscovery(null);
      return;
    }
    void lookup();
  }

  function changePurpose(value: PaymentRequestSourcePurpose) {
    setCompletedPayment(null); setZeroReceipt(null);
    setPurpose(value);
    setSourceId("");
    setPatientId("");
    setCareEpisodeId("");
    setDiscovery(null);
    setError(null);
    quoteGeneration.current += 1;
    setQuote(null);
    setQuoteError(null);
    setQuoteUnavailable(false);
  }

  const requestIsOpen = discovery?.status === "PENDING" || discovery?.status === "PARTIALLY_PAID";
  const accountAcceptsPayment = discovery?.accountStatus !== "CLOSED" && discovery?.accountStatus !== "SETTLED";
  const notExpired = !discovery?.expiresAt || new Date(discovery.expiresAt).getTime() > new Date(discovery.observedAt).getTime();
  const quoteZero = quote !== null && isZeroAmount(quote.patientPayable);
  const quoteExpired = quote !== null && !quoteIsCurrent(clientNow);
  const quoteBlocksPayment = quoteEnabled && (quoteLoading || quoteError !== null || (!quote && !quoteUnavailable) || quoteExpired);
  const labQuotePayment = quoteEnabled && purpose === "LAB_TEST";
  const positivePayable = labQuotePayment
    ? quote !== null && /^\d+(?:\.\d+)?$/.test(quote.patientPayable) && Number(quote.patientPayable) > 0
    : Number.isFinite(discovery?.remainingAmount) && (discovery?.remainingAmount ?? 0) > 0;
  const canPay = Boolean(discovery && requestIsOpen && accountAcceptsPayment && notExpired && !quoteZero && !quoteBlocksPayment
    && positivePayable);

  return (
    <div className="flex flex-col gap-5">
      {error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
        {error.message}{error.correlationId ? <span className="mt-1 block text-xs">Mã theo dõi: <span className="font-mono">{error.correlationId}</span></span> : null}
      </p> : null}
      <details open={lookupOpen} onToggle={event => setLookupOpen(event.currentTarget.open)} className="order-last rounded-xl border border-border bg-surface p-4">
        <summary className="min-h-11 cursor-pointer py-2 text-sm font-medium">Tra cứu bằng mã được bàn giao (hỗ trợ)</summary>
      <form onSubmit={submit} noValidate className="space-y-4 pt-3">
        <p className="text-sm text-muted-foreground">
          Dùng thông tin được bàn giao khi không tìm thấy khoản trong hàng chờ.
        </p>
        <Field label="Loại khoản phí" htmlFor="payment-request-purpose" required>
          <select id="payment-request-purpose" value={purpose} disabled={loading || paymentLocked} onChange={(event) => changePurpose(event.target.value as PaymentRequestSourcePurpose)} className={controlClassName}>
            {Object.entries(purposes).map(([value, option]) => <option key={value} value={value}>{option.label}</option>)}
          </select>
        </Field>
        <Field label={purposes[purpose].sourceLabel} htmlFor="payment-request-source-id" required>
          <input id="payment-request-source-id" required value={sourceId} disabled={loading || paymentLocked} onChange={(event) => { setCompletedPayment(null); setZeroReceipt(null); setSourceId(event.target.value); setDiscovery(null); }} placeholder="Mã do quầy phát sinh cung cấp" className={controlClassName} />
        </Field>
        <Field label="Mã bệnh nhân" htmlFor="payment-request-patient-id" required hint="Nhập tên, CCCD hoặc mã được bàn giao, bấm Tìm rồi chọn kết quả có tên bệnh nhân.">
          <ReferenceInput kind="patient" id="payment-request-patient-id" required value={patientId} disabled={loading || paymentLocked} onValueChange={(selectedId) => { setCompletedPayment(null); setZeroReceipt(null); setPatientId(selectedId); setDiscovery(null); }} placeholder="Tìm theo tên hoặc CCCD" className={controlClassName} />
        </Field>
        {showCareEpisode ? (
          <Field label="Mã đợt điều trị" htmlFor="payment-request-care-episode-id" required hint="Nhập mã do màn hình nguồn cung cấp; không suy ra từ mã xét nghiệm hoặc đơn thuốc.">
            <input id="payment-request-care-episode-id" required value={careEpisodeId} disabled={loading || paymentLocked} onChange={(event) => { setCompletedPayment(null); setZeroReceipt(null); setCareEpisodeId(event.target.value); setDiscovery(null); }} placeholder="Mã đợt chăm sóc từ màn hình nguồn" className={controlClassName} />
          </Field>
        ) : (
          <p className="text-xs text-muted-foreground">Đối với khoản phí khám, Billing dùng mã lịch khám làm mã đợt điều trị.</p>
        )}
        <div className="flex gap-2">
          <Button type="submit" variant="primary" loading={loading} disabled={paymentLocked}>Tra cứu khoản phí</Button>
          <Button type="button" disabled={paymentLocked} onClick={() => setLookupOpen(false)}>Đóng tra cứu</Button>
        </div>
      </form>
      </details>

      {error && (initialSelection || completedPayment || zeroReceipt) ? <Button type="button" variant="secondary" disabled={loading || paymentLocked}
        onClick={() => void lookup()}>Thử kiểm tra lại</Button> : null}

      {completedPayment ? <p role="status" className="break-words rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">Thanh toán thành công. Đã ghi nhận {formatDecimal(completedPayment.amount)} {completedPayment.currency}.</p> : null}
      {zeroReceipt ? <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">Đã xác nhận nghĩa vụ 0 đồng.</p> : null}
      {(completedPayment || zeroReceipt) && onNext ? <Button type="button" variant="primary" disabled={loading || paymentLocked} onClick={onNext}>Thu khoản tiếp theo</Button> : null}
      {loading ? <AsyncState kind="loading" message="Đang tra cứu khoản phí…" /> : null}
      {discovery ? (
        <section aria-label="Chi tiết khoản phí" className="space-y-5 rounded-xl border border-border bg-surface p-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <h2 className="text-lg font-semibold">Khoản phí được xác nhận</h2>
            <StatusBadge tone={canPay ? "warning" : discovery.status === "PAID" ? "success" : "neutral"}>{requestStatusLabels[discovery.status]}</StatusBadge>
          </div>
          <dl className="grid gap-4 sm:grid-cols-2">
            <Amount label="Số tiền yêu cầu thu" amount={discovery.requestedAmount} currency={discovery.currency} />
            <Amount label="Đã thanh toán" amount={discovery.paidAmount} currency={discovery.currency} />
            <Amount label="Còn phải thu" amount={discovery.remainingAmount} currency={discovery.currency} />
            <Info label="Nguồn phí" value={purposes[discovery.purpose].label} />
            <Info label="Bệnh nhân" value={<ReferenceText kind="patient" value={discovery.patientId} fallback="Bệnh nhân" />} />
            <Info label="Khoa" value={<ReferenceText kind="department" value={discovery.departmentId} fallback="Khoa" />} />
            <Info label="Hạn thanh toán" value={discovery.expiresAt ? formatInstant(discovery.expiresAt) : "Không có hạn"} />
          </dl>
          {quoteEnabled && requestIsOpen ? <div className="space-y-3 border-t border-border pt-4">
            {quote ? collapsedLookup ? <details className="rounded-lg border border-border p-3"><summary className="min-h-11 cursor-pointer py-2 text-sm font-medium">Chi tiết giá và hỗ trợ mô phỏng</summary><OutpatientQuoteBreakdown quote={quote} /></details> : <OutpatientQuoteBreakdown quote={quote} /> : null}
            {quoteLoading ? <p role="status" className="text-sm text-muted-foreground">Đang tải báo giá Billing…</p> : null}
            {quoteUnavailable ? <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">Billing chưa có báo giá cho yêu cầu này. Không xem đây là khoản miễn phí; {labQuotePayment ? "luồng thanh toán LAB V2 cần báo giá trước khi thu." : "các thao tác hiện có vẫn dựa trên số dư Billing dương."}</p> : null}
            {quoteError ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">{quoteError.message}{quoteError.correlationId ? <span className="mt-1 block text-xs">Mã theo dõi: <span className="font-mono">{quoteError.correlationId}</span></span> : null}</p> : null}
            {quoteExpired ? <p role="alert" className="text-sm text-danger">Báo giá đã hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi tiếp tục.</p> : null}
            {quoteError ? <Button type="button" variant="secondary" loading={quoteLoading} disabled={quoteLoading || paymentLocked} onClick={() => void loadQuote(discovery.paymentRequestId)}>Thử tải lại báo giá</Button> : null}
          </div> : null}
          {purpose === "LAB_TEST" && quoteZero && requestIsOpen && accountAcceptsPayment && notExpired ? quoteEnabled && zeroLiabilityEnabled ? <LabZeroLiabilityAction
            paymentRequestId={discovery.paymentRequestId}
            quoteId={quote?.quoteId ?? ""}
            quoteExpiresAt={quote?.expiresAt ?? ""}
            quoteCurrent={quoteIsCurrent(clientNow)}
            enabled={zeroLiabilityEnabled}
            onLockChange={reportLock}
            onCompleted={receipt => void refreshAfterZero(receipt)}
          /> : <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">Báo giá mô phỏng là 0 đồng. Chức năng xác nhận nghĩa vụ 0 chưa được mở; không thu tiền mặt.</p> : null}
          {canPay || (paymentLocked && labQuotePayment && quote && !quoteZero) ? (
            labQuotePayment && quote ? <LabQuotePaymentAction paymentRequestId={discovery.paymentRequestId} quote={quote} quoteCurrent={quoteIsCurrent(clientNow)} onLockChange={reportLock} onCompleted={payment => void refreshAfterPayment(payment)} />
              : <LedgerPaymentForm requestId={discovery.paymentRequestId} remainingAmount={discovery.remainingAmount} currency={discovery.currency} onLockChange={reportLock} onCompleted={payment => void refreshAfterPayment(payment)} />
          ) : (
            <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
              {discovery.status === "PAID" ? "Khoản phí đã thanh toán. Không thu thêm cho yêu cầu này." : quoteZero ? "Báo giá hiện hành xác nhận người bệnh không phải trả tiền; không ghi nhận khoản thu tiền mặt." : quoteBlocksPayment ? "Chưa thể tiếp tục khi báo giá hiện hành chưa được xác nhận." : labQuotePayment && quoteUnavailable ? "Không thể thu LAB V2 khi Billing chưa cấp báo giá cho yêu cầu này." : "Khoản phí này không còn ở trạng thái có thể thu. Tra cứu lại để lấy trạng thái hiện tại trước khi tiếp tục."}
            </p>
          )}
        </section>
      ) : null}
      {discovery || completedPayment || zeroReceipt ? <details className="rounded-lg border border-border p-4">
        <summary className="min-h-11 cursor-pointer py-2 text-sm font-medium">Thông tin đối soát khoản đã chọn</summary>
        <dl className="mt-2 grid gap-3 text-sm">
          <Info label="Mã yêu cầu" value={completedPayment?.paymentRequestId ?? zeroReceipt?.paymentRequestId ?? discovery?.paymentRequestId ?? ""} mono />
          {discovery ? <>
            <Info label="Trạng thái tài khoản viện phí" value={accountStatusLabels[discovery.accountStatus]} />
            <Info label="Dữ liệu cập nhật lúc" value={formatInstant(discovery.observedAt)} />
            <Info label="Mã nguồn" value={discovery.sourceId} mono />
            <Info label="Mã đợt điều trị" value={discovery.careEpisodeId} mono />
          </> : null}
          {completedPayment ? <Info label="Mã giao dịch" value={completedPayment.transactionId} mono /> : null}
          {zeroReceipt ? <Info label="Mã xác nhận 0 đồng" value={zeroReceipt.clearanceId} mono /> : null}
        </dl>
      </details> : null}
    </div>
  );
}

function Amount({ label, amount, currency }: { label: string; amount: number; currency: string }) {
  return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 text-lg font-semibold">{formatDecimal(amount)} {currency}</dd></div>;
}

function Info({ label, value, mono = false }: { label: string; value: import("react").ReactNode; mono?: boolean }) {
  return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className={`mt-1 break-all text-sm ${mono ? "font-mono" : ""}`}>{value}</dd></div>;
}
