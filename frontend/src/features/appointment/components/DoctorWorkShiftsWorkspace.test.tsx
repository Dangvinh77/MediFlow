import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { getSessionKey } from "@/lib/session";
import { prepareCommandIntent } from "@/lib/command-intent";
import { appointmentApi } from "../api";
import { DoctorWorkShiftsWorkspace } from "./DoctorWorkShiftsWorkspace";
vi.mock("../api", () => ({ appointmentApi: { workShifts: vi.fn(), consultationRooms: vi.fn(), createSessionFromShift: vi.fn(), cancelWorkShift: vi.fn(), shiftImpact: vi.fn() } }));
vi.mock("@/lib/session", () => ({ getSessionKey: vi.fn(() => "session") }));
vi.mock("@/lib/command-intent", () => ({ CommandIntentError: class extends Error {}, prepareCommandIntent: vi.fn() }));
vi.mock("@/components/references/ReferenceInput", () => ({ ReferenceInput: () => null }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: () => <span>Bác sĩ A</span> }));
it("requires an explicit action to turn assigned working hours into consultation capacity", async () => {
  vi.mocked(appointmentApi.workShifts).mockResolvedValue({ content: [{ shiftId: "s", revision: 1, doctorId: "d", roomId: "r", departmentId: "k", shiftDate: "2026-11-01", startTime: "08:00", endTime: "12:00", status: "ACTIVE" }], number: 0, totalPages: 1, totalElements: 1, size: 20 });
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false} />);
  fireEvent.click(screen.getByRole("button", { name: "Tải giờ làm việc" }));
  fireEvent.click(await screen.findByRole("button", { name: "Tạo buổi khám từ giờ làm việc" }));
  expect(screen.getByLabelText("Sức chứa buổi khám")).toBeInTheDocument();
  expect(appointmentApi.createSessionFromShift).not.toHaveBeenCalled();
});

const shift = { shiftId: "s1", revision: 1, doctorId: "d", roomId: "r", departmentId: "k", shiftDate: "2026-11-02", startTime: "08:00", endTime: "12:00", status: "ACTIVE" as const };
const result = (content: typeof shift[], number = 0, totalPages = 1) => ({ content, number, totalPages, totalElements: content.length, size: 100 });
beforeEach(() => {
  vi.mocked(appointmentApi.workShifts).mockReset(); vi.mocked(getSessionKey).mockReturnValue("session");
  vi.mocked(prepareCommandIntent).mockResolvedValue({ key: "key", correlationId: "trace", settle: vi.fn(), cancelUnsent: vi.fn() });
});
async function selectWeek() {
  await waitFor(() => expect(screen.getByRole("button", { name: "Tải giờ làm việc" })).toBeEnabled());
  await waitFor(() => expect(appointmentApi.workShifts).toHaveBeenCalled());
  await waitFor(() => expect(screen.getByRole("button", { name: "Theo tuần" })).toBeEnabled());
  fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-11-04" } });
  fireEvent.click(screen.getByRole("button", { name: "Theo tuần" }));
  fireEvent.click(screen.getByRole("button", { name: "Tải giờ làm việc" }));
}
it("loads every week page before showing full active totals", async () => {
  let finish!: (value: ReturnType<typeof result>) => void;
  vi.mocked(appointmentApi.workShifts).mockResolvedValueOnce(result([])).mockResolvedValueOnce(result([shift], 0, 2)).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false} />);
  await selectWeek();
  await waitFor(() => expect(appointmentApi.workShifts).toHaveBeenLastCalledWith({ departmentId: "k", fromDate: "2026-11-02", toDate: "2026-11-08", page: 1, size: 100 }));
  expect(screen.queryByText("4 giờ")).not.toBeInTheDocument();
  await act(async () => finish(result([{ ...shift, shiftId: "s2", shiftDate: "2026-11-03" }], 1, 2)));
  expect(await screen.findByText("8 giờ")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Trang sau" })).not.toBeInTheDocument();
});
it("shows an empty week without implying an all-staff roster", async () => {
  vi.mocked(appointmentApi.workShifts).mockResolvedValue(result([]));
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false} />);
  await selectWeek();
  expect(await screen.findByText("Không có giờ làm việc trong khoảng đã chọn.")).toBeInTheDocument();
  expect(screen.getByText(/Bác sĩ có ca trong tuần đã chọn/)).toBeInTheDocument();
});
it("discards an unfinished week after its dates change", async () => {
  let finish!: (value: ReturnType<typeof result>) => void;
  vi.mocked(appointmentApi.workShifts).mockResolvedValueOnce(result([])).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; })).mockResolvedValue(result([]));
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false} />);
  await selectWeek();
  await waitFor(() => expect(appointmentApi.workShifts).toHaveBeenCalledTimes(2));
  // Read filters remain usable while loading, so an obsolete week can be abandoned.
  fireEvent.change(screen.getByLabelText("Từ ngày"), { target: { value: "2026-11-11" } });
  await act(async () => finish(result([shift], 0, 2)));
  expect(screen.queryByRole("region", { name: "Lưới phân ca theo tuần" })).not.toBeInTheDocument();
  expect(appointmentApi.workShifts).toHaveBeenCalledTimes(2);
  fireEvent.click(screen.getByRole("button", { name: "Tải giờ làm việc" }));
  await waitFor(() => expect(appointmentApi.workShifts).toHaveBeenLastCalledWith({ departmentId: "k", fromDate: "2026-11-09", toDate: "2026-11-15", page: 0, size: 100 }));
});

it("does not display partial totals when a later week page fails", async () => {
  vi.mocked(appointmentApi.workShifts).mockResolvedValueOnce(result([])).mockResolvedValueOnce(result([shift], 0, 2)).mockRejectedValueOnce(new Error("page unavailable"));
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false} />);
  await selectWeek();
  expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải giờ làm việc");
  expect(screen.queryByRole("region", { name: "Lưới phân ca theo tuần" })).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Tải giờ làm việc" })).toBeEnabled();
});
it("stops reading an obsolete session between pages", async () => {
  let finish!: (value: ReturnType<typeof result>) => void;
  vi.mocked(appointmentApi.workShifts).mockResolvedValueOnce(result([])).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false} />);
  await selectWeek();
  await waitFor(() => expect(appointmentApi.workShifts).toHaveBeenCalledTimes(2));
  vi.mocked(getSessionKey).mockReturnValue("different-session");
  await act(async () => finish(result([shift], 0, 2)));
  expect(appointmentApi.workShifts).toHaveBeenCalledTimes(2);
  expect(screen.queryByRole("region", { name: "Lưới phân ca theo tuần" })).not.toBeInTheDocument();
});
it("cancels the selected weekly shift and refreshes every page without creating a session", async () => {
  const second = { ...shift, shiftId: "s2", revision: 3, shiftDate: "2026-11-03" };
  vi.mocked(appointmentApi.workShifts).mockResolvedValueOnce(result([])).mockResolvedValueOnce(result([shift, second]));
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false} />);
  await selectWeek();
  await screen.findByText("8 giờ");
  fireEvent.click(screen.getAllByRole("button", { name: "Hủy giờ làm việc" })[1]);
  expect(screen.getByText(/Hủy phân công 03\/11\/2026/)).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Lý do hủy giờ làm việc"), { target: { value: "Đổi phân công" } });
  vi.mocked(appointmentApi.cancelWorkShift).mockResolvedValue({ ...second, status: "CANCELLED" });
  vi.mocked(appointmentApi.workShifts).mockResolvedValueOnce(result([shift], 0, 2)).mockResolvedValueOnce({ ...result([], 1, 2), content: [{ ...second, status: "CANCELLED" }] });
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy giờ làm việc" }));
  expect(await screen.findByText("4 giờ")).toBeInTheDocument();
  expect(appointmentApi.cancelWorkShift).toHaveBeenCalledWith("s2", { expectedRevision: 3, reason: "Đổi phân công" }, { idempotencyKey: "key", correlationId: "trace" });
  expect(appointmentApi.workShifts).toHaveBeenLastCalledWith({ departmentId: "k", fromDate: "2026-11-02", toDate: "2026-11-08", page: 1, size: 100 });
  expect(appointmentApi.createSessionFromShift).not.toHaveBeenCalled();
});

it("normalizes Sunday date selection and previous/next week controls to full weeks", async () => {
  vi.mocked(appointmentApi.workShifts).mockResolvedValue(result([]));
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false} />);
  await selectWeek();
  await screen.findByRole("region", { name: "Lưới phân ca theo tuần" });
  fireEvent.change(screen.getByLabelText("Đến ngày"), { target: { value: "2026-11-15" } });
  expect(screen.getByLabelText("Từ ngày")).toHaveValue("2026-11-09");
  expect(screen.getByLabelText("Đến ngày")).toHaveValue("2026-11-15");
  fireEvent.click(screen.getByRole("button", { name: "Tuần trước" }));
  expect(screen.getByLabelText("Từ ngày")).toHaveValue("2026-11-02");
  expect(screen.getByLabelText("Đến ngày")).toHaveValue("2026-11-08");
  fireEvent.click(screen.getByRole("button", { name: "Tuần sau" }));
  expect(screen.getByLabelText("Từ ngày")).toHaveValue("2026-11-09");
  expect(screen.getByLabelText("Đến ngày")).toHaveValue("2026-11-15");
});
it("shift cancel shows affected registrations before confirming", async () => {
  vi.mocked(appointmentApi.cancelWorkShift).mockClear();
  vi.mocked(appointmentApi.workShifts).mockResolvedValue(result([shift]));
  vi.mocked(appointmentApi.shiftImpact).mockResolvedValue({ sessionIds: ["x"], heldCount: 0, paymentLockedCount: 0, confirmedCount: 1, consumedCount: 0, registrations: [
    { registrationId: "r1", patientId: "p1", sessionId: "x", status: "CONFIRMED", reservationStatus: "CONFIRMED", checkedInAt: null, queueNumber: 4, roomQueueNumber: null },
  ] });
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false} clinicIntakeEnabled />);
  const cancel = await screen.findByRole("button", { name: "Hủy giờ làm việc" });
  await waitFor(() => expect(cancel).toBeEnabled());
  fireEvent.click(cancel);
  expect(await screen.findByRole("heading", { name: "Bệnh nhân bị ảnh hưởng" })).toBeInTheDocument();
  expect(appointmentApi.shiftImpact).toHaveBeenCalledWith("s1");
  expect(await screen.findByText(/Số quầy 4/)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Xác nhận hủy giờ làm việc" })).toBeInTheDocument();
  expect(appointmentApi.cancelWorkShift).not.toHaveBeenCalled();
});
