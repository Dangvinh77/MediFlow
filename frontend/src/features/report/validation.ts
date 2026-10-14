import { isUuid } from "@/lib/validation";

export interface MonthlyReportQuery {
  month: number;
  year: number;
  departmentId?: string;
}

export function validateMonthlyReportQuery(query: MonthlyReportQuery): string | null {
  if (!Number.isInteger(query.month) || query.month < 1 || query.month > 12) return "Tháng phải là số nguyên từ 1 đến 12.";
  if (!Number.isInteger(query.year) || query.year < 2000 || query.year > 2100) return "Năm phải là số nguyên từ 2000 đến 2100.";
  if (query.departmentId && !isUuid(query.departmentId)) return "Mã khoa phải là UUID hợp lệ.";
  return null;
}
