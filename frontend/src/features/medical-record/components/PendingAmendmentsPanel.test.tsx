import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { PendingAmendmentsPanel } from "./PendingAmendmentsPanel";

const mocks = vi.hoisted(() => ({
  pending: vi.fn(), role: "DOCTOR" as string | null, sessionKey: "doctor-session" as string | null,
  listeners: [] as Array<() => void>,
  notifyAuthChange: () => { for (const listener of [...mocks.listeners]) listener(); },
  subscribe: (listener: () => void) => {
    mocks.listeners.push(listener);
    return () => { mocks.listeners = mocks.listeners.filter((item) => item !== listener); };
  },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, getSessionKey: () => mocks.sessionKey, subscribeToAuthChanges: mocks.subscribe }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => mocks.sessionKey, subscribeToAuthChanges: mocks.subscribe }));
vi.mock("../api", () => ({ medicalRecordApi: { pendingAmendments: mocks.pending } }));

const amendment = { amendmentId: "amend-a", section: "SYMPTOMS", originalText: "Đau đầu", amendedText: "Đau đầu nhẹ", reason: "Nhập thiếu", authorStaffId: "doctor-b", assignedByStaffId: null, status: "PENDING", requestedAt: "2026-11-01T02:00:00Z", decidedByStaffId: null, decisionNote: null, decidedAt: null, contentHash: null };

describe("PendingAmendmentsPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.role = "DOCTOR";
    mocks.sessionKey = "doctor-session";
    mocks.listeners = [];
    mocks.pending.mockResolvedValue([]);
  });

  it("shows loading, empty, forbidden, hidden and a retryable outage", async () => {
    let resolve!: (value: unknown) => void;
    mocks.pending.mockReturnValueOnce(new Promise((done) => { resolve = done; }));
    const loading = render(<PendingAmendmentsPanel />);
    expect(screen.getByText(/Đang tải danh sách đính chính chờ duyệt/)).toBeVisible();
    resolve([]);
    expect(await screen.findByText(/Không có đính chính đang chờ/)).toBeVisible();
    loading.unmount();

    mocks.pending.mockRejectedValueOnce(new ApiRequestError("Cấm", 403));
    const denied = render(<PendingAmendmentsPanel />);
    expect(await screen.findByText(/Bạn không có quyền xem danh sách đính chính/)).toBeVisible();
    denied.unmount();

    mocks.pending.mockRejectedValueOnce(new ApiRequestError("Tắt", 404, "RESOURCE_NOT_FOUND"));
    const hidden = render(<PendingAmendmentsPanel />);
    await waitFor(() => expect(screen.queryByRole("region", { name: "Đính chính chờ duyệt" })).not.toBeInTheDocument());
    hidden.unmount();

    mocks.pending.mockRejectedValueOnce(new Error("offline"));
    mocks.pending.mockResolvedValueOnce([]);
    render(<PendingAmendmentsPanel />);
    fireEvent.click(await screen.findByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText(/Không có đính chính đang chờ/)).toBeVisible();
  });

  it("lists pending DTOs without invented record links or decision buttons", async () => {
    mocks.pending.mockResolvedValueOnce([amendment]);
    render(<PendingAmendmentsPanel />);
    expect(await screen.findByText(/Đính chính triệu chứng/i)).toBeVisible();
    expect(screen.getByText(/Đau đầu nhẹ/)).toBeVisible();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Duyệt/ })).not.toBeInTheDocument();
  });

  it("hides the doctor queue after a live role change", async () => {
    mocks.pending.mockResolvedValueOnce([amendment]);
    render(<PendingAmendmentsPanel />);
    expect(await screen.findByText(/Đau đầu nhẹ/)).toBeVisible();
    mocks.role = "NURSE";
    mocks.sessionKey = "nurse-session";
    mocks.notifyAuthChange();
    await waitFor(() => expect(screen.queryByRole("region", { name: "Đính chính chờ duyệt" })).not.toBeInTheDocument());
    expect(mocks.pending).toHaveBeenCalledTimes(1);
  });
});
