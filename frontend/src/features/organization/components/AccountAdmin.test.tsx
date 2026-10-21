import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { accountContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({ createAccount: vi.fn(), updateAccountStatus: vi.fn(), getToken: vi.fn(() => "session-a") }));
vi.mock("../api", () => ({ organizationApi: mocks }));
vi.mock("@/lib/auth", () => ({ getToken: mocks.getToken }));

import { AccountAdmin } from "./AccountAdmin";

describe("AccountAdmin command forms", () => {
  beforeEach(() => {
    mocks.createAccount.mockReset();
    mocks.updateAccountStatus.mockReset();
    mocks.getToken.mockReturnValue("session-a");
  });

  it("creates a patient account linked to the submitted patient UUID and resets the password", async () => {
    mocks.createAccount.mockResolvedValue(accountContractFixture);
    render(<AccountAdmin />);
    fireEvent.change(screen.getByLabelText(/Tên đăng nhập/), { target: { value: " patient.demo " } });
    fireEvent.change(screen.getByLabelText(/Mật khẩu/), { target: { value: "correct-horse-battery" } });
    fireEvent.change(screen.getByLabelText(/Vai trò/), { target: { value: "PATIENT" } });
    fireEvent.change(screen.getByLabelText(/Mã bệnh nhân/), { target: { value: accountContractFixture.patientId } });
    fireEvent.submit(screen.getByRole("button", { name: "Tạo tài khoản" }).closest("form")!);

    await waitFor(() => expect(mocks.createAccount).toHaveBeenCalledWith({
      username: "patient.demo",
      password: "correct-horse-battery",
      role: "PATIENT",
      staffId: null,
      patientId: accountContractFixture.patientId,
    }));
    expect(await screen.findByRole("status")).toHaveTextContent("Đã cập nhật tài khoản");
    expect(screen.getByLabelText(/Mật khẩu/)).toHaveValue("");
    expect(screen.getByText(accountContractFixture.accountId)).toBeVisible();
  });

  it("validates UUIDs before sending and submits account status with isActive", async () => {
    render(<AccountAdmin />);
    fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: "bad-id" } });
    fireEvent.submit(screen.getByRole("button", { name: "Tạo tài khoản" }).closest("form")!);
    expect(await screen.findByRole("alert")).toHaveTextContent("UUID hợp lệ");
    expect(mocks.createAccount).not.toHaveBeenCalled();

    mocks.updateAccountStatus.mockResolvedValue({ ...accountContractFixture, isActive: false });
    fireEvent.change(screen.getByLabelText(/Mã tài khoản/), { target: { value: accountContractFixture.accountId } });
    fireEvent.change(screen.getByLabelText(/Trạng thái/), { target: { value: "false" } });
    fireEvent.submit(screen.getByRole("button", { name: "Cập nhật trạng thái" }).closest("form")!);
    await waitFor(() => expect(mocks.updateAccountStatus).toHaveBeenCalledWith(accountContractFixture.accountId, false));
    expect(await screen.findByRole("status")).toHaveTextContent("Đã khóa");
  });

  it("does not show an old account-creation result after the signed-in account changes", async () => {
    let resolve!: (account: typeof accountContractFixture) => void;
    const request = new Promise<typeof accountContractFixture>((done) => { resolve = done; });
    mocks.createAccount.mockReturnValue(request);
    render(<AccountAdmin />);
    fireEvent.change(screen.getByLabelText(/Tên đăng nhập/), { target: { value: "patient.demo" } });
    fireEvent.change(screen.getByLabelText(/Mật khẩu/), { target: { value: "correct-horse-battery" } });
    fireEvent.change(screen.getByLabelText(/Vai trò/), { target: { value: "PATIENT" } });
    fireEvent.change(screen.getByLabelText(/Mã bệnh nhân/), { target: { value: accountContractFixture.patientId } });
    fireEvent.submit(screen.getByRole("button", { name: "Tạo tài khoản" }).closest("form")!);
    await waitFor(() => expect(mocks.createAccount).toHaveBeenCalledTimes(1));

    mocks.getToken.mockReturnValue("session-b");
    await act(async () => { resolve(accountContractFixture); await request; });
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });
});
