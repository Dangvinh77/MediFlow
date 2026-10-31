export function vietnamToday(now = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

export function sessionDateErrors(fromDate: string, toDate: string): Partial<Record<"fromDate" | "toDate", string>> {
  const errors: Partial<Record<"fromDate" | "toDate", string>> = {};
  for (const [field, value] of [["fromDate", fromDate], ["toDate", toDate]] as const) {
    if (!value) errors[field] = "Vui lòng chọn ngày bắt buộc.";
    else if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !Number.isFinite(Date.parse(`${value}T00:00:00Z`)) || new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) !== value) errors[field] = "Ngày không hợp lệ.";
  }
  if (!Object.keys(errors).length) {
    if (fromDate > toDate) errors.toDate = "Đến ngày phải bằng hoặc sau Từ ngày.";
    else if ((Date.parse(toDate) - Date.parse(fromDate)) / 86400000 > 30) errors.toDate = "Khoảng tìm kiếm tối đa 30 ngày giữa hai mốc.";
  }
  return errors;
}
