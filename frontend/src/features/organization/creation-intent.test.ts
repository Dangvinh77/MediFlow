import { webcrypto } from "node:crypto";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { prepareCreation } from "./creation-intent";

describe("pending Organization creation", () => {
  beforeEach(() => { vi.stubGlobal("crypto", webcrypto); sessionStorage.clear(); });
  afterEach(() => vi.unstubAllGlobals());

  it("retains the original key across callers and refuses altered content", async () => {
    const first = await prepareCreation("staff", "bearer-a", { fullName: "Staff A" });
    first.settle(new Error("Lost response"));
    expect((await prepareCreation("staff", "bearer-a", { fullName: "Staff A" })).key).toBe(first.key);
    await expect(prepareCreation("staff", "bearer-a", { fullName: "Staff B" })).rejects.toThrow("Lệnh tạo trước");
    expect(sessionStorage.getItem(sessionStorage.key(0)!)).not.toContain("Staff A");
    expect(sessionStorage.key(0)).not.toContain("bearer-a");
  });

  it.each([200, 408, 500, 503])("retains the receipt for ambiguous status %s", async (status) => {
    const first = await prepareCreation("staff", "session", { name: "A" });
    first.settle(new ApiRequestError("Unavailable", status));
    expect((await prepareCreation("staff", "session", { name: "A" })).key).toBe(first.key);
  });

  it("retains a conflicting key and clears only on success or a definitive rejection", async () => {
    const first = await prepareCreation("account", "session", { username: "admin" });
    first.settle(new ApiRequestError("Conflict", 409, "ACCOUNT_IDEMPOTENCY_KEY_REUSED"));
    expect((await prepareCreation("account", "session", { username: "admin" })).key).toBe(first.key);
    first.settle();
    const second = await prepareCreation("account", "session", { username: "admin" });
    expect(second.key).not.toBe(first.key);
    second.settle(new ApiRequestError("Validation", 400));
    expect(sessionStorage.length).toBe(0);
  });

  it("scopes staff retries by CCCD without persisting raw identity", async () => {
    const first = await prepareCreation("staff", "session", { name: "A" }, "012345678901");
    first.settle(new Error("Lost response"));
    const second = await prepareCreation("staff", "session", { name: "B" }, "012345678902");
    expect(second.key).not.toBe(first.key);
    expect((await prepareCreation("staff", "session", { name: "A" }, "012345678901")).key).toBe(first.key);
    await expect(prepareCreation("staff", "session", { name: "Changed A" }, "012345678901")).rejects.toThrow("Lệnh tạo trước");
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i)!;
      expect(key + sessionStorage.getItem(key)).not.toContain("012345678901");
      expect(key + sessionStorage.getItem(key)).not.toContain("012345678902");
    }
  });

  it("isolates both the operation and session", async () => {
    const staff = await prepareCreation("staff", "a", { name: "A" });
    const otherSession = await prepareCreation("staff", "b", { name: "B" });
    const account = await prepareCreation("account", "a", { name: "C" });
    expect(new Set([staff.key, otherSession.key, account.key]).size).toBe(3);
  });

  it("cancels only new unsent intents while retaining earlier unresolved requests", async () => {
    const unsent = await prepareCreation("staff", "a", {});
    unsent.cancelUnsent();
    expect(sessionStorage.length).toBe(0);
    const sent = await prepareCreation("staff", "a", {});
    sent.settle(new Error("Lost response"));
    const retry = await prepareCreation("staff", "a", {});
    retry.cancelUnsent();
    expect((await prepareCreation("staff", "a", {})).key).toBe(sent.key);
  });

  it("refuses to send when pending intent storage is unavailable", async () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("Denied"); });
    await expect(prepareCreation("staff", "a", {})).rejects.toThrow("Chưa gửi lệnh tạo");
  });
});
