"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";

import { useCallback, useEffect, useRef, useState } from "react";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { ReferenceText } from "@/components/references/ReferenceText";
import { Button } from "@/components/ui/Button";
import { AsyncState } from "@/components/ui/AsyncState";
import { ApiRequestError } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { billingApi } from "../api";
import type { ScheduledPaymentQueueItem } from "../types";

export function ScheduledPaymentQueue({ disabled, onSelect, completedRegistrationIds = [] }: { disabled: boolean; onSelect: (registrationId: string) => void; completedRegistrationIds?: string[] }) {
  const { localize } = useLocale();
  const [patientId, setPatientId] = useState("");
  const [items, setItems] = useState<ScheduledPaymentQueueItem[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<{ message: string; correlationId: string | null } | null>(null);
  const generation = useRef(0);
  const load = useCallback(async (nextPage = 0) => {
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
  }, [disabled, patientId]);
  const initiallyLoaded = useRef(false);
  useEffect(() => {
    if (!disabled && !initiallyLoaded.current) { initiallyLoaded.current = true; void load(); }
  }, [disabled, load]);
  useEffect(() => () => { generation.current++; initiallyLoaded.current = false; }, []);
  const waitingItems = items?.filter(item => !completedRegistrationIds.includes(item.registrationId));
  return <section aria-label={localize("Danh sách chờ thu phí")} className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <h2 className="text-lg font-semibold"><L10n>{"Khoản khám đang chờ thu"}</L10n></h2>
    <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={event => { event.preventDefault(); void load(); }}>
      <div className="min-w-0 flex-1"><label htmlFor="cashier-queue-patient" className="mb-1 block text-sm font-medium"><L10n>{"Tìm bệnh nhân"}</L10n></label><ReferenceInput id="cashier-queue-patient" kind="patient" value={patientId} onValueChange={value => { generation.current++; setPatientId(value); setItems(null); setBusy(false); setError(null); }} disabled={disabled} /></div>
      <Button type="submit" disabled={disabled} loading={busy}><L10n>{"Tải khoản chờ thu"}</L10n></Button>
    </form>
    <L10nValue>{busy ? <AsyncState kind="loading" message={localize("Đang tải khoản chờ thu…")} /> : null}</L10nValue>
    <L10nValue>{error ? <AsyncState kind="error" message={localize(error.message)} correlationId={error.correlationId} onRetry={() => void load(page)} /> : null}</L10nValue>
    <L10nValue>{waitingItems ? waitingItems.length ? <ul className="divide-y divide-border"><L10nValue>{waitingItems.map(item => <li key={item.registrationId} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
      <div className="space-y-1"><p className="font-medium"><ReferenceText kind="patient" value={item.patientId} fallback="Bệnh nhân" /></p><p><ReferenceText kind="department" value={item.departmentId} fallback="Khoa" /></p><p className="text-xs text-muted-foreground"><L10n>{"Đăng ký "}</L10n><L10nValue>{formatInstant(item.createdAt)}</L10nValue><L10n>{" · Hạn thanh toán "}</L10n><L10nValue>{formatInstant(item.expiresAt)}</L10nValue></p></div>
      <Button type="button" disabled={disabled} onClick={() => onSelect(item.registrationId)}><L10n>{"Chọn khoản thu"}</L10n></Button>
    </li>)}</L10nValue></ul> : <p role="status" className="text-sm text-muted-foreground"><L10n>{"Không có khoản khám còn hạn đang chờ thu. Nếu vừa đăng ký, tải lại sau khi hệ thống ghi nhận phiếu."}</L10n></p> : null}</L10nValue>
    <L10nValue>{items && totalPages > 1 ? <nav aria-label={localize("Trang khoản chờ thu")} className="flex items-center justify-between gap-3"><Button type="button" disabled={disabled || busy || page === 0} onClick={() => void load(page - 1)}><L10n>{"Trang trước"}</L10n></Button><span className="text-sm"><L10n>{"Trang"}</L10n> <L10nValue>{page + 1}</L10nValue> / <L10nValue>{totalPages}</L10nValue></span><Button type="button" disabled={disabled || busy || page + 1 >= totalPages} onClick={() => void load(page + 1)}><L10n>{"Trang sau"}</L10n></Button></nav> : null}</L10nValue>
  </section>;
}
