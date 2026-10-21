import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
const mocks = vi.hoisted(() => ({ change: vi.fn(), reset: vi.fn(), logout: vi.fn(), replace: vi.fn(), getSessionKey: vi.fn(() => "a") }));
vi.mock("../api", () => ({ organizationApi: { changePassword: mocks.change, resetPassword: mocks.reset } }));
vi.mock("@/lib/auth", () => ({ getSessionKey: mocks.getSessionKey, logout: mocks.logout }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: mocks.replace }) }));
import { PasswordForm } from "./PasswordForm";
beforeEach(() => { vi.clearAllMocks(); mocks.getSessionKey.mockReturnValue("a"); vi.spyOn(window, "confirm").mockReturnValue(true); });
function fill() {
  fireEvent.change(screen.getByLabelText(/^Mật khẩu mới/), { target: { value: "new-password" } });
  fireEvent.change(screen.getByLabelText(/^Xác nhận/), { target: { value: "new-password" } });
}
it("serializes password changes and signs out only after confirmed success", async () => {
  let resolve!: () => void;
  mocks.change.mockReturnValue(new Promise<void>(done => { resolve = done; }));
  render(<PasswordForm />); fill();
  fireEvent.change(screen.getByLabelText(/^Mật khẩu hiện tại/), { target: { value: "old-password" } });
  const form = screen.getByRole("button", { name: "Đổi mật khẩu" }).closest("form")!;
  fireEvent.submit(form); fireEvent.submit(form);
  expect(mocks.change).toHaveBeenCalledTimes(1);
  expect(mocks.logout).not.toHaveBeenCalled();
  await act(async () => resolve());
  expect(mocks.logout).toHaveBeenCalledTimes(1);
  expect(mocks.replace).toHaveBeenCalledWith("/login");
});
it("cannot apply a late password-reset result to another selected account", async () => {
  let resolve!: () => void;
  mocks.reset.mockReturnValue(new Promise<void>(done => { resolve = done; }));
  const view = render(<PasswordForm accountId="account-a" username="A" />); fill();
  fireEvent.submit(screen.getByRole("button", { name: "Đặt lại mật khẩu" }).closest("form")!);
  view.rerender(<PasswordForm accountId="account-b" username="B" />);
  await act(async () => resolve());
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "Đặt lại mật khẩu: B" })).toBeVisible();
  await waitFor(() => expect(mocks.reset).toHaveBeenCalledWith("account-a", "new-password"));
});
