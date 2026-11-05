import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { webcrypto } from "node:crypto";

const mocks = vi.hoisted(() => ({
  role: "ADMIN",
  session: "session-a",
  profileAccount: vi.fn(),
  provisionPatientAccount: vi.fn(),
  patientMailDelivery: vi.fn(),
  resetPatientPassword: vi.fn(),
  retryPatientMail: vi.fn(),
  account: vi.fn(),
  mailDelivery: vi.fn(),
  resetPassword: vi.fn(),
  retryMail: vi.fn(),
  updateAccountEmail: vi.fn(),
  updatePatientEmail: vi.fn(),
  router: { replace: vi.fn() },
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: () => mocks.role,
  getSessionKey: () => mocks.session,
  subscribeToAuthChanges: () => () => {},
}));
vi.mock("@/lib/session", () => ({
  getSessionKey: () => mocks.session,
  subscribeToAuthChanges: () => () => {},
}));
vi.mock("../api", () => ({ organizationApi: mocks }));

import { PatientAccountIssuance } from "./PatientAccountIssuance";

const patientId = "00000000-0000-4000-8000-000000000001";
const account = {
  accountId: "00000000-0000-4000-8000-000000000002",
  username: "patient@example.com",
  staffId: null,
  patientId,
  role: "PATIENT",
  isActive: false,
  lastLoginAt: null,
  createdAt: "2026-11-04T00:00:00Z",
  updatedAt: "2026-11-04T00:00:00Z",
  mailDelivery: null,
};
const linked = { account, profileEmail: "patient@example.com", emailSyncPending: false };
const unlinked = { account: null, profileEmail: "patient@example.com", emailSyncPending: false };

beforeEach(() => {
  vi.stubGlobal("crypto", webcrypto);
  vi.spyOn(window, "confirm").mockReturnValue(true);
  sessionStorage.clear();
  vi.clearAllMocks();
  mocks.role = "ADMIN";
  mocks.session = "session-a";
  mocks.profileAccount.mockResolvedValue(unlinked);
  mocks.patientMailDelivery.mockResolvedValue({ messageId: "mail-1", status: "FAILED" });
});
afterEach(() => vi.unstubAllGlobals());

it("loads the linked account from the patient profile and offers one reset action without secrets", async () => {
  mocks.profileAccount.mockResolvedValue(linked);
  render(<PatientAccountIssuance patientId={patientId} />);

  expect(await screen.findByText("Tài khoản đã khóa")).toBeVisible();
  expect(mocks.profileAccount).toHaveBeenCalledWith("patient", patientId);
  expect(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" })).toBeVisible();
  expect(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" })).toBeDisabled();
  expect(screen.queryByRole("button", { name: "Cấp tài khoản bệnh nhân" })).not.toBeInTheDocument();
  expect(screen.queryByLabelText(/mật khẩu/i)).not.toBeInTheDocument();
  expect(document.body.textContent).not.toMatch(/secret-password|passwordHash/i);
});

it("does not mistake a failed account lookup for an unlinked profile", async () => {
  mocks.profileAccount.mockRejectedValue(new Error("Lookup unavailable"));
  render(<PatientAccountIssuance patientId={patientId} />);

  expect(await screen.findByRole("alert")).toHaveTextContent("Lookup unavailable");
  expect(screen.queryByRole("button", { name: "Cấp tài khoản bệnh nhân" })).not.toBeInTheDocument();
  expect(mocks.provisionPatientAccount).not.toHaveBeenCalled();
});

it("does not expose account actions when the lookup returns another patient identity", async () => {
  mocks.profileAccount.mockResolvedValue({
    ...linked,
    account: { ...account, patientId: "00000000-0000-4000-8000-000000000099" },
  });
  render(<PatientAccountIssuance patientId={patientId} />);

  expect(await screen.findByRole("alert")).toHaveTextContent("không khớp hồ sơ");
  expect(screen.queryByRole("button", { name: "Cấp mật khẩu tạm qua email" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Cấp tài khoản bệnh nhân" })).not.toBeInTheDocument();
  expect(mocks.resetPatientPassword).not.toHaveBeenCalled();
});

it("issues from a verified unlinked profile and stays on the profile after reloading account state", async () => {
  mocks.profileAccount.mockResolvedValueOnce(unlinked).mockResolvedValueOnce(linked);
  mocks.provisionPatientAccount.mockResolvedValue({ accountId: account.accountId, patientId, username: account.username });
  render(<PatientAccountIssuance patientId={patientId} />);

  fireEvent.click(await screen.findByRole("button", { name: "Cấp tài khoản bệnh nhân" }));
  expect(await screen.findByRole("button", { name: "Cấp mật khẩu tạm qua email" })).toBeVisible();
  expect(mocks.provisionPatientAccount).toHaveBeenCalledWith(patientId, expect.any(String));
  expect(mocks.profileAccount).toHaveBeenCalledTimes(2);
  expect(mocks.router.replace).not.toHaveBeenCalled();
  expect(screen.queryByLabelText(/mật khẩu/i)).not.toBeInTheDocument();
});

it("lets nurses manage email and reset only the patient linked to this profile", async () => {
  mocks.role = "NURSE";
  mocks.profileAccount.mockResolvedValue({ ...linked, account: { ...account, isActive: true } });
  mocks.resetPatientPassword.mockResolvedValue({ messageId: "mail-2", status: "PENDING" });
  render(<PatientAccountIssuance patientId={patientId} />);

  fireEvent.click(await screen.findByRole("button", { name: "Cấp mật khẩu tạm qua email" }));
  await waitFor(() => expect(mocks.resetPatientPassword).toHaveBeenCalledWith(patientId, expect.any(String)));
  expect(mocks.resetPassword).not.toHaveBeenCalled();
  expect(screen.getByLabelText("Email đăng nhập mới")).toBeVisible();
});

it("blocks reset and mail retry while patient email synchronization is pending", async () => {
  mocks.profileAccount.mockResolvedValue({ ...linked, account: { ...account, isActive: true }, emailSyncPending: true });
  mocks.patientMailDelivery.mockResolvedValue({ messageId: "mail-1", status: "FAILED" });
  render(<PatientAccountIssuance patientId={patientId} />);

  expect(await screen.findByText(/chờ đồng bộ/)).toBeVisible();
  expect(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" })).toBeDisabled();
  expect(await screen.findByRole("button", { name: "Gửi lại thư" })).toBeDisabled();
  expect(mocks.resetPatientPassword).not.toHaveBeenCalled();
  expect(mocks.retryPatientMail).not.toHaveBeenCalled();
});

it("lets a nurse recheck synchronization before sending credentials", async () => {
  mocks.role = "NURSE";
  const active = { ...linked, account: { ...account, isActive: true } };
  mocks.profileAccount.mockResolvedValueOnce({ ...active, emailSyncPending: true })
    .mockResolvedValue(active);
  render(<PatientAccountIssuance patientId={patientId} />);
  expect(await screen.findByRole("button", { name: "Cấp mật khẩu tạm qua email" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "Kiểm tra đồng bộ email" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" })).toBeEnabled());
  expect(mocks.profileAccount).toHaveBeenCalledTimes(2);
  expect(mocks.resetPatientPassword).not.toHaveBeenCalled();
});

it("lets a nurse repair a mismatched profile using the existing login email", async () => {
  mocks.role = "NURSE";
  const active = { ...linked, account: { ...account, isActive: true } };
  mocks.profileAccount.mockResolvedValueOnce({ ...active, profileEmail: "old@example.com", emailSyncPending: true })
    .mockResolvedValue(active);
  mocks.updatePatientEmail.mockResolvedValue(active.account);
  render(<PatientAccountIssuance patientId={patientId} />);
  fireEvent.change(await screen.findByLabelText("Lý do thay đổi email"), { target: { value: "Đã xác minh email đăng nhập" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu email mới" }));
  await waitFor(() => expect(mocks.updatePatientEmail).toHaveBeenCalledWith(patientId, account.username, "Đã xác minh email đăng nhập"));
  await waitFor(() => expect(screen.queryByText(/chờ đồng bộ/)).not.toBeInTheDocument());
  expect(mocks.resetPatientPassword).not.toHaveBeenCalled();
  expect(mocks.updateAccountEmail).not.toHaveBeenCalled();
});

it("reuses the same issuance key after a lost response and profile remount", async () => {
  mocks.provisionPatientAccount.mockRejectedValueOnce(new Error("Timeout"))
    .mockResolvedValueOnce({ accountId: account.accountId, patientId, username: account.username });
  const first = render(<PatientAccountIssuance patientId={patientId} />);
  fireEvent.click(await screen.findByRole("button", { name: "Cấp tài khoản bệnh nhân" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Timeout");
  const firstKey = mocks.provisionPatientAccount.mock.calls[0][1];
  first.unmount();
  mocks.profileAccount.mockResolvedValueOnce(unlinked).mockResolvedValueOnce(linked);
  render(<PatientAccountIssuance patientId={patientId} />);
  fireEvent.click(await screen.findByRole("button", { name: "Cấp tài khoản bệnh nhân" }));
  await waitFor(() => expect(mocks.provisionPatientAccount).toHaveBeenCalledTimes(2));
  expect(mocks.provisionPatientAccount.mock.calls[1][1]).toBe(firstKey);
  expect(await screen.findByText("Tài khoản đã khóa")).toBeVisible();
});

it("keeps the patient account state isolated when the signed-in session changes", async () => {
  mocks.profileAccount.mockResolvedValue({ ...linked, account: { ...account, isActive: true } });
  let resolve!: (value: { messageId: string; status: "PENDING" }) => void;
  mocks.resetPatientPassword.mockReturnValue(new Promise(done => { resolve = done; }));
  render(<PatientAccountIssuance patientId={patientId} />);
  fireEvent.click(await screen.findByRole("button", { name: "Cấp mật khẩu tạm qua email" }));
  await waitFor(() => expect(mocks.resetPatientPassword).toHaveBeenCalledOnce());
  mocks.session = "session-b";
  await act(async () => resolve({ messageId: "mail-old", status: "PENDING" }));
  expect(screen.queryByText(/Đã cấp mật khẩu tạm/)).not.toBeInTheDocument();
});
