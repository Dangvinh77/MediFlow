import { formatDecimal } from "@/lib/format";
import { averagePerVisit, completedMonthValues, MONTH_LABELS, monthlyFigures, monthlyValues, revenueOf } from "../dashboardData";
import type { OutpatientStatisticsDTO, OutpatientStatisticsFigures } from "../types";
import { CalendarHeatmap, CartesianChart, ChartCard, ChartLegend, RankedBars, SegmentedBar, Sparkline, compact } from "./ReportCharts";
import { ReportDepartmentName } from "./ReportDepartmentName";

type RevenueKey = "examRevenue" | "labRevenue" | "prescriptionRevenue" | "unclassifiedRevenue";
export const revenueLines: { key: RevenueKey; label: string; color: string }[] = [
  { key: "examRevenue", label: "Khám bệnh", color: "text-chart-1" },
  { key: "labRevenue", label: "Xét nghiệm", color: "text-chart-2" },
  { key: "prescriptionRevenue", label: "Thuốc", color: "text-chart-3" },
  { key: "unclassifiedRevenue", label: "Chưa phân loại", color: "text-chart-neutral" },
];
/** The unclassified line only appears when some receipt in range actually lacks a service line. */
export const visibleLines = (totals: OutpatientStatisticsFigures) =>
  revenueLines.filter(line => line.key !== "unclassifiedRevenue" || totals.unclassifiedRevenue > 0);

export const money = (value: number) => `${formatDecimal(Math.round(value))} ₫`;
export const compactMoney = (value: number) => `${compact(value)} ₫`;

function StatTile({ label, value, note, trend }: { label: string; value: string; note?: string; trend?: (number | null)[] }) {
  return <div className="min-w-0 rounded-xl border border-border bg-surface p-4">
    <dt className="text-sm text-muted-foreground">{label}</dt>
    <dd className="mt-1 text-2xl font-semibold tabular-nums">{value}</dd>
    {note && <dd className="mt-0.5 text-xs text-muted-foreground">{note}</dd>}
    {trend && <dd><Sparkline values={trend} /></dd>}
  </div>;
}

export function YearOverview({ report, year }: { report: OutpatientStatisticsDTO; year: number }) {
  const { totals } = report;
  const lines = visibleLines(totals);
  const revenue = revenueOf(totals);
  const months = monthlyFigures(report);
  const revenueTrend = monthlyValues(report, revenueOf);
  const visitTrend = completedMonthValues(report, row => row.completedVisits);
  const stackSeries = lines.map(line => ({ label: line.label, color: line.color, values: monthlyValues(report, row => row[line.key]) }));
  const departments = [...report.departments].sort((a, b) => revenueOf(b) - revenueOf(a));
  return <div className="space-y-4">
    <dl className="grid grid-cols-2 gap-3 xl:grid-cols-4" aria-label="Tổng hợp năm">
      <StatTile label="Doanh thu" value={compactMoney(revenue)} note={money(revenue)} trend={completedMonthValues(report, revenueOf)} />
      <StatTile label="Lượt khám hoàn tất" value={formatDecimal(totals.completedVisits)} note="lượt" trend={visitTrend} />
      <StatTile label="Doanh thu mỗi lượt" value={compactMoney(averagePerVisit(totals))} note="trung bình cả năm" trend={monthlyValues(report, averagePerVisit)} />
      <StatTile label="Tiền đã hoàn" value={compactMoney(totals.refunds)} note={revenue > 0 ? `${((totals.refunds / revenue) * 100).toFixed(1).replace(".", ",")}% doanh thu` : undefined} trend={completedMonthValues(report, row => row.refunds)} />
    </dl>
    <div className="grid min-w-0 gap-4 xl:grid-cols-5">
      <ChartCard title="Doanh thu theo tháng" subtitle="Chia theo loại dịch vụ" className="xl:col-span-3">
        <ChartLegend items={lines} />
        <CartesianChart kind="stacked" labels={MONTH_LABELS} series={stackSeries} format={money} description={`Doanh thu từng tháng năm ${year} theo loại dịch vụ. Bảng bên dưới có đầy đủ số liệu.`} />
      </ChartCard>
      <ChartCard title="Cơ cấu doanh thu" subtitle={`Tỷ trọng từng dịch vụ năm ${year}`} className="xl:col-span-2">
        <SegmentedBar parts={lines.map(line => ({ label: line.label, color: line.color, value: totals[line.key] }))} format={money} description={lines.map(line => `${line.label} ${formatDecimal(totals[line.key])}`).join(", ")} />
      </ChartCard>
    </div>
    <div className="grid min-w-0 gap-4 xl:grid-cols-2">
      <ChartCard title="Lượt khám theo tháng" subtitle="Lượt khám đã hoàn tất">
        <CartesianChart kind="line" labels={MONTH_LABELS} series={[{ label: "Lượt khám", color: "text-chart-1", values: visitTrend }]} format={value => `${formatDecimal(value)} lượt`} description={`Xu hướng lượt khám từng tháng năm ${year}.`} height={220} />
      </ChartCard>
      <ChartCard title="Doanh thu mỗi lượt khám" subtitle="Doanh thu chia cho lượt khám từng tháng">
        <CartesianChart kind="line" labels={MONTH_LABELS} series={[{ label: "Doanh thu mỗi lượt", color: "text-chart-2", values: monthlyValues(report, averagePerVisit) }]} format={money} description={`Doanh thu trung bình mỗi lượt khám từng tháng năm ${year}.`} height={220} />
      </ChartCard>
    </div>
    <ChartCard title="Mật độ lượt khám theo ngày" subtitle="Mỗi ô là một ngày, màu càng đậm càng đông; thấy rõ ngày nghỉ và mùa cao điểm">
      <CalendarHeatmap days={report.days.map(day => ({ date: day.date, value: day.completedVisits }))} unit="lượt" description={`Lịch lượt khám từng ngày năm ${year}.`} />
    </ChartCard>
    {departments.length > 1 && <ChartCard title="Doanh thu theo khoa" subtitle="Xếp từ cao xuống thấp">
      <RankedBars format={money} rows={departments.map(row => ({ id: row.departmentId, label: <ReportDepartmentName id={row.departmentId} />, value: revenueOf(row), note: `${formatDecimal(row.completedVisits)} lượt` }))} />
    </ChartCard>}
    <ChartCard title="Chi tiết doanh thu theo tháng" subtitle="Số liệu đầy đủ của các biểu đồ trên">
      <div className="mt-3 overflow-x-auto">
        <table className="w-full min-w-[40rem] text-right text-sm tabular-nums">
          <thead className="border-b border-border text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="py-2 pr-3 text-left font-medium">Tháng</th>
              <th scope="col" className="px-3 py-2 font-medium">Lượt khám</th>
              {lines.map(line => <th key={line.key} scope="col" className="px-3 py-2 font-medium">{line.label}</th>)}
              <th scope="col" className="px-3 py-2 font-medium">Doanh thu</th>
              <th scope="col" className="py-2 pl-3 font-medium">Đã hoàn</th>
            </tr>
          </thead>
          <tbody>
            {months.map((row, index) => revenueTrend[index] === null ? null : <tr key={MONTH_LABELS[index]} className="border-b border-border last:border-0">
              <th scope="row" className="py-2 pr-3 text-left font-medium">Tháng {index + 1}</th>
              <td className="px-3 py-2">{formatDecimal(row.completedVisits)}</td>
              {lines.map(line => <td key={line.key} className="px-3 py-2">{formatDecimal(row[line.key])}</td>)}
              <td className="px-3 py-2 font-medium">{formatDecimal(revenueOf(row))}</td>
              <td className="py-2 pl-3">{formatDecimal(row.refunds)}</td>
            </tr>)}
          </tbody>
          <tfoot className="border-t border-control-border font-semibold">
            <tr>
              <th scope="row" className="py-2 pr-3 text-left">Cả năm</th>
              <td className="px-3 py-2">{formatDecimal(totals.completedVisits)}</td>
              {lines.map(line => <td key={line.key} className="px-3 py-2">{formatDecimal(totals[line.key])}</td>)}
              <td className="px-3 py-2">{formatDecimal(revenue)}</td>
              <td className="py-2 pl-3">{formatDecimal(totals.refunds)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </ChartCard>
  </div>;
}
