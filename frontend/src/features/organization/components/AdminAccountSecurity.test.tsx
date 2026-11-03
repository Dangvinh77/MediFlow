import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { webcrypto } from "node:crypto";
import { ApiRequestError } from "@/lib/api";
const mocks = vi.hoisted(() => ({ account: vi.fn(), resetPassword: vi.fn(), mailDelivery: vi.fn(), retryMail: vi.fn(), updateAccountEmail: vi.fn(), session: "a" }));
vi.mock("../api", () => ({ organizationApi: mocks }));
vi.mock("@/lib/auth", () => ({ getSessionKey: () => mocks.session }));
import { AdminAccountSecurity } from "./AdminAccountSecurity";
beforeEach(() => {
  vi.clearAllMocks(); sessionStorage.clear(); mocks.session = "a";
  vi.stubGlobal("crypto", webcrypto); vi.spyOn(window, "confirm").mockReturnValue(true);
  mocks.mailDelivery.mockResolvedValue({ messageId: "mail-1", status: "FAILED" });
});
afterEach(() => vi.unstubAllGlobals());
it.each([new Error("Lost response"), new ApiRequestError("Malformed response", 200)])("reconciles an uncertain email write before allowing another security change (%s)", async cause => {
  const saved = vi.fn();
  mocks.updateAccountEmail.mockRejectedValueOnce(cause);
  mocks.account.mockResolvedValue({ username: "corrected@example.com" });
  render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" onEmailChanged={saved} />);
  fireEvent.change(screen.getByLabelText("Email đăng nhập mới"), { target: { value: "corrected@example.com" } });
  fireEvent.change(screen.getByLabelText("Lý do thay đổi email"), { target: { value: "Đã xác minh" } });
  fireEvent.submit(screen.getByRole("button", { name: "Lưu email mới" }).closest("form")!);
  await screen.findByRole("alert");
  expect(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "Kiểm tra kết quả đổi email" }));
  await waitFor(() => expect(saved).toHaveBeenCalledOnce());
  expect(mocks.updateAccountEmail).toHaveBeenCalledTimes(1);
});
it("retries the same mail without issuing another password", async () => {
  mocks.retryMail.mockResolvedValue({ messageId: "mail-1", status: "PENDING" });
  render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" />);
  fireEvent.click(await screen.findByRole("button", { name: "Gửi lại thư" }));
  await waitFor(() => expect(mocks.retryMail).toHaveBeenCalledExactlyOnceWith("account-a", "mail-1"));
  expect(mocks.resetPassword).not.toHaveBeenCalled();
});
it("keeps the same reset intent across timeout and remount and never asks for plaintext passwords", async () => {
  mocks.resetPassword.mockRejectedValueOnce(new Error("Lost response")).mockResolvedValueOnce({ messageId: "mail-2", status: "PENDING" });
  const view = render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" />);
  expect(screen.queryByLabelText(/Mật khẩu mới/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" }));
  await screen.findByRole("alert"); const key = mocks.resetPassword.mock.calls[0][1];
  view.unmount(); render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" />);
  fireEvent.click(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" }));
  await waitFor(() => expect(mocks.resetPassword).toHaveBeenCalledTimes(2));
  expect(mocks.resetPassword.mock.calls[1][1]).toBe(key);
});
it("sends the audited email correction and ignores old-session completion", async () => {
  let resolve!: () => void; const saved = vi.fn();
  mocks.updateAccountEmail.mockReturnValue(new Promise<void>(done => { resolve = done; }));
  render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" onEmailChanged={saved} />);
  fireEvent.change(screen.getByLabelText("Email đăng nhập mới"), { target: { value: "corrected@example.com" } });
  fireEvent.change(screen.getByLabelText("Lý do thay đổi email"), { target: { value: "Đã xác minh lại email" } });
  fireEvent.submit(screen.getByRole("button", { name: "Lưu email mới" }).closest("form")!);
  expect(mocks.updateAccountEmail).toHaveBeenCalledWith("account-a", "corrected@example.com", "Đã xác minh lại email");
  mocks.session = "b"; await act(async () => resolve()); expect(saved).not.toHaveBeenCalled();
});
