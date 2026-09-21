import { api } from "@/lib/api";
import type { DailyReportDTO } from "./types";

export const reportApi = {
  daily: (date: string, departmentId?: string) => {
    const query = new URLSearchParams({ date });

    if (departmentId) {
      query.set("departmentId", departmentId);
    }

    return api.get<DailyReportDTO>(`/v1/reports/daily?${query}`);
  },
};
