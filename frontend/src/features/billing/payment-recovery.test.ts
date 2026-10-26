import { webcrypto } from "node:crypto";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearScheduledPaymentRecovery, readScheduledPaymentRecovery, saveScheduledPaymentRecovery } from "./payment-recovery";

const recovery = {
  kind: "PATIENT_PAYMENT" as const,
  command: {
    reservationId: "reservation-id", expectedReservationRevision: 4, quoteId: null, amount: "120000.00",
    currency: "VND" as const, paymentMethod: "CASH" as const, providerReference: null,
  },
  paymentOperationId: "operation-id",
};

describe("scheduled payment recovery storage", () => {
  beforeEach(() => { vi.stubGlobal("crypto", webcrypto); sessionStorage.clear(); });

  it("keeps the payment command and operation id across reloads within the same authenticated session", async () => {
    await saveScheduledPaymentRecovery("request-id", "cashier-session", recovery);
    expect(await readScheduledPaymentRecovery("request-id", "cashier-session")).toEqual(recovery);
    expect(await readScheduledPaymentRecovery("request-id", "different-session")).toBeNull();
    await clearScheduledPaymentRecovery("request-id", "cashier-session");
    expect(await readScheduledPaymentRecovery("request-id", "cashier-session")).toBeNull();
  });

  it("keeps zero-liability separate from the cash command during recovery", async () => {
    const zero = {
      kind: "ZERO_LIABILITY" as const,
      command: { reservationId: "reservation-id", expectedReservationRevision: 4, quoteId: "quote-id" },
      paymentOperationId: null,
    };
    await saveScheduledPaymentRecovery("request-id", "cashier-session", zero);
    expect(await readScheduledPaymentRecovery("request-id", "cashier-session")).toEqual(zero);
  });

  it("fails closed when pending recovery cannot be persisted", async () => {
    const original = sessionStorage.setItem;
    sessionStorage.setItem = () => { throw new Error("blocked"); };
    await expect(saveScheduledPaymentRecovery("request-id", "cashier-session", recovery)).rejects.toThrow("Chưa gửi giao dịch");
    sessionStorage.setItem = original;
  });
});
