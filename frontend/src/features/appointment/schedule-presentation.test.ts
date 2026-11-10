import { expect, it } from "vitest";
import { formatScheduleObservedAt, initialScheduleDate, sessionAvailability } from "./schedule-presentation";

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
it("distinguishes full, not open and load error", () => {
  expect(sessionAvailability({ status: "OPEN", availableCount: 2 }, null)).toBe("AVAILABLE");
  expect(sessionAvailability({ status: "OPEN", availableCount: 0 }, null)).toBe("FULL");
  expect(sessionAvailability({ status: "STOPPED", availableCount: 2 }, null)).toBe("NOT_OPEN");
  expect(sessionAvailability({ status: "CANCELLED", availableCount: 0 }, null)).toBe("NOT_OPEN");
  expect(sessionAvailability(null, null)).toBe("NOT_OPEN");
  expect(sessionAvailability(null, new Error("offline"))).toBe("LOAD_ERROR");
  expect(sessionAvailability({ status: "OPEN", availableCount: 2 }, new Error("stale"))).toBe("LOAD_ERROR");
});
