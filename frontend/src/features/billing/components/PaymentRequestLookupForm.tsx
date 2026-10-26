"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal, formatInstant } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { BillingSessionBoundary } from "./BillingSessionBoundary";
import { LedgerPaymentForm } from "./LedgerPaymentForm";
import { LabQuotePaymentAction } from "./LabQuotePaymentAction";
import { OutpatientQuoteBreakdown } from "./OutpatientQuoteBreakdown";
import { LabZeroLiabilityAction } from "./LabZeroLiabilityAction";
import { billingApi } from "../api";
import type { OutpatientQuoteDTO, PaymentRequestDiscoveryDTO, PaymentRequestSourcePurpose } from "../types";

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
}

export function PaymentRequestLookupForm({ quoteEnabled = false, zeroLiabilityEnabled = false }: PaymentRequestLookupFormProps) {
  return (
    <BillingSessionBoundary identity="payment-request-discovery">
      <PaymentRequestLookupSession quoteEnabled={quoteEnabled} zeroLiabilityEnabled={zeroLiabilityEnabled} />
    </BillingSessionBoundary>
  );
}

function isZeroAmount(value: string): boolean {
  return /^0+(?:\.0+)?$/.test(value);
}

function PaymentRequestLookupSession({ quoteEnabled, zeroLiabilityEnabled }: PaymentRequestLookupFormProps) {
  const [purpose, setPurpose] = useState<PaymentRequestSourcePurpose>("EXAM");
  const [sourceId, setSourceId] = useState("");
  const [patientId, setPatientId] = useState("");
  const [careEpisodeId, setCareEpisodeId] = useState("");
  const [discovery, setDiscovery] = useState<PaymentRequestDiscoveryDTO | null>(null);
  const [error, setError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const [loading, setLoading] = useState(false);
  const [paymentLocked, setPaymentLocked] = useState(false);
  const searchLock = useRef(false);
  const generation = useRef(0);
  const quoteGeneration = useRef(0);
  const [quote, setQuote] = useState<OutpatientQuoteDTO | null>(null);
  const [quoteError, setQuoteError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const [quoteUnavailable, setQuoteUnavailable] = useState(false);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [clientNow, setClientNow] = useState(0);

  useEffect(() => {
    const firstTick = window.setTimeout(() => setClientNow(Date.now()), 0);
    const interval = window.setInterval(() => setClientNow(Date.now()), 15_000);
    return () => { window.clearTimeout(firstTick); window.clearInterval(interval); };
  }, []);

  const showCareEpisode = purpose !== "EXAM";

  async function loadQuote(paymentRequestId: string) {
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
  }

  function quoteIsCurrent(now: number) {
    return quote !== null && Number.isFinite(Date.parse(quote.expiresAt)) && Date.parse(quote.expiresAt) > now;
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
      const result = await billingApi.discoverPaymentRequest({
        purpose,
        sourceId: sourceId.trim(),
        patientId: patientId.trim(),
        // The Billing contract binds EXAM to appointmentId for both values.
        careEpisodeId: showCareEpisode ? careEpisodeId.trim() : sourceId.trim(),
      });
      if (generation.current === thisSearch) {
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
    if (!isUuid(source) || !isUuid(patient) || (showCareEpisode && !isUuid(episode))) {
      setError({ message: "Mã nguồn, mã bệnh nhân và mã đợt điều trị phải là UUID hợp lệ.", correlationId: null });
      setDiscovery(null);
      return;
    }
    void lookup();
  }

  function changePurpose(value: PaymentRequestSourcePurpose) {
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
    <div className="mt-6 max-w-2xl space-y-5">
      <form onSubmit={submit} noValidate className="space-y-5 rounded-xl border border-border bg-surface p-6">
        <p className="text-sm text-muted-foreground">
          Tra cứu chính xác bằng mã nguồn, bệnh nhân và đợt điều trị. Billing sẽ trả về số tiền còn phải thu và mã tiền; không nhập hoặc ước tính các giá trị này thủ công.
        </p>
        {error ? (
          <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
            {error.message}{error.correlationId ? <span className="mt-1 block text-xs">Mã theo dõi: <span className="font-mono">{error.correlationId}</span></span> : null}
          </p>
        ) : null}
        <Field label="Loại khoản phí" htmlFor="payment-request-purpose" required>
          <select id="payment-request-purpose" value={purpose} disabled={loading || paymentLocked} onChange={(event) => changePurpose(event.target.value as PaymentRequestSourcePurpose)} className={controlClassName}>
            {Object.entries(purposes).map(([value, option]) => <option key={value} value={value}>{option.label}</option>)}
          </select>
        </Field>
        <Field label={purposes[purpose].sourceLabel} htmlFor="payment-request-source-id" required>
          <input id="payment-request-source-id" required value={sourceId} disabled={loading || paymentLocked} onChange={(event) => { setSourceId(event.target.value); setDiscovery(null); }} placeholder="UUID" className={controlClassName} />
        </Field>
        <Field label="Mã bệnh nhân" htmlFor="payment-request-patient-id" required>
          <ReferenceInput kind="patient" id="payment-request-patient-id" required value={patientId} disabled={loading || paymentLocked} onValueChange={(selectedId) => { setPatientId(selectedId); setDiscovery(null); }} placeholder="UUID" className={controlClassName} />
        </Field>
        {showCareEpisode ? (
          <Field label="Mã đợt điều trị" htmlFor="payment-request-care-episode-id" required hint="Nhập mã do màn hình nguồn cung cấp; không suy ra từ mã xét nghiệm hoặc đơn thuốc.">
            <input id="payment-request-care-episode-id" required value={careEpisodeId} disabled={loading || paymentLocked} onChange={(event) => { setCareEpisodeId(event.target.value); setDiscovery(null); }} placeholder="UUID" className={controlClassName} />
          </Field>
        ) : (
          <p className="text-xs text-muted-foreground">Đối với khoản phí khám, Billing dùng mã lịch khám làm mã đợt điều trị.</p>
        )}
        <Button type="submit" variant="primary" loading={loading} disabled={paymentLocked}>Tra cứu khoản phí</Button>
      </form>

      {loading ? <AsyncState kind="loading" message="Đang tra cứu khoản phí…" /> : null}
      {discovery ? (
        <section aria-label="Chi tiết khoản phí" className="space-y-5 rounded-xl border border-border bg-surface p-6">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div><h2 className="text-lg font-semibold">Khoản phí được xác nhận</h2><p className="mt-1 break-all font-mono text-xs text-muted-foreground"><ReferenceText value={discovery.paymentRequestId} fallback="Yêu cầu thanh toán" /></p></div>
            <StatusBadge tone={canPay ? "warning" : discovery.status === "PAID" ? "success" : "neutral"}>{requestStatusLabels[discovery.status]}</StatusBadge>
          </div>
          <dl className="grid gap-4 sm:grid-cols-2">
            <Amount label="Tổng khoản phí" amount={discovery.requestedAmount} currency={discovery.currency} />
            <Amount label="Đã thanh toán" amount={discovery.paidAmount} currency={discovery.currency} />
            <Amount label="Còn phải thu" amount={discovery.remainingAmount} currency={discovery.currency} />
            <Info label="Trạng thái tài khoản" value={accountStatusLabels[discovery.accountStatus]} />
            <Info label="Nguồn phí" value={purposes[discovery.purpose].label} />
            <Info label="Bệnh nhân" value={<ReferenceText kind="patient" value={discovery.patientId} fallback="Bệnh nhân" />} />
            <Info label="Mã đợt điều trị" value={discovery.careEpisodeId} mono />
            <Info label="Hạn thanh toán" value={discovery.expiresAt ? formatInstant(discovery.expiresAt) : "Không có hạn"} />
            <Info label="Dữ liệu Billing ghi nhận lúc" value={formatInstant(discovery.observedAt)} />
          </dl>
          {quoteEnabled ? <div className="space-y-3 border-t border-border pt-4">
            {quote ? <OutpatientQuoteBreakdown quote={quote} /> : null}
            {quoteLoading ? <p role="status" className="text-sm text-muted-foreground">Đang tải báo giá Billing…</p> : null}
            {quoteUnavailable ? <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">Billing chưa có báo giá cho yêu cầu này. Không xem đây là khoản miễn phí; {labQuotePayment ? "luồng thanh toán LAB V2 cần báo giá trước khi thu." : "các thao tác hiện có vẫn dựa trên số dư Billing dương."}</p> : null}
            {quoteError ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger">{quoteError.message}{quoteError.correlationId ? <span className="mt-1 block text-xs">Mã theo dõi: <span className="font-mono">{quoteError.correlationId}</span></span> : null}</p> : null}
            {quoteExpired ? <p role="alert" className="text-sm text-danger">Báo giá đã hết hạn. Yêu cầu thanh toán này cần được xử lý theo quy trình hết hạn trước khi tiếp tục.</p> : null}
            <Button type="button" variant="secondary" loading={quoteLoading} disabled={quoteLoading || paymentLocked} onClick={() => void loadQuote(discovery.paymentRequestId)}>Kiểm tra báo giá hiện hành</Button>
          </div> : null}
          {purpose === "LAB_TEST" && quoteZero && requestIsOpen && accountAcceptsPayment && notExpired ? quoteEnabled && zeroLiabilityEnabled ? <LabZeroLiabilityAction
            paymentRequestId={discovery.paymentRequestId}
            quoteId={quote?.quoteId ?? ""}
            quoteExpiresAt={quote?.expiresAt ?? ""}
            quoteCurrent={quoteIsCurrent(clientNow)}
            enabled={zeroLiabilityEnabled}
          /> : <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">Báo giá mô phỏng là 0 đồng. Chức năng xác nhận nghĩa vụ 0 chưa được mở; không thu tiền mặt.</p> : null}
          {canPay ? (
            labQuotePayment && quote ? <LabQuotePaymentAction paymentRequestId={discovery.paymentRequestId} quote={quote} quoteCurrent={quoteIsCurrent(clientNow)} onLockChange={setPaymentLocked} />
              : <LedgerPaymentForm requestId={discovery.paymentRequestId} remainingAmount={discovery.remainingAmount} currency={discovery.currency} onLockChange={setPaymentLocked} />
          ) : (
            <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
              {quoteZero ? "Báo giá hiện hành xác nhận người bệnh không phải trả tiền; không ghi nhận khoản thu tiền mặt." : quoteBlocksPayment ? "Chưa thể tiếp tục khi báo giá hiện hành chưa được xác nhận." : labQuotePayment && quoteUnavailable ? "Không thể thu LAB V2 khi Billing chưa cấp báo giá cho yêu cầu này." : "Khoản phí này không còn ở trạng thái có thể thu. Tra cứu lại để lấy trạng thái hiện tại trước khi tiếp tục."}
            </p>
          )}
        </section>
      ) : null}
    </div>
  );
}

function Amount({ label, amount, currency }: { label: string; amount: number; currency: string }) {
  return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className="mt-1 text-lg font-semibold">{formatDecimal(amount)} {currency}</dd></div>;
}

function Info({ label, value, mono = false }: { label: string; value: import("react").ReactNode; mono?: boolean }) {
  return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className={`mt-1 break-all text-sm ${mono ? "font-mono" : ""}`}>{value}</dd></div>;
}
