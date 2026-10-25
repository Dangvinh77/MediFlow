import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Role } from "@/lib/roles";

const mocks = vi.hoisted(() => ({ role: "ADMIN" as Role | null, pathname: "/pharmacy/drugs" }));
vi.mock("next/navigation", () => ({ usePathname: () => mocks.pathname }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
import { PharmacyNav } from "./PharmacyNav";

describe("PharmacyNav", () => {
  beforeEach(() => { mocks.role = "ADMIN"; mocks.pathname = "/pharmacy/drugs"; });
  it("keeps prescription creation reachable for DOCTOR and hides recovery", () => {
    mocks.role = "DOCTOR"; render(<PharmacyNav />);
    expect(screen.getByRole("link", { name: "Kê đơn" })).toHaveAttribute("href", "/pharmacy/prescriptions/new");
    expect(screen.queryByText("Quản trị hệ thống")).not.toBeInTheDocument();
  });
  it("marks only the longest matching prescription route active", () => {
    mocks.pathname = "/pharmacy/prescriptions/new"; render(<PharmacyNav />);
    expect(screen.getByRole("link", { name: "Kê đơn" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Đơn thuốc" })).not.toHaveAttribute("aria-current");
  });
  it("does not offer create prescription or outbox to a pharmacist", () => {
    mocks.role = "PHARMACIST"; render(<PharmacyNav />);
    expect(screen.queryByRole("link", { name: "Kê đơn" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Danh mục" })).toBeVisible();
    expect(screen.queryByText("Quản trị hệ thống")).not.toBeInTheDocument();
  });
  it("keeps maintenance collapsed for administrators during normal work", () => {
    render(<PharmacyNav />);
    expect(screen.getByText("Quản trị hệ thống").closest("details")).not.toHaveAttribute("open");
    expect(screen.getByText("Khôi phục đồng bộ dữ liệu")).not.toBeVisible();
  });
  it("does not render role-specific navigation before session hydration", () => {
    mocks.role = null; render(<PharmacyNav />);
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
  });
});
