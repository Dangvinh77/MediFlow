"use client";

import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { useCommand } from "@/lib/use-command";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";
import { createPendingLabOrder, hasUnresolvedLabOrder } from "../pending-lab-order";
import type { ClinicalLabOrderDTO } from "../types";

function localToday() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function ClinicalLabOrderForm({ recordId, onCreated }: { recordId: string; onCreated: (order: ClinicalLabOrderDTO) => void }) {
  const command = useCommand();
  const { markDirty, markSaved } = useUnsavedChanges();
  const [labType, setLabType] = useState("");
  const [requestedDate, setRequestedDate] = useState(localToday);
  const [priceCode, setPriceCode] = useState("");
  const [created, setCreated] = useState(false);
  const [validation, setValidation] = useState<string | null>(null);
  const [restoredUncertain] = useState(() => hasUnresolvedLabOrder(recordId));

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (created || command.busy || command.uncertain || restoredUncertain) return;
    if (!labType.trim() || !priceCode.trim() || !requestedDate || requestedDate > localToday()) {
      setValidation("Nhập loại xét nghiệm, mã giá và ngày yêu cầu không ở tương lai."); return;
    }
    setValidation(null);
    markDirty();
    void command.run(() => createPendingLabOrder(recordId, { labType: labType.trim(), requestedDate, priceCode: priceCode.trim() }),
      (order) => { markSaved(); setCreated(true); onCreated(order); });
  }
  return <form onSubmit={submit} onChange={markDirty} className="space-y-4 rounded-xl border border-border bg-surface p-6">
    <h2 className="text-lg font-semibold">Chỉ định xét nghiệm</h2>
    <p className="text-sm text-muted-foreground">Chỉ định gắn với hồ sơ đang khám. Nhập mã giá xét nghiệm được cơ sở phê duyệt.</p>
    <CommandFeedback error={command.error} uncertain={command.uncertain} />
    {validation ? <p role="alert" className="text-sm text-danger">{validation}</p> : null}
    <fieldset disabled={command.busy || command.uncertain || restoredUncertain || created} className="grid gap-4 sm:grid-cols-2">
      <Field label="Loại xét nghiệm" htmlFor="order-lab-type" required><input id="order-lab-type" required maxLength={50} className={controlClassName} value={labType} onChange={(event) => setLabType(event.target.value)} /></Field>
      <Field label="Mã giá" htmlFor="order-price" required><input id="order-price" required maxLength={64} className={controlClassName} value={priceCode} onChange={(event) => setPriceCode(event.target.value)} /></Field>
      <Field label="Ngày yêu cầu" htmlFor="order-date" required><input id="order-date" required type="date" max={localToday()} className={controlClassName} value={requestedDate} onChange={(event) => setRequestedDate(event.target.value)} /></Field>
      <div className="sm:col-span-2"><Button type="submit" variant="primary" loading={command.busy}>Gửi chỉ định xét nghiệm</Button></div>
    </fieldset>
    {command.uncertain || restoredUncertain ? <p className="text-sm text-warning">Chưa xác định kết quả lập chỉ định trong hồ sơ. Không lập lại; cần đối soát hồ sơ với bộ phận hỗ trợ trước khi tiếp tục. Lab có thể chưa nhận được yêu cầu.</p> : null}
  </form>;
}
