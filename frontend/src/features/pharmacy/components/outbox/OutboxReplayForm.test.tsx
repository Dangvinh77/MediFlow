import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
const mocks = vi.hoisted(() => ({ role: "ADMIN" as Role | null, replay: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
vi.mock("../../api", () => ({ pharmacyApi: { replayOutbox: mocks.replay } }));
import { OutboxReplayForm } from "./OutboxReplayForm";

const eventId = "00000000-0000-4000-8000-000000000001";
function prepare() {
  fireEvent.change(screen.getByLabelText("Mã yêu cầu"), { target: { value: "  " + eventId + "  " } });
  fireEvent.submit(screen.getByLabelText("Mã yêu cầu").closest("form")!);
}
function confirm() { fireEvent.click(screen.getByRole("button", { name: "Xác nhận khôi phục" })); }

describe("OutboxReplayForm", () => {
  beforeEach(() => { mocks.role = "ADMIN"; mocks.replay.mockReset(); mocks.router.replace.mockReset(); });
  it.each(["DOCTOR", "PHARMACIST", null] as const)("does not expose recovery for role %s", (role) => {
    mocks.role = role; render(<OutboxReplayForm />);
    expect(screen.queryByLabelText("Mã yêu cầu")).not.toBeInTheDocument();
    expect(mocks.replay).not.toHaveBeenCalled();
  });
  it("rejects invalid IDs and restores focus to the input", () => {
    render(<OutboxReplayForm />);
    fireEvent.submit(screen.getByLabelText("Mã yêu cầu").closest("form")!);
    expect(screen.getByLabelText("Mã yêu cầu")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("Mã yêu cầu")).toHaveFocus();
    expect(mocks.replay).not.toHaveBeenCalled();
  });
  it("uses an inline labelled section and allows Escape or cancellation without replay", async () => {
    render(<OutboxReplayForm />); prepare();
    expect(screen.getByRole("region", { name: "Xác nhận khôi phục đồng bộ" })).toBeVisible();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xác nhận khôi phục" })).toHaveFocus();
    await userEvent.keyboard("{Escape}");
    expect(screen.getByLabelText("Mã yêu cầu")).toHaveFocus();
    expect(screen.queryByRole("region", { name: "Xác nhận khôi phục đồng bộ" })).not.toBeInTheDocument();
    prepare(); await userEvent.click(screen.getByRole("button", { name: "Quay lại" }));
    expect(screen.getByLabelText("Mã yêu cầu")).toHaveFocus();
    expect(mocks.replay).not.toHaveBeenCalled();
  });
  it("submits once while pending and describes only the server replay outcome", async () => {
    let resolve!: (value: { eventId: string; replayed: boolean }) => void;
    mocks.replay.mockReturnValue(new Promise(done => { resolve = done; }));
    render(<OutboxReplayForm />); prepare();
    const button = screen.getByRole("button", { name: "Xác nhận khôi phục" });
    fireEvent.click(button); fireEvent.click(button);
    expect(mocks.replay).toHaveBeenCalledTimes(1); expect(mocks.replay).toHaveBeenCalledWith(eventId);
    expect(screen.getByRole("button", { name: "Đang gửi yêu cầu…" })).toBeDisabled();
    fireEvent.submit(screen.getByLabelText("Mã yêu cầu").closest("form")!);
    expect(screen.getByRole("button", { name: "Đang gửi yêu cầu…" })).toBeDisabled();
    await act(async () => resolve({ eventId, replayed: true }));
    expect(screen.getByRole("heading", { name: "Đã gửi yêu cầu đồng bộ lại" })).toBeVisible();
    expect(screen.getByText(/Kết quả nghiệp vụ có thể chưa cập nhật ngay/)).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Khôi phục yêu cầu khác" }));
    expect(screen.getByLabelText("Mã yêu cầu")).toHaveValue("");
    expect(mocks.replay).toHaveBeenCalledTimes(1);
  });
  it("does not claim requeue success when replayed is false", async () => {
    mocks.replay.mockResolvedValue({ eventId, replayed: false });
    render(<OutboxReplayForm />); prepare(); confirm();
    expect(await screen.findByRole("heading", { name: "Chưa gửi được yêu cầu đồng bộ lại" })).toBeVisible();
  });
  it("focuses the event ID on 404 and includes correlation evidence", async () => {
    mocks.replay.mockRejectedValue(new ApiRequestError("Missing", 404, "OUTBOX_EVENT_NOT_FOUND", [], "corr-404"));
    render(<OutboxReplayForm />); prepare(); confirm();
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-404");
    await waitFor(() => expect(screen.getByLabelText("Mã yêu cầu")).toHaveFocus());
    expect(mocks.replay).toHaveBeenCalledTimes(1);
  });
  it("preserves forbidden context and prevents a misleading retry", async () => {
    mocks.replay.mockRejectedValue(new ApiRequestError("Denied", 403, "FORBIDDEN", [], "corr-denied"));
    render(<OutboxReplayForm />); prepare(); confirm();
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-denied");
    await waitFor(() => expect(screen.getByRole("alert")).toHaveFocus());
    expect(screen.getByRole("button", { name: "Xem xác nhận" })).toBeDisabled();
    fireEvent.submit(screen.getByLabelText("Mã yêu cầu").closest("form")!);
    expect(screen.queryByRole("button", { name: "Xác nhận khôi phục" })).not.toBeInTheDocument();
    expect(mocks.replay).toHaveBeenCalledTimes(1);
  });
  it("keeps a network failure uncertain and requires a fresh confirmation before retry", async () => {
    mocks.replay.mockRejectedValueOnce(new Error("Mất kết nối; chưa xác định kết quả")).mockResolvedValueOnce({ eventId, replayed: true });
    render(<OutboxReplayForm />); prepare(); confirm();
    expect(await screen.findByRole("alert")).toHaveTextContent("chưa xác định kết quả");
    expect(screen.queryByRole("heading", { name: "Đã gửi yêu cầu đồng bộ lại" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Xem xác nhận" }));
    expect(mocks.replay).toHaveBeenCalledTimes(1); confirm();
    await screen.findByRole("heading", { name: "Đã gửi yêu cầu đồng bộ lại" });
    expect(mocks.replay).toHaveBeenCalledTimes(2);
  });
  it("redirects expired sessions to login", async () => {
    mocks.replay.mockRejectedValue(new ApiRequestError("Hết phiên", 401));
    render(<OutboxReplayForm />); prepare(); confirm();
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
  });
  it.each([408, 500, 503, 200])("preserves HTTP %s as an unknown replay outcome and requires deliberate reconfirmation", async status => {
    mocks.replay.mockRejectedValueOnce(new ApiRequestError("Mất phản hồi", status, "UNKNOWN_RESULT", [], "replay-timeout"))
      .mockResolvedValueOnce({ eventId, replayed: true });
    render(<OutboxReplayForm />); prepare(); confirm();
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Chưa xác định kết quả replay"); expect(alert).toHaveTextContent("replay-timeout");
    await waitFor(() => expect(alert).toHaveFocus());
    expect(screen.getByLabelText("Mã yêu cầu")).toHaveValue("  " + eventId + "  ");
    expect(mocks.replay).toHaveBeenCalledTimes(1);
    await userEvent.keyboard("{Enter}"); expect(mocks.replay).toHaveBeenCalledTimes(1);
    await userEvent.click(screen.getByRole("button", { name: "Xem xác nhận" }));
    expect(screen.getByRole("region", { name: "Xác nhận khôi phục đồng bộ" })).toHaveTextContent(eventId);
    expect(mocks.replay).toHaveBeenCalledTimes(1); confirm();
    await screen.findByRole("heading", { name: "Đã gửi yêu cầu đồng bộ lại" });
    expect(mocks.replay.mock.calls).toEqual([[eventId], [eventId]]);
  });
});
