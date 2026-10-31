import { describe, expect, it } from "vitest";
import { sessionDateErrors, vietnamToday } from "./session-dates";
describe("consultation search date contract", () => {
  it("requires both endpoints", () => { expect(sessionDateErrors("", "")).toHaveProperty("fromDate"); expect(sessionDateErrors("2026-10-31", "")).toHaveProperty("toDate"); });
  it("rejects malformed and impossible dates", () => { expect(sessionDateErrors("bad", "2026-10-31")).toHaveProperty("fromDate"); expect(sessionDateErrors("2026-02-30", "2026-03-01")).toHaveProperty("fromDate"); });
  it("rejects inverted or over 30 elapsed days", () => { expect(sessionDateErrors("2026-11-01", "2026-10-31")).toHaveProperty("toDate"); expect(sessionDateErrors("2026-10-01", "2026-11-01")).toHaveProperty("toDate"); });
  it("accepts same day and server maximum", () => { expect(sessionDateErrors("2026-10-31", "2026-10-31")).toEqual({}); expect(sessionDateErrors("2026-10-01", "2026-10-31")).toEqual({}); });
  it("defaults to the Vietnam calendar day at UTC midnight boundary", () => { expect(vietnamToday(new Date("2026-10-30T18:00:00Z"))).toBe("2026-10-31"); });
});
