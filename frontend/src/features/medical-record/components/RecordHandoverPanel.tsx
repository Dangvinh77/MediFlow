"use client";

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
  const read = useCallback(() => medicalRecordApi.handovers(recordId), [recordId]);
  const { state, retry } = usePanelResource(`handovers:${recordId}`, true, read, arrayIsEmpty);
  const [showForm, setShowForm] = useState(false);
  const [toDoctorId, setToDoctorId] = useState("");
  const [reason, setReason] = useState("");
  const [pendingItems, setPendingItems] = useState("");
  const [commandLocked, setCommandLocked] = useState(false);
  const refresh = () => { setShowForm(false); setToDoctorId(""); setReason(""); setPendingItems(""); retry(); onCommandSuccess?.(); };

  if (state.status === "hidden") return null;
  if (state.status !== "empty" && state.status !== "loaded") return <PanelReadState label="Bàn giao bác sĩ" state={state} loadingMessage="Đang tải bàn giao…" deniedMessage="Bạn không có quyền xem bàn giao của hồ sơ này." retryMessage="Không thể tải danh sách bàn giao." emptyMessage="Chưa có bàn giao." onRetry={retry} />;

  const handovers = state.status === "loaded" ? state.data : [];
  const hasPendingHandover = handovers.some((item) => item.status === "PENDING");
  const requestBody = { toDoctorId: toDoctorId.trim(), reason: reason.trim(), pendingItems: pendingItems.trim() };
  return <section role="region" aria-label="Bàn giao bác sĩ" className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <header className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-lg font-semibold">Bàn giao bác sĩ</h2><p className="mt-1 text-sm text-muted-foreground">Bàn giao ghi rõ người nhận, lý do và việc còn chờ.</p></div>
      {canHandover && !hasPendingHandover && !showForm ? <Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setShowForm(true)}>Tạo bàn giao</Button> : null}
    </header>
    {state.status === "empty" ? <p role="status" className="text-sm text-muted-foreground">Chưa có bàn giao.</p> : null}
    {handovers.length ? <ul className="divide-y divide-border">{handovers.map((handover) => <li key={handover.handoverId} className="space-y-2 py-3 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-center justify-between gap-2"><span className="font-medium">{handover.status === "PENDING" ? "Đang chờ người nhận" : "Đã tiếp nhận"}</span><time className="text-xs text-muted-foreground">{formatClinicalInstant(handover.acceptedAt ?? handover.requestedAt)}</time></div>
      <p className="text-sm">Lý do: {handover.reason}</p><p className="whitespace-pre-wrap text-sm">Việc còn chờ: {handover.pendingItems}</p>
      <dl className="grid gap-3 text-xs text-muted-foreground sm:grid-cols-2"><div><dt>Bác sĩ bàn giao</dt><dd className="mt-1 break-all font-mono">{handover.fromDoctorId}</dd></div><div><dt>Bác sĩ nhận</dt><dd className="mt-1 break-all font-mono">{handover.toDoctorId}</dd></div></dl>
      {handover.status === "PENDING" && canAcceptHandover ? <IdempotentAction<RecordHandoverDTO> scope={`record-handover:accept:${recordId}:${handover.handoverId}`} label="Chấp nhận bàn giao" body={{}} disabled={commandLocked} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.acceptHandover(recordId, handover.handoverId, options)} onSuccess={refresh} /> : null}
    </li>)}</ul> : null}
    {canHandover && !hasPendingHandover && showForm ? <form className="space-y-3 rounded-lg border border-border p-4" onSubmit={(event) => event.preventDefault()}>
      <Field label="Bác sĩ nhận bàn giao" htmlFor={`handover-doctor-${recordId}`} required hint="Chọn bác sĩ trong cùng khoa."><ReferenceInput kind="doctor" scope={departmentId} id={`handover-doctor-${recordId}`} value={toDoctorId} onValueChange={setToDoctorId} disabled={commandLocked} placeholder="Tìm bác sĩ trong khoa" /></Field>
      <Field label="Lý do bàn giao" htmlFor={`handover-reason-${recordId}`} required><textarea id={`handover-reason-${recordId}`} value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} disabled={commandLocked} className={controlClassName} /></Field>
      <Field label="Việc còn chờ" htmlFor={`handover-pending-${recordId}`} required><textarea id={`handover-pending-${recordId}`} value={pendingItems} maxLength={2000} onChange={(event) => setPendingItems(event.target.value)} disabled={commandLocked} className={controlClassName} /></Field>
      <div className="flex flex-wrap items-center gap-3"><IdempotentAction<RecordHandoverDTO> scope={`record-handover:request:${recordId}`} label="Gửi bàn giao" body={requestBody} disabled={commandLocked || !requestBody.toDoctorId || !requestBody.reason || !requestBody.pendingItems} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.requestHandover(recordId, requestBody, options)} onSuccess={refresh} /><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setShowForm(false)}>Hủy</Button></div>
    </form> : null}
    {hasPendingHandover && !canAcceptHandover ? <p className="text-sm text-muted-foreground">Bác sĩ cũ chỉ xem được nội dung sau khi bàn giao.</p> : null}
  </section>;
}
