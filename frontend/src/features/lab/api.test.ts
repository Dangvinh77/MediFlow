import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { labApi } from "./api";
import type { CreateLabRequest } from "./types";

vi.mock("client-only", () => ({}));
vi.mock("@/lib/session", () => ({ getToken: () => "test-token", clearSession: vi.fn() }));

describe("Lab care contract through Gateway", () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
  });
  afterEach(() => vi.unstubAllGlobals());

  it("sends the supplied source order and episode unchanged without inferring clearance", async () => {
    const request: CreateLabRequest = {
      recordId: "record", patientId: "patient", requestingDepartmentId: "department",
      labType: "CBC", requestedDate: "2026-10-15", sourceOrderId: "clinical-order",
      careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: "record", priceCode: "LAB-CBC",
    };
    const test = { testId: "test", status: "AWAITING_PAYMENT", paid: false, clearanceId: null };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: test }, { status: 201 }));
    expect(await labApi.create(request)).toEqual(test);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/lab");
    expect(fetchMock.mock.calls[0][1].method).toBe("POST");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(request);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("uses normal start without inventing an emergency approval", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: true, data: { status: "IN_PROGRESS" } }));
    await labApi.start("test/1");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/lab/test%2F1/start");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ emergencyOverride: null });
  });

  it("does not bypass a financial block with results or a legacy retry", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: false, data: null, error: {
      code: "LAB_CLEARANCE_REQUIRED", message: "Payment required", details: [],
    }, correlationId: "lab-trace" }, { status: 422 }));
    await expect(labApi.start("test")).rejects.toMatchObject({ status: 422, code: "LAB_CLEARANCE_REQUIRED", correlationId: "lab-trace" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("preserves an unavailable source-order authority without retrying create", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: false, data: null, error: {
      code: "LAB_SOURCE_ORDER_UNVERIFIED", message: "Clinical authority unavailable", details: [],
    }, correlationId: "authority-trace" }, { status: 503 }));
    await expect(labApi.create({
      recordId: "record", patientId: "patient", requestingDepartmentId: "department",
      labType: "CBC", requestedDate: "2026-10-15", sourceOrderId: "clinical-order",
      careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: "record", priceCode: "LAB-CBC",
    })).rejects.toMatchObject({ status: 503, code: "LAB_SOURCE_ORDER_UNVERIFIED", correlationId: "authority-trace" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
