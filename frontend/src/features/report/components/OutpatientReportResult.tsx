import { ReportDepartmentName } from "./ReportDepartmentName";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatDecimal, formatLocalDate } from "@/lib/format";
import type { OutpatientReportDTO, OutpatientReportPeriod } from "../types";
import { CalendarHeatmap, CartesianChart, ChartCard, ChartLegend, RankedBars, Sparkline, compact } from "./ReportCharts";

type Metric = Exclude<keyof OutpatientReportPeriod, "fromDate" | "toDate">;
const metrics = ["completedVisits", "labTests", "dispensedPrescriptions", "dispensedUnits", "cashReceived", "refunds", "netCash"] as const satisfies readonly Metric[];
const columns: Record<Metric, string> = {
  completedVisits: "Lượt khám hoàn tất", labTests: "Xét nghiệm", dispensedPrescriptions: "Đơn đã xuất",
  dispensedUnits: "Số lượng thuốc đã xuất", cashReceived: "Tiền đã thu", refunds: "Tiền đã hoàn", netCash: "Tiền thu ròng",
};
const activity = [
  { key: "completedVisits", label: "Lượt khám", color: "text-chart-1" },
  { key: "labTests", label: "Xét nghiệm", color: "text-chart-2" },
  { key: "dispensedPrescriptions", label: "Đơn thuốc", color: "text-chart-3" },
] as const;
/** Up to a month per day still reads as bars; longer ranges switch to lines and a calendar. */
const BAR_LIMIT = 31;

function reportInstant(value: string, zoneId: string) {
  try {
    return new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short", timeZone: zoneId }).format(new Date(value));
  } catch {
    return value;
  }
}

const isDay = (period: OutpatientReportPeriod) => period.fromDate === period.toDate;
const isMonth = (period: OutpatientReportPeriod) => period.fromDate.endsWith("-01") && period.toDate.slice(0, 7) === period.fromDate.slice(0, 7);

function periodName(period: OutpatientReportPeriod) {
  if (isDay(period)) return formatLocalDate(period.fromDate);
  if (isMonth(period)) return `Tháng ${Number(period.fromDate.slice(5, 7))}/${period.fromDate.slice(0, 4)}`;
  return `${formatLocalDate(period.fromDate)} – ${formatLocalDate(period.toDate)}`;
}

/** Short, unique axis labels: a range crossing a year keeps the year so no two bands share a name. */
function axisLabels(periods: OutpatientReportPeriod[]) {
  const oneYear = periods.every(period => period.fromDate.slice(0, 4) === periods[0].fromDate.slice(0, 4));
  return periods.map(period => {
    const [year, month, day] = period.fromDate.split("-");
    if (isDay(period)) return oneYear ? `${day}/${month}` : `${day}/${month}/${year.slice(2)}`;
    if (isMonth(period)) return oneYear ? `T${Number(month)}` : `${month}/${year.slice(2)}`;
    return formatLocalDate(period.fromDate);
  });
}

function sum(periods: OutpatientReportPeriod[], key: Metric) {
  return periods.reduce((total, period) => total + period[key], 0);
}

function Tile({ label, value, note, trend, tone = "" }: { label: string; value: string; note: string; trend: number[]; tone?: string }) {
  return <div className="min-w-0 rounded-xl border border-border bg-background p-4">
    <dt className="text-sm text-muted-foreground">{label}</dt>
    <dd className={`mt-1 break-words text-2xl font-semibold tabular-nums ${tone}`}>{value}</dd>
    <dd className="mt-0.5 text-xs text-muted-foreground">{note}</dd>
    {trend.length > 1 && <dd><Sparkline values={trend} /></dd>}
  </div>;
}

export function OutpatientReportResult({ report }: { report: OutpatientReportDTO }) {
  if (report.covered !== true) {
    return <p role="status" className="mt-6 rounded-xl border border-border bg-surface p-5 text-muted-foreground">Chưa có dữ liệu đã đối soát cho phạm vi này.</p>;
  }
  const { periods, currency } = report;
  const money = (value: number) => `${formatDecimal(value)} ${currency}`;
  const totals = Object.fromEntries(metrics.map(key => [key, sum(periods, key)])) as Record<Metric, number>;
  const labels = axisLabels(periods);
  const daily = periods.length > 0 && periods.every(isDay);
  const unit = daily ? "ngày" : "kỳ";
  const asBars = periods.length <= BAR_LIMIT;
  const activeDays = periods.filter(period => period.completedVisits > 0).length;
  const busiest = [...periods].filter(period => period.netCash !== 0).sort((a, b) => b.netCash - a.netCash).slice(0, 5);
  const trend = (key: Metric) => periods.map(period => period[key]);

  return (
    <section aria-label="Kết quả báo cáo ngoại trú" className="mt-6 min-w-0 space-y-4 rounded-xl border border-border bg-surface p-4 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-border pb-4">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold">Ngoại trú từ {formatLocalDate(report.fromDate)} đến {formatLocalDate(report.toDate)}</h2>
          <p className="mt-1 break-all text-sm text-muted-foreground">Phạm vi: <ReportDepartmentName id={report.departmentId} /></p>
          <p className="mt-1 break-words text-sm text-muted-foreground">Tiền tệ: {currency} · Múi giờ: {report.zoneId}</p>
        </div>
        <StatusBadge tone="success">Đã đối soát</StatusBadge>
      </div>
      {report.syntheticData === true && <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm">Dữ liệu mô phỏng phục vụ minh họa; không phải hồ sơ hoặc giao dịch thực tế.</p>}

      {periods.length === 0 ? <p role="status" className="text-sm text-muted-foreground">Báo cáo chưa có kỳ dữ liệu.</p> : <>
        <dl aria-label="Tổng kỳ đã chọn" className="grid grid-cols-2 gap-3 xl:grid-cols-4">
          <Tile label="Tổng lượt khám" value={formatDecimal(totals.completedVisits)}
            note={daily ? `${activeDays}/${periods.length} ngày có khám · TB ${formatDecimal(Math.round((totals.completedVisits / periods.length) * 10) / 10)}/ngày` : `${periods.length} ${unit}`}
            trend={trend("completedVisits")} />
          <Tile label="Tổng xét nghiệm" value={formatDecimal(totals.labTests)}
            note={totals.completedVisits > 0 ? `${formatDecimal(Math.round((totals.labTests / totals.completedVisits) * 100))} XN / 100 lượt khám` : "xét nghiệm hoàn tất"}
            trend={trend("labTests")} />
          <Tile label="Đơn thuốc đã cấp" value={formatDecimal(totals.dispensedPrescriptions)}
            note={`Số lượng đã cấp: ${formatDecimal(totals.dispensedUnits)}`} trend={trend("dispensedPrescriptions")} />
          <Tile label="Thu ròng cả kỳ" value={money(totals.netCash)} tone={totals.netCash < 0 ? "text-danger" : ""}
            note={`Thu ${compact(totals.cashReceived)} · hoàn ${compact(totals.refunds)}`} trend={trend("netCash")} />
        </dl>

        <div className="grid min-w-0 gap-4 xl:grid-cols-2">
          <ChartCard title={`Dòng tiền theo ${unit}`} subtitle={`Tiền đã thu và đã hoàn (${currency})`}>
            <ChartLegend items={[{ label: "Tiền đã thu", color: "text-chart-1", line: !asBars }, { label: "Tiền đã hoàn", color: "text-danger", line: !asBars }]} />
            <CartesianChart kind={asBars ? "grouped" : "line"} labels={labels} format={money}
              series={[{ label: "Tiền đã thu", color: "text-chart-1", values: trend("cashReceived") }, { label: "Tiền đã hoàn", color: "text-danger", values: trend("refunds") }]}
              description={`Dòng tiền ngoại trú (${currency}) theo ${unit}. Bảng chi tiết bên dưới có đầy đủ giá trị.`} />
          </ChartCard>
          <ChartCard title={`Hoạt động theo ${unit}`} subtitle="Lượt khám, xét nghiệm và đơn thuốc hoàn tất">
            <ChartLegend items={activity.map(item => ({ ...item, line: !asBars }))} />
            <CartesianChart kind={asBars ? "grouped" : "line"} labels={labels} format={value => formatDecimal(value)}
              series={activity.map(item => ({ label: item.label, color: item.color, values: trend(item.key) }))}
              description={`Hoạt động ngoại trú (lượt / xét nghiệm / đơn) theo ${unit}. Bảng chi tiết bên dưới có đầy đủ giá trị.`} />
          </ChartCard>
        </div>

        <div className="grid min-w-0 gap-4 xl:grid-cols-5">
          {daily && periods.length >= 14 && <ChartCard title="Lịch lượt khám" subtitle="Mỗi ô là một ngày; màu càng đậm càng đông" className="xl:col-span-3">
            <CalendarHeatmap days={periods.map(period => ({ date: period.fromDate, value: period.completedVisits }))} unit="lượt" description="Lượt khám hoàn tất từng ngày trong kỳ đã chọn." />
          </ChartCard>}
          {busiest.length > 1 && <ChartCard title={`${daily ? "Ngày" : "Kỳ"} thu ròng cao nhất`} subtitle={`Top ${busiest.length} trong kỳ đã chọn`} className={daily && periods.length >= 14 ? "xl:col-span-2" : "xl:col-span-5"}>
            <RankedBars label={`Xếp hạng ${unit} theo thu ròng`} format={money}
              rows={busiest.map(period => ({ id: period.fromDate, label: periodName(period), value: period.netCash, note: `${formatDecimal(period.completedVisits)} lượt` }))} />
          </ChartCard>}
        </div>

        <details open={periods.length <= BAR_LIMIT} className="rounded-xl border border-border bg-background px-3 sm:px-4">
          <summary className="min-h-11 cursor-pointer py-3 font-medium focus-visible:outline-2 focus-visible:outline-primary">Bảng chi tiết theo {unit} ({periods.length})</summary>
          <div className="overflow-x-auto pb-3">
            <table aria-label="Các kỳ báo cáo" className="w-full min-w-[46rem] text-right text-sm tabular-nums">
              <thead className="border-b border-border text-xs text-muted-foreground">
                <tr>
                  <th scope="col" className="py-2 pr-3 text-left font-medium">{daily ? "Ngày" : "Kỳ"}</th>
                  {metrics.map(key => <th key={key} scope="col" className="px-2 py-2 font-medium">{columns[key]}</th>)}
                </tr>
              </thead>
              <tbody>
                {periods.map(period => {
                  const empty = metrics.every(key => period[key] === 0);
                  return <tr key={`${period.fromDate}/${period.toDate}`} className={`border-b border-border last:border-0 ${empty ? "text-muted-foreground" : ""}`}>
                    <th scope="row" className="py-2 pr-3 text-left font-medium">{periodName(period)}</th>
                    {metrics.map(key => <td key={key} className={`px-2 py-2 ${key === "netCash" ? `font-medium ${period.netCash < 0 ? "text-danger" : ""}` : ""}`}>{formatDecimal(period[key])}</td>)}
                  </tr>;
                })}
              </tbody>
              <tfoot className="border-t border-control-border font-semibold">
                <tr>
                  <th scope="row" className="py-2 pr-3 text-left">Cả kỳ</th>
                  {metrics.map(key => <td key={key} className="px-2 py-2">{formatDecimal(totals[key])}</td>)}
                </tr>
              </tfoot>
            </table>
          </div>
        </details>
        <p className="text-xs text-muted-foreground">Tiền thu ròng là tiền đã thu trừ tiền đã hoàn do máy chủ trả về, không phải doanh thu ghi nhận. Ngày hiển thị 0 nằm trong phạm vi đã đối soát.</p>
      </>}

      <details className="rounded-lg border border-border px-3 text-sm">
        <summary className="min-h-11 cursor-pointer py-3 font-medium focus-visible:outline-2 focus-visible:outline-primary">Nguồn và đối soát</summary>
        <div className="pb-3 text-muted-foreground">
          <dl className="grid gap-3 sm:grid-cols-2">
            <div><dt>Dữ liệu nguồn cập nhật</dt><dd className="mt-1">{reportInstant(report.sourceObservedAt, report.zoneId)}</dd></div>
            <div><dt>Đối soát lúc</dt><dd className="mt-1">{reportInstant(report.reconciledAt, report.zoneId)}</dd></div>
          </dl>
          <p className="mt-3">Mã đối soát</p>
          <p className="break-all">{report.coverageId}</p>
        </div>
      </details>
    </section>
  );
}
