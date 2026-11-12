"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { prepareCommandIntent } from "@/lib/command-intent";
import { getSessionKey } from "@/lib/session";
import { useCommand } from "@/lib/use-command";
import { medicalRecordApi } from "../api";
import { closureReasonLabels } from "../closure";
import type { MedicalRecordDTO, RecordClosureReason } from "../types";

const reasons = Object.entries(closureReasonLabels) as [RecordClosureReason, string][];

interface CloseRecordFormProps {
  record: MedicalRecordDTO;
  onClosed: (record: MedicalRecordDTO) => void;
  disabled?: boolean;
  isInteractionBlocked?: () => boolean;
  onActivityChange?: (active: boolean) => void;
  onBusyChange?: (busy: boolean) => void;
}

export function CloseRecordForm({ record, onClosed, disabled = false, isInteractionBlocked, onActivityChange, onBusyChange }: CloseRecordFormProps) {
  const { localize } = useLocale();
  const command = useCommand();
  const [reason, setReason] = useState<RecordClosureReason>("PATIENT_LEFT");
  const [note, setNote] = useState("");
  const [confirm, setConfirm] = useState(false);
  const trimmed = note.trim();
  const ready = confirm && trimmed.length > 0;

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled || isInteractionBlocked?.() || !ready || command.busy || command.uncertain || record.status !== "OPEN") return;
    const body = { reason, note: trimmed };
    onBusyChange?.(true);
    onActivityChange?.(true);
    void command.run(async () => {
      const intent = await prepareCommandIntent(`close-record:${record.recordId}`, getSessionKey(), body);
      try {
        const closed = await medicalRecordApi.closeWithoutConclusion(record.recordId, body, { idempotencyKey: intent.key, correlationId: intent.correlationId });
        intent.settle();
        return closed;
      } catch (cause) {
        intent.settle(cause);
        throw cause;
      }
    }, (closed) => { onActivityChange?.(false); onClosed(closed); }).finally(() => onBusyChange?.(false));
  }

  return <form onSubmit={submit} className="space-y-4">
    <CommandFeedback error={command.error} uncertain={command.uncertain} />
    <L10nValue>{command.error?.code === "CLINICAL_LAB_ORDER_PENDING"
      ? <p className="text-sm"><a href="#record-lab-orders" className="font-medium underline underline-offset-4"><L10n>{"Xem chỉ định xét nghiệm"}</L10n></a><L10n>{" để hủy, hoãn hoặc chờ kết quả trước khi kết thúc."}</L10n></p>
      : null}</L10nValue>
    <L10nValue>{command.error?.code === "CLINICAL_LAB_ORDER_NOT_FOUND"
      ? <p className="text-sm"><L10n>{"Rút chỉ định Lab chưa tiếp nhận rồi kết thúc lại. "}</L10n><a href="#record-lab-orders" className="font-medium underline underline-offset-4"><L10n>{"Xem chỉ định xét nghiệm"}</L10n></a></p>
      : null}</L10nValue>
    <fieldset disabled={disabled || command.busy || command.uncertain} className="space-y-4">
      <Field label={localize("Lý do kết thúc")} htmlFor="close-reason" required>
        <select id="close-reason" className={controlClassName} value={reason} onChange={(event) => { setReason(event.target.value as RecordClosureReason); onActivityChange?.(true); }}>
          <L10nValue>{reasons.map(([value, label]) => <option key={value} value={value}><L10nValue>{label}</L10nValue></option>)}</L10nValue>
        </select>
      </Field>
      <Field label={localize("Ghi chú và dặn dò đã đưa")} htmlFor="close-note" required hint="Tối đa 1.000 ký tự.">
        <textarea id="close-note" rows={3} maxLength={1000} className={controlClassName} value={note} onChange={(event) => { setNote(event.target.value); onActivityChange?.(true); }} />
      </Field>
      <label className="flex items-start gap-3 text-sm"><input type="checkbox" className="mt-1" checked={confirm} onChange={(event) => setConfirm(event.target.checked)} /><L10n>{" Tôi xác nhận kết thúc lượt khám không có kết luận. Hồ sơ sẽ không thể chỉnh sửa và phí khám đã thu không tự hoàn."}</L10n></label>
      <Button type="submit" variant="danger" loading={command.busy} disabled={disabled || !ready}><L10n>{"Kết thúc lượt khám"}</L10n></Button>
    </fieldset>
    <L10nValue>{command.uncertain ? <p className="text-sm text-muted-foreground"><L10n>{"Tải lại hồ sơ để xác nhận kết quả trước khi thao tác tiếp."}</L10n></p> : null}</L10nValue>
  </form>;
}
