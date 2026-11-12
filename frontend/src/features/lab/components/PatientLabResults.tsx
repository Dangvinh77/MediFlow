"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";

import { PatientResultsList } from "@/components/ui/PatientResultsList";
import { formatLocalDate } from "@/lib/format";
import { labApi } from "../api";

export function PatientLabResults() {
  return <PatientResultsList load={labApi.mine} empty="Chưa có kết quả xét nghiệm đã hoàn tất." render={test =>
    <article key={test.testId} className="space-y-4 rounded-xl border border-border bg-surface p-5">
      <h2 className="text-lg font-semibold">{test.labType}</h2>
      <p><L10n>{"Ngày thực hiện: "}</L10n><L10nValue>{formatLocalDate(test.performedDate)}</L10nValue></p>
      <div className="overflow-x-auto"><table className="w-full text-left text-sm">
        <thead><tr><th className="p-2"><L10n>{"Chỉ số"}</L10n></th><th className="p-2"><L10n>{"Kết quả"}</L10n></th><th className="p-2"><L10n>{"Đơn vị"}</L10n></th><th className="p-2"><L10n>{"Khoảng tham chiếu"}</L10n></th></tr></thead>
        <tbody><L10nValue>{test.results.map(result => <tr key={result.resultId}><td className="p-2">{result.indicator}</td><td className="p-2">{result.value}</td><td className="p-2"><L10nValue>{result.unit ?? "—"}</L10nValue></td><td className="p-2"><L10nValue>{result.referenceRange ?? "—"}</L10nValue></td></tr>)}</L10nValue></tbody>
      </table></div>
      <L10nValue>{test.conclusion ? <p className="whitespace-pre-wrap"><L10n>{"Kết luận: "}</L10n>{test.conclusion}</p> : null}</L10nValue>
    </article>} />;
}
