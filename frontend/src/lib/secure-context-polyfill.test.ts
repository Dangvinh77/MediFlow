import { createHash, webcrypto } from "node:crypto";
import { describe, expect, it } from "vitest";
import { installSecureContextPolyfill } from "./secure-context-polyfill";

function insecureCrypto() {
  const target = { getRandomValues: webcrypto.getRandomValues.bind(webcrypto) } as unknown as Crypto;
  installSecureContextPolyfill(target);
  return target;
}

const hex = (buffer: ArrayBuffer) => Buffer.from(buffer).toString("hex");

describe("installSecureContextPolyfill", () => {
  it.each([0, 1, 3, 55, 56, 63, 64, 65, 119, 120, 1000, 100_000])("hashes %i bytes like native SHA-256", async (length) => {
    const message = Uint8Array.from({ length }, (_, i) => (i * 31 + 7) & 0xff);
    const digest = await insecureCrypto().subtle.digest("SHA-256", message);
    expect(hex(digest)).toBe(createHash("sha256").update(message).digest("hex"));
  });

  it("hashes only the viewed range and accepts a raw ArrayBuffer", async () => {
    const target = insecureCrypto();
    const backing = new TextEncoder().encode("xxabcyy");
    const expected = createHash("sha256").update("abc").digest("hex");
    expect(hex(await target.subtle.digest({ name: "SHA-256" }, backing.subarray(2, 5)))).toBe(expected);
    expect(hex(await target.subtle.digest("SHA-256", backing.slice(2, 5).buffer))).toBe(expected);
  });

  it("rejects algorithms it does not implement", async () => {
    await expect(insecureCrypto().subtle.digest("SHA-512", new Uint8Array())).rejects.toThrow("SHA-512");
  });

  it("generates distinct version 4 UUIDs", () => {
    const target = insecureCrypto();
    const ids = new Set(Array.from({ length: 200 }, () => target.randomUUID()));
    expect(ids.size).toBe(200);
    ids.forEach((id) => expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/));
  });

  it("keeps native implementations", () => {
    const randomUUID = () => "native" as ReturnType<Crypto["randomUUID"]>;
    const subtle = {} as SubtleCrypto;
    const target = { randomUUID, subtle } as unknown as Crypto;
    installSecureContextPolyfill(target);
    expect(target.randomUUID).toBe(randomUUID);
    expect(target.subtle).toBe(subtle);
  });
});
