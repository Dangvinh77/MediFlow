"use client";

import { useEffect, useId, useRef, useState } from "react";
import { formatDecimal, formatLocalDate } from "@/lib/format";
import type { DailyReportDTO } from "../types";

const tickFormatter = new Intl.NumberFormat("vi-VN", { notation: "compact", maximumFractionDigits: 1 });

export function MonthlyRevenueChart({ days }: { days: DailyReportDTO[] }) {
  const root = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const [width, setWidth] = useState(640);
  useEffect(() => {
    if (!root.current || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(200, entry.contentRect.width)));
    observer.observe(root.current);
    return () => observer.disconnect();
  }, []);

  const left = 64, right = width - 12, top = 20, bottom = 210;
  const values = days.map((day) => day.revenue);
  const min = Math.min(0, ...values);
  const max = Math.max(1, ...values);
  const y = (value: number) => bottom - (value - min) / (max - min) * (bottom - top);
  const step = (right - left) / Math.max(days.length, 1);
  const tickStride = Math.max(1, Math.ceil(days.length / 4));

  return (
    <div ref={root}>
      <p className="mb-2 text-xs text-muted-foreground">Giá trị doanh thu</p>
      <svg viewBox={`0 0 ${width} 250`} className="block w-full" role="img" aria-labelledby={titleId}>
        <title id={titleId}>Doanh thu theo {days.length} ngày. Bảng chi tiết bên dưới có đầy đủ giá trị.</title>
        {Array.from({ length: 5 }, (_, i) => min + (max - min) * i / 4).map((value, i) => (
          <g key={i}>
            <line x1={left} x2={right} y1={y(value)} y2={y(value)} className="stroke-border" />
            <text x={left - 8} y={y(value) + 4} textAnchor="end" className="fill-current text-xs text-muted-foreground">{tickFormatter.format(value)}</text>
          </g>
        ))}
        {days.map((day, index) => (
          <g key={day.reportDate}>
            <rect x={left + index * step + step * .16} y={Math.min(y(0), y(day.revenue))} width={step * .68} height={Math.abs(y(day.revenue) - y(0))} rx={2} className="fill-primary">
              <title>{formatLocalDate(day.reportDate)}: {formatDecimal(day.revenue)}</title>
            </rect>
            {(index % tickStride === 0 || index === days.length - 1) && (
              <text x={left + index * step + step / 2} y={232} textAnchor={index === 0 ? "start" : index === days.length - 1 ? "end" : "middle"} className="fill-current text-xs text-muted-foreground">{day.reportDate.slice(8, 10)}</text>
            )}
          </g>
        ))}
      </svg>
      <p className="text-xs text-muted-foreground">Ngày trong tháng</p>
    </div>
  );
}
