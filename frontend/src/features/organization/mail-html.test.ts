import { expect, it } from "vitest";
import { safeMailHtml } from "./mail-html";
it("blocks scripts, external resources and dangerous navigation while preserving email formatting", () => {
  const html = safeMailHtml(`<html><head><base href="https://evil.test"></head><body onload="alert(1)"><script>alert(1)</script><form><input></form><table><tr><td style="color:blue">MediFlow</td></tr></table><img src="https://tracker.test/pixel"><a href="javascript:alert(1)">Bad</a><a href="${location.origin}/account-access?purpose=reset&token=abc">Reset</a><a href="https://evil.test/account-access">External</a></body></html>`);
  expect(html).not.toContain("<script"); expect(html).not.toContain("onload"); expect(html).not.toContain("<form");
  expect(html).not.toContain("tracker.test"); expect(html).not.toContain("javascript:"); expect(html).not.toContain("evil.test");
  expect(html).toContain("Content-Security-Policy"); expect(html).toContain("<table>"); expect(html).toContain("token=abc");
});
