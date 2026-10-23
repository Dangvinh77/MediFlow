import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { staffContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({ read: vi.fn(), session: vi.fn(() => "session-a") }));
vi.mock("../api", () => ({ organizationApi: { staffMember: mocks.read } }));
vi.mock("@/lib/auth", () => ({ getSessionKey: mocks.session }));
import { AccountStaffLink } from "./AccountStaffLink";

beforeEach(() => { vi.resetAllMocks(); mocks.session.mockReturnValue("session-a"); });

it("keeps the real detail link usable when the name fails, then retries", async () => {
  mocks.read.mockRejectedValueOnce(new Error("Offline")).mockResolvedValueOnce(staffContractFixture);
  render(<AccountStaffLink staffId={staffContractFixture.staffId} />);
  expect(await screen.findByRole("link", { name: /Chưa tải được tên/ })).toHaveAttribute("href", `/organization/staff/${staffContractFixture.staffId}`);
  fireEvent.click(screen.getByRole("button", { name: "Tải lại tên nhân viên" }));
  expect(await screen.findByRole("link", { name: `Nhân viên: ${staffContractFixture.fullName}` })).toBeVisible();
});

it("discards a name arriving after the session changes", async () => {
  let resolve!: (staff: typeof staffContractFixture) => void;
  mocks.read.mockReturnValue(new Promise(done => { resolve = done; }));
  render(<AccountStaffLink staffId={staffContractFixture.staffId} />);
  mocks.session.mockReturnValue("session-b");
  await act(async () => { resolve(staffContractFixture); });
  expect(screen.queryByText(`Nhân viên: ${staffContractFixture.fullName}`)).not.toBeInTheDocument();
});
