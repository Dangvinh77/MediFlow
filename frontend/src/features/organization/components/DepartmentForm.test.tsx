import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { departmentContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({
  department: vi.fn(),
  createDepartment: vi.fn(),
  updateDepartment: vi.fn(),
  getToken: vi.fn<() => string | null>(() => "session-a"),
  router: { back: vi.fn(), push: vi.fn(), replace: vi.fn() },
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({
  organizationApi: {
    department: mocks.department,
    createDepartment: mocks.createDepartment,
    updateDepartment: mocks.updateDepartment,
  },
}));
vi.mock("@/lib/auth", () => ({ getToken: mocks.getToken }));

import { ApiRequestError } from "@/lib/api";

import { DepartmentForm } from "./DepartmentForm";

describe("DepartmentForm", () => {
  it("serializes duplicate submits and allows a retry after failure", async () => {
    let reject!: (cause: Error) => void;
    const request = new Promise<never>((_, fail) => { reject = fail; });
    mocks.createDepartment.mockReturnValueOnce(request).mockResolvedValue(departmentContractFixture);
    render(<DepartmentForm />);
    const form = screen.getByRole("button", { name: "Tạo khoa" }).closest("form")!;
    act(() => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.createDepartment).toHaveBeenCalledTimes(1);
    await act(async () => { reject(new Error("Unavailable")); });
    expect(await screen.findByRole("alert")).toHaveTextContent("Unavailable");
    fireEvent.submit(form);
    await waitFor(() => expect(mocks.createDepartment).toHaveBeenCalledTimes(2));
  });

  it("does not navigate from an old save after switching to another department", async () => {
    let resolve!: (value: typeof departmentContractFixture) => void;
    const request = new Promise<typeof departmentContractFixture>((done) => { resolve = done; });
    mocks.department.mockResolvedValue(departmentContractFixture);
    mocks.updateDepartment.mockReturnValue(request);
    const view = render(<DepartmentForm departmentId={departmentContractFixture.departmentId} />);
    await screen.findByDisplayValue("Clinical");
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    view.rerender(<DepartmentForm departmentId="00000000-0000-4000-8000-000000000099" />);
    await act(async () => { resolve(departmentContractFixture); });
    expect(mocks.router.push).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Lưu thay đổi" })).toBeEnabled();
  });

  it.each([null, "session-b"])("handles save 401 without redirecting a newer session (%s)", async (token) => {
    mocks.createDepartment.mockImplementation(async () => {
      mocks.getToken.mockReturnValue(token);
      throw new ApiRequestError("Expired", 401);
    });
    render(<DepartmentForm />);
    fireEvent.submit(screen.getByRole("button", { name: "Tạo khoa" }).closest("form")!);
    await waitFor(() => expect(mocks.createDepartment).toHaveBeenCalledTimes(1));
    if (token === null) expect(mocks.router.replace).toHaveBeenCalledWith("/login");
    else expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  beforeEach(() => {
    mocks.department.mockReset();
    mocks.createDepartment.mockReset();
    mocks.updateDepartment.mockReset();
    mocks.getToken.mockReturnValue("session-a");
    mocks.router.back.mockReset();
    mocks.router.push.mockReset();
    mocks.router.replace.mockReset();
  });

  it("does not allow an edit to submit blank data after the initial load fails, and retries", async () => {
    mocks.department
      .mockRejectedValueOnce(new Error("Không kết nối được"))
      .mockResolvedValueOnce(departmentContractFixture);
    render(<DepartmentForm departmentId={departmentContractFixture.departmentId} />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Không kết nối được");
    expect(screen.queryByLabelText(/Tên khoa/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByDisplayValue("Clinical")).toBeVisible();
    expect(mocks.department).toHaveBeenCalledTimes(2);
    expect(mocks.updateDepartment).not.toHaveBeenCalled();
  });

  it("sends the create request and routes to the returned department", async () => {
    mocks.createDepartment.mockResolvedValue(departmentContractFixture);
    render(<DepartmentForm />);
    fireEvent.change(screen.getByLabelText(/Tên khoa/), { target: { value: "Clinical" } });
    fireEvent.change(screen.getByLabelText(/Viết tắt/), { target: { value: "CLN" } });
    fireEvent.submit(screen.getByRole("button", { name: "Tạo khoa" }).closest("form")!);

    await waitFor(() => expect(mocks.createDepartment).toHaveBeenCalledWith({
      departmentName: "Clinical",
      abbreviation: "CLN",
      departmentType: "CLINICAL",
      location: null,
    }));
    expect(mocks.router.push).toHaveBeenCalledWith(
      `/organization/departments/${departmentContractFixture.departmentId}?notice=created`,
    );
  });

  it("keeps update fields contract-shaped and validates the department-head UUID", async () => {
    mocks.department.mockResolvedValue({
      ...departmentContractFixture,
      departmentHeadId: "00000000-0000-4000-8000-000000000009",
    });
    mocks.updateDepartment.mockResolvedValue(departmentContractFixture);
    render(<DepartmentForm departmentId={departmentContractFixture.departmentId} />);
    await screen.findByDisplayValue("Clinical");
    fireEvent.change(screen.getByLabelText(/Mã trưởng khoa/), { target: { value: "not-a-uuid" } });
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    expect(await screen.findByRole("alert")).toHaveTextContent("UUID hợp lệ");
    expect(mocks.updateDepartment).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(/Mã trưởng khoa/), { target: { value: "" } });
    fireEvent.change(screen.getByLabelText(/Địa điểm/), { target: { value: "  West Wing  " } });
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    await waitFor(() => expect(mocks.updateDepartment).toHaveBeenCalledWith(
      departmentContractFixture.departmentId,
      {
        departmentName: "Clinical",
        departmentType: "CLINICAL",
        location: "West Wing",
        departmentHeadId: null,
        active: true,
      },
    ));
    expect(mocks.router.push).toHaveBeenCalledWith(
      `/organization/departments/${departmentContractFixture.departmentId}?notice=updated`,
    );
  });
});
