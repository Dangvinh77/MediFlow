import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Role } from "@/lib/roles";
import type { AppointmentDTO } from "../types";

const mocks = vi.hoisted(() => ({
  checkIn: vi.fn(),
  startExam: vi.fn(),
  changeStatus: vi.fn(),
  replace: vi.fn(),
  token: "nurse-session-a",
  authListeners: new Set<() => void>(),
}));

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: mocks.replace }) }));
vi.mock("@/lib/auth", () => ({
  getToken: () => mocks.token,
  subscribeToAuthChanges: (listener: () => void) => {
    mocks.authListeners.add(listener);
    return () => mocks.authListeners.delete(listener);
  },
}));
vi.mock("../api", () => ({
  appointmentApi: {
    checkIn: mocks.checkIn,
    startExam: mocks.startExam,
    changeStatus: mocks.changeStatus,
  },
}));

import { OutpatientExamActions } from "./OutpatientExamActions";

const pendingAppointment: AppointmentDTO = {
  appointmentId: "7c7b386a-a2a0-4b8f-b894-3f5d5eb1369c",
  patientId: "de98e95b-a9c4-4b23-852b-9472a29fa8bd",
  doctorId: "3a1900b7-601a-48e2-90ee-5c7adab4c998",
  departmentId: "ac8c9216-924e-44fc-b82f-2a136aed8fd1",
  appointmentDate: "2026-10-20",
  appointmentTime: "09:00",
  status: "PENDING",
  reason: "First visit",
  createdAt: "2026-10-19T08:00:00Z",
  updatedAt: null,
  careContractVersion: 0,
  examClearanceId: null,
  examClearanceAt: null,
  emergencyOverrideId: null,
  examPriceCode: null,
  checkedInAt: null,
  examinationStartedAt: null,
  completedAt: null,
};

const readyAppointment: AppointmentDTO = {
  ...pendingAppointment,
  status: "READY_FOR_EXAM",
  careContractVersion: 1,
  examClearanceId: "1cb989c4-209b-4b52-9d13-320d0f3bf281",
  examClearanceAt: "2026-10-20T08:30:00Z",
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function renderActions({
  appointment = pendingAppointment,
  role = "NURSE",
  onUpdated = vi.fn(),
  onRefresh = vi.fn(),
}: {
  appointment?: AppointmentDTO;
  role?: Role | null;
  onUpdated?: (updated: AppointmentDTO) => void;
  onRefresh?: () => void;
} = {}) {
  const view = render(
    <OutpatientExamActions appointment={appointment} role={role} onUpdated={onUpdated} onRefresh={onRefresh} />,
  );
  return { ...view, onUpdated, onRefresh };
}

describe("outpatient appointment actions", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.token = "nurse-session-a";
    mocks.authListeners.clear();
  });

  it.each([
    ["ADMIN", pendingAppointment, true],
    ["NURSE", pendingAppointment, true],
    ["DOCTOR", pendingAppointment, false],
    ["MANAGER", pendingAppointment, false],
    ["NURSE", { ...pendingAppointment, status: "READY_FOR_EXAM" }, false],
    ["NURSE", { ...pendingAppointment, careContractVersion: 1 }, false],
  ] as const)("offers check-in only to an authorized role on legacy pending appointments", (role, appointment, allowed) => {
    renderActions({ role, appointment });
    if (allowed) {
      expect(screen.getByRole("button", { name: "Tiếp nhận bệnh nhân" })).toBeEnabled();
    } else {
      expect(screen.queryByRole("button", { name: "Tiếp nhận bệnh nhân" })).not.toBeInTheDocument();
    }
  });

  it.each([
    ["ADMIN", readyAppointment, true],
    ["DOCTOR", readyAppointment, true],
    ["NURSE", readyAppointment, false],
    ["MANAGER", readyAppointment, false],
    ["DOCTOR", { ...readyAppointment, status: "AWAITING_PAYMENT" }, false],
    ["DOCTOR", { ...readyAppointment, careContractVersion: 0 }, false],
    ["DOCTOR", { ...readyAppointment, examClearanceId: null }, false],
  ] as const)("offers start only with a V1 ready appointment and server clearance", (role, appointment, allowed) => {
    renderActions({ role, appointment });
    if (allowed) {
      expect(screen.getByRole("button", { name: "Bắt đầu khám" })).toBeEnabled();
    } else {
      expect(screen.queryByRole("button", { name: "Bắt đầu khám" })).not.toBeInTheDocument();
    }
  });

  it("passes the server-returned check-in state to the parent", async () => {
    const updated = { ...pendingAppointment, status: "AWAITING_PAYMENT", careContractVersion: 1 } satisfies AppointmentDTO;
    mocks.checkIn.mockResolvedValue(updated);
    const { onUpdated } = renderActions();

    fireEvent.click(screen.getByRole("button", { name: "Tiếp nhận bệnh nhân" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));

    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(updated));
    expect(mocks.checkIn).toHaveBeenCalledWith(pendingAppointment.appointmentId);
  });

  it("passes the server-returned exam state to the parent", async () => {
    const updated = { ...readyAppointment, status: "IN_EXAM", examinationStartedAt: "2026-10-20T08:45:00Z" } satisfies AppointmentDTO;
    mocks.startExam.mockResolvedValue(updated);
    const { onUpdated } = renderActions({ appointment: readyAppointment, role: "DOCTOR" });

    fireEvent.click(screen.getByRole("button", { name: "Bắt đầu khám" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));

    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(updated));
    expect(mocks.startExam).toHaveBeenCalledWith(readyAppointment.appointmentId);
  });

  it("sends a single check-in when confirmation is activated twice before the response", () => {
    mocks.checkIn.mockReturnValue(new Promise<AppointmentDTO>(() => undefined));
    renderActions();
    fireEvent.click(screen.getByRole("button", { name: "Tiếp nhận bệnh nhân" }));
    const confirm = screen.getByRole("button", { name: "Xác nhận" });

    act(() => {
      fireEvent.click(confirm);
      fireEvent.click(confirm);
    });

    expect(mocks.checkIn).toHaveBeenCalledTimes(1);
  });

  it("keeps an uncertain check-in locked and offers only an explicit status refresh", async () => {
    const onRefresh = vi.fn();
    mocks.checkIn.mockRejectedValue(new Error("Gateway connection closed"));
    renderActions({ onRefresh });
    fireEvent.click(screen.getByRole("button", { name: "Tiếp nhận bệnh nhân" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Kiểm tra trạng thái hiện tại trước khi gửi lại.");
    expect(screen.getByRole("button", { name: "Xác nhận" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Cập nhật trạng thái" }));

    expect(onRefresh).toHaveBeenCalledTimes(1);
    expect(mocks.checkIn).toHaveBeenCalledTimes(1);
  });

  it("ignores a pending check-in response after the signed session changes", async () => {
    const pending = deferred<AppointmentDTO>();
    const onUpdated = vi.fn();
    mocks.checkIn.mockReturnValue(pending.promise);
    renderActions({ onUpdated });
    fireEvent.click(screen.getByRole("button", { name: "Tiếp nhận bệnh nhân" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));

    await act(async () => {
      mocks.token = "nurse-session-b";
      for (const listener of [...mocks.authListeners]) listener();
    });
    await act(async () => pending.resolve({ ...pendingAppointment, status: "AWAITING_PAYMENT" }));

    expect(onUpdated).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Tiếp nhận bệnh nhân" })).toBeEnabled();
  });

  it("hides outpatient commands when no signed session exists despite a stale route role", () => {
    mocks.token = "";
    renderActions({ role: "NURSE" });

    expect(screen.queryByRole("button", { name: "Tiếp nhận bệnh nhân" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Hủy lịch hẹn" })).not.toBeInTheDocument();
  });

  it("ignores a pending response after the appointment action unmounts", async () => {
    const pending = deferred<AppointmentDTO>();
    const onUpdated = vi.fn();
    mocks.checkIn.mockReturnValue(pending.promise);
    const view = renderActions({ onUpdated });
    fireEvent.click(screen.getByRole("button", { name: "Tiếp nhận bệnh nhân" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));
    view.unmount();

    await act(async () => pending.resolve({ ...pendingAppointment, status: "AWAITING_PAYMENT" }));

    expect(onUpdated).not.toHaveBeenCalled();
    expect(mocks.replace).not.toHaveBeenCalled();
  });
});
