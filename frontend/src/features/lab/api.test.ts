import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { labApi } from "./api";
import type { CreateLabRequest, LabCatalogItemDTO, LabTestTemplateDTO } from "./types";

vi.mock("client-only", () => ({}));
vi.mock("@/lib/session", () => ({ getToken: () => "test-token", getSessionKey: () => "test-session", getRefreshToken: () => null, clearSession: vi.fn() }));

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

  it("sends a catalogue service code with the locked name and price code", async () => {
    const request: CreateLabRequest = {
      recordId: "record", patientId: "patient", requestingDepartmentId: "department",
      labType: "Công thức máu", requestedDate: "2026-10-15", sourceOrderId: "clinical-order",
      careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: "record", priceCode: "LAB-CBC",
      serviceCode: "CBC",
    };
    const test = { testId: "test", labType: "Công thức máu", priceCode: "LAB-CBC" };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: test }, { status: 201 }));

    expect(await labApi.create(request)).toEqual(test);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(request);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toMatchObject({
      labType: "Công thức máu", priceCode: "LAB-CBC", serviceCode: "CBC",
    });
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

  it("gets the full catalog through the Gateway when no group is selected", async () => {
    const catalog: LabCatalogItemDTO[] = [{
      serviceCode: "CBC", name: "Công thức máu", displayGroup: "HEMATOLOGY",
      specimenType: "Máu toàn phần", preparation: null, turnaroundHours: 4,
      priceCode: "LAB-CBC", templateCode: "CBC_V1", panel: false,
    }];
    fetchMock.mockResolvedValue(Response.json({ success: true, data: catalog }));

    await expect(labApi.catalog()).resolves.toEqual(catalog);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/lab/catalog");
    expect(fetchMock.mock.calls[0][1].method).toBe("GET");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("filters the catalog by its requested group", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: true, data: [] }));

    await expect(labApi.catalog("HEMATOLOGY")).resolves.toEqual([]);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/lab/catalog?group=HEMATOLOGY");
  });

  it("gets the pinned result template for the encoded test ID", async () => {
    const template: LabTestTemplateDTO = {
      testId: "test/1",
      serviceCode: "CBC",
      template: {
        templateCode: "CBC_V1", version: 1, name: "Công thức máu",
        fields: [{
          fieldCode: "WBC", label: "Bạch cầu", position: 1, required: true,
          valueType: "QUANTITATIVE", unit: "10^9/L", allowedOptions: null,
          allowsOperator: true,
          ranges: [{
            sex: null, ageMinDays: null, ageMaxDays: null, low: 4, high: 10,
            criticalLow: null, criticalHigh: null, reviewed: false,
          }],
        }],
      },
    };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: template }));

    await expect(labApi.getTemplate("test/1")).resolves.toEqual(template);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/lab/test%2F1/template");
    expect(fetchMock.mock.calls[0][1].method).toBe("GET");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("preserves the disabled catalog 404 code for the caller", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: false, data: null, error: {
      code: "LAB_CATALOG_DISABLED", message: "Lab catalog is disabled", details: [],
    } }, { status: 404 }));

    await expect(labApi.catalog()).rejects.toMatchObject({
      status: 404,
      code: "LAB_CATALOG_DISABLED",
    } satisfies Partial<ApiRequestError>);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/lab/catalog");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
