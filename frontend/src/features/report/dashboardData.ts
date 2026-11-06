import { localReportDate } from "./validation";
import type { OutpatientStatisticsDTO, OutpatientStatisticsFigures } from "./types";

export const MONTH_LABELS = Array.from({ length: 12 }, (_, index) => `T${index + 1}`);

const emptyFigures = (): OutpatientStatisticsFigures => ({
  completedVisits: 0, examRevenue: 0, labRevenue: 0, prescriptionRevenue: 0, unclassifiedRevenue: 0, refunds: 0,
});

export function addFigures(total: OutpatientStatisticsFigures, row: OutpatientStatisticsFigures) {
  return {
    completedVisits: total.completedVisits + row.completedVisits,
    examRevenue: total.examRevenue + row.examRevenue,
    labRevenue: total.labRevenue + row.labRevenue,
    prescriptionRevenue: total.prescriptionRevenue + row.prescriptionRevenue,
    unclassifiedRevenue: total.unclassifiedRevenue + row.unclassifiedRevenue,
    refunds: total.refunds + row.refunds,
  };
}

/** Gross cash received; refunds are reported beside it, not netted here. */
export function revenueOf(row: OutpatientStatisticsFigures) {
  return row.examRevenue + row.labRevenue + row.prescriptionRevenue + row.unclassifiedRevenue;
}

export function averagePerVisit(row: OutpatientStatisticsFigures) {
  return row.completedVisits > 0 ? revenueOf(row) / row.completedVisits : 0;
}

export function hasActivity(row: OutpatientStatisticsFigures) {
  return row.completedVisits > 0 || revenueOf(row) > 0 || row.refunds > 0;
}

/** A closed year runs to 31/12; the running year stops today so future days are never charted. */
export function yearRange(year: number, now: Date) {
  const lastDay = `${year}-12-31`;
  const today = localReportDate(now);
  return { fromDate: `${year}-01-01`, toDate: today < lastDay ? today : lastDay };
}

/** Twelve buckets by calendar month; `report.days` is already complete and sorted per day. */
export function monthlyFigures(report: OutpatientStatisticsDTO) {
  const months = Array.from({ length: 12 }, emptyFigures);
  for (const day of report.days) {
    const month = Number(day.date.slice(5, 7)) - 1;
    months[month] = addFigures(months[month], day);
  }
  return months;
}

/** Month index of the last reported day: later months are blank, not zero. */
export function lastReportedMonth(report: OutpatientStatisticsDTO) {
  return Number(report.toDate.slice(5, 7)) - 1;
}

export function monthlyValues(report: OutpatientStatisticsDTO, value: (row: OutpatientStatisticsFigures) => number) {
  const last = lastReportedMonth(report);
  return monthlyFigures(report).map((row, index) => (index <= last ? value(row) : null));
}

/** For trend lines: a month still running would read as a collapse, so it stays off the line. */
export function completedMonthValues(report: OutpatientStatisticsDTO, value: (row: OutpatientStatisticsFigures) => number) {
  const last = lastFullMonthIndex(report.toDate.slice(5));
  return monthlyValues(report, value).map((item, index) => (index <= last ? item : null));
}

export function cumulativeRevenue(report: OutpatientStatisticsDTO) {
  let running = 0;
  return monthlyValues(report, revenueOf).map(value => {
    if (value === null) return null;
    running += value;
    return running;
  });
}

/** The earlier "MM-DD" two reports both reach, so a running year compares like for like. */
export function commonCutoff(first: OutpatientStatisticsDTO, second: OutpatientStatisticsDTO) {
  const firstEnd = first.toDate.slice(5);
  const secondEnd = second.toDate.slice(5);
  return firstEnd < secondEnd ? firstEnd : secondEnd;
}

export function figuresThrough(report: OutpatientStatisticsDTO, cutoff: string) {
  return report.days.filter(day => day.date.slice(5) <= cutoff).reduce<OutpatientStatisticsFigures>(addFigures, emptyFigures());
}

/** Index of the last month a "MM-DD" cutoff fully contains; a part month must not read as a drop. */
export function lastFullMonthIndex(cutoff: string) {
  const month = Number(cutoff.slice(0, 2));
  // 2025 is not a leap year; a "02-29" cutoff still lands on the closed-month branch.
  const lastDay = new Date(Date.UTC(2025, month, 0)).getUTCDate();
  return Number(cutoff.slice(3)) >= lastDay ? month - 1 : month - 2;
}

/** Null when the base is zero: a percentage against nothing would be meaningless. */
export function percentChange(current: number, previous: number) {
  return previous === 0 ? null : ((current - previous) / Math.abs(previous)) * 100;
}

/** Monday-first week grid for the calendar heatmap, one column per week of the year. */
export function calendarCells(days: { date: string; value: number }[]) {
  if (!days.length) return [];
  const firstDay = new Date(`${days[0].date}T00:00:00Z`).getUTCDay();
  const offset = (firstDay + 6) % 7;
  return days.map((day, index) => {
    const position = index + offset;
    return { ...day, week: Math.floor(position / 7), weekday: position % 7 };
  });
}
