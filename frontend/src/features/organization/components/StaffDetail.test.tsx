import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { staffContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({
  role: "ADMIN",
  session: "session-a",
  staffMember: vi.fn(),
  profileAccount: vi.fn(),
  mailDelivery: vi.fn(),
  resetPassword: vi.fn(),
  retryMail: vi.fn(),
  account: vi.fn(),
  updateAccountEmail: vi.fn(),
  router: { replace: vi.fn() },
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: () => mocks.role,
  getSessionKey: () => mocks.session,
  subscribeToAuthChanges: () => () => {},
}));
vi.mock("../api", () => ({ organizationApi: mocks }));

import { StaffDetail } from "./StaffDetail";

const linkedAccount = {
  accountId: "00000000-0000-4000-8000-000000000003",
  username: "doctor@example.com",
  staffId: staffContractFixture.staffId,
  patientId: null,
  role: "DOCTOR",
  isActive: true,
  lastLoginAt: null,
  createdAt: "2026-11-04T00:00:00Z",
  updatedAt: "2026-11-04T00:00:00Z",
  mailDelivery: null,
};

beforeEach(() => {
  vi.clearAllMocks();
  mocks.role = "ADMIN";
  mocks.session = "session-a";
  mocks.staffMember.mockResolvedValue({ ...staffContractFixture, email: "doctor@example.com", active: true });
  mocks.profileAccount.mockResolvedValue({ account: null, profileEmail: "doctor@example.com", emailSyncPending: false });
  mocks.mailDelivery.mockResolvedValue({ messageId: "mail-1", status: "SENT" });
});

it("puts the account state and issue action inside an administrator's staff profile", async () => {
  render(<StaffDetail staffId={staffContractFixture.staffId} />);
  expect(await screen.findByRole("button", { name: "Cấp tài khoản nhân sự" })).toBeVisible();
  expect(mocks.profileAccount).toHaveBeenCalledWith("staff", staffContractFixture.staffId);
  expect(screen.getByText(/Email hồ sơ: doctor@example.com/)).toBeVisible();
});

it("shows linked account state and audited email controls without exposing credentials", async () => {
  mocks.profileAccount.mockResolvedValue({ account: linkedAccount, profileEmail: "doctor@example.com", emailSyncPending: false });
  render(<StaffDetail staffId={staffContractFixture.staffId} />);
  expect(await screen.findByText("Tài khoản đang hoạt động")).toBeVisible();
  await waitFor(() => expect(mocks.mailDelivery).toHaveBeenCalledWith(linkedAccount.accountId));
  expect(screen.getByLabelText("Lý do thay đổi email")).toBeVisible();
  expect(screen.queryByLabelText(/mật khẩu/i)).not.toBeInTheDocument();
  expect(document.body.textContent).not.toMatch(/passwordHash|secret-password/i);
});

it("does not offer account creation when the owner lookup fails", async () => {
  mocks.profileAccount.mockRejectedValue(new Error("Account lookup unavailable"));
  render(<StaffDetail staffId={staffContractFixture.staffId} />);
  expect(await screen.findByText("Account lookup unavailable")).toBeVisible();
  expect(screen.queryByRole("button", { name: "Cấp tài khoản nhân sự" })).not.toBeInTheDocument();
});
