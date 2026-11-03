"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { useCommand } from "@/lib/use-command";
import { medicalRecordApi } from "../api";
import type { MedicalRecordDTO, RecordDisposition } from "../types";

const dispositions = [
  ["OUTPATIENT_FOLLOW_UP", "Theo dõi ngoại trú"],
  ["PRESCRIPTION", "Kê đơn thuốc"],
  ["OTHER", "Khác"],
] as const;

export function CompleteRecordForm({ record, onCompleted, disabled = false, isInteractionBlocked, onActivityChange, onBusyChange }: { record: MedicalRecordDTO; onCompleted: (record: MedicalRecordDTO) => void; disabled?: boolean; isInteractionBlocked?: () => boolean; onActivityChange?: (active: boolean) => void; onBusyChange?: (busy: boolean) => void }) {
  const command = useCommand();
  const [disposition, setDisposition] = useState<RecordDisposition>("OUTPATIENT_FOLLOW_UP");
  const [note, setNote] = useState("");
  const [confirm, setConfirm] = useState(false);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (disabled || isInteractionBlocked?.() || !confirm || command.busy || command.uncertain || record.status !== "OPEN" || !record.diagnoses.length) return;
    onBusyChange?.(true);
    onActivityChange?.(true);
    void command.run(() => medicalRecordApi.complete(record.recordId, { disposition, dispositionNote: note.trim() || null }), (updated) => { onActivityChange?.(false); onCompleted(updated); }).finally(() => onBusyChange?.(false));
  }
  return <form onSubmit={submit} className="space-y-4">
    {!record.diagnoses.length ? <p role="status" className="text-sm text-warning">Cần thêm ít nhất một chẩn đoán trước khi hoàn tất.</p> : null}
    <CommandFeedback error={command.error} uncertain={command.uncertain} />
    <fieldset disabled={disabled || command.busy || command.uncertain} className="space-y-4">
      <Field label="Hướng xử trí" htmlFor="complete-disposition" required>
        <select id="complete-disposition" className={controlClassName} value={disposition} onChange={(event) => { setDisposition(event.target.value as RecordDisposition); setConfirm(false); onActivityChange?.(true); }}>
          {dispositions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </Field>
      <Field label="Ghi chú xử trí" htmlFor="complete-note" hint="Tối đa 2.000 ký tự.">
        <textarea id="complete-note" rows={3} maxLength={2000} className={controlClassName} value={note} onChange={(event) => { setNote(event.target.value); setConfirm(false); onActivityChange?.(true); }} />
      </Field>
      <label className="flex items-start gap-3 text-sm"><input type="checkbox" className="mt-1" checked={confirm} onChange={(event) => { setConfirm(event.target.checked); onActivityChange?.(true); }} />Tôi đã kiểm tra chẩn đoán, chỉ định và xác nhận hoàn tất lần khám.</label>
      <Button type="submit" variant="primary" loading={command.busy} disabled={disabled || !confirm || !record.diagnoses.length}>Hoàn tất hồ sơ khám</Button>
    </fieldset>
    {command.uncertain ? <p className="text-sm text-muted-foreground">Tải lại hồ sơ để xác nhận kết quả trước khi thực hiện thêm thao tác.</p> : null}
  </form>;
}
