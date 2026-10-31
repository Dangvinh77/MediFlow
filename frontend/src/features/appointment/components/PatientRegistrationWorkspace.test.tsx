import { StrictMode } from "react";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { appointmentApi } from "../api";
import { ReferenceProvider } from "@/components/references/ReferenceProvider";
import { PatientRegistrationWorkspace } from "./PatientRegistrationWorkspace";

const mocks = vi.hoisted(() => ({ mine: vi.fn() }));
const auth = vi.hoisted(() => ({ session: "session-a", listeners: new Set<() => void>() }));
vi.mock("@/lib/auth", () => ({ getSessionKey: () => auth.session, subscribeToAuthChanges: (listener: () => void) => { auth.listeners.add(listener); return () => auth.listeners.delete(listener); } }));
vi.mock("../api", () => ({ appointmentApi: { myRegistrations: mocks.mine } }));

const ownRegistration = {
  registrationId: "my-registration", revision: 2, patientId: "signed-patient", doctorId: "doctor-id",
  recordId: null, followUpRecommendation: null,
  departmentId: "department-id", roomId: "room-id", sessionId: "session-id", sessionDate: "2026-10-27",
  startTime: "08:00", endTime: "12:00", receptionMode: "PREBOOKED" as const, visitType: "NEW_VISIT" as const,
  previousRegistrationId: null, status: "AWAITING_PAYMENT" as const, reservationId: "reservation-id",
  reservationRevision: 1, reservationStatus: "HELD" as const, reservationExpiresAt: "2026-10-26T04:15:00Z",
  checkedInAt: null, queueNumber: null, readyForExam: false,
  createdAt: "2026-10-26T04:00:00Z", updatedAt: "2026-10-26T04:00:00Z",
};

describe("patient own registrations", () => {
  beforeEach(() => {
    vi.clearAllMocks(); auth.session = "session-a"; auth.listeners.clear();
    mocks.mine.mockResolvedValue({ content: [ownRegistration], totalElements: 1, totalPages: 1, number: 0, size: 10 });
  });

  it("loads successfully after StrictMode effect cleanup and replay", async () => {
    render(<StrictMode><PatientRegistrationWorkspace enabled /></StrictMode>);
    expect(await screen.findByRole("heading", { name: "Khám mới · 27/10/2026" })).toBeVisible();
    expect(mocks.mine).toHaveBeenCalledTimes(2);
  });

  it("ignores the old account read after a session change and retains refresh", async () => {
    let resolve!: (value: unknown) => void;
    mocks.mine.mockReturnValueOnce(new Promise(done => { resolve = done; })).mockResolvedValueOnce({ content: [], totalPages: 0, number: 0 });
    render(<PatientRegistrationWorkspace enabled />);
    act(() => { auth.session = "session-b"; for (const listener of auth.listeners) listener(); });
    expect(await screen.findByText("Bạn chưa có phiếu đăng ký lịch khám.")).toBeVisible();
    await act(async () => resolve({ content: [ownRegistration], totalPages: 1, number: 0 }));
    expect(screen.queryByRole("heading", { name: "Khám mới · 27/10/2026" })).not.toBeInTheDocument();
    expect(mocks.mine).toHaveBeenCalledTimes(2);
    mocks.mine.mockResolvedValueOnce({ content: [], totalPages: 0, number: 0 });
    await userEvent.click(screen.getByRole("button", { name: "Tải phiếu của tôi" }));
    expect(mocks.mine).toHaveBeenCalledTimes(3);
  });

  it("automatically loads its own scope on entry", async () => {
    render(<PatientRegistrationWorkspace enabled />);
    await vi.waitFor(() => expect(mocks.mine).toHaveBeenCalledTimes(1));
  });

  it("loads the server-filtered own list and does not show a queue before confirmation", async () => {
    const user = userEvent.setup();
    render(<PatientRegistrationWorkspace enabled />);
    await user.click(await screen.findByRole("button", { name: "Tải phiếu của tôi" }));
    expect(await screen.findByRole("heading", { name: "Khám mới · 27/10/2026" })).toBeInTheDocument();
    expect(screen.queryByText("my-registration")).not.toBeInTheDocument();
    expect(mocks.mine).toHaveBeenCalledWith({ page: 0, size: 10 });
    expect(mocks.mine.mock.calls[0][0]).not.toHaveProperty("patientId");
    expect(screen.getByText("Chưa cấp số trước khi xác nhận")).toBeInTheDocument();
  });

  it("does not call the patient own-list endpoint while the scheduling gate is off", () => {
    render(<PatientRegistrationWorkspace enabled={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("chưa được mở");
    expect(appointmentApi.myRegistrations).not.toHaveBeenCalled();
  });

  it("shows an attached follow-up recommendation only from the server-filtered patient list", async () => {
    const user = userEvent.setup();
    mocks.mine.mockResolvedValue({ content: [{
      ...ownRegistration,
      recordId: "source-record-id",
      followUpRecommendation: {
        recommendationId: "recommendation-id", sourceRecordId: "source-record-id", sourceRegistrationId: "source-registration-id",
        patientId: "signed-patient", departmentId: "department-id", preferredDoctorId: "doctor-id",
        recommendedFrom: "2026-11-01", recommendedThrough: "2026-11-10", reason: "Theo dõi huyết áp",
        preparationInstructions: "Mang theo sổ đo huyết áp", createdAt: "2026-10-26T04:00:00Z",
      },
    }], totalElements: 1, totalPages: 1, number: 0, size: 10 });
    render(<PatientRegistrationWorkspace enabled />);
    await user.click(await screen.findByRole("button", { name: "Tải phiếu của tôi" }));

    expect(await screen.findByRole("region", { name: "Đề xuất tái khám" })).toHaveTextContent("01/11/2026");
    expect(screen.getByText("Theo dõi huyết áp")).toBeInTheDocument();
    expect(screen.getByText("Mang theo sổ đo huyết áp")).toBeInTheDocument();
  });
});

it("shows patient-friendly care names and no obsolete hold expiry on a confirmed visit", async () => {
  mocks.mine.mockResolvedValue({ content: [{ ...ownRegistration, status: "CONFIRMED", queueNumber: 3 }], totalPages: 1, number: 0 });
  render(<ReferenceProvider directory={{ canSearch: () => false, canResolve: () => true, search: vi.fn(), resolve: async kind => ({ doctor: "BS Nguyễn An", department: "Khoa Nội", room: "Phòng Nội 1" })[kind] ?? null }}><PatientRegistrationWorkspace enabled /></ReferenceProvider>);
  await userEvent.click(await screen.findByRole("button", { name: "Tải phiếu của tôi" }));
  expect(await screen.findByText("BS Nguyễn An")).toBeVisible();
  expect(await screen.findByText("Khoa Nội")).toBeVisible();
  expect(await screen.findByText("Phòng Nội 1")).toBeVisible();
  expect(screen.queryByText("Hạn thanh toán giữ chỗ")).not.toBeInTheDocument();
  expect(screen.getByText(/Vui lòng đến quầy tiếp nhận/)).toBeVisible();
  expect(screen.queryByText("CONFIRMED")).not.toBeInTheDocument();
});
