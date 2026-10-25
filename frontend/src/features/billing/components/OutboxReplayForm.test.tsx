import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { clearSession, storeSession } from "@/lib/session";

const mocks = vi.hoisted(() => ({ replay: vi.fn(), push: vi.fn(), back: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks }));
vi.mock("../api", () => ({ billingApi: { replayOutboxEvent: mocks.replay } }));

import { OutboxReplayForm } from "./OutboxReplayForm";

const eventId = "00000000-0000-4000-8000-000000000001";

function fillEventId(value: string) {
  fireEvent.change(screen.getByLabelText("Mã event", { exact: false }), { target: { value } });
}

function submit() {
  fireEvent.click(screen.getByRole("button", { name: "Phát lại event" }));
}

beforeEach(() => {
  vi.clearAllMocks();
  act(() => storeSession({ accessToken: "session-a", refreshToken: "refresh-a", role: "ADMIN" }));
});

afterEach(() => act(() => clearSession()));

describe("OutboxReplayForm", () => {
  it("rejects a non-UUID event id without calling the API", () => {
    render(<OutboxReplayForm />);
    fillEventId("not-a-uuid");
    submit();

    expect(screen.getByRole("alert")).toHaveTextContent("UUID hợp lệ");
    expect(mocks.replay).not.toHaveBeenCalled();
  });

  it("reports a successful requeue without claiming downstream delivery", async () => {
    mocks.replay.mockResolvedValue({ eventId, replayed: true });
    render(<OutboxReplayForm />);
    fillEventId(eventId);
    submit();

    expect(await screen.findByRole("status")).toHaveTextContent("Đã đưa event vào hàng đợi phát lại; chưa xác nhận bên nhận xử lý");
    expect(screen.getByRole("status")).toHaveTextContent(eventId);
  });

  it("reports when the event was not replayed", async () => {
    mocks.replay.mockResolvedValue({ eventId, replayed: false });
    render(<OutboxReplayForm />);
    fillEventId(eventId);
    submit();

    expect(await screen.findByRole("status")).toHaveTextContent("Event không được phát lại");
  });

  it("shows the server rejection with its correlation id and leaves the form usable", async () => {
    mocks.replay.mockRejectedValue(new ApiRequestError("Event không tồn tại.", 404, "BILLING_OUTBOX_EVENT_NOT_FOUND", [], "trace-replay"));
    render(<OutboxReplayForm />);
    fillEventId(eventId);
    submit();

    expect(await screen.findByText("trace-replay", { exact: false })).toBeVisible();
    expect(screen.getByText("Event không tồn tại.")).toBeVisible();
    expect(screen.getByRole("button", { name: "Phát lại event" })).not.toBeDisabled();
    expect(screen.getByLabelText("Mã event", { exact: false })).not.toBeDisabled();
  });

  it("locks the input and submit after an uncertain outcome", async () => {
    mocks.replay.mockRejectedValue(new ApiRequestError("timeout", 408, undefined, [], "trace-timeout"));
    render(<OutboxReplayForm />);
    fillEventId(eventId);
    submit();

    expect(await screen.findByText(/Chưa xác định thao tác đã được lưu hay chưa/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Phát lại event" })).toBeDisabled();
    expect(screen.getByLabelText("Mã event", { exact: false })).toBeDisabled();
  });

  it("navigates back when the back button is clicked", () => {
    render(<OutboxReplayForm />);
    fireEvent.click(screen.getByRole("button", { name: "Quay lại" }));

    expect(mocks.back).toHaveBeenCalledTimes(1);
  });

  it("clears a prior result before issuing a new replay", async () => {
    mocks.replay.mockResolvedValueOnce({ eventId, replayed: true });
    render(<OutboxReplayForm />);
    fillEventId(eventId);
    submit();
    await screen.findByRole("status");

    let resolveSecond!: (value: { eventId: string; replayed: boolean }) => void;
    mocks.replay.mockReturnValueOnce(new Promise((done) => { resolveSecond = done; }));
    const otherEventId = "00000000-0000-4000-8000-000000000002";
    fillEventId(otherEventId);
    submit();

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    await act(async () => resolveSecond({ eventId: otherEventId, replayed: true }));
    expect(screen.getByRole("status")).toHaveTextContent(otherEventId);
  });
});
