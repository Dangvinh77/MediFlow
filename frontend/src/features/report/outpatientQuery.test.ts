import { describe, expect, it } from "vitest";
import { buildOutpatientQuery, readOutpatientQuery, validateOutpatientQuery, type OutpatientReportQuery } from "./outpatientQuery";

const query: OutpatientReportQuery = { mode: "daily", fromDate: "2024-01-01", toDate: "2024-12-31", year: 2024 };
describe("Outpatient report query contract", () => {
  it("accepts exactly 366 inclusive days in a leap year and rejects 367", () => {
    expect(validateOutpatientQuery(query)).toEqual({});
    expect(validateOutpatientQuery({ ...query, toDate: "2025-01-01" }).toDate).toContain("366");
  });
  it.each(["2023-02-29", "1999-12-31", "2101-01-01", "2024-04-31", ""])("rejects invalid or out-of-contract date %s", (fromDate) => {
    expect(validateOutpatientQuery({ ...query, fromDate }).fromDate).toBeTruthy();
  });
  it("rejects reverse dates and malformed departments", () => {
    expect(validateOutpatientQuery({ ...query, fromDate: "2025-01-01", departmentId: "wrong" })).toMatchObject({ toDate: expect.any(String), departmentId: expect.any(String) });
  });
  it.each([1999, 2101, 2024.5, NaN])("rejects monthly year %s", (year) => {
    expect(validateOutpatientQuery({ ...query, mode: "monthly", year }).year).toBeTruthy();
  });
  it("does not validate unused daily dates in monthly mode", () => {
    expect(validateOutpatientQuery({ ...query, mode: "monthly", fromDate: "", toDate: "", year: 2000 })).toEqual({});
  });
  it("only auto-loads complete and valid deep links", () => {
    for (const value of ["", "fromDate=2024-01-01", "view=monthly", "view=monthly&year=bad", "fromDate=2024-01-01&toDate=2025-01-01"]) {
      expect(readOutpatientQuery(new URLSearchParams(value)).autoLoad).toBe(false);
    }
    expect(readOutpatientQuery(new URLSearchParams("view=monthly&year=2024")).autoLoad).toBe(true);
    expect(readOutpatientQuery(new URLSearchParams("fromDate=2024-01-01&toDate=2024-12-31")).autoLoad).toBe(true);
  });
  it("round-trips active filters, clears department and preserves the other view's filters", () => {
    const next = buildOutpatientQuery(new URLSearchParams("year=2023&departmentId=old"), query);
    expect(new URLSearchParams(next).get("departmentId")).toBeNull();
    expect(new URLSearchParams(next).get("year")).toBe("2023");
    expect(readOutpatientQuery(new URLSearchParams(next)).query).toMatchObject({ mode: "daily", fromDate: query.fromDate, toDate: query.toDate });
    const monthly = buildOutpatientQuery(new URLSearchParams(next), { ...query, mode: "monthly", year: 2024 });
    expect(readOutpatientQuery(new URLSearchParams(monthly)).query).toMatchObject({ mode: "monthly", year: 2024, fromDate: query.fromDate });
  });
});
