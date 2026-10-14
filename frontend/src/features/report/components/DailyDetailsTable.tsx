import { DataTableShell } from "@/components/ui/DataTableShell";
import { formatDecimal, formatLocalDate } from "@/lib/format";
import type { DailyReportDTO } from "../types";

export function DailyDetailsTable({ days }: { days: DailyReportDTO[] }) {
  return (
    <DataTableShell title="Chi tiết theo ngày">
      <table className="w-full min-w-[620px] text-left text-sm">
        <caption className="sr-only">Chi tiết báo cáo tháng theo ngày</caption>
        <thead className="bg-surface-muted text-muted-foreground"><tr>
          <th scope="col" className="px-4 py-3 font-medium">Ngày</th>
          <th scope="col" className="px-4 py-3 text-right font-medium">Lượt khám</th>
          <th scope="col" className="px-4 py-3 text-right font-medium">Xét nghiệm</th>
          <th scope="col" className="px-4 py-3 text-right font-medium">Đơn thuốc</th>
          <th scope="col" className="px-4 py-3 text-right font-medium">Doanh thu</th>
        </tr></thead>
        <tbody>{days.map((day) => <tr key={day.reportDate} className="border-b border-border last:border-0">
          <th scope="row" className="px-4 py-3 font-normal tabular-nums">{formatLocalDate(day.reportDate)}</th>
          <td className="px-4 py-3 text-right tabular-nums">{day.visitCount.toLocaleString("vi-VN")}</td>
          <td className="px-4 py-3 text-right tabular-nums">{day.labCount.toLocaleString("vi-VN")}</td>
          <td className="px-4 py-3 text-right tabular-nums">{day.prescriptionCount.toLocaleString("vi-VN")}</td>
          <td className="px-4 py-3 text-right tabular-nums">{formatDecimal(day.revenue)}</td>
        </tr>)}</tbody>
      </table>
    </DataTableShell>
  );
}
