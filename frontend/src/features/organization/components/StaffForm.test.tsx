import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { departmentContractFixture, staffContractFixture } from "../contract.fixture";
import type { DepartmentDTO, StaffDTO } from "../types";

const mocks = vi.hoisted(() => ({
  departments: vi.fn(),
  staffMember: vi.fn(),
  createStaff: vi.fn(),
  updateStaff: vi.fn(),
  changeStaffDepartment: vi.fn(),
  getToken: vi.fn(() => "session-a"),
  router: { back: vi.fn(), push: vi.fn(), replace: vi.fn() },
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({
  organizationApi: {
    departments: mocks.departments,
    staffMember: mocks.staffMember,
    createStaff: mocks.createStaff,
    updateStaff: mocks.updateStaff,
    changeStaffDepartment: mocks.changeStaffDepartment,
  },
}));
vi.mock("@/lib/auth", () => ({ getToken: mocks.getToken }));

import { StaffForm } from "./StaffForm";

const targetDepartment: DepartmentDTO = {
  ...departmentContractFixture,
  departmentId: "00000000-0000-4000-8000-000000000005",
  departmentName: "Laboratory",
  abbreviation: "LAB",
  departmentType: "PARACLINICAL",
};

describe("StaffForm", () => {
  beforeEach(() => {
    mocks.departments.mockReset();
    mocks.staffMember.mockReset();
    mocks.createStaff.mockReset();
    mocks.updateStaff.mockReset();
    mocks.changeStaffDepartment.mockReset();
    mocks.getToken.mockReturnValue("session-a");
    mocks.router.back.mockReset();
    mocks.router.push.mockReset();
    mocks.router.replace.mockReset();
  });

  it("blocks a new staff form when department loading fails and retries the lookup", async () => {
    mocks.departments
      .mockRejectedValueOnce(new Error("Khoa không tải được"))
      .mockResolvedValueOnce([departmentContractFixture]);
    render(<StaffForm />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Khoa không tải được");
    expect(screen.queryByLabelText(/Họ tên/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByLabelText(/Họ tên/)).toBeVisible();
    expect(mocks.departments).toHaveBeenCalledTimes(2);
    expect(mocks.createStaff).not.toHaveBeenCalled();
  });

  it("creates a staff profile with explicit nullable optional fields", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture]);
    mocks.createStaff.mockResolvedValue(staffContractFixture);
    render(<StaffForm />);
    await screen.findByLabelText(/Họ tên/);
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: " Nguyen Van A " } });
    fireEvent.change(screen.getByLabelText(/Khoa/), { target: { value: departmentContractFixture.departmentId } });
    fireEvent.submit(screen.getByRole("button", { name: "Tạo nhân sự" }).closest("form")!);

    await waitFor(() => expect(mocks.createStaff).toHaveBeenCalledWith({
      fullName: "Nguyen Van A",
      departmentId: departmentContractFixture.departmentId,
      jobTitle: "DOCTOR",
      specialization: null,
      licenseNumber: null,
      phoneNumber: null,
      email: null,
    }));
    expect(mocks.router.push).toHaveBeenCalledWith(
      `/organization/staff/${staffContractFixture.staffId}?notice=created`,
    );
  });

  it("preserves and reports a saved profile when a department transfer fails, then safely retries it", async () => {
    const profileSaved: StaffDTO = { ...staffContractFixture, fullName: "Updated Name" };
    const currentDepartment: StaffDTO = { ...profileSaved, departmentId: departmentContractFixture.departmentId };
    const movedStaff: StaffDTO = { ...profileSaved, departmentId: targetDepartment.departmentId };
    mocks.departments.mockResolvedValue([departmentContractFixture, targetDepartment]);
    mocks.staffMember.mockResolvedValueOnce(staffContractFixture).mockResolvedValueOnce(currentDepartment);
    mocks.updateStaff.mockResolvedValue(profileSaved);
    mocks.changeStaffDepartment
      .mockRejectedValueOnce(new Error("DEPARTMENT_HEAD_INVALID: cần gỡ trưởng khoa trước"))
      .mockResolvedValueOnce(movedStaff);
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    await screen.findByDisplayValue("Nguyen Van A");
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "Updated Name" } });
    fireEvent.change(screen.getByLabelText(/Khoa/), { target: { value: targetDepartment.departmentId } });
    const submit = () => fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);

    submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("Thông tin nhân sự đã được lưu");
    expect(screen.getByLabelText(/Họ tên/)).toHaveValue("Updated Name");
    expect(screen.getByLabelText(/Khoa/)).toHaveValue(targetDepartment.departmentId);
    expect(mocks.staffMember).toHaveBeenCalledTimes(2);

    submit();
    await waitFor(() => expect(mocks.router.push).toHaveBeenCalledWith(
      `/organization/staff/${staffContractFixture.staffId}?notice=updated`,
    ));
    expect(mocks.updateStaff).toHaveBeenCalledTimes(2);
    expect(mocks.changeStaffDepartment).toHaveBeenCalledTimes(2);
  });

  it("does not send a follow-up transfer after the session changes during the profile save", async () => {
    let resolve!: (staff: StaffDTO) => void;
    const request = new Promise<StaffDTO>((done) => { resolve = done; });
    mocks.departments.mockResolvedValue([departmentContractFixture, targetDepartment]);
    mocks.staffMember.mockResolvedValue(staffContractFixture);
    mocks.updateStaff.mockReturnValue(request);
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    await screen.findByDisplayValue("Nguyen Van A");
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "Updated Name" } });
    fireEvent.change(screen.getByLabelText(/Khoa/), { target: { value: targetDepartment.departmentId } });
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    await waitFor(() => expect(mocks.updateStaff).toHaveBeenCalledTimes(1));

    mocks.getToken.mockReturnValue("session-b");
    await act(async () => { resolve({ ...staffContractFixture, fullName: "Updated Name" }); await request; });
    expect(mocks.changeStaffDepartment).not.toHaveBeenCalled();
    expect(mocks.router.push).not.toHaveBeenCalled();
  });
});
