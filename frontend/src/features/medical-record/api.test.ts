import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { medicalRecordApi } from "./api";
import type { ClinicalLabOrderDTO } from "./types";

vi.mock("client-only", () => ({}));
vi.mock("@/lib/session", () => ({ getToken: () => "test-token", clearSession: vi.fn() }));

describe("record care commands through Gateway", () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
  });
  afterEach(() => vi.unstubAllGlobals());

  it("completes with the disposition wire names and keeps the returned record", async () => {
    const request = { disposition: "OUTPATIENT_FOLLOW_UP" as const, dispositionNote: null };
    const record = { recordId: "record", status: "COMPLETED", ...request };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: record }));
    expect(await medicalRecordApi.complete("record/1", request)).toEqual(record);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/records/record%2F1/complete");
    expect(init.method).toBe("PUT");
    expect(JSON.parse(init.body)).toEqual(request);
  });

  it("receives source order identity from Clinical without generating or looking it up", async () => {
    const request = { labType: "CBC", requestedDate: "2026-10-15", priceCode: "LAB-CBC" };
    const order: ClinicalLabOrderDTO = {
      sourceOrderId: "server-order", recordId: "record", patientId: "patient",
      requestingDoctorId: "doctor", requestingDepartmentId: "department",
      careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: "record", ...request,
      orderedAt: "2026-10-15T08:00:00Z",
    };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: order }, { status: 201 }));
    expect(await medicalRecordApi.createLabOrder("record/1", request)).toEqual(order);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/records/record%2F1/lab-orders");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual(request);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("keeps field validation details and correlation ID for the form", async () => {
    const details = [{ field: "priceCode", message: "must not be blank" }];
    fetchMock.mockResolvedValue(Response.json({ success: false, data: null, error: {
      code: "VALIDATION_ERROR", message: "Invalid request", details,
    }, correlationId: "order-trace" }, { status: 400 }));
    await expect(medicalRecordApi.createLabOrder("record", {
      labType: "CBC", requestedDate: "2026-10-15", priceCode: "",
    })).rejects.toMatchObject({ status: 400, code: "VALIDATION_ERROR", details, correlationId: "order-trace" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
