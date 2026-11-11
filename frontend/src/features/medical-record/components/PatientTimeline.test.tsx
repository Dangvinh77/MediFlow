import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { TimelineEntryDTO } from "../types";

const mocks = vi.hoisted(() => ({ timeline: vi.fn(), role: "DOCTOR", router: { replace: vi.fn() } }));
vi.mock("../api", () => ({ medicalRecordApi: { timeline: mocks.timeline } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
vi.mock("@/components/references/ReferenceInput", () => ({
  ReferenceInput: ({ value, onValueChange, ...props }: { value: string; onValueChange: (value: string) => void }) =>
    <input {...props} value={value} onChange={(event) => onValueChange(event.target.value)} />,
}));
vi.mock("@/components/references/ReferenceText", () => ({
  ReferenceText: ({ value, fallback }: { value: string | null; fallback: string }) => <span>{value ?? fallback}</span>,
}));

import { PatientTimeline } from "./PatientTimeline";

const patientId = "11111111-1111-4111-8111-111111111111";
const entry: TimelineEntryDTO = {
  recordId: "record-current",
  examinationDate: "2026-11-09",
  departmentId: "department-a",
  doctorId: "doctor-a",
  responsibleDoctorId: "doctor-b",
  visitKind: "NEW",
  status: "COMPLETED",
  disposition: "OUTPATIENT_FOLLOW_UP",
  previousRecordId: null,
  treatmentCourseIds: [],
};
const page = (content: TimelineEntryDTO[], totalPages = 1) => ({
  content,
  totalElements: content.length,
  totalPages,
  number: 0,
  size: 20,
});

function searchPatient(id = patientId) {
  fireEvent.change(screen.getByLabelText("Bệnh nhân"), { target: { value: id } });
  fireEvent.click(screen.getByRole("button", { name: "Tìm hồ sơ" }));
}

describe("PatientTimeline", () => {
  beforeEach(() => mocks.timeline.mockReset().mockResolvedValue(page([entry])));

  it("shows date, department, responsible doctor, visit kind, and status", async () => {
    render(<PatientTimeline />);
    searchPatient();

    expect(await screen.findByText("Khám mới")).toBeVisible();
    expect(screen.getByText("09/11/2026")).toBeVisible();
    expect(screen.getByText("department-a")).toBeVisible();
    expect(screen.getByText("doctor-b")).toBeVisible();
    expect(screen.getAllByText("Đã hoàn tất").at(-1)).toBeVisible();
  });

  it("links a return visit to its source visit", async () => {
    mocks.timeline.mockResolvedValue(page([{ ...entry, visitKind: "RETURN", previousRecordId: "record-source" }]));
    render(<PatientTimeline />);
    searchPatient();

    const source = await screen.findByRole("link", { name: "Lượt trước" });
    expect(source).toHaveAttribute("href", "/records/record-source");
    expect(screen.getByText("Quay lại")).toBeVisible();
  });

  it("filters by department and status", async () => {
    render(<PatientTimeline />);
    searchPatient();
    await screen.findAllByText("Khám mới");

    fireEvent.change(screen.getByLabelText("Khoa"), { target: { value: "department-b" } });
    fireEvent.change(screen.getByLabelText("Trạng thái"), { target: { value: "COMPLETED" } });
    fireEvent.click(screen.getByRole("button", { name: "Lọc" }));

    await waitFor(() => expect(mocks.timeline).toHaveBeenLastCalledWith(patientId, {
      page: 0, size: 20, departmentId: "department-b", status: "COMPLETED",
    }));
  });

  it("pages through timeline results", async () => {
    mocks.timeline.mockResolvedValue(page([entry], 2));
    render(<PatientTimeline />);
    searchPatient();
    await screen.findByText("Trang 1 / 2");
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));

    await waitFor(() => expect(mocks.timeline).toHaveBeenLastCalledWith(patientId, {
      page: 1, size: 20, departmentId: null, status: null,
    }));
  });

  it("groups visits only under the returned course IDs, including multiple memberships", async () => {
    mocks.timeline.mockResolvedValue(page([
      { ...entry, recordId: "record-one", treatmentCourseIds: ["course-a", "course-b"] },
      { ...entry, recordId: "record-two", treatmentCourseIds: ["course-a"] },
      { ...entry, recordId: "record-three", treatmentCourseIds: [] },
    ]));
    render(<PatientTimeline />);
    searchPatient();

    await screen.findAllByText("Khám mới");
    expect(screen.getAllByText(/Quá trình điều trị/)).toHaveLength(2);
    expect(screen.getByText("Lượt chưa gắn vào quá trình")).toBeVisible();
    expect(screen.getAllByRole("link", { name: "Mở hồ sơ" })).toHaveLength(4);
  });

  it("shows loading, retryable outage, forbidden, and empty states distinctly", async () => {
    let finish!: (value: ReturnType<typeof page>) => void;
    mocks.timeline.mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
    const view = render(<PatientTimeline />);
    searchPatient();
    expect(screen.getByText("Đang tải dòng thời gian…")).toBeVisible();
    await act(async () => finish(page([])));
    expect(await screen.findByText("Chưa có lượt khám")).toBeVisible();

    mocks.timeline.mockRejectedValueOnce(new ApiRequestError("Dịch vụ đang gián đoạn", 503, "SERVICE_UNAVAILABLE"));
    fireEvent.click(screen.getByRole("button", { name: "Tìm hồ sơ" }));
    expect(await screen.findByText("Dịch vụ đang gián đoạn")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("Khám mới");

    mocks.timeline.mockRejectedValueOnce(new ApiRequestError("Bị từ chối", 403, "RECORD_ACCESS_DENIED"));
    fireEvent.click(screen.getByRole("button", { name: "Tìm hồ sơ" }));
    expect(await screen.findByText("Bạn không có quyền xem hồ sơ của bệnh nhân này")).toBeVisible();
    view.unmount();
  });

  it("drops a response when the patient selection changes", async () => {
    let finish!: (value: ReturnType<typeof page>) => void;
    mocks.timeline.mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
    render(<PatientTimeline />);
    searchPatient();
    fireEvent.change(screen.getByLabelText("Bệnh nhân"), { target: { value: "22222222-2222-4222-8222-222222222222" } });
    await act(async () => finish(page([entry])));

    expect(screen.queryByText("Khám mới")).not.toBeInTheDocument();
  });

  it("offers no edit action", async () => {
    render(<PatientTimeline />);
    searchPatient();
    await screen.findByText("Khám mới");

    expect(screen.queryByRole("button", { name: /Sửa|Chỉnh sửa/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Sửa|Chỉnh sửa/ })).not.toBeInTheDocument();
    expect(screen.getAllByRole("link").every((link) => !link.getAttribute("href")?.endsWith("/edit"))).toBe(true);
  });
});
