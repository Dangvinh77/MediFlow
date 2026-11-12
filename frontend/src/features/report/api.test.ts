import { beforeEach, describe, expect, it, vi } from "vitest";
const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api", () => ({ api: { get } }));
import { reportApi } from "./api";

describe("Report outpatient query contracts", () => {
  beforeEach(() => get.mockReset());
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
