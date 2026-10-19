import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

vi.mock("@/components/auth/RoleGate", () => ({ RoleGate: ({ children }: { children: ReactNode }) => children }));
vi.mock("@/features/pharmacy/components/PharmacyPageShell", () => ({ PharmacyPageShell: ({ children }: { children: ReactNode }) => children }));
vi.mock("@/features/pharmacy/components/prescription/PrescriptionDetail", () => ({ PrescriptionDetail: ({ mode }: { mode: string | null }) => <div>detail:{mode ?? "unavailable"}</div> }));
import Page, { dynamic } from "./page";

const params = Promise.resolve({ prescriptionId: "00000000-0000-4000-8000-000000000001" });
describe("prescription detail server contract mode", () => {
  afterEach(() => vi.unstubAllEnvs());
  it.each([undefined, "outpatient"])("uses V1 runtime for %s without read metadata", async mode => {
    vi.stubEnv("MEDIFLOW_PHARMACY_PRESCRIPTION_MODE", mode);
    render(await Page({ params }));
    expect(screen.getByText("detail:outpatient")).toBeVisible();
    expect(dynamic).toBe("force-dynamic");
  });
  it.each([["legacy", "legacy"], ["invalid", "unavailable"]])("passes %s explicitly as %s", async (setting, expected) => {
    vi.stubEnv("MEDIFLOW_PHARMACY_PRESCRIPTION_MODE", setting);
    render(await Page({ params }));
    expect(screen.getByText(`detail:${expected}`)).toBeVisible();
  });
});
