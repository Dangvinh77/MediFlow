import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { accountContractFixture } from "../contract.fixture";
import { ApiRequestError } from "@/lib/api";

const mocks = vi.hoisted(() => ({ account: vi.fn(), updateAccountStatus: vi.fn(), session: vi.fn<() => string | null>(() => "session-a"), router: { replace: vi.fn() } }));
vi.mock("../api", () => ({ organizationApi: mocks }));
vi.mock("@/lib/auth", () => ({ getSessionKey: mocks.session }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("./PasswordForm", () => ({ PasswordForm: ({ accountId }: { accountId: string }) => <div>Đổi mật khẩu {accountId}</div> }));
import { AccountDetail } from "./AccountDetail";

beforeEach(() => { vi.clearAllMocks(); mocks.account.mockReset(); mocks.updateAccountStatus.mockReset(); mocks.session.mockReturnValue("session-a"); mocks.account.mockResolvedValue(accountContractFixture); });

it("loads detail by exact route id and places password and status actions there", async () => {
  render(<AccountDetail accountId={accountContractFixture.accountId} />);
  await screen.findByRole("heading", { name: accountContractFixture.username });
  expect(mocks.account).toHaveBeenCalledWith(accountContractFixture.accountId);
  expect(screen.getByText(`Đổi mật khẩu ${accountContractFixture.accountId}`)).toBeVisible();
  expect(screen.getByRole("button", { name: "Active tài khoản" })).toBeVisible();
  expect(screen.getByRole("link", { name: /Bệnh nhân:/ })).toHaveAttribute("href", `/patients/${accountContractFixture.patientId}`);
});

it("confirms reactivation replacing another active account and prevents duplicate commands", async () => {
  mocks.updateAccountStatus.mockRejectedValueOnce(new ApiRequestError("other.login sẽ bị inactive", 409, "ACCOUNT_OWNER_ACTIVE"))
    .mockResolvedValueOnce({ ...accountContractFixture, isActive: true });
  render(<AccountDetail accountId={accountContractFixture.accountId} />);
  fireEvent.click(await screen.findByRole("button", { name: "Active tài khoản" }));
  expect(await screen.findByRole("alertdialog")).toHaveTextContent("other.login");
  expect(mocks.updateAccountStatus).toHaveBeenCalledTimes(1);
  const button = screen.getByRole("button", { name: "Tiếp tục active tài khoản" });
  act(() => { fireEvent.click(button); fireEvent.click(button); });
  await screen.findByRole("status");
  expect(mocks.updateAccountStatus).toHaveBeenCalledTimes(2);
  expect(mocks.updateAccountStatus).toHaveBeenLastCalledWith(accountContractFixture.accountId, true, true);
  expect(screen.getByRole("button", { name: "Inactive tài khoản" })).toBeVisible();
});

it("reloads an unknown status outcome before allowing another mutation", async () => {
  mocks.updateAccountStatus.mockRejectedValue(new Error("Lost response"));
  render(<AccountDetail accountId={accountContractFixture.accountId} />);
  fireEvent.click(await screen.findByRole("button", { name: "Active tài khoản" }));
  await screen.findByRole("alert");
  expect(screen.getByRole("button", { name: "Active tài khoản" })).toBeDisabled();
  mocks.account.mockResolvedValue({ ...accountContractFixture, isActive: true });
  fireEvent.click(screen.getByRole("button", { name: "Tải lại trạng thái" }));
  expect(await screen.findByRole("button", { name: "Inactive tài khoản" })).toBeEnabled();
  expect(mocks.updateAccountStatus).toHaveBeenCalledTimes(1);
});

it("retries a failed detail request and ignores a late result from an old session", async () => {
  let resolve!: (value: typeof accountContractFixture) => void;
  mocks.account.mockRejectedValueOnce(new Error("Unavailable")).mockReturnValueOnce(new Promise(done => { resolve = done; }));
  render(<AccountDetail accountId={accountContractFixture.accountId} />);
  await screen.findByRole("alert"); fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
  await waitFor(() => expect(mocks.account).toHaveBeenCalledTimes(2));
  mocks.session.mockReturnValue("session-b"); await act(async () => resolve(accountContractFixture));
  expect(screen.queryByText(accountContractFixture.username)).not.toBeInTheDocument();
});


it("links a manager to their exact scope workspace only when scheduling is enabled", async () => {
  mocks.account.mockResolvedValue({ ...accountContractFixture, role: "MANAGER", patientId: null });
  const view = render(<AccountDetail accountId={accountContractFixture.accountId} schedulingEnabled />);
  expect(await screen.findByRole("link", { name: "Quản lý phạm vi khoa" })).toHaveAttribute("href", `/appointments/schedule?managerAccountId=${accountContractFixture.accountId}#manager-scope`);
  view.rerender(<AccountDetail accountId={accountContractFixture.accountId} schedulingEnabled={false} />);
  expect(screen.queryByRole("link", { name: "Quản lý phạm vi khoa" })).not.toBeInTheDocument();
});
