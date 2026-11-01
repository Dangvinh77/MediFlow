import { expect, it } from "vitest";
import { formatScheduleObservedAt, initialScheduleDate } from "./schedule-presentation";

it("shows observation times in Vietnam with 24-hour precision across a UTC date boundary", () => {
  expect(formatScheduleObservedAt("2026-11-01T18:05:34.123456700Z")).toBe("01:05 02/11/2026 (giờ Việt Nam)");
  expect(formatScheduleObservedAt("2026-11-01T06:26:44.711143200Z")).toBe("13:26 01/11/2026 (giờ Việt Nam)");
});
it("does not echo an invalid observation timestamp", () => {
  expect(formatScheduleObservedAt("invalid")).toBe("Chưa có thời điểm cập nhật");
});
it("accepts only a real ISO calendar day from navigation", () => {
  expect(initialScheduleDate("2026-11-04")).toBe("2026-11-04");
  for (const value of [undefined,"","2026-02-30","04/11/2026","2026-11-04?departmentId=other"]) {
    expect(initialScheduleDate(value)).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(initialScheduleDate(value)).not.toBe(value);
  }
});
