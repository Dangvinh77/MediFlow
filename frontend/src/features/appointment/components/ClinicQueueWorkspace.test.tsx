import { webcrypto } from "node:crypto";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { ClinicQueueWorkspace } from "./ClinicQueueWorkspace";

const mocks = vi.hoisted(() => ({ clinicQueue: vi.fn(), issueRoomTicket: vi.fn(), callTicket: vi.fn(), adjustTicket: vi.fn(), createVitalSigns: vi.fn(), listVitalSigns: vi.fn(), role: "NURSE" }));
vi.mock("../api", () => ({ appointmentApi: mocks }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "nurse-session" }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => undefined }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: ({ value }: { value: string }) => <span>{value}</span> }));

const base = {
  registrationId: "r1", patientId: "patient-1", sessionId: "s1", doctorId: "d1", departmentId: "k1", roomId: "room-a", queueDate: "2026-11-10",
  queueNumber: 3, ticketId: null as string | null, ticketRevision: null as number | null, stage: "WAITING_ROOM_TICKET", priority: false,
  checkedInAt: "2026-11-10T01:00:00Z", calledAt: null as string | null, vitalSignsRecorded: false, readyForExam: false, continuationRequired: false,
};
const waiting = { ...base, registrationId: "r2", patientId: "patient-2", ticketId: "t2", ticketRevision: 4, roomQueueNumber: 2, stage: "WAITING_CALL", readyForExam: true };
const ticket = { ticketId: "t1", revision: 1, registrationId: "r1", roomId: "room-a", queueDate: "2026-11-10", roomQueueNumber: 7, status: "WAITING", priority: false, issuedAt: "2026-11-10T01:05:00Z", calledAt: null };

describe("department nurse desk", () => {
  beforeEach(() => {
    Object.values(mocks).forEach(mock => { if (typeof mock !== "string") mock.mockReset(); });
    mocks.role = "NURSE"; vi.stubGlobal("crypto", webcrypto); sessionStorage.clear();
  });

  it("groups the queue by room with each room numbered independently", async () => {
    mocks.clinicQueue.mockResolvedValue([
      { ...waiting, registrationId: "a1", ticketId: "a1t", roomQueueNumber: 1 },
      { ...waiting, registrationId: "a2", ticketId: "a2t", roomQueueNumber: 2 },
      { ...waiting, registrationId: "b1", ticketId: "b1t", roomId: "room-b", roomQueueNumber: 1 },
    ]);
    render(<ClinicQueueWorkspace enabled />);
    const roomA = await screen.findByRole("region", { name: /room-a/ });
    const roomB = screen.getByRole("region", { name: /room-b/ });
    expect(within(roomA).getByText(/Số phòng 1/)).toBeVisible();
    expect(within(roomA).getByText(/Số phòng 2/)).toBeVisible();
    expect(within(roomB).getByText(/Số phòng 1/)).toBeVisible();
    expect(within(roomB).queryByText(/Số phòng 2/)).not.toBeInTheDocument();
  });

  it("separates waiting for a room number from allowed to examine", async () => {
    mocks.clinicQueue.mockResolvedValue([base, waiting]);
    render(<ClinicQueueWorkspace enabled />);
    const needNumber = await screen.findByRole("list", { name: "Chờ cấp số phòng" });
    const allowed = screen.getByRole("list", { name: "Được vào khám" });
    expect(within(needNumber).getByText("patient-1")).toBeVisible();
    expect(within(needNumber).queryByText("patient-2")).not.toBeInTheDocument();
    expect(within(allowed).getByText("patient-2")).toBeVisible();
  });

  it("issues a number and shows it", async () => {
    mocks.clinicQueue.mockResolvedValue([base]);
    mocks.issueRoomTicket.mockResolvedValue(ticket);
    render(<ClinicQueueWorkspace enabled />);
    await userEvent.setup().click(await screen.findByRole("button", { name: "Cấp số phòng" }));
    expect(await screen.findByText(/Đã cấp số phòng 7/)).toBeVisible();
    expect(mocks.issueRoomTicket.mock.calls[0][0]).toBe("r1");
    await waitFor(() => expect(mocks.clinicQueue).toHaveBeenCalledTimes(2));
  });

  it("retry keeps the same key", async () => {
    mocks.clinicQueue.mockResolvedValue([base]);
    mocks.issueRoomTicket.mockRejectedValueOnce(new ApiRequestError("Lỗi máy chủ", 500)).mockResolvedValueOnce(ticket);
    render(<ClinicQueueWorkspace enabled />);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Cấp số phòng" }));
    const retry = await screen.findByRole("button", { name: "Gửi lại cùng yêu cầu" });
    expect(screen.getByRole("button", { name: "Cấp số phòng" })).toBeDisabled();
    await user.click(retry);
    expect(await screen.findByText(/Đã cấp số phòng 7/)).toBeVisible();
    const [first, second] = mocks.issueRoomTicket.mock.calls;
    expect(second[1].idempotencyKey).toBe(first[1].idempotencyKey);
  });

  it("shows a clear refusal for a reception nurse", async () => {
    mocks.clinicQueue.mockRejectedValue(new ApiRequestError("denied", 403, "CLINIC_DESK_SCOPE_DENIED"));
    render(<ClinicQueueWorkspace enabled />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Chỉ điều dưỡng của khoa");
    expect(screen.queryByRole("button", { name: /Thử lại/ })).not.toBeInTheDocument();
  });

  it("call records and shows the caller time", async () => {
    mocks.clinicQueue.mockResolvedValueOnce([waiting]).mockResolvedValueOnce([{ ...waiting, stage: "CALLED", ticketRevision: 5, calledAt: "2026-11-10T02:30:00Z" }]);
    mocks.callTicket.mockResolvedValue({ ...ticket, ticketId: "t2", status: "CALLED", calledAt: "2026-11-10T02:30:00Z" });
    render(<ClinicQueueWorkspace enabled />);
    await userEvent.setup().click(await screen.findByRole("button", { name: "Gọi vào khám" }));
    expect(await screen.findByText(/Đã gọi lúc/)).toBeVisible();
    expect(mocks.callTicket.mock.calls[0].slice(0, 2)).toEqual(["t2", { expectedRevision: 4 }]);
    expect(screen.getByRole("button", { name: "Gọi lại" })).toBeVisible();
  });

  it.each([["absent requires a reason", "Vắng mặt", "NO_SHOW"], ["priority requires a reason", "Ưu tiên", "PRIORITY"]])("%s", async (_name, action, type) => {
    mocks.clinicQueue.mockResolvedValue([waiting]);
    mocks.adjustTicket.mockResolvedValue({ ...ticket, ticketId: "t2", revision: 5 });
    render(<ClinicQueueWorkspace enabled />);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: action }));
    const confirm = screen.getByRole("button", { name: "Xác nhận" });
    expect(confirm).toBeDisabled();
    await user.type(screen.getByLabelText("Lý do"), "Đã gọi ba lần");
    await user.click(confirm);
    await waitFor(() => expect(mocks.adjustTicket).toHaveBeenCalledTimes(1));
    expect(mocks.adjustTicket.mock.calls[0].slice(0, 2)).toEqual(["t2", { expectedRevision: 4, type, reason: "Đã gọi ba lần" }]);
  });

  it("flags open visits needing continuation", async () => {
    mocks.clinicQueue.mockResolvedValue([{ ...waiting, stage: "IN_EXAM", readyForExam: false, continuationRequired: true }]);
    render(<ClinicQueueWorkspace enabled />);
    expect(await screen.findByText(/lượt khám còn mở/)).toBeVisible();
  });

  it("keeps the room number desk action to nurses", async () => {
    mocks.role = "DOCTOR";
    mocks.clinicQueue.mockResolvedValue([base]);
    render(<ClinicQueueWorkspace enabled />);
    expect(await screen.findByText("patient-1")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Cấp số phòng" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ghi sinh hiệu" })).not.toBeInTheDocument();
  });

  it("stays closed when the flag is off", () => {
    render(<ClinicQueueWorkspace enabled={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("chưa được mở");
    expect(mocks.clinicQueue).not.toHaveBeenCalled();
  });
});
