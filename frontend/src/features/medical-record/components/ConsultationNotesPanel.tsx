"use client";

import { useCallback, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { medicalRecordApi } from "../api";
import type { ConsultationNoteDTO } from "../types";
import { formatClinicalInstant, IdempotentAction, PanelReadState, usePanelResource } from "./record-collaboration-ui";

const arrayIsEmpty = (value: ConsultationNoteDTO[]) => value.length === 0;

export function ConsultationNotesPanel({ recordId, canAddConsultationNote, onCommandSuccess }: {
  recordId: string;
  canAddConsultationNote: boolean;
  onCommandSuccess?: () => void;
}) {
  const read = useCallback(() => medicalRecordApi.consultationNotes(recordId), [recordId]);
  const { state, retry } = usePanelResource(`consultation-notes:${recordId}`, true, read, arrayIsEmpty);
  const [opinion, setOpinion] = useState("");
  const [commandLocked, setCommandLocked] = useState(false);
  const refresh = () => { setOpinion(""); retry(); onCommandSuccess?.(); };
  if (state.status === "hidden") return null;
  if (state.status !== "empty" && state.status !== "loaded") return <PanelReadState label="Hội chẩn" state={state} loadingMessage="Đang tải ghi chú hội chẩn…" deniedMessage="Bạn không có quyền xem ghi chú hội chẩn của hồ sơ này." retryMessage="Không thể tải ghi chú hội chẩn." emptyMessage="Chưa có ghi chú hội chẩn." onRetry={retry} />;

  const notes = state.status === "loaded" ? state.data : [];
  const body = { opinion: opinion.trim() };
  return <section role="region" aria-label="Hội chẩn" className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <div><h2 className="text-lg font-semibold">Hội chẩn</h2><p className="mt-1 text-sm text-muted-foreground">Mỗi ý kiến được lưu riêng theo tác giả và thời điểm.</p></div>
    {state.status === "empty" ? <p role="status" className="text-sm text-muted-foreground">Chưa có ghi chú hội chẩn.</p> : null}
    {notes.length ? <ul className="divide-y divide-border">{notes.map((note) => <li key={note.noteId} className="space-y-2 py-3 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-center justify-between gap-2"><span className="text-sm font-medium">Bác sĩ <span className="break-all font-mono">{note.authorStaffId}</span></span><time className="text-xs text-muted-foreground">{formatClinicalInstant(note.createdAt)}</time></div>
      <p className="whitespace-pre-wrap text-sm">{note.opinion}</p>
    </li>)}</ul> : null}
    {canAddConsultationNote ? <form className="space-y-3 border-t border-border pt-4" onSubmit={(event) => event.preventDefault()}>
      <Field label="Ý kiến hội chẩn" htmlFor={`consultation-opinion-${recordId}`} required hint="Ý kiến được ghi riêng, không thay thế kết luận của bác sĩ phụ trách."><textarea id={`consultation-opinion-${recordId}`} value={opinion} maxLength={4000} onChange={(event) => setOpinion(event.target.value)} disabled={commandLocked} className={controlClassName} /></Field>
      <IdempotentAction scope={`consultation-note:${recordId}`} label="Thêm ghi chú" body={body} disabled={commandLocked || !body.opinion} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.addConsultationNote(recordId, body, options)} onSuccess={refresh} />
    </form> : null}
  </section>;
}
