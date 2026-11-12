"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useCallback, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { medicalRecordApi } from "../api";
import type { CloseTreatmentCourseRequest, TreatmentCourseDTO, TreatmentCourseDecision } from "../types";
import { formatClinicalInstant, IdempotentAction, PanelReadState, usePanelResource } from "./record-collaboration-ui";

const arrayIsEmpty = (value: TreatmentCourseDTO[]) => value.length === 0;
const decisions: Array<[TreatmentCourseDecision, string]> = [
  ["RESOLVED", "Đã giải quyết"], ["TRANSFERRED", "Đã chuyển nơi theo dõi"],
  ["PATIENT_STOPPED", "Người bệnh dừng điều trị"], ["OTHER", "Lý do khác"],
];

export interface TreatmentCoursesPanelProps {
  patientId: string;
  recordId: string;
  canOpen: boolean;
  canAddVisit: boolean;
  canCloseCourse: (course: TreatmentCourseDTO) => boolean;
  onCommandSuccess?: () => void;
}

export function TreatmentCoursesPanel({ patientId, recordId, canOpen, canAddVisit, canCloseCourse, onCommandSuccess }: TreatmentCoursesPanelProps) {
  const { localize } = useLocale();
  const read = useCallback(() => medicalRecordApi.treatmentCourses(patientId), [patientId]);
  const { state, retry } = usePanelResource(`treatment-courses:${patientId}`, true, read, arrayIsEmpty);
  const [showOpen, setShowOpen] = useState(false);
  const [problem, setProblem] = useState("");
  const [openingReason, setOpeningReason] = useState("");
  const [closingCourse, setClosingCourse] = useState<TreatmentCourseDTO | null>(null);
  const [decision, setDecision] = useState<TreatmentCourseDecision | "">("");
  const [closureNote, setClosureNote] = useState("");
  const [commandLocked, setCommandLocked] = useState(false);

  const refresh = () => { setShowOpen(false); setClosingCourse(null); setProblem(""); setOpeningReason(""); setClosureNote(""); setDecision(""); retry(); onCommandSuccess?.(); };
  if (state.status === "hidden") return null;
  if (state.status !== "empty" && state.status !== "loaded") return <PanelReadState label={localize("Quá trình điều trị")} state={state} loadingMessage={localize("Đang tải quá trình điều trị…")} deniedMessage="Bạn không có quyền xem quá trình điều trị của bệnh nhân này." retryMessage="Không thể tải quá trình điều trị." emptyMessage={localize("Chưa có quá trình điều trị.")} onRetry={retry} />;

  const courses = state.status === "loaded" ? state.data : [];
  const openBody = { patientId, problem: problem.trim(), openingReason: openingReason.trim(), recordId };

  return <section role="region" aria-label={localize("Quá trình điều trị")} className="space-y-4 rounded-xl border border-border bg-surface p-5">
    <header className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-lg font-semibold"><L10n>{"Quá trình điều trị"}</L10n></h2><p className="mt-1 text-sm text-muted-foreground"><L10n>{"Các lượt được bác sĩ gắn vào quá trình để theo dõi liên tục."}</L10n></p></div>
      <L10nValue>{canOpen && !showOpen ? <Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setShowOpen(true)}><L10n>{"Mở quá trình"}</L10n></Button> : null}</L10nValue>
    </header>
    <L10nValue>{state.status === "empty" ? <p role="status" className="text-sm text-muted-foreground"><L10n>{"Chưa có quá trình điều trị."}</L10n></p> : null}</L10nValue>
    <L10nValue>{courses.length ? <ul className="divide-y divide-border"><L10nValue>{courses.map((course) => {
      const containsVisit = course.recordIds.includes(recordId);
      return <li key={course.courseId} className="space-y-3 py-4 first:pt-0 last:pb-0">
        <div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="font-semibold">{course.problem}</h3><p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">{course.openingReason}</p></div><span className="rounded-full bg-surface-muted px-3 py-1 text-xs font-medium"><L10nValue>{course.status === "OPEN" ? "Đang theo dõi" : "Đã đóng"}</L10nValue></span></div>
        <dl className="grid gap-3 text-sm sm:grid-cols-2"><div><dt className="text-xs font-medium text-muted-foreground"><L10n>{"Bắt đầu"}</L10n></dt><dd className="mt-1"><L10nValue>{formatClinicalInstant(course.openedAt)}</L10nValue></dd></div><div><dt className="text-xs font-medium text-muted-foreground"><L10n>{"Lượt trong quá trình"}</L10n></dt><dd className="mt-1">{course.recordIds.length}</dd></div></dl>
        <L10nValue>{course.reviewDue ? <p role="status" className="rounded-lg border border-warning px-3 py-2 text-sm"><L10n>{"Cần rà soát"}</L10n></p> : null}</L10nValue>
        <p className="break-all text-xs text-muted-foreground"><L10n>{"Bác sĩ phụ trách: "}</L10n><span className="font-mono">{course.responsibleDoctorId}</span></p>
        <div className="flex flex-wrap gap-2">
          <L10nValue>{canAddVisit && course.status === "OPEN" && !containsVisit ? <IdempotentAction scope={`treatment-course:add-visit:${course.courseId}:${recordId}`} label={localize("Gắn lượt hiện tại")} body={{ recordId }} disabled={commandLocked} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.addCourseVisit(course.courseId, { recordId }, options)} onSuccess={refresh} /> : null}</L10nValue>
          <L10nValue>{course.status === "OPEN" && canCloseCourse(course) && closingCourse?.courseId !== course.courseId ? <Button type="button" variant="secondary" disabled={commandLocked} onClick={() => { setClosingCourse(course); setDecision(""); setClosureNote(""); }}><L10n>{"Đóng quá trình"}</L10n></Button> : null}</L10nValue>
        </div>
        <L10nValue>{closingCourse?.courseId === course.courseId && canCloseCourse(course) ? <div className="space-y-3 rounded-lg border border-border p-4">
          <Field label={localize("Quyết định kết thúc")} htmlFor={`course-decision-${course.courseId}`} required><select id={`course-decision-${course.courseId}`} value={decision} onChange={(event) => setDecision(event.target.value as TreatmentCourseDecision | "")} disabled={commandLocked} className={controlClassName}><option value=""><L10n>{"Chọn quyết định"}</L10n></option><L10nValue>{decisions.map(([value, label]) => <option value={value} key={value}><L10nValue>{label}</L10nValue></option>)}</L10nValue></select></Field>
          <Field label={localize("Ghi chú kết thúc")} htmlFor={`course-note-${course.courseId}`} required><textarea id={`course-note-${course.courseId}`} value={closureNote} maxLength={1000} onChange={(event) => setClosureNote(event.target.value)} disabled={commandLocked} className={controlClassName} /></Field>
          <div className="flex flex-wrap items-center gap-3"><IdempotentAction< TreatmentCourseDTO > scope={`treatment-course:close:${course.courseId}`} label={localize("Xác nhận đóng quá trình")} body={{ decision, note: closureNote.trim() }} disabled={commandLocked || !decision || !closureNote.trim()} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.closeTreatmentCourse(course.courseId, { decision: decision as TreatmentCourseDecision, note: closureNote.trim() }, options)} onSuccess={refresh} /><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setClosingCourse(null)}><L10n>{"Hủy"}</L10n></Button></div>
        </div> : null}</L10nValue>
      </li>;
    })}</L10nValue></ul> : null}</L10nValue>
    <L10nValue>{canOpen && showOpen ? <form className="space-y-3 rounded-lg border border-border p-4" onSubmit={(event) => event.preventDefault()}>
      <h3 className="font-semibold"><L10n>{"Mở quá trình từ lượt này"}</L10n></h3>
      <Field label={localize("Vấn đề điều trị")} htmlFor="course-problem" required><textarea id="course-problem" value={problem} maxLength={500} onChange={(event) => setProblem(event.target.value)} disabled={commandLocked} className={controlClassName} /></Field>
      <Field label={localize("Lý do mở quá trình")} htmlFor="course-opening-reason" required><textarea id="course-opening-reason" value={openingReason} maxLength={1000} onChange={(event) => setOpeningReason(event.target.value)} disabled={commandLocked} className={controlClassName} /></Field>
      <div className="flex flex-wrap items-center gap-3"><IdempotentAction scope={`treatment-course:open:${patientId}:${recordId}`} label={localize("Tạo quá trình")} body={openBody} disabled={commandLocked || !problem.trim() || !openingReason.trim()} onLockedChange={setCommandLocked} run={(options) => medicalRecordApi.openTreatmentCourse(openBody, options)} onSuccess={refresh} /><Button type="button" variant="secondary" disabled={commandLocked} onClick={() => setShowOpen(false)}><L10n>{"Hủy"}</L10n></Button></div>
    </form> : null}</L10nValue>
  </section>;
}
