import { webcrypto } from "node:crypto";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { RecordHandoverPanel } from "./RecordHandoverPanel";

const mocks = vi.hoisted(() => ({
  list: vi.fn(), request: vi.fn(), accept: vi.fn(),
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
vi.mock("../api", () => ({ medicalRecordApi: { handovers: mocks.list, requestHandover: mocks.request, acceptHandover: mocks.accept } }));

const handover = { handoverId: "handover-a", fromDoctorId: "doctor-a", toDoctorId: "doctor-b", reason: "Ca trực", pendingItems: "Theo dõi kết quả", status: "PENDING", requestedAt: "2026-11-10T03:00:00Z", acceptedAt: null };
const props = { recordId: "record-a", departmentId: "department-a", canHandover: false, canAcceptHandover: false };

describe("RecordHandoverPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("crypto", webcrypto);
    sessionStorage.clear();
    mocks.sessionKey = "doctor-session";
    mocks.listeners = [];
    mocks.list.mockResolvedValue([]);
    mocks.request.mockResolvedValue(handover);
    mocks.accept.mockResolvedValue({ ...handover, status: "ACCEPTED" });
  });

  it("shows loading, empty, forbidden, hidden and a retryable outage", async () => {
    let resolve!: (value: unknown) => void;
    mocks.list.mockReturnValueOnce(new Promise((done) => { resolve = done; }));
    const loading = render(<RecordHandoverPanel {...props} />);
    expect(screen.getByText(/Đang tải bàn giao/)).toBeVisible();
    resolve([]);
    expect(await screen.findByText(/Chưa có bàn giao/)).toBeVisible();
    loading.unmount();

    mocks.list.mockRejectedValueOnce(new ApiRequestError("Cấm", 403));
    const denied = render(<RecordHandoverPanel {...props} />);
    expect(await screen.findByText(/Bạn không có quyền xem bàn giao/)).toBeVisible();
    denied.unmount();

    mocks.list.mockRejectedValueOnce(new ApiRequestError("Tắt", 404, "RESOURCE_NOT_FOUND"));
    const hidden = render(<RecordHandoverPanel {...props} />);
    await waitFor(() => expect(screen.queryByRole("region", { name: "Bàn giao bác sĩ" })).not.toBeInTheDocument());
    hidden.unmount();

    mocks.list.mockRejectedValueOnce(new Error("offline"));
    mocks.list.mockResolvedValueOnce([]);
    render(<RecordHandoverPanel {...props} />);
    fireEvent.click(await screen.findByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText(/Chưa có bàn giao/)).toBeVisible();
  });

  it("keeps a former doctor read-only after handover", async () => {
    mocks.list.mockResolvedValueOnce([handover]);
    render(<RecordHandoverPanel {...props} />);
    await screen.findByText(/Theo dõi kết quả/);
    expect(screen.queryByRole("button", { name: /Tạo bàn giao/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Chấp nhận bàn giao/ })).not.toBeInTheDocument();
  });

  it("sends a handover and retries an uncertain outcome with the same payload and key", async () => {
    mocks.request.mockRejectedValueOnce(new Error("network dropped"));
    render(<RecordHandoverPanel {...props} canHandover />);
    await screen.findByText(/Chưa có bàn giao/);
    fireEvent.click(screen.getByRole("button", { name: /Tạo bàn giao/ }));
    fireEvent.change(screen.getByLabelText(/^Bác sĩ nhận bàn giao/), { target: { value: "doctor-b" } });
    fireEvent.change(screen.getByLabelText(/^Lý do bàn giao/), { target: { value: "Kết thúc ca" } });
    fireEvent.change(screen.getByLabelText(/^Việc còn chờ/), { target: { value: "Đọc kết quả Lab" } });
    fireEvent.click(screen.getByRole("button", { name: "Gửi bàn giao" }));

    expect(await screen.findByRole("button", { name: /Gửi lại cùng nội dung/ })).toBeVisible();
    const firstBody = mocks.request.mock.calls[0][1];
    const firstOptions = mocks.request.mock.calls[0][2];
    mocks.request.mockResolvedValueOnce(handover);
    fireEvent.click(screen.getByRole("button", { name: /Gửi lại cùng nội dung/ }));
    await waitFor(() => expect(mocks.request).toHaveBeenCalledTimes(2));
    expect(mocks.request.mock.calls[1][1]).toEqual(firstBody);
    expect(mocks.request.mock.calls[1][2].idempotencyKey).toBe(firstOptions.idempotencyKey);
  });

  it("lets the pending receiver accept the handover", async () => {
    mocks.list.mockResolvedValueOnce([handover]);
    render(<RecordHandoverPanel {...props} canAcceptHandover />);
    await screen.findByText(/Theo dõi kết quả/);
    fireEvent.click(screen.getByRole("button", { name: /Chấp nhận bàn giao/ }));
    await waitFor(() => expect(mocks.accept).toHaveBeenCalledWith(
      "record-a", "handover-a", expect.objectContaining({ idempotencyKey: expect.any(String) }),
    ));
  });
});
