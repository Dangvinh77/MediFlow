import { beforeEach, describe, expect, it, vi } from "vitest";
const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));
vi.mock("@/lib/api", () => ({ api: mocks }));
import { pharmacyApi } from "./api";

describe("Pharmacy HTTP contracts", () => {
  beforeEach(() => { mocks.get.mockReset(); mocks.post.mockReset(); mocks.put.mockReset(); });
  it("encodes drug name with backend pagination and omits blank keyword", () => {
    pharmacyApi.searchDrugs({ keyword: " A & B ", page: 2, size: 50 });
    expect(mocks.get).toHaveBeenCalledWith("/v1/pharmacy/drugs?page=2&size=50&keyword=A+%26+B");
    pharmacyApi.searchDrugs({ keyword: " " });
    expect(mocks.get).toHaveBeenLastCalledWith("/v1/pharmacy/drugs?page=0&size=20");
  });
  it("preserves the bodyless dispense and known-event replay contracts", () => {
    pharmacyApi.dispensePrescription("rx-id"); pharmacyApi.replayOutbox("event-id");
    expect(mocks.put).toHaveBeenCalledWith("/v1/pharmacy/prescriptions/rx-id/dispense");
    expect(mocks.post).toHaveBeenCalledWith("/v1/pharmacy/admin/outbox/event-id/replay");
  });
  it("keeps cancellation reason separate from dispense", () => {
    pharmacyApi.cancelPrescription("rx-id", { reason: "Theo yêu cầu" });
    expect(mocks.put).toHaveBeenCalledWith("/v1/pharmacy/prescriptions/rx-id/cancel", { reason: "Theo yêu cầu" });
  });
});
