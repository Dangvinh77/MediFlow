import { api } from "@/lib/api";
import type { OutpatientAvailablePeriodDTO, OutpatientReportDTO, OutpatientStatisticsDTO } from "./types";

export const reportApi = {
  availablePeriods: (departmentId?: string) => {
    const query = new URLSearchParams();
    if (departmentId) query.set("departmentId", departmentId);
    return api.get<OutpatientAvailablePeriodDTO[]>(`/v1/reports/outpatient/available-periods${query.size ? `?${query}` : ""}`);
  },
  outpatientDaily: (fromDate: string, toDate: string, departmentId?: string) => {
    const query = new URLSearchParams({ fromDate, toDate });
    if (departmentId) query.set("departmentId", departmentId);
    return api.get<OutpatientReportDTO>(`/v1/reports/outpatient/daily?${query}`);
  },
  outpatientMonthly: (year: number, departmentId?: string) => {
    const query = new URLSearchParams({ year: String(year) });
    if (departmentId) query.set("departmentId", departmentId);
    return api.get<OutpatientReportDTO>(`/v1/reports/outpatient/monthly?${query}`);
  },
  outpatientStatistics: (fromDate: string, toDate: string, departmentId?: string) => {
    const query = new URLSearchParams({ fromDate, toDate });
    if (departmentId) query.set("departmentId", departmentId);
    return api.get<OutpatientStatisticsDTO>(`/v1/reports/outpatient/statistics?${query}`);
  },
};
