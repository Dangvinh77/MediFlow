import { webcrypto } from "node:crypto";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { RescheduleDialog } from "./RescheduleDialog";

const mocks = vi.hoisted(() => ({ registration: vi.fn(), list: vi.fn(), reschedule: vi.fn() }));
vi.mock("../api", () => ({ appointmentApi: { registration: mocks.registration, listConsultationSessions: mocks.list, reschedule: mocks.reschedule } }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "nurse-session" }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: ({ fallback }: { fallback?: string }) => <span>{fallback}</span> }));

const current = { registrationId: "registration-id", revision: 4, departmentId: "department-id", sessionId: "old-session", status: "CONFIRMED", checkedInAt: null };
const target = {
  sessionId: "new-session", revision: 2, doctorId: "doctor-id", departmentId: "department-id", roomId: "room-id",
  sessionDate: "2026-11-20", startTime: "09:00", endTime: "11:00", breaks: [], capacity: 10,
  status: "OPEN" as const, heldCount: 0, paymentLockedCount: 0, confirmedCount: 0, consumedCount: 0, availableCount: 5, observedAt: "2026-11-10T01:00:00Z",
};
const full = { ...target, sessionId: "full-session", startTime: "13:00", endTime: "15:00", availableCount: 0 };

describe("reschedule dialog", () => {
  beforeEach(() => {
    Object.values(mocks).forEach(mock => mock.mockReset()); vi.stubGlobal("crypto", webcrypto); sessionStorage.clear();
    mocks.registration.mockResolvedValue(current);
    mocks.list.mockResolvedValue({ content: [{ ...target, sessionId: "old-session", startTime: "07:00" }, target, full], number: 0, totalPages: 1, totalElements: 3, size: 100 });
  });

  async function chooseTarget(onRescheduled = vi.fn()) {
    const user = userEvent.setup();
    render(<RescheduleDialog registrationId="registration-id" onClose={vi.fn()} onRescheduled={onRescheduled} />);
    await waitFor(() => expect(mocks.registration).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText("Ngày khám mới"), { target: { value: "2026-11-20" } });
    await waitFor(() => expect(screen.getByRole("button", { name: "Tìm buổi khám" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "Tìm buổi khám" }));
    await user.click(await screen.findByRole("radio", { name: /09:00/ }));
    return user;
  }

  it("reschedule requires reason and patient confirmation", async () => {
    const user = await chooseTarget();
    expect(screen.queryByRole("radio", { name: /07:00/ })).not.toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /13:00/ })).toBeDisabled();
    const submit = screen.getByRole("button", { name: "Xác nhận đổi lịch" });
    expect(submit).toBeDisabled();
    await user.type(screen.getByLabelText("Lý do đổi lịch"), "Bác sĩ nghỉ đột xuất");
    expect(submit).toBeDisabled();
    await user.click(screen.getByRole("checkbox", { name: /Bệnh nhân đã đồng ý/ }));
    expect(submit).toBeEnabled();
    mocks.reschedule.mockResolvedValue({ ...current, revision: 5 });
    await user.click(submit);
    await waitFor(() => expect(mocks.reschedule).toHaveBeenCalledTimes(1));
    expect(mocks.reschedule.mock.calls[0][0]).toBe("registration-id");
    expect(mocks.reschedule.mock.calls[0][1]).toEqual({ expectedRevision: 4, targetSessionId: "new-session", reason: "Bác sĩ nghỉ đột xuất", patientConfirmed: true });
  });

  it("reschedule retries with the same key", async () => {
    const onRescheduled = vi.fn();
    const user = await chooseTarget(onRescheduled);
    await user.type(screen.getByLabelText("Lý do đổi lịch"), "Bác sĩ nghỉ");
    await user.click(screen.getByRole("checkbox", { name: /Bệnh nhân đã đồng ý/ }));
    mocks.reschedule.mockRejectedValueOnce(new ApiRequestError("Lỗi máy chủ", 500)).mockResolvedValueOnce({ ...current, revision: 5 });
    await user.click(screen.getByRole("button", { name: "Xác nhận đổi lịch" }));
    const retry = await screen.findByRole("button", { name: "Gửi lại cùng yêu cầu" });
    expect(screen.getByLabelText("Lý do đổi lịch")).toBeDisabled();
    await user.click(retry);
    await waitFor(() => expect(onRescheduled).toHaveBeenCalledWith(expect.objectContaining({ revision: 5 })));
    const [first, second] = mocks.reschedule.mock.calls;
    expect(second[1]).toEqual(first[1]);
    expect(second[2].idempotencyKey).toBe(first[2].idempotencyKey);
  });

  it("explains a payment in progress and reloads the registration", async () => {
    const user = await chooseTarget();
    await user.type(screen.getByLabelText("Lý do đổi lịch"), "Bác sĩ nghỉ");
    await user.click(screen.getByRole("checkbox", { name: /Bệnh nhân đã đồng ý/ }));
    mocks.reschedule.mockRejectedValueOnce(new ApiRequestError("conflict", 409, "REGISTRATION_PAYMENT_IN_PROGRESS"));
    await user.click(screen.getByRole("button", { name: "Xác nhận đổi lịch" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("đang được thanh toán");
    await waitFor(() => expect(mocks.registration).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole("button", { name: "Gửi lại cùng yêu cầu" })).not.toBeInTheDocument();
  });
});
