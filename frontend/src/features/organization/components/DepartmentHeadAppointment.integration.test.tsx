import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { departmentContractFixture as department, staffContractFixture as doctor } from "../contract.fixture";
import type { HeadAppointmentSnapshot } from "../types";
import { DepartmentDetail } from "./DepartmentDetail";
import { HeadAppointments } from "./HeadAppointments";
import { ApiRequestError } from "@/lib/api";

const mocks = vi.hoisted(() => ({
  department: vi.fn(), staff: vi.fn(), headAppointments: vi.fn(), replaceHeadAppointment: vi.fn(), rooms: vi.fn(async () => []), roomTypes: vi.fn(async () => []),
  session: vi.fn(() => "session-a"), router: { replace: vi.fn() }, settle: vi.fn(),
}));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => "ADMIN", getSessionKey: mocks.session, subscribeToAuthChanges: () => () => {} }));
vi.mock("@/lib/session", () => ({ getSessionKey: mocks.session }));
vi.mock("@/lib/command-intent", () => ({ prepareCommandIntent: vi.fn(async () => ({ key: "same-key", correlationId: "same-correlation", settle: mocks.settle, cancelUnsent: vi.fn() })) }));
vi.mock("../api", () => ({ organizationApi: mocks }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: ({ value }: { value: string }) => <span>{value}</span> }));

const initial: HeadAppointmentSnapshot = { departmentId: department.departmentId, revision: 0, staffId: null, accountId: null, appointmentId: null, updatedAt: null, updatedByAccountId: null, history: [] };
const appointed: HeadAppointmentSnapshot = { ...initial, revision: 1, staffId: doctor.staffId, accountId: "account", appointmentId: "head", history: [{ appointmentId: "head", staffId: doctor.staffId, accountId: "account", startedAt: "2026-11-01T01:05:00Z", endedAt: null }] };

beforeEach(() => {
  vi.clearAllMocks();
  mocks.session.mockReturnValue("session-a");
  mocks.department.mockResolvedValue(department);
  mocks.staff.mockResolvedValue({ content: [doctor], totalPages: 1 });
  mocks.headAppointments.mockResolvedValue(initial);
  mocks.rooms.mockResolvedValue([]);
  mocks.replaceHeadAppointment.mockResolvedValue(appointed);
});

async function confirmAppointment(name = doctor.fullName) {
  fireEvent.click(await screen.findByRole("radio", { name: `Chọn ${name}` }));
  fireEvent.change(screen.getByLabelText("Lý do"), { target: { value: "Bổ nhiệm theo quyết định" } });
  fireEvent.click(screen.getByRole("button", { name: "Bổ nhiệm" }));
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận bổ nhiệm" }));
}

it("updates the department head immediately on appointment and revocation without resetting revision/history", async () => {
  mocks.replaceHeadAppointment.mockResolvedValueOnce(appointed).mockResolvedValueOnce({ ...initial, revision: 2, history: [{ ...appointed.history[0], endedAt: "2026-11-01T02:00:00Z" }] });
  render(<DepartmentDetail departmentId={department.departmentId} schedulingEnabled />);
  await confirmAppointment();
  expect(await screen.findByText(`${doctor.fullName} · Bác sĩ`)).toBeVisible();
  expect(screen.getByText("Đã bổ nhiệm trưởng khoa.")).toBeVisible();
  expect(screen.getByText("Lịch sử bổ nhiệm (1)")).toBeVisible();
  expect(screen.getByText(/08:05/)).toHaveTextContent("01/11/2026");
  fireEvent.change(screen.getByLabelText("Lý do"), { target: { value: "Kết thúc nhiệm kỳ" } });
  fireEvent.click(screen.getByRole("button", { name: "Miễn nhiệm" }));
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận miễn nhiệm" }));
  expect(await screen.findByText(/^Chưa bổ nhiệm\./)).toBeVisible();
  expect(screen.getByText("Đã miễn nhiệm trưởng khoa.")).toBeVisible();
  expect(screen.getByText("Lịch sử bổ nhiệm (1)")).toBeVisible();
  expect(mocks.headAppointments).toHaveBeenCalledTimes(1);
  expect(mocks.replaceHeadAppointment).toHaveBeenCalledTimes(2);
  expect(mocks.replaceHeadAppointment).toHaveBeenLastCalledWith(department.departmentId, { expectedRevision: 1, staffId: null, reason: "Kết thúc nhiệm kỳ" }, { idempotencyKey: "same-key", correlationId: "same-correlation" });
});

it.each(["department", "session"])("ignores a late appointment completion after a %s change", async change => {
  let complete!: (value: HeadAppointmentSnapshot) => void;
  mocks.replaceHeadAppointment.mockReturnValue(new Promise(resolve => { complete = resolve; }));
  const view = render(<DepartmentDetail departmentId={department.departmentId} schedulingEnabled />);
  await confirmAppointment();
  await waitFor(() => expect(mocks.replaceHeadAppointment).toHaveBeenCalledTimes(1));
  if (change === "department") {
    mocks.department.mockResolvedValue({ ...department, departmentId: "other", departmentName: "Other" });
    mocks.headAppointments.mockResolvedValue({ ...initial, departmentId: "other" });
    view.rerender(<DepartmentDetail departmentId="other" schedulingEnabled />);
    await screen.findByRole("heading", { name: "Other" });
  } else mocks.session.mockReturnValue("session-b");
  await act(async () => { complete(appointed); });
  expect(screen.queryByText(`${doctor.fullName} · Bác sĩ`)).not.toBeInTheDocument();
  expect(screen.getByText(/^Chưa bổ nhiệm\./)).toBeVisible();
});

it("locks duplicate confirmation and keeps an uncertain retry immutable", async () => {
  mocks.replaceHeadAppointment.mockRejectedValueOnce(new Error("Mất kết nối")).mockResolvedValueOnce(appointed);
  render(<DepartmentDetail departmentId={department.departmentId} schedulingEnabled />);
  await confirmAppointment();
  expect(await screen.findByRole("alert")).toHaveTextContent("Mất kết nối");
  expect(screen.getByLabelText("Lý do")).toBeDisabled();
  const retry = screen.getByRole("button", { name: "Gửi lại cùng yêu cầu" });
  fireEvent.click(retry); fireEvent.click(retry);
  expect(await screen.findByText(`${doctor.fullName} · Bác sĩ`)).toBeVisible();
  expect(mocks.replaceHeadAppointment).toHaveBeenCalledTimes(2);
  expect(mocks.replaceHeadAppointment.mock.calls[1]).toEqual(mocks.replaceHeadAppointment.mock.calls[0]);
});

it("does not notify the parent when a save finishes after unmount", async () => {
  let complete!: (value: HeadAppointmentSnapshot) => void;
  mocks.replaceHeadAppointment.mockReturnValue(new Promise(resolve => { complete = resolve; }));
  const onSnapshot = vi.fn();
  const view = render(<HeadAppointments departmentId={department.departmentId} onSnapshot={onSnapshot} />);
  await confirmAppointment();
  await waitFor(() => expect(mocks.replaceHeadAppointment).toHaveBeenCalledTimes(1));
  expect(onSnapshot).toHaveBeenCalledExactlyOnceWith(initial);
  onSnapshot.mockClear();
  view.unmount();
  await act(async () => { complete(appointed); });
  expect(onSnapshot).not.toHaveBeenCalled();
  expect(mocks.settle).toHaveBeenCalledTimes(1);
});

it("shows the persisted appointment projection when the department is reopened", async () => {
  const view = render(<DepartmentDetail departmentId={department.departmentId} schedulingEnabled />);
  await confirmAppointment();
  await screen.findByText(`${doctor.fullName} · Bác sĩ`);
  view.unmount();
  mocks.department.mockResolvedValue({ ...department, departmentHeadId: appointed.staffId });
  mocks.headAppointments.mockResolvedValue(appointed);
  render(<DepartmentDetail departmentId={department.departmentId} schedulingEnabled />);
  expect(await screen.findByText(`${doctor.fullName} · Bác sĩ`)).toBeVisible();
  expect(mocks.replaceHeadAppointment).toHaveBeenCalledTimes(1);
});

it.each(["appointment", "revocation"])("synchronizes an external %s into the department overview after a revision conflict reload", async change => {
  const before = change === "appointment" ? initial : appointed;
  const after = change === "appointment" ? appointed : { ...initial, revision: 2, history: [{ ...appointed.history[0], endedAt: "2026-11-01T02:00:00Z" }] };
  mocks.department.mockResolvedValue({ ...department, departmentHeadId: before.staffId });
  mocks.headAppointments.mockResolvedValueOnce(before).mockResolvedValueOnce(after);
  mocks.replaceHeadAppointment.mockRejectedValueOnce(new ApiRequestError("Changed externally", 409, "HEAD_REVISION_CONFLICT"));
  // The sitting head cannot be re-appointed, so a revocation race starts from another doctor.
  const candidate = { ...doctor, staffId: "00000000-0000-4000-8000-000000000077", fullName: "Bác sĩ Ứng viên" };
  mocks.staff.mockResolvedValue({ content: [doctor, candidate], totalPages: 1 });
  render(<DepartmentDetail departmentId={department.departmentId} schedulingEnabled />);
  await confirmAppointment(change === "appointment" ? doctor.fullName : candidate.fullName);
  await screen.findByText("Bổ nhiệm đã thay đổi.", { exact: false });
  if (change === "appointment") expect(await screen.findByText(`${doctor.fullName} · Bác sĩ`)).toBeVisible();
  else expect(await screen.findByText(/^Chưa bổ nhiệm\./)).toBeVisible();
  expect(screen.getByRole("alert")).toHaveTextContent("Bổ nhiệm đã thay đổi.");
  expect(screen.getByLabelText("Lý do")).toHaveValue("Bổ nhiệm theo quyết định");
  expect(mocks.headAppointments).toHaveBeenCalledTimes(2);
  expect(mocks.replaceHeadAppointment).toHaveBeenCalledTimes(1);
});

it("ignores an older read that completes after a newer authoritative save", async () => {
  let completeRead!: (value: HeadAppointmentSnapshot) => void;
  mocks.headAppointments.mockResolvedValueOnce(initial).mockReturnValueOnce(new Promise(resolve => { completeRead = resolve; }));
  const onSnapshot = vi.fn();
  const view = render(<HeadAppointments departmentId={department.departmentId} onSnapshot={onSnapshot} />);
  await screen.findByRole("radio", { name: `Chọn ${doctor.fullName}` });
  const currentResult = { ...appointed, departmentId: "other" };
  mocks.replaceHeadAppointment.mockResolvedValueOnce(currentResult);
  view.rerender(<HeadAppointments departmentId="other" onSnapshot={onSnapshot} />);
  await confirmAppointment();
  await screen.findByText("Đã bổ nhiệm trưởng khoa.");
  await act(async () => { completeRead({ ...initial, departmentId: "other" }); });
  expect(onSnapshot).toHaveBeenCalledTimes(2);
  expect(onSnapshot).toHaveBeenLastCalledWith(currentResult);
});
