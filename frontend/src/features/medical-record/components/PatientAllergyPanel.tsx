"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { Field, controlClassName } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { prepareCommandIntent } from "@/lib/command-intent";
import { getSessionKey, subscribeToAuthChanges } from "@/lib/session";
import type { Role } from "@/lib/roles";
import { medicalRecordApi } from "../api";
import type { AllergySeverity, PatientAllergiesDTO, PatientAllergyDTO } from "../types";

type PanelState =
  | { key: string; status: "loading" }
  | { key: string; status: "error"; message: string; correlationId: string | null }
  | { key: string; status: "forbidden" }
  | { key: string; status: "success"; allergies: PatientAllergiesDTO };

interface CommandState {
  busy: boolean;
  uncertain: boolean;
  error: { status: number | null; message: string; code?: string; correlationId: string | null; details: [] } | null;
}

type PendingIntent = {
  scope: string;
  session: string | null;
  body: unknown;
  request: (options: { idempotencyKey: string; correlationId: string }) => Promise<unknown>;
  onSuccess: () => void;
};

const severityLabels: Record<AllergySeverity, string> = {
  MILD: "Nhẹ",
  MODERATE: "Trung bình",
  SEVERE: "Nặng",
  UNKNOWN: "Chưa rõ",
};

function formatClinicalInstant(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Asia/Ho_Chi_Minh",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(date);
  const part = (name: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === name)?.value ?? "";
  return `${part("hour")}:${part("minute")} ${part("day")}/${part("month")}/${part("year")}`;
}

function AllergyEntry({ allergy }: { allergy: PatientAllergyDTO }) {
  return (
    <article className="rounded-lg border border-border p-3">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h3 className="font-semibold">{allergy.substance}</h3>
        <span className="text-xs text-muted-foreground"><L10n>{"Mức độ: "}</L10n><L10nValue>{severityLabels[allergy.severity]}</L10nValue></span>
      </div>
      <L10nValue>{allergy.reaction ? <p className="mt-1 text-sm"><L10n>{"Phản ứng: "}</L10n>{allergy.reaction}</p> : null}</L10nValue>
      <p className="mt-1 text-xs text-muted-foreground"><L10n>{"Ghi nhận lúc "}</L10n><L10nValue>{formatClinicalInstant(allergy.recordedAt)}</L10nValue></p>
      <L10nValue>{allergy.statusReason ? <p className="mt-1 text-xs text-muted-foreground"><L10n>{"Lý do cập nhật: "}</L10n>{allergy.statusReason}</p> : null}</L10nValue>
    </article>
  );
}

export function PatientAllergyPanel({
  patientId,
  role,
  sourceRecordId,
}: {
  patientId: string;
  role: Role | null;
  sourceRecordId?: string | null;
}) {
  const session = useSyncExternalStore(subscribeToAuthChanges, getSessionKey, () => null);
  return <PatientAllergyPanelContent
    key={`${patientId}:${session ?? "anonymous"}`}
    patientId={patientId}
    role={role}
    sourceRecordId={sourceRecordId}
    session={session}
  />;
}

function PatientAllergyPanelContent({
  patientId,
  role,
  sourceRecordId,
  session,
}: {
  patientId: string;
  role: Role | null;
  sourceRecordId?: string | null;
  session: string | null;
}) {
  const { localize } = useLocale();
  const router = useRouter();
  const commandLock = useRef(false);
  const inFlight = useRef(false);
  const mounted = useRef(false);
  const pendingIntent = useRef<PendingIntent | null>(null);
  const [command, setCommand] = useState<CommandState>({ busy: false, uncertain: false, error: null });
  const [retryToken, setRetryToken] = useState(0);
  const key = `${patientId}:${retryToken}`;
  const [panel, setPanel] = useState<PanelState>({ key, status: "loading" });
  const [expandedInactive, setExpandedInactive] = useState(false);
  const [substance, setSubstance] = useState("");
  const [reaction, setReaction] = useState("");
  const [severity, setSeverity] = useState<AllergySeverity>("UNKNOWN");
  const [statusTarget, setStatusTarget] = useState<PatientAllergyDTO | null>(null);
  const [statusReason, setStatusReason] = useState("");
  const canAdd = role === "DOCTOR" || role === "NURSE";
  const [formKey, setFormKey] = useState(key);
  if (formKey !== key) {
    setFormKey(key);
    setExpandedInactive(false);
    setSubstance("");
    setReaction("");
    setStatusTarget(null);
  }

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    let active = true;
    medicalRecordApi.allergies(patientId)
      .then((allergies) => {
        if (active) setPanel({ key, status: "success", allergies });
      })
      .catch((cause: unknown) => {
        if (!active) return;
        if (cause instanceof ApiRequestError && cause.status === 401) { router.replace("/login"); return; }
        if (cause instanceof ApiRequestError && cause.status === 403) { setPanel({ key, status: "forbidden" }); return; }
        setPanel({
          key,
          status: "error",
          message: "Không thể tải sổ dị ứng của bệnh nhân.",
          correlationId: cause instanceof ApiRequestError ? cause.correlationId : null,
        });
      });
    return () => { active = false; };
  }, [key, patientId, router]);

  const current = panel.key === key ? panel : { key, status: "loading" as const };

  async function sendCommand(scope: string, body: unknown, request: PendingIntent["request"], onSuccess: () => void, isRetry = false) {
    const isCurrent = () => mounted.current && getSessionKey() === session;
    if (!isCurrent() || inFlight.current || (commandLock.current && !isRetry)) return;
    const priorIntent = pendingIntent.current;
    if (isRetry && (!priorIntent || priorIntent.scope !== scope || priorIntent.session !== session)) return;
    inFlight.current = true;
    commandLock.current = true;
    setCommand({ busy: true, uncertain: isRetry, error: null });
    const intent: PendingIntent = { scope, session, body, request, onSuccess };
    pendingIntent.current = intent;
    let uncertain = false;
    try {
      const prepared = await prepareCommandIntent(scope, session, body);
      if (!isCurrent()) {
        prepared.cancelUnsent();
        return;
      }
      try {
        await request({ idempotencyKey: prepared.key, correlationId: prepared.correlationId });
        prepared.settle();
        pendingIntent.current = null;
        if (isCurrent()) onSuccess();
      } catch (cause) {
        prepared.settle(cause);
        uncertain = cause instanceof ApiRequestError
          ? cause.status < 400 || cause.status === 408 || cause.status >= 500
          : true;
        if (!uncertain) pendingIntent.current = null;
        throw cause;
      }
      setCommand({ busy: false, uncertain: false, error: null });
    } catch (cause) {
      if (!isCurrent()) return;
      const apiError = cause instanceof ApiRequestError ? cause : null;
      if (apiError?.status === 401) { router.replace("/login"); pendingIntent.current = null; }
      uncertain = uncertain || !apiError || apiError.status < 400 || apiError.status === 408 || apiError.status >= 500;
      setCommand({
        busy: false,
        uncertain,
        error: {
          status: apiError?.status ?? null,
          message: apiError?.message ?? (cause instanceof Error ? cause.message : "Không thể thực hiện thao tác."),
          code: apiError?.code,
          correlationId: apiError?.correlationId ?? null,
          details: [],
        },
      });
    } finally {
      inFlight.current = false;
      if (isCurrent()) commandLock.current = uncertain;
    }
  }

  function retryUncertainCommand() {
    const pending = pendingIntent.current;
    if (pending && command.uncertain && !inFlight.current && pending.session === session) void sendCommand(pending.scope, pending.body, pending.request, pending.onSuccess, true);
  }

  function submitAllergy(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedSubstance = substance.trim();
    if (!normalizedSubstance || normalizedSubstance.length > 200 || command.busy || command.uncertain) return;
    const body = { substance: normalizedSubstance, reaction: reaction.trim() || null, severity, sourceRecordId: sourceRecordId ?? null };
    void sendCommand(`patient-allergy:add:${patientId}`, body,
      (options) => medicalRecordApi.addAllergy(patientId, body, options),
      () => { setSubstance(""); setReaction(""); setRetryToken((value) => value + 1); });
  }

  function confirmStatusChange() {
    if (!statusTarget || !statusReason.trim() || command.busy || command.uncertain) return;
    const body = { status: "ENTERED_IN_ERROR" as const, reason: statusReason.trim() };
    void sendCommand(`patient-allergy:status:${patientId}:${statusTarget.allergyId}`, body,
      (options) => medicalRecordApi.changeAllergyStatus(patientId, statusTarget.allergyId, body, options),
      () => { setStatusTarget(null); setStatusReason(""); setExpandedInactive(true); setRetryToken((value) => value + 1); });
  }

  return (
    <section aria-labelledby="patient-allergies-heading" className="space-y-4 rounded-xl border border-warning/40 bg-surface p-4">
      <div>
        <h2 id="patient-allergies-heading" className="text-lg font-semibold"><L10n>{"Sổ dị ứng"}</L10n></h2>
        <p className="mt-1 text-sm text-muted-foreground"><L10n>{"Thông tin cấp bệnh nhân để tham khảo ở mọi lượt khám."}</L10n></p>
      </div>
      <L10nValue>{current.status === "loading" ? <AsyncState kind="loading" message={localize("Đang tải sổ dị ứng…")} /> : null}</L10nValue>
      <L10nValue>{current.status === "error" ? <AsyncState kind="error" message={localize(current.message)} correlationId={current.correlationId} onRetry={() => setRetryToken((value) => value + 1)} /> : null}</L10nValue>
      <L10nValue>{current.status === "forbidden" ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Bạn không có quyền xem sổ dị ứng của bệnh nhân này."}</L10n></p> : null}</L10nValue>
      <L10nValue>{current.status === "success" ? (
        <>
          <L10nValue>{current.allergies.active.length === 0 ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Chưa ghi nhận dị ứng"}</L10n></p> : (
            <ul className="space-y-2"><L10nValue>{current.allergies.active.map((allergy) => (
              <li key={allergy.allergyId} className="space-y-2">
                <AllergyEntry allergy={allergy} />
                <L10nValue>{role === "DOCTOR" ? <Button type="button" variant="ghost" disabled={command.busy || command.uncertain} onClick={() => { setStatusTarget(allergy); setStatusReason(""); }}><L10n>{"Đánh dấu nhập nhầm"}</L10n></Button> : null}</L10nValue>
              </li>
            ))}</L10nValue></ul>
          )}</L10nValue>
        <L10nValue>{current.allergies.inactive.length > 0 ? (
            <div className="border-t border-border pt-3">
              <Button type="button" variant="ghost" aria-expanded={expandedInactive} onClick={() => setExpandedInactive((value) => !value)}><L10n>{"Dị ứng đã ngừng hoặc ghi nhầm ("}</L10n>{current.allergies.inactive.length})
              </Button>
              <L10nValue>{expandedInactive ? <ul className="mt-2 space-y-2"><L10nValue>{current.allergies.inactive.map((allergy) => <li key={allergy.allergyId}><AllergyEntry allergy={allergy} /></li>)}</L10nValue></ul> : null}</L10nValue>
            </div>
          ) : null}</L10nValue>
          <L10nValue>{canAdd ? (
            <form onSubmit={submitAllergy} className="space-y-3 border-t border-border pt-4">
              <h3 className="font-semibold"><L10n>{"Ghi nhận dị ứng"}</L10n></h3>
              <fieldset disabled={command.busy || command.uncertain} className="space-y-3">
                <Field label={localize("Dị nguyên")} htmlFor="allergy-substance" required hint="Tối đa 200 ký tự.">
                  <input id="allergy-substance" value={substance} maxLength={200} required className={controlClassName} onChange={(event) => setSubstance(event.target.value)} />
                </Field>
                <Field label={localize("Phản ứng")} htmlFor="allergy-reaction" hint="Tối đa 500 ký tự.">
                  <textarea id="allergy-reaction" rows={2} value={reaction} maxLength={500} className={controlClassName} onChange={(event) => setReaction(event.target.value)} />
                </Field>
                <Field label={localize("Mức độ")} htmlFor="allergy-severity">
                  <select id="allergy-severity" className={controlClassName} value={severity} onChange={(event) => setSeverity(event.target.value as AllergySeverity)}>
                    <L10nValue>{Object.entries(severityLabels).map(([value, label]) => <option key={value} value={value}><L10nValue>{label}</L10nValue></option>)}</L10nValue>
                  </select>
                </Field>
                <Button type="submit" variant="primary" loading={command.busy} disabled={!substance.trim() || substance.trim().length > 200}><L10n>{"Ghi nhận dị ứng"}</L10n></Button>
              </fieldset>
            </form>
          ) : null}</L10nValue>
          <L10nValue>{command.error ? <CommandFeedback error={command.error} uncertain={command.uncertain} /> : null}</L10nValue>
          <L10nValue>{command.uncertain ? <Button type="button" variant="secondary" onClick={retryUncertainCommand} data-testid="allergy-command-retry" disabled={command.busy}><L10n>{"Gửi lại cùng yêu cầu"}</L10n></Button> : null}</L10nValue>
          <L10nValue>{statusTarget ? (
            <div role="group" aria-label={localize(`Cập nhật dị ứng ${statusTarget.substance}`)} className="space-y-3 rounded-lg border border-border p-3">
              <p className="text-sm"><L10n>{"Đánh dấu “"}</L10n><L10nValue>{statusTarget.substance}</L10nValue><L10n>{"” là đã nhập nhầm?"}</L10n></p>
              <Field label={localize("Lý do cập nhật tình trạng")} htmlFor="allergy-status-reason" required>
                <textarea id="allergy-status-reason" rows={2} maxLength={500} value={statusReason} disabled={command.busy || command.uncertain} className={controlClassName} onChange={(event) => setStatusReason(event.target.value)} />
              </Field>
              <div className="flex gap-2">
                <Button type="button" variant="primary" loading={command.busy} disabled={!statusReason.trim() || command.uncertain} onClick={confirmStatusChange}><L10n>{"Xác nhận"}</L10n></Button>
                <Button type="button" onClick={() => setStatusTarget(null)} disabled={command.busy || command.uncertain}><L10n>{"Hủy"}</L10n></Button>
              </div>
            </div>
          ) : null}</L10nValue>
        </>
      ) : null}</L10nValue>
    </section>
  );
}
