import { useId } from "react";
import { formatDecimal, formatLocalDate } from "@/lib/format";
import type { OutpatientReportPeriod } from "../types";

type Metric = "cashReceived" | "refunds" | "netCash" | "completedVisits" | "labTests" | "dispensedPrescriptions";
const cash: { key: Metric; label: string }[] = [{key:"cashReceived",label:"Thu vào"},{key:"refunds",label:"Hoàn ra"},{key:"netCash",label:"Thu ròng"}];
const activity: { key: Metric; label: string }[] = [{key:"completedVisits",label:"Khám hoàn tất"},{key:"labTests",label:"Xét nghiệm hoàn tất"},{key:"dispensedPrescriptions",label:"Đơn thuốc đã xuất"}];
const colors = ["text-primary", "text-danger", "text-success"];
const dashes = [undefined, "6 4", "2 3"];
const compact = new Intl.NumberFormat("vi-VN", {notation:"compact",maximumFractionDigits:1});

/** Display server period metrics without reconstructing authoritative totals. */
export function OutpatientTrendChart({periods, kind, currency}: {periods: OutpatientReportPeriod[]; kind:"cash"|"activity"; currency:string}) {
  const titleId = useId();
  const series = kind === "cash" ? cash : activity;
  const title = kind === "cash" ? `Dòng tiền ngoại trú (${currency})` : "Hoạt động ngoại trú (lượt / xét nghiệm / đơn)";
  if (!periods.length) return null;
  const values = periods.flatMap(period => series.map(item => period[item.key]));
  if (values.some(value => !Number.isFinite(value))) return <p role="alert">Chưa thể biểu diễn số liệu không hợp lệ.</p>;
  const min = Math.min(0, ...values), max = Math.max(1, ...values);
  const left=64, right=620, top=20, bottom=190;
  const y = (value:number) => bottom - (value-min)/(max-min)*(bottom-top);
  const x = (index:number) => periods.length === 1 ? (left+right)/2 : left+index*(right-left)/(periods.length-1);
  const stride = Math.max(1,Math.ceil(periods.length/4));
  return <figure className="min-w-0 rounded-lg border border-border p-3 sm:p-4">
    <figcaption className="font-semibold">{title}</figcaption>
    <div className="mt-2 flex flex-wrap gap-x-4 gap-y-2 text-xs" aria-label="Chú giải">
      {series.map((item,index) => <span key={item.key} className={`inline-flex items-center gap-2 ${colors[index]}`}>
        <svg width="24" height="12" aria-hidden="true"><line x1="0" x2="24" y1="6" y2="6" stroke="currentColor" strokeWidth="2" strokeDasharray={dashes[index]} /></svg>{item.label}
      </span>)}
    </div>
    <div className="overflow-x-auto">
    <svg viewBox="0 0 640 235" role="img" aria-labelledby={titleId} className="mt-2 block w-full min-w-[480px]">
      <title id={titleId}>{title}. Bảng các kỳ bên dưới có đầy đủ giá trị.</title>
      {Array.from({length:5},(_,index) => min+(max-min)*index/4).map((value,index) => <g key={index}>
        <line x1={left} x2={right} y1={y(value)} y2={y(value)} className="stroke-border" />
        <text x={left-8} y={y(value)+4} textAnchor="end" className="fill-current text-xs text-muted-foreground">{compact.format(value)}</text>
      </g>)}
      <line x1={left} x2={right} y1={y(0)} y2={y(0)} className="stroke-muted-foreground" strokeDasharray="3 3" />
      {series.map((item,index) => <g key={item.key} className={colors[index]}>
        <polyline points={periods.map((period,i) => `${x(i)},${y(period[item.key])}`).join(" ")} fill="none" stroke="currentColor" strokeWidth="2" strokeDasharray={dashes[index]} />
        {periods.map((period,i) => <circle key={period.fromDate} cx={x(i)} cy={y(period[item.key])} r="3" fill="currentColor">
          <title>{formatLocalDate(period.fromDate)}: {item.label} {formatDecimal(period[item.key])}{kind === "cash" ? ` ${currency}` : ""}</title>
        </circle>)}
      </g>)}
      {periods.map((period,index) => index%stride === 0 || index===periods.length-1 ? <text key={period.fromDate} x={x(index)} y="215" textAnchor={index===0 ? "start" : index===periods.length-1 ? "end":"middle"} className="fill-current text-xs text-muted-foreground">{period.fromDate===period.toDate ? formatLocalDate(period.fromDate).slice(0,5) : period.fromDate.slice(0,7)}</text> : null)}
    </svg>
    </div>
    <p className="text-xs text-muted-foreground">Mỗi điểm là một kỳ; số 0 chỉ thuộc phạm vi đã đối soát. Vuốt ngang biểu đồ khi màn hình hẹp.</p>
  </figure>;
}
