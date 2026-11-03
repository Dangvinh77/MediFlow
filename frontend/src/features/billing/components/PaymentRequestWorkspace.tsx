"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { ReferenceText } from "@/components/references/ReferenceText";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { Pagination } from "@/components/ui/Pagination";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal, formatInstant } from "@/lib/format";
import type { PageResult } from "@/lib/types";
import { billingApi } from "../api";
import type { PaymentRequestQueueItem, PaymentRequestQueueQuery, PaymentRequestSelection } from "../types";
import { BillingSessionBoundary } from "./BillingSessionBoundary";
import { PaymentRequestLookupForm } from "./PaymentRequestLookupForm";

export function PaymentRequestWorkspace(props: { quoteEnabled?: boolean; zeroLiabilityEnabled?: boolean }) {
  return <BillingSessionBoundary identity="payment-request-queue"><PaymentRequestWorkspaceSession {...props} /></BillingSessionBoundary>;
}

function PaymentRequestWorkspaceSession({ quoteEnabled, zeroLiabilityEnabled }: { quoteEnabled?: boolean; zeroLiabilityEnabled?: boolean }) {
  const [filters, setFilters] = useState<PaymentRequestQueueQuery>({ page: 0 });
  const [result, setResult] = useState<PageResult<PaymentRequestQueueItem> | null>(null);
  const [error, setError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [selection, setSelection] = useState<PaymentRequestSelection | null>(null);
  const [locked, setLocked] = useState(false);
  const lock = useRef(false);
  const onLockChange = useCallback((value: boolean) => { lock.current = value; setLocked(value); }, []);
  const refresh = useCallback(() => setAttempt(value => value + 1), []);

  useEffect(() => {
    let active = true;
    billingApi.paymentRequestQueue(filters).then(value => {
      if (active) { setResult(value); setError(null); setLoading(false); }
    }).catch(cause => {
      if (!active) return;
      setResult(null); setLoading(false);
      setError({ message: cause instanceof ApiRequestError && cause.status === 404
        ? "Hàng khoản thu chưa được mở tại cơ sở. Dùng tra cứu hỗ trợ khi đã có mã được cung cấp."
        : cause instanceof Error ? cause.message : "Không thể tải hàng khoản thu.",
      correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    });
    return () => { active = false; };
  }, [filters, attempt]);

  function changeFilters(next: PaymentRequestQueueQuery, preserveSelection = false) {
    if (lock.current) return;
    if (!preserveSelection && (next.patientId !== filters.patientId || next.purpose !== filters.purpose)) setSelection(null);
    setFilters(next); setLoading(true); setResult(null); setError(null);
  }

  function choose(item: PaymentRequestQueueItem) {
    if (lock.current) return;
    const request = item.request;
    if (selection?.paymentRequestId === request.paymentRequestId) return;
    setSelection({ paymentRequestId: request.paymentRequestId, purpose: request.purpose, sourceId: request.sourceId,
      patientId: request.patientId, careEpisodeId: request.careEpisodeId });
  }

  const needsEpisodeScope = Boolean(selection && (filters.patientId !== selection.patientId || filters.careEpisodeId !== selection.careEpisodeId));

  return <div className="space-y-5">
    {locked ? <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">Đang đối soát khoản đã chọn. Chưa đổi bệnh nhân hoặc khoản thu khi kết quả giao dịch chưa rõ.</p> : null}
    <div className="grid items-start gap-5 lg:grid-cols-[minmax(18rem,0.8fr)_minmax(0,1.2fr)]">
      <section aria-label="Hàng khoản thu" className="min-w-0 rounded-xl border border-border bg-surface p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-lg font-semibold">Khoản cần xử lý</h2>
          <Button type="button" variant="secondary" disabled={locked || loading} onClick={() => { setLoading(true); setResult(null); refresh(); }}>Cập nhật hàng chờ</Button>
        </div>
        <div className="mt-4 space-y-3">
          <label className="block space-y-1 text-sm font-medium">Tìm bệnh nhân chờ thu
            <ReferenceInput kind="patient" aria-label="Tìm bệnh nhân chờ thu" value={filters.patientId ?? ""} disabled={locked}
              onValueChange={patientId => changeFilters({ ...filters, patientId: patientId || undefined, careEpisodeId: undefined, page: 0 })} />
          </label>
          <label className="block space-y-1 text-sm font-medium">Loại khoản cần xử lý
            <select aria-label="Loại khoản cần xử lý" value={filters.purpose ?? ""} disabled={locked}
              onChange={event => changeFilters({ ...filters, purpose: event.target.value as PaymentRequestQueueQuery["purpose"] || undefined, page: 0 })}
              className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3">
              <option value="">Xét nghiệm và thuốc</option><option value="LAB_TEST">Xét nghiệm</option><option value="PRESCRIPTION">Đơn thuốc</option>
            </select>
          </label>
          {needsEpisodeScope && selection ? <Button type="button" variant="secondary" disabled={locked} onClick={() => changeFilters({ ...filters, patientId: selection.patientId, careEpisodeId: selection.careEpisodeId, page: 0 }, true)}>Chỉ khoản cùng lượt đã chọn</Button> : null}
          {filters.patientId || filters.purpose || filters.careEpisodeId ? <Button type="button" variant="secondary" disabled={locked} onClick={() => changeFilters({ page: 0 })}>Bỏ bộ lọc</Button> : null}
          {filters.careEpisodeId ? <p className="text-sm text-primary">Đang xem các khoản cùng lượt đã chọn.</p> : null}
        </div>
        {loading ? <AsyncState kind="loading" message="Đang tải khoản cần xử lý…" /> : error ? <AsyncState kind="error" message={error.message} correlationId={error.correlationId}
          onRetry={() => { if (!lock.current) { setLoading(true); refresh(); } }} /> : result?.content.length === 0 ? <p role="status" className="mt-5 text-sm text-muted-foreground">Không có khoản cần xử lý trong bộ lọc. Nếu vừa có chỉ định hoặc đơn thuốc, cập nhật hàng chờ sau khi hệ thống ghi nhận.</p> : null}
        {!loading && !error && result ? <>
          <ul className="mt-4 space-y-3 lg:max-h-[calc(100vh-22rem)] lg:overflow-y-auto lg:pr-1">
            {result.content.map(item => {
              const request = item.request;
              const selected = request.paymentRequestId === selection?.paymentRequestId;
              return <li key={request.paymentRequestId} className={`space-y-3 rounded-lg border p-4 ${selected ? "border-primary bg-primary/5" : "border-border"}`}>
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <p className="font-semibold"><ReferenceText kind="patient" value={request.patientId} fallback="Bệnh nhân" /></p>
                  <span className="rounded-full bg-surface-muted px-2 py-1 text-xs font-medium">{request.purpose === "LAB_TEST" ? "Xét nghiệm" : "Đơn thuốc"}</span>
                </div>
                <p className="text-sm text-muted-foreground"><ReferenceText kind="department" value={request.departmentId} fallback="Khoa" /> · Phát sinh {formatInstant(item.createdAt)}</p>
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <p className="font-semibold">{request.remainingAmount === 0 ? "Xác nhận nghĩa vụ 0 đồng" : `${formatDecimal(request.remainingAmount)} ${request.currency}`}</p>
                  {selected ? <span role="status" className="text-sm font-medium text-primary">Đang đối chiếu</span>
                    : <Button type="button" variant="secondary" disabled={locked} onClick={() => choose(item)}>Chọn khoản</Button>}
                </div>
                {request.expiresAt ? <p className="text-xs text-muted-foreground">Hạn xử lý: {formatInstant(request.expiresAt)}</p> : null}
              </li>;
            })}
          </ul>
          <Pagination page={result.number} totalPages={result.totalPages} totalElements={result.totalElements} loading={locked || loading}
            onPageChange={page => changeFilters({ ...filters, page })} />
        </> : null}
      </section>
      <div className="min-w-0">
        {!selection ? <p role="status" className="mb-4 rounded-xl border border-border bg-surface p-5 text-sm text-muted-foreground">Chọn một khoản từ hàng chờ để đối chiếu và thanh toán. Không cần chép mã xét nghiệm, đơn thuốc hay lượt khám.</p> : null}
        <PaymentRequestLookupForm key={selection?.paymentRequestId ?? "manual"} initialSelection={selection ?? undefined}
          collapsedLookup defaultPurpose="LAB_TEST" quoteEnabled={quoteEnabled} zeroLiabilityEnabled={zeroLiabilityEnabled}
          onLockChange={onLockChange} onCompleted={refresh} onNext={() => { if (!lock.current) { setSelection(null); refresh(); } }} />
      </div>
    </div>
  </div>;
}
