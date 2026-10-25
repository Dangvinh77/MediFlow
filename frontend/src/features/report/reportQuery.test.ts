import { describe, expect, it, vi } from "vitest";
import { buildDailyReportQuery, buildMonthlyReportQuery, buildTopMedicinesQuery, getReportView, readDailyReportQuery, readMonthlyReportQuery, readTopMedicinesQuery } from "./reportQuery";
import { isReportDate, localReportDate, validateMonthlyReportQuery, validateTopMedicinesQuery } from "./validation";

describe("Report query boundaries", () => {
  it("defaults unknown views to monthly without inventing another report", () => {
    expect(getReportView(null)).toBe("monthly"); expect(getReportView("finance")).toBe("monthly"); expect(getReportView("daily")).toBe("daily");
  });
  it.each([
    { month: 0, year: 2026 }, { month: 13, year: 2026 }, { month: 1.5, year: 2026 },
    { month: 1, year: 1999 }, { month: 1, year: 2101 }, { month: 1, year: NaN },
    { month: 1, year: 2026, departmentId: "invalid" },
  ])("rejects invalid monthly input %j", (query) => { expect(validateMonthlyReportQuery(query)).not.toBeNull(); });
  it("auto-loads the current month and valid explicit URLs, preserving invalid scope for correction", () => {
    const invalid = readMonthlyReportQuery(new URLSearchParams("month=10&year=2026&departmentId=wrong"));
    expect(invalid.autoLoad).toBe(false); expect(invalid.query.departmentId).toBe("wrong");
    expect(readMonthlyReportQuery(new URLSearchParams("month=2&year=2024")).autoLoad).toBe(true);
    expect(readMonthlyReportQuery(new URLSearchParams()).autoLoad).toBe(true);
  });
  it("clears optional scope rather than reusing a stale department", () => {
    const next = new URLSearchParams(buildMonthlyReportQuery(new URLSearchParams("view=daily&departmentId=old&date=2026-10-12"), { month: 10, year: 2026 }));
    expect(next.get("departmentId")).toBeNull(); expect(next.get("view")).toBe("monthly"); expect(next.get("date")).toBe("2026-10-12");
  });
  it.each(["2023-02-29", "1900-02-29", "2024-04-31", "2024-00-10", "2024-13-01", "2024-01-00", "0000-01-01", "2024-1-1", "2024-02-29T00:00:00Z", ""])("rejects calendar-invalid date %s", (date) => {
    expect(isReportDate(date)).toBe(false);
    expect(readDailyReportQuery(new URLSearchParams({ date })).autoLoad).toBe(false);
  });
  it.each(["2024-02-29", "2000-02-29", "2024-12-31", "0001-01-01"])("accepts calendar-valid date %s", (date) => {
    expect(isReportDate(date)).toBe(true);
    expect(readDailyReportQuery(new URLSearchParams({ date })).autoLoad).toBe(true);
  });
  it("uses local calendar fields near midnight rather than an ISO UTC date", () => {
    const now = new Date(2026, 9, 16, 0, 5);
    vi.spyOn(now, "toISOString").mockImplementation(() => { throw new Error("UTC conversion is forbidden"); });
    expect(localReportDate(now)).toBe("2026-10-16");
    const parsed = readTopMedicinesQuery(new URLSearchParams(), now);
    expect(parsed.query).toEqual({ fromDate: "2026-10-16", toDate: "2026-10-16", limit: 10, departmentId: undefined });
    expect(parsed.autoLoad).toBe(true);
    expect(readDailyReportQuery(new URLSearchParams(), now)).toEqual({ query: {
      date: "2026-10-16", departmentId: undefined,
    }, autoLoad: true });
  });
  it("preserves each view's submitted dates and clears an optional department", () => {
    const daily = buildDailyReportQuery(new URLSearchParams("view=monthly&month=2&year=2024&departmentId=old"), { date: "2024-02-29" });
    expect(readDailyReportQuery(new URLSearchParams(daily))).toEqual({ query: { date: "2024-02-29", departmentId: undefined }, autoLoad: true });
    expect(new URLSearchParams(daily).get("month")).toBe("2");
    const top = buildTopMedicinesQuery(new URLSearchParams(daily), { fromDate: "2024-02-01", toDate: "2024-02-29", limit: 50 });
    expect(new URLSearchParams(top).get("date")).toBe("2024-02-29");
    expect(readTopMedicinesQuery(new URLSearchParams(top)).autoLoad).toBe(true);
  });
  it.each(["limit=0", "limit=51", "limit=1.5", "limit=", "departmentId=wrong", "toDate=2023-02-29"])("does not auto-load malformed ranking filters %s", (suffix) => {
    const params = new URLSearchParams("fromDate=2024-02-01&toDate=2024-02-29");
    new URLSearchParams(suffix).forEach((value, key) => params.set(key, value));
    expect(readTopMedicinesQuery(params).autoLoad).toBe(false);
  });
  it("accepts same-day inclusive ranking bounds and both limit boundaries", () => {
    for (const limit of [1, 50]) expect(validateTopMedicinesQuery({ fromDate: "2024-02-29", toDate: "2024-02-29", limit })).toBeNull();
  });
});
