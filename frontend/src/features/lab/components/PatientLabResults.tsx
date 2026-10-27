"use client";
import { PatientResultsList } from "@/components/ui/PatientResultsList";
import { formatLocalDate } from "@/lib/format";
import { labApi } from "../api";

export function PatientLabResults() {
  return <PatientResultsList load={labApi.mine} empty="Chưa có kết quả xét nghiệm đã hoàn tất." render={test =>
    <article key={test.testId} className="space-y-4 rounded-xl border border-border bg-surface p-5">
      <h2 className="text-lg font-semibold">{test.labType}</h2>
      <p>Ngày thực hiện: {formatLocalDate(test.performedDate)}</p>
      <div className="overflow-x-auto"><table className="w-full text-left text-sm">
        <thead><tr><th className="p-2">Chỉ số</th><th className="p-2">Kết quả</th><th className="p-2">Đơn vị</th><th className="p-2">Khoảng tham chiếu</th></tr></thead>
        <tbody>{test.results.map(result => <tr key={result.resultId}><td className="p-2">{result.indicator}</td><td className="p-2">{result.value}</td><td className="p-2">{result.unit ?? "—"}</td><td className="p-2">{result.referenceRange ?? "—"}</td></tr>)}</tbody>
      </table></div>
      {test.conclusion ? <p className="whitespace-pre-wrap">Kết luận: {test.conclusion}</p> : null}
    </article>} />;
}
