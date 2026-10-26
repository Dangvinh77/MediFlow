import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { staffContractFixture as staff } from "../contract.fixture";
import { ApiRequestError } from "@/lib/api";
const mocks = vi.hoisted(() => ({ update: vi.fn(), replace: vi.fn(), session: "session-a", saved: vi.fn(), dirty: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: mocks.replace }) }));
vi.mock("@/lib/auth", () => ({ getSessionKey: () => mocks.session }));
vi.mock("../api", () => ({ organizationApi: { updateOwnContact: mocks.update } }));
vi.mock("../use-unsaved-changes", () => ({ useUnsavedChanges: () => ({ markDirty: mocks.dirty, markSaved: mocks.saved }) }));
import { OwnStaffContactForm } from "./OwnStaffContactForm";
beforeEach(() => { vi.clearAllMocks(); mocks.session = "session-a"; });
it("sends only editable contact fields and retains the returned profile", async () => {
  mocks.update.mockResolvedValue({ ...staff, email: "new@example.com" });
  const onSaved = vi.fn(); render(<OwnStaffContactForm staff={staff} onSaved={onSaved} />);
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "new@example.com" } });
  fireEvent.change(screen.getByLabelText("Địa chỉ"), { target: { value: "  Hanoi  " } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin liên hệ" }));
  await screen.findByText("Đã cập nhật thông tin liên hệ.");
  expect(mocks.update).toHaveBeenCalledExactlyOnceWith(staff.staffId, { phoneNumber: staff.phoneNumber, email: "new@example.com", address: "Hanoi" });
  expect(onSaved).toHaveBeenCalledWith({ ...staff, email: "new@example.com" });
  expect(mocks.saved).toHaveBeenCalledOnce();
  expect(screen.queryByLabelText("Chức danh")).not.toBeInTheDocument();
});
it("does not submit twice while the first save is pending", async () => {
  let resolve!: (value: typeof staff) => void;
  mocks.update.mockReturnValue(new Promise<typeof staff>(done => { resolve = done; }));
  render(<OwnStaffContactForm staff={staff} onSaved={vi.fn()} />);
  const form = screen.getByLabelText("Email").closest("form")!;
  fireEvent.submit(form); fireEvent.submit(form);
  expect(mocks.update).toHaveBeenCalledOnce();
  await act(async () => resolve(staff));
});
it("ignores a response from a previous session", async () => {
  let resolve!: (value: typeof staff) => void;
  mocks.update.mockReturnValue(new Promise<typeof staff>(done => { resolve = done; }));
  const onSaved = vi.fn(); render(<OwnStaffContactForm staff={staff} onSaved={onSaved} />);
  fireEvent.submit(screen.getByLabelText("Email").closest("form")!);
  mocks.session = "session-b";
  await act(async () => resolve(staff));
  expect(onSaved).not.toHaveBeenCalled(); expect(mocks.saved).not.toHaveBeenCalled();
});
it("redirects an expired session without retrying the write", async () => {
  mocks.update.mockRejectedValue(new ApiRequestError("expired", 401));
  render(<OwnStaffContactForm staff={staff} onSaved={vi.fn()} />);
  fireEvent.submit(screen.getByLabelText("Email").closest("form")!);
  await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/login"));
  expect(mocks.update).toHaveBeenCalledOnce();
});
