import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { ConsultationNotesPanel } from "./ConsultationNotesPanel";

const mocks = vi.hoisted(() => ({
  list: vi.fn(), add: vi.fn(),
  sessionKey: "doctor-session" as string | null,
  listeners: [] as Array<() => void>,
  notifyAuthChange: () => { for (const listener of [...mocks.listeners]) listener(); },
  subscribe: (listener: () => void) => {
    mocks.listeners.push(listener);
    return () => { mocks.listeners = mocks.listeners.filter((item) => item !== listener); };
  },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => mocks.sessionKey, subscribeToAuthChanges: mocks.subscribe }));
vi.mock("../api", () => ({ medicalRecordApi: { consultationNotes: mocks.list, addConsultationNote: mocks.add } }));

const note = { noteId: "note-a", authorStaffId: "doctor-b", opinion: "Nên theo dõi", createdAt: "2026-11-10T03:00:00Z" };

describe("ConsultationNotesPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.sessionKey = "doctor-session";
    mocks.listeners = [];
    mocks.list.mockResolvedValue([]);
    mocks.add.mockResolvedValue(note);
  });

  it("shows loading, empty, forbidden, hidden and a retryable outage", async () => {
    let resolve!: (value: unknown) => void;
    mocks.list.mockReturnValueOnce(new Promise((done) => { resolve = done; }));
    const loading = render(<ConsultationNotesPanel recordId="record-a" canAddConsultationNote={false} />);
    expect(screen.getByText(/Đang tải ghi chú hội chẩn/)).toBeVisible();
    resolve([]);
    expect(await screen.findByText(/Chưa có ghi chú hội chẩn/)).toBeVisible();
    loading.unmount();

    mocks.list.mockRejectedValueOnce(new ApiRequestError("Cấm", 403));
    const denied = render(<ConsultationNotesPanel recordId="record-a" canAddConsultationNote={false} />);
    expect(await screen.findByText(/Bạn không có quyền xem ghi chú hội chẩn/)).toBeVisible();
    denied.unmount();

    mocks.list.mockRejectedValueOnce(new ApiRequestError("Tắt", 404, "RESOURCE_NOT_FOUND"));
    const hidden = render(<ConsultationNotesPanel recordId="record-a" canAddConsultationNote={false} />);
    await waitFor(() => expect(screen.queryByRole("region", { name: "Hội chẩn" })).not.toBeInTheDocument());
    hidden.unmount();

    mocks.list.mockRejectedValueOnce(new Error("offline"));
    mocks.list.mockResolvedValueOnce([]);
    const unavailable = render(<ConsultationNotesPanel recordId="record-a" canAddConsultationNote={false} />);
    fireEvent.click(await screen.findByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText(/Chưa có ghi chú hội chẩn/)).toBeVisible();
    unavailable.unmount();
  });

  it("shows author and Vietnam local time, and only offers a note form with capability", async () => {
    mocks.list.mockResolvedValueOnce([note]);
    const view = render(<ConsultationNotesPanel recordId="record-a" canAddConsultationNote={false} />);
    expect(await screen.findByText("Nên theo dõi")).toBeVisible();
    expect(screen.getByText("doctor-b")).toBeVisible();
    expect(screen.queryByLabelText(/^Ý kiến hội chẩn/)).not.toBeInTheDocument();
    view.rerender(<ConsultationNotesPanel recordId="record-a" canAddConsultationNote />);
    expect(await screen.findByLabelText(/^Ý kiến hội chẩn/)).toBeVisible();
  });

  it("requires and submits an opinion with idempotency metadata", async () => {
    render(<ConsultationNotesPanel recordId="record-a" canAddConsultationNote />);
    await screen.findByText(/Chưa có ghi chú hội chẩn/);
    fireEvent.click(screen.getByRole("button", { name: "Thêm ghi chú" }));
    expect(mocks.add).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(/^Ý kiến hội chẩn/), { target: { value: "Đồng thuận theo dõi" } });
    fireEvent.click(screen.getByRole("button", { name: "Thêm ghi chú" }));
    await waitFor(() => expect(mocks.add).toHaveBeenCalledWith(
      "record-a", { opinion: "Đồng thuận theo dõi" }, expect.objectContaining({ idempotencyKey: expect.any(String) }),
    ));
  });
});
