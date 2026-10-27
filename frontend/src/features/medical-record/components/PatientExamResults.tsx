"use client";
import { PatientResultsList } from "@/components/ui/PatientResultsList";
import { formatLocalDate } from "@/lib/format";
import { medicalRecordApi } from "../api";
import { FollowUpRecommendationPanel } from "./FollowUpRecommendationPanel";

export function PatientExamResults({ schedulingEnabled }: { schedulingEnabled: boolean }) {
  return <PatientResultsList load={medicalRecordApi.mine} empty="Chưa có kết quả khám đã hoàn tất." render={record =>
    <article key={record.recordId} className="space-y-4 rounded-xl border border-border bg-surface p-5">
      <h2 className="text-lg font-semibold">Ngày khám: {formatLocalDate(record.examinationDate)}</h2>
      {record.symptoms ? <p className="whitespace-pre-wrap">Triệu chứng: {record.symptoms}</p> : null}
      <div><h3 className="font-semibold">Chẩn đoán</h3><ul className="list-inside list-disc">
        {record.diagnoses.map(diagnosis => <li key={diagnosis.diagnosisId}>{diagnosis.diagnosisName}{diagnosis.icdCode ? ` (${diagnosis.icdCode})` : ""}{diagnosis.description ? `: ${diagnosis.description}` : ""}</li>)}
      </ul></div>
      {record.dispositionNote ? <p className="whitespace-pre-wrap">Kết luận và hướng dẫn: {record.dispositionNote}</p> : null}
      <FollowUpRecommendationPanel recordId={record.recordId} enabled={schedulingEnabled} canCreate={false} />
    </article>} />;
}
