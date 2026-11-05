import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { webcrypto } from "node:crypto";
import { ApiRequestError } from "@/lib/api";
import { patientContractFixture as patient } from "@/features/patient/contract.fixture";
import type { PatientDTO } from "@/features/patient/types";

const mocks = vi.hoisted(() => ({
  getById: vi.fn(), profileAccount: vi.fn(), provisionPatientAccount: vi.fn(), role: vi.fn(() => "NURSE"),
  session: vi.fn(() => "session-a"), subscribe: vi.fn(() => () => {}),
  patientMailDelivery: vi.fn(), resetPatientPassword: vi.fn(), retryPatientMail: vi.fn(), mailDelivery: vi.fn(), resetPassword: vi.fn(), retryMail: vi.fn(), account: vi.fn(), updateAccountEmail: vi.fn(), updatePatientEmail: vi.fn(),
  router: { replace: vi.fn(), push: vi.fn() },
}));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: mocks.role, getSessionKey: mocks.session, subscribeToAuthChanges: mocks.subscribe }));
vi.mock("@/lib/session", () => ({ getSessionKey: mocks.session, subscribeToAuthChanges: mocks.subscribe }));
vi.mock("@/features/patient/api", () => ({ patientApi: { getById: mocks.getById } }));
vi.mock("@/features/organization/api", () => ({ organizationApi: mocks }));
import PatientDetailPage from "./page";

async function page(notice?: string, patientId = patient.maBenhNhan) {
  return PatientDetailPage({ params: Promise.resolve({ patientId }), searchParams: Promise.resolve({ notice }) });
}

beforeEach(() => {
  vi.stubGlobal("crypto", webcrypto);
  sessionStorage.clear();
  vi.clearAllMocks();
  mocks.getById.mockReset().mockResolvedValue(patient);
  mocks.provisionPatientAccount.mockReset();
  mocks.profileAccount.mockReset().mockResolvedValue({ account: null, profileEmail: "patient@example.com", emailSyncPending: false });
  mocks.patientMailDelivery.mockResolvedValue(null);
  mocks.role.mockReturnValue("NURSE");
});

it.each([undefined, "created", "updated", "unrelated"])("lets a nurse continue onboarding with notice=%s", async (notice) => {
  render(await page(notice));
  expect(await screen.findByRole("button", { name: "Cấp tài khoản bệnh nhân" })).toBeVisible();
  expect(screen.getByRole("link", { name: "Tiếp tục đăng ký khám" }))
    .toHaveAttribute("href", `/appointments/reception?patientId=${patient.maBenhNhan}`);
  expect(mocks.provisionPatientAccount).not.toHaveBeenCalled();
});

it.each(["ADMIN", "DOCTOR", "PATIENT"])("shows onboarding only for allowed roles: %s", async (role) => {
  mocks.role.mockReturnValue(role);
  render(await page());
  if (role === "ADMIN") {
    expect(await screen.findByRole("button", { name: "Cấp tài khoản bệnh nhân" })).toBeVisible();
  } else {
    if (role === "DOCTOR") await screen.findByRole("heading", { name: patient.hoTen });
    expect(screen.queryByRole("button", { name: "Cấp tài khoản bệnh nhân" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Tiếp tục đăng ký khám" })).not.toBeInTheDocument();
  }
});

it("waits for an active patient and hides onboarding for an archived record", async () => {
  let resolve!: (value: PatientDTO) => void;
  mocks.getById.mockReturnValue(new Promise(done => { resolve = done; }));
  render(await page("created"));
  expect(screen.queryByRole("button", { name: "Cấp tài khoản bệnh nhân" })).not.toBeInTheDocument();
  await act(async () => resolve({ ...patient, daLuuTru: true }));
  await screen.findByRole("heading", { name: patient.hoTen });
  expect(screen.queryByRole("link", { name: "Tiếp tục đăng ký khám" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Cấp tài khoản bệnh nhân" })).not.toBeInTheDocument();
});

it.each([404, 503])("hides onboarding when patient lookup returns %s", async (status) => {
  mocks.getById.mockRejectedValue(new ApiRequestError("Không tải được hồ sơ", status));
  render(await page("created"));
  await screen.findByText(status === 404 ? "Không tìm thấy bệnh nhân." : "Không tải được hồ sơ");
  expect(screen.queryByRole("button", { name: "Cấp tài khoản bệnh nhân" })).not.toBeInTheDocument();
});

it("recovers an uncertain issuance after reopening the plain detail URL with the same key", async () => {
  mocks.provisionPatientAccount.mockRejectedValueOnce(new Error("Timeout"));
  const existingAccount = { accountId: "account-a", username: "patient@example.com", patientId: patient.maBenhNhan,
    staffId: null, role: "PATIENT", isActive: true, lastLoginAt: null, createdAt: "2026-11-04T00:00:00Z",
    updatedAt: "2026-11-04T00:00:00Z", mailDelivery: null };
  mocks.profileAccount.mockResolvedValueOnce({ account: null, profileEmail: "patient@example.com", emailSyncPending: false })
    .mockResolvedValueOnce({ account: null, profileEmail: "patient@example.com", emailSyncPending: false })
    .mockResolvedValue({ account: existingAccount, profileEmail: "patient@example.com", emailSyncPending: false });
  const first = render(await page("created"));
  fireEvent.click(await screen.findByRole("button", { name: "Cấp tài khoản bệnh nhân" }));
  await screen.findByRole("alert");
  const key = mocks.provisionPatientAccount.mock.calls[0][1];
  first.unmount();
  mocks.provisionPatientAccount.mockResolvedValue({ accountId: "account-a", patientId: patient.maBenhNhan, username: "patient@example.com" });
  render(await page());
  fireEvent.click(await screen.findByRole("button", { name: "Cấp tài khoản bệnh nhân" }));
  expect(await screen.findByText("Tài khoản đang hoạt động")).toBeVisible();
  expect(mocks.provisionPatientAccount).toHaveBeenLastCalledWith(patient.maBenhNhan, key);
  expect(screen.queryByText(/Mật khẩu ban đầu|secret-password|passwordHash/i)).not.toBeInTheDocument();
});

it("keeps the registration link when the backend reports an existing account", async () => {
  mocks.provisionPatientAccount.mockRejectedValue(new ApiRequestError("Bệnh nhân đã được cấp tài khoản", 409, "PATIENT_ACCOUNT_ALREADY_ISSUED"));
  render(await page());
  fireEvent.click(await screen.findByRole("button", { name: "Cấp tài khoản bệnh nhân" }));
  await screen.findByRole("alert");
  expect(screen.getByRole("link", { name: "Tiếp tục đăng ký khám" })).toBeVisible();
  await waitFor(() => expect(mocks.provisionPatientAccount).toHaveBeenCalledTimes(1));
});

it.each(["ADMIN", "NURSE"])("refreshes the patient profile after %s changes its account email", async (role) => {
  mocks.role.mockReturnValue(role);
  const before = { ...patient, email: "patient@example.com" };
  const after = { ...patient, email: "corrected@example.com" };
  mocks.getById.mockReset().mockResolvedValueOnce(before).mockResolvedValue(after);
  const account = { accountId: "account-a", username: "patient@example.com", patientId: patient.maBenhNhan,
    staffId: null, role: "PATIENT", isActive: true, lastLoginAt: null, createdAt: "2026-11-04T00:00:00Z",
    updatedAt: "2026-11-04T00:00:00Z", mailDelivery: null };
  mocks.profileAccount.mockResolvedValueOnce({ account, profileEmail: "patient@example.com", emailSyncPending: false })
    .mockResolvedValue({ account: { ...account, username: "corrected@example.com" }, profileEmail: "corrected@example.com", emailSyncPending: false });
  mocks.patientMailDelivery.mockResolvedValue({ messageId: "mail-1", status: "SENT" });
  mocks.updatePatientEmail.mockResolvedValue({ ...account, username: "corrected@example.com" });

  render(await page());
  fireEvent.change(await screen.findByLabelText("Email đăng nhập mới"), { target: { value: "corrected@example.com" } });
  fireEvent.change(screen.getByLabelText("Lý do thay đổi email"), { target: { value: "Đã đối chiếu hồ sơ" } });
  fireEvent.submit(screen.getByRole("button", { name: "Lưu email mới" }).closest("form")!);

  await waitFor(() => expect(mocks.getById).toHaveBeenCalledTimes(2));
  expect(await screen.findByText("corrected@example.com")).toBeVisible();
  expect(mocks.updatePatientEmail).toHaveBeenCalledWith(patient.maBenhNhan, "corrected@example.com", "Đã đối chiếu hồ sơ");
  expect(mocks.updateAccountEmail).not.toHaveBeenCalled();
});

it("rejects invalid identifiers before loading a patient or showing onboarding", async () => {
  render(await page("created", "invalid"));
  expect(screen.getByRole("alert")).toHaveTextContent("UUID hợp lệ");
  expect(mocks.getById).not.toHaveBeenCalled();
  expect(screen.queryByRole("button", { name: "Cấp tài khoản bệnh nhân" })).not.toBeInTheDocument();
});
