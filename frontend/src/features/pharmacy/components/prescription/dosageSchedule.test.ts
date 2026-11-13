import { describe, expect, it } from "vitest";
import {
  applyDosePreset,
  applyDurationPreset,
  createDosageSchedule,
  formatDosageSchedule,
  longPrescriptionNotice,
  scheduleDays,
  suggestQuantity,
  validateDosageSchedule,
  type DosageSchedule,
  type DurationUnit,
} from "./dosageSchedule";

function schedule(duration = "", durationUnit: DurationUnit = "Ngày"): DosageSchedule {
  return { ...applyDosePreset(createDosageSchedule(), ["Sáng", "Tối"]), duration, durationUnit };
}

describe("dosage schedule", () => {
  it("writes one phrase per selected session and the course length", () => {
    expect(formatDosageSchedule(schedule("7"), "Viên")).toBe("Sáng 1 viên sau ăn; Tối 1 viên sau ăn (7 ngày)");
  });

  it("writes a course in weeks or months with its day count", () => {
    expect(formatDosageSchedule(schedule("2", "Tuần"), "viên")).toBe("Sáng 1 viên sau ăn; Tối 1 viên sau ăn (2 tuần – 14 ngày)");
    expect(formatDosageSchedule(schedule("1", "Tháng"), "viên")).toBe("Sáng 1 viên sau ăn; Tối 1 viên sau ăn (1 tháng – 30 ngày)");
  });

  it("counts a week as 7 days and a month of medicine as 30 days", () => {
    expect(scheduleDays(schedule("3", "Tuần"))).toBe(21);
    expect(scheduleDays(schedule("3", "Tháng"))).toBe(90);
    expect(scheduleDays(applyDurationPreset(schedule(), "2", "Tháng"))).toBe(60);
  });

  it("keeps a separate timing and dose per session, including half tablets", () => {
    const value = schedule();
    value.rows[0] = { ...value.rows[0], dose: 0.5, timing: "Trước ăn" };
    value.rows[3] = { ...value.rows[3], dose: 1.5, timing: "Trước khi ngủ" };
    expect(formatDosageSchedule(value, "viên")).toBe("Sáng 1/2 viên trước ăn; Tối 1,5 viên trước khi ngủ");
  });

  it("requires a session, a valid dose step and a whole duration within 90 days", () => {
    expect(validateDosageSchedule(createDosageSchedule())).toMatch(/ít nhất một buổi/);
    const dose = schedule();
    dose.rows[0] = { ...dose.rows[0], dose: 0.3 };
    expect(validateDosageSchedule(dose)).toMatch(/Liều mỗi buổi/);
    expect(validateDosageSchedule(schedule("0"))).toMatch(/Thời gian dùng/);
    expect(validateDosageSchedule(schedule("1.5"))).toMatch(/Thời gian dùng/);
    expect(validateDosageSchedule(schedule("91"))).toMatch(/tối đa 90 ngày/);
    expect(validateDosageSchedule(schedule("13", "Tuần"))).toMatch(/tối đa 90 ngày/);
    expect(validateDosageSchedule(schedule("4", "Tháng"))).toMatch(/tối đa 90 ngày/);
    expect(validateDosageSchedule(schedule("3", "Tháng"))).toBeNull();
    expect(validateDosageSchedule(schedule("7"))).toBeNull();
    expect(validateDosageSchedule(schedule())).toBeNull();
  });

  it("flags a course over 30 days as a long chronic-disease prescription without blocking it", () => {
    expect(longPrescriptionNotice(schedule("1", "Tháng"))).toBeNull();
    expect(longPrescriptionNotice(schedule("5", "Tuần"))).toMatch(/trên 30 ngày/);
    expect(longPrescriptionNotice(schedule("2", "Tháng"))).toMatch(/bệnh mạn tính/);
  });

  it("suggests dose × days rounded up, and only for per-dose units", () => {
    expect(suggestQuantity(schedule("7"), "Viên")).toBe(14);
    expect(suggestQuantity(schedule("1", "Tháng"), "viên")).toBe(60);
    const half = schedule("3");
    half.rows[0] = { ...half.rows[0], dose: 0.5 };
    expect(suggestQuantity(half, "viên")).toBe(5);
    expect(suggestQuantity(schedule("7"), "Chai")).toBeNull();
    expect(suggestQuantity(schedule(), "viên")).toBeNull();
  });

  it("leaves as-needed doses out of the suggestion", () => {
    const value = { ...applyDosePreset(createDosageSchedule(), ["Khi cần"]), duration: "5" };
    expect(suggestQuantity(value, "viên")).toBeNull();
    expect(formatDosageSchedule(value, "viên")).toBe("Khi cần 1 viên khi có triệu chứng (5 ngày)");
  });
});
