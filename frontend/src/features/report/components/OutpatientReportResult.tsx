import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatDecimal, formatLocalDate } from "@/lib/format";
import type { OutpatientReportDTO } from "../types";

function reportInstant(value: string, zoneId: string) {
  try {
    return new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short", timeZone: zoneId }).format(new Date(value));
  } catch {
    return value;
  }
}

export function OutpatientReportResult({ report }: { report: OutpatientReportDTO }) {
  if (report.covered !== true) {
    return <p role="status" className="mt-6 rounded-xl border border-border bg-surface p-5 text-muted-foreground">Chưa có dữ liệu đã đối soát cho phạm vi này.</p>;
  }
  return (
    <section aria-label="Kết quả báo cáo ngoại trú" className="mt-6 min-w-0 rounded-xl border border-border bg-surface p-4 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-border pb-4">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold">Ngoại trú từ {formatLocalDate(report.fromDate)} đến {formatLocalDate(report.toDate)}</h2>
          <p className="mt-1 break-all text-sm text-muted-foreground">Phạm vi: {report.departmentId ?? "Toàn bệnh viện"}</p>
          <p className="mt-1 break-words text-sm text-muted-foreground">Tiền tệ: {report.currency} · Múi giờ: {report.zoneId}</p>
        </div>
        <StatusBadge tone="success">Đã đối soát</StatusBadge>
      </div>
      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <div><dt className="text-muted-foreground">Dữ liệu nguồn cập nhật</dt><dd className="mt-1">{reportInstant(report.sourceObservedAt, report.zoneId)}</dd></div>
        <div><dt className="text-muted-foreground">Đối soát lúc</dt><dd className="mt-1">{reportInstant(report.reconciledAt, report.zoneId)}</dd></div>
      </dl>
      <details className="mt-3 text-sm text-muted-foreground"><summary className="min-h-11 cursor-pointer py-3 focus-visible:outline-2 focus-visible:outline-primary">Mã đối soát</summary><p className="break-all">{report.coverageId}</p></details>
      {report.periods.length === 0 ? <p role="status" className="mt-4 text-sm text-muted-foreground">Báo cáo chưa có kỳ dữ liệu.</p> : (
        <ul aria-label="Các kỳ báo cáo" className="mt-4 space-y-4">
          {report.periods.map((period) => (
            <li key={`${period.fromDate}/${period.toDate}`} className="rounded-lg border border-border p-4">
              <h3 className="font-semibold">{period.fromDate === period.toDate ? formatLocalDate(period.fromDate) : `${formatLocalDate(period.fromDate)} – ${formatLocalDate(period.toDate)}`}</h3>
              <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-5 sm:grid-cols-4 xl:grid-cols-7">
                {([
                  ["Lượt khám hoàn tất", period.completedVisits], ["Xét nghiệm", period.labTests],
                  ["Đơn đã xuất", period.dispensedPrescriptions], ["Số lượng thuốc đã xuất", period.dispensedUnits],
                  ["Tiền đã thu", period.cashReceived], ["Tiền đã hoàn", period.refunds], ["Tiền thu ròng", period.netCash],
                ] as const).map(([label, value]) => <div key={label} className="min-w-0"><dt className="text-sm text-muted-foreground">{label}</dt><dd className="mt-1 break-words text-lg font-semibold tabular-nums">{formatDecimal(value)}</dd></div>)}
              </dl>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
