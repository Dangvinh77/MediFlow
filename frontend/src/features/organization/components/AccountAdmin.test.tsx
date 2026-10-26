import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { webcrypto } from "node:crypto";
import { accountContractFixture, staffContractFixture } from "../contract.fixture";
import { ApiRequestError } from "@/lib/api";

const mocks = vi.hoisted(() => ({ createAccount: vi.fn(), staffMember: vi.fn(), getSessionKey: vi.fn<() => string | null>(() => "session-a"), router: { replace: vi.fn(), push: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ organizationApi: mocks }));
vi.mock("@/lib/auth", () => ({ getSessionKey: mocks.getSessionKey }));
vi.mock("./StaffPicker", () => ({ StaffPicker: ({ value, onChange }: { value: string; onChange: (id: string) => void }) => <label>Mã nhân viên<input value={value} onChange={e => onChange(e.target.value)} /></label> }));
import { AccountAdmin as AccountAdminForm } from "./AccountAdmin";
const loadPatientIdentity = vi.fn(async () => "012345678901");
function AccountAdmin() { return <AccountAdminForm loadPatientIdentity={loadPatientIdentity} />; }

beforeEach(() => {
  vi.stubGlobal("crypto", webcrypto); sessionStorage.clear(); vi.clearAllMocks();
  mocks.createAccount.mockReset(); mocks.staffMember.mockReset(); mocks.getSessionKey.mockReturnValue("session-a");
  mocks.staffMember.mockResolvedValue({ ...staffContractFixture, active: true });
});
afterEach(() => vi.unstubAllGlobals());
async function patientDraft() {
  fireEvent.change(screen.getByLabelText(/Tên đăng nhập/), { target: { value: "patient.demo" } });
  fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: "secret-password" } });
  fireEvent.change(screen.getByLabelText("Loại hồ sơ liên kết"), { target: { value: "patient" } });
  fireEvent.change(screen.getByLabelText(/Mã bệnh nhân/), { target: { value: accountContractFixture.patientId } });
  await waitFor(() => expect(screen.getByLabelText(/Tên đăng nhập/)).toHaveValue("012345678901"));
}
function submit() { fireEvent.submit(screen.getByRole("button", { name: "Tạo tài khoản" }).closest("form")!); }

it.each([["ADMINISTRATIVE", "ADMIN", "Quản trị viên"], ["TECHNICIAN", "LAB_TECH", "Kỹ thuật viên xét nghiệm"], ["DOCTOR", "DOCTOR", "Bác sĩ"]])("derives %s from selected employee with no role selector", async (jobTitle, role, label) => {
  mocks.staffMember.mockResolvedValue({ ...staffContractFixture, active: true, jobTitle });
  mocks.createAccount.mockResolvedValue(accountContractFixture);
  render(<AccountAdmin />);
  fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: staffContractFixture.staffId } });
  await waitFor(() => expect(screen.getByLabelText("Vai trò tự động")).toHaveValue(label));
  expect(screen.queryByRole("combobox", { name: /Vai trò/ })).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText(/Tên đăng nhập/), { target: { value: "employee.demo" } });
  fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: "secret-password" } });
  act(() => { submit(); submit(); });
  await waitFor(() => expect(mocks.createAccount).toHaveBeenCalledTimes(1));
  expect(mocks.createAccount.mock.calls[0][0]).toMatchObject({ staffId: staffContractFixture.staffId, patientId: null, role, username: staffContractFixture.identityNumber, password: "1" });
  await waitFor(() => expect(mocks.router.push).toHaveBeenCalledWith(`/organization/accounts/${accountContractFixture.accountId}`));
});

it("blocks creation when the selected profile has no CCCD", async () => {
  mocks.staffMember.mockResolvedValue({ ...staffContractFixture, identityNumber: null });
  render(<AccountAdmin />);
  fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: staffContractFixture.staffId } });
  expect(await screen.findByRole("alert")).toHaveTextContent("chưa có CCCD hợp lệ");
  expect(screen.getByRole("button", { name: "Tạo tài khoản" })).toBeDisabled();
  submit(); expect(mocks.createAccount).not.toHaveBeenCalled();
});

it("preselects a saved patient and derives read-only credentials", async () => {
  render(<AccountAdminForm initialOwnerId={accountContractFixture.patientId!} initialOwnerType="patient" loadPatientIdentity={loadPatientIdentity} />);
  await waitFor(() => expect(screen.getByLabelText(/Tên đăng nhập/)).toHaveValue("012345678901"));
  expect(screen.getByLabelText(/Tên đăng nhập/)).toHaveAttribute("readonly");
  expect(screen.getByLabelText(/Mật khẩu ban đầu/)).toHaveValue("1");
  expect(screen.getByLabelText(/Mật khẩu ban đầu/)).toHaveAttribute("readonly");
});

it("requires confirmation before replacing and cancel makes no extra request", async () => {
  mocks.createAccount.mockRejectedValueOnce(new ApiRequestError("old.account sẽ bị inactive", 409, "ACCOUNT_OWNER_ACTIVE")).mockResolvedValueOnce(accountContractFixture);
  render(<AccountAdmin />); await patientDraft(); submit();
  expect(await screen.findByRole("alertdialog")).toHaveTextContent("old.account");
  expect(mocks.createAccount).toHaveBeenCalledTimes(1);
  expect(mocks.createAccount.mock.calls[0][0]).not.toHaveProperty("replaceActiveAccount");
  fireEvent.click(screen.getByRole("button", { name: "Hủy" }));
  expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  expect(mocks.createAccount).toHaveBeenCalledTimes(1);
});

it("sends confirmed replacement only after continue and retains exact command on lost response", async () => {
  mocks.createAccount.mockRejectedValueOnce(new ApiRequestError("old.account sẽ bị inactive", 409, "ACCOUNT_OWNER_ACTIVE"))
    .mockRejectedValueOnce(new Error("Lost response")).mockResolvedValueOnce(accountContractFixture);
  const view = render(<AccountAdmin />); await patientDraft(); submit();
  await screen.findByRole("alertdialog");
  fireEvent.click(screen.getByRole("button", { name: "Tiếp tục tạo tài khoản" }));
  await screen.findByRole("alert");
  expect(mocks.createAccount.mock.calls[1][0]).toMatchObject({ role: "PATIENT", replaceActiveAccount: true });
  const key = mocks.createAccount.mock.calls[1][1];
  expect(JSON.stringify(sessionStorage)).not.toContain("secret-password");
  view.unmount(); render(<AccountAdmin />); await patientDraft(); submit();
  await screen.findByRole("alertdialog");
  expect(mocks.createAccount).toHaveBeenCalledTimes(2);
  fireEvent.click(screen.getByRole("button", { name: "Tiếp tục tạo tài khoản" }));
  await waitFor(() => expect(mocks.router.push).toHaveBeenCalled());
  expect(mocks.createAccount.mock.calls[2]).toEqual([mocks.createAccount.mock.calls[1][0], key]);
  expect(sessionStorage.length).toBe(0);
});

it("ignores creation success after session changes", async () => {
  let resolve!: (value: typeof accountContractFixture) => void;
  mocks.createAccount.mockReturnValue(new Promise(done => { resolve = done; }));
  render(<AccountAdmin />); await patientDraft(); submit();
  await waitFor(() => expect(mocks.createAccount).toHaveBeenCalledTimes(1));
  mocks.getSessionKey.mockReturnValue("session-b");
  await act(async () => resolve(accountContractFixture));
  expect(mocks.router.push).not.toHaveBeenCalled();
});

it("does not create while employee role is still loading or selection has changed", async () => {
  let resolve!: (value: typeof staffContractFixture) => void;
  mocks.staffMember.mockReturnValueOnce(new Promise(done => { resolve = done; })).mockResolvedValueOnce({ ...staffContractFixture, staffId: accountContractFixture.patientId, jobTitle: "NURSE" });
  render(<AccountAdmin />);
  fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: staffContractFixture.staffId } }); submit();
  expect(mocks.createAccount).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText(/Mã nhân viên/), { target: { value: accountContractFixture.patientId } });
  await waitFor(() => expect(screen.getByLabelText("Vai trò tự động")).toHaveValue("Điều dưỡng"));
  await act(async () => resolve(staffContractFixture));
  expect(screen.getByLabelText("Vai trò tự động")).toHaveValue("Điều dưỡng");
});
