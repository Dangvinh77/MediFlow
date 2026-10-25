"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal, formatInstant } from "@/lib/format";
import { isUuid } from "@/lib/validation";
import { BillingSessionBoundary } from "./BillingSessionBoundary";
import { LedgerPaymentForm } from "./LedgerPaymentForm";
import { billingApi } from "../api";
import type { PaymentRequestDiscoveryDTO, PaymentRequestSourcePurpose } from "../types";

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

export function PaymentRequestLookupForm() {
  return (
    <BillingSessionBoundary identity="payment-request-discovery">
      <PaymentRequestLookupSession />
    </BillingSessionBoundary>
  );
}

function PaymentRequestLookupSession() {
  const router = useRouter();
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

  const showCareEpisode = purpose !== "EXAM";

  async function lookup() {
    if (searchLock.current || paymentLocked) return;
    searchLock.current = true;
    const thisSearch = ++generation.current;
    setLoading(true);
    setError(null);
    setDiscovery(null);
    try {
      const result = await billingApi.discoverPaymentRequest({
        purpose,
        sourceId: sourceId.trim(),
        patientId: patientId.trim(),
        // The Billing contract binds EXAM to appointmentId for both values.
        careEpisodeId: showCareEpisode ? careEpisodeId.trim() : sourceId.trim(),
      });
      if (generation.current === thisSearch) setDiscovery(result);
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
  }

  const requestIsOpen = discovery?.status === "PENDING" || discovery?.status === "PARTIALLY_PAID";
  const accountAcceptsPayment = discovery?.accountStatus !== "CLOSED" && discovery?.accountStatus !== "SETTLED";
  const notExpired = !discovery?.expiresAt || new Date(discovery.expiresAt).getTime() > new Date(discovery.observedAt).getTime();
  const canPay = Boolean(discovery && requestIsOpen && accountAcceptsPayment && notExpired && Number.isFinite(discovery.remainingAmount) && discovery.remainingAmount > 0);

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
        <div className="flex gap-2">
          <Button type="submit" variant="primary" loading={loading} disabled={paymentLocked}>Tra cứu khoản phí</Button>
          <Button type="button" disabled={paymentLocked} onClick={() => router.back()}>Quay lại</Button>
        </div>
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
          {canPay ? (
            <LedgerPaymentForm requestId={discovery.paymentRequestId} remainingAmount={discovery.remainingAmount} currency={discovery.currency} onLockChange={setPaymentLocked} />
          ) : (
            <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-4 text-sm">
              Khoản phí này không còn ở trạng thái có thể thu. Tra cứu lại để lấy trạng thái hiện tại trước khi tiếp tục.
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
