import { isUuid } from "@/lib/validation";
import { isReportDate, localReportDate } from "./validation";

export type OutpatientReportMode = "daily" | "monthly";
export interface OutpatientReportQuery {
  mode: OutpatientReportMode;
  fromDate: string;
  toDate: string;
  year: number;
  departmentId?: string;
}
export type OutpatientQueryErrors = Partial<Record<"fromDate" | "toDate" | "year" | "departmentId", string>>;

export function validateOutpatientQuery(query: OutpatientReportQuery): OutpatientQueryErrors {
  const errors: OutpatientQueryErrors = {};
  if (query.mode === "daily") {
    for (const field of ["fromDate", "toDate"] as const) {
      const value = query[field];
      if (!isReportDate(value) || value < "2000-01-01" || value > "2100-12-31") {
        errors[field] = "Chọn ngày hợp lệ từ năm 2000 đến 2100.";
      }
    }
    if (!errors.fromDate && !errors.toDate) {
      if (query.fromDate > query.toDate) errors.toDate = "Đến ngày phải bằng hoặc sau từ ngày.";
      else {
        const days = (Date.parse(`${query.toDate}T00:00:00Z`) - Date.parse(`${query.fromDate}T00:00:00Z`)) / 86_400_000;
        if (days >= 366) errors.toDate = "Mỗi lần xem tối đa 366 ngày, tính cả ngày bắt đầu và kết thúc.";
      }
    }
  } else if (!Number.isInteger(query.year) || query.year < 2000 || query.year > 2100) {
    errors.year = "Năm phải là số nguyên từ 2000 đến 2100.";
  }
  if (query.departmentId && !isUuid(query.departmentId)) errors.departmentId = "Mã khoa phải là UUID hợp lệ.";
  return errors;
}

export function readOutpatientQuery(params: URLSearchParams, now = new Date()) {
  const mode = params.get("view") === "monthly" ? "monthly" : "daily";
  const query: OutpatientReportQuery = {
    mode,
    fromDate: params.get("fromDate") ?? localReportDate(now),
    toDate: params.get("toDate") ?? localReportDate(now),
    year: params.has("year") ? Number(params.get("year")) : now.getFullYear(),
    departmentId: params.get("departmentId")?.trim() || undefined,
  };
  const explicit = mode === "daily" ? params.has("fromDate") && params.has("toDate") : params.has("year");
  return { query, autoLoad: explicit && Object.keys(validateOutpatientQuery(query)).length === 0 };
}

export function buildOutpatientQuery(params: URLSearchParams, query: OutpatientReportQuery) {
  const next = new URLSearchParams(params);
  next.set("view", query.mode);
  if (query.mode === "daily") {
    next.set("fromDate", query.fromDate);
    next.set("toDate", query.toDate);
  } else next.set("year", String(query.year));
  if (query.departmentId) next.set("departmentId", query.departmentId);
  else next.delete("departmentId");
  return next.toString();
}
