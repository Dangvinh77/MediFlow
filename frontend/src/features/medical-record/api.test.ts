import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { medicalRecordApi } from "./api";
import type { ClinicalLabOrderDTO, FollowUpRecommendationDTO, RecordDocumentDTO } from "./types";

vi.mock("client-only", () => ({}));
vi.mock("@/lib/session", () => ({ getToken: () => "test-token", getSessionKey: () => "test-session", getRefreshToken: () => null, clearSession: vi.fn() }));

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

  it("creates and reads the immutable V2 follow-up recommendation through the record routes", async () => {
    const request = {
      recommendedFrom: "2026-11-01",
      recommendedThrough: "2026-11-30",
      reason: "Review symptoms after treatment.",
      preparationInstructions: null,
    };
    const recommendation: FollowUpRecommendationDTO = {
      recommendationId: "recommendation-1", sourceRecordId: "record-1", sourceRegistrationId: "registration-1",
      patientId: "patient-1", departmentId: "department-1", preferredDoctorId: "doctor-1",
      ...request, createdAt: "2026-10-26T04:00:00Z",
    };
    fetchMock.mockResolvedValueOnce(Response.json({ success: true, data: recommendation }, { status: 201 }));
    expect(await medicalRecordApi.createFollowUpRecommendation("record/1", request, {
      idempotencyKey: "follow-up-key", correlationId: "follow-up-trace",
    })).toEqual(recommendation);
    const [postUrl, postInit] = fetchMock.mock.calls[0];
    expect(postUrl).toBe("/api/v2/records/record%2F1/follow-up-recommendations");
    expect(postInit.method).toBe("POST");
    expect(JSON.parse(postInit.body)).toEqual(request);
    expect(new Headers(postInit.headers).get("Idempotency-Key")).toBe("follow-up-key");
    expect(new Headers(postInit.headers).get("X-Correlation-Id")).toBe("follow-up-trace");

    fetchMock.mockResolvedValueOnce(Response.json({ success: true, data: recommendation }));
    expect(await medicalRecordApi.getFollowUpRecommendation("record/1")).toEqual(recommendation);
    expect(fetchMock.mock.calls[1][0]).toBe("/api/v2/records/record%2F1/follow-up-recommendation");
    expect(fetchMock.mock.calls[1][1].method).toBe("GET");
    expect(fetchMock).toHaveBeenCalledTimes(2);
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

describe("Wave 4 patient record API contracts", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
  });

  afterEach(() => vi.unstubAllGlobals());

  it("loads the patient timeline with encoded filters and a paged result", async () => {
    const page = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: page }));

    expect(await medicalRecordApi.timeline("patient/one", {
      page: 0, size: 20, departmentId: "department 1", status: "COMPLETED",
    })).toEqual(page);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/patients/patient%2Fone/timeline?page=0&size=20&departmentId=department+1&status=COMPLETED");
    expect(fetchMock.mock.calls[0][1].method).toBe("GET");
  });

  it("uses the exact read paths for documents, courses, collaboration, allergies, and review queue", async () => {
    fetchMock.mockImplementation(() => Response.json({ success: true, data: [] }));
    await medicalRecordApi.documents("record/1");
    await medicalRecordApi.treatmentCourses("patient/1");
    await medicalRecordApi.handovers("record/1");
    await medicalRecordApi.consultationNotes("record/1");
    await medicalRecordApi.amendments("record/1");
    await medicalRecordApi.pendingAmendments();
    await medicalRecordApi.allergies("patient/1");

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v2/records/record%2F1/documents",
      "/api/v2/patients/patient%2F1/treatment-courses",
      "/api/v2/records/record%2F1/handovers",
      "/api/v2/records/record%2F1/consultation-notes",
      "/api/v2/records/record%2F1/amendments",
      "/api/v2/amendments/pending",
      "/api/v2/patients/patient%2F1/allergies",
    ]);
  });

  it("preserves current and previous lab revision metadata from Clinical", async () => {
    const documents: RecordDocumentDTO[] = [{
      type: "LAB",
      referenceId: "lab-result-1",
      summary: "Công thức máu",
      attachedAt: "2026-11-11T01:15:00Z",
      afterConclusion: false,
      resultVersion: 3,
      amendmentReason: "Sửa lại kết quả sau khi đối chiếu.",
      previousRevisions: [{
        resultVersion: 2,
        summary: "Công thức máu phiên bản 2",
        amendmentReason: "Nhập sai trị số bình thường.",
        sourceAt: "2026-11-10T03:00:00Z",
        label: "Đã đính chính",
      }],
    }];
    fetchMock.mockResolvedValue(Response.json({ success: true, data: documents }));

    expect(await medicalRecordApi.documents("record/1")).toEqual(documents);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/records/record%2F1/documents");
    expect(fetchMock.mock.calls[0][1].method).toBe("GET");
  });

  it("sends every new write with a stable idempotency key and correlation ID", async () => {
    fetchMock.mockImplementation(() => Response.json({ success: true, data: { ok: true } }, { status: 201 }));
    const options = { idempotencyKey: "intent-123", correlationId: "trace-123" };
    const commands: Array<() => Promise<unknown>> = [
      () => medicalRecordApi.update("record/1", { symptoms: "Ho", chiefComplaint: "Đau đầu" }, options),
      () => medicalRecordApi.openTreatmentCourse({ patientId: "patient", problem: "Đau đầu", openingReason: "Theo dõi", recordId: "record" }, options),
      () => medicalRecordApi.addCourseVisit("course/1", { recordId: "record/2" }, options),
      () => medicalRecordApi.closeTreatmentCourse("course/1", { decision: "RESOLVED", note: "Ổn định" }, options),
      () => medicalRecordApi.requestHandover("record/1", { toDoctorId: "doctor-2", reason: "Đổi ca", pendingItems: "Theo dõi" }, options),
      () => medicalRecordApi.acceptHandover("record/1", "handover/1", options),
      () => medicalRecordApi.addConsultationNote("record/1", { opinion: "Ý kiến" }, options),
      () => medicalRecordApi.requestAmendment("record/1", { section: "SYMPTOMS", amendedText: "Sửa", reason: "Nhập sai" }, options),
      () => medicalRecordApi.decideAmendment("record/1", "amendment/1", { decision: "APPROVED", note: "Đồng ý" }, options),
      () => medicalRecordApi.assignAmendment("record/1", { assigneeStaffId: "staff-2", reason: "Bàn giao" }, options),
      () => medicalRecordApi.addAllergy("patient/1", { substance: "Penicillin", reaction: "Phát ban", severity: "MODERATE", sourceRecordId: "record/1" }, options),
      () => medicalRecordApi.changeAllergyStatus("patient/1", "allergy/1", { status: "ENTERED_IN_ERROR", reason: "Ghi nhầm" }, options),
    ];

    for (const command of commands) await command();

    expect(fetchMock).toHaveBeenCalledTimes(commands.length);
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/records/record%2F1",
      "/api/v2/treatment-courses",
      "/api/v2/treatment-courses/course%2F1/visits",
      "/api/v2/treatment-courses/course%2F1/closure",
      "/api/v2/records/record%2F1/handovers",
      "/api/v2/records/record%2F1/handovers/handover%2F1/acceptance",
      "/api/v2/records/record%2F1/consultation-notes",
      "/api/v2/records/record%2F1/amendments",
      "/api/v2/records/record%2F1/amendments/amendment%2F1/decision",
      "/api/v2/records/record%2F1/amendment-assignments",
      "/api/v2/patients/patient%2F1/allergies",
      "/api/v2/patients/patient%2F1/allergies/allergy%2F1/status",
    ]);
    expect(fetchMock.mock.calls.map(([, init]) => init.method)).toEqual([
      "PUT", "POST", "POST", "PUT", "POST", "PUT", "POST", "POST", "PUT", "POST", "POST", "PUT",
    ]);
    for (const [, init] of fetchMock.mock.calls) {
      const headers = new Headers(init.headers);
      expect(headers.get("Idempotency-Key")).toBe(options.idempotencyKey);
      expect(headers.get("X-Correlation-Id")).toBe(options.correlationId);
      expect(["POST", "PUT"]).toContain(init.method);
    }
    expect(fetchMock.mock.calls[5][1].body).toBeUndefined();
  });

  it("preserves null query filters and omits them from the timeline URL", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: true, data: { content: [], totalElements: 0, totalPages: 0, number: 1, size: 10 } }));
    await medicalRecordApi.timeline("patient-1", { page: 1, size: 10, departmentId: null, status: null });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/patients/patient-1/timeline?page=1&size=10");
  });
});
