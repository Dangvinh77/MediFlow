import { webcrypto } from "node:crypto";
import Link from "next/link";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { appointmentApi } from "../api";
import { ScheduleManagementWorkspace, SessionAdministration } from "./ScheduleManagementWorkspace";

const mocks = vi.hoisted(() => ({
  rooms: vi.fn(), scope: vi.fn(), replaceScope: vi.fn(),
  createSession: vi.fn(), updateSession: vi.fn(), changeStatus: vi.fn(), listSessions: vi.fn(),
  departments: vi.fn(), sessionImpact: vi.fn(),
}));
vi.mock("../api", () => ({ appointmentApi: {
  consultationRooms: mocks.rooms, scheduleManagementScope: mocks.scope, replaceScheduleManagementScope: mocks.replaceScope,
  createSession: mocks.createSession, updateSession: mocks.updateSession, changeSessionStatus: mocks.changeStatus,
  listConsultationSessions: mocks.listSessions, sessionImpact: mocks.sessionImpact,
} }));
vi.mock("@/lib/api", async importOriginal => ({ ...await importOriginal<typeof import("@/lib/api")>(), api: { get: mocks.departments } }));
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
  afterEach(() => vi.restoreAllMocks());

  it.each(["add", "remove"])("keeps the draft when leaving after a button-only break %s", async action => {
    const user = userEvent.setup();
    mocks.listSessions.mockResolvedValue({ content: [{ ...created, breaks: action === "remove" ? [{ startTime: "10:00", endTime: "10:15" }] : [] }], number: 0, totalPages: 1, totalElements: 1 });
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    render(<><Link href="/appointments/my-calendar">Về Khám bệnh</Link><SessionAdministration fixedDepartmentId="department-id" /></>);
    await user.click(await screen.findByRole("button", { name: "Sửa" }));
    await user.click(screen.getByRole("button", { name: action === "add" ? "Thêm giờ nghỉ" : "Xóa giờ nghỉ" }));
    expect(fireEvent.click(screen.getByRole("link", { name: "Về Khám bệnh" }))).toBe(false);
    expect(confirm).toHaveBeenCalledOnce();
    expect(screen.getByRole("heading", { name: "Sửa buổi khám đã chọn" })).toBeVisible();
  });

  beforeEach(() => {
    vi.clearAllMocks(); vi.stubGlobal("crypto", webcrypto); sessionStorage.clear();
    mocks.departments.mockResolvedValue([]);
    mocks.rooms.mockResolvedValue({ content: [{ roomId: "room-id", departmentId: "department-id", roomName: "Phòng khám 1", roomType: "CONSULTATION" }], totalElements: 1, totalPages: 1, number: 0, size: 50 });
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
    expect(screen.queryByRole("button", { name: "Tải phòng tư vấn" })).not.toBeInTheDocument();
    expect(await screen.findByRole("option", { name: "Phòng khám 1" })).toBeInTheDocument();
    expect(mocks.rooms).toHaveBeenLastCalledWith("department-id", 0, 50);
    await user.type(screen.getByLabelText("Bác sĩ"), "doctor-id");
    await user.selectOptions(await screen.findByLabelText("Phòng khám"), "room-id");
    await user.type(screen.getByLabelText("Ngày khám"), "2026-10-27");
    fireEvent.change(screen.getByLabelText("Bắt đầu"), { target: { value: "08:00" } });
    fireEvent.change(screen.getByLabelText("Kết thúc"), { target: { value: "12:00" } });
    await user.clear(screen.getByLabelText("Sức chứa"));
    await user.type(screen.getByLabelText("Sức chứa"), "20");
    expect(screen.getByLabelText("Bác sĩ")).toHaveValue("doctor-id");
    expect(screen.getByLabelText("Phòng khám")).toHaveValue("room-id");
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

  it("locks session creation until a department is chosen, then asks for the doctor before the room", async () => {
    const user = userEvent.setup();
    mocks.rooms.mockResolvedValue({ content: [{ roomId: "room-id", departmentId: "department-id", roomName: "Phòng khám Nội 1", roomType: "CONSULTATION", roomNumber: "101", zone: "Khu A", location: "Tầng 1" }], totalElements: 1, totalPages: 1, number: 0, size: 50 });
    render(<ScheduleManagementWorkspace enabled />);
    await user.click(screen.getByText("Tạo buổi khám", { selector: "h3" }));
    expect(screen.getByLabelText("Bác sĩ")).toBeDisabled();
    expect(screen.getByLabelText("Phòng khám")).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Tạo buổi khám đang khóa: chọn khoa trước" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Chọn khoa buổi khám trước");
    expect(mocks.rooms).not.toHaveBeenCalled();
    expect(mocks.createSession).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText("Khoa buổi khám"), "department-id");
    expect(await screen.findByRole("option", { name: "P.101 · Phòng khám Nội 1 · Khu A" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tạo buổi khám đang khóa: chọn khoa trước" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Phòng khám")).toBeDisabled();
    await user.type(screen.getByLabelText("Bác sĩ"), "doctor-id");
    expect(screen.getByLabelText("Phòng khám")).toBeEnabled();
  });

  it("does not call V2 endpoints while the scheduling gate is off", () => {
    render(<ScheduleManagementWorkspace enabled={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("chưa được mở");
    expect(appointmentApi.createSession).not.toHaveBeenCalled();
    expect(appointmentApi.consultationRooms).not.toHaveBeenCalled();
  });

});

describe("schedule impact", () => {
  it("blocked session edit opens the impact panel", async () => {
    vi.stubGlobal("crypto", webcrypto); sessionStorage.clear();
    const user = userEvent.setup();
    mocks.listSessions.mockResolvedValue({ content: [created], number: 0, totalPages: 1, totalElements: 1 });
    mocks.rooms.mockResolvedValue({ content: [{ roomId: "room-id", departmentId: "department-id", roomName: "Phòng 1", roomType: "CONSULTATION" }], number: 0, totalPages: 1, totalElements: 1 });
    mocks.updateSession.mockRejectedValue(new ApiRequestError("Buổi đã có phiếu", 409, "SESSION_HAS_REGISTRATIONS"));
    mocks.sessionImpact.mockResolvedValue({ sessionIds: ["new-session"], heldCount: 0, paymentLockedCount: 0, confirmedCount: 1, consumedCount: 0, registrations: [
      { registrationId: "r1", patientId: "p1", sessionId: "new-session", status: "CONFIRMED", reservationStatus: "CONFIRMED", checkedInAt: null, queueNumber: 4, roomQueueNumber: null },
    ] });
    render(<SessionAdministration fixedDepartmentId="department-id" clinicIntakeEnabled />);
    await user.click(await screen.findByRole("button", { name: "Sửa" }));
    fireEvent.change(screen.getByLabelText("Lý do sửa buổi"), { target: { value: "Bác sĩ nghỉ" } });
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi buổi" }));
    expect(await screen.findByRole("heading", { name: "Bệnh nhân bị ảnh hưởng" })).toBeVisible();
    expect(mocks.sessionImpact).toHaveBeenCalledWith("new-session");
    expect(await screen.findByText(/Số quầy 4/)).toBeVisible();
  });
});
