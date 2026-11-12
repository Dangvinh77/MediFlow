
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
import { formatDecimal } from "@/lib/format";
import { averagePerVisit, commonCutoff, completedMonthValues, cumulativeRevenue, figuresThrough, lastFullMonthIndex, MONTH_LABELS, monthlyValues, percentChange, revenueOf } from "../dashboardData";
import type { OutpatientStatisticsDTO, OutpatientStatisticsFigures } from "../types";
import { CartesianChart, ChartCard, ChartLegend, Dumbbell, compact } from "./ReportCharts";
import { ReportDepartmentName } from "./ReportDepartmentName";
import { compactMoney, money, revenueLines } from "./YearOverview";

/** The arrow plus sign carry direction; a rise is not "good" for every measure, so no status colour. */
function Delta({ current, previous }: { current: number; previous: number }) {
  const change = percentChange(current, previous);
  if (change === null) return <span className="text-muted-foreground"><L10n>{"Không có số năm so sánh"}</L10n></span>;
  const flat = Math.abs(change) < .05;
  return <span className="font-medium tabular-nums"><L10nValue>{flat ? "=" : change > 0 ? "▲" : "▼"}</L10nValue> <L10nValue>{Math.abs(change).toFixed(1).replace(".", ",")}</L10nValue>%</span>;
}

function CompareTile({ label, current, previous, format, compareYear }: { label: string; current: number; previous: number; format: (value: number) => string; compareYear: number }) {
  return <div className="min-w-0 rounded-xl border border-border bg-surface p-4">
    <dt className="text-sm text-muted-foreground"><L10nValue>{label}</L10nValue></dt>
    <dd className="mt-1 text-2xl font-semibold tabular-nums"><L10nValue>{format(current)}</L10nValue></dd>
    <dd className="mt-1 text-sm"><Delta current={current} previous={previous} /></dd>
    <dd className="mt-0.5 text-xs text-muted-foreground"><L10n>{"Năm "}</L10n><L10nValue>{compareYear}</L10nValue>: <L10nValue>{format(previous)}</L10nValue></dd>
  </div>;
}

export function YearComparison({ base, compare, baseYear, compareYear, basePeriod, comparePeriod }: {
  base: OutpatientStatisticsDTO;
  compare: OutpatientStatisticsDTO;
  baseYear: number;
  compareYear: number;
  /** Both years cut to the shared period: `departments` cannot be re-cut from `days`. */
  basePeriod: OutpatientStatisticsDTO;
  comparePeriod: OutpatientStatisticsDTO;
}) {
  const cutoff = commonCutoff(base, compare);
  const partial = cutoff !== "12-31";
  const baseTotal = figuresThrough(base, cutoff);
  const compareTotal = figuresThrough(compare, cutoff);
  const periodLabel = partial ? `cùng kỳ 01/01 – ${cutoff.slice(3)}/${cutoff.slice(0, 2)}` : "cả năm";
  const pair = (value: (row: OutpatientStatisticsFigures) => number) => [
    { label: String(compareYear), color: "text-chart-neutral", values: monthlyValues(compare, value) },
    { label: String(baseYear), color: "text-chart-1", values: monthlyValues(base, value) },
  ];
  const baseRevenue = monthlyValues(base, revenueOf);
  const compareRevenue = monthlyValues(compare, revenueOf);
  const lastFullMonth = lastFullMonthIndex(cutoff);
  const monthlyGap = baseRevenue.map((value, index) => {
    const other = compareRevenue[index];
    return value === null || other === null || index > lastFullMonth ? null : value - other;
  });
  const cumulative = [
    { label: String(compareYear), color: "text-chart-neutral", values: cumulativeRevenue(compare) },
    { label: String(baseYear), color: "text-chart-1", values: cumulativeRevenue(base) },
  ];
  const departments = [...new Set([...basePeriod.departments, ...comparePeriod.departments].map(row => row.departmentId))];
  const departmentRevenue = (report: OutpatientStatisticsDTO, id: string) => {
    const row = report.departments.find(item => item.departmentId === id);
    return row ? revenueOf(row) : 0;
  };
  const baseVisits = monthlyValues(base, row => row.completedVisits);
  const compareVisits = monthlyValues(compare, row => row.completedVisits);
  const visitLines = [
    { label: String(compareYear), color: "text-chart-neutral", values: completedMonthValues(compare, row => row.completedVisits) },
    { label: String(baseYear), color: "text-chart-1", values: completedMonthValues(base, row => row.completedVisits) },
  ];
  const legend = pair(revenueOf).map(item => ({ label: item.label, color: item.color }));
  return <div className="space-y-4">
    <p className="text-sm text-muted-foreground"><L10n>{"So sánh năm "}</L10n><L10nValue>{baseYear}</L10nValue><L10n>{" với "}</L10n><L10nValue>{compareYear}</L10nValue><L10n>{", phần trăm tính trên "}</L10n><L10nValue>{periodLabel}</L10nValue><L10nValue>{partial ? " để hai năm cùng số ngày" : ""}</L10nValue>.
    </p>
    <dl className="grid grid-cols-2 gap-3 xl:grid-cols-4" aria-label="So sánh tổng hợp">
      <CompareTile label="Doanh thu" current={revenueOf(baseTotal)} previous={revenueOf(compareTotal)} format={compactMoney} compareYear={compareYear} />
      <CompareTile label="Lượt khám hoàn tất" current={baseTotal.completedVisits} previous={compareTotal.completedVisits} format={formatDecimal} compareYear={compareYear} />
      <CompareTile label="Doanh thu mỗi lượt" current={averagePerVisit(baseTotal)} previous={averagePerVisit(compareTotal)} format={compactMoney} compareYear={compareYear} />
      <CompareTile label="Tiền đã hoàn" current={baseTotal.refunds} previous={compareTotal.refunds} format={compactMoney} compareYear={compareYear} />
    </dl>
    <div className="grid min-w-0 gap-4 xl:grid-cols-2">
      <ChartCard title="Doanh thu từng tháng" subtitle={partial ? `Hai cột cạnh nhau; tháng ${cutoff.slice(0, 2)} chưa trọn tháng` : "Hai cột cạnh nhau cho mỗi tháng"}>
        <ChartLegend items={legend} />
        <CartesianChart kind="grouped" labels={MONTH_LABELS} series={pair(revenueOf)} format={money} description={`Doanh thu từng tháng năm ${baseYear} và ${compareYear}. Bảng bên dưới có đầy đủ số liệu.`} />
      </ChartCard>
      <ChartCard title="Doanh thu lũy kế" subtitle="Đường nằm trên là năm đang dẫn trước">
        <ChartLegend items={legend.map(item => ({ ...item, line: true }))} />
        <CartesianChart kind="line" labels={MONTH_LABELS} series={cumulative} format={money} description={`Doanh thu cộng dồn năm ${baseYear} và ${compareYear}.`} />
      </ChartCard>
      <ChartCard title="Lượt khám từng tháng" subtitle="Hai đường cùng trục để thấy lệch mùa">
        <ChartLegend items={legend.map(item => ({ ...item, line: true }))} />
        <CartesianChart kind="line" labels={MONTH_LABELS} series={visitLines} format={value => `${formatDecimal(value)} lượt`} description={`Lượt khám từng tháng năm ${baseYear} và ${compareYear}.`} />
      </ChartCard>
      <ChartCard title="Chênh lệch doanh thu từng tháng" subtitle={`${baseYear} trừ ${compareYear}; tháng chưa trọn được bỏ qua`}>
        <ChartLegend items={[{ label: `${baseYear} cao hơn`, color: "text-chart-1" }, { label: `${baseYear} thấp hơn`, color: "text-chart-3" }]} />
        <CartesianChart kind="diverging" labels={MONTH_LABELS} series={[{ label: "Chênh lệch", color: "text-chart-1", values: monthlyGap }]} negativeColor="text-chart-3" format={value => `${value > 0 ? "+" : ""}${money(value)}`} description={`Chênh lệch doanh thu từng tháng giữa ${baseYear} và ${compareYear}.`} />
      </ChartCard>
    </div>
    <div className="grid min-w-0 gap-4 xl:grid-cols-2">
      <ChartCard title="Doanh thu theo dịch vụ" subtitle={`Từ ${compareYear} sang ${baseYear}, ${periodLabel}`} className={departments.length > 1 ? "" : "xl:col-span-2"}>
        <Dumbbell baseLabel={String(baseYear)} compareLabel={String(compareYear)} format={value => compactMoney(value)}
          rows={revenueLines.filter(line => baseTotal[line.key] > 0 || compareTotal[line.key] > 0).map(line => ({ id: line.key, label: line.label, base: baseTotal[line.key], compare: compareTotal[line.key] }))} />
      </ChartCard>
      <L10nValue>{departments.length > 1 && <ChartCard title="Doanh thu theo khoa" subtitle={`Từ ${compareYear} sang ${baseYear}, ${periodLabel}`}>
        <Dumbbell baseLabel={String(baseYear)} compareLabel={String(compareYear)} format={value => compactMoney(value)}
          rows={departments.map(id => ({ id, label: <ReportDepartmentName id={id} />, base: departmentRevenue(basePeriod, id), compare: departmentRevenue(comparePeriod, id) }))} />
      </ChartCard>}</L10nValue>
    </div>
    <ChartCard title="Bảng so sánh theo tháng" subtitle="Số liệu đầy đủ của các biểu đồ trên">
      <div className="mt-3 overflow-x-auto">
        <table className="w-full min-w-[36rem] text-right text-sm tabular-nums">
          <thead className="border-b border-border text-xs text-muted-foreground">
            <tr>
              <th scope="col" className="py-2 pr-3 text-left font-medium"><L10n>{"Tháng"}</L10n></th>
              <th scope="col" className="px-3 py-2 font-medium">Doanh thu <L10nValue>{compareYear}</L10nValue></th>
              <th scope="col" className="px-3 py-2 font-medium">Doanh thu <L10nValue>{baseYear}</L10nValue></th>
              <th scope="col" className="px-3 py-2 font-medium"><L10n>{"Thay đổi"}</L10n></th>
              <th scope="col" className="px-3 py-2 font-medium"><L10n>{"Lượt khám "}</L10n><L10nValue>{compareYear}</L10nValue></th>
              <th scope="col" className="py-2 pl-3 font-medium"><L10n>{"Lượt khám "}</L10n><L10nValue>{baseYear}</L10nValue></th>
            </tr>
          </thead>
          <tbody>
            <L10nValue>{MONTH_LABELS.map((label, index) => {
              const baseValue = baseRevenue[index], compareValue = compareRevenue[index];
              return <tr key={label} className="border-b border-border last:border-0">
                <th scope="row" className="py-2 pr-3 text-left font-medium"><L10n>{"Tháng "}</L10n><L10nValue>{index + 1}</L10nValue></th>
                <td className="px-3 py-2"><L10nValue>{compareValue === null ? "—" : compact(compareValue)}</L10nValue></td>
                <td className="px-3 py-2"><L10nValue>{baseValue === null ? "—" : compact(baseValue)}</L10nValue></td>
                <td className="px-3 py-2"><L10nValue>{monthlyGap[index] === null || baseValue === null || compareValue === null ? "—" : <Delta current={baseValue} previous={compareValue} />}</L10nValue></td>
                <td className="px-3 py-2"><L10nValue>{compareVisits[index] === null ? "—" : formatDecimal(compareVisits[index])}</L10nValue></td>
                <td className="py-2 pl-3"><L10nValue>{baseVisits[index] === null ? "—" : formatDecimal(baseVisits[index])}</L10nValue></td>
              </tr>;
            })}</L10nValue>
          </tbody>
        </table>
      </div>
    </ChartCard>
  </div>;
}
