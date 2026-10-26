import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DashboardSidebar } from "./DashboardSidebar";

vi.mock("next/navigation", () => ({ usePathname: () => "/pharmacy/drugs/123" }));

describe("MVP dashboard navigation", () => {
  it("shows the six service groups and preserves the nine logical routes for administrators", () => {
    render(<DashboardSidebar role="ADMIN" open={false} onClose={vi.fn()} />);
    for (const group of ["Tổ chức & bệnh nhân", "Khám ngoại trú", "Báo cáo & thông báo"]) {
      expect(screen.getByText(group)).toBeInTheDocument();
    }
    expect(screen.getAllByRole("link")).toHaveLength(9);
    expect(screen.getByRole("link", { name: /Dược.*Thuốc/ })).toHaveAttribute("aria-current", "page");
    expect(screen.queryByRole("link", { name: /Nội trú|Phẫu thuật/ })).not.toBeInTheDocument();
  });

  it("filters routes and empty groups by role and closes navigation after selection", async () => {
    const onClose = vi.fn();
    render(<DashboardSidebar role="PHARMACIST" open onClose={onClose} />);
    expect(screen.getAllByRole("link")).toHaveLength(1);
    expect(screen.queryByText("Tổ chức & bệnh nhân")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("link", { name: /Dược.*Thuốc/ }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("lets managers reach the department revenue report without typing a URL", () => {
    render(<DashboardSidebar role="MANAGER" open={false} onClose={vi.fn()} />);
    expect(screen.getByRole("link", { name: /Doanh thu theo khoa/ })).toHaveAttribute("href", "/billing/revenue");
  });

  it("exposes no business routes without a role", () => {
    render(<DashboardSidebar role={null} open={false} onClose={vi.fn()} />);
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});
