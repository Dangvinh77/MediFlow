import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api", () => ({ api: { get: mocks.get, post: mocks.post } }));

import { billingApi } from "./api";

describe("billingApi discovery", () => {
  beforeEach(() => vi.clearAllMocks());

  it("uses only the exact public adjustment and refund paths", async () => {
    await billingApi.refundsDue("rx/id");
    expect(mocks.get).toHaveBeenCalledWith("/v1/billing/prescription-adjustments/rx%2Fid/refunds-due");
    const body = { idempotencyKey: "intent", amount: 25, reason: "cancelled", paymentMethod: "CASH" as const };
    await billingApi.recordRefund("tx/id", body);
    expect(mocks.post).toHaveBeenCalledWith("/v1/billing/transactions/tx%2Fid/refunds", body);
  });

  it("sends only the four exact public source-query fields", async () => {
    mocks.get.mockResolvedValue({ paymentRequestId: "request" });
    await billingApi.discoverPaymentRequest({
      purpose: "LAB_TEST",
      sourceId: "lab-id",
      patientId: "patient-id",
      careEpisodeId: "episode-id",
    });

    expect(mocks.get).toHaveBeenCalledWith(
      "/v1/billing/payment-requests/by-source?purpose=LAB_TEST&sourceId=lab-id&patientId=patient-id&careEpisodeId=episode-id",
    );
  });
});
