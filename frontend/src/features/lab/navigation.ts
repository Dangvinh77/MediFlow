import type { LabSearchParams } from "./api";

export function labQueueHref(filters: LabSearchParams, page = 0): string {
  const query = new URLSearchParams({ status: filters.status ?? "all" });
  if (filters.departmentId) query.set("departmentId", filters.departmentId);
  if (filters.episodeType) query.set("episodeType", filters.episodeType);
  if (filters.episodeId) query.set("episodeId", filters.episodeId);
  if (page > 0) query.set("page", String(page));
  return `/lab?${query}`;
}

export function safeLabReturn(value?: string): string {
  if (value === "/lab" || value?.startsWith("/lab?")) return value;
  return "/lab";
}
