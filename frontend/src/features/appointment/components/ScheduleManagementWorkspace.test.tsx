import { webcrypto } from "node:crypto";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { appointmentApi } from "../api";
import { ScheduleManagementWorkspace } from "./ScheduleManagementWorkspace";

const mocks = vi.hoisted(() => ({
  rooms: vi.fn(), scope: vi.fn(), replaceScope: vi.fn(),
  createSession: vi.fn(), updateSession: vi.fn(), changeStatus: vi.fn(), listSessions: vi.fn(),
  departments: vi.fn(),
}));
vi.mock("../api", () => ({ appointmentApi: {
  consultationRooms: mocks.rooms, scheduleManagementScope: mocks.scope, replaceScheduleManagementScope: mocks.replaceScope,
  createSession: mocks.createSession, updateSession: mocks.updateSession, changeSessionStatus: mocks.changeStatus,
  listConsultationSessions: mocks.listSessions,
} }));
vi.mock("@/features/organization/api", () => ({ organizationApi: { departments: mocks.departments } }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "admin-session" }));
vi.mock("@/lib/auth", () => ({ getRole: () => "ADMIN", subscribeToAuthChanges: () => () => undefined }));
vi.mock("@/components/references/ReferenceInput", () => ({
  ReferenceInput: ({ value, onValueChange, "aria-label": label }: { value: string; onValueChange: (value: string) => void; "aria-label": string }) => (
    <input aria-label={label} value={value} onChange={event => onValueChange(event.target.value)} />
  ),
}));

const created = {
  sessionId: "new-session", revision: 1, doctorId: "doctor-id", departmentId: "department-id", roomId: "room-id",
  sessionDate: "2026-10-27", startTime: "08:00", endTime: "12:00", breaks: [], capacity: 20,
  status: "OPEN" as const, heldCount: 0, paymentLockedCount: 0, confirmedCount: 0, consumedCount: 0,
  availableCount: 20, observedAt: "2026-10-26T04:00:00Z",
};

describe("schedule management", () => {
  beforeEach(() => {
    vi.clearAllMocks(); vi.stubGlobal("crypto", webcrypto); sessionStorage.clear();
    mocks.departments.mockResolvedValue([]);
    mocks.rooms.mockResolvedValue({ content: [{ roomId: "room-id", departmentId: "department-id", roomName: "Phòng khám 1", roomType: "CONSULTATION" }], totalElements: 1, totalPages: 1, number: 0, size: 50 });
  });

  it("prefills a linked manager without writing scope on entry", async () => {
    render(<ScheduleManagementWorkspace enabled initialManagerAccountId="00000000-0000-4000-8000-000000000123" />);
    expect(screen.getByLabelText("Tài khoản quản lý")).toHaveValue("00000000-0000-4000-8000-000000000123");
    expect(mocks.replaceScope).not.toHaveBeenCalled();
  });

  it("puts schedule browsing before the separate create controls and access administration", () => {
    render(<ScheduleManagementWorkspace enabled />);
    expect(screen.getByRole("heading", { name: "Buổi trong bộ lọc" }).compareDocumentPosition(screen.getByRole("heading", { name: "Tạo buổi khám", hidden: true })) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("blocks missing, inverted and oversized dates with associated errors before requesting", async () => {
    const user = userEvent.setup();
    render(<ScheduleManagementWorkspace enabled />);
    await user.type(screen.getByLabelText("Khoa buổi khám"), "department-id");
    const from = screen.getByLabelText("Từ ngày"); const to = screen.getByLabelText("Đến ngày");
    expect(from).toHaveValue(to.getAttribute("value"));
    fireEvent.change(to, { target: { value: "" } });
    await user.click(screen.getByRole("button", { name: "Tải buổi đã lập" }));
    expect(to).toHaveAttribute("aria-invalid", "true");
    expect(to).toHaveAccessibleDescription("Vui lòng chọn ngày bắt buộc.");
    expect(mocks.listSessions).not.toHaveBeenCalled();
    fireEvent.change(from, { target: { value: "2026-11-01" } });
    fireEvent.change(to, { target: { value: "2026-10-31" } });
    await user.click(screen.getByRole("button", { name: "Tải buổi đã lập" }));
    expect(to).toHaveAccessibleDescription("Đến ngày phải bằng hoặc sau Từ ngày.");
    expect(mocks.listSessions).not.toHaveBeenCalled();
    fireEvent.change(from, { target: { value: "2026-09-01" } });
    await user.click(screen.getByRole("button", { name: "Tải buổi đã lập" }));
    expect(to).toHaveAccessibleDescription("Khoảng tìm kiếm tối đa 30 ngày giữa hai mốc.");
    expect(mocks.listSessions).not.toHaveBeenCalled();
  });

  it("retries an uncertain create using the same command and idempotency headers", async () => {
    const user = userEvent.setup();
    mocks.createSession.mockRejectedValueOnce(new TypeError("Connection lost")).mockResolvedValueOnce(created);
    render(<ScheduleManagementWorkspace enabled />);
    await user.type(screen.getByLabelText("Khoa buổi khám"), "department-id");
    await user.click(screen.getByText("Tạo buổi khám", { selector: "h3" }));
    await user.click(screen.getByRole("button", { name: "Tải phòng tư vấn" }));
    await user.type(screen.getByLabelText("Bác sĩ"), "doctor-id");
    await user.selectOptions(await screen.findByLabelText("Phòng tư vấn"), "room-id");
    await user.type(screen.getByLabelText("Ngày khám"), "2026-10-27");
    fireEvent.change(screen.getByLabelText("Bắt đầu"), { target: { value: "08:00" } });
    fireEvent.change(screen.getByLabelText("Kết thúc"), { target: { value: "12:00" } });
    await user.clear(screen.getByLabelText("Sức chứa"));
    await user.type(screen.getByLabelText("Sức chứa"), "20");
    expect(screen.getByLabelText("Bác sĩ")).toHaveValue("doctor-id");
    expect(screen.getByLabelText("Phòng tư vấn")).toHaveValue("room-id");
    expect(screen.getByLabelText("Ngày khám")).toHaveValue("2026-10-27");
    expect(screen.getByLabelText("Bắt đầu")).toHaveValue("08:00");
    expect(screen.getByLabelText("Kết thúc")).toHaveValue("12:00");
    expect(screen.getByLabelText("Sức chứa")).toHaveValue(20);
    await user.click(screen.getByRole("button", { name: "Tạo buổi khám" }));
    expect(await screen.findByText(/Kết quả chưa xác định/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Gửi lại cùng yêu cầu" }));
    await waitFor(() => expect(mocks.createSession).toHaveBeenCalledTimes(2));
    expect(mocks.createSession).toHaveBeenCalledTimes(2);
    expect(mocks.createSession.mock.calls[0][0]).toEqual({
      doctorId: "doctor-id", departmentId: "department-id", roomId: "room-id", sessionDate: "2026-10-27",
      startTime: "08:00", endTime: "12:00", breaks: [], capacity: 20,
    });
    expect(mocks.createSession.mock.calls[0][1]).toEqual(mocks.createSession.mock.calls[1][1]);
    expect(mocks.createSession.mock.calls[0][1].idempotencyKey).toBeTruthy();
  });

  it("does not call V2 endpoints while the scheduling gate is off", () => {
    render(<ScheduleManagementWorkspace enabled={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("chưa được mở");
    expect(appointmentApi.createSession).not.toHaveBeenCalled();
    expect(appointmentApi.consultationRooms).not.toHaveBeenCalled();
  });

  it("replaces a manager's scheduling scope using its current revision", async () => {
    const user = userEvent.setup();
    mocks.scope.mockResolvedValue({ accountId: "manager-id", revision: 2, departmentIds: [], updatedAt: null, updatedByAccountId: null });
    mocks.departments.mockResolvedValue([{ departmentId: "department-id", departmentName: "Khoa Ngoại" }]);
    mocks.replaceScope.mockResolvedValue({ accountId: "manager-id", revision: 3, departmentIds: ["department-id"], updatedAt: "2026-10-26T04:00:00Z", updatedByAccountId: "admin-id" });
    render(<ScheduleManagementWorkspace enabled />);
    await user.click(screen.getByText("Quản trị quyền lập lịch"));
    await user.type(screen.getByLabelText("Tài khoản quản lý"), "manager-id");
    await user.click(screen.getByRole("button", { name: "Tải phạm vi" }));
    await user.click(await screen.findByLabelText("Khoa Ngoại"));
    await user.type(screen.getByLabelText("Lý do cấp hoặc thu hồi quyền"), "Phân công lịch khoa Ngoại");
    await user.click(screen.getByRole("button", { name: "Lưu phạm vi" }));
    await waitFor(() => expect(mocks.replaceScope).toHaveBeenCalledTimes(1));
    expect(mocks.replaceScope.mock.calls[0][0]).toBe("manager-id");
    expect(mocks.replaceScope.mock.calls[0][1]).toEqual({ expectedRevision: 2, departmentIds: ["department-id"], reason: "Phân công lịch khoa Ngoại" });
    expect(mocks.replaceScope.mock.calls[0][2].idempotencyKey).toBeTruthy();
  });
});
