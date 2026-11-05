import { useId } from "react";
import { formatDecimal, formatLocalDate } from "@/lib/format";
import type { OutpatientStatisticsDTO, OutpatientStatisticsFigures } from "../types";
import { ReportDepartmentName } from "./ReportDepartmentName";

type RevenueKey = "examRevenue" | "labRevenue" | "prescriptionRevenue" | "unclassifiedRevenue";
export const revenueSeries: { key: RevenueKey; label: string; color: string }[] = [
  { key: "examRevenue", label: "Khám bệnh", color: "text-primary" },
  { key: "labRevenue", label: "Xét nghiệm", color: "text-success" },
  { key: "prescriptionRevenue", label: "Thuốc", color: "text-warning" },
  { key: "unclassifiedRevenue", label: "Chưa phân loại", color: "text-muted-foreground" },
];
const compact = new Intl.NumberFormat("vi-VN", { notation: "compact", maximumFractionDigits: 1 });

/** Hide the unclassified series unless some receipt in range actually lacks a service line. */
function visibleSeries(totals: OutpatientStatisticsFigures) {
  return revenueSeries.filter(item => item.key !== "unclassifiedRevenue" || totals.unclassifiedRevenue > 0);
}

function Legend({ items }: { items: { label: string; color: string }[] }) {
  return <div className="mt-2 flex flex-wrap gap-x-4 gap-y-2 text-xs" aria-label="Chú giải">
    {items.map(item => <span key={item.label} className={`inline-flex items-center gap-2 ${item.color}`}>
      <svg width="12" height="12" aria-hidden="true"><rect width="12" height="12" rx="2" className="fill-current" /></svg>
      <span className="text-foreground">{item.label}</span>
    </span>)}
  </div>;
}

/** Stacked daily revenue by service line, with visits as a second, separate chart. */
export function DailyRevenueChart({ report }: { report: OutpatientStatisticsDTO }) {
  const titleId = useId();
  const series = visibleSeries(report.totals);
  const totals = report.days.map(day => series.reduce((sum, item) => sum + day[item.key], 0));
  const max = Math.max(1, ...totals);
  const left = 64, right = 620, top = 16, bottom = 190;
  const step = (right - left) / Math.max(report.days.length, 1);
  const y = (value: number) => bottom - value / max * (bottom - top);
  const stride = Math.max(1, Math.ceil(report.days.length / 6));
  return <figure className="min-w-0 rounded-lg border border-border p-3 sm:p-4">
    <figcaption className="font-semibold">Doanh thu theo ngày và loại dịch vụ ({report.currency})</figcaption>
    <Legend items={series} />
    <div className="overflow-x-auto">
      <svg viewBox="0 0 640 230" role="img" aria-labelledby={titleId} className="mt-2 block w-full min-w-[480px]">
        <title id={titleId}>Doanh thu khám bệnh, xét nghiệm và thuốc theo ngày. Bảng theo khoa bên dưới có tổng từng loại.</title>
        {Array.from({ length: 5 }, (_, i) => max * i / 4).map((value, i) => <g key={i}>
          <line x1={left} x2={right} y1={y(value)} y2={y(value)} className="stroke-border" />
          <text x={left - 8} y={y(value) + 4} textAnchor="end" className="fill-current text-xs text-muted-foreground">{compact.format(value)}</text>
        </g>)}
        {report.days.map((day, index) => {
          let base = 0;
          return <g key={day.date}>
            {series.map(item => {
              const value = day[item.key];
              const rect = value > 0 ? <rect key={item.key} x={left + index * step + step * .15} y={y(base + value)}
                width={step * .7} height={y(base) - y(base + value)} className={`fill-current ${item.color}`}>
                <title>{formatLocalDate(day.date)} · {item.label}: {formatDecimal(value)} {report.currency}</title>
              </rect> : null;
              base += value;
              return rect;
            })}
            {(index % stride === 0 || index === report.days.length - 1) && <text x={left + index * step + step / 2} y="212"
              textAnchor="middle" className="fill-current text-xs text-muted-foreground">{formatLocalDate(day.date).slice(0, 5)}</text>}
          </g>;
        })}
      </svg>
    </div>
  </figure>;
}

export function DailyVisitsChart({ report }: { report: OutpatientStatisticsDTO }) {
  const titleId = useId();
  const max = Math.max(1, ...report.days.map(day => day.completedVisits));
  const left = 40, right = 620, top = 16, bottom = 150;
  const step = (right - left) / Math.max(report.days.length, 1);
  const y = (value: number) => bottom - value / max * (bottom - top);
  const stride = Math.max(1, Math.ceil(report.days.length / 6));
  const ticks = Array.from(new Set(Array.from({ length: 5 }, (_, i) => Math.round(max * i / 4))));
  return <figure className="min-w-0 rounded-lg border border-border p-3 sm:p-4">
    <figcaption className="font-semibold">Số lượt khám hoàn tất theo ngày</figcaption>
    <div className="overflow-x-auto">
      <svg viewBox="0 0 640 190" role="img" aria-labelledby={titleId} className="mt-2 block w-full min-w-[480px]">
        <title id={titleId}>Lượt khám hoàn tất theo ngày.</title>
        {ticks.map(value => <g key={value}>
          <line x1={left} x2={right} y1={y(value)} y2={y(value)} className="stroke-border" />
          <text x={left - 8} y={y(value) + 4} textAnchor="end" className="fill-current text-xs text-muted-foreground">{value}</text>
        </g>)}
        {report.days.map((day, index) => <g key={day.date}>
          {day.completedVisits > 0 && <rect x={left + index * step + step * .2} y={y(day.completedVisits)} width={step * .6}
            height={bottom - y(day.completedVisits)} rx={2} className="fill-current text-primary">
            <title>{formatLocalDate(day.date)}: {day.completedVisits} lượt khám</title>
          </rect>}
          {(index % stride === 0 || index === report.days.length - 1) && <text x={left + index * step + step / 2} y="172"
            textAnchor="middle" className="fill-current text-xs text-muted-foreground">{formatLocalDate(day.date).slice(0, 5)}</text>}
        </g>)}
      </svg>
    </div>
  </figure>;
}

/** One row per department: visits bar and stacked revenue bar on a shared scale. */
export function DepartmentCharts({ report }: { report: OutpatientStatisticsDTO }) {
  const series = visibleSeries(report.totals);
  const revenue = (row: OutpatientStatisticsFigures) => series.reduce((sum, item) => sum + row[item.key], 0);
  const maxRevenue = Math.max(1, ...report.departments.map(revenue));
  const maxVisits = Math.max(1, ...report.departments.map(row => row.completedVisits));
  if (!report.departments.length) return <p role="status" className="text-sm text-muted-foreground">Chưa có lượt khám hoặc khoản thu nào trong phạm vi này.</p>;
  return <figure className="min-w-0 rounded-lg border border-border p-3 sm:p-4">
    <figcaption className="font-semibold">Theo khoa: lượt khám và doanh thu ({report.currency})</figcaption>
    <Legend items={[{ label: "Lượt khám", color: "text-foreground" }, ...series]} />
    <ul className="mt-3 space-y-4" aria-label="Thống kê theo khoa">
      {report.departments.map(row => {
        let offset = 0;
        return <li key={row.departmentId} className="min-w-0">
          <div className="flex flex-wrap items-baseline justify-between gap-2 text-sm">
            <span className="font-medium"><ReportDepartmentName id={row.departmentId} showId={false} /></span>
            <span className="tabular-nums text-muted-foreground">{row.completedVisits} lượt · {formatDecimal(revenue(row))} {report.currency}</span>
          </div>
          <svg viewBox="0 0 400 30" preserveAspectRatio="none" className="mt-1 block h-8 w-full" role="img"
            aria-label={`${row.completedVisits} lượt khám; ${series.map(item => `${item.label} ${formatDecimal(row[item.key])}`).join(", ")}`}>
            <rect x="0" y="1" width={400 * row.completedVisits / maxVisits} height="10" rx="2" className="fill-current text-foreground" />
            {series.map(item => {
              const width = 400 * row[item.key] / maxRevenue;
              const rect = width > 0 ? <rect key={item.key} x={offset} y="15" width={width} height="14" className={`fill-current ${item.color}`} /> : null;
              offset += width;
              return rect;
            })}
          </svg>
          <dl className="mt-1 grid grid-cols-2 gap-x-4 text-xs text-muted-foreground sm:grid-cols-4">
            {series.map(item => <div key={item.key} className="flex gap-1"><dt>{item.label}:</dt><dd className="tabular-nums">{formatDecimal(row[item.key])}</dd></div>)}
          </dl>
        </li>;
      })}
    </ul>
  </figure>;
}
