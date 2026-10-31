import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

vi.mock("@/components/auth/RoleGate", () => ({ RoleGate: ({ children }: { children: ReactNode }) => children }));
vi.mock("@/features/pharmacy/components/PharmacyPageShell", () => ({ PharmacyPageShell: ({ children }: { children: ReactNode }) => children }));
vi.mock("@/features/pharmacy/components/prescription/CreatePrescriptionForm", () => ({ CreatePrescriptionForm: ({ mode }: { mode: string }) => <div>form:{mode}</div> }));
import Page, { dynamic } from "./page";

describe("prescription server runtime mode", () => {
  afterEach(() => vi.unstubAllEnvs());
  it.each([undefined, "outpatient"])("renders only V1 when the mode is %s", async mode => {
    vi.stubEnv("MEDIFLOW_PHARMACY_PRESCRIPTION_MODE", mode); render(await Page({ searchParams: Promise.resolve({}) }));
    expect(screen.getByText("form:outpatient")).toBeVisible(); expect(screen.queryByText("form:legacy")).not.toBeInTheDocument();
    expect(dynamic).toBe("force-dynamic");
  });
  it("keeps V0 only for an explicit legacy runtime", async () => {
    vi.stubEnv("MEDIFLOW_PHARMACY_PRESCRIPTION_MODE", "legacy"); render(await Page({ searchParams: Promise.resolve({}) }));
    expect(screen.getByText("form:legacy")).toBeVisible();
  });
  it("fails closed on an invalid runtime setting", async () => {
    vi.stubEnv("MEDIFLOW_PHARMACY_PRESCRIPTION_MODE", "unknown"); render(await Page({ searchParams: Promise.resolve({}) }));
    expect(screen.getByRole("alert")).toHaveTextContent("chưa sẵn sàng"); expect(screen.queryByText(/^form:/)).not.toBeInTheDocument();
  });
});
