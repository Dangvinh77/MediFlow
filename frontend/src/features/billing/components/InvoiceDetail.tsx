"use client";

import { ReferenceText } from "@/components/references/ReferenceText";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal, formatInstant, formatLocalDate } from "@/lib/format";
import { BillingSessionBoundary } from "./BillingSessionBoundary";
import { billingApi } from "../api";
import type { InvoiceDTO, PaymentMethod } from "../types";

const paymentLabels: Record<PaymentMethod, string> = { CASH: "Tiền mặt", TRANSFER: "Chuyển khoản", INSURANCE: "Bảo hiểm" };

export function InvoiceDetail({ invoiceId, notice }: { invoiceId: string; notice?: "created" }) {
  return (
    <BillingSessionBoundary identity={`invoice:${invoiceId}`}>
      <InvoiceDetailSession invoiceId={invoiceId} notice={notice} />
    </BillingSessionBoundary>
  );
}

function InvoiceDetailSession({ invoiceId, notice }: { invoiceId: string; notice?: "created" }) {
  const router = useRouter();
  const [invoice, setInvoice] = useState<InvoiceDTO | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const [error, setError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [paying, setPaying] = useState(false);
  const [paidNotice, setPaidNotice] = useState(false);
  const [paymentOutcomeUnknown, setPaymentOutcomeUnknown] = useState(false);
  const [statusStillUnpaid, setStatusStillUnpaid] = useState(false);
  const [checkingStatus, setCheckingStatus] = useState(false);
  const payLock = useRef(false);
  const refreshLock = useRef(false);
  const active = useRef(true);

  const retry = useCallback(() => { setError(null); setRetryToken((value) => value + 1); }, []);

  useEffect(() => {
    active.current = true;
    void billingApi.getById(invoiceId).then((value) => {
      if (active.current) setInvoice(value);
    }).catch((cause: unknown) => {
      if (!active.current) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else setError({ message: cause instanceof Error ? cause.message : "Không thể tải hóa đơn.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    });
    return () => { active.current = false; };
  }, [invoiceId, retryToken, router]);

  async function checkStatus() {
    if (refreshLock.current) return;
    refreshLock.current = true;
    setCheckingStatus(true);
    setError(null);
    try {
      const current = await billingApi.getById(invoiceId);
      if (!active.current) return;
      setInvoice(current);
      if (current.isPaid) {
        setPaidNotice(true);
        setPaymentOutcomeUnknown(false);
        setStatusStillUnpaid(false);
      } else if (paymentOutcomeUnknown) {
        // Invoice pay has no idempotency contract, so a read showing unpaid
        // cannot make a second payment request safe after an ambiguous reply.
        setStatusStillUnpaid(true);
      }
    } catch (cause: unknown) {
      if (!active.current) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      setError({ message: cause instanceof Error ? cause.message : "Không thể kiểm tra trạng thái hóa đơn.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    } finally {
      refreshLock.current = false;
      if (active.current) setCheckingStatus(false);
    }
  }

  async function pay() {
    if (payLock.current || paymentOutcomeUnknown || invoice?.isPaid) return;
    payLock.current = true;
    setPaying(true);
    setError(null);
    try {
      const result = await billingApi.pay(invoiceId, { paymentMethod: method });
      if (!active.current) return;
      if (!result.success) {
        setError({ message: "Billing chưa xác nhận giao dịch. Hãy kiểm tra trạng thái hóa đơn trước khi thao tác tiếp.", correlationId: null });
        setPaymentOutcomeUnknown(true);
        return;
      }
      setInvoice((current) => current ? { ...current, isPaid: true, paymentMethod: result.paymentMethod, paidAt: result.paidAt, sagaStatus: result.sagaStatus } : current);
      setPaidNotice(true);
      // The payment response has no fee rows. Refresh them without allowing a
      // failed or stale read to undo the confirmed payment or reopen payment.
      try {
        const current = await billingApi.getById(invoiceId);
        if (!active.current) return;
        if (current.isPaid) setInvoice(current);
        else setError({ message: "Hóa đơn đã thanh toán; chi tiết mới chưa đồng bộ. Hãy tải lại trang để đối soát.", correlationId: null });
      } catch (refreshCause: unknown) {
        if (!active.current) return;
        setError({ message: "Hóa đơn đã thanh toán; chưa tải được chi tiết mới. Hãy tải lại trang để đối soát.", correlationId: refreshCause instanceof ApiRequestError ? refreshCause.correlationId : null });
      }
    } catch (cause: unknown) {
      if (!active.current) return;
      const uncertain = !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500;
      if (uncertain) setPaymentOutcomeUnknown(true);
      setError({
        message: uncertain
          ? "Chưa xác nhận được kết quả thanh toán. Hóa đơn này không hỗ trợ khóa chống gửi trùng; hãy kiểm tra trạng thái trước khi thao tác tiếp."
          : cause instanceof Error ? cause.message : "Không thể thanh toán hóa đơn.",
        correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
      });
    } finally {
      payLock.current = false;
      if (active.current) setPaying(false);
    }
  }

  if (error && !invoice) return <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={retry} />;
  if (!invoice) return <AsyncState kind="loading" message="Đang tải hóa đơn…" />;

  return (
    <section className="mt-6 space-y-6">
      {notice === "created" || paidNotice ? (
        <p role="status" className="rounded-lg border border-success/40 bg-success/10 p-4 text-sm text-success">{paidNotice ? "Thanh toán thành công." : "Đã lập hóa đơn."}</p>
      ) : null}
      {error ? (
        <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">
          {error.message}{error.correlationId ? <span className="mt-1 block text-xs">Mã theo dõi: <span className="font-mono">{error.correlationId}</span></span> : null}
        </p>
      ) : null}
      {paymentOutcomeUnknown ? (
        <div className="space-y-3 rounded-xl border border-warning/40 bg-warning/10 p-4 text-sm">
          {statusStillUnpaid ? <p role="status">Hóa đơn hiện vẫn chưa xác định được trạng thái thanh toán; không thể gửi lại giao dịch an toàn.</p> : null}
          <Button type="button" loading={checkingStatus} onClick={() => void checkStatus()}>Kiểm tra trạng thái hóa đơn</Button>
        </div>
      ) : null}
      <div className="flex items-start justify-between gap-4 rounded-xl border border-border bg-surface p-6">
        <div><p className="break-all font-mono text-sm"><ReferenceText value={invoice.invoiceId} fallback="Hóa đơn" /></p><p className="mt-2 text-2xl font-semibold">{formatDecimal(invoice.totalAmount)}</p></div>
        <StatusBadge tone={invoice.isPaid ? "success" : "warning"}>{invoice.isPaid ? "Đã thanh toán" : "Chưa thanh toán"}</StatusBadge>
      </div>
      <dl className="grid gap-5 rounded-xl border border-border bg-surface p-6 sm:grid-cols-2 lg:grid-cols-3">
        <Info label="Bệnh nhân" value={invoice.patientId} mono />
        <Info label="Ngày lập" value={formatLocalDate(invoice.createdDate)} />
        <Info label="Hình thức" value={invoice.paymentMethod ? paymentLabels[invoice.paymentMethod] : null} />
        <Info label="Thanh toán lúc" value={formatInstant(invoice.paidAt)} />
        <Info label="Saga" value={invoice.sagaStatus} />
        <Info label="Đơn thuốc" value={invoice.prescriptionId} mono />
      </dl>
      <div className="overflow-x-auto rounded-xl border border-border bg-surface">
        <table className="w-full min-w-3xl text-left text-sm">
          <thead className="bg-surface-muted"><tr><th className="px-4 py-3">Loại phí</th><th className="px-4 py-3">Khoa</th><th className="px-4 py-3">Ngày phát sinh</th><th className="px-4 py-3">Số tiền</th><th className="px-4 py-3">Trạng thái</th></tr></thead>
          <tbody>{invoice.fees.map((fee) => (
            <tr key={fee.feeId} className="border-t border-border">
              <td className="px-4 py-3">{fee.feeType}</td><td className="break-all px-4 py-3 font-mono text-xs"><ReferenceText kind="department" value={fee.departmentId} fallback="Khoa" /></td><td className="px-4 py-3">{formatLocalDate(fee.incurredDate)}</td><td className="px-4 py-3">{formatDecimal(fee.amount)}</td><td className="px-4 py-3"><StatusBadge tone={fee.isPaid ? "success" : "warning"}>{fee.isPaid ? "Đã trả" : "Chưa trả"}</StatusBadge></td>
            </tr>
          ))}</tbody>
        </table>
      </div>
      {!invoice.isPaid && !paymentOutcomeUnknown ? (
        <section className="max-w-xl rounded-xl border border-border bg-surface p-6">
          <h2 className="text-lg font-semibold">Thanh toán hóa đơn</h2>
          <div className="mt-4"><Field label="Hình thức" htmlFor="payment-method"><select id="payment-method" value={method} disabled={paying} onChange={(event) => setMethod(event.target.value as PaymentMethod)} className={controlClassName}>{Object.entries(paymentLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field></div>
          <Button type="button" variant="primary" loading={paying} className="mt-4" onClick={() => void pay()}>Xác nhận thanh toán</Button>
        </section>
      ) : null}
      <Link href="/billing" className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 text-sm font-semibold">Quay lại tra cứu</Link>
    </section>
  );
}

function Info({ label, value, mono = false }: { label: string; value: string | null; mono?: boolean }) {
  return <div><dt className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</dt><dd className={`mt-1 break-all text-sm ${mono ? "font-mono" : ""}`}>{mono ? <ReferenceText kind={label.includes("bệnh nhân") || label === "Bệnh nhân" ? "patient" : undefined} value={value} fallback={label} /> : value ?? "—"}</dd></div>;
}
