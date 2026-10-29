import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";

const mocks = vi.hoisted(() => ({ session: vi.fn<() => string | null>(() => "session-a") }));
vi.mock("@/lib/auth", () => ({ getSessionKey: mocks.session }));

import { SaveOutcomeRecovery } from "./SaveOutcomeRecovery";

beforeEach(() => mocks.session.mockReturnValue("session-a"));

it("serializes reads and ignores a completion after unmount", async () => {
  let resolve!: (value: string) => void;
  const read = vi.fn(() => new Promise<string>(done => { resolve = done; }));
  const matches = vi.fn(() => true);
  const verified = vi.fn();
  const view = render(<SaveOutcomeRecovery read={read} matches={matches} onVerified={verified} onUnauthorized={vi.fn()} />);
  const button = screen.getByRole("button", { name: "Kiểm tra dữ liệu đã lưu" });
  act(() => { fireEvent.click(button); fireEvent.click(button); });
  expect(read).toHaveBeenCalledTimes(1);
  view.unmount();
  await act(async () => { resolve("saved"); });
  expect(matches).not.toHaveBeenCalled();
  expect(verified).not.toHaveBeenCalled();
});

it("ignores a read completed under a different session", async () => {
  let resolve!: (value: string) => void;
  const matches = vi.fn(() => true);
  render(<SaveOutcomeRecovery read={() => new Promise<string>(done => { resolve = done; })}
    matches={matches} onVerified={vi.fn()} onUnauthorized={vi.fn()} />);
  fireEvent.click(screen.getByRole("button", { name: "Kiểm tra dữ liệu đã lưu" }));
  mocks.session.mockReturnValue("session-b");
  await act(async () => { resolve("saved"); });
  expect(matches).not.toHaveBeenCalled();
  expect(screen.queryByRole("button", { name: "Xác nhận kết quả đã lưu" })).not.toBeInTheDocument();
});

it("refuses confirmation when the login changes after a successful read", async () => {
  const verified = vi.fn();
  render(<SaveOutcomeRecovery read={async () => "saved"} matches={() => true} onVerified={verified} onUnauthorized={vi.fn()} />);
  fireEvent.click(screen.getByRole("button", { name: "Kiểm tra dữ liệu đã lưu" }));
  const confirm = await screen.findByRole("button", { name: "Xác nhận kết quả đã lưu" });
  mocks.session.mockReturnValue("session-b");
  fireEvent.click(confirm);
  expect(verified).not.toHaveBeenCalled();
});

it.each([null, "session-b"])("handles an expired read without redirecting a newer login (%s)", async session => {
  const expired = vi.fn();
  render(<SaveOutcomeRecovery read={async () => {
    mocks.session.mockReturnValue(session);
    throw new ApiRequestError("Expired", 401);
  }} matches={() => true} onVerified={vi.fn()} onUnauthorized={expired} />);
  fireEvent.click(screen.getByRole("button", { name: "Kiểm tra dữ liệu đã lưu" }));
  await act(async () => {});
  expect(expired).toHaveBeenCalledTimes(session === null ? 1 : 0);
});

it("allows read retries after errors without confirming a mismatched result", async () => {
  const read = vi.fn().mockRejectedValueOnce(new Error("Read unavailable")).mockResolvedValueOnce("different").mockResolvedValue("saved");
  const verified = vi.fn();
  render(<SaveOutcomeRecovery<string> read={read} matches={value => value === "saved"} onVerified={verified} onUnauthorized={vi.fn()} />);
  const check = () => fireEvent.click(screen.getByRole("button", { name: "Kiểm tra dữ liệu đã lưu" }));
  check();
  await screen.findByText(/Read unavailable/);
  check();
  await screen.findByText(/Thông tin hiện tại khác bản nháp/);
  expect(screen.queryByRole("button", { name: "Xác nhận kết quả đã lưu" })).not.toBeInTheDocument();
  check();
  fireEvent.click(await screen.findByRole("button", { name: "Xác nhận kết quả đã lưu" }));
  expect(verified).toHaveBeenCalledExactlyOnceWith("saved");
});
