import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { labReviewApi } from "./review-api";

vi.mock("client-only", () => ({}));
vi.mock("@/lib/session", () => ({ getToken: () => "test-token", getSessionKey: () => "test-session", getRefreshToken: () => null, clearSession: vi.fn() }));

describe("lab review API", () => {
  const fetchMock = vi.fn();
  beforeEach(() => { vi.stubGlobal("fetch", fetchMock); fetchMock.mockReset(); });
  afterEach(() => vi.unstubAllGlobals());

  it("loads the lab worklist through the gateway", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: true, data: [] }));
    await expect(labReviewApi.worklist()).resolves.toEqual([]);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/lab/worklist");
    expect(fetchMock.mock.calls[0][1].method).toBe("GET");
  });

  it("reads specimens with an encoded test identifier", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: true, data: [] }));
    await expect(labReviewApi.specimens("test/1")).resolves.toEqual([]);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/lab/test%2F1/specimens");
  });

  it("collects a specimen with the requested test links", async () => {
    const request = { specimenType: "BLOOD", additionalTestIds: ["other-test"], replacesSpecimenId: "rejected-1" };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: { specimenId: "sample-1" } }));
    await expect(labReviewApi.collectSpecimen("test/1", request)).resolves.toMatchObject({ specimenId: "sample-1" });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/lab/test%2F1/specimens");
    expect(fetchMock.mock.calls[0][1].method).toBe("POST");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(request);
  });

  it("sends accepted or rejected specimen decisions", async () => {
    const request = { decision: "REJECTED" as const, reason: "Mẫu không đủ" };
    fetchMock.mockResolvedValue(Response.json({ success: true, data: { specimenId: "sample/1" } }));
    await labReviewApi.decideSpecimen("sample/1", request);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/lab/specimens/sample%2F1/decision");
    expect(fetchMock.mock.calls[0][1].method).toBe("PUT");
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(request);
  });

  it.each(["OPEN", "ACKNOWLEDGED"] as const)("filters critical notices by %s", async (status) => {
    fetchMock.mockResolvedValue(Response.json({ success: true, data: [] }));
    await expect(labReviewApi.criticalNotices(status)).resolves.toEqual([]);
    expect(fetchMock.mock.calls[0][0]).toBe(`/api/v2/lab/critical-notices?status=${status}`);
  });

  it("acknowledges a critical notice using encoded identifiers", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: true, data: { noticeId: "notice" } }));
    await labReviewApi.acknowledgeCriticalNotice("test/1", "notice/2");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v2/lab/test%2F1/critical-notices/notice%2F2/acknowledgement");
    expect(fetchMock.mock.calls[0][1].method).toBe("POST");
  });

  it("preserves LAB_REVIEW_DISABLED for workflow fallback", async () => {
    fetchMock.mockResolvedValue(Response.json({ success: false, data: null, error: {
      code: "LAB_REVIEW_DISABLED", message: "Review disabled", details: [],
    } }, { status: 404 }));
    await expect(labReviewApi.worklist()).rejects.toMatchObject({ status: 404, code: "LAB_REVIEW_DISABLED" } satisfies Partial<ApiRequestError>);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
