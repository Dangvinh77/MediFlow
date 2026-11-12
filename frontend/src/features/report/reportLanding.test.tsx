import { afterEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ redirect: vi.fn((url: string) => { throw new Error(`redirect:${url}`); }) }));
vi.mock("next/navigation", () => ({ redirect: mocks.redirect }));
import ReportsPage from "@/app/(dashboard)/reports/page";

afterEach(() => { vi.clearAllMocks(); });

describe("Report landing", () => {
  it("always opens the merged report", async () => {
    await expect(ReportsPage({ searchParams: Promise.resolve({}) })).rejects.toThrow("redirect:/reports/outpatient");
  });
  it("keeps old legacy links working by forwarding their query", async () => {
    await expect(ReportsPage({ searchParams: Promise.resolve({ view: "monthly", year: "2024" }) }))
      .rejects.toThrow("redirect:/reports/outpatient?view=monthly&year=2024");
  });
});
