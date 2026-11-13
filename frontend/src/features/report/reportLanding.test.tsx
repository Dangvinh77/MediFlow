import { describe, expect, it } from "vitest";
import nextConfig from "../../../next.config";

// Next forwards the query string of a config redirect, so legacy ?view=monthly links keep working.
describe("Report landing", () => {
  it("always opens the merged report from a config redirect, not from render", async () => {
    const redirects = await nextConfig.redirects!();
    expect(redirects).toContainEqual({ source: "/reports", destination: "/reports/outpatient", permanent: false });
    expect(redirects).toContainEqual({ source: "/pharmacy", destination: "/pharmacy/drugs", permanent: false });
  });
});
