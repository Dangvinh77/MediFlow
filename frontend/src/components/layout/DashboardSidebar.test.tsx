import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DashboardSidebar } from "./DashboardSidebar";

const route = vi.hoisted(() => ({ pathname: "/pharmacy/drugs/123" }));
vi.mock("next/navigation", () => ({ usePathname: () => route.pathname }));

describe("MVP dashboard navigation", () => {
  it("gives patients their own result pages without staff workspaces", () => {
    render(<DashboardSidebar role="PATIENT" open={false} onClose={vi.fn()} />);
    expect(screen.getByRole("link", { name: /Kết quả khám của tôi/ })).toHaveAttribute("href", "/records/mine");
    expect(screen.getByRole("link", { name: /Kết quả xét nghiệm/ })).toHaveAttribute("href", "/lab/mine");
    expect(screen.queryByRole("link", { name: /Hồ sơ khám/ })).not.toBeInTheDocument();
  });
  it("shows the six service groups and offers the billing source lookup for administrators", () => {
    render(<DashboardSidebar role="ADMIN" open={false} onClose={vi.fn()} />);
    for (const group of ["Tổ chức & bệnh nhân", "Khám ngoại trú", "Báo cáo & thông báo"]) {
      expect(screen.getByText(group)).toBeInTheDocument();
    }
    expect(screen.getAllByRole("link")).toHaveLength(10);
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

  it("shows C06 routes only when the server scheduling gate is enabled and only to their roles", () => {
    const { rerender } = render(<DashboardSidebar role="NURSE" open={false} onClose={vi.fn()} schedulingEnabled={false} />);
    expect(screen.queryByRole("link", { name: /Tiếp nhận ca khám/ })).not.toBeInTheDocument();
    rerender(<DashboardSidebar role="NURSE" open={false} onClose={vi.fn()} schedulingEnabled />);
    expect(screen.getByRole("link", { name: /Tiếp nhận ca khám/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Quản lý buổi khám/ })).not.toBeInTheDocument();
  });
});


it.each([
  ["NURSE", "/appointments/reception", "Tiếp nhận ca khám"],
  ["DOCTOR", "/appointments/my-calendar", "Lịch khám của tôi"],
  ["CASHIER", "/billing/scheduled-payments", "Thu phí lịch khám"],
] as const)("marks only the current workspace for %s", (role, pathname, name) => {
  route.pathname = pathname;
  render(<DashboardSidebar role={role} open={false} onClose={vi.fn()} schedulingEnabled />);
  const current = screen.getAllByRole("link").filter(link => link.getAttribute("aria-current") === "page");
  expect(current).toHaveLength(1);
  expect(current[0]).toHaveAccessibleName(new RegExp(name));
  route.pathname = "/pharmacy/drugs/123";
});
