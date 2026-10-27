import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { webcrypto } from "node:crypto";
const mocks = vi.hoisted(() => ({ provisionPatientAccount: vi.fn() }));
vi.mock("../api", () => ({ organizationApi: mocks }));
import { PatientAccountIssuance } from "./PatientAccountIssuance";
const patientId = "00000000-0000-4000-8000-000000000001";

beforeEach(() => { vi.stubGlobal("crypto", webcrypto); sessionStorage.clear(); vi.clearAllMocks(); });

it("preserves the patient's changed password when recovering a previous issuance", async () => {
  mocks.provisionPatientAccount.mockResolvedValue({ accountId: "account-a", patientId, username: "012345678901", mustChangePassword: false });
  render(<PatientAccountIssuance patientId={patientId} />);
  fireEvent.click(screen.getByRole("button", { name: "Cấp tài khoản bệnh nhân" }));
  await screen.findByText("012345678901");
  expect(screen.queryByText(/Mật khẩu ban đầu:/)).not.toBeInTheDocument();
  expect(screen.getByText(/mật khẩu đã được bệnh nhân đổi/i)).toBeInTheDocument();
});

it("allows registration before the patient logs in and issues only for the saved patient", async () => {
  mocks.provisionPatientAccount.mockResolvedValue({ accountId: "account-a", patientId, username: "012345678901", mustChangePassword: true });
  render(<PatientAccountIssuance patientId={patientId} />);
  expect(screen.getByRole("link", { name: /Tiếp tục đăng ký khám/ })).toHaveAttribute("href", `/appointments/reception?patientId=${patientId}`);
  fireEvent.click(screen.getByRole("button", { name: "Cấp tài khoản bệnh nhân" }));
  await screen.findByText("012345678901");
  expect(mocks.provisionPatientAccount).toHaveBeenCalledWith(patientId, expect.any(String));
  expect(screen.getByText(/chưa cần đăng nhập/i)).toBeInTheDocument();
});

it("retains the same issuance key after timeout and reopening", async () => {
  mocks.provisionPatientAccount.mockRejectedValue(new Error("Timeout"));
  const first = render(<PatientAccountIssuance patientId={patientId} />);
  fireEvent.click(screen.getByRole("button", { name: "Cấp tài khoản bệnh nhân" }));
  await screen.findByRole("alert");
  const key = mocks.provisionPatientAccount.mock.calls[0][1];
  first.unmount();
  mocks.provisionPatientAccount.mockResolvedValue({ accountId: "account-a", patientId, username: "012345678901", mustChangePassword: true });
  render(<PatientAccountIssuance patientId={patientId} />);
  fireEvent.click(screen.getByRole("button", { name: "Cấp tài khoản bệnh nhân" }));
  await waitFor(() => expect(mocks.provisionPatientAccount).toHaveBeenCalledTimes(2));
  expect(mocks.provisionPatientAccount.mock.calls[1][1]).toBe(key);
});
