import { webcrypto } from "node:crypto";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearLabZeroRecovery, readLabZeroRecovery, saveLabZeroRecovery } from "./lab-zero-recovery";

describe("Lab zero-liability recovery storage", () => {
  beforeEach(() => { vi.stubGlobal("crypto", webcrypto); sessionStorage.clear(); });

  it("persists the exact quote needed to replay an uncertain clearance", async () => {
    const recovery = { quoteId: "quote-id" };
    await saveLabZeroRecovery("request-id", "cashier-session", recovery);
    expect(await readLabZeroRecovery("request-id", "cashier-session")).toEqual(recovery);
    expect(await readLabZeroRecovery("request-id", "another-session")).toBeNull();
    await clearLabZeroRecovery("request-id", "cashier-session");
    expect(await readLabZeroRecovery("request-id", "cashier-session")).toBeNull();
  });

  it("fails closed when the pending quote cannot be stored", async () => {
    const original = sessionStorage.setItem;
    sessionStorage.setItem = () => { throw new Error("blocked"); };
    await expect(saveLabZeroRecovery("request-id", "cashier-session", { quoteId: "quote-id" })).rejects.toThrow("Chưa gửi xác nhận");
    sessionStorage.setItem = original;
  });
});
