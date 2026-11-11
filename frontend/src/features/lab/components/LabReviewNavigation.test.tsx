import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ role: "ADMIN" as string | null }));
vi.mock("@/lib/auth", () => ({
  getRole: () => mocks.role,
  subscribeToAuthChanges: () => () => undefined,
}));

import { LabReviewNavigation } from "./LabReviewNavigation";

describe("lab review navigation", () => {
  beforeEach(() => { mocks.role = "ADMIN"; });

  it("shows the worklist to administrators", () => {
    render(<LabReviewNavigation />);
    expect(screen.getByRole("link", { name: "Hàng đợi duyệt" })).toHaveAttribute("href", "/lab/worklist");
    expect(screen.queryByRole("link", { name: "Cảnh báo nguy cấp" })).not.toBeInTheDocument();
  });

  it("shows the critical page to doctors and both pages to lab technicians", () => {
    mocks.role = "DOCTOR";
    const { rerender } = render(<LabReviewNavigation />);
    expect(screen.getByRole("link", { name: "Cảnh báo nguy cấp" })).toHaveAttribute("href", "/lab/critical");
    expect(screen.queryByRole("link", { name: "Hàng đợi duyệt" })).not.toBeInTheDocument();
    mocks.role = "LAB_TECH";
    rerender(<LabReviewNavigation />);
    expect(screen.getByRole("link", { name: "Hàng đợi duyệt" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Cảnh báo nguy cấp" })).toBeVisible();
  });

  it("hides review links from other roles", () => {
    mocks.role = "NURSE";
    const { container } = render(<LabReviewNavigation />);
    expect(container).toBeEmptyDOMElement();
  });
});
