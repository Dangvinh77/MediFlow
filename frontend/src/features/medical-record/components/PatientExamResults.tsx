"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";

import { PatientResultsList } from "@/components/ui/PatientResultsList";
import { formatInstant, formatLocalDate } from "@/lib/format";
import { medicalRecordApi } from "../api";
import { FollowUpRecommendationPanel } from "./FollowUpRecommendationPanel";

export function PatientExamResults({ schedulingEnabled }: { schedulingEnabled: boolean }) {
  return <PatientResultsList load={medicalRecordApi.mine} empty="Chưa có kết quả khám đã hoàn tất." render={record =>
    <article key={record.recordId} className="space-y-4 rounded-xl border border-border bg-surface p-5">
      <h2 className="text-lg font-semibold"><L10n>{"Ngày khám: "}</L10n><L10nValue>{formatLocalDate(record.examinationDate)}</L10nValue></h2>
      <L10nValue>{record.effectiveCorrections?.length ? <section aria-label="Chỉnh sửa có hiệu lực" className="space-y-3 rounded-lg border border-success p-4">
        <div><h3 className="font-semibold"><L10n>{"Chỉnh sửa có hiệu lực"}</L10n></h3><p className="text-sm text-muted-foreground"><L10n>{"Nội dung bên dưới là giá trị đang có hiệu lực; bản gốc được giữ trong hồ sơ."}</L10n></p></div>
        {record.effectiveCorrections.map(correction => <div key={correction.section} className="grid gap-2 border-t border-border pt-3 sm:grid-cols-2">
          <div><h4 className="text-xs font-medium text-muted-foreground"><L10n>{"Nội dung gốc"}</L10n></h4><p className="whitespace-pre-wrap text-sm">{correction.originalText}</p></div>
          <div><h4 className="text-xs font-medium text-success"><L10n>{"Nội dung đang có hiệu lực"}</L10n></h4><p className="whitespace-pre-wrap text-sm font-medium">{correction.correctedText}</p></div>
          <p className="text-sm sm:col-span-2"><span className="font-medium"><L10n>{"Lý do đính chính:"}</L10n></span> {correction.reason} <span className="text-muted-foreground">· <L10n>{"Thời điểm áp dụng: "}</L10n><L10nValue>{formatInstant(correction.appliedAt)}</L10nValue></span></p>
        </div>)}
      </section> : null}</L10nValue>
      <L10nValue>{record.symptoms ? <p className="whitespace-pre-wrap"><L10nValue>{record.effectiveCorrections?.some(correction => correction.section === "SYMPTOMS") ? "Triệu chứng ban đầu: " : "Triệu chứng: "}</L10nValue>{record.symptoms}</p> : null}</L10nValue>
      <div><h3 className="font-semibold"><L10nValue>{record.effectiveCorrections?.some(correction => correction.section === "DIAGNOSIS") ? "Chẩn đoán ban đầu" : "Chẩn đoán"}</L10nValue></h3><ul className="list-inside list-disc">
        <L10nValue>{record.diagnoses.map(diagnosis => <li key={diagnosis.diagnosisId}>{diagnosis.diagnosisName}<L10nValue>{diagnosis.icdCode ? ` (${diagnosis.icdCode})` : ""}</L10nValue><L10nValue>{diagnosis.description ? `: ${diagnosis.description}` : ""}</L10nValue></li>)}</L10nValue>
      </ul></div>
      <L10nValue>{record.dispositionNote ? <p className="whitespace-pre-wrap"><L10nValue>{record.effectiveCorrections?.some(correction => correction.section === "DISPOSITION_NOTE") ? "Kết luận ban đầu: " : "Kết luận và hướng dẫn: "}</L10nValue>{record.dispositionNote}</p> : null}</L10nValue>
      <FollowUpRecommendationPanel recordId={record.recordId} enabled={schedulingEnabled} canCreate={false} patientView />
    </article>} />;
}
