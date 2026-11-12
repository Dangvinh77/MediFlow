"use client";

import { useEffect, useState } from "react";
import { ApiRequestError } from "@/lib/api";
import { formatInstant } from "@/lib/format";
import { AsyncState } from "@/components/ui/AsyncState";
import { appointmentApi } from "../api";
import type { VitalSignObservationDTO } from "../types";

interface HistoryRead { key: string; rows?: VitalSignObservationDTO[]; message?: string; correlationId?: string | null }

const measured = (value: number | null, unit: string) => value === null ? "Chưa đo" : `${value} ${unit}`;

export function VitalSignHistory({ registrationId, refreshKey = 0 }: { registrationId: string; refreshKey?: number }) {
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
  return <section aria-label="Sinh hiệu đã ghi" className="space-y-2 text-sm">
    <h3 className="font-semibold">Sinh hiệu đã ghi</h3>
    {!current ? <AsyncState kind="loading" message="Đang tải sinh hiệu…" /> : null}
    {current?.message ? <AsyncState kind="error" message={current.message} correlationId={current.correlationId ?? null} onRetry={() => setAttempt(value => value + 1)} /> : null}
    {current?.rows ? current.rows.length ? <ul className="divide-y divide-border rounded-lg border border-border">{current.rows.map(row => <li key={row.observationId} className="space-y-1 p-3">
      <p className="text-muted-foreground">{formatInstant(row.observedAt)}</p>
      <p>Mạch {measured(row.pulseBpm, "lần/phút")} · Huyết áp {row.systolicMmhg === null || row.diastolicMmhg === null ? "Chưa đo" : `${row.systolicMmhg}/${row.diastolicMmhg} mmHg`} · Nhiệt độ {measured(row.temperatureC, "°C")} · Nhịp thở {measured(row.respiratoryRate, "lần/phút")} · SpO2 {row.spo2Percent === null ? "Chưa đo" : `${row.spo2Percent}%`} · Cân nặng {measured(row.weightKg, "kg")}</p>
      {row.notMeasuredReason ? <p>Lý do chưa đo đủ: {row.notMeasuredReason}</p> : null}
    </li>)}</ul> : <p role="status" className="rounded-lg border border-border p-3">Chưa có sinh hiệu cho lượt khám này. Vẫn có thể bắt đầu khám và đo sau.</p> : null}
  </section>;
}
