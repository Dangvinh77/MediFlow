"use client";

import { useCallback, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { medicalRecordApi } from "../api";
import type { AmendmentSection, RecordAmendmentDTO, RecordAmendmentDecision } from "../types";
import { formatClinicalInstant, IdempotentAction, PanelReadState, usePanelResource } from "./record-collaboration-ui";

const arrayIsEmpty = (value: RecordAmendmentDTO[]) => value.length === 0;
const sections: Array<[AmendmentSection, string]> = [
  ["SYMPTOMS", "Triệu chứng"], ["CHIEF_COMPLAINT", "Lý do khám"], ["PRESENT_ILLNESS", "Bệnh sử hiện tại"],
  ["PAST_HISTORY", "Tiền sử"], ["ALLERGY_NOTE", "Ghi chú dị ứng của lượt"], ["PHYSICAL_EXAM", "Khám thực thể"],
  ["ADVICE", "Dặn dò"], ["DISPOSITION_NOTE", "Ghi chú hướng xử trí"], ["CLOSURE_NOTE", "Ghi chú kết thúc"],
  ["DIAGNOSIS", "Mô tả chẩn đoán"],
];
const sectionNames = Object.fromEntries(sections) as Record<AmendmentSection, string>;

export function RecordAmendmentsPanel({ recordId, departmentId, canAmend, canDecideAmendments, canAssign, currentStaffId, onCommandSuccess }: {
  recordId: string;
  departmentId: string;
  canAmend: boolean;
  canDecideAmendments: boolean;
  canAssign?: boolean;
  currentStaffId: string | null;
  onCommandSuccess?: () => void;
}) {
  const read = useCallback(() => medicalRecordApi.amendments(recordId), [recordId]);
  const { state, retry } = usePanelResource(`amendments:${recordId}`, true, read, arrayIsEmpty);
  const [section, setSection] = useState<AmendmentSection | "">("");
  const [amendedText, setAmendedText] = useState("");
  const [reason, setReason] = useState("");
  const [decisionTarget, setDecisionTarget] = useState<RecordAmendmentDTO | null>(null);
  const [decision, setDecision] = useState<RecordAmendmentDecision>("APPROVED");
  const [decisionNote, setDecisionNote] = useState("");
  const [showAssignment, setShowAssignment] = useState(false);
  const [assigneeStaffId, setAssigneeStaffId] = useState("");
  const [assignmentReason, setAssignmentReason] = useState("");
  const [commandLocked, setCommandLocked] = useState(false);
  const refresh = () => { setSection(""); setAmendedText(""); setReason(""); setDecisionTarget(null); setDecisionNote(""); setShowAssignment(false); setAssigneeStaffId(""); setAssignmentReason(""); retry(); onCommandSuccess?.(); };

  if (state.status === "hidden") return null;
  if (state.status !== "empty" && state.status !== "loaded") return <PanelReadState label="Đính chính hồ sơ" state={state} loadingMessage="Đang tải đính chính…" deniedMessage="Bạn không có quyền xem đính chính của hồ sơ này." retryMessage="Không thể tải danh sách đính chính." emptyMessage="Chưa có đính chính." onRetry={retry} />;

  const amendments = state.status === "loaded" ? state.data : [];
  const requestBody = { section: section as AmendmentSection, amendedText: amendedText.trim(), reason: reason.trim() };
  return <section role="region" aria-label="Đính chính hồ sơ" className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <header className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-lg font-semibold">Đính chính hồ sơ</h2><p className="mt-1 text-sm text-muted-foreground">Bản gốc được giữ nguyên; yêu cầu đính chính được lưu cùng lý do và quyết định.</p></div>{canAssign && !showAssignment ? <Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setShowAssignment(true)}>Phân công bác sĩ</Button> : null}</header>
    {state.status === "empty" ? <p role="status" className="text-sm text-muted-foreground">Chưa có đính chính.</p> : null}
    {amendments.length ? <ul className="divide-y divide-border">{amendments.map((amendment) => {
      const isAuthor = currentStaffId !== null && amendment.authorStaffId === currentStaffId;
      const canDecide = amendment.status === "PENDING" && canDecideAmendments && currentStaffId !== null && !isAuthor;
      return <li key={amendment.amendmentId} className="space-y-3 py-4 first:pt-0 last:pb-0">
        <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-semibold">{sectionNames[amendment.section]}</h3><span className="rounded-full bg-surface-muted px-3 py-1 text-xs font-medium">{amendment.status === "PENDING" ? "Chờ duyệt" : amendment.status === "APPROVED" ? "Đã duyệt" : "Đã từ chối"}</span></div>
        <div className="grid gap-3 md:grid-cols-2"><div className="rounded-lg bg-surface-muted p-3"><h4 className="text-xs font-medium text-muted-foreground">Nội dung ban đầu</h4><p className="mt-2 whitespace-pre-wrap text-sm">{amendment.originalText}</p></div><div className="rounded-lg border border-border p-3"><h4 className="text-xs font-medium text-muted-foreground">Nội dung đề nghị</h4><p className="mt-2 whitespace-pre-wrap text-sm">{amendment.amendedText}</p></div></div>
        <p className="whitespace-pre-wrap text-sm"><span className="font-medium">Lý do:</span> {amendment.reason}</p>
        <dl className="grid gap-2 text-xs text-muted-foreground sm:grid-cols-2"><div><dt>Người gửi</dt><dd className="mt-1 break-all font-mono">{amendment.authorStaffId}</dd></div><div><dt>Thời điểm gửi</dt><dd className="mt-1">{formatClinicalInstant(amendment.requestedAt)}</dd></div></dl>
        {amendment.assignedByStaffId ? <p className="text-xs text-muted-foreground">Được phân công bởi <span className="break-all font-mono">{amendment.assignedByStaffId}</span></p> : null}
        {amendment.status === "APPROVED" ? <p className="rounded-lg border border-success px-3 py-2 text-sm">Đã xác nhận nội bộ{amendment.contentHash ? <> · <span className="font-mono">{amendment.contentHash.slice(0, 12)}</span></> : null}</p> : null}
        {amendment.status !== "PENDING" && amendment.decidedAt ? <div className="text-sm text-muted-foreground">{amendment.status === "REJECTED" ? "Đã từ chối" : "Đã duyệt"} bởi <span className="break-all font-mono">{amendment.decidedByStaffId ?? "—"}</span> · {formatClinicalInstant(amendment.decidedAt)}{amendment.decisionNote ? <p className="mt-1 whitespace-pre-wrap">Ghi chú: {amendment.decisionNote}</p> : null}</div> : null}
        {canDecide ? <div className="flex flex-wrap gap-2"><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => { setDecisionTarget(amendment); setDecision("APPROVED"); setDecisionNote(""); }}>Duyệt đính chính</Button><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => { setDecisionTarget(amendment); setDecision("REJECTED"); setDecisionNote(""); }}>Từ chối đính chính</Button></div> : null}
        {canDecide && decisionTarget?.amendmentId === amendment.amendmentId ? <div className="space-y-3 rounded-lg border border-border p-4">
          <Field label="Ghi chú quyết định" htmlFor={`amendment-decision-note-${amendment.amendmentId}`} required><textarea id={`amendment-decision-note-${amendment.amendmentId}`} maxLength={1000} value={decisionNote} disabled={commandLocked} onChange={(event) => setDecisionNote(event.target.value)} className={controlClassName} /></Field>
          <div className="flex flex-wrap items-center gap-3"><IdempotentAction scope={`amendment:decision:${recordId}:${amendment.amendmentId}`} label={decision === "APPROVED" ? "Xác nhận duyệt" : "Xác nhận từ chối"} body={{ decision, note: decisionNote.trim() }} disabled={commandLocked || !decisionNote.trim()} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.decideAmendment(recordId, amendment.amendmentId, { decision, note: decisionNote.trim() }, options)} onSuccess={refresh} /><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setDecisionTarget(null)}>Hủy</Button></div>
        </div> : null}
      </li>;
    })}</ul> : null}
    {canAssign && showAssignment ? <form className="space-y-3 border-t border-border pt-4" onSubmit={(event) => event.preventDefault()}>
      <h3 className="font-semibold">Phân công người gửi đính chính</h3>
      <p className="text-sm text-muted-foreground">Dùng khi bác sĩ phụ trách hiện tại không còn hoạt động tại khoa.</p>
      <Field label="Bác sĩ được phân công" htmlFor={`amendment-assignee-${recordId}`} required><ReferenceInput kind="doctor" scope={departmentId} id={`amendment-assignee-${recordId}`} value={assigneeStaffId} onValueChange={setAssigneeStaffId} disabled={commandLocked} placeholder="Tìm bác sĩ trong khoa" /></Field>
      <Field label="Lý do phân công" htmlFor={`amendment-assignment-reason-${recordId}`} required><textarea id={`amendment-assignment-reason-${recordId}`} maxLength={1000} value={assignmentReason} disabled={commandLocked} onChange={(event) => setAssignmentReason(event.target.value)} className={controlClassName} /></Field>
      <div className="flex flex-wrap items-center gap-3"><IdempotentAction scope={`amendment:assignment:${recordId}`} label="Xác nhận phân công" body={{ assigneeStaffId, reason: assignmentReason.trim() }} disabled={commandLocked || !assigneeStaffId || !assignmentReason.trim()} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.assignAmendment(recordId, { assigneeStaffId, reason: assignmentReason.trim() }, options)} onSuccess={refresh} /><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setShowAssignment(false)}>Hủy</Button></div>
    </form> : null}
    {canAmend ? <form className="space-y-3 border-t border-border pt-4" onSubmit={(event) => event.preventDefault()}>
      <h3 className="font-semibold">Yêu cầu đính chính</h3>
      <Field label="Mục cần đính chính" htmlFor={`amendment-section-${recordId}`} required><select id={`amendment-section-${recordId}`} value={section} disabled={commandLocked} onChange={(event) => setSection(event.target.value as AmendmentSection | "")} className={controlClassName}><option value="">Chọn mục</option>{sections.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
      <Field label="Nội dung thay thế" htmlFor={`amendment-text-${recordId}`} required hint="Tối đa 4.000 ký tự."><textarea id={`amendment-text-${recordId}`} maxLength={4000} value={amendedText} disabled={commandLocked} onChange={(event) => setAmendedText(event.target.value)} className={controlClassName} /></Field>
      <Field label="Lý do đính chính" htmlFor={`amendment-reason-${recordId}`} required><textarea id={`amendment-reason-${recordId}`} maxLength={1000} value={reason} disabled={commandLocked} onChange={(event) => setReason(event.target.value)} className={controlClassName} /></Field>
      <IdempotentAction scope={`amendment:request:${recordId}`} label="Tạo yêu cầu đính chính" body={requestBody} disabled={commandLocked || !section || !requestBody.amendedText || !requestBody.reason} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.requestAmendment(recordId, requestBody, options)} onSuccess={refresh} />
    </form> : null}
  </section>;
}
