import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { DashboardSidebar } from "./DashboardSidebar";

vi.mock("next/navigation", () => ({ usePathname: () => "/pharmacy/drugs/123" }));

describe("MVP dashboard navigation", () => {
  it("gives patients their own result pages without staff workspaces", () => {
    render(<DashboardSidebar role="PATIENT" open={false} onClose={vi.fn()} />);
    expect(screen.getByRole("link", { name: /Kết quả khám của tôi/ })).toHaveAttribute("href", "/records/mine");
    expect(screen.getByRole("link", { name: /Kết quả xét nghiệm/ })).toHaveAttribute("href", "/lab/mine");
    expect(screen.queryByRole("link", { name: /Hồ sơ khám/ })).not.toBeInTheDocument();
  });
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

  it("shows C06 routes only when the server scheduling gate is enabled and only to their roles", () => {
    const { rerender } = render(<DashboardSidebar role="NURSE" open={false} onClose={vi.fn()} schedulingEnabled={false} />);
    expect(screen.queryByRole("link", { name: /Tiếp nhận ca khám/ })).not.toBeInTheDocument();
    rerender(<DashboardSidebar role="NURSE" open={false} onClose={vi.fn()} schedulingEnabled />);
    expect(screen.getByRole("link", { name: /Tiếp nhận ca khám/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Quản lý buổi khám/ })).not.toBeInTheDocument();
  });
});
