import {
  localReportDate, validateDailyReportQuery, validateMonthlyReportQuery, validateTopMedicinesQuery,
  type DailyReportQuery, type MonthlyReportQuery, type TopMedicinesQuery,
} from "./validation";

export type ReportView = "daily" | "monthly" | "medicines";
export const reportViews: Record<ReportView, { title: string; label: string }> = {
  daily: { title: "Báo cáo ngày", label: "Hàng ngày" },
  monthly: { title: "Báo cáo tháng", label: "Hàng tháng" },
  medicines: { title: "Thuốc xuất nhiều", label: "Thuốc xuất nhiều" },
};

export function getReportView(raw: string | null): ReportView {
  return raw === "daily" || raw === "medicines" ? raw : "monthly";
}

export function readMonthlyReportQuery(params: URLSearchParams, now = new Date()) {
  const rawMonth = params.get("month");
  const rawYear = params.get("year");
  const query: MonthlyReportQuery = {
    month: rawMonth === null ? now.getMonth() + 1 : Number(rawMonth),
    year: rawYear === null ? now.getFullYear() : Number(rawYear),
    departmentId: params.get("departmentId")?.trim() || undefined,
  };
  return { query, autoLoad: validateMonthlyReportQuery(query) === null };
}

export function buildMonthlyReportQuery(params: URLSearchParams, query: MonthlyReportQuery) {
  const next = new URLSearchParams(params);
  next.set("view", "monthly");
  next.set("month", String(query.month));
  next.set("year", String(query.year));
  if (query.departmentId) next.set("departmentId", query.departmentId);
  else next.delete("departmentId");
  return next.toString();
}

export function readDailyReportQuery(params: URLSearchParams, now = new Date()) {
  const query: DailyReportQuery = {
    date: params.get("date") ?? localReportDate(now),
    departmentId: params.get("departmentId")?.trim() || undefined,
  };
  return { query, autoLoad: validateDailyReportQuery(query) === null };
}

export function buildDailyReportQuery(params: URLSearchParams, query: DailyReportQuery) {
  const next = new URLSearchParams(params);
  next.set("view", "daily");
  next.set("date", query.date);
  if (query.departmentId) next.set("departmentId", query.departmentId);
  else next.delete("departmentId");
  return next.toString();
}

export function readTopMedicinesQuery(params: URLSearchParams, now = new Date()) {
  const query: TopMedicinesQuery = {
    fromDate: params.get("fromDate") ?? localReportDate(now),
    toDate: params.get("toDate") ?? localReportDate(now),
    limit: params.has("limit") ? Number(params.get("limit")) : 10,
    departmentId: params.get("departmentId")?.trim() || undefined,
  };
  return { query, autoLoad: validateTopMedicinesQuery(query) === null };
}

export function buildTopMedicinesQuery(params: URLSearchParams, query: TopMedicinesQuery) {
  const next = new URLSearchParams(params);
  next.set("view", "medicines");
  next.set("fromDate", query.fromDate);
  next.set("toDate", query.toDate);
  next.set("limit", String(query.limit));
  if (query.departmentId) next.set("departmentId", query.departmentId);
  else next.delete("departmentId");
  return next.toString();
}
