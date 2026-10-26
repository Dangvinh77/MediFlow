import { describe, it, expect } from "vitest";
import { getAppointmentStatusPresentation } from "./presentation";
describe("appointment readiness labels", () => {
  it("does not promise an exam before a paid scheduled patient arrives", () => {
    expect(getAppointmentStatusPresentation({ status: "READY_FOR_EXAM", careContractVersion: 2, checkedInAt: null }).label).toBe("Đã thanh toán · Chờ đến");
    expect(getAppointmentStatusPresentation({ status: "READY_FOR_EXAM", careContractVersion: 2, checkedInAt: "2026-10-27T01:00:00Z" }).label).toBe("Đã đến · Chờ khám");
    expect(getAppointmentStatusPresentation({ status: "READY_FOR_EXAM", careContractVersion: 1, checkedInAt: null }).label).toBe("Sẵn sàng khám");
  });
});
