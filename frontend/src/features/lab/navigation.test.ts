import { describe, expect, it } from "vitest";
import { labQueueHref, safeLabReturn } from "./navigation";

describe("lab queue context", () => {
  it("retains explicit all-status selection, episode scope and page", () => {
    const href = labQueueHref({ episodeType: "ADMISSION", episodeId: "episode", departmentId: "department" }, 2);
    const params = new URL(href, "http://localhost").searchParams;
    expect(params.get("status")).toBe("all");
    expect(params.get("episodeType")).toBe("ADMISSION");
    expect(params.get("episodeId")).toBe("episode");
    expect(params.get("page")).toBe("2");
    expect(safeLabReturn(href)).toBe(href);
  });

  it.each(["https://example.com/lab", "//example.com/lab", "/lab/other", "/billing"])("does not use arbitrary return target %s", value => {
    expect(safeLabReturn(value)).toBe("/lab");
  });
});
