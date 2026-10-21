import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { webcrypto } from "node:crypto";
import { accountContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({ createAccount: vi.fn(), updateAccountStatus: vi.fn(), getToken: vi.fn<() => string | null>(() => "session-a"), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ organizationApi: mocks }));
vi.mock("@/lib/auth", () => ({ getToken: mocks.getToken }));

import { ApiRequestError } from "@/lib/api";

import { AccountAdmin } from "./AccountAdmin";

describe("AccountAdmin command forms", () => {
  afterEach(() => vi.unstubAllGlobals());
  it("shares a synchronous submit lock between creation and status commands", async () => {
    let reject!: (cause: Error) => void;
    mocks.createAccount.mockReturnValue(new Promise((_, fail) => { reject = fail; }));
    mocks.updateAccountStatus.mockResolvedValue(accountContractFixture);
    render(<AccountAdmin />);
    fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: accountContractFixture.patientId } });
    fireEvent.change(screen.getByLabelText(/Mã tài khoản/), { target: { value: accountContractFixture.accountId } });
    const create = screen.getByRole("button", { name: "Tạo tài khoản" }).closest("form")!;
    const status = screen.getByRole("button", { name: "Cập nhật trạng thái" }).closest("form")!;
    act(() => { fireEvent.submit(create); fireEvent.submit(create); fireEvent.submit(status); });
    await waitFor(() => expect(mocks.createAccount).toHaveBeenCalledTimes(1));
    expect(mocks.updateAccountStatus).not.toHaveBeenCalled();
    await act(async () => { reject(new Error("Unavailable")); });
    fireEvent.submit(status);
    await waitFor(() => expect(mocks.updateAccountStatus).toHaveBeenCalledTimes(1));
  });

  it.each([null, "session-b"])("handles account status 401 for session %s", async (token) => {
    mocks.updateAccountStatus.mockImplementation(async () => {
      mocks.getToken.mockReturnValue(token);
      throw new ApiRequestError("Expired", 401);
    });
    render(<AccountAdmin />);
    fireEvent.change(screen.getByLabelText(/Mã tài khoản/), { target: { value: accountContractFixture.accountId } });
    fireEvent.submit(screen.getByRole("button", { name: "Cập nhật trạng thái" }).closest("form")!);
    await waitFor(() => expect(mocks.updateAccountStatus).toHaveBeenCalledTimes(1));
    if (token === null) expect(mocks.router.replace).toHaveBeenCalledWith("/login");
    else expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  beforeEach(() => {
    vi.stubGlobal("crypto", webcrypto);
    sessionStorage.clear();
    mocks.router.replace.mockReset();
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
    }, expect.any(String)));
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

  it("recovers an account after losing its creation response using the original key", async () => {
    mocks.createAccount.mockRejectedValueOnce(new Error("Lost response")).mockResolvedValueOnce(accountContractFixture);
    const workspace = render(<AccountAdmin />);
    function submit() {
      fireEvent.change(screen.getByLabelText(/Tên đăng nhập/), { target: { value: "patient.demo" } });
      fireEvent.change(screen.getByLabelText(/Mật khẩu/), { target: { value: "secret-password" } });
      fireEvent.change(screen.getByLabelText(/Vai trò/), { target: { value: "PATIENT" } });
      fireEvent.change(screen.getByLabelText(/Mã bệnh nhân/), { target: { value: accountContractFixture.patientId } });
      fireEvent.submit(screen.getByRole("button", { name: "Tạo tài khoản" }).closest("form")!);
    }
    submit();
    expect(screen.getByLabelText(/Mật khẩu/)).toBeDisabled();
    expect(await screen.findByRole("alert")).toHaveTextContent("Chưa xác nhận được kết quả");
    const key = mocks.createAccount.mock.calls[0][1];
    expect(JSON.stringify(sessionStorage)).not.toContain("secret-password");
    workspace.unmount();
    render(<AccountAdmin />);
    submit();
    expect(await screen.findByRole("status")).toHaveTextContent(accountContractFixture.accountId);
    expect(mocks.createAccount.mock.calls[1][1]).toBe(key);
    expect(sessionStorage.length).toBe(0);
  });
});
