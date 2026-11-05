import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { webcrypto } from "node:crypto";
import { ApiRequestError } from "@/lib/api";
const mocks = vi.hoisted(() => ({ account: vi.fn(), profileAccount: vi.fn(), updatePatientEmail: vi.fn(), resetPassword: vi.fn(), mailDelivery: vi.fn(), retryMail: vi.fn(), updateAccountEmail: vi.fn(), patientMailDelivery: vi.fn(), resetPatientPassword: vi.fn(), retryPatientMail: vi.fn(), session: "a" }));
vi.mock("../api", () => ({ organizationApi: mocks }));
vi.mock("@/lib/auth", () => ({ getSessionKey: () => mocks.session }));
import { AdminAccountSecurity } from "./AdminAccountSecurity";
beforeEach(() => {
  vi.clearAllMocks(); sessionStorage.clear(); mocks.session = "a";
  vi.stubGlobal("crypto", webcrypto); vi.spyOn(window, "confirm").mockReturnValue(true);
  mocks.mailDelivery.mockResolvedValue({ messageId: "mail-1", status: "FAILED" });
});
afterEach(() => vi.unstubAllGlobals());
it("corrects patient login email through the patient-scoped command", async () => {
  mocks.patientMailDelivery.mockResolvedValue({ messageId: "mail-1", status: "SENT" });
  mocks.updatePatientEmail.mockResolvedValue({ username: "new@example.com" });
  const saved = vi.fn();
  render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" patientIdForMail="patient-a" onEmailChanged={saved} />);
  fireEvent.change(screen.getByLabelText("Email đăng nhập mới"), { target: { value: "new@example.com" } });
  fireEvent.change(screen.getByLabelText("Lý do thay đổi email"), { target: { value: "Bệnh nhân xác nhận" } });
  fireEvent.submit(screen.getByRole("button", { name: "Lưu email mới" }).closest("form")!);
  await waitFor(() => expect(saved).toHaveBeenCalledOnce());
  expect(mocks.updatePatientEmail).toHaveBeenCalledExactlyOnceWith("patient-a", "new@example.com", "Bệnh nhân xác nhận");
  expect(mocks.updateAccountEmail).not.toHaveBeenCalled();
});

it("reconciles uncertain patient email writes using patient profile lookup", async () => {
  mocks.patientMailDelivery.mockResolvedValue({ messageId: "mail-1", status: "SENT" });
  mocks.updatePatientEmail.mockRejectedValueOnce(new Error("Lost response"));
  mocks.profileAccount.mockResolvedValue({ emailSyncPending: false, account: { accountId: "account-a", patientId: "patient-a", staffId: null, role: "PATIENT", username: "new@example.com" } });
  const saved = vi.fn();
  render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" patientIdForMail="patient-a" onEmailChanged={saved} />);
  fireEvent.change(screen.getByLabelText("Email đăng nhập mới"), { target: { value: "new@example.com" } });
  fireEvent.change(screen.getByLabelText("Lý do thay đổi email"), { target: { value: "Bệnh nhân xác nhận" } });
  fireEvent.submit(screen.getByRole("button", { name: "Lưu email mới" }).closest("form")!);
  await screen.findByRole("alert");
  fireEvent.click(screen.getByRole("button", { name: "Kiểm tra kết quả đổi email" }));
  await waitFor(() => expect(saved).toHaveBeenCalledOnce());
  expect(mocks.profileAccount).toHaveBeenCalledWith("patient", "patient-a");
  expect(mocks.account).not.toHaveBeenCalled();
});

it("keeps an uncertain patient email command blocked when lookup returns another owner", async () => {
  mocks.patientMailDelivery.mockResolvedValue({ messageId: "mail-1", status: "SENT" });
  mocks.updatePatientEmail.mockRejectedValueOnce(new Error("Lost response"));
  mocks.profileAccount.mockResolvedValue({ emailSyncPending: false, account: { accountId: "account-a", patientId: "other-patient", staffId: null, role: "PATIENT", username: "new@example.com" } });
  const saved = vi.fn();
  render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" patientIdForMail="patient-a" onEmailChanged={saved} />);
  fireEvent.change(screen.getByLabelText("Email đăng nhập mới"), { target: { value: "new@example.com" } });
  fireEvent.change(screen.getByLabelText("Lý do thay đổi email"), { target: { value: "Bệnh nhân xác nhận" } });
  fireEvent.submit(screen.getByRole("button", { name: "Lưu email mới" }).closest("form")!);
  await screen.findByRole("alert");
  fireEvent.click(screen.getByRole("button", { name: "Kiểm tra kết quả đổi email" }));
  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Chưa xác minh"));
  expect(saved).not.toHaveBeenCalled();
  expect(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" })).toBeDisabled();
});

it("does not treat an unchanged username as proof that an uncertain sync repair was queued", async () => {
  mocks.updatePatientEmail.mockRejectedValueOnce(new Error("Lost response"));
  mocks.profileAccount.mockResolvedValue({
    emailSyncPending: true,
    account: { accountId: "account-a", patientId: "patient-a", staffId: null, role: "PATIENT", username: "patient@example.com" },
  });
  const saved = vi.fn();
  render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" patientIdForMail="patient-a" emailSyncPending disablePasswordActions onEmailChanged={saved} />);
  fireEvent.change(screen.getByLabelText("Lý do thay đổi email"), { target: { value: "Đã xác minh lại email" } });
  fireEvent.submit(screen.getByRole("button", { name: "Lưu email mới" }).closest("form")!);
  await screen.findByRole("alert");
  fireEvent.click(screen.getByRole("button", { name: "Kiểm tra kết quả đổi email" }));

  expect(await screen.findByText(/hồ sơ vẫn chờ đồng bộ/i)).toBeVisible();
  expect(screen.queryByText("Đã xác nhận email đăng nhập mới.")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Lưu email mới" })).toBeEnabled();
  expect(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" })).toBeDisabled();
  expect(saved).toHaveBeenCalledOnce();

  fireEvent.click(screen.getByRole("button", { name: "Lưu email mới" }));
  await waitFor(() => expect(mocks.updatePatientEmail).toHaveBeenCalledTimes(2));
  expect(mocks.updatePatientEmail).toHaveBeenNthCalledWith(1, "patient-a", "patient@example.com", "Đã xác minh lại email");
  expect(mocks.updatePatientEmail).toHaveBeenNthCalledWith(2, "patient-a", "patient@example.com", "Đã xác minh lại email");
});

it.each([new Error("Lost response"), new ApiRequestError("Malformed response", 200)])("reconciles an uncertain email write before allowing another security change (%s)", async cause => {
  const saved = vi.fn();
  mocks.updateAccountEmail.mockRejectedValueOnce(cause);
  mocks.account.mockResolvedValue({ username: "corrected@example.com" });
  render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" onEmailChanged={saved} />);
  fireEvent.change(screen.getByLabelText("Email đăng nhập mới"), { target: { value: "corrected@example.com" } });
  fireEvent.change(screen.getByLabelText("Lý do thay đổi email"), { target: { value: "Đã xác minh" } });
  fireEvent.submit(screen.getByRole("button", { name: "Lưu email mới" }).closest("form")!);
  await screen.findByRole("alert");
  expect(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "Kiểm tra kết quả đổi email" }));
  await waitFor(() => expect(saved).toHaveBeenCalledOnce());
  expect(mocks.updateAccountEmail).toHaveBeenCalledTimes(1);
});
it("retries the same mail without issuing another password", async () => {
  mocks.retryMail.mockResolvedValue({ messageId: "mail-1", status: "PENDING" });
  render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" />);
  fireEvent.click(await screen.findByRole("button", { name: "Gửi lại thư" }));
  await waitFor(() => expect(mocks.retryMail).toHaveBeenCalledExactlyOnceWith("account-a", "mail-1"));
  expect(mocks.resetPassword).not.toHaveBeenCalled();
});
it("uses patient-scoped reset and retry endpoints and can hide email changes from nurses", async () => {
  mocks.patientMailDelivery.mockResolvedValue({ messageId: "patient-mail", status: "FAILED" });
  mocks.retryPatientMail.mockResolvedValue({ messageId: "patient-mail", status: "PENDING" });
  mocks.resetPatientPassword.mockResolvedValue({ messageId: "patient-mail", status: "PENDING" });
  render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" patientIdForMail="patient-a" allowEmailChange={false} />);
  expect(await screen.findByRole("button", { name: "Gửi lại thư" })).toBeVisible();
  expect(screen.queryByLabelText("Email đăng nhập mới")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Gửi lại thư" }));
  await waitFor(() => expect(mocks.retryPatientMail).toHaveBeenCalledExactlyOnceWith("patient-a", "patient-mail"));
  fireEvent.click(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" }));
  await waitFor(() => expect(mocks.resetPatientPassword).toHaveBeenCalledExactlyOnceWith("patient-a", expect.any(String)));
  expect(mocks.resetPassword).not.toHaveBeenCalled();
});
it("keeps the same reset intent across timeout and remount and never asks for plaintext passwords", async () => {
  mocks.resetPassword.mockRejectedValueOnce(new Error("Lost response")).mockResolvedValueOnce({ messageId: "mail-2", status: "PENDING" });
  const view = render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" />);
  expect(screen.queryByLabelText(/Mật khẩu mới/)).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" }));
  await screen.findByRole("alert"); const key = mocks.resetPassword.mock.calls[0][1];
  view.unmount(); render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" />);
  fireEvent.click(screen.getByRole("button", { name: "Cấp mật khẩu tạm qua email" }));
  await waitFor(() => expect(mocks.resetPassword).toHaveBeenCalledTimes(2));
  expect(mocks.resetPassword.mock.calls[1][1]).toBe(key);
});
it("sends the audited email correction and ignores old-session completion", async () => {
  let resolve!: () => void; const saved = vi.fn();
  mocks.updateAccountEmail.mockReturnValue(new Promise<void>(done => { resolve = done; }));
  render(<AdminAccountSecurity accountId="account-a" username="patient@example.com" onEmailChanged={saved} />);
  fireEvent.change(screen.getByLabelText("Email đăng nhập mới"), { target: { value: "corrected@example.com" } });
  fireEvent.change(screen.getByLabelText("Lý do thay đổi email"), { target: { value: "Đã xác minh lại email" } });
  fireEvent.submit(screen.getByRole("button", { name: "Lưu email mới" }).closest("form")!);
  expect(mocks.updateAccountEmail).toHaveBeenCalledWith("account-a", "corrected@example.com", "Đã xác minh lại email");
  mocks.session = "b"; await act(async () => resolve()); expect(saved).not.toHaveBeenCalled();
});
