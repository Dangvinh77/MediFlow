import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { AppointmentDTO } from "../types";

const mocks = vi.hoisted(() => ({ create: vi.fn(), getById: vi.fn(), update: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ appointmentApi: { create: mocks.create, getById: mocks.getById, update: mocks.update } }));
import { CreateAppointmentForm } from "./CreateAppointmentForm";
import { EditAppointmentForm } from "./EditAppointmentForm";

const appointment: AppointmentDTO = {
  appointmentId: "appointment-a", patientId: "123e4567-e89b-42d3-a456-426614174000",
  doctorId: "123e4567-e89b-42d3-a456-426614174001", departmentId: "123e4567-e89b-42d3-a456-426614174002",
  appointmentDate: "2026-11-01", appointmentTime: "09:00", status: "PENDING", reason: "First visit",
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

function fillCreateAppointment() {
  fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: appointment.patientId } });
  fireEvent.change(screen.getByLabelText("Mã khoa", { exact: false }), { target: { value: appointment.departmentId } });
  fireEvent.change(screen.getByLabelText("Mã bác sĩ", { exact: false }), { target: { value: appointment.doctorId } });
  fireEvent.change(screen.getByLabelText("Ngày hẹn", { exact: false }), { target: { value: appointment.appointmentDate } });
  fireEvent.change(screen.getByLabelText("Giờ hẹn", { exact: false }), { target: { value: appointment.appointmentTime } });
}

describe("Appointment forms", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.create.mockReset(); mocks.getById.mockReset(); mocks.update.mockReset(); });

  it("sends only one create request when submit fires twice before the pending request settles", async () => {
    const pending = deferred<AppointmentDTO>();
    mocks.create.mockReturnValue(pending.promise);
    render(<CreateAppointmentForm />);
    fillCreateAppointment();
    const form = screen.getByRole("button", { name: "Tạo lịch hẹn" }).closest("form")!;
    await act(async () => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });

  it("ignores a late authentication failure after the create form unmounts", async () => {
    const pending = deferred<AppointmentDTO>();
    mocks.create.mockReturnValue(pending.promise);
    const view = render(<CreateAppointmentForm />);
    fillCreateAppointment();
    fireEvent.click(screen.getByRole("button", { name: "Tạo lịch hẹn" }));
    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));
    view.unmount();
    await act(async () => pending.reject(new ApiRequestError("Phiên hết hạn", 401, "UNAUTHORIZED")));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  it("maps server field errors and preserves their correlation ID", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError(
      "Ngày không hợp lệ", 422, "VALIDATION_ERROR", [{ field: "appointmentDate", message: "Ngày đã qua" }], "appointment-form-correlation",
    ));
    render(<CreateAppointmentForm />);
    fillCreateAppointment();
    fireEvent.click(screen.getByRole("button", { name: "Tạo lịch hẹn" }));
    expect(await screen.findByText("Ngày đã qua")).toBeVisible();
    expect(screen.getByText("appointment-form-correlation")).toBeVisible();
  });

  it("shows required appointment fields before making a create request", () => {
    render(<CreateAppointmentForm />);
    fireEvent.click(screen.getByRole("button", { name: "Tạo lịch hẹn" }));
    expect(screen.getByText("Mã bệnh nhân là bắt buộc.")).toBeVisible();
    expect(screen.getByText("Ngày hẹn là bắt buộc.")).toBeVisible();
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it("does not navigate from an old appointment edit after the route identity changes", async () => {
    const pending = deferred<AppointmentDTO>();
    mocks.getById.mockResolvedValueOnce(appointment).mockResolvedValueOnce({
      ...appointment,
      appointmentId: "appointment-b",
      patientId: "patient-b",
      reason: "Second appointment",
    });
    mocks.update.mockReturnValue(pending.promise);
    const view = render(<EditAppointmentForm appointmentId="appointment-a" />);
    expect(await screen.findByText("First visit")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    await waitFor(() => expect(mocks.update).toHaveBeenCalledTimes(1));
    view.rerender(<EditAppointmentForm appointmentId="appointment-b" />);
    expect(await screen.findByText("Second appointment")).toBeVisible();
    await act(async () => pending.resolve(appointment));
    expect(mocks.router.replace).not.toHaveBeenCalled();
    expect(screen.getByText("Second appointment")).toBeVisible();
  });

  it("locks a valid edit while its first update is still pending", async () => {
    const pending = deferred<AppointmentDTO>();
    mocks.getById.mockResolvedValue(appointment);
    mocks.update.mockReturnValue(pending.promise);
    render(<EditAppointmentForm appointmentId="appointment-a" />);
    await screen.findByText("First visit");
    const form = screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!;
    await act(async () => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.update).toHaveBeenCalledTimes(1);
  });
});
