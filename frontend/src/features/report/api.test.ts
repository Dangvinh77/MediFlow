import { beforeEach, describe, expect, it, vi } from "vitest";
const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api", () => ({ api: { get } }));
import { reportApi } from "./api";

describe("Report legacy query contracts", () => {
  beforeEach(() => get.mockReset());
  it("uses the monthly wire query and optional department without another /api prefix", () => {
    reportApi.monthly(2, 2024, "department-id");
    expect(get).toHaveBeenCalledWith("/v1/reports/monthly?month=2&year=2024&departmentId=department-id");
  });
  it("keeps daily and ranking query names distinct", () => {
    reportApi.daily("2026-10-12");
    expect(get).toHaveBeenLastCalledWith("/v1/reports/daily?date=2026-10-12");
    reportApi.topMedicines("2026-10-01", "2026-10-12", 10);
    expect(get).toHaveBeenLastCalledWith("/v1/reports/top-medicines?fromDate=2026-10-01&toDate=2026-10-12&limit=10");
  });
  it("uses the outpatient range endpoint instead of the legacy single date endpoint", () => {
    reportApi.outpatientDaily("2024-02-01", "2024-02-29", "department-id");
    expect(get).toHaveBeenLastCalledWith("/v1/reports/outpatient/daily?fromDate=2024-02-01&toDate=2024-02-29&departmentId=department-id");
  });
  it("requests outpatient monthly periods by year only and omits absent department", () => {
    reportApi.outpatientMonthly(2024);
    expect(get).toHaveBeenLastCalledWith("/v1/reports/outpatient/monthly?year=2024");
    reportApi.outpatientMonthly(2024, "department-id");
    expect(get).toHaveBeenLastCalledWith("/v1/reports/outpatient/monthly?year=2024&departmentId=department-id");
  });
});
