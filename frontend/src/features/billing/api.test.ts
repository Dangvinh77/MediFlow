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

  it("uses the exact scheduled-payment V2 routes and typed payment body", async () => {
    const request = { paymentRequestId: "request" };
    const operation = { operation: { paymentOperationId: "operation", status: "PREPARING" } };
    mocks.get.mockResolvedValueOnce(request).mockResolvedValueOnce(operation);
    await billingApi.scheduledPaymentRequestByRegistration("registration/id");
    await billingApi.scheduledPaymentOperation("operation/id");
    expect(mocks.get).toHaveBeenNthCalledWith(1, "/v2/billing/payment-requests/by-registration?registrationId=registration%2Fid");
    expect(mocks.get).toHaveBeenNthCalledWith(2, "/v2/billing/payment-operations/operation%2Fid");

    const body = {
      reservationId: "reservation", expectedReservationRevision: 4,
      quoteId: null, amount: "100000.00", currency: "VND" as const, paymentMethod: "CASH" as const,
      providerReference: null,
    };
    await billingApi.payScheduledRequest("request/id", body, {
      idempotencyKey: "10000000-0000-4000-8000-000000000001",
      correlationId: "c06-cashier-intent",
    });
    expect(mocks.post).toHaveBeenCalledWith(
      "/v2/billing/payment-requests/request%2Fid/payments",
      { ...body, quoteId: null },
      { idempotencyKey: "10000000-0000-4000-8000-000000000001", correlationId: "c06-cashier-intent" },
    );
  });

  it("reads the outpatient quote and submits zero-liability commands to their separate exact routes", async () => {
    const quote = { quoteId: "quote-id", paymentRequestId: "request/id", patientPayable: "0.00" };
    const zeroResult = { operation: { basis: "ZERO_LIABILITY", quoteId: "quote-id" }, payment: null, clearanceId: "clearance-id" };
    const labZeroResult = {
      eligibilityOperationId: "lab-zero-id", paymentRequestId: "request/id", quoteId: "quote-id",
      basis: "ZERO_LIABILITY", clearanceId: "lab-clearance-id", completedAt: "2026-10-26T04:00:00Z",
    };
    mocks.get.mockResolvedValueOnce(quote);
    mocks.post.mockResolvedValueOnce(zeroResult).mockResolvedValueOnce(labZeroResult);

    await billingApi.outpatientQuote("request/id");
    await billingApi.confirmScheduledZeroLiability("request/id", {
      reservationId: "reservation-id", expectedReservationRevision: 8, quoteId: "quote-id",
    }, { idempotencyKey: "zero-key", correlationId: "zero-correlation" });
    await billingApi.confirmLabZeroLiability("request/id", { quoteId: "quote-id" }, {
      idempotencyKey: "lab-zero-key", correlationId: "lab-zero-correlation",
    });

    expect(mocks.get).toHaveBeenCalledWith("/v2/billing/payment-requests/request%2Fid/quote");
    expect(mocks.post).toHaveBeenNthCalledWith(1,
      "/v2/billing/payment-requests/request%2Fid/zero-payable-eligibility",
      { reservationId: "reservation-id", expectedReservationRevision: 8, quoteId: "quote-id" },
      { idempotencyKey: "zero-key", correlationId: "zero-correlation" },
    );
    expect(mocks.post).toHaveBeenNthCalledWith(2,
      "/v2/billing/payment-requests/request%2Fid/lab-zero-payable-eligibility",
      { quoteId: "quote-id" },
      { idempotencyKey: "lab-zero-key", correlationId: "lab-zero-correlation" },
    );
  });

  it("submits positive LAB quote payments through the exact V2 route with standard command headers", async () => {
    const body = { quoteId: "lab-quote", amount: "80.00", currency: "VND", paymentMethod: "BANK_TRANSFER" } as const;
    const receipt = { transactionId: "transaction", paymentRequestId: "request/id", status: "COMPLETED" };
    mocks.post.mockResolvedValue(receipt);

    await billingApi.createLabPayment("request/id", body, { idempotencyKey: "lab-pay-key", correlationId: "lab-pay-correlation" });

    expect(mocks.post).toHaveBeenCalledWith(
      "/v2/billing/payment-requests/request%2Fid/lab-payments",
      body,
      { idempotencyKey: "lab-pay-key", correlationId: "lab-pay-correlation" },
    );
  });
});
