import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { AppointmentDTO } from "../types";

const mocks = vi.hoisted(() => ({ getById: vi.fn(), changeStatus: vi.fn(), role: "NURSE", token: "session-a", listeners: new Set<() => void>(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: () => mocks.role,
  getSessionKey: () => mocks.token,
  subscribeToAuthChanges: (listener: () => void) => { mocks.listeners.add(listener); return () => { mocks.listeners.delete(listener); }; },
}));
vi.mock("../api", () => ({ appointmentApi: { getById: mocks.getById, changeStatus: mocks.changeStatus } }));
import { AppointmentDetail } from "./AppointmentDetail";

const appointment: AppointmentDTO = {
  appointmentId: "appointment-a", patientId: "patient-a", doctorId: "doctor-a", departmentId: "department-a",
  appointmentDate: "2026-10-17", appointmentTime: "09:00", status: "PENDING", reason: "First visit",
  createdAt: "2026-10-17T01:00:00Z", updatedAt: null, careContractVersion: 0,
  examClearanceId: null, examClearanceAt: null, emergencyOverrideId: null, examPriceCode: null,
  checkedInAt: null, examinationStartedAt: null, completedAt: null,
};
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

describe("Appointment detail identity and request states", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.getById.mockReset(); mocks.changeStatus.mockReset(); mocks.role = "NURSE"; mocks.token = "session-a"; mocks.listeners.clear(); });

  it("preserves the current appointment when an old status mutation finishes", async () => {
    const pending = deferred<AppointmentDTO>();
    mocks.getById.mockResolvedValueOnce(appointment).mockResolvedValueOnce({ ...appointment, appointmentId: "appointment-b", reason: "Second visit" });
    mocks.changeStatus.mockReturnValue(pending.promise);
    const view = render(<AppointmentDetail appointmentId="appointment-a" />);
    fireEvent.click(await screen.findByRole("button", { name: "Ghi nhận đã đến" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));
    view.rerender(<AppointmentDetail appointmentId="appointment-b" />);
    expect(await screen.findByText("Second visit")).toBeVisible();
    await act(async () => pending.resolve({ ...appointment, status: "ARRIVED" }));
    expect(screen.getByText("Second visit")).toBeVisible();
    expect(screen.getByRole("button", { name: "Ghi nhận đã đến" })).toBeEnabled();
  });

  it("clears the previous appointment confirmation on identity change", async () => {
    mocks.getById.mockResolvedValueOnce(appointment).mockResolvedValueOnce({ ...appointment, appointmentId: "appointment-b", reason: "Second visit" });
    const view = render(<AppointmentDetail appointmentId="appointment-a" />);
    fireEvent.click(await screen.findByRole("button", { name: "Hủy lịch hẹn" }));
    view.rerender(<AppointmentDetail appointmentId="appointment-b" />);
    await screen.findByText("Second visit");
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("clears the previous patient's appointment while the replacement session reloads it", async () => {
    const pending = deferred<AppointmentDTO>();
    mocks.getById.mockResolvedValueOnce(appointment).mockReturnValueOnce(pending.promise);
    render(<AppointmentDetail appointmentId="appointment-a" />);
    expect(await screen.findByText("First visit")).toBeVisible();

    act(() => {
      mocks.token = "session-b";
      for (const listener of mocks.listeners) listener();
    });

    expect(screen.queryByText("First visit")).not.toBeInTheDocument();
    expect(screen.getByText("Đang tải chi tiết lịch hẹn…")).toBeVisible();
    await act(async () => pending.resolve({ ...appointment, patientId: "patient-b", reason: "Second session visit" }));
    expect(screen.getByText("Second session visit")).toBeVisible();
    expect(mocks.getById).toHaveBeenCalledTimes(2);
  });

  it("ignores an old mutation authentication error after navigation", async () => {
    const pending = deferred<AppointmentDTO>();
    mocks.getById.mockResolvedValueOnce(appointment).mockResolvedValueOnce({ ...appointment, appointmentId: "appointment-b", reason: "Second visit" });
    mocks.changeStatus.mockReturnValue(pending.promise);
    const view = render(<AppointmentDetail appointmentId="appointment-a" />);
    fireEvent.click(await screen.findByRole("button", { name: "Ghi nhận đã đến" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));
    view.rerender(<AppointmentDetail appointmentId="appointment-b" />);
    await screen.findByText("Second visit");
    await act(async () => pending.reject(new ApiRequestError("Old session", 401, "UNAUTHORIZED")));
    expect(mocks.router.replace).not.toHaveBeenCalled();
    expect(screen.getByText("Second visit")).toBeVisible();
  });

  it.each([403, 503])("keeps HTTP %s and correlation visible until an explicit read retry", async (status) => {
    mocks.getById.mockRejectedValueOnce(new ApiRequestError("Read denied or unavailable", status, "READ_ERROR", [], "appointment-correlation"));
    mocks.getById.mockResolvedValueOnce(appointment);
    render(<AppointmentDetail appointmentId="appointment-a" />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Read denied or unavailable");
    expect(screen.getByText("appointment-correlation")).toBeVisible();
    expect(mocks.getById).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("First visit")).toBeVisible();
  });

  it.each(["AWAITING_PAYMENT", "READY_FOR_EXAM", "IN_EXAM", "COMPLETED", "CANCELLED"] as const)("does not expose legacy transitions for %s", async (status) => {
    mocks.getById.mockResolvedValue({ ...appointment, status, careContractVersion: 1 });
    render(<AppointmentDetail appointmentId="appointment-a" />);
    await screen.findByText("First visit");
    expect(screen.queryByRole("button", { name: "Ghi nhận đã đến" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Hủy lịch hẹn" })).not.toBeInTheDocument();
  });
});
