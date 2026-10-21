import { act, render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { clearSession, storeSession } from "@/lib/session";
const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/components/theme/ThemeToggle", () => ({ ThemeToggle: () => null }));
import LoginPage from "./page";
afterEach(() => { act(() => clearSession()); vi.clearAllMocks(); });
describe("role-aware login landing", () => {
  it.each([ ["PATIENT", "/notifications"], ["CASHIER", "/billing"], ["LAB_TECH", "/lab"] ] as const)("opens an accessible landing page for %s", async (role, destination) => {
    act(() => storeSession({ accessToken: "test", refreshToken: "r", role }));
    render(<LoginPage />);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith(destination));
  });
});
