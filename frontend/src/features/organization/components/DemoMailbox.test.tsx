import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
const mocks = vi.hoisted(() => ({ demoConfig: vi.fn(), demoAccounts: vi.fn(), demoMessages: vi.fn(), demoMessage: vi.fn() }));
vi.mock("../security-api", () => ({ accountSecurityApi: mocks }));
import { DemoMailbox } from "./DemoMailbox";
const page = <T,>(content: T[]) => ({ content, number: 0, size: 20, totalPages: 1, totalElements: content.length });
beforeEach(() => vi.clearAllMocks());
it("does not request an account directory when demo access is disabled", async () => {
  mocks.demoConfig.mockResolvedValue({ enabled: false });
  render(<DemoMailbox />);
  expect(await screen.findByText(/Hộp thư demo chưa được bật/)).toBeVisible();
  expect(mocks.demoAccounts).not.toHaveBeenCalled();
});
it("discards an old inbox when a different account is selected and renders HTML in a sandbox", async () => {
  mocks.demoConfig.mockResolvedValue({ enabled: true });
  mocks.demoAccounts.mockResolvedValue(page([
    { accountId: "a", role: "PATIENT", displayName: "Bệnh nhân A", email: "a@demo.mediflow.local" },
    { accountId: "b", role: "DOCTOR", displayName: "Bác sĩ B", email: "b@demo.mediflow.local" },
  ]));
  let resolveOld!: (value: unknown) => void;
  mocks.demoMessages.mockImplementation((id: string) => id === "a" ? new Promise(done => { resolveOld = done; }) : Promise.resolve(page([{ messageId: "mail-b", subject: "Thư cho B", createdAt: "2026-11-04T00:00:00Z", preview: "B", from: "MediFlow", to: "b@demo.mediflow.local" }])));
  mocks.demoMessage.mockResolvedValue({ messageId: "mail-b", subject: "Thư cho B", from: "MediFlow", to: "b@demo.mediflow.local", createdAt: "2026-11-04T00:00:00Z", textBody: "MediFlow", htmlBody: "<h1>MediFlow</h1>" });
  render(<DemoMailbox />);
  fireEvent.click(await screen.findByRole("button", { name: /Bệnh nhân A/ }));
  await waitFor(() => expect(mocks.demoMessages).toHaveBeenCalledWith("a", 0));
  fireEvent.click(screen.getByRole("button", { name: /Bác sĩ B/ }));
  fireEvent.click(await screen.findByRole("button", { name: /Thư cho B/ }));
  await act(async () => resolveOld(page([{ messageId: "mail-a", subject: "Thư cho A" }])));
  expect(screen.queryByText("Thư cho A")).not.toBeInTheDocument();
  const frame = await screen.findByTitle("Email: Thư cho B");
  expect(frame).toHaveAttribute("sandbox", "allow-top-navigation-by-user-activation");
  expect(frame.getAttribute("srcdoc")).toContain("MediFlow");
});
