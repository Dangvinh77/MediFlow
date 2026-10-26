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

  it("uses the exact C06 session, own-calendar and registration routes", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: true, data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 } }));
    await appointmentApi.listConsultationSessions({ departmentId: "dept/id", fromDate: "2026-10-26", toDate: "2026-10-27", page: 1, size: 10 });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/consultation-sessions?departmentId=dept%2Fid&fromDate=2026-10-26&toDate=2026-10-27&page=1&size=10");

    fetchMock.mockResolvedValue(Response.json({ success: true, data: { doctorId: "self", fromDate: "2026-10-26", toDate: "2026-11-01", timezone: "Asia/Ho_Chi_Minh", sessions: [] } }));
    await appointmentApi.myCalendar({ weekStart: "2026-10-26" });
    expect(fetchMock.mock.calls[1][0]).toBe("/api/v2/consultation-sessions/mine?weekStart=2026-10-26");

    fetchMock.mockResolvedValue(Response.json({ success: true, data: { registrationId: "reg" } }));
    await appointmentApi.registration("reg/id");
    expect(fetchMock.mock.calls[2][0]).toBe("/api/v2/outpatient-registrations/reg%2Fid");
    expect(fetchMock.mock.calls[3]).toBeUndefined();
  });

  it("lists only the signed-in patient's registrations without accepting a patient filter", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: true, data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 } }));
    await appointmentApi.myRegistrations({ page: 2, size: 10 });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/outpatient-registrations/mine?page=2&size=10");
  });

  it("lists only completed V2 source registrations through the staff follow-up picker route", async () => {
    const page = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: page }));
    await appointmentApi.followUpSources({ patientId: "patient/id", page: 2, size: 10 });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/outpatient-registrations/follow-up-sources?patientId=patient%2Fid&page=2&size=10");
  });

  it("sends V2 registration and session mutations with explicit idempotency and correlation headers", async () => {
    const key = "10000000-0000-4000-8000-000000000001";
    const options = { idempotencyKey: key, correlationId: "c06-reception-intent" };
    const body = {
      patientId: "patient", sessionId: "session", expectedSessionRevision: 3,
      receptionMode: "AT_COUNTER" as const, visitType: "NEW_VISIT" as const,
      previousRegistrationId: null, followUpRecommendationId: null, replacementReason: null,
      patientAgreedToDoctorReplacement: null, reason: null,
    };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: { registrationId: "registration" } }));
    await appointmentApi.createRegistration(body, options);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/outpatient-registrations");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(body);
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get("Idempotency-Key")).toBe(key);
    expect(new Headers(fetchMock.mock.calls[0][1].headers).get("X-Correlation-Id")).toBe(options.correlationId);
  });
});
