import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { prepareCommandIntent } from "@/lib/command-intent";
import { appointmentApi } from "../api";
import { DoctorWorkShiftsWorkspace } from "./DoctorWorkShiftsWorkspace";

vi.mock("../api", () => ({ appointmentApi: { workShifts: vi.fn(), consultationRooms: vi.fn(), createWorkShift: vi.fn(), updateWorkShift: vi.fn(), cancelWorkShift: vi.fn(), createSessionFromShift: vi.fn() } }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "same-session" }));
vi.mock("@/lib/command-intent", () => ({ CommandIntentError: class extends Error {}, prepareCommandIntent: vi.fn() }));
vi.mock("@/components/references/ReferenceInput", () => ({ ReferenceInput: () => null }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: () => <span>Bác sĩ A</span> }));
const row = {shiftId:"s",revision:2,doctorId:"d",roomId:"r",departmentId:"k",shiftDate:"2026-11-04",startTime:"08:00",endTime:"12:00",status:"ACTIVE" as const};
const page = {content:[row],number:0,totalPages:1,totalElements:1,size:20};
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(prepareCommandIntent).mockResolvedValue({key:"same-key",correlationId:"trace",settle:vi.fn(),cancelUnsent:vi.fn()});
  vi.mocked(appointmentApi.workShifts).mockResolvedValue(page);
  vi.mocked(appointmentApi.consultationRooms).mockResolvedValue({content:[{roomId:"r",roomName:"Phòng A",departmentId:"k",roomType:"CONSULTATION"}],number:0,totalPages:1,totalElements:1,size:50});
  vi.mocked(appointmentApi.createSessionFromShift).mockResolvedValue({...row, sessionId:"session",sessionDate:row.shiftDate,breaks:[],capacity:8,heldCount:0,paymentLockedCount:0,confirmedCount:0,consumedCount:0,availableCount:8,observedAt:"2026-11-01T01:00:00Z",status:"OPEN"});
});

async function openSession() {
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false}/>);
  fireEvent.click(await screen.findByRole("button",{name:"Tạo buổi khám từ giờ làm việc"}));
}

it("moves keyboard focus to the session editor when it opens", async () => {
  await openSession();
  expect(screen.getByRole("heading",{name:"Tạo buổi khám"}).closest("form")).toHaveFocus();
});

it("refreshes the affected day, retains its rows and links to that day's sessions after a successful write", async () => {
  await openSession();
  fireEvent.change(screen.getByLabelText("Sức chứa buổi khám"),{target:{value:"8"}});
  fireEvent.click(screen.getByRole("button",{name:"Xác nhận tạo buổi khám"}));
  const link=await screen.findByRole("link",{name:"Xem buổi khám vừa tạo"});
  expect(link).toHaveAttribute("href","/appointments/department/sessions?date=2026-11-04");
  await waitFor(()=>expect(appointmentApi.workShifts).toHaveBeenLastCalledWith(expect.objectContaining({fromDate:"2026-11-04",toDate:"2026-11-04"})));
  expect(screen.getByLabelText("Từ ngày")).toHaveValue("2026-11-04");
  expect(screen.getByLabelText("Ngày làm việc")).toHaveValue("2026-11-04");
  expect(await screen.findByRole("button",{name:"Sửa giờ làm việc"})).toBeEnabled();
});

it("does not offer to resend an already successful command when refreshing the list fails", async () => {
  await openSession();
  vi.mocked(appointmentApi.workShifts).mockRejectedValueOnce(new Error("read unavailable"));
  fireEvent.click(screen.getByRole("button",{name:"Xác nhận tạo buổi khám"}));
  expect(await screen.findByRole("link",{name:"Xem buổi khám vừa tạo"})).toBeInTheDocument();
  expect(await screen.findByText(/đã được lưu.*chưa thể tải lại/i)).toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"Gửi lại cùng yêu cầu"})).not.toBeInTheDocument();
  expect(appointmentApi.createSessionFromShift).toHaveBeenCalledTimes(1);
});

it("restores a valid affected-day filter when the separate browsing range was cleared before save", async () => {
  await openSession();
  fireEvent.change(screen.getByLabelText("Từ ngày"),{target:{value:""}});
  fireEvent.change(screen.getByLabelText("Đến ngày"),{target:{value:"2026-11-05"}});
  fireEvent.click(screen.getByRole("button",{name:"Xác nhận tạo buổi khám"}));
  await screen.findByRole("link",{name:"Xem buổi khám vừa tạo"});
  await waitFor(()=>expect(appointmentApi.workShifts).toHaveBeenLastCalledWith(expect.objectContaining({fromDate:"2026-11-04",toDate:"2026-11-04"})));
});

it("translates a linked-session conflict and retains the draft for correction", async () => {
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false}/>);
  fireEvent.click(await screen.findByRole("button",{name:"Sửa giờ làm việc"}));
  await screen.findByRole("option",{name:"Phòng A"});
  fireEvent.change(screen.getByLabelText("Giờ kết thúc"),{target:{value:"11:00"}});
  fireEvent.change(screen.getByLabelText("Lý do sửa hoặc hủy giờ làm việc"),{target:{value:"Điều chỉnh"}});
  vi.mocked(appointmentApi.updateWorkShift).mockRejectedValueOnce(new ApiRequestError("Cancel linked consultation sessions before changing the shift",409,"SHIFT_HAS_SESSIONS"));
  fireEvent.click(screen.getByRole("button",{name:"Lưu sửa giờ làm việc"}));
  expect(await screen.findByRole("alert")).toHaveTextContent("Ca đã có buổi khám đang hoạt động");
  expect(screen.getByRole("alert")).toHaveFocus();
  expect(screen.getByLabelText("Giờ kết thúc")).toHaveValue("11:00");
  expect(screen.queryByText(/Cancel linked/)).not.toBeInTheDocument();
});

it("keeps an uncertain write locked and retries only its original body and command identity", async () => {
  await openSession();
  vi.mocked(appointmentApi.createSessionFromShift).mockRejectedValueOnce(new ApiRequestError("Unavailable",503));
  fireEvent.click(screen.getByRole("button",{name:"Xác nhận tạo buổi khám"}));
  const retry=await screen.findByRole("button",{name:"Gửi lại cùng yêu cầu"});
  expect(screen.getByLabelText("Sức chứa buổi khám")).toBeDisabled();
  expect(appointmentApi.workShifts).toHaveBeenCalledTimes(1);
  const original=vi.mocked(appointmentApi.createSessionFromShift).mock.calls[0];
  fireEvent.click(retry);
  await screen.findByRole("link",{name:"Xem buổi khám vừa tạo"});
  expect(vi.mocked(appointmentApi.createSessionFromShift).mock.calls[1]).toEqual(original);
  expect(appointmentApi.createSessionFromShift).toHaveBeenCalledTimes(2);
});

it("explains a definite session overlap without claiming an unknown payment or write outcome", async () => {
  await openSession();
  vi.mocked(appointmentApi.createSessionFromShift).mockRejectedValueOnce(new ApiRequestError("Overlapping consultation session",409,"SESSION_OVERLAP"));
  fireEvent.click(screen.getByRole("button",{name:"Xác nhận tạo buổi khám"}));
  expect(await screen.findByRole("alert")).toHaveTextContent("Buổi khám trùng giờ");
  expect(screen.queryByText(/Chưa thể xác nhận kết quả lưu/)).not.toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"Gửi lại cùng yêu cầu"})).not.toBeInTheDocument();
});

it("refreshes a cancelled shift into its terminal state without creating capacity", async () => {
  render(<DoctorWorkShiftsWorkspace departmentId="k" checking={false}/>);
  fireEvent.click(await screen.findByRole("button",{name:"Hủy giờ làm việc"}));
  fireEvent.change(screen.getByLabelText("Lý do hủy giờ làm việc"),{target:{value:"Điều chỉnh nhân sự demo"}});
  vi.mocked(appointmentApi.cancelWorkShift).mockResolvedValue({...row,status:"CANCELLED",revision:3});
  vi.mocked(appointmentApi.workShifts).mockResolvedValue({...page,content:[{...row,status:"CANCELLED",revision:3}]});
  fireEvent.click(screen.getByRole("button",{name:"Xác nhận hủy giờ làm việc"}));
  expect(await screen.findByText(/Đã hủy · Phiên bản 3/)).toBeInTheDocument();
  expect(screen.queryByRole("button",{name:"Sửa giờ làm việc"})).not.toBeInTheDocument();
  expect(appointmentApi.createSessionFromShift).not.toHaveBeenCalled();
});
