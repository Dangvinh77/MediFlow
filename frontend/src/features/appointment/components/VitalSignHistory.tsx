"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { AsyncState } from "@/components/ui/AsyncState";
import { appointmentApi } from "../api";
import type { VitalSignObservationDTO } from "../types";

interface HistoryRead { key: string; rows?: VitalSignObservationDTO[]; message?: string; correlationId?: string | null }

const measured = (value: number | null, unit: string) => value === null ? "Chưa đo" : `${value} ${unit}`;

export function VitalSignHistory({ registrationId, refreshKey = 0 }: { registrationId: string; refreshKey?: number }) {
  const { localize } = useLocale();
  const [attempt, setAttempt] = useState(0);
  const [read, setRead] = useState<HistoryRead | null>(null);
  const key = `${registrationId}:${refreshKey}:${attempt}`;
  useEffect(() => {
    let live = true;
    appointmentApi.listVitalSigns(registrationId)
      .then(rows => { if (live) setRead({ key, rows }); })
      .catch((cause: unknown) => {
        if (live) setRead({ key, message: cause instanceof ApiRequestError ? cause.message : "Không thể tải sinh hiệu.", correlationId: cause instanceof ApiRequestError ? cause.correlationId : null });
      });
    return () => { live = false; };
  }, [registrationId, key]);
  const current = read?.key === key ? read : null;
  return <section aria-label={localize("Sinh hiệu đã ghi")} className="space-y-2 text-sm">
    <h3 className="font-semibold"><L10n>{"Sinh hiệu đã ghi"}</L10n></h3>
    <L10nValue>{!current ? <AsyncState kind="loading" message={localize("Đang tải sinh hiệu…")} /> : null}</L10nValue>
    <L10nValue>{current?.message ? <AsyncState kind="error" message={localize(current.message)} correlationId={current.correlationId ?? null} onRetry={() => setAttempt(value => value + 1)} /> : null}</L10nValue>
    <L10nValue>{current?.rows ? current.rows.length ? <ul className="divide-y divide-border rounded-lg border border-border"><L10nValue>{current.rows.map(row => <li key={row.observationId} className="space-y-1 p-3">
      <p className="text-muted-foreground"><L10nValue>{formatInstant(row.observedAt)}</L10nValue></p>
      <p><L10n>{"Mạch "}</L10n><L10nValue>{measured(row.pulseBpm, "lần/phút")}</L10nValue><L10n>{" · Huyết áp "}</L10n><L10nValue>{row.systolicMmhg === null || row.diastolicMmhg === null ? "Chưa đo" : `${row.systolicMmhg}/${row.diastolicMmhg} mmHg`}</L10nValue><L10n>{" · Nhiệt độ "}</L10n><L10nValue>{measured(row.temperatureC, "°C")}</L10nValue><L10n>{" · Nhịp thở "}</L10n><L10nValue>{measured(row.respiratoryRate, "lần/phút")}</L10nValue> · SpO2 <L10nValue>{row.spo2Percent === null ? "Chưa đo" : `${row.spo2Percent}%`}</L10nValue><L10n>{" · Cân nặng "}</L10n><L10nValue>{measured(row.weightKg, "kg")}</L10nValue></p>
      <L10nValue>{row.notMeasuredReason ? <p><L10n>{"Lý do chưa đo đủ: "}</L10n>{row.notMeasuredReason}</p> : null}</L10nValue>
    </li>)}</L10nValue></ul> : <p role="status" className="rounded-lg border border-border p-3"><L10n>{"Chưa có sinh hiệu cho lượt khám này. Vẫn có thể bắt đầu khám và đo sau."}</L10n></p> : null}</L10nValue>
  </section>;
}
