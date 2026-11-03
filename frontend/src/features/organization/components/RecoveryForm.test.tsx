import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
const mocks = vi.hoisted(() => ({ forgot: vi.fn(), reset: vi.fn() }));
vi.mock("../security-api", () => ({ accountSecurityApi: { forgot: mocks.forgot, reset: mocks.reset } }));
import { RecoveryForm } from "./RecoveryForm";
beforeEach(() => vi.clearAllMocks());
it("acknowledges a forgotten-password request without exposing whether an account exists", async () => {
  mocks.forgot.mockResolvedValue(true);
  render(<RecoveryForm />);
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "patient@example.com" } });
  fireEvent.submit(screen.getByRole("button", { name: "Gửi link đặt lại mật khẩu" }).closest("form")!);
  await waitFor(() => expect(mocks.forgot).toHaveBeenCalledWith("patient@example.com"));
  expect(await screen.findByRole("status")).toHaveTextContent("Nếu email");
});
it("rejects password mismatch without consuming the reset token", () => {
  render(<RecoveryForm token="one-use-token" />);
  fireEvent.change(screen.getByLabelText("Mật khẩu mới"), { target: { value: "long-password" } });
  fireEvent.change(screen.getByLabelText("Xác nhận mật khẩu"), { target: { value: "different-password" } });
  fireEvent.submit(screen.getByRole("button", { name: "Lưu mật khẩu mới" }).closest("form")!);
  expect(screen.getByRole("alert")).toHaveTextContent("không khớp");
  expect(mocks.reset).not.toHaveBeenCalled();
});
it("passes the opaque token once and does not log in automatically", async () => {
  mocks.reset.mockResolvedValue(true);
  render(<RecoveryForm token="one-use-token" />);
  for (const label of ["Mật khẩu mới", "Xác nhận mật khẩu"]) fireEvent.change(screen.getByLabelText(label), { target: { value: "long-password" } });
  const form = screen.getByRole("button", { name: "Lưu mật khẩu mới" }).closest("form")!;
  fireEvent.submit(form); fireEvent.submit(form);
  await waitFor(() => expect(mocks.reset).toHaveBeenCalledExactlyOnceWith("one-use-token", "long-password"));
  expect(await screen.findByRole("status")).toHaveTextContent("Đã đổi mật khẩu");
  expect(screen.getByRole("link", { name: "Đăng nhập" })).toHaveAttribute("href", "/login");
});
