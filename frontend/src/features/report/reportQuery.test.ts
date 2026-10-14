import { describe, expect, it } from "vitest";
import { buildMonthlyReportQuery, getReportView, readMonthlyReportQuery } from "./reportQuery";
import { validateMonthlyReportQuery } from "./validation";

describe("Report query boundaries", () => {
  it("defaults unknown views to monthly without inventing another report", () => {
    expect(getReportView(null)).toBe("monthly"); expect(getReportView("finance")).toBe("monthly"); expect(getReportView("daily")).toBe("daily");
  });
  it.each([
    { month: 0, year: 2026 }, { month: 13, year: 2026 }, { month: 1.5, year: 2026 },
    { month: 1, year: 1999 }, { month: 1, year: 2101 }, { month: 1, year: NaN },
    { month: 1, year: 2026, departmentId: "invalid" },
  ])("rejects invalid monthly input %j", (query) => { expect(validateMonthlyReportQuery(query)).not.toBeNull(); });
  it("only auto-loads a valid explicit monthly URL and preserves invalid department for correction", () => {
    const invalid = readMonthlyReportQuery(new URLSearchParams("month=10&year=2026&departmentId=wrong"));
    expect(invalid.autoLoad).toBe(false); expect(invalid.query.departmentId).toBe("wrong");
    expect(readMonthlyReportQuery(new URLSearchParams("month=2&year=2024")).autoLoad).toBe(true);
    expect(readMonthlyReportQuery(new URLSearchParams()).autoLoad).toBe(false);
  });
  it("clears optional scope rather than reusing a stale department", () => {
    const next = new URLSearchParams(buildMonthlyReportQuery(new URLSearchParams("view=daily&departmentId=old&date=2026-10-12"), { month: 10, year: 2026 }));
    expect(next.get("departmentId")).toBeNull(); expect(next.get("view")).toBe("monthly"); expect(next.get("date")).toBe("2026-10-12");
  });
});
