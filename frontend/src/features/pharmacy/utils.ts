import type { ApiError } from "@/lib/types";
import { isUuid as validateUuid } from "@/lib/validation";

const vndFormatter = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
});

const dateFormatter = new Intl.DateTimeFormat("vi-VN");

const dateTimeFormatter = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "short",
});

export const DRUG_CATALOG_PAGE_SIZES = [10, 20, 50] as const;
export const DEFAULT_DRUG_CATALOG_PAGE_SIZE = 20;

export interface DrugCatalogQuery {
  keyword: string;
  page: number;
  size: number;
}

export interface SearchParamReader {
  get(name: string): string | null;
}

/**
 * Normalizes the catalogue query at the URL boundary.
 * The backend also clamps these values, but keeping the client rule explicit
 * makes reload/back/forward deterministic and prevents invalid requests.
 */
export function parseDrugCatalogQuery(
  params: SearchParamReader,
): DrugCatalogQuery {
  const rawPage = Number.parseInt(params.get("page") ?? "0", 10);
  const rawSize = Number.parseInt(
    params.get("size") ?? String(DEFAULT_DRUG_CATALOG_PAGE_SIZE),
    10,
  );

  const size = DRUG_CATALOG_PAGE_SIZES.includes(
    rawSize as (typeof DRUG_CATALOG_PAGE_SIZES)[number],
  )
    ? rawSize
    : DEFAULT_DRUG_CATALOG_PAGE_SIZE;

  return {
    keyword: params.get("keyword")?.trim() ?? "",
    page: Number.isFinite(rawPage) && rawPage >= 0 ? rawPage : 0,
    size,
  };
}

/** Builds the canonical query string used by catalogue controls. */
export function buildDrugCatalogQuery(query: DrugCatalogQuery): string {
  const params = new URLSearchParams({
    page: String(Math.max(0, query.page)),
    size: String(query.size),
  });

  if (query.keyword) {
    params.set("keyword", query.keyword);
  }

  return params.toString();
}

export type DrugExpiryState = "VALID" | "EXPIRING_SOON" | "EXPIRED";

/**
 * Presentation-only expiry rule: a drug is "sắp hết hạn" in the next 30 days.
 * The backend remains the source of truth for whether a dispense is allowed.
 */
export function getDrugExpiryState(
  expiryDate: string,
  todayIso = getLocalTodayIso(),
  soonDays = 30,
): DrugExpiryState {
  const expiry = new Date(`${expiryDate}T00:00:00`);
  const today = new Date(`${todayIso}T00:00:00`);

  if (
    Number.isNaN(expiry.getTime()) ||
    Number.isNaN(today.getTime()) ||
    soonDays < 0
  ) {
    return "VALID";
  }

  const soonBoundary = new Date(today);
  soonBoundary.setDate(soonBoundary.getDate() + soonDays);

  if (expiry < today) return "EXPIRED";
  if (expiry <= soonBoundary) return "EXPIRING_SOON";
  return "VALID";
}

/** Shared UUID contract used by all bounded-context lookup pages. */
export function isUuid(value: string): boolean {
  return validateUuid(value.trim());
}

export function formatVnd(value: number): string {
  return vndFormatter.format(value);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";

  const date = new Date(
    // Preserve a backend LocalDate as local calendar time instead of parsing it as UTC.
    value.length === 10
      ? `${value}T00:00:00`
      : value,
  );

  if (Number.isNaN(date.getTime())) {
    return "—";
  }

  return dateFormatter.format(date);
}

export function formatDateTime(
  value: string | null | undefined,
): string {
  if (!value) return "—";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "—";
  }

  return dateTimeFormatter.format(date);
}

export function getLocalTodayIso(): string {
  const now = new Date();

  const local = new Date(
    now.getTime() - now.getTimezoneOffset() * 60_000,
  );

  return local.toISOString().slice(0, 10);
}

export function mapFieldErrors(
  details: ApiError["details"],
): Record<string, string> {
  // Global errors stay on ApiRequestError.message; this helper maps field details only.
  return Object.fromEntries(
    details.map(({ field, message }) => [field, message]),
  );
}
