import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { clearSession, storeSession } from "@/lib/session";
const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/components/theme/ThemeToggle", () => ({ ThemeToggle: () => null }));
import LoginPage from "./page";
afterEach(() => { act(() => clearSession()); vi.clearAllMocks(); });
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
    expect(screen.getByLabelText("Tài khoản")).toHaveValue("");
    expect(screen.getByLabelText("Mật khẩu")).toHaveValue("");
  });
  it.each([ ["PATIENT", "/notifications"], ["CASHIER", "/billing"], ["LAB_TECH", "/lab"] ] as const)("opens an accessible landing page for %s", async (role, destination) => {
    act(() => storeSession({ accessToken: "test", refreshToken: "r", role }));
    render(<LoginPage />);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith(destination));
  });
});
