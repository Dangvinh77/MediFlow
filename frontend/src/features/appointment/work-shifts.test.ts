import { expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { appointmentApi } from "./api";
vi.mock("@/lib/api", () => ({ api: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }));
it("creates shifts separately from explicit session capacity", async () => {
  const options = { idempotencyKey: "intent", correlationId: "trace" };
  const shift = { doctorId: "d", departmentId: "k", roomId: "r", shiftDate: "2026-11-01", startTime: "08:00", endTime: "12:00" };
  await appointmentApi.createWorkShift(shift, options);
  expect(api.post).toHaveBeenCalledWith("/v2/doctor-work-shifts", shift, options);
  const session = { doctorId: "d", departmentId: "k", roomId: "r", sessionDate: shift.shiftDate, startTime: "08:00", endTime: "09:00", breaks: [], capacity: 5 };
  await appointmentApi.createSessionFromShift("s/1", session, options);
  expect(api.post).toHaveBeenLastCalledWith("/v2/doctor-work-shifts/s%2F1/consultation-sessions", session, options);
});
