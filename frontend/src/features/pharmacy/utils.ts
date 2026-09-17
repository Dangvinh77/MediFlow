import type { ApiError } from "@/lib/types";

const uuidPattern =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const vndFormatter = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
});

const dateFormatter = new Intl.DateTimeFormat("vi-VN");

const dateTimeFormatter = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "short",
});

export function isUuid(value: string): boolean {
  return uuidPattern.test(value.trim());
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
