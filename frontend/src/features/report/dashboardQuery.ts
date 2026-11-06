/** URL names owned by the yearly dashboard; the reconciled report keeps `view`, `year` and dates. */
export const dashboardParams = ["tab", "baseYear", "compareYear", "dept"] as const;
export type DashboardTab = "overview" | "compare";

const YEARS_SHOWN = 5;

/** Newest first. Statistics are opened by calendar year, so the list is the recent years. */
export function yearOptions(currentYear: number) {
  return Array.from({ length: YEARS_SHOWN }, (_, index) => currentYear - index);
}

export function readDashboardQuery(params: URLSearchParams, currentYear: number) {
  const years = yearOptions(currentYear);
  const pick = (name: string, fallback: number) => {
    const value = Number(params.get(name));
    return years.includes(value) ? value : fallback;
  };
  const baseYear = pick("baseYear", currentYear);
  const fallbackCompare = years.find(year => year < baseYear) ?? years.find(year => year !== baseYear)!;
  const requested = pick("compareYear", fallbackCompare);
  return {
    tab: (params.get("tab") === "compare" ? "compare" : "overview") as DashboardTab,
    baseYear,
    compareYear: requested === baseYear ? fallbackCompare : requested,
    departmentId: params.get("dept") ?? "",
  };
}
