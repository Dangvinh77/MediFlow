import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { staffContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({ role: "ADMIN", session: "a", deactivateStaff: vi.fn(), staffMember: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: mocks.replace }) }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, getSessionKey: () => mocks.session, subscribeToAuthChanges: () => () => {} }));
vi.mock("../api", () => ({ organizationApi: mocks }));
import { DeactivateStaffAction } from "./DeactivateStaffAction";
const member = { ...staffContractFixture, active: true };
beforeEach(() => { vi.clearAllMocks(); mocks.role = "ADMIN"; mocks.session = "a"; });

it("requires explicit confirmation and returns the server's inactive profile", async () => {
  const user = userEvent.setup(); const changed = vi.fn();
  mocks.deactivateStaff.mockResolvedValue({ ...member, active: false });
  render(<DeactivateStaffAction staff={member} onChanged={changed} />);
  const trigger = screen.getByRole("button", { name: "Ngừng làm việc" });
  await user.click(trigger);
  expect(mocks.deactivateStaff).not.toHaveBeenCalled();
  expect(screen.getByRole("alertdialog")).toHaveFocus();
  await user.keyboard("{Escape}");
  expect(trigger).toHaveFocus();
  await user.click(trigger);
  await user.click(screen.getByRole("button", { name: "Xác nhận ngừng làm việc" }));
  expect(mocks.deactivateStaff).toHaveBeenCalledExactlyOnceWith(member.staffId);
  expect(changed).toHaveBeenCalledWith({ ...member, active: false });
});

it.each(["DOCTOR", "NURSE", "PATIENT"])("hides the action for %s", role => {
  mocks.role = role; render(<DeactivateStaffAction staff={member} onChanged={vi.fn()} />);
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
});
it("hides the action for inactive staff", () => {
  render(<DeactivateStaffAction staff={{ ...member, active: false }} onChanged={vi.fn()} />);
  expect(screen.queryByRole("button")).not.toBeInTheDocument();
});
it("keeps a refused command recoverable without changing the profile", async () => {
  const changed = vi.fn(); const user = userEvent.setup();
  mocks.deactivateStaff.mockRejectedValue(new ApiRequestError("Hãy thay trưởng khoa trước.", 409, "STAFF_DEPARTMENT_HEAD", [], "trace"));
  render(<DeactivateStaffAction staff={member} onChanged={changed} />);
  await user.click(screen.getByRole("button", { name: "Ngừng làm việc" }));
  await user.click(screen.getByRole("button", { name: "Xác nhận ngừng làm việc" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Hãy thay trưởng khoa trước.");
  expect(changed).not.toHaveBeenCalled();
});
it("blocks duplicate writes and ignores a result from an old session", async () => {
  let resolve!: (value: typeof member) => void;
  mocks.deactivateStaff.mockReturnValue(new Promise(done => { resolve = done; }));
  const changed = vi.fn(); const user = userEvent.setup();
  render(<DeactivateStaffAction staff={member} onChanged={changed} />);
  await user.click(screen.getByRole("button", { name: "Ngừng làm việc" }));
  const confirm = screen.getByRole("button", { name: "Xác nhận ngừng làm việc" });
  fireEvent.click(confirm); fireEvent.click(confirm);
  expect(mocks.deactivateStaff).toHaveBeenCalledTimes(1);
  mocks.session = "b";
  resolve({ ...member, active: false });
  await waitFor(() => expect(changed).not.toHaveBeenCalled());
});
it("reads back an unknown result without repeating the mutation", async () => {
  const changed = vi.fn(); const user = userEvent.setup();
  mocks.deactivateStaff.mockRejectedValue(new Error("Network unavailable"));
  mocks.staffMember.mockResolvedValue({ ...member, active: false });
  render(<DeactivateStaffAction staff={member} onChanged={changed} />);
  await user.click(screen.getByRole("button", { name: "Ngừng làm việc" }));
  await user.click(screen.getByRole("button", { name: "Xác nhận ngừng làm việc" }));
  await user.click(await screen.findByRole("button", { name: "Kiểm tra trạng thái" }));
  expect(changed).toHaveBeenCalledWith({ ...member, active: false });
  expect(mocks.deactivateStaff).toHaveBeenCalledTimes(1);
});
