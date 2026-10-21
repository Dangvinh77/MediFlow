import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { appointmentApi } from "./api";

vi.mock("client-only", () => ({}));
vi.mock("@/lib/session", () => ({ getToken: () => "test-token", getSessionKey: () => "test-session", getRefreshToken: () => null, clearSession: vi.fn() }));

describe("appointment care commands through Gateway", () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
  });
  afterEach(() => vi.unstubAllGlobals());

  it("checks in without a body and preserves the returned payment state", async () => {
    const appointment = { appointmentId: "appointment", status: "AWAITING_PAYMENT", examClearanceId: null };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: appointment }));
    expect(await appointmentApi.checkIn("appointment/1")).toEqual(appointment);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/appointments/appointment%2F1/check-in");
    expect(init.method).toBe("PUT");
    expect(init.body).toBeUndefined();
    expect(init.headers.has("Content-Type")).toBe(false);
    expect(init.headers.get("Authorization")).toBe("Bearer test-token");
  });

  it("starts examination with an explicit normal-path body and returns an appointment", async () => {
    const appointment = { appointmentId: "appointment", status: "IN_EXAM" };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: appointment }));
    expect(await appointmentApi.startExam("appointment")).toEqual(appointment);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/appointments/appointment/start-exam");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ emergencyOverride: null });
    expect(fetchMock.mock.calls[0][1].method).toBe("PUT");
  });

  it.each([
    { status: 403, code: "FORBIDDEN" },
    { status: 422, code: "CLINICAL_EXAM_CLEARANCE_REQUIRED" },
  ])("preserves $status errors without retrying or falling back to legacy status", async ({ status, code }) => {
    fetchMock.mockResolvedValue(Response.json({ success: false, data: null, error: {
      code, message: "Examination is blocked", details: [],
    }, correlationId: "exam-trace" }, { status }));
    await expect(appointmentApi.startExam("appointment")).rejects.toMatchObject({
      status, code, correlationId: "exam-trace",
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
