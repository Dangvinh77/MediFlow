import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PageResult } from "@/lib/types";
import { departmentContractFixture, staffContractFixture } from "../contract.fixture";
import type { StaffDTO } from "../types";

const mocks = vi.hoisted(() => ({
  departments: vi.fn(),
  staff: vi.fn(),
  router: { replace: vi.fn() },
  getRole: vi.fn(() => "ADMIN"),
  getToken: vi.fn<() => string | null>(() => "session-a"),
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: mocks.getRole,
  getToken: mocks.getToken,
  subscribeToAuthChanges: () => () => {},
}));
vi.mock("../api", () => ({ organizationApi: { departments: mocks.departments, staff: mocks.staff } }));

import { ApiRequestError } from "@/lib/api";

import { DepartmentList } from "./DepartmentList";
import { StaffTable } from "./StaffTable";
import { OrganizationAdminActions } from "./OrganizationAdminActions";

function staffPage(overrides: Partial<PageResult<StaffDTO>> = {}): PageResult<StaffDTO> {
  return {
    content: [staffContractFixture],
    totalElements: 41,
    totalPages: 3,
    number: 0,
    size: 20,
    ...overrides,
  };
}

describe("Organization browse and actions", () => {
  it.each(["departments", "staff"] as const)("ignores a retry 401 after %s unmounts", async (resource) => {
    let reject!: (cause: Error) => void;
    mocks[resource].mockRejectedValueOnce(new Error("Unavailable"))
      .mockReturnValueOnce(new Promise((_, fail) => { reject = fail; }));
    const view = render(resource === "departments" ? <DepartmentList /> : <StaffTable />);
    await screen.findByRole("alert");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(mocks[resource]).toHaveBeenCalledTimes(2));
    view.unmount();
    await act(async () => { reject(new ApiRequestError("Expired", 401)); });
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  it.each(["departments", "staff"] as const)("ignores old-session %s data", async (resource) => {
    let resolve!: (value: unknown) => void;
    mocks[resource].mockReturnValueOnce(new Promise((done) => { resolve = done; }));
    render(resource === "departments" ? <DepartmentList /> : <StaffTable />);
    mocks.getToken.mockReturnValue("session-b");
    await act(async () => { resolve(resource === "departments" ? [departmentContractFixture] : staffPage()); });
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  beforeEach(() => {
    mocks.departments.mockReset();
    mocks.staff.mockReset();
    mocks.router.replace.mockReset();
    mocks.getRole.mockReturnValue("ADMIN");
    mocks.getToken.mockReturnValue("session-a");
  });

  it("loads departments and staff independently and can retry a failed department lookup", async () => {
    mocks.departments
      .mockRejectedValueOnce(new Error("Khoa tạm thời chưa tải được"))
      .mockResolvedValueOnce([{ ...departmentContractFixture, active: false }]);
    mocks.staff.mockResolvedValue(staffPage());
    render(<><DepartmentList /><StaffTable /></>);

    expect(await screen.findByRole("link", { name: "Nguyen Van A" })).toBeVisible();
    expect(await screen.findByRole("alert")).toHaveTextContent("Khoa tạm thời chưa tải được");
    expect(screen.queryByRole("table", { name: "Khoa phòng" })).not.toBeInTheDocument();
    expect(screen.getByRole("table")).toBeInTheDocument();
    expect(mocks.departments).toHaveBeenCalledWith(false);
    expect(mocks.staff).toHaveBeenCalledWith({ page: 0, size: 20 });

    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Clinical")).toBeVisible();
    expect(await screen.findByText("Ngừng hoạt động")).toBeVisible();
    expect(mocks.departments).toHaveBeenCalledTimes(2);
  });

  it("retries a failed staff page using the same page number", async () => {
    mocks.staff
      .mockResolvedValueOnce(staffPage())
      .mockRejectedValueOnce(new Error("Trang nhân sự chưa tải được"))
      .mockResolvedValueOnce(staffPage({ number: 1, content: [{ ...staffContractFixture, fullName: "Second Page" }] }));
    render(<StaffTable />);
    expect(await screen.findByRole("link", { name: "Nguyen Van A" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Trang nhân sự chưa tải được");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("link", { name: "Second Page" })).toBeVisible();
    await waitFor(() => expect(mocks.staff).toHaveBeenNthCalledWith(3, { page: 1, size: 20 }));
  });

  it("keeps organization mutations admin-only in the action menu", () => {
    const view = render(<OrganizationAdminActions />);
    expect(screen.getByRole("link", { name: "Thêm khoa" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Thêm nhân sự" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Quản lý tài khoản" })).toBeVisible();

    mocks.getRole.mockReturnValue("MANAGER");
    view.rerender(<OrganizationAdminActions />);
    expect(screen.queryByRole("link", { name: "Thêm khoa" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Quản lý tài khoản" })).not.toBeInTheDocument();
  });
});
