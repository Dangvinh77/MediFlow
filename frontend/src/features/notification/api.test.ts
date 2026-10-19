import { beforeEach, describe, expect, it, vi } from "vitest";
const transport = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api", () => ({ api: transport }));
import { notificationApi } from "./api";

describe("Notification public contracts on Support", () => {
  beforeEach(() => { transport.get.mockReset(); transport.post.mockReset(); });
  it("uses the unchanged logical Gateway paths with encoded identifiers and server pagination", () => {
    notificationApi.getById("id/with space"); notificationApi.byPatient("patient/id", 2, 20);
    expect(transport.get.mock.calls).toEqual([
      ["/v1/notifications/id%2Fwith%20space"], ["/v1/notifications/patient/patient%2Fid?page=2&size=20"],
    ]);
  });
  it("does not invent a /support prefix, idempotency, or retry commands for manual delivery", () => {
    const body = { patientId: "id", title: "Title", content: "Body", channel: "IN_APP" as const, recipientAddress: null };
    notificationApi.send(body);
    expect(transport.post).toHaveBeenCalledExactlyOnceWith("/v1/notifications/send", body);
  });
});
