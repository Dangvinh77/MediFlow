import { isUuid } from "@/lib/validation";

export interface DailyReportQuery {
  date: string;
  departmentId?: string;
}

export interface TopMedicinesQuery {
  fromDate: string;
  toDate: string;
  limit: number;
  departmentId?: string;
}

export function localReportDate(now = new Date()): string {
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

export function isReportDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  if (year < 1 || month < 1 || month > 12 || day < 1) return false;
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  return day <= days[month - 1];
}

export function validateDailyReportQuery(query: DailyReportQuery): string | null {
  if (!query.date) return "Ngày báo cáo là bắt buộc.";
  if (!isReportDate(query.date)) return "Ngày báo cáo không hợp lệ.";
  if (query.departmentId && !isUuid(query.departmentId)) return "Mã khoa phải là UUID hợp lệ.";
  return null;
}

export function validateTopMedicinesQuery(query: TopMedicinesQuery): string | null {
  if (!isReportDate(query.fromDate) || !isReportDate(query.toDate)) return "Từ ngày và đến ngày phải là ngày hợp lệ.";
  if (query.fromDate > query.toDate) return "Ngày bắt đầu không được sau ngày kết thúc.";
  if (!Number.isInteger(query.limit) || query.limit < 1 || query.limit > 50) return "Số kết quả phải là số nguyên từ 1 đến 50.";
  if (query.departmentId && !isUuid(query.departmentId)) return "Mã khoa phải là UUID hợp lệ.";
  return null;
}

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
