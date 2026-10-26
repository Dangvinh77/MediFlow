import { webcrypto } from "node:crypto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "./api";
import { prepareCommandIntent } from "./command-intent";

describe("persistent command intent", () => {
  beforeEach(() => { vi.stubGlobal("crypto", webcrypto); sessionStorage.clear(); });
  afterEach(() => vi.unstubAllGlobals());

  it("reuses one key and correlation ID for the same command across reload callers", async () => {
    const first = await prepareCommandIntent("registration", "session-a", { patientId: "patient", sessionId: "session" });
    first.settle(new Error("Connection lost"));
    const retry = await prepareCommandIntent("registration", "session-a", { patientId: "patient", sessionId: "session" });
    expect(retry.key).toBe(first.key);
    expect(retry.correlationId).toBe(first.correlationId);
  });

  it("refuses changed command data while an earlier outcome is uncertain", async () => {
    const first = await prepareCommandIntent("scheduled-payment", "session-a", { amount: "100000.00", paymentMethod: "CASH" });
    first.settle(new ApiRequestError("Timed out", 408, "CLIENT_REQUEST_TIMEOUT"));
    await expect(prepareCommandIntent("scheduled-payment", "session-a", { amount: "100000.00", paymentMethod: "BANK_TRANSFER" }))
      .rejects.toThrow("chưa được xác nhận");
  });

  it("clears a key after a terminal result or a definitive validation rejection", async () => {
    const first = await prepareCommandIntent("scope", "session-a", { departmentIds: ["department"] });
    first.settle();
    const second = await prepareCommandIntent("scope", "session-a", { departmentIds: ["department"] });
    expect(second.key).not.toBe(first.key);
    second.settle(new ApiRequestError("Invalid", 422, "SCHEDULE_INELIGIBLE"));
    expect(sessionStorage.length).toBe(0);
  });

  it("does not send a command if storage cannot retain its idempotency receipt", async () => {
    vi.stubGlobal("sessionStorage", {
      getItem: () => null,
      setItem: () => { throw new Error("Denied"); },
      removeItem: vi.fn(),
    });
    await expect(prepareCommandIntent("registration", "session-a", {})).rejects.toThrow("Chưa gửi lệnh");
  });
});
