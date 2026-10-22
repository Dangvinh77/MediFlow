import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Role } from "@/lib/roles";
import { DashboardHeader } from "./DashboardHeader";

vi.mock("@/components/theme/ThemeToggle", () => ({ ThemeToggle: () => null }));

describe("Personal profile navigation", () => {
  it.each<Role>(["ADMIN", "MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT"])("offers the profile for %s", (role) => {
    render(<DashboardHeader role={role} onLogout={vi.fn()} onOpenNavigation={vi.fn()} />);
    expect(screen.getByRole("link", { name: "Hồ sơ cá nhân" })).toHaveAttribute("href", "/organization/profile");
  });
  it.each([null, "SYSTEM"] as const)("does not offer a human profile for %s", (role) => {
    render(<DashboardHeader role={role} onLogout={vi.fn()} onOpenNavigation={vi.fn()} />);
    expect(screen.queryByRole("link", { name: "Hồ sơ cá nhân" })).not.toBeInTheDocument();
  });
});
