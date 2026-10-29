import { webcrypto } from "node:crypto";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { appointmentApi } from "../api";
import { ReceptionWorkspace } from "./ReceptionWorkspace";

const mocks = vi.hoisted(() => ({ list: vi.fn(), create: vi.fn(), registrations: vi.fn(), registration: vi.fn(), arrival: vi.fn(), followUpSources: vi.fn() }));
vi.mock("../api", () => ({ appointmentApi: {
  listConsultationSessions: mocks.list,
  createRegistration: mocks.create,
  registrationsBySession: mocks.registrations,
  registration: mocks.registration,
  markRegistrationArrival: mocks.arrival,
  followUpSources: mocks.followUpSources,
} }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "nurse-session" }));
vi.mock("@/components/references/ReferenceInput", () => ({
  ReferenceInput: ({ kind, value, onValueChange }: { kind: string; value: string; onValueChange: (value: string) => void }) => (
    <label>{kind === "department" ? "Khoa" : "Bệnh nhân"}<input aria-label={kind === "department" ? "Khoa" : "Bệnh nhân"} value={value} onChange={event => onValueChange(event.target.value)} /></label>
  ),
}));

const session = {
  sessionId: "session-id", revision: 7, doctorId: "doctor-id", departmentId: "department-id", roomId: "room-id",
  sessionDate: "2026-10-27", startTime: "08:00", endTime: "12:00", breaks: [], capacity: 10,
  status: "OPEN" as const, heldCount: 1, paymentLockedCount: 0, confirmedCount: 2, consumedCount: 0,
  availableCount: 7, observedAt: "2026-10-26T04:00:00Z",
};
const registration = {
  registrationId: "registration-id", revision: 1, patientId: "patient-id", doctorId: "doctor-id",
  departmentId: "department-id", roomId: "room-id", sessionId: "session-id", sessionDate: "2026-10-27",
  startTime: "08:00", endTime: "12:00", receptionMode: "AT_COUNTER" as const, visitType: "NEW_VISIT" as const,
  previousRegistrationId: null, status: "AWAITING_PAYMENT" as const, reservationId: "reservation-id",
  reservationRevision: 1, reservationStatus: "HELD" as const, reservationExpiresAt: "2026-10-26T04:15:00Z",
  checkedInAt: "2026-10-26T04:00:00Z", queueNumber: null, readyForExam: false,
  createdAt: "2026-10-26T04:00:00Z", updatedAt: "2026-10-26T04:00:00Z",
};
const confirmedPrebooked = {
  ...registration,
  revision: 2,
  receptionMode: "PREBOOKED" as const,
  status: "CONFIRMED" as const,
  reservationStatus: "CONFIRMED" as const,
  checkedInAt: null,
  queueNumber: 3,
};

describe("reception session registration", () => {
  beforeEach(() => {
    vi.clearAllMocks(); vi.stubGlobal("crypto", webcrypto); sessionStorage.clear();
    mocks.list.mockResolvedValue({ content: [session], totalElements: 1, totalPages: 1, number: 0, size: 20 });
    mocks.registrations.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });

  it("distinguishes a search not yet run from an empty result", async () => {
    const user = userEvent.setup();
    mocks.list.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
    render(<ReceptionWorkspace enabled />);
    expect(screen.queryByText("Chưa có buổi khám trong bộ lọc.")).not.toBeInTheDocument();
    expect(screen.getByText(/Chọn khoa và khoảng ngày/)).toBeInTheDocument();
    await user.type(screen.getByLabelText("Khoa"), "department-id");
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    expect(await screen.findByText("Chưa có buổi khám trong bộ lọc.")).toBeInTheDocument();
  });

  it("cannot select stale sessions while a replacement filter is loading or fails", async () => {
    let reject!: (cause: unknown) => void;
    mocks.list.mockResolvedValueOnce({ content: [session], totalPages: 1, number: 0 })
      .mockReturnValueOnce(new Promise((_, fail) => { reject = fail; }));
    const user = userEvent.setup();
    render(<ReceptionWorkspace enabled />);
    await user.type(screen.getByLabelText("Khoa"), "department-id");
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    await screen.findByRole("button", { name: "Chọn buổi" });
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    expect(screen.queryByRole("button", { name: "Chọn buổi" })).not.toBeInTheDocument();
    await act(async () => reject(new Error("Không thể tải buổi mới")));
    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải buổi mới");
    expect(screen.queryByRole("button", { name: "Chọn buổi" })).not.toBeInTheDocument();
  });

  it("retries an uncertain hold with the same idempotency key and reports awaiting payment", async () => {
    const user = userEvent.setup();
    mocks.create.mockRejectedValueOnce(new TypeError("Connection lost")).mockResolvedValueOnce(registration);
    render(<ReceptionWorkspace enabled />);
    await user.type(screen.getByLabelText("Khoa"), "department-id");
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    await user.click(await screen.findByRole("button", { name: /Chọn buổi/ }));
    await user.type(screen.getByLabelText("Bệnh nhân"), "patient-id");
    await user.click(screen.getByRole("button", { name: "Đăng ký và giữ chỗ" }));
    expect(await screen.findByText(/Chưa xác định phiếu đã được tạo/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Gửi lại cùng yêu cầu" }));
    expect(await screen.findByText("Đang chờ thu tiền")).toBeInTheDocument();
    expect(mocks.create).toHaveBeenCalledTimes(2);
    expect(mocks.create.mock.calls[0][0]).toEqual({
      patientId: "patient-id", sessionId: "session-id", expectedSessionRevision: 7,
      receptionMode: "AT_COUNTER", visitType: "NEW_VISIT", previousRegistrationId: null,
      followUpRecommendationId: null, replacementReason: null, patientAgreedToDoctorReplacement: null, reason: null,
    });
    expect(mocks.create.mock.calls[0][1]).toEqual(mocks.create.mock.calls[1][1]);
    expect(mocks.create.mock.calls[0][1].idempotencyKey).toBeTruthy();
    expect(mocks.create.mock.calls[0][1].correlationId).toBeTruthy();
    expect(screen.getByText("Số thứ tự").nextElementSibling).toHaveTextContent("Chưa cấp trước khi xác nhận");
    expect(screen.getByLabelText("Mã phiếu bàn giao thu ngân")).toHaveValue("registration-id");
    expect(screen.getByRole("link", { name: "Mở phiếu khám vừa đăng ký" })).toHaveAttribute("href", "/appointments/registration-id");
    await user.clear(screen.getByLabelText("Bệnh nhân"));
    expect(screen.queryByLabelText("Mã phiếu bàn giao thu ngân")).not.toBeInTheDocument();
  });

  it("does not call V2 endpoints while the scheduling gate is off", () => {
    render(<ReceptionWorkspace enabled={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("chưa được mở");
    expect(appointmentApi.listConsultationSessions).not.toHaveBeenCalled();
  });

  it("records arrival for a confirmed prebooked patient and retries the same revision command", async () => {
    const user = userEvent.setup();
    mocks.registrations.mockResolvedValue({
      content: [{
        registrationId: "registration-id", patientId: "patient-id", visitType: "NEW_VISIT",
        status: "CONFIRMED", checkedInAt: null, queueNumber: 3, readyForExam: false,
      }],
      totalElements: 1, totalPages: 1, number: 0, size: 20,
    });
    mocks.registration.mockResolvedValue(confirmedPrebooked);
    mocks.arrival.mockRejectedValueOnce(new TypeError("Connection lost"))
      .mockResolvedValueOnce({ ...confirmedPrebooked, revision: 3, checkedInAt: "2026-10-27T01:00:00Z" });

    render(<ReceptionWorkspace enabled />);
    await user.type(screen.getByLabelText("Khoa"), "department-id");
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    await user.click(await screen.findByRole("button", { name: /Chọn buổi/ }));
    await user.click(await screen.findByRole("button", { name: "Tải phiếu trong buổi" }));
    await user.click(await screen.findByRole("button", { name: "Ghi nhận đã đến" }));

    expect(await screen.findByText(/Chưa xác định trạng thái đã đến/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Gửi lại cùng lệnh đến" }));
    expect(await screen.findByText("Đã ghi nhận khách đến" )).toBeInTheDocument();
    expect(mocks.registration).toHaveBeenCalledWith("registration-id");
    expect(mocks.arrival).toHaveBeenCalledTimes(2);
    expect(mocks.arrival.mock.calls[0][0]).toBe("registration-id");
    expect(mocks.arrival.mock.calls[0][1]).toEqual({ expectedRevision: 2 });
    expect(mocks.arrival.mock.calls[0][1]).toEqual(mocks.arrival.mock.calls[1][1]);
    expect(mocks.arrival.mock.calls[0][2]).toEqual(mocks.arrival.mock.calls[1][2]);
    expect(mocks.arrival.mock.calls[0][2].idempotencyKey).toBeTruthy();
    expect(mocks.arrival.mock.calls[0][2].correlationId).toBeTruthy();
  });

  it("links a follow-up registration to its completed source and warns without blocking an out-of-window date", async () => {
    const user = userEvent.setup();
    mocks.followUpSources.mockResolvedValue({
      content: [{
        registrationId: "source-registration", recordId: "source-record", patientId: "patient-id",
        departmentId: "department-id", doctorId: "doctor-id", completedAt: "2026-10-20T03:00:00Z",
        recommendation: {
          recommendationId: "recommendation-id", sourceRecordId: "source-record", sourceRegistrationId: "source-registration",
          patientId: "patient-id", departmentId: "department-id", preferredDoctorId: "doctor-id",
          recommendedFrom: "2026-11-01", recommendedThrough: "2026-11-30", reason: "Review symptoms.",
          preparationInstructions: null, createdAt: "2026-10-20T03:00:00Z",
        },
      }], totalElements: 1, totalPages: 1, number: 0, size: 20,
    });
    mocks.create.mockResolvedValue({ ...registration, visitType: "FOLLOW_UP" });

    render(<ReceptionWorkspace enabled />);
    await user.type(screen.getByLabelText("Khoa"), "department-id");
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    await user.click(await screen.findByRole("button", { name: /Chọn buổi/ }));
    await user.type(screen.getByLabelText("Bệnh nhân"), "patient-id");
    await user.selectOptions(screen.getByLabelText("Loại lượt khám"), "FOLLOW_UP");
    await user.click(screen.getByRole("button", { name: "Tìm lượt khám trước" }));
    await user.click(await screen.findByRole("button", { name: "Chọn lượt khám trước" }));

    expect(screen.getByText(/nằm ngoài thời gian bác sĩ khuyến nghị/)).toHaveAttribute("role", "status");
    await user.click(screen.getByRole("button", { name: "Đăng ký và giữ chỗ" }));
    await screen.findByText("Đang chờ thu tiền");
    expect(mocks.followUpSources).toHaveBeenCalledWith({ patientId: "patient-id", page: 0, size: 20 });
    expect(mocks.create.mock.calls[0][0]).toEqual({
      patientId: "patient-id", sessionId: "session-id", expectedSessionRevision: 7,
      receptionMode: "AT_COUNTER", visitType: "FOLLOW_UP", previousRegistrationId: "source-registration",
      followUpRecommendationId: "recommendation-id", replacementReason: null,
      patientAgreedToDoctorReplacement: null, reason: null,
    });
  });

  it("requires a self-return reason and patient agreement when the follow-up uses a different doctor", async () => {
    const user = userEvent.setup();
    mocks.followUpSources.mockResolvedValue({
      content: [{
        registrationId: "source-registration", recordId: "source-record", patientId: "patient-id",
        departmentId: "department-id", doctorId: "previous-doctor", completedAt: "2026-10-20T03:00:00Z",
        recommendation: null,
      }], totalElements: 1, totalPages: 1, number: 0, size: 20,
    });
    mocks.create.mockResolvedValue({ ...registration, visitType: "FOLLOW_UP" });

    render(<ReceptionWorkspace enabled />);
    await user.type(screen.getByLabelText("Khoa"), "department-id");
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    await user.click(await screen.findByRole("button", { name: /Chọn buổi/ }));
    await user.type(screen.getByLabelText("Bệnh nhân"), "patient-id");
    await user.selectOptions(screen.getByLabelText("Loại lượt khám"), "FOLLOW_UP");
    await user.click(screen.getByRole("button", { name: "Tìm lượt khám trước" }));
    await user.click(await screen.findByRole("button", { name: "Chọn lượt khám trước" }));
    await user.click(screen.getByRole("button", { name: "Đăng ký và giữ chỗ" }));
    expect(await screen.findByText(/Cần lý do tái khám/)).toBeInTheDocument();
    expect(mocks.create).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText("Lý do đăng ký tái khám"), "Follow-up after treatment");
    await user.type(screen.getByLabelText("Lý do chọn bác sĩ khác"), "Patient requested the available doctor");
    await user.click(screen.getByLabelText(/Bệnh nhân đồng ý/));
    await user.click(screen.getByRole("button", { name: "Đăng ký và giữ chỗ" }));
    await screen.findByText("Đang chờ thu tiền");
    expect(mocks.create.mock.calls[0][0]).toMatchObject({
      visitType: "FOLLOW_UP", previousRegistrationId: "source-registration", followUpRecommendationId: null,
      reason: "Follow-up after treatment", replacementReason: "Patient requested the available doctor",
      patientAgreedToDoctorReplacement: true,
    });
  });
});
