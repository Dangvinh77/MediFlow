"use client";

import { useEffect, useId, useRef, useState, type PointerEvent, type ReactNode } from "react";
import { formatLocalDate } from "@/lib/format";
import { calendarCells } from "../dashboardData";

export interface ChartSeries {
  label: string;
  /** Tailwind text colour; marks paint with `currentColor` so every theme resolves it. */
  color: string;
  values: (number | null)[];
  dashed?: boolean;
}

const compactNumber = new Intl.NumberFormat("vi-VN", { notation: "compact", maximumFractionDigits: 1 });
export const compact = (value: number) => compactNumber.format(value);

function useChartWidth() {
  const ref = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(640);
  useEffect(() => {
    if (!ref.current || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(240, entry.contentRect.width)));
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, []);
  return { ref, width };
}

/** Round ticks from zero (or a negative floor) so axes never read like raw maxima. */
export function niceTicks(min: number, max: number, count = 4) {
  const span = Math.max(max - min, 1);
  const rough = span / count;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const step = [1, 2, 2.5, 5, 10].map(factor => factor * magnitude).find(candidate => candidate >= rough) ?? rough;
  const ticks: number[] = [];
  for (let tick = Math.floor(min / step) * step; ; tick += step) {
    ticks.push(tick);
    if (tick >= max) return ticks;
  }
}

/** Bar rounded only at its data end; `height` is signed, positive bars grow up from y + height. */
function barPath(x: number, y: number, width: number, height: number, radius: number) {
  const r = Math.min(radius, width / 2, Math.abs(height));
  if (height >= 0) return `M${x},${y + height}V${y + r}Q${x},${y} ${x + r},${y}H${x + width - r}Q${x + width},${y} ${x + width},${y + r}V${y + height}Z`;
  return `M${x},${y + height}V${y - r}Q${x},${y} ${x + r},${y}H${x + width - r}Q${x + width},${y} ${x + width},${y - r}V${y + height}Z`;
}

export function ChartCard({ title, subtitle, children, className = "" }: { title: string; subtitle?: string; children: ReactNode; className?: string }) {
  return <figure className={`min-w-0 rounded-xl border border-border bg-surface p-4 sm:p-5 ${className}`}>
    <figcaption>
      <span className="block font-semibold">{title}</span>
      {subtitle && <span className="mt-0.5 block text-sm text-muted-foreground">{subtitle}</span>}
    </figcaption>
    {children}
  </figure>;
}

export function ChartLegend({ items }: { items: { label: string; color: string; dashed?: boolean; line?: boolean }[] }) {
  return <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-foreground" aria-label="Chú giải">
    {items.map(item => <li key={item.label} className="inline-flex items-center gap-2">
      {item.line
        ? <svg width="20" height="10" aria-hidden="true" className={item.color}><line x1="0" x2="20" y1="5" y2="5" stroke="currentColor" strokeWidth="2" strokeDasharray={item.dashed ? "5 3" : undefined} /></svg>
        : <svg width="10" height="10" aria-hidden="true" className={item.color}><rect width="10" height="10" rx="2" fill="currentColor" /></svg>}
      {item.label}
    </li>)}
  </ul>;
}

function Tooltip({ x, width, title, rows, footer }: { x: number; width: number; title: string; rows: { label: string; color: string; value: string }[]; footer?: { label: string; value: string } }) {
  const boxWidth = 196;
  const left = Math.min(Math.max(x - boxWidth / 2, 0), width - boxWidth);
  return <div role="status" style={{ left, width: boxWidth }} className="pointer-events-none absolute top-0 z-10 rounded-lg border border-border bg-surface p-2 text-xs shadow-md">
    <p className="font-semibold">{title}</p>
    <dl className="mt-1 space-y-0.5">
      {rows.map(row => <div key={row.label} className="flex items-center justify-between gap-3">
        <dt className="inline-flex min-w-0 items-center gap-1.5 text-muted-foreground">
          <svg width="8" height="8" aria-hidden="true" className={`shrink-0 ${row.color}`}><rect width="8" height="8" rx="2" fill="currentColor" /></svg>{row.label}
        </dt>
        <dd className="font-medium tabular-nums">{row.value}</dd>
      </div>)}
      {footer && <div className="flex justify-between gap-3 border-t border-border pt-0.5"><dt className="text-muted-foreground">{footer.label}</dt><dd className="font-semibold tabular-nums">{footer.value}</dd></div>}
    </dl>
  </div>;
}

export type CartesianKind = "stacked" | "grouped" | "line" | "diverging";

/**
 * One axis, one hover layer, four mark styles over the same bands. Months keep their position
 * whatever the kind, so charts stacked on the page line up. A null value is "not reported yet".
 */
export function CartesianChart({ kind, labels, series, format, description, negativeColor, height = 260 }: {
  kind: CartesianKind;
  labels: string[];
  series: ChartSeries[];
  format: (value: number) => string;
  /** Alt text; the table below every chart carries the exact figures. */
  description: string;
  negativeColor?: string;
  height?: number;
}) {
  const { ref, width } = useChartWidth();
  const titleId = useId();
  const [hover, setHover] = useState<number | null>(null);
  const left = 48, right = width - 6, top = 10, bottom = height - 24;
  const band = (right - left) / labels.length;
  const totals = labels.map((_, index) => series.reduce((sum, item) => sum + (item.values[index] ?? 0), 0));
  const values = series.flatMap(item => item.values.filter((value): value is number => value !== null));
  const max = Math.max(0, ...(kind === "stacked" ? totals : values));
  const min = Math.min(0, ...values);
  const ticks = niceTicks(min, max);
  const low = ticks[0], high = ticks[ticks.length - 1];
  const y = (value: number) => bottom - ((value - low) / (high - low || 1)) * (bottom - top);
  const center = (index: number) => left + band * (index + .5);
  // Thin bands (a month or more of days) label every n-th band so axis text never overlaps.
  const stride = Math.max(1, Math.ceil(26 / band));

  function onPointer(event: PointerEvent<SVGSVGElement>) {
    const box = event.currentTarget.getBoundingClientRect();
    const index = Math.floor((event.clientX - box.left - left) / band);
    setHover(index >= 0 && index < labels.length ? index : null);
  }

  function bars() {
    const barWidth = kind === "grouped" ? Math.min(band * .36, 18) : Math.min(band * .62, 34);
    return labels.map((label, index) => {
      if (kind === "stacked") {
        let base = 0;
        const topMost = series.reduce((last, item, order) => (item.values[index] ? order : last), -1);
        return <g key={label}>{series.map((item, order) => {
          const value = item.values[index] ?? 0;
          if (value <= 0) return null;
          const yTop = y(base + value), yBase = y(base);
          base += value;
          // A 2px surface gap separates segments; only the top one carries the rounded end.
          const segment = Math.max(yBase - yTop - (order === 0 ? 0 : 2), 1);
          const shape = order === topMost
            ? barPath(center(index) - barWidth / 2, yTop, barWidth, segment, 4)
            : `M${center(index) - barWidth / 2},${yTop}h${barWidth}v${segment}h${-barWidth}Z`;
          return <path key={item.label} d={shape} fill="currentColor" className={item.color} />;
        })}</g>;
      }
      if (kind === "grouped") {
        const groupWidth = barWidth * series.length + 2 * (series.length - 1);
        return <g key={label}>{series.map((item, order) => {
          const value = item.values[index];
          if (value === null || value === undefined || value <= 0) return null;
          return <path key={item.label} d={barPath(center(index) - groupWidth / 2 + order * (barWidth + 2), y(value), barWidth, bottom - y(value), 4)} fill="currentColor" className={item.color} />;
        })}</g>;
      }
      const value = series[0].values[index];
      if (value === null || value === undefined || value === 0) return null;
      const positive = value > 0;
      return <path key={label} d={barPath(center(index) - barWidth / 2, y(value), barWidth, y(0) - y(value), 4)}
        fill="currentColor" className={positive ? series[0].color : negativeColor ?? series[0].color} />;
    });
  }

  function lines() {
    return series.map((item, order) => {
      const points = item.values.flatMap((value, index) => value === null ? [] : [[center(index), y(value)] as const]);
      if (!points.length) return null;
      const path = points.map(([x, py]) => `${x},${py}`).join(" ");
      const [lastX, lastY] = points[points.length - 1];
      return <g key={item.label} className={item.color}>
        {series.length === 1 && points.length > 1 && <polygon points={`${points[0][0]},${bottom} ${path} ${lastX},${bottom}`} fill="currentColor" fillOpacity=".12" />}
        <polyline points={path} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" strokeDasharray={item.dashed ? "6 4" : undefined} />
        {hover !== null && item.values[hover] !== null && item.values[hover] !== undefined
          ? <circle cx={center(hover)} cy={y(item.values[hover] as number)} r="4" fill="currentColor" stroke="var(--surface)" strokeWidth="2" />
          : <circle cx={lastX} cy={lastY} r="3.5" fill="currentColor" stroke="var(--surface)" strokeWidth="2" />}
        {series.length > 1 && <text x={Math.min(lastX, right - 2)} y={lastY - 8 + order * 18} textAnchor="end" className="fill-foreground text-[11px] font-medium">{item.label}</text>}
      </g>;
    });
  }

  const tooltipRows = hover === null ? [] : series.flatMap(item => {
    const value = item.values[hover];
    return value === null || value === undefined ? [] : [{ label: item.label, color: item.color, value: format(value) }];
  });
  return <div ref={ref} className="relative mt-3 min-w-0">
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-labelledby={titleId} className="block max-w-full touch-pan-y"
      onPointerMove={onPointer} onPointerDown={onPointer} onPointerLeave={() => setHover(null)}>
      <title id={titleId}>{description}</title>
      {ticks.map(tick => <g key={tick}>
        <line x1={left} x2={right} y1={y(tick)} y2={y(tick)} className={tick === 0 ? "stroke-control-border" : "stroke-border"} strokeDasharray={tick === 0 ? undefined : "2 4"} />
        <text x={left - 6} y={y(tick) + 4} textAnchor="end" className="fill-muted-foreground text-[11px] tabular-nums">{compact(tick)}</text>
      </g>)}
      {hover !== null && <rect x={left + hover * band} y={top} width={band} height={bottom - top} className="fill-foreground/5" />}
      {kind === "line" ? lines() : bars()}
      {labels.map((label, index) => index % stride === 0 && <text key={label} x={center(index)} y={height - 6} textAnchor="middle" className="fill-muted-foreground text-[11px]">{label}</text>)}
    </svg>
    {hover !== null && tooltipRows.length > 0 && <Tooltip x={center(hover)} width={width} title={labels[hover]} rows={tooltipRows}
      footer={kind === "stacked" ? { label: "Tổng", value: format(totals[hover]) } : undefined} />}
  </div>;
}

/** Tile trend: no axis, the tile's own number carries the scale. */
export function Sparkline({ values }: { values: (number | null)[] }) {
  const points = values.flatMap((value, index) => value === null ? [] : [[index, value] as const]);
  if (points.length < 2) return null;
  const max = Math.max(...points.map(([, value]) => value), 1);
  const min = Math.min(...points.map(([, value]) => value));
  const x = (index: number) => 2 + (index / (values.length - 1)) * 96;
  const y = (value: number) => 22 - ((value - min) / (max - min || 1)) * 20;
  return <svg viewBox="0 0 100 24" preserveAspectRatio="none" aria-hidden="true" className="mt-2 block h-6 w-full text-chart-1">
    <polyline points={points.map(([index, value]) => `${x(index)},${y(value)}`).join(" ")} fill="none" stroke="currentColor" strokeWidth="1.5" vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
  </svg>;
}

/** One whole, at a glance: share printed on each block where it fits, names in the legend. */
export function SegmentedBar({ parts, format, description }: { parts: { label: string; color: string; value: number }[]; format: (value: number) => string; description: string }) {
  const total = parts.reduce((sum, part) => sum + part.value, 0);
  const visible = parts.filter(part => part.value > 0);
  return <div className="mt-4">
    <div role="img" aria-label={description} className="flex h-14 gap-0.5 overflow-hidden rounded-lg">
      {visible.map(part => {
        const share = total > 0 ? (part.value / total) * 100 : 0;
        return <div key={part.label} style={{ width: `${share}%` }} title={`${part.label}: ${format(part.value)}`} className={`flex min-w-1 items-center justify-center bg-current ${part.color}`}>
          {share >= 9 && <span className="text-sm font-semibold tabular-nums text-white">{Math.round(share)}%</span>}
        </div>;
      })}
    </div>
    <dl className="mt-3 space-y-1.5 text-sm">
      {visible.map(part => <div key={part.label} className="flex items-center justify-between gap-3">
        <dt className="inline-flex items-center gap-2 text-muted-foreground">
          <svg width="10" height="10" aria-hidden="true" className={part.color}><rect width="10" height="10" rx="2" fill="currentColor" /></svg>{part.label}
        </dt>
        <dd className="font-medium tabular-nums">{format(part.value)}</dd>
      </div>)}
    </dl>
  </div>;
}

/** Ranked horizontal bars, one hue: a shared baseline compares parts more exactly than slices. */
export function RankedBars({ rows, format, label = "Xếp hạng theo khoa" }: { rows: { id: string; label: ReactNode; value: number; note?: string }[]; format: (value: number) => string; label?: string }) {
  const max = Math.max(1, ...rows.map(row => row.value));
  return <ul className="mt-4 space-y-3" aria-label={label}>
    {rows.map(row => <li key={row.id} className="min-w-0">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 text-sm">
        <span className="min-w-0 font-medium">{row.label}</span>
        <span className="tabular-nums text-muted-foreground">{format(row.value)}{row.note ? ` · ${row.note}` : ""}</span>
      </div>
      <div className="mt-1 h-3 rounded-full bg-surface-muted">
        <div style={{ width: `${(row.value / max) * 100}%` }} className="h-full rounded-full bg-chart-1" />
      </div>
    </li>)}
  </ul>;
}

/** Before → after per item: gray dot is the reference year, coloured dot the main year. */
export function Dumbbell({ rows, format, baseLabel, compareLabel }: {
  rows: { id: string; label: ReactNode; base: number; compare: number }[];
  format: (value: number) => string;
  baseLabel: string;
  compareLabel: string;
}) {
  const max = Math.max(1, ...rows.flatMap(row => [row.base, row.compare]));
  const position = (value: number) => `${(value / max) * 100}%`;
  return <div className="mt-3">
    <ChartLegend items={[{ label: compareLabel, color: "text-chart-neutral" }, { label: baseLabel, color: "text-chart-1" }]} />
    <ul className="mt-3 space-y-4">
      {rows.map(row => <li key={row.id} className="min-w-0">
        <div className="flex flex-wrap items-baseline justify-between gap-x-3 text-sm">
          <span className="min-w-0 font-medium">{row.label}</span>
          <span className="tabular-nums text-muted-foreground">{format(row.compare)} → {format(row.base)}</span>
        </div>
        <div aria-hidden="true" className="relative mx-1.5 mt-2 h-3">
          <div className="absolute inset-x-0 top-1/2 h-px bg-border" />
          <div style={{ left: position(Math.min(row.base, row.compare)), width: `${(Math.abs(row.base - row.compare) / max) * 100}%` }} className="absolute top-1/2 h-0.5 -translate-y-1/2 bg-chart-neutral" />
          <span style={{ left: position(row.compare) }} className="absolute top-1/2 size-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-surface bg-chart-neutral" />
          <span style={{ left: position(row.base) }} className="absolute top-1/2 size-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-surface bg-chart-1" />
        </div>
      </li>)}
    </ul>
  </div>;
}

const HEAT_STEPS = [.18, .38, .6, .8, 1];
const WEEKDAYS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"];

/** Sequential, one hue, more is darker. Zero days keep the muted surface so gaps read as gaps. */
export function CalendarHeatmap({ days, unit, description }: { days: { date: string; value: number }[]; unit: string; description: string }) {
  const { ref, width } = useChartWidth();
  const titleId = useId();
  const [hover, setHover] = useState<{ date: string; value: number; x: number } | null>(null);
  const cells = calendarCells(days);
  const max = Math.max(1, ...days.map(day => day.value));
  const weeks = Math.max(1, ...cells.map(cell => cell.week + 1));
  const left = 24, top = 16;
  const cell = Math.min((width - left) / weeks, 16);
  const size = Math.max(cell - 2, 2);
  const height = top + cell * 7;
  const months = cells.filter(item => item.date.endsWith("-01"));
  return <div ref={ref} className="relative mt-3 min-w-0">
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-labelledby={titleId} className="block max-w-full" onPointerLeave={() => setHover(null)}>
      <title id={titleId}>{description}</title>
      {months.map(item => <text key={item.date} x={left + item.week * cell} y={10} className="fill-muted-foreground text-[11px]">T{Number(item.date.slice(5, 7))}</text>)}
      {WEEKDAYS.map((label, index) => index % 2 === 0 && <text key={label} x={0} y={top + index * cell + size - 1} className="fill-muted-foreground text-[10px]">{label}</text>)}
      {cells.map(item => {
        const x = left + item.week * cell;
        const step = HEAT_STEPS[Math.min(HEAT_STEPS.length - 1, Math.floor(((item.value - 1) / max) * HEAT_STEPS.length))];
        return <rect key={item.date} x={x} y={top + item.weekday * cell} width={size} height={size} rx={2}
          className={item.value > 0 ? "text-chart-1" : "fill-surface-muted"} fill={item.value > 0 ? "currentColor" : undefined} fillOpacity={item.value > 0 ? step : undefined}
          onPointerEnter={() => setHover({ date: item.date, value: item.value, x: x + size / 2 })} />;
      })}
    </svg>
    <div className="mt-2 flex items-center justify-end gap-1 text-[11px] text-muted-foreground" aria-hidden="true">
      <span className="mr-1">Ít</span>
      {HEAT_STEPS.map(step => <svg key={step} width="11" height="11" className="text-chart-1"><rect width="11" height="11" rx="2" fill="currentColor" fillOpacity={step} /></svg>)}
      <span className="ml-1">Nhiều · cao nhất {max} {unit}/ngày</span>
    </div>
    {hover && <Tooltip x={hover.x} width={width} title={formatLocalDate(hover.date)} rows={[{ label: unit, color: "text-chart-1", value: String(hover.value) }]} />}
  </div>;
}
