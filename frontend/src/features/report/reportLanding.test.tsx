import { afterEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ redirect: vi.fn((url: string) => { throw new Error(`redirect:${url}`); }) }));
vi.mock("next/navigation", () => ({ redirect: mocks.redirect }));
vi.mock("@/components/auth/RoleGate", () => ({ RoleGate: () => null }));
vi.mock("@/features/report/components/ReportWorkspace", () => ({ ReportWorkspace: () => null }));
import ReportsPage from "@/app/(dashboard)/reports/page";

afterEach(() => { vi.unstubAllEnvs(); vi.clearAllMocks(); });

describe("Report landing", () => {
  it("opens accepted outpatient statistics when the rollout is enabled", async () => {
    vi.stubEnv("MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED", "true");
    await expect(ReportsPage({ searchParams: Promise.resolve({}) })).rejects.toThrow("redirect:/reports/outpatient?view=monthly");
  });
  it("preserves explicit legacy links and the disabled rollout", async () => {
    vi.stubEnv("MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED", "true");
    await ReportsPage({ searchParams: Promise.resolve({ view: "monthly" }) });
    expect(mocks.redirect).not.toHaveBeenCalled();
    vi.stubEnv("MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED", "false");
    await ReportsPage({ searchParams: Promise.resolve({}) });
    expect(mocks.redirect).not.toHaveBeenCalled();
  });
});
