import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { AppointmentDTO } from "../types";

const mocks = vi.hoisted(() => ({ search: vi.fn(), role: "NURSE", router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
vi.mock("../api", () => ({ appointmentApi: { search: mocks.search } }));
import { AppointmentTable } from "./AppointmentTable";

const emptyPage = { content: [], number: 0, size: 20, totalPages: 0, totalElements: 0, first: true, last: true };
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

describe("Appointment list filters and request lifecycle", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.search.mockReset(); mocks.role = "NURSE"; });

  it("does not send an invalid department UUID", async () => {
    mocks.search.mockResolvedValue(emptyPage);
    render(<AppointmentTable />);
    await screen.findByText("Chưa có lịch hẹn phù hợp.");
    fireEvent.change(screen.getByLabelText("Khoa"), { target: { value: "department" } });
    fireEvent.click(screen.getByRole("button", { name: "Lọc" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Chọn khoa từ danh sách tìm kiếm.");
    expect(mocks.search).toHaveBeenCalledTimes(1);
  });

  it("uses the submitted filters when moving to another page", async () => {
    mocks.search.mockResolvedValue({ ...emptyPage, content: [appointment], totalPages: 2, totalElements: 21 });
    render(<AppointmentTable />);
    await screen.findByText("First visit");
    fireEvent.change(screen.getByLabelText("Khoa"), { target: { value: "123e4567-e89b-42d3-a456-426614174000" } });
    fireEvent.change(screen.getByLabelText("Ngày hẹn"), { target: { value: "2026-10-17" } });
    fireEvent.click(screen.getByRole("button", { name: "Lọc" }));
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(2));
    fireEvent.change(screen.getByLabelText("Khoa"), { target: { value: "123e4567-e89b-42d3-a456-426614174001" } });
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(3));
    expect(mocks.search).toHaveBeenLastCalledWith({
      departmentId: "123e4567-e89b-42d3-a456-426614174000",
      appointmentDate: "2026-10-17",
      page: 1,
    });
  });

  it("retries the submitted filters and keeps the correlation ID visible", async () => {
    mocks.search.mockResolvedValueOnce(emptyPage)
      .mockRejectedValueOnce(new ApiRequestError("Tạm thời không tải được", 503, "UNAVAILABLE", [], "appointment-correlation"))
      .mockResolvedValueOnce(emptyPage);
    render(<AppointmentTable />);
    await screen.findByText("Chưa có lịch hẹn phù hợp.");
    fireEvent.change(screen.getByLabelText("Khoa"), { target: { value: "123e4567-e89b-42d3-a456-426614174000" } });
    fireEvent.click(screen.getByRole("button", { name: "Lọc" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Tạm thời không tải được");
    expect(screen.getByText("appointment-correlation")).toBeVisible();
    fireEvent.change(screen.getByLabelText("Khoa"), { target: { value: "123e4567-e89b-42d3-a456-426614174001" } });
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("Chưa có lịch hẹn phù hợp.");
    expect(mocks.search).toHaveBeenLastCalledWith({ departmentId: "123e4567-e89b-42d3-a456-426614174000", page: 0 });
  });

  it("keeps the newer filtered page when an older filter fails later", async () => {
    const older = deferred<typeof emptyPage>();
    mocks.search.mockResolvedValueOnce(emptyPage)
      .mockReturnValueOnce(older.promise)
      .mockResolvedValueOnce({ ...emptyPage, content: [{ ...appointment, reason: "Latest filter" }], totalPages: 1, totalElements: 1 });
    render(<AppointmentTable />);
    await screen.findByText("Chưa có lịch hẹn phù hợp.");
    const form = screen.getByRole("button", { name: "Lọc" }).closest("form")!;
    fireEvent.change(screen.getByLabelText("Khoa"), { target: { value: "123e4567-e89b-42d3-a456-426614174000" } });
    fireEvent.submit(form);
    fireEvent.change(screen.getByLabelText("Khoa"), { target: { value: "123e4567-e89b-42d3-a456-426614174001" } });
    fireEvent.submit(form);
    expect(await screen.findByText("Latest filter")).toBeVisible();
    await act(async () => older.reject(new ApiRequestError("Older filter failed", 503, "UNAVAILABLE")));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("Latest filter")).toBeVisible();
  });

  it("does not redirect after a submitted request returns 401 after unmount", async () => {
    let reject!: (cause: unknown) => void;
    mocks.search.mockResolvedValueOnce(emptyPage)
      .mockReturnValueOnce(new Promise((_, fail) => { reject = fail; }));
    const view = render(<AppointmentTable />);
    await screen.findByText("Chưa có lịch hẹn phù hợp.");
    fireEvent.change(screen.getByLabelText("Ngày hẹn"), { target: { value: "2026-10-18" } });
    fireEvent.click(screen.getByRole("button", { name: "Lọc" }));
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(2));
    view.unmount();
    await act(async () => reject(new ApiRequestError("Phiên hết hạn", 401, "UNAUTHORIZED")));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  it.each([
    ["ADMIN", true, true],
    ["DOCTOR", false, true],
    ["NURSE", true, true],
    ["MANAGER", false, false],
  ])("shows only actions allowed for %s", async (role, canCreate, canView) => {
    mocks.role = role;
    mocks.search.mockResolvedValue({ ...emptyPage, content: [appointment], totalPages: 1, totalElements: 1 });
    render(<AppointmentTable />);
    await screen.findByText("First visit");
    expect(Boolean(screen.queryByRole("link", { name: "Tạo lịch hẹn" }))).toBe(canCreate);
    expect(Boolean(screen.queryByRole("link", { name: "Xem" }))).toBe(canView);
  });
});
