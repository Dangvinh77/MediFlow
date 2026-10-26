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
  getSessionKey: vi.fn<() => string | null>(() => "session-a"),
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: mocks.getRole,
  getSessionKey: mocks.getSessionKey,
  subscribeToAuthChanges: () => () => {},
}));
vi.mock("../api", () => ({ organizationApi: { departments: mocks.departments, staff: mocks.staff } }));

import { ApiRequestError } from "@/lib/api";

import { DepartmentList } from "./DepartmentList";
import { StaffTable } from "./StaffTable";
import { OrganizationAdminActions } from "./OrganizationAdminActions";
import { OrganizationDirectory } from "./OrganizationDirectory";

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
  it("searches the server directory and resets pagination while keeping filters", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture]);
    mocks.staff.mockResolvedValue(staffPage());
    render(<StaffTable departments={[departmentContractFixture]} />);
    await screen.findByRole("link", { name: "Xem chi tiết Nguyen Van A" });
    fireEvent.change(screen.getByLabelText("Lọc theo khoa"), { target: { value: departmentContractFixture.departmentId } });
    await waitFor(() => expect(mocks.staff).toHaveBeenLastCalledWith(expect.objectContaining({ departmentId: departmentContractFixture.departmentId })));
    fireEvent.change(screen.getByLabelText("Tìm nhân sự"), { target: { value: "  dang anh  " } });
    fireEvent.submit(screen.getByLabelText("Tìm nhân sự").closest("form")!);
    await waitFor(() => expect(mocks.staff).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0, keyword: "dang anh", departmentId: departmentContractFixture.departmentId })));
  });

  it("filters department names without accents and by abbreviation", async () => {
    mocks.departments.mockResolvedValue([
      { ...departmentContractFixture, departmentName: "Khoa Nội", abbreviation: "NOI" },
      { ...departmentContractFixture, departmentId: "lab", departmentName: "Khoa Xét nghiệm", abbreviation: "XN" },
    ]);
    render(<DepartmentList />);
    await screen.findByRole("link", { name: "Xem chi tiết Khoa Nội" });
    fireEvent.change(screen.getByLabelText("Tìm khoa/phòng"), { target: { value: "noi" } });
    expect(screen.getByRole("link", { name: "Xem chi tiết Khoa Nội" })).toBeVisible();
    expect(screen.queryByRole("link", { name: "Xem chi tiết Khoa Xét nghiệm" })).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Tìm khoa/phòng"), { target: { value: "xn" } });
    expect(screen.getByRole("link", { name: "Xem chi tiết Khoa Xét nghiệm" })).toBeVisible();
    expect(mocks.departments).toHaveBeenCalledTimes(1);
  });
  it("switches between khoa, phòng and staff without mixing department categories", async () => {
    mocks.departments.mockResolvedValue([
      { ...departmentContractFixture, departmentName: "Khoa Nội", departmentType: "CLINICAL" },
      { ...departmentContractFixture, departmentId: "lab", departmentName: "Khoa Xét nghiệm", departmentType: "PARACLINICAL" },
      { ...departmentContractFixture, departmentId: "office", departmentName: "Phòng Hành chính", departmentType: "ADMINISTRATIVE" },
    ]);
    mocks.staff.mockResolvedValue(staffPage());
    render(<OrganizationDirectory />);
    expect(screen.getByRole("button", { name: /Khoa/ })).toHaveAttribute("aria-pressed", "true");
    expect(await screen.findByRole("link", { name: "Xem chi tiết Khoa Nội" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Xem chi tiết Khoa Xét nghiệm" })).toBeVisible();
    expect(screen.queryByRole("link", { name: "Xem chi tiết Phòng Hành chính" })).not.toBeInTheDocument();
    expect(mocks.staff).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: /Phòng/ }));
    expect(screen.getByRole("link", { name: "Xem chi tiết Phòng Hành chính" })).toBeVisible();
    expect(screen.queryByRole("link", { name: "Xem chi tiết Khoa Nội" })).not.toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Tên phòng" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: /Nhân sự/ }));
    expect(await screen.findByRole("link", { name: "Xem chi tiết Nguyen Van A" })).toBeVisible();
    expect(screen.queryByRole("table", { name: "Khoa" })).not.toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Lọc theo khoa" })).toHaveTextContent("Phòng Hành chính");
    fireEvent.click(screen.getByRole("button", { name: /Khoa/ }));
    expect(screen.getByRole("link", { name: "Xem chi tiết Khoa Nội" })).toBeVisible();
    expect(screen.queryByRole("link", { name: "Xem chi tiết Nguyen Van A" })).not.toBeInTheDocument();
    expect(mocks.departments).toHaveBeenCalledTimes(1);
  });

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
    mocks.getSessionKey.mockReturnValue("session-b");
    await act(async () => { resolve(resource === "departments" ? [departmentContractFixture] : staffPage()); });
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  beforeEach(() => {
    mocks.departments.mockReset();
    mocks.staff.mockReset();
    mocks.router.replace.mockReset();
    mocks.getRole.mockReturnValue("ADMIN");
    mocks.getSessionKey.mockReturnValue("session-a");
  });

  it("loads departments and staff independently and can retry a failed department lookup", async () => {
    mocks.departments
      .mockRejectedValueOnce(new Error("Khoa tạm thời chưa tải được"))
      .mockResolvedValueOnce([{ ...departmentContractFixture, active: false }]);
    mocks.staff.mockResolvedValue(staffPage());
    render(<><DepartmentList /><StaffTable /></>);

    expect(await screen.findByRole("link", { name: "Xem chi tiết Nguyen Van A" })).toBeVisible();
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
    expect(await screen.findByRole("link", { name: "Xem chi tiết Nguyen Van A" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Trang nhân sự chưa tải được");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("link", { name: "Xem chi tiết Second Page" })).toBeVisible();
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
