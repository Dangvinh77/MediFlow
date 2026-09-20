const localDatePattern = /^(\d{4})-(\d{2})-(\d{2})$/;

export function formatLocalDate(value: string): string {
  const match = localDatePattern.exec(value);
  if (!match) return value || "—";
  return [match[3], match[2], match[1]].join("/");
}

export function formatInstant(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(date);
}

export function formatDecimal(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === "") return "—";
  const number = Number(value);
  if (!Number.isFinite(number)) return typeof value === "string" ? value : "—";
  return new Intl.NumberFormat("vi-VN", {
    maximumFractionDigits: 20,
  }).format(number);
}
