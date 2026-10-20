import { act, fireEvent, render, screen } from "@testing-library/react";
import { useState } from "react";
import { renderToString } from "react-dom/server";
import { beforeEach, expect, it, vi } from "vitest";
import { clearSession, storeSession } from "@/lib/session";
import DashboardLayout from "./layout";

const router = vi.hoisted(() => ({ replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("@/components/layout/DashboardSidebar", () => ({ DashboardSidebar: () => null }));
vi.mock("@/components/layout/DashboardHeader", () => ({ DashboardHeader: () => null }));

function PrivateDraft() {
  const [draft, setDraft] = useState("");
  return <input aria-label="Private draft" value={draft} onChange={(event) => setDraft(event.target.value)} />;
}

beforeEach(() => { clearSession(); router.replace.mockClear(); });

it("hydrates a valid persisted session without redirecting it to login", async () => {
  storeSession({ accessToken: "valid", refreshToken: "refresh", role: "ADMIN" });
  const container = document.createElement("div");
  document.body.appendChild(container);
  container.innerHTML = renderToString(<DashboardLayout><PrivateDraft /></DashboardLayout>);
  await act(async () => render(<DashboardLayout><PrivateDraft /></DashboardLayout>, { container, hydrate: true }));
  expect(screen.getByLabelText("Private draft")).toBeVisible();
  expect(router.replace).not.toHaveBeenCalled();
});

it("removes private drafts when another user with the same role signs in", async () => {
  storeSession({ accessToken: "first", refreshToken: "first-refresh", role: "ADMIN" });
  render(<DashboardLayout><PrivateDraft /></DashboardLayout>);
  fireEvent.change(screen.getByLabelText("Private draft"), { target: { value: "First patient's private draft" } });
  await act(async () => storeSession({ accessToken: "second", refreshToken: "second-refresh", role: "ADMIN" }));
  expect(screen.getByLabelText("Private draft")).toHaveValue("");
});
