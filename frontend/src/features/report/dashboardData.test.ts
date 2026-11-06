import { describe, expect, it } from "vitest";
import { calendarCells, commonCutoff, completedMonthValues, cumulativeRevenue, figuresThrough, lastFullMonthIndex, monthlyFigures, monthlyValues, percentChange, revenueOf, yearRange } from "./dashboardData";
import type { OutpatientStatisticsDTO } from "./types";

const figures = (revenue: number, visits = 1) => ({ completedVisits: visits, examRevenue: revenue, labRevenue: 0, prescriptionRevenue: 0, unclassifiedRevenue: 0, refunds: 0 });
function report(toDate: string, days: [string, number][]): OutpatientStatisticsDTO {
  return {
    fromDate: `${toDate.slice(0, 4)}-01-01`, toDate, departmentId: null, currency: "VND", zoneId: "Asia/Bangkok", reconciled: false,
    totals: figures(0), days: days.map(([date, revenue]) => ({ ...figures(revenue), date })), departments: [],
  };
}

describe("dashboard aggregation", () => {
  it("stops the running year today and keeps a closed year complete", () => {
    const now = new Date(2026, 10, 6);
    expect(yearRange(2026, now)).toEqual({ fromDate: "2026-01-01", toDate: "2026-11-06" });
    expect(yearRange(2025, now)).toEqual({ fromDate: "2025-01-01", toDate: "2025-12-31" });
  });

  it("buckets days by month and leaves months after the last reported day blank", () => {
    const running = report("2026-02-10", [["2026-01-05", 100], ["2026-01-06", 50], ["2026-02-10", 25]]);
    expect(monthlyFigures(running).map(revenueOf).slice(0, 3)).toEqual([150, 25, 0]);
    expect(monthlyValues(running, revenueOf).slice(0, 3)).toEqual([150, 25, null]);
    expect(cumulativeRevenue(running).slice(0, 3)).toEqual([150, 175, null]);
  });

  it("keeps a month still running off trend lines", () => {
    const running = report("2026-02-10", [["2026-01-05", 100], ["2026-02-10", 25]]);
    expect(completedMonthValues(running, revenueOf).slice(0, 2)).toEqual([100, null]);
    const closedMonth = report("2026-02-28", [["2026-02-10", 25]]);
    expect(completedMonthValues(closedMonth, revenueOf)[1]).toBe(25);
  });

  it("compares a running year against the same period of a closed year", () => {
    const closed = report("2025-12-31", [["2025-01-05", 100], ["2025-11-06", 10], ["2025-11-07", 999]]);
    const running = report("2026-11-06", [["2026-01-05", 120]]);
    const cutoff = commonCutoff(running, closed);
    expect(cutoff).toBe("11-06");
    expect(revenueOf(figuresThrough(closed, cutoff))).toBe(110);
    expect(revenueOf(figuresThrough(running, cutoff))).toBe(120);
  });

  it("treats a month as comparable only when the cutoff contains all of it", () => {
    expect(lastFullMonthIndex("11-06")).toBe(9);
    expect(lastFullMonthIndex("11-30")).toBe(10);
    expect(lastFullMonthIndex("12-31")).toBe(11);
    expect(lastFullMonthIndex("01-15")).toBe(-1);
  });

  it("reports no percentage against a zero base", () => {
    expect(percentChange(120, 100)).toBe(20);
    expect(percentChange(80, 100)).toBe(-20);
    expect(percentChange(5, 0)).toBeNull();
  });

  it("places calendar cells on Monday-first weeks", () => {
    // 2026-01-01 is a Thursday, so the first cell sits on weekday 3 of week 0.
    const cells = calendarCells([{ date: "2026-01-01", value: 1 }, { date: "2026-01-04", value: 2 }, { date: "2026-01-05", value: 3 }]);
    expect(cells.map(cell => [cell.week, cell.weekday])).toEqual([[0, 3], [0, 4], [0, 5]]);
  });
});
