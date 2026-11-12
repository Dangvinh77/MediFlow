"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useCallback, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { ReferenceInput } from "@/components/references/ReferenceInput";
import { medicalRecordApi } from "../api";
import type { AmendmentSection, MedicalRecordDTO, RecordAmendmentDTO, RecordAmendmentDecision } from "../types";
import { formatClinicalInstant, IdempotentAction, PanelReadState, usePanelResource } from "./record-collaboration-ui";

const arrayIsEmpty = (value: RecordAmendmentDTO[]) => value.length === 0;
const sections: Array<[AmendmentSection, string]> = [
  ["SYMPTOMS", "Triệu chứng"], ["CHIEF_COMPLAINT", "Lý do khám"], ["PRESENT_ILLNESS", "Bệnh sử hiện tại"],
  ["PAST_HISTORY", "Tiền sử"], ["ALLERGY_NOTE", "Ghi chú dị ứng của lượt"], ["PHYSICAL_EXAM", "Khám thực thể"],
  ["ADVICE", "Dặn dò"], ["DISPOSITION_NOTE", "Ghi chú hướng xử trí"], ["CLOSURE_NOTE", "Ghi chú kết thúc"],
  ["DIAGNOSIS", "Mô tả chẩn đoán"],
];
const sectionNames = Object.fromEntries(sections) as Record<AmendmentSection, string>;

export function RecordAmendmentsPanel({ record, recordId, departmentId, canAmend, canApplyAmendment, canDecideAmendments, canAssign, currentStaffId, onCommandSuccess }: {
  record: MedicalRecordDTO;
  recordId: string;
  departmentId: string;
  canAmend: boolean;
  canApplyAmendment: boolean;
  canDecideAmendments: boolean;
  canAssign?: boolean;
  currentStaffId: string | null;
  onCommandSuccess?: () => void;
}) {
  const { localize } = useLocale();
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
  if (state.status !== "empty" && state.status !== "loaded") return <PanelReadState label={localize("Đính chính hồ sơ")} state={state} loadingMessage={localize("Đang tải đính chính…")} deniedMessage="Bạn không có quyền xem đính chính của hồ sơ này." retryMessage="Không thể tải danh sách đính chính." emptyMessage={localize("Chưa có đính chính.")} onRetry={retry} />;

  const amendments = state.status === "loaded" ? state.data : [];
  const requestBody = { section: section as AmendmentSection, originalText: amendmentCurrentText(record, section), amendedText: amendedText.trim(), reason: reason.trim() };
  return <section role="region" aria-label={localize("Đính chính hồ sơ")} className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <header className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-lg font-semibold"><L10n>{"Đính chính hồ sơ"}</L10n></h2><p className="mt-1 text-sm text-muted-foreground"><L10n>{"Bản gốc được giữ nguyên; yêu cầu đính chính được lưu cùng lý do và quyết định."}</L10n></p></div><L10nValue>{canAssign && !showAssignment ? <Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setShowAssignment(true)}><L10n>{"Phân công bác sĩ"}</L10n></Button> : null}</L10nValue></header>
    <L10nValue>{state.status === "empty" ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Chưa có đính chính."}</L10n></p> : null}</L10nValue>
    <L10nValue>{amendments.length ? <ul className="divide-y divide-border"><L10nValue>{amendments.map((amendment) => {
      const isAuthor = currentStaffId !== null && amendment.authorStaffId === currentStaffId;
      const canDecide = amendment.status === "PENDING" && canDecideAmendments && currentStaffId !== null && !isAuthor;
      return <li key={amendment.amendmentId} className="space-y-3 py-4 first:pt-0 last:pb-0">
        <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="font-semibold">{sectionNames[amendment.section]}</h3><span className="rounded-full bg-surface-muted px-3 py-1 text-xs font-medium"><L10nValue>{amendment.status === "PENDING" ? "Chờ duyệt" : amendment.status === "APPROVED" ? "Đã duyệt" : amendment.status === "APPLIED" ? "Đã áp dụng bởi bác sĩ phụ trách" : "Đã từ chối"}</L10nValue></span></div>
        <div className="grid gap-3 md:grid-cols-2"><div className="rounded-lg bg-surface-muted p-3"><h4 className="text-xs font-medium text-muted-foreground"><L10n>{"Nội dung ban đầu"}</L10n></h4><p className="mt-2 whitespace-pre-wrap text-sm">{amendment.originalText}</p></div><div className="rounded-lg border border-border p-3"><h4 className="text-xs font-medium text-muted-foreground"><L10nValue>{amendment.status === "APPLIED" || amendment.status === "APPROVED" ? "Nội dung đã áp dụng" : "Nội dung đề nghị"}</L10nValue></h4><p className="mt-2 whitespace-pre-wrap text-sm">{amendment.amendedText}</p></div></div>
        <p className="whitespace-pre-wrap text-sm"><span className="font-medium"><L10n>{"Lý do:"}</L10n></span> {amendment.reason}</p>
        <dl className="grid gap-2 text-xs text-muted-foreground sm:grid-cols-2"><div><dt><L10n>{"Người gửi"}</L10n></dt><dd className="mt-1 break-all font-mono">{amendment.authorStaffId}</dd></div><div><dt><L10n>{"Thời điểm gửi"}</L10n></dt><dd className="mt-1"><L10nValue>{formatClinicalInstant(amendment.requestedAt)}</L10nValue></dd></div></dl>
        <L10nValue>{amendment.assignedByStaffId ? <p className="text-xs text-muted-foreground"><L10n>{"Được phân công bởi "}</L10n><span className="break-all font-mono">{amendment.assignedByStaffId}</span></p> : null}</L10nValue>
        <L10nValue>{amendment.status === "APPROVED" ? <p className="rounded-lg border border-success px-3 py-2 text-sm"><L10n>{"Đã xác nhận nội bộ"}</L10n><L10nValue>{amendment.contentHash ? <> · <span className="font-mono"><L10nValue>{amendment.contentHash.slice(0, 12)}</L10nValue></span></> : null}</L10nValue></p> : null}</L10nValue>
        <L10nValue>{amendment.status === "APPLIED" && amendment.contentHash ? <p className="text-xs text-muted-foreground"><L10n>{"Mã băm nội dung: "}</L10n><span className="font-mono">{amendment.contentHash.slice(0, 12)}</span></p> : null}</L10nValue>
        <L10nValue>{amendment.status !== "PENDING" && amendment.decidedAt ? <div className="text-sm text-muted-foreground"><L10nValue>{amendment.status === "REJECTED" ? "Đã từ chối" : amendment.status === "APPLIED" ? "Đã áp dụng trực tiếp bởi bác sĩ phụ trách" : "Đã duyệt"}</L10nValue><L10n>{" bởi "}</L10n><span className="break-all font-mono"><L10nValue>{amendment.decidedByStaffId ?? "—"}</L10nValue></span> · <L10nValue>{formatClinicalInstant(amendment.decidedAt)}</L10nValue><L10nValue>{amendment.decisionNote ? <p className="mt-1 whitespace-pre-wrap"><L10n>{"Ghi chú: "}</L10n>{amendment.decisionNote}</p> : null}</L10nValue></div> : null}</L10nValue>
        <L10nValue>{canDecide ? <div className="flex flex-wrap gap-2"><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => { setDecisionTarget(amendment); setDecision("APPROVED"); setDecisionNote(""); }}><L10n>{"Duyệt đính chính"}</L10n></Button><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => { setDecisionTarget(amendment); setDecision("REJECTED"); setDecisionNote(""); }}><L10n>{"Từ chối đính chính"}</L10n></Button></div> : null}</L10nValue>
        <L10nValue>{canDecide && decisionTarget?.amendmentId === amendment.amendmentId ? <div className="space-y-3 rounded-lg border border-border p-4">
          <Field label={localize("Ghi chú quyết định")} htmlFor={`amendment-decision-note-${amendment.amendmentId}`} required><textarea id={`amendment-decision-note-${amendment.amendmentId}`} maxLength={1000} value={decisionNote} disabled={commandLocked} onChange={(event) => setDecisionNote(event.target.value)} className={controlClassName} /></Field>
          <div className="flex flex-wrap items-center gap-3"><IdempotentAction scope={`amendment:decision:${recordId}:${amendment.amendmentId}`} label={localize(decision === "APPROVED" ? "Xác nhận duyệt" : "Xác nhận từ chối")} body={{ decision, note: decisionNote.trim() }} disabled={commandLocked || !decisionNote.trim()} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.decideAmendment(recordId, amendment.amendmentId, { decision, note: decisionNote.trim() }, options)} onSuccess={refresh} /><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setDecisionTarget(null)}><L10n>{"Hủy"}</L10n></Button></div>
        </div> : null}</L10nValue>
      </li>;
    })}</L10nValue></ul> : null}</L10nValue>
    <L10nValue>{canAssign && showAssignment ? <form className="space-y-3 border-t border-border pt-4" onSubmit={(event) => event.preventDefault()}>
      <h3 className="font-semibold"><L10n>{"Phân công người gửi đính chính"}</L10n></h3>
      <p className="text-sm text-muted-foreground"><L10n>{"Dùng khi bác sĩ phụ trách hiện tại không còn hoạt động tại khoa."}</L10n></p>
      <Field label={localize("Bác sĩ được phân công")} htmlFor={`amendment-assignee-${recordId}`} required><ReferenceInput kind="doctor" scope={departmentId} id={`amendment-assignee-${recordId}`} value={assigneeStaffId} onValueChange={setAssigneeStaffId} disabled={commandLocked} placeholder={localize("Tìm bác sĩ trong khoa")} /></Field>
      <Field label={localize("Lý do phân công")} htmlFor={`amendment-assignment-reason-${recordId}`} required><textarea id={`amendment-assignment-reason-${recordId}`} maxLength={1000} value={assignmentReason} disabled={commandLocked} onChange={(event) => setAssignmentReason(event.target.value)} className={controlClassName} /></Field>
      <div className="flex flex-wrap items-center gap-3"><IdempotentAction scope={`amendment:assignment:${recordId}`} label={localize("Xác nhận phân công")} body={{ assigneeStaffId, reason: assignmentReason.trim() }} disabled={commandLocked || !assigneeStaffId || !assignmentReason.trim()} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.assignAmendment(recordId, { assigneeStaffId, reason: assignmentReason.trim() }, options)} onSuccess={refresh} /><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setShowAssignment(false)}><L10n>{"Hủy"}</L10n></Button></div>
    </form> : null}</L10nValue>
    <L10nValue>{canAmend ? <form className="space-y-3 border-t border-border pt-4" onSubmit={(event) => event.preventDefault()}>
      <h3 className="font-semibold"><L10nValue>{canApplyAmendment ? "Áp dụng đính chính vào hồ sơ hoàn tất" : "Yêu cầu đính chính"}</L10nValue></h3>
      <Field label={localize("Mục cần đính chính")} htmlFor={`amendment-section-${recordId}`} required><select id={`amendment-section-${recordId}`} value={section} disabled={commandLocked} onChange={(event) => setSection(event.target.value as AmendmentSection | "")} className={controlClassName}><option value=""><L10n>{"Chọn mục"}</L10n></option><L10nValue>{sections.map(([value, label]) => <option key={value} value={value}><L10nValue>{label}</L10nValue></option>)}</L10nValue></select></Field>
      <Field label={localize(canApplyAmendment ? "Nội dung cần sửa" : "Nội dung thay thế")} htmlFor={`amendment-text-${recordId}`} required hint="Tối đa 4.000 ký tự."><textarea id={`amendment-text-${recordId}`} maxLength={4000} value={amendedText} disabled={commandLocked} onChange={(event) => setAmendedText(event.target.value)} className={controlClassName} /></Field>
      <Field label={localize("Lý do đính chính")} htmlFor={`amendment-reason-${recordId}`} required><textarea id={`amendment-reason-${recordId}`} maxLength={1000} value={reason} disabled={commandLocked} onChange={(event) => setReason(event.target.value)} className={controlClassName} /></Field>
      <IdempotentAction scope={`amendment:request:${recordId}`} label={localize(canApplyAmendment ? "Áp dụng đính chính" : "Tạo yêu cầu đính chính")} body={requestBody} disabled={commandLocked || !section || !requestBody.amendedText || !requestBody.reason} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.requestAmendment(recordId, requestBody, options)} onSuccess={refresh} />
    </form> : null}</L10nValue>
  </section>;
}

function amendmentCurrentText(record: MedicalRecordDTO, section: AmendmentSection | "") {
  if (!section) return "";
  const effective = record.effectiveCorrections?.find((correction) => correction.section === section);
  if (effective) return effective.correctedText;
  switch (section) {
    case "SYMPTOMS": return record.symptoms ?? "";
    case "CHIEF_COMPLAINT": return record.chiefComplaint ?? "";
    case "PRESENT_ILLNESS": return record.presentIllness ?? "";
    case "PAST_HISTORY": return record.pastHistory ?? "";
    case "ALLERGY_NOTE": return record.allergyNote ?? "";
    case "PHYSICAL_EXAM": return record.physicalExam ?? "";
    case "ADVICE": return record.advice ?? "";
    case "DISPOSITION_NOTE": return record.dispositionNote ?? "";
    case "CLOSURE_NOTE": return record.closureNote ?? "";
    case "DIAGNOSIS": return record.diagnoses.map((diagnosis) =>
      `${diagnosis.icdCode?.trim() ? `${diagnosis.icdCode} ` : ""}${diagnosis.diagnosisName}${diagnosis.description?.trim() ? ` — ${diagnosis.description}` : ""}`,
    ).join("\n");
  }
}
