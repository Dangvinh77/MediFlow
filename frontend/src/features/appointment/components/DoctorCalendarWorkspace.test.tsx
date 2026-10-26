import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { appointmentApi } from "../api";
import { DoctorCalendarWorkspace } from "./DoctorCalendarWorkspace";

const mocks = vi.hoisted(() => ({ calendar: vi.fn(), registrations: vi.fn() }));
vi.mock("../api", () => ({ appointmentApi: { myCalendar: mocks.calendar, registrationsBySession: mocks.registrations } }));

const session = {
  sessionId: "own-session", revision: 1, doctorId: "signed-doctor", departmentId: "dept", roomId: "room",
  sessionDate: "2026-10-26", startTime: "08:00", endTime: "12:00", breaks: [], capacity: 10,
  status: "OPEN" as const, heldCount: 1, paymentLockedCount: 0, confirmedCount: 2, consumedCount: 0,
  availableCount: 7, observedAt: "2026-10-25T23:00:00Z",
};

describe("doctor own calendar", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.calendar.mockResolvedValue({ doctorId: "signed-doctor", fromDate: "2026-10-26", toDate: "2026-11-01", timezone: "Asia/Ho_Chi_Minh", sessions: [session] });
    mocks.registrations.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });

  it("calculates Monday for week view and loads only the signed-in doctor's route", async () => {
    const user = userEvent.setup();
    render(<DoctorCalendarWorkspace enabled />);
    await user.selectOptions(screen.getByLabelText("Chế độ lịch"), "week");
    await user.clear(screen.getByLabelText("Ngày tham chiếu"));
    await user.type(screen.getByLabelText("Ngày tham chiếu"), "2026-10-28");
    await user.click(screen.getByRole("button", { name: "Tải lịch của tôi" }));
    expect(await screen.findByRole("heading", { name: /08:00–12:00/ })).toBeInTheDocument();
    expect(mocks.calendar).toHaveBeenCalledWith({ weekStart: "2026-10-26" });
    expect(mocks.calendar.mock.calls[0][0]).not.toHaveProperty("doctorId");
  });

  it("does not call doctor routes while the scheduling gate is off", () => {
    render(<DoctorCalendarWorkspace enabled={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("chưa được mở");
    expect(appointmentApi.myCalendar).not.toHaveBeenCalled();
  });
});
