"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useRef, useState, type FormEvent } from "react";
import { ApiRequestError } from "@/lib/api";
import { CommandIntentError, prepareCommandIntent } from "@/lib/command-intent";
import { getSessionKey } from "@/lib/session";
import { Button } from "@/components/ui/Button";
import { appointmentApi } from "../api";
import type { VitalSignCreateRequest } from "../types";

type NumericField = "pulseBpm" | "systolicMmhg" | "diastolicMmhg" | "temperatureC" | "respiratoryRate" | "spo2Percent" | "weightKg";

const fields: readonly { name: NumericField; label: string; decimal: boolean }[] = [
  { name: "pulseBpm", label: "Mạch (lần/phút)", decimal: false },
  { name: "systolicMmhg", label: "Huyết áp tâm thu (mmHg)", decimal: false },
  { name: "diastolicMmhg", label: "Huyết áp tâm trương (mmHg)", decimal: false },
  { name: "temperatureC", label: "Nhiệt độ (°C)", decimal: true },
  { name: "respiratoryRate", label: "Nhịp thở (lần/phút)", decimal: false },
  { name: "spo2Percent", label: "SpO2 (%)", decimal: false },
  { name: "weightKg", label: "Cân nặng (kg)", decimal: true },
];

const emptyValues: Record<NumericField, string> = { pulseBpm: "", systolicMmhg: "", diastolicMmhg: "", temperatureC: "", respiratoryRate: "", spo2Percent: "", weightKg: "" };

function validated(values: Record<NumericField, string>, reason: string): VitalSignCreateRequest | string {
  const body: VitalSignCreateRequest = { pulseBpm: null, systolicMmhg: null, diastolicMmhg: null, temperatureC: null, respiratoryRate: null, spo2Percent: null, weightKg: null, notMeasuredReason: reason.trim() || null };
  for (const field of fields) {
    const raw = values[field.name].trim();
    if (!raw) continue;
    if (raw.includes(",")) return `${field.label}: dùng dấu chấm cho phần thập phân, ví dụ 36.5.`;
    if (!(field.decimal ? /^\d+(\.\d+)?$/ : /^\d+$/).test(raw)) return `${field.label}: ${field.decimal ? "cần nhập một số." : "cần nhập số nguyên."}`;
    body[field.name] = Number(raw);
  }
  if ((body.systolicMmhg === null) !== (body.diastolicMmhg === null)) return "Cần nhập cả huyết áp tâm thu và tâm trương.";
  const incomplete = fields.some(field => body[field.name] === null);
  if (incomplete && !body.notMeasuredReason) return "Cần ghi lý do khi chưa đo đủ các chỉ số.";
  return body;
}

function errorText(cause: unknown): string {
  if (cause instanceof ApiRequestError) {
    if (cause.code === "VITAL_SIGNS_INVALID") return "Có chỉ số nằm ngoài khoảng đo được của thiết bị hoặc huyết áp chưa hợp lệ. Hãy kiểm tra lại.";
    if (cause.code === "VITAL_SIGNS_REASON_REQUIRED") return "Cần ghi lý do khi chưa đo đủ các chỉ số.";
    return cause.message;
  }
  return cause instanceof Error ? cause.message : "Không thể lưu sinh hiệu.";
}

function outcomeUnknown(cause: unknown) {
  return cause instanceof CommandIntentError || !(cause instanceof ApiRequestError) || cause.status < 400 || cause.status === 408 || cause.status >= 500;
}

export function VitalSignForm({ registrationId, onSaved }: { registrationId: string; onSaved: () => void }) {
  const { localize } = useLocale();
  const [values, setValues] = useState(emptyValues);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [pending, setPending] = useState<VitalSignCreateRequest | null>(null);
  const lock = useRef(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (lock.current) return;
    const checked = pending ?? validated(values, reason);
    if (typeof checked === "string") { setError(checked); return; }
    lock.current = true; setBusy(true); setError(null);
    let intent: Awaited<ReturnType<typeof prepareCommandIntent>> | undefined;
    try {
      intent = await prepareCommandIntent(`outpatient-vital-signs:${registrationId}`, getSessionKey(), checked);
      await appointmentApi.createVitalSigns(registrationId, checked, { idempotencyKey: intent.key, correlationId: intent.correlationId });
      intent.settle();
      setPending(null); setValues(emptyValues); setReason("");
      onSaved();
    } catch (cause) {
      intent?.settle(cause);
      setPending(outcomeUnknown(cause) ? checked : null);
      setError(errorText(cause));
    } finally {
      lock.current = false; setBusy(false);
    }
  }

  const locked = busy || Boolean(pending);
  return <form noValidate aria-label={localize("Ghi sinh hiệu")} onSubmit={submit} className="space-y-3 rounded-lg border border-border p-3 text-sm">
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4"><L10nValue>{fields.map(field => <label key={field.name} className="space-y-1 font-medium" htmlFor={`${registrationId}-${field.name}`}><L10nValue>{field.label}</L10nValue>
      <input id={`${registrationId}-${field.name}`} inputMode={field.decimal ? "decimal" : "numeric"} disabled={locked} value={values[field.name]} onChange={event => setValues(current => ({ ...current, [field.name]: event.target.value }))} className="min-h-11 w-full rounded-lg border border-control-border bg-surface px-3 font-normal tabular-nums" />
    </label>)}</L10nValue></div>
    <label className="block space-y-1 font-medium" htmlFor={`${registrationId}-not-measured`}><L10n>{"Lý do chưa đo đủ"}</L10n><textarea id={`${registrationId}-not-measured`} maxLength={500} disabled={locked} value={reason} onChange={event => setReason(event.target.value)} className="min-h-16 w-full rounded-lg border border-control-border bg-surface px-3 py-2 font-normal" />
    </label>
    <L10nValue>{error ? <div role="alert" className="rounded-lg border border-border p-3 font-medium"><p><L10nValue>{error}</L10nValue></p><L10nValue>{pending ? <p className="mt-1 font-normal"><L10n>{"Chưa xác định sinh hiệu đã được lưu hay chưa. Gửi lại đúng yêu cầu để tránh ghi trùng."}</L10n></p> : null}</L10nValue></div> : null}</L10nValue>
    <Button type="submit" variant="primary" loading={busy}><L10nValue>{pending ? "Gửi lại cùng yêu cầu" : "Lưu sinh hiệu"}</L10nValue></Button>
  </form>;
}
