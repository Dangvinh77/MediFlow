import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { staffContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({
  role: "ADMIN",
  session: "session-a",
  staffMember: vi.fn(),
  deactivateStaff: vi.fn(),
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
vi.mock("./StaffCapabilityGrants", () => ({ StaffCapabilityGrants: () => <div data-testid="capability-grants" /> }));

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

it("updates employment and reloads the linked account state after confirmation", async () => {
  mocks.deactivateStaff.mockResolvedValue({ ...staffContractFixture, email: "doctor@example.com", active: false });
  render(<StaffDetail staffId={staffContractFixture.staffId} />);
  await screen.findByRole("button", { name: "Cấp tài khoản nhân sự" });
  fireEvent.click(screen.getByRole("button", { name: "Ngừng làm việc" }));
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận ngừng làm việc" }));
  expect(await screen.findByText("Đã ngừng làm việc nhân viên và khóa tài khoản liên kết.")).toBeVisible();
  expect(screen.getByText("Đã ngừng")).toBeVisible();
  expect(screen.queryByRole("button", { name: "Ngừng làm việc" })).not.toBeInTheDocument();
  await waitFor(() => expect(mocks.profileAccount).toHaveBeenCalledTimes(2));
  expect(screen.getByRole("button", { name: "Cấp tài khoản nhân sự" })).toBeDisabled();
});

it("ignores a profile read started before successful deactivation", async () => {
  let resolveRead!: (value: typeof staffContractFixture) => void;
  const staleRead = new Promise<typeof staffContractFixture>(resolve => { resolveRead = resolve; });
  mocks.staffMember.mockResolvedValueOnce({ ...staffContractFixture, active: true }).mockReturnValueOnce(staleRead);
  mocks.profileAccount.mockResolvedValue({ account: null, profileEmail: "doctor@example.com", emailSyncPending: true });
  mocks.deactivateStaff.mockResolvedValue({ ...staffContractFixture, active: false });
  render(<StaffDetail staffId={staffContractFixture.staffId} />);
  fireEvent.click(await screen.findByRole("button", { name: "Kiểm tra đồng bộ email" }));
  await waitFor(() => expect(mocks.staffMember).toHaveBeenCalledTimes(2));
  fireEvent.click(screen.getByRole("button", { name: "Ngừng làm việc" }));
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận ngừng làm việc" }));
  await screen.findByText("Đã ngừng");
  await act(async () => { resolveRead({ ...staffContractFixture, active: true }); });
  expect(screen.getByText("Đã ngừng")).toBeVisible();
  expect(screen.queryByRole("button", { name: "Ngừng làm việc" })).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Cấp tài khoản nhân sự" })).toBeDisabled();
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
