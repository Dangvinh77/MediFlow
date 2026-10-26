import { webcrypto } from "node:crypto";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearLabPaymentRecovery, readLabPaymentRecovery, saveLabPaymentRecovery } from "./lab-payment-recovery";

describe("Lab payment recovery storage", () => {
  beforeEach(() => { vi.stubGlobal("crypto", webcrypto); sessionStorage.clear(); });

  it("preserves the full quote-bound command per signed session for an exact retry", async () => {
    const recovery = { quoteId: "quote-id", amount: "80.00", currency: "VND", paymentMethod: "BANK_TRANSFER" as const };
    await saveLabPaymentRecovery("request-id", "cashier-session", recovery);
    expect(await readLabPaymentRecovery("request-id", "cashier-session")).toEqual(recovery);
    expect(await readLabPaymentRecovery("request-id", "another-session")).toBeNull();
    await clearLabPaymentRecovery("request-id", "cashier-session");
    expect(await readLabPaymentRecovery("request-id", "cashier-session")).toBeNull();
  });

  it("fails closed when the unresolved command cannot be persisted", async () => {
    const original = sessionStorage.setItem;
    sessionStorage.setItem = () => { throw new Error("blocked"); };
    await expect(saveLabPaymentRecovery("request-id", "cashier-session", {
      quoteId: "quote-id", amount: "80.00", currency: "VND", paymentMethod: "CASH",
    })).rejects.toThrow("Chưa gửi thanh toán");
    sessionStorage.setItem = original;
  });
});
