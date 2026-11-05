import { describe, expect, it } from "vitest";
import {
  applyDosePreset,
  createDosageSchedule,
  formatDosageSchedule,
  suggestQuantity,
  validateDosageSchedule,
  type DosageSchedule,
} from "./dosageSchedule";

function schedule(days = ""): DosageSchedule {
  return { ...applyDosePreset(createDosageSchedule(), ["Sáng", "Tối"]), days };
}

describe("dosage schedule", () => {
  it("writes one phrase per selected session and the course length", () => {
    expect(formatDosageSchedule(schedule("7"), "Viên")).toBe("Sáng 1 viên sau ăn; Tối 1 viên sau ăn (7 ngày)");
  });

  it("keeps a separate timing and dose per session, including half tablets", () => {
    const value = schedule();
    value.rows[0] = { ...value.rows[0], dose: 0.5, timing: "Trước ăn" };
    value.rows[3] = { ...value.rows[3], dose: 1.5, timing: "Trước khi ngủ" };
    expect(formatDosageSchedule(value, "viên")).toBe("Sáng 1/2 viên trước ăn; Tối 1,5 viên trước khi ngủ");
  });

  it("requires a session, a valid dose step and a valid day count", () => {
    expect(validateDosageSchedule(createDosageSchedule())).toMatch(/ít nhất một buổi/);
    const dose = schedule();
    dose.rows[0] = { ...dose.rows[0], dose: 0.3 };
    expect(validateDosageSchedule(dose)).toMatch(/Liều mỗi buổi/);
    expect(validateDosageSchedule(schedule("0"))).toMatch(/Số ngày/);
    expect(validateDosageSchedule(schedule("1.5"))).toMatch(/Số ngày/);
    expect(validateDosageSchedule(schedule("7"))).toBeNull();
    expect(validateDosageSchedule(schedule())).toBeNull();
  });

  it("suggests dose × days rounded up, and only for per-dose units", () => {
    expect(suggestQuantity(schedule("7"), "Viên")).toBe(14);
    const half = schedule("3");
    half.rows[0] = { ...half.rows[0], dose: 0.5 };
    expect(suggestQuantity(half, "viên")).toBe(5);
    expect(suggestQuantity(schedule("7"), "Chai")).toBeNull();
    expect(suggestQuantity(schedule(), "viên")).toBeNull();
  });

  it("leaves as-needed doses out of the suggestion", () => {
    const value = { ...applyDosePreset(createDosageSchedule(), ["Khi cần"]), days: "5" };
    expect(suggestQuantity(value, "viên")).toBeNull();
    expect(formatDosageSchedule(value, "viên")).toBe("Khi cần 1 viên khi có triệu chứng (5 ngày)");
  });
});
