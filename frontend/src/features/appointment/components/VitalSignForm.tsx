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

/**
 * The measurable range of the device, mirrored from Clinical's VitalSignObservation. It only catches
 * typing mistakes; it never judges whether a value is clinically abnormal.
 */
const fields: readonly { name: NumericField; label: string; indicator: string; unit: string; min: number; max: number; decimals: number }[] = [
  { name: "pulseBpm", label: "Mạch (lần/phút)", indicator: "Mạch", unit: "lần/phút", min: 20, max: 250, decimals: 0 },
  { name: "systolicMmhg", label: "Huyết áp tâm thu (mmHg)", indicator: "Huyết áp tâm thu", unit: "mmHg", min: 50, max: 300, decimals: 0 },
  { name: "diastolicMmhg", label: "Huyết áp tâm trương (mmHg)", indicator: "Huyết áp tâm trương", unit: "mmHg", min: 20, max: 200, decimals: 0 },
  { name: "temperatureC", label: "Nhiệt độ (°C)", indicator: "Nhiệt độ", unit: "°C", min: 30, max: 45, decimals: 1 },
  { name: "respiratoryRate", label: "Nhịp thở (lần/phút)", indicator: "Nhịp thở", unit: "lần/phút", min: 5, max: 80, decimals: 0 },
  { name: "spo2Percent", label: "SpO2 (%)", indicator: "SpO2", unit: "%", min: 50, max: 100, decimals: 0 },
  { name: "weightKg", label: "Cân nặng (kg)", indicator: "Cân nặng", unit: "kg", min: 0.5, max: 400, decimals: 2 },
];
type VitalField = (typeof fields)[number];

const emptyValues: Record<NumericField, string> = { pulseBpm: "", systolicMmhg: "", diastolicMmhg: "", temperatureC: "", respiratoryRate: "", spo2Percent: "", weightKg: "" };

const scaleText = (field: VitalField) => `${field.min} – ${field.max}`;
const formatText = (field: VitalField) => field.decimals === 0 ? "Số nguyên" : `Tối đa ${field.decimals} chữ số thập phân`;

/** The problem with one entered value, or null. An empty value is allowed (a reason covers it). */
export function vitalFieldError(name: NumericField, raw: string, values: Record<NumericField, string>): string | null {
  const field = fields.find(item => item.name === name)!;
  const text = raw.trim();
  if (!text) return null;
  if (text.includes(",")) return `${field.label}: dùng dấu chấm cho phần thập phân, ví dụ 36.5.`;
  if (field.decimals === 0 && !/^\d+$/.test(text)) return `${field.label}: cần nhập số nguyên.`;
  if (field.decimals > 0 && !/^\d+(\.\d+)?$/.test(text)) return `${field.label}: cần nhập một số.`;
  if ((text.split(".")[1]?.length ?? 0) > field.decimals) return `${field.label}: tối đa ${field.decimals} chữ số thập phân.`;
  const value = Number(text);
  if (value < field.min || value > field.max) return `${field.label}: ngoài thang đo ${scaleText(field)} ${field.unit}.`;
  const systolic = values.systolicMmhg.trim();
  if (name === "diastolicMmhg" && /^\d+$/.test(systolic) && value >= Number(systolic)) return "Huyết áp tâm trương phải nhỏ hơn tâm thu.";
  return null;
}

/** In its own row the indicator name is already visible, so only the problem is repeated. */
function rowMessage(field: VitalField, problem: string) {
  const text = problem.startsWith(`${field.label}: `) ? problem.slice(field.label.length + 2) : problem;
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function validated(values: Record<NumericField, string>, reason: string): VitalSignCreateRequest | string {
  const body: VitalSignCreateRequest = { pulseBpm: null, systolicMmhg: null, diastolicMmhg: null, temperatureC: null, respiratoryRate: null, spo2Percent: null, weightKg: null, notMeasuredReason: reason.trim() || null };
  for (const field of fields) {
    const problem = vitalFieldError(field.name, values[field.name], values);
    if (problem) return problem;
    const raw = values[field.name].trim();
    if (raw) body[field.name] = Number(raw);
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
  const [touched, setTouched] = useState<Partial<Record<NumericField, boolean>>>({});
  const [submitted, setSubmitted] = useState(false);

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
      setPending(null); setValues(emptyValues); setReason(""); setTouched({}); setSubmitted(false);
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
  const missing = fields.filter(field => !values[field.name].trim()).length;
  return <form noValidate aria-label={localize("Ghi sinh hiệu")} onSubmit={event => { setSubmitted(true); void submit(event); }} className="space-y-3 rounded-lg border border-border p-3 text-sm">
    <div className="overflow-x-auto rounded-lg border border-border">
      <table className="w-full min-w-xl text-left text-sm">
        <caption className="sr-only"><L10n>{"Bảng nhập sinh hiệu"}</L10n></caption>
        <thead className="bg-surface-muted text-muted-foreground"><tr>
          <th scope="col" className="px-3 py-2"><L10n>{"Chỉ số"}</L10n></th>
          <th scope="col" className="w-44 px-3 py-2"><L10n>{"Giá trị"}</L10n></th>
          <th scope="col" className="px-3 py-2"><L10n>{"Đơn vị"}</L10n></th>
          <th scope="col" className="px-3 py-2"><L10n>{"Thang đo hợp lệ"}</L10n></th>
        </tr></thead>
        <tbody><L10nValue>{fields.map(field => {
          const id = `${registrationId}-${field.name}`;
          const problem = touched[field.name] || submitted ? vitalFieldError(field.name, values[field.name], values) : null;
          return <tr key={field.name} className="border-t border-border align-top">
            <th scope="row" className="px-3 py-2 font-medium"><L10nValue>{field.indicator}</L10nValue></th>
            <td className="px-3 py-2">
              <input id={id} aria-label={localize(field.label)} inputMode={field.decimals ? "decimal" : "numeric"} disabled={locked} value={values[field.name]}
                aria-invalid={problem ? true : undefined} aria-describedby={problem ? `${id}-error` : `${id}-scale`}
                onBlur={() => setTouched(current => ({ ...current, [field.name]: true }))}
                onChange={event => setValues(current => ({ ...current, [field.name]: event.target.value }))}
                className={`min-h-10 w-full rounded-lg border bg-surface px-3 font-normal tabular-nums ${problem ? "border-danger" : "border-control-border"}`} />
              <L10nValue>{problem ? <p id={`${id}-error`} className="mt-1 text-xs text-danger"><L10nValue>{rowMessage(field, problem)}</L10nValue></p> : null}</L10nValue>
            </td>
            <td className="whitespace-nowrap px-3 py-2 text-muted-foreground"><L10nValue>{field.unit}</L10nValue></td>
            <td id={`${id}-scale`} className="px-3 py-2"><span className="whitespace-nowrap tabular-nums">{scaleText(field)}</span><span className="block text-xs text-muted-foreground"><L10nValue>{formatText(field)}</L10nValue><L10nValue>{field.name === "diastolicMmhg" ? <>; <L10n>{"nhỏ hơn tâm thu"}</L10n></> : null}</L10nValue></span></td>
          </tr>;
        })}</L10nValue></tbody>
      </table>
    </div>
    <p className="text-xs text-muted-foreground"><L10n>{"Thang đo là giới hạn đo được của thiết bị, dùng để bắt lỗi nhập liệu; hệ thống không đánh giá ý nghĩa lâm sàng của chỉ số. Dùng dấu chấm cho số thập phân."}</L10n></p>
    <label className="block space-y-1 font-medium" htmlFor={`${registrationId}-not-measured`}><L10nValue>{missing ? `Lý do chưa đo đủ (còn ${missing} chỉ số trống)` : "Lý do chưa đo đủ"}</L10nValue><textarea id={`${registrationId}-not-measured`} aria-label={localize("Lý do chưa đo đủ")} maxLength={500} disabled={locked} value={reason} onChange={event => setReason(event.target.value)} className="min-h-16 w-full rounded-lg border border-control-border bg-surface px-3 py-2 font-normal" />
    </label>
    <L10nValue>{error ? <div role="alert" className="rounded-lg border border-border p-3 font-medium"><p><L10nValue>{error}</L10nValue></p><L10nValue>{pending ? <p className="mt-1 font-normal"><L10n>{"Chưa xác định sinh hiệu đã được lưu hay chưa. Gửi lại đúng yêu cầu để tránh ghi trùng."}</L10n></p> : null}</L10nValue></div> : null}</L10nValue>
    <Button type="submit" variant="primary" loading={busy}><L10nValue>{pending ? "Gửi lại cùng yêu cầu" : "Lưu sinh hiệu"}</L10nValue></Button>
  </form>;
}
