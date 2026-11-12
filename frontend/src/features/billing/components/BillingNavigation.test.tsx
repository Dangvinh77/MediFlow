import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BillingNavigation } from "./BillingNavigation";

vi.mock("next/navigation", () => ({ usePathname: () => "/billing/payment-requests" }));

describe("BillingNavigation", () => {
  it("shows one active route and omits appointment fees when scheduling is closed", () => {
    render(<BillingNavigation schedulingEnabled={false} />);

    expect(screen.getByRole("link", { name: "Xét nghiệm và thuốc" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Lịch sử thu" })).toBeVisible();
    expect(screen.queryByRole("link", { name: "Hoàn tiền" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Phí khám" })).not.toBeInTheDocument();
  });

  it("adds the scheduled-fee workspace only when its feature is enabled", () => {
    render(<BillingNavigation schedulingEnabled />);

    expect(screen.getByRole("link", { name: "Phí khám" })).toHaveAttribute("href", "/billing/scheduled-payments");
  });
});
