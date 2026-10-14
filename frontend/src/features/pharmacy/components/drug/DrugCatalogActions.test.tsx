import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { Role } from "@/lib/roles";
const mocks = vi.hoisted(() => ({ role: null as Role | null }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
import { DrugCatalogActions } from "./DrugCatalogActions";

describe("DrugCatalogActions", () => {
  it.each(["ADMIN", "PHARMACIST"] as Role[])("offers create drug to %s", role => {
    mocks.role = role; render(<DrugCatalogActions />);
    expect(screen.getByRole("link", { name: /Thêm thuốc/ })).toHaveAttribute("href", "/pharmacy/drugs/new");
  });
  it.each(["DOCTOR", "MANAGER", null] as (Role | null)[])("does not offer inventory mutations to %s", role => {
    mocks.role = role; render(<DrugCatalogActions />);
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
