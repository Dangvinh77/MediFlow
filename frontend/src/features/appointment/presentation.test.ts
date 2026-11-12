import { describe, it, expect } from "vitest";
import { getAppointmentStatusPresentation, queueNumberLabel, registrationOutcomeLabel } from "./presentation";
describe("appointment readiness labels", () => {
  it("does not promise an exam before a paid scheduled patient arrives", () => {
    expect(getAppointmentStatusPresentation({ status: "READY_FOR_EXAM", careContractVersion: 2, checkedInAt: null }).label).toBe("Đã thanh toán · Chờ đến");
    expect(getAppointmentStatusPresentation({ status: "READY_FOR_EXAM", careContractVersion: 2, checkedInAt: "2026-10-27T01:00:00Z" }).label).toBe("Đã đến · Chờ khám");
    expect(getAppointmentStatusPresentation({ status: "READY_FOR_EXAM", careContractVersion: 1, checkedInAt: null }).label).toBe("Sẵn sàng khám");
  });
});
describe("registration outcome labels", () => {
  it("labels cancelled, expired, absent and unresolved payment differently", () => {
    const labels = [
      registrationOutcomeLabel({ status: "CANCELLED", checkedInAt: null }),
      registrationOutcomeLabel({ status: "EXPIRED", checkedInAt: null }),
      registrationOutcomeLabel({ status: "CONFIRMED", checkedInAt: "2026-11-10T01:00:00Z", stage: "ABSENT" }),
      registrationOutcomeLabel({ status: "RECONCILIATION_REQUIRED", checkedInAt: null }),
    ];
    expect(labels).toEqual(["Đã hủy", "Hết hạn giữ chỗ", "Vắng mặt", "Cần đối soát thanh toán"]);
    expect(new Set(labels).size).toBe(4);
  });
  it("prebooked paid registration is not shown as arrived", () => {
    expect(registrationOutcomeLabel({ status: "CONFIRMED", checkedInAt: null })).toBe("Đã thanh toán · Chờ đến");
    expect(registrationOutcomeLabel({ status: "CONFIRMED", checkedInAt: "2026-11-10T01:00:00Z" })).toBe("Đã đến · Chờ khám");
  });
  it("names the counter number only when clinic intake is on", () => {
    expect(queueNumberLabel(true)).toBe("Số quầy");
    expect(queueNumberLabel(false)).toBe("Số thứ tự");
  });
});
