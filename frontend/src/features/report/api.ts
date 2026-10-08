import { api } from "@/lib/api";
import type { DailyReportDTO, MonthlyReportDTO, TopMedicineDTO } from "./types";

export const reportApi = {
  daily: (date: string, departmentId?: string) => {
    const query = new URLSearchParams({ date });

    if (departmentId) {
      query.set("departmentId", departmentId);
    }

    return api.get<DailyReportDTO>(`/v1/reports/daily?${query}`);
  },
  monthly: (month: number, year: number, departmentId?: string) => {
    const query = new URLSearchParams({ month: String(month), year: String(year) });
    if (departmentId) query.set("departmentId", departmentId);
    return api.get<MonthlyReportDTO>(`/v1/reports/monthly?${query}`);
  },
  topMedicines: (fromDate: string, toDate: string, limit: number, departmentId?: string) => {
    const query = new URLSearchParams({ fromDate, toDate, limit: String(limit) });
    if (departmentId) query.set("departmentId", departmentId);
    return api.get<TopMedicineDTO[]>(`/v1/reports/top-medicines?${query}`);
  },
};
