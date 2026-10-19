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
  fireEvent.change(screen.getByLabelText("Event ID"), { target: { value: "  " + eventId + "  " } });
  fireEvent.submit(screen.getByLabelText("Event ID").closest("form")!);
}
function confirm() { fireEvent.click(screen.getByRole("button", { name: "Xác nhận replay" })); }

describe("OutboxReplayForm", () => {
  beforeEach(() => { mocks.role = "ADMIN"; mocks.replay.mockReset(); mocks.router.replace.mockReset(); });
  it.each(["DOCTOR", "PHARMACIST", null] as const)("does not expose recovery for role %s", (role) => {
    mocks.role = role; render(<OutboxReplayForm />);
    expect(screen.queryByLabelText("Event ID")).not.toBeInTheDocument();
    expect(mocks.replay).not.toHaveBeenCalled();
  });
  it("rejects invalid IDs and restores focus to the input", () => {
    render(<OutboxReplayForm />);
    fireEvent.submit(screen.getByLabelText("Event ID").closest("form")!);
    expect(screen.getByLabelText("Event ID")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("Event ID")).toHaveFocus();
    expect(mocks.replay).not.toHaveBeenCalled();
  });
  it("uses an inline labelled section and allows Escape or cancellation without replay", async () => {
    render(<OutboxReplayForm />); prepare();
    expect(screen.getByRole("region", { name: "Xác nhận replay event" })).toBeVisible();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xác nhận replay" })).toHaveFocus();
    await userEvent.keyboard("{Escape}");
    expect(screen.getByLabelText("Event ID")).toHaveFocus();
    expect(screen.queryByRole("region", { name: "Xác nhận replay event" })).not.toBeInTheDocument();
    prepare(); await userEvent.click(screen.getByRole("button", { name: "Quay lại" }));
    expect(screen.getByLabelText("Event ID")).toHaveFocus();
    expect(mocks.replay).not.toHaveBeenCalled();
  });
  it("submits once while pending and describes only the server replay outcome", async () => {
    let resolve!: (value: { eventId: string; replayed: boolean }) => void;
    mocks.replay.mockReturnValue(new Promise(done => { resolve = done; }));
    render(<OutboxReplayForm />); prepare();
    const button = screen.getByRole("button", { name: "Xác nhận replay" });
    fireEvent.click(button); fireEvent.click(button);
    expect(mocks.replay).toHaveBeenCalledTimes(1); expect(mocks.replay).toHaveBeenCalledWith(eventId);
    expect(screen.getByRole("button", { name: "Đang replay…" })).toBeDisabled();
    fireEvent.submit(screen.getByLabelText("Event ID").closest("form")!);
    expect(screen.getByRole("button", { name: "Đang replay…" })).toBeDisabled();
    await act(async () => resolve({ eventId, replayed: true }));
    expect(screen.getByRole("heading", { name: "Đã đưa event trở lại hàng đợi replay" })).toBeVisible();
    expect(screen.getByText(/consumer xử lý thành công cần/)).toBeVisible();
    await userEvent.click(screen.getByRole("button", { name: "Replay event khác" }));
    expect(screen.getByLabelText("Event ID")).toHaveValue("");
    expect(mocks.replay).toHaveBeenCalledTimes(1);
  });
  it("does not claim requeue success when replayed is false", async () => {
    mocks.replay.mockResolvedValue({ eventId, replayed: false });
    render(<OutboxReplayForm />); prepare(); confirm();
    expect(await screen.findByRole("heading", { name: "Event chưa được đưa lại vào hàng đợi" })).toBeVisible();
  });
  it("focuses the event ID on 404 and includes correlation evidence", async () => {
    mocks.replay.mockRejectedValue(new ApiRequestError("Missing", 404, "OUTBOX_EVENT_NOT_FOUND", [], "corr-404"));
    render(<OutboxReplayForm />); prepare(); confirm();
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-404");
    await waitFor(() => expect(screen.getByLabelText("Event ID")).toHaveFocus());
    expect(mocks.replay).toHaveBeenCalledTimes(1);
  });
  it("preserves forbidden context and prevents a misleading retry", async () => {
    mocks.replay.mockRejectedValue(new ApiRequestError("Denied", 403, "FORBIDDEN", [], "corr-denied"));
    render(<OutboxReplayForm />); prepare(); confirm();
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-denied");
    await waitFor(() => expect(screen.getByRole("alert")).toHaveFocus());
    expect(screen.getByRole("button", { name: "Xem xác nhận" })).toBeDisabled();
    fireEvent.submit(screen.getByLabelText("Event ID").closest("form")!);
    expect(screen.queryByRole("button", { name: "Xác nhận replay" })).not.toBeInTheDocument();
    expect(mocks.replay).toHaveBeenCalledTimes(1);
  });
  it("keeps a network failure uncertain and requires a fresh confirmation before retry", async () => {
    mocks.replay.mockRejectedValueOnce(new Error("Mất kết nối; chưa xác định kết quả")).mockResolvedValueOnce({ eventId, replayed: true });
    render(<OutboxReplayForm />); prepare(); confirm();
    expect(await screen.findByRole("alert")).toHaveTextContent("chưa xác định kết quả");
    expect(screen.queryByRole("heading", { name: "Đã đưa event trở lại hàng đợi replay" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Xem xác nhận" }));
    expect(mocks.replay).toHaveBeenCalledTimes(1); confirm();
    await screen.findByRole("heading", { name: "Đã đưa event trở lại hàng đợi replay" });
    expect(mocks.replay).toHaveBeenCalledTimes(2);
  });
  it("redirects expired sessions to login", async () => {
    mocks.replay.mockRejectedValue(new ApiRequestError("Hết phiên", 401));
    render(<OutboxReplayForm />); prepare(); confirm();
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
  });
});
