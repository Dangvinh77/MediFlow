import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { TreatmentCourseDTO } from "../types";
import { TreatmentCoursesPanel } from "./TreatmentCoursesPanel";

const mocks = vi.hoisted(() => ({
  list: vi.fn(), open: vi.fn(), addVisit: vi.fn(), close: vi.fn(),
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
vi.mock("../api", () => ({ medicalRecordApi: { treatmentCourses: mocks.list, openTreatmentCourse: mocks.open, addCourseVisit: mocks.addVisit, closeTreatmentCourse: mocks.close } }));

const course: TreatmentCourseDTO = { courseId: "course-a", patientId: "patient-a", departmentId: "department-a", problem: "Đau đầu", openingReason: "Theo dõi", responsibleDoctorId: "doctor-a", status: "OPEN", recordIds: ["record-a"], openedAt: "2026-11-01T02:00:00Z", lastVisitDate: "2026-11-01", reviewDue: true, closureDecision: null, closureNote: null, closedAt: null };
const panelProps = (canClose = true) => ({
  patientId: "patient-a", recordId: "record-a", canOpen: true, canAddVisit: true,
  canCloseCourse: (candidate: TreatmentCourseDTO) => canClose && candidate.courseId === course.courseId,
});

describe("TreatmentCoursesPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.sessionKey = "doctor-session";
    mocks.listeners = [];
    mocks.list.mockResolvedValue([]);
    mocks.open.mockResolvedValue(course);
    mocks.addVisit.mockResolvedValue(course);
    mocks.close.mockResolvedValue({ ...course, status: "CLOSED" });
  });

  it("shows loading, empty, forbidden, hidden and a retryable outage", async () => {
    let resolve!: (value: unknown) => void;
    mocks.list.mockReturnValueOnce(new Promise((done) => { resolve = done; }));
    const loading = render(<TreatmentCoursesPanel {...panelProps()} />);
    expect(screen.getByText(/Đang tải quá trình điều trị/)).toBeVisible();
    resolve([]);
    expect(await screen.findByText(/Chưa có quá trình điều trị/)).toBeVisible();
    loading.unmount();

    mocks.list.mockRejectedValueOnce(new ApiRequestError("Cấm", 403));
    const denied = render(<TreatmentCoursesPanel {...panelProps()} />);
    expect(await screen.findByText(/Bạn không có quyền xem quá trình điều trị/)).toBeVisible();
    denied.unmount();

    mocks.list.mockRejectedValueOnce(new ApiRequestError("Tắt", 404, "RESOURCE_NOT_FOUND"));
    const hidden = render(<TreatmentCoursesPanel {...panelProps()} />);
    await waitFor(() => expect(screen.queryByRole("region", { name: "Quá trình điều trị" })).not.toBeInTheDocument());
    hidden.unmount();

    mocks.list.mockRejectedValueOnce(new Error("offline"));
    mocks.list.mockResolvedValueOnce([]);
    const unavailable = render(<TreatmentCoursesPanel {...panelProps()} />);
    fireEvent.click(await screen.findByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText(/Chưa có quá trình điều trị/)).toBeVisible();
    unavailable.unmount();
  });

  it("opens a course, refreshes, and attaches the current visit", async () => {
    const newlyOpened = { ...course, courseId: "course-new", recordIds: [] as string[] };
    mocks.list.mockResolvedValueOnce([course]).mockResolvedValueOnce([course, newlyOpened]);
    render(<TreatmentCoursesPanel {...panelProps()} />);
    expect(await screen.findByText("Cần rà soát")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: /Mở quá trình/ }));
    fireEvent.change(screen.getByLabelText(/^Vấn đề điều trị/), { target: { value: "Đau đầu mới" } });
    fireEvent.change(screen.getByLabelText(/^Lý do mở quá trình/), { target: { value: "Theo dõi liên tục" } });
    fireEvent.click(screen.getByRole("button", { name: "Tạo quá trình" }));
    await waitFor(() => expect(mocks.open).toHaveBeenCalledWith(
      { patientId: "patient-a", problem: "Đau đầu mới", openingReason: "Theo dõi liên tục", recordId: "record-a" },
      expect.objectContaining({ idempotencyKey: expect.any(String) }),
    ));

    fireEvent.click(await screen.findByRole("button", { name: /Gắn lượt hiện tại/ }));
    await waitFor(() => expect(mocks.addVisit).toHaveBeenCalledWith(
      "course-new", { recordId: "record-a" }, expect.objectContaining({ idempotencyKey: expect.any(String) }),
    ));
  });

  it("requires a closure decision and note before closing", async () => {
    mocks.list.mockResolvedValueOnce([course]);
    render(<TreatmentCoursesPanel {...panelProps()} />);
    await screen.findByText("Cần rà soát");
    fireEvent.click(screen.getByRole("button", { name: "Đóng quá trình" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận đóng quá trình" }));
    expect(mocks.close).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(/^Quyết định kết thúc/), { target: { value: "RESOLVED" } });
    fireEvent.change(screen.getByLabelText(/^Ghi chú kết thúc/), { target: { value: "Đã ổn định" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận đóng quá trình" }));
    await waitFor(() => expect(mocks.close).toHaveBeenCalledWith(
      "course-a", { decision: "RESOLVED", note: "Đã ổn định" }, expect.objectContaining({ idempotencyKey: expect.any(String) }),
    ));
  });

  it("removes the close form when close permission is revoked", async () => {
    mocks.list.mockResolvedValueOnce([course]);
    const view = render(<TreatmentCoursesPanel {...panelProps()} />);
    await screen.findByText("Cần rà soát");
    fireEvent.click(screen.getByRole("button", { name: "Đóng quá trình" }));
    expect(screen.getByLabelText(/^Quyết định kết thúc/)).toBeVisible();
    view.rerender(<TreatmentCoursesPanel {...panelProps(false)} />);
    expect(screen.queryByLabelText(/^Quyết định kết thúc/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xác nhận đóng quá trình" })).not.toBeInTheDocument();
  });
});
