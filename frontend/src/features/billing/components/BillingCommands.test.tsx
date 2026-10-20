import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { clearSession, storeSession } from "@/lib/session";
const mocks = vi.hoisted(() => ({ create: vi.fn(), replay: vi.fn(), push: vi.fn(), back: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks }));
vi.mock("../api", () => ({ billingApi: { create: mocks.create, replayOutboxEvent: mocks.replay } }));
import { InvoiceForm } from "./InvoiceForm";
import { OutboxReplayForm } from "./OutboxReplayForm";
const id = "00000000-0000-4000-8000-000000000001";
beforeEach(() => { vi.clearAllMocks(); act(() => { clearSession(); storeSession({ accessToken: "test-a", refreshToken: "r", role: "ADMIN" }); }); });
describe("Billing command safety", () => {
  it("blocks another invoice creation after an ambiguous response", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("timeout", 408, undefined, [], "trace-invoice"));
    render(<InvoiceForm />);
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: id } });
    const button = screen.getByRole("button", { name: "Lập hóa đơn" });
    fireEvent.submit(button.closest("form")!);
    expect(await screen.findByRole("alert")).toHaveTextContent("trace-invoice");
    fireEvent.submit(button.closest("form")!);
    expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();
  });
  it("prevents duplicate outbox submissions and does not claim consumer delivery", async () => {
    let resolve!: (value: { eventId: string; replayed: boolean }) => void;
    mocks.replay.mockReturnValue(new Promise((done) => { resolve = done; }));
    render(<OutboxReplayForm />);
    fireEvent.change(screen.getByLabelText("Mã event", { exact: false }), { target: { value: id } });
    const button = screen.getByRole("button", { name: "Phát lại event" });
    fireEvent.submit(button.closest("form")!);
    fireEvent.submit(button.closest("form")!);
    expect(mocks.replay).toHaveBeenCalledTimes(1);
    await act(async () => resolve({ eventId: id, replayed: false }));
    expect(screen.getByRole("status")).toHaveTextContent(/không.*phát lại/i);
  });
});
