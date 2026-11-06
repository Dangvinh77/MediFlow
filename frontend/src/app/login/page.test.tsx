import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiRequestError } from "@/lib/api";
import { clearSession, storeSession } from "@/lib/session";
const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/components/theme/ThemeToggle", () => ({ ThemeToggle: () => null }));
import LoginPage from "./page";
afterEach(() => { act(() => clearSession()); vi.clearAllMocks(); vi.unstubAllEnvs(); });
describe("role-aware login landing", () => {
  it.each(["PATIENT", "ADMIN", "MANAGER", "NURSE", "DOCTOR", "CASHIER", "LAB_TECH", "PHARMACIST"] as const)("forces %s with the initial password to change it after reopening login", async role => {
    const accessToken = `header.${btoa(JSON.stringify({ mustChangePassword: true }))}.signature`;
    act(() => storeSession({ accessToken, refreshToken: "r", role }));
    const view = render(<LoginPage />);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/organization/security"));
    view.unmount(); router.replace.mockClear();
    render(<LoginPage />);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/organization/security"));
  });
  it("starts without demo credentials", () => {
    localStorage.clear();
    render(<LoginPage />);
    expect(screen.getByLabelText("Email")).toHaveValue("");
    expect(screen.getByLabelText("Mật khẩu")).toHaveValue("");
  });
  it.each([ ["PATIENT", "/notifications"], ["CASHIER", "/billing/payment-requests"], ["LAB_TECH", "/lab"] ] as const)("opens an accessible landing page for %s", async (role, destination) => {
    act(() => storeSession({ accessToken: "test", refreshToken: "r", role }));
    render(<LoginPage />);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith(destination));
  });
});


it.each([
  ["DOCTOR", "/appointments/my-calendar"], ["NURSE", "/appointments/reception"],
  ["CASHIER", "/billing/scheduled-payments"], ["MANAGER", "/appointments/schedule"],
  ["PATIENT", "/appointments/my-registration"], ["ADMIN", "/organization"],
] as const)("opens the current work queue for %s when scheduling is enabled", async (role, destination) => {
  vi.stubEnv("MEDIFLOW_SCHEDULING_UI_ENABLED", "true");
  act(() => storeSession({ accessToken: "test", refreshToken: "r", role }));
  render(<LoginPage />);
  await waitFor(() => expect(router.replace).toHaveBeenCalledWith(destination));
});

describe("full-screen sign-in loader", () => {
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });
  function submit() {
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "lab@mediflow.test" } });
    fireEvent.change(screen.getByLabelText("Mật khẩu"), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));
  }
  it("holds the loader for one second before entering the system", async () => {
    vi.useFakeTimers();
    vi.spyOn(api, "postRaw").mockResolvedValue({ accessToken: "test", refreshToken: "r", role: "LAB_TECH" });
    submit();
    await act(() => vi.advanceTimersByTimeAsync(0));
    const loader = screen.getByRole("status", { name: "Đang vào hệ thống…" });
    expect(loader.parentElement).toHaveClass("fixed", "inset-0", "items-center", "justify-center");
    await act(() => vi.advanceTimersByTimeAsync(999));
    expect(router.replace).not.toHaveBeenCalled();
    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(router.replace).toHaveBeenCalledWith("/lab");
  });
  it("returns to the form without a hold when sign-in fails", async () => {
    vi.spyOn(api, "postRaw").mockRejectedValue(new ApiRequestError("Unauthorized", 401));
    submit();
    expect(await screen.findByText("Đăng nhập thất bại — sai tài khoản hoặc mật khẩu.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đăng nhập" })).toBeEnabled();
    expect(router.replace).not.toHaveBeenCalled();
  });
});
