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
it("cannot apply a late password-change result after the session changes", async () => {
  let resolve!: () => void;
  mocks.change.mockReturnValue(new Promise<void>(done => { resolve = done; }));
  render(<PasswordForm />); fill();
  fireEvent.change(screen.getByLabelText(/^Mật khẩu hiện tại/), { target: { value: "old-password" } });
  fireEvent.submit(screen.getByRole("button", { name: "Đổi mật khẩu" }).closest("form")!);
  mocks.getSessionKey.mockReturnValue("b");
  await act(async () => resolve());
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
  expect(mocks.logout).not.toHaveBeenCalled();
  await waitFor(() => expect(mocks.change).toHaveBeenCalledWith("old-password", "new-password"));
});
