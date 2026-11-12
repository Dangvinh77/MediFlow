"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useCallback, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { medicalRecordApi } from "../api";
import type { RecordHandoverDTO } from "../types";
import { formatClinicalInstant, IdempotentAction, PanelReadState, usePanelResource } from "./record-collaboration-ui";

const arrayIsEmpty = (value: RecordHandoverDTO[]) => value.length === 0;

export function RecordHandoverPanel({ recordId, departmentId, canHandover, canAcceptHandover, onCommandSuccess }: {
  recordId: string;
  departmentId: string;
  canHandover: boolean;
  canAcceptHandover: boolean;
  onCommandSuccess?: () => void;
}) {
  const { localize } = useLocale();
  const read = useCallback(() => medicalRecordApi.handovers(recordId), [recordId]);
  const { state, retry } = usePanelResource(`handovers:${recordId}`, true, read, arrayIsEmpty);
  const [showForm, setShowForm] = useState(false);
  const [toDoctorId, setToDoctorId] = useState("");
  const [reason, setReason] = useState("");
  const [pendingItems, setPendingItems] = useState("");
  const [commandLocked, setCommandLocked] = useState(false);
  const refresh = () => { setShowForm(false); setToDoctorId(""); setReason(""); setPendingItems(""); retry(); onCommandSuccess?.(); };

  if (state.status === "hidden") return null;
  if (state.status !== "empty" && state.status !== "loaded") return <PanelReadState label={localize("Bàn giao bác sĩ")} state={state} loadingMessage={localize("Đang tải bàn giao…")} deniedMessage="Bạn không có quyền xem bàn giao của hồ sơ này." retryMessage="Không thể tải danh sách bàn giao." emptyMessage={localize("Chưa có bàn giao.")} onRetry={retry} />;

  const handovers = state.status === "loaded" ? state.data : [];
  const hasPendingHandover = handovers.some((item) => item.status === "PENDING");
  const hasAcceptedHandover = handovers.some((item) => item.status === "ACCEPTED");
  const requestBody = { toDoctorId: toDoctorId.trim(), reason: reason.trim(), pendingItems: pendingItems.trim() };
  return <section role="region" aria-label={localize("Bàn giao bác sĩ")} className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <header className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-lg font-semibold"><L10n>{"Bàn giao bác sĩ"}</L10n></h2><p className="mt-1 text-sm text-muted-foreground"><L10n>{"Bàn giao ghi rõ người nhận, lý do và việc còn chờ."}</L10n></p></div>
      <L10nValue>{canHandover && !showForm ? <Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setShowForm(true)}><L10n>{"Tạo bàn giao"}</L10n></Button> : null}</L10nValue>
    </header>
    <L10nValue>{state.status === "empty" ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Chưa có bàn giao."}</L10n></p> : null}</L10nValue>
    <L10nValue>{handovers.length ? <ul className="divide-y divide-border"><L10nValue>{handovers.map((handover) => <li key={handover.handoverId} className="space-y-2 py-3 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-center justify-between gap-2"><span className="font-medium"><L10nValue>{handover.status === "PENDING" ? "Đang chờ người nhận" : handover.status === "SUPERSEDED" ? "Đã thay bằng yêu cầu mới" : "Đã tiếp nhận"}</L10nValue></span><time className="text-xs text-muted-foreground"><L10nValue>{formatClinicalInstant(handover.acceptedAt ?? handover.requestedAt)}</L10nValue></time></div>
      <p className="text-sm"><L10n>{"Lý do: "}</L10n>{handover.reason}</p><p className="whitespace-pre-wrap text-sm"><L10n>{"Việc còn chờ: "}</L10n>{handover.pendingItems}</p>
      <dl className="grid gap-3 text-xs text-muted-foreground sm:grid-cols-2"><div><dt><L10n>{"Bác sĩ bàn giao"}</L10n></dt><dd className="mt-1 break-all font-mono">{handover.fromDoctorId}</dd></div><div><dt><L10n>{"Bác sĩ nhận"}</L10n></dt><dd className="mt-1 break-all font-mono">{handover.toDoctorId}</dd></div></dl>
      <L10nValue>{handover.status === "PENDING" && canAcceptHandover ? <IdempotentAction<RecordHandoverDTO> scope={`record-handover:accept:${recordId}:${handover.handoverId}`} label={localize("Chấp nhận bàn giao")} body={{}} disabled={commandLocked} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.acceptHandover(recordId, handover.handoverId, options)} onSuccess={refresh} /> : null}</L10nValue>
    </li>)}</L10nValue></ul> : null}</L10nValue>
    <L10nValue>{canHandover && showForm ? <form className="space-y-3 rounded-lg border border-border p-4" onSubmit={(event) => event.preventDefault()}>
      <L10nValue>{hasPendingHandover ? <p className="text-sm text-muted-foreground"><L10n>{"Gửi bàn giao mới sẽ thay yêu cầu đang chờ."}</L10n></p> : null}</L10nValue>
      <Field label={localize("Bác sĩ nhận bàn giao")} htmlFor={`handover-doctor-${recordId}`} required hint="Chọn bác sĩ trong cùng khoa."><ReferenceInput kind="doctor" scope={departmentId} id={`handover-doctor-${recordId}`} value={toDoctorId} onValueChange={setToDoctorId} disabled={commandLocked} placeholder={localize("Tìm bác sĩ trong khoa")} /></Field>
      <Field label={localize("Lý do bàn giao")} htmlFor={`handover-reason-${recordId}`} required><textarea id={`handover-reason-${recordId}`} value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} disabled={commandLocked} className={controlClassName} /></Field>
      <Field label={localize("Việc còn chờ")} htmlFor={`handover-pending-${recordId}`} required><textarea id={`handover-pending-${recordId}`} value={pendingItems} maxLength={2000} onChange={(event) => setPendingItems(event.target.value)} disabled={commandLocked} className={controlClassName} /></Field>
      <div className="flex flex-wrap items-center gap-3"><IdempotentAction<RecordHandoverDTO> scope={`record-handover:request:${recordId}`} label={localize("Gửi bàn giao")} body={requestBody} disabled={commandLocked || !requestBody.toDoctorId || !requestBody.reason || !requestBody.pendingItems} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.requestHandover(recordId, requestBody, options)} onSuccess={refresh} /><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setShowForm(false)}><L10n>{"Hủy"}</L10n></Button></div>
    </form> : null}</L10nValue>
    <L10nValue>{hasPendingHandover && !canAcceptHandover ? <p className="text-sm text-muted-foreground"><L10n>{"Bác sĩ hiện tại vẫn phụ trách cho tới khi bác sĩ nhận chấp nhận bàn giao."}</L10n></p> : null}</L10nValue>
    <L10nValue>{hasAcceptedHandover && !hasPendingHandover ? <p className="text-sm text-muted-foreground"><L10n>{"Bác sĩ cũ chỉ xem được nội dung sau khi bàn giao."}</L10n></p> : null}</L10nValue>
  </section>;
}
