"use client";
import { useRef, useState } from "react";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { ReferenceText } from "@/components/references/ReferenceText";
import { Button } from "@/components/ui/Button";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { billingApi } from "../api";
import type { ScheduledPaymentQueueItem } from "../types";

export function ScheduledPaymentQueue({ disabled, onSelect }: { disabled: boolean; onSelect: (registrationId: string) => void }) {
  const [patientId, setPatientId] = useState("");
  const [items, setItems] = useState<ScheduledPaymentQueueItem[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const generation = useRef(0);
  async function load(nextPage = 0) {
    if (disabled) return;
    const current = ++generation.current;
    setBusy(true); setItems(null); setError(null);
    try {
      const result = await billingApi.scheduledPaymentQueue(patientId, nextPage);
      if (current !== generation.current) return;
      setItems(result.content); setPage(result.number); setTotalPages(result.totalPages);
    } catch (cause) {
      if (current === generation.current) setError({ message: cause instanceof Error ? cause.message : "Không thể tải khoản chờ thu.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
    } finally { if (current === generation.current) setBusy(false); }
  }
  return <section aria-label="Danh sách chờ thu phí" className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <div><h2 className="text-lg font-semibold">Khoản khám đang chờ thu</h2><p className="mt-1 text-sm text-muted-foreground">Chọn bệnh nhân để tìm, hoặc tải toàn bộ khoản còn hạn. Chọn phiếu để kiểm tra số tiền hiện hành trước khi thu.</p></div>
    <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={event => { event.preventDefault(); void load(); }}>
      <div className="min-w-0 flex-1"><label htmlFor="cashier-queue-patient" className="mb-1 block text-sm font-medium">Tìm bệnh nhân</label><ReferenceInput id="cashier-queue-patient" kind="patient" value={patientId} onValueChange={value => { generation.current++; setPatientId(value); setItems(null); setBusy(false); setError(null); }} disabled={disabled} /></div>
      <Button type="submit" disabled={disabled} loading={busy}>Tải khoản chờ thu</Button>
    </form>
    {error ? <AsyncState kind="error" message={error.message} correlationId={error.correlationId} onRetry={() => void load(page)} /> : null}
    {items ? items.length ? <ul className="divide-y divide-border">{items.map(item => <li key={item.registrationId} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
      <div className="space-y-1"><p className="font-medium"><ReferenceText kind="patient" value={item.patientId} fallback="Bệnh nhân" /></p><p><ReferenceText kind="department" value={item.departmentId} fallback="Khoa" /></p><p className="text-xs text-muted-foreground">Đăng ký {formatInstant(item.createdAt)} · Hạn thanh toán {formatInstant(item.expiresAt)}</p></div>
      <Button type="button" disabled={disabled} onClick={() => onSelect(item.registrationId)}>Chọn khoản thu</Button>
    </li>)}</ul> : <p role="status" className="text-sm text-muted-foreground">Không có khoản khám còn hạn đang chờ thu. Nếu vừa đăng ký, tải lại sau khi hệ thống ghi nhận phiếu.</p> : null}
    {items && totalPages > 1 ? <nav aria-label="Trang khoản chờ thu" className="flex items-center justify-between gap-3"><Button type="button" disabled={disabled || busy || page === 0} onClick={() => void load(page - 1)}>Trang trước</Button><span className="text-sm">Trang {page + 1} / {totalPages}</span><Button type="button" disabled={disabled || busy || page + 1 >= totalPages} onClick={() => void load(page + 1)}>Trang sau</Button></nav> : null}
  </section>;
}
