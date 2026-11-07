import { webcrypto } from "node:crypto";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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
    Object.values(mocks).forEach(mock => mock.mockReset()); vi.stubGlobal("crypto", webcrypto); sessionStorage.clear();
    mocks.list.mockResolvedValue({ content: [session], totalElements: 1, totalPages: 1, number: 0, size: 20 });
    mocks.registrations.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
  });

  async function registerPatient() {
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Khoa"), "department-id");
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    await user.click(await screen.findByRole("button", { name: /Chọn buổi/ }));
    await user.type(screen.getByLabelText("Bệnh nhân"), "patient-id");
    await user.click(screen.getByRole("button", { name: "Đăng ký và giữ chỗ" }));
    return user;
  }

  it("lets the nurse identify the patient before choosing a session", () => {
    render(<ReceptionWorkspace enabled initialPatientId="patient-from-profile" />);
    expect(screen.getByLabelText("Bệnh nhân")).toHaveValue("patient-from-profile");
    expect(screen.getByLabelText("Bệnh nhân").compareDocumentPosition(screen.getByLabelText("Khoa")) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("requires the next-patient action after success and keeps the selected session", async () => {
    mocks.create.mockResolvedValue(registration);
    render(<ReceptionWorkspace enabled />);
    const user = await registerPatient();
    await screen.findByLabelText("Mã phiếu bàn giao thu ngân");
    await waitFor(() => expect(screen.getByRole("button", { name: "Tiếp nhận bệnh nhân tiếp theo" })).toBeEnabled());
    expect(screen.getByRole("button", { name: "Đăng ký và giữ chỗ" })).toBeDisabled();
    expect(screen.getByLabelText("Loại lượt khám")).toBeDisabled();
    fireEvent.submit(screen.getByRole("button", { name: "Đăng ký và giữ chỗ" }).closest("form")!);
    expect(mocks.create).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole("button", { name: "Tiếp nhận bệnh nhân tiếp theo" }));
    expect(screen.getByLabelText("Bệnh nhân")).toHaveValue("");
    expect(screen.getByRole("heading", { name: /Tiếp nhận vào buổi/ })).toBeVisible();
    expect(screen.queryByLabelText("Mã phiếu bàn giao thu ngân")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đăng ký và giữ chỗ" })).toBeDisabled();
    expect(mocks.create).toHaveBeenCalledTimes(1);
    await user.type(screen.getByLabelText("Bệnh nhân"), "next-patient");
    expect(screen.getByRole("button", { name: "Đăng ký và giữ chỗ" })).toBeEnabled();
  });

  it("shows the date range inline and reports date errors without loading", async () => {
    render(<ReceptionWorkspace enabled />);
    const user = userEvent.setup();
    expect(screen.getByLabelText("Từ ngày")).toBeVisible();
    expect(screen.getByLabelText("Đến ngày")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Tiếp nhận hôm nay" })).not.toBeInTheDocument();
    await user.type(screen.getByLabelText("Khoa"), "department-id");
    fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "" } });
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    expect(screen.getByLabelText("Đến ngày")).toHaveAttribute("aria-invalid", "true");
    expect(mocks.list).not.toHaveBeenCalled();
  });

  it("loads today's sessions directly even after another date range was entered", async () => {
    render(<ReceptionWorkspace enabled />);
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Khoa"), "department-id");
    const dates = document.querySelectorAll<HTMLInputElement>('input[type="date"]');
    fireEvent.change(dates[0], { target: { value: "2025-01-01" } });
    fireEvent.change(dates[1], { target: { value: "2025-01-02" } });
    await user.click(screen.getByRole("button", { name: "Tiếp nhận hôm nay" }));
    await waitFor(() => expect(mocks.list).toHaveBeenCalledTimes(1));
    const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
    expect(mocks.list.mock.calls[0][0]).toMatchObject({ departmentId: "department-id", fromDate: today, toDate: today });
  });

  it("refreshes authoritative capacity and the queue after a successful registration", async () => {
    mocks.create.mockResolvedValue(registration);
    mocks.list.mockResolvedValueOnce({ content: [session], totalPages: 1, number: 0 })
      .mockResolvedValue({ content: [{ ...session, revision: 8, availableCount: 6 }], totalPages: 1, number: 0 });
    mocks.registrations.mockResolvedValueOnce({ content: [], totalPages: 0, number: 0 })
      .mockResolvedValue({ content: [registration], totalPages: 1, number: 0 });
    render(<ReceptionWorkspace enabled />);
    await registerPatient();
    await waitFor(() => expect(mocks.list).toHaveBeenCalledTimes(2));
    expect(await screen.findByText("Còn 6 / 10 chỗ")).toBeVisible();
    const queue = screen.getByRole("region", { name: "Phiếu trong buổi" });
    expect(within(queue).getByText("patient-id")).toBeInTheDocument();
    expect(screen.queryByText("Chưa có phiếu nào trong buổi này.")).not.toBeInTheDocument();
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });

  it("keeps the confirmed receipt when a refresh fails and retries reads without creating again", async () => {
    mocks.create.mockResolvedValue(registration);
    mocks.list.mockResolvedValueOnce({ content: [session], totalPages: 1, number: 0 })
      .mockRejectedValueOnce(new Error("Read unavailable"))
      .mockResolvedValue({ content: [{ ...session, revision: 8, availableCount: 6 }], totalPages: 1, number: 0 });
    mocks.registrations.mockResolvedValue({ content: [registration], totalPages: 1, number: 0 });
    render(<ReceptionWorkspace enabled />);
    const user = await registerPatient();
    expect(await screen.findByLabelText("Mã phiếu bàn giao thu ngân")).toHaveValue("registration-id");
    expect(await screen.findByText(/Phiếu đã được tạo.*chưa cập nhật được/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Đăng ký và giữ chỗ" })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Gửi lại cùng yêu cầu" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Cập nhật danh sách và số chỗ" }));
    expect(await screen.findByText("Còn 6 / 10 chỗ")).toBeVisible();
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });

  it("does not replace another department with a late post-registration refresh", async () => {
    let resolve!: (value: unknown) => void;
    mocks.create.mockResolvedValue(registration);
    mocks.list.mockResolvedValueOnce({ content: [session], totalPages: 1, number: 0 })
      .mockReturnValueOnce(new Promise(done => { resolve = done; }));
    render(<ReceptionWorkspace enabled />);
    const user = await registerPatient();
    await waitFor(() => expect(mocks.list).toHaveBeenCalledTimes(2));
    await user.clear(screen.getByLabelText("Khoa"));
    await user.type(screen.getByLabelText("Khoa"), "another-department");
    await act(async () => resolve({ content: [{ ...session, revision: 8, availableCount: 6 }], totalPages: 1, number: 0 }));
    expect(screen.queryByLabelText("Mã phiếu bàn giao thu ngân")).not.toBeInTheDocument();
    expect(screen.queryByText("Còn 6 / 10 chỗ")).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /Tiếp nhận vào buổi/ })).not.toBeInTheDocument();
  });

  it("offers finding and creating patient profiles before selecting a session", () => {
    render(<ReceptionWorkspace enabled />);
    expect(screen.getByRole("link", { name: "Tìm hồ sơ bệnh nhân" })).toHaveAttribute("href", "/patients");
    expect(screen.getByRole("link", { name: "Tạo hồ sơ bệnh nhân" })).toHaveAttribute("href", "/patients/new");
  });

  it("blocks missing, inverted and oversized dates with associated errors before requesting", async () => {
    const user = userEvent.setup();
    render(<ReceptionWorkspace enabled />);
    await user.type(screen.getByLabelText("Khoa"), "department-id");
    const from = screen.getByLabelText("Từ ngày"); const to = screen.getByLabelText("Đến ngày");
    expect(from).toHaveValue(to.getAttribute("value"));
    fireEvent.change(to, { target: { value: "" } });
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    expect(to).toHaveAttribute("aria-invalid", "true");
    expect(to).toHaveAccessibleDescription("Vui lòng chọn ngày bắt buộc.");
    expect(mocks.list).not.toHaveBeenCalled();
    fireEvent.change(from, { target: { value: "2026-11-01" } });
    fireEvent.change(to, { target: { value: "2026-10-31" } });
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    expect(to).toHaveAccessibleDescription("Đến ngày phải bằng hoặc sau Từ ngày.");
    expect(mocks.list).not.toHaveBeenCalled();
    fireEvent.change(from, { target: { value: "2026-09-01" } });
    await user.click(screen.getByRole("button", { name: "Tải buổi khám" }));
    expect(to).toHaveAccessibleDescription("Khoảng tìm kiếm tối đa 30 ngày giữa hai mốc.");
    expect(mocks.list).not.toHaveBeenCalled();
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

    expect(screen.getByRole("button", { name: "Chọn buổi" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Tải buổi khám" })).toBeDisabled();
    const listCalls = mocks.list.mock.calls.length;
    fireEvent.submit(screen.getByRole("button", { name: "Tải buổi khám" }).closest("form")!);
    expect(mocks.list).toHaveBeenCalledTimes(listCalls);
    fireEvent.change(screen.getByLabelText("Khoa"), { target: { value: "another-department" } });
    expect(screen.getByLabelText("Khoa")).toHaveValue("department-id");

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

    expect(screen.getByRole("region", { name: "Ngữ cảnh tiếp nhận" })).toHaveTextContent("patient-id");
    expect(screen.getByText(/Đã chọn lượt nguồn hoàn tất/)).toHaveTextContent("20/10/26");
    expect(screen.getByText(/Đã chọn lượt nguồn hoàn tất/)).toHaveTextContent("Chưa đăng ký tái khám");
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
