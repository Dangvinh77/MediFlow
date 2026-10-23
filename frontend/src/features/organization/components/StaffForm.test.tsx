import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { webcrypto } from "node:crypto";
import { departmentContractFixture, staffContractFixture as baseStaffFixture } from "../contract.fixture";
import type { DepartmentDTO, StaffDTO } from "../types";

const mocks = vi.hoisted(() => ({
  departments: vi.fn(),
  staffMember: vi.fn(),
  createStaff: vi.fn(),
  updateStaff: vi.fn(),
  changeStaffDepartment: vi.fn(),
  getSessionKey: vi.fn<() => string | null>(() => "session-a"),
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
vi.mock("@/lib/auth", () => ({ getSessionKey: mocks.getSessionKey }));

import { ApiRequestError } from "@/lib/api";

import { StaffForm } from "./StaffForm";

const staffContractFixture: StaffDTO = { ...baseStaffFixture, licenseNumber: "LIC-001" };

const targetDepartment: DepartmentDTO = {
  ...departmentContractFixture,
  departmentId: "00000000-0000-4000-8000-000000000005",
  departmentName: "Laboratory",
  abbreviation: "LAB",
  departmentType: "PARACLINICAL",
};

describe("StaffForm", () => {
  it("requires a doctor's license before sending any staff command", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture]);
    render(<StaffForm />);
    const license = await screen.findByLabelText(/Số giấy phép/);
    expect(license).toBeRequired();
    fireEvent.submit(license.closest("form")!);
    expect(await screen.findByRole("alert")).toHaveTextContent("Bác sĩ phải có số giấy phép");
    expect(mocks.createStaff).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(/Chức danh/), { target: { value: "NURSE" } });
    expect(license).not.toBeRequired();
  });
  afterEach(() => vi.unstubAllGlobals());
  it("uses the reconciled department as the baseline when changing the transfer target", async () => {
    const otherDepartment = { ...targetDepartment, departmentId: "00000000-0000-4000-8000-000000000099" };
    const reconciled = { ...staffContractFixture, departmentId: otherDepartment.departmentId };
    mocks.departments.mockResolvedValue([departmentContractFixture, targetDepartment, otherDepartment]);
    mocks.staffMember.mockResolvedValueOnce(staffContractFixture).mockResolvedValue(reconciled);
    mocks.updateStaff.mockResolvedValue(staffContractFixture);
    mocks.changeStaffDepartment.mockRejectedValueOnce(new Error("Lost response"))
      .mockResolvedValueOnce(staffContractFixture);
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    await screen.findByDisplayValue("Nguyen Van A");
    const select = screen.getByLabelText(/Đơn vị công tác/);
    fireEvent.change(select, { target: { value: targetDepartment.departmentId } });
    const submit = () => fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    submit();
    await screen.findByRole("alert");
    // Explicitly select the initial department after reconciliation reported a third one.
    fireEvent.change(select, { target: { value: departmentContractFixture.departmentId } });
    submit();
    await waitFor(() => expect(mocks.router.push).toHaveBeenCalled());
    expect(mocks.changeStaffDepartment).toHaveBeenLastCalledWith(staffContractFixture.staffId, departmentContractFixture.departmentId);
    expect(mocks.updateStaff).toHaveBeenCalledTimes(1);
  });

  it("refuses to repeat a transfer until a fresh read can confirm the result", async () => {
    const movedStaff = { ...staffContractFixture, departmentId: targetDepartment.departmentId };
    mocks.departments.mockResolvedValue([departmentContractFixture, targetDepartment]);
    mocks.staffMember.mockResolvedValueOnce(staffContractFixture)
      .mockRejectedValueOnce(new Error("Read unavailable"))
      .mockRejectedValueOnce(new Error("Still unavailable"))
      .mockResolvedValueOnce(movedStaff);
    mocks.updateStaff.mockResolvedValue(staffContractFixture);
    mocks.changeStaffDepartment.mockRejectedValue(new Error("Lost response"));
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    await screen.findByDisplayValue("Nguyen Van A");
    fireEvent.change(screen.getByLabelText(/Đơn vị công tác/), { target: { value: targetDepartment.departmentId } });
    const submit = () => fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("chưa xác minh được khoa hiện tại");
    submit();
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Still unavailable"));
    expect(mocks.changeStaffDepartment).toHaveBeenCalledTimes(1);
    expect(mocks.router.push).not.toHaveBeenCalled();
    submit();
    await waitFor(() => expect(mocks.router.push).toHaveBeenCalled());
    expect(mocks.changeStaffDepartment).toHaveBeenCalledTimes(1);
    expect(mocks.updateStaff).toHaveBeenCalledTimes(1);
  });

  it("does not reverse a concurrent department move when only the profile was edited", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture, targetDepartment]);
    mocks.staffMember.mockResolvedValue(staffContractFixture);
    mocks.updateStaff.mockResolvedValue({ ...staffContractFixture, departmentId: targetDepartment.departmentId });
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    await screen.findByDisplayValue("Nguyen Van A");
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "Updated Name" } });
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    await waitFor(() => expect(mocks.router.push).toHaveBeenCalled());
    expect(mocks.changeStaffDepartment).not.toHaveBeenCalled();
  });

  it("keeps an inactive current department when editing a staff profile", async () => {
    mocks.departments.mockResolvedValue([{ ...departmentContractFixture, active: false }, targetDepartment]);
    mocks.staffMember.mockResolvedValue(staffContractFixture);
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    await screen.findByDisplayValue("Nguyen Van A");
    expect(mocks.departments).toHaveBeenCalledWith(false);
    expect(screen.getByLabelText(/Đơn vị công tác/)).toHaveValue(staffContractFixture.departmentId);
  });

  it("uses the saved profile's authoritative department to avoid a redundant transfer", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture, targetDepartment]);
    mocks.staffMember.mockResolvedValue(staffContractFixture);
    mocks.updateStaff.mockResolvedValue({ ...staffContractFixture, departmentId: targetDepartment.departmentId });
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    await screen.findByDisplayValue("Nguyen Van A");
    fireEvent.change(screen.getByLabelText(/Đơn vị công tác/), { target: { value: targetDepartment.departmentId } });
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    await waitFor(() => expect(mocks.router.push).toHaveBeenCalled());
    expect(mocks.changeStaffDepartment).not.toHaveBeenCalled();
  });

  it("does not transfer or navigate after switching staff during a pending profile save", async () => {
    let resolve!: (staff: StaffDTO) => void;
    mocks.departments.mockResolvedValue([departmentContractFixture, targetDepartment]);
    mocks.staffMember.mockResolvedValue(staffContractFixture);
    mocks.updateStaff.mockReturnValue(new Promise<StaffDTO>((done) => { resolve = done; }));
    const view = render(<StaffForm staffId={staffContractFixture.staffId} />);
    await screen.findByDisplayValue("Nguyen Van A");
    fireEvent.change(screen.getByLabelText(/Đơn vị công tác/), { target: { value: targetDepartment.departmentId } });
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    view.rerender(<StaffForm staffId="00000000-0000-4000-8000-000000000099" />);
    await act(async () => { resolve(staffContractFixture); });
    expect(mocks.changeStaffDepartment).not.toHaveBeenCalled();
    expect(mocks.router.push).not.toHaveBeenCalled();
  });

  it("redirects on a 401 while reconciling a failed transfer after transport clears the token", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture, targetDepartment]);
    mocks.staffMember.mockResolvedValueOnce(staffContractFixture).mockImplementationOnce(async () => {
      mocks.getSessionKey.mockReturnValue(null);
      throw new ApiRequestError("Expired", 401);
    });
    mocks.updateStaff.mockResolvedValue(staffContractFixture);
    mocks.changeStaffDepartment.mockRejectedValue(new Error("Lost response"));
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    await screen.findByDisplayValue("Nguyen Van A");
    fireEvent.change(screen.getByLabelText(/Đơn vị công tác/), { target: { value: targetDepartment.departmentId } });
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
    expect(mocks.router.push).not.toHaveBeenCalled();
  });

  beforeEach(() => {
    vi.stubGlobal("crypto", webcrypto);
    sessionStorage.clear();
    mocks.departments.mockReset();
    mocks.staffMember.mockReset();
    mocks.createStaff.mockReset();
    mocks.updateStaff.mockReset();
    mocks.changeStaffDepartment.mockReset();
    mocks.getSessionKey.mockReturnValue("session-a");
    mocks.router.back.mockReset();
    mocks.router.push.mockReset();
    mocks.router.replace.mockReset();
  });

  it.each(["", "12345678901", "1234567890123", "01234567890A"])("rejects invalid CCCD before creating: %s", async (identity) => {
    mocks.departments.mockResolvedValue([departmentContractFixture]);
    render(<StaffForm />);
    await screen.findByLabelText(/CCCD/);
    fireEvent.change(screen.getByLabelText(/Số giấy phép/), { target: { value: "LIC-001" } });
    fireEvent.change(screen.getByLabelText(/CCCD/), { target: { value: identity } });
    fireEvent.submit(screen.getByRole("button", { name: "Tạo nhân sự" }).closest("form")!);
    expect(await screen.findByRole("alert")).toHaveTextContent("CCCD phải gồm đúng 12 chữ số");
    expect(mocks.createStaff).not.toHaveBeenCalled();
  });

  it("allows a different CCCD while an earlier staff creation is unresolved", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture]);
    mocks.createStaff.mockRejectedValue(new Error("Lost response"));
    const view = render(<StaffForm />);
    async function submit(identity: string, name: string) {
      await screen.findByLabelText(/CCCD/);
    fireEvent.change(screen.getByLabelText(/Số giấy phép/), { target: { value: "LIC-001" } });
      fireEvent.change(screen.getByLabelText(/CCCD/), { target: { value: identity } });
      fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: name } });
      fireEvent.change(screen.getByLabelText(/Đơn vị công tác/), { target: { value: departmentContractFixture.departmentId } });
      fireEvent.submit(screen.getByRole("button", { name: "Tạo nhân sự" }).closest("form")!);
      await screen.findByRole("alert");
    }
    await submit("012345678901", "Staff A");
    const firstKey = mocks.createStaff.mock.calls[0][1];
    view.unmount();
    const secondView = render(<StaffForm />);
    await submit("012345678902", "Staff B");
    expect(mocks.createStaff.mock.calls[1][1]).not.toBe(firstKey);
    secondView.unmount();
    render(<StaffForm />);
    await submit("012345678901", "Staff A");
    expect(mocks.createStaff.mock.calls[2][1]).toBe(firstKey);
  });

  it("shows a clear duplicate identity error and keeps the draft", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture]);
    mocks.createStaff.mockRejectedValue(new ApiRequestError("Duplicate", 409, "STAFF_IDENTITY_DUPLICATE"));
    render(<StaffForm />);
    await screen.findByLabelText(/CCCD/);
    fireEvent.change(screen.getByLabelText(/Số giấy phép/), { target: { value: "LIC-001" } });
    fireEvent.change(screen.getByLabelText(/CCCD/), { target: { value: "012345678901" } });
    fireEvent.submit(screen.getByRole("button", { name: "Tạo nhân sự" }).closest("form")!);
    expect(await screen.findByRole("alert")).toHaveTextContent("CCCD này đã có hồ sơ nhân sự");
    expect(screen.getByLabelText(/CCCD/)).toHaveValue("012345678901");
    expect(mocks.router.push).not.toHaveBeenCalled();
  });

  it("keeps legacy identity optional and immutable when editing", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture]);
    mocks.staffMember.mockResolvedValue({ ...staffContractFixture, identityNumber: null });
    mocks.updateStaff.mockResolvedValue({ ...staffContractFixture, identityNumber: null });
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    const identity = await screen.findByLabelText(/CCCD/);
    fireEvent.change(screen.getByLabelText(/Số giấy phép/), { target: { value: "LIC-001" } });
    expect(identity).toHaveAttribute("readonly");
    expect(identity).not.toBeRequired();
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    await waitFor(() => expect(mocks.router.push).toHaveBeenCalled());
    expect(mocks.updateStaff.mock.calls[0][1]).not.toHaveProperty("identityNumber");
  });

  it("reuses the key after an ambiguous retry and refuses changed content until reconciliation", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture]);
    mocks.createStaff.mockRejectedValue(new Error("Network interrupted"));
    render(<StaffForm />);
    await screen.findByLabelText(/Họ tên/);
    fireEvent.change(screen.getByLabelText(/Số giấy phép/), { target: { value: "LIC-001" } });
    fireEvent.change(screen.getByLabelText(/CCCD/), { target: { value: "012345678901" } });
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "Nguyen Van A" } });
    fireEvent.change(screen.getByLabelText(/Đơn vị công tác/), { target: { value: departmentContractFixture.departmentId } });
    const submit = () => fireEvent.submit(screen.getByRole("button", { name: "Tạo nhân sự" }).closest("form")!);
    submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("Chưa xác nhận được kết quả tạo nhân sự");
    const firstKey = mocks.createStaff.mock.calls[0][1];
    expect(firstKey).toEqual(expect.any(String));
    submit();
    await waitFor(() => expect(mocks.createStaff).toHaveBeenCalledTimes(2));
    await screen.findByRole("alert");
    expect(mocks.createStaff.mock.calls[1][1]).toBe(firstKey);
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "Nguyen Van B" } });
    submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("Lệnh tạo trước chưa được xác nhận");
    expect(mocks.createStaff).toHaveBeenCalledTimes(2);
  });

  it("recovers the same creation key after the form unmounts", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture]);
    mocks.createStaff.mockRejectedValueOnce(new Error("Lost response")).mockResolvedValueOnce(staffContractFixture);
    const workspace = render(<StaffForm />);
    async function submit() {
      await screen.findByLabelText(/Họ tên/);
    fireEvent.change(screen.getByLabelText(/Số giấy phép/), { target: { value: "LIC-001" } });
    fireEvent.change(screen.getByLabelText(/CCCD/), { target: { value: "012345678901" } });
      fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "Nguyen Van A" } });
      fireEvent.change(screen.getByLabelText(/Đơn vị công tác/), { target: { value: departmentContractFixture.departmentId } });
      fireEvent.submit(screen.getByRole("button", { name: "Tạo nhân sự" }).closest("form")!);
    }
    await submit();
    await screen.findByRole("alert");
    const key = mocks.createStaff.mock.calls[0][1];
    workspace.unmount();
    render(<StaffForm />);
    await submit();
    await waitFor(() => expect(mocks.router.push).toHaveBeenCalled());
    expect(mocks.createStaff.mock.calls[1][1]).toBe(key);
    expect(sessionStorage.length).toBe(0);
    // Navigation may not unmount immediately; a completed command stays locked.
    const completedButton = screen.getByRole("button", { name: "Đang xử lý…" });
    expect(completedButton).toBeDisabled();
    fireEvent.submit(completedButton.closest("form")!);
    expect(mocks.createStaff).toHaveBeenCalledTimes(2);
  });

  it("locks every draft field throughout profile save and department transfer", async () => {
    let resolveProfile!: (staff: StaffDTO) => void;
    let rejectTransfer!: (cause: Error) => void;
    mocks.departments.mockResolvedValue([departmentContractFixture, targetDepartment]);
    mocks.staffMember.mockResolvedValue(staffContractFixture);
    mocks.updateStaff.mockReturnValue(new Promise((resolve) => { resolveProfile = resolve; }));
    mocks.changeStaffDepartment.mockReturnValue(new Promise((_, reject) => { rejectTransfer = reject; }));
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    await screen.findByDisplayValue("Nguyen Van A");
    fireEvent.change(screen.getByLabelText(/Đơn vị công tác/), { target: { value: targetDepartment.departmentId } });
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    for (const label of [/Họ tên/, /Đơn vị công tác/, /Chức danh/, /Chuyên môn/, /Số giấy phép/, /Số điện thoại/, /Email/]) {
      expect(screen.getByLabelText(label)).toBeDisabled();
    }
    await act(async () => { resolveProfile(staffContractFixture); });
    expect(screen.getByLabelText(/Email/)).toBeDisabled();
    await act(async () => { rejectTransfer(new Error("Transfer refused")); });
    await screen.findByRole("alert");
    expect(screen.getByLabelText(/Email/)).toBeEnabled();
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

  it("loads new personal fields and submits normalized values", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture]);
    mocks.staffMember.mockResolvedValue({ ...staffContractFixture, dateOfBirth: "1990-01-02", gender: "FEMALE", address: "Address", hireDate: "2020-03-01", qualification: "Degree" });
    mocks.updateStaff.mockResolvedValue(staffContractFixture);
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    expect(await screen.findByDisplayValue("Address")).toBeVisible();
    expect(screen.getByLabelText("Ngày sinh")).toHaveValue("1990-01-02");
    fireEvent.change(screen.getByLabelText("Địa chỉ liên hệ"), { target: { value: " New address " } });
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    await waitFor(() => expect(mocks.updateStaff).toHaveBeenCalledWith(staffContractFixture.staffId, expect.objectContaining({ profile: { dateOfBirth: "1990-01-02", gender: "FEMALE", address: "New address", hireDate: "2020-03-01", qualification: "Degree" } })));
  });

  it("creates a staff profile with explicit nullable optional fields", async () => {
    mocks.departments.mockResolvedValue([departmentContractFixture]);
    mocks.createStaff.mockResolvedValue(staffContractFixture);
    render(<StaffForm />);
    await screen.findByLabelText(/Họ tên/);
    fireEvent.change(screen.getByLabelText(/Số giấy phép/), { target: { value: "LIC-001" } });
    fireEvent.change(screen.getByLabelText(/CCCD/), { target: { value: "012345678901" } });
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: " Nguyen Van A " } });
    fireEvent.change(screen.getByLabelText(/Đơn vị công tác/), { target: { value: departmentContractFixture.departmentId } });
    fireEvent.submit(screen.getByRole("button", { name: "Tạo nhân sự" }).closest("form")!);

    await waitFor(() => expect(mocks.createStaff).toHaveBeenCalledWith({
      identityNumber: "012345678901", profile: { dateOfBirth: null, gender: null, address: null, hireDate: null, qualification: null },
      fullName: "Nguyen Van A",
      departmentId: departmentContractFixture.departmentId,
      jobTitle: "DOCTOR",
      specialization: null,
      licenseNumber: "LIC-001",
      phoneNumber: null,
      email: null,
    }, expect.any(String)));
    expect(mocks.router.push).toHaveBeenCalledWith(
      `/organization/staff/${staffContractFixture.staffId}?notice=created`,
    );
  });

  it("preserves and reports a saved profile when a department transfer fails, then safely retries it", async () => {
    const profileSaved: StaffDTO = { ...staffContractFixture, fullName: "Updated Name" };
    const currentDepartment: StaffDTO = { ...profileSaved, departmentId: departmentContractFixture.departmentId };
    const movedStaff: StaffDTO = { ...profileSaved, departmentId: targetDepartment.departmentId };
    mocks.departments.mockResolvedValue([departmentContractFixture, targetDepartment]);
    mocks.staffMember.mockResolvedValueOnce(staffContractFixture).mockResolvedValue(currentDepartment);
    mocks.updateStaff.mockResolvedValue(profileSaved);
    mocks.changeStaffDepartment
      .mockRejectedValueOnce(new Error("DEPARTMENT_HEAD_INVALID: cần gỡ trưởng khoa trước"))
      .mockResolvedValueOnce(movedStaff);
    render(<StaffForm staffId={staffContractFixture.staffId} />);
    await screen.findByDisplayValue("Nguyen Van A");
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "Updated Name" } });
    fireEvent.change(screen.getByLabelText(/Đơn vị công tác/), { target: { value: targetDepartment.departmentId } });
    const submit = () => fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);

    submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("Thông tin nhân sự đã được lưu");
    expect(screen.getByLabelText(/Họ tên/)).toHaveValue("Updated Name");
    expect(screen.getByLabelText(/Đơn vị công tác/)).toHaveValue(targetDepartment.departmentId);
    expect(mocks.staffMember).toHaveBeenCalledTimes(2);

    submit();
    await waitFor(() => expect(mocks.router.push).toHaveBeenCalledWith(
      `/organization/staff/${staffContractFixture.staffId}?notice=updated`,
    ));
    expect(mocks.updateStaff).toHaveBeenCalledTimes(1);
    expect(mocks.staffMember).toHaveBeenCalledTimes(3);
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
    fireEvent.change(screen.getByLabelText(/Đơn vị công tác/), { target: { value: targetDepartment.departmentId } });
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);
    await waitFor(() => expect(mocks.updateStaff).toHaveBeenCalledTimes(1));

    mocks.getSessionKey.mockReturnValue("session-b");
    await act(async () => { resolve({ ...staffContractFixture, fullName: "Updated Name" }); await request; });
    expect(mocks.changeStaffDepartment).not.toHaveBeenCalled();
    expect(mocks.router.push).not.toHaveBeenCalled();
  });
});
