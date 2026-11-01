import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { SessionAdministration } from "./ScheduleManagementWorkspace";
import { appointmentApi } from "../api";
vi.mock("../api", () => ({ appointmentApi: { listConsultationSessions: vi.fn(), consultationRooms: vi.fn() } }));
vi.mock("@/features/organization/api", () => ({ organizationApi: {} }));
vi.mock("@/components/references/ReferenceInput", () => ({ ReferenceInput: () => null }));
it("opens today's department sessions in the fixed appointed department without scope controls", async () => {
 vi.mocked(appointmentApi.listConsultationSessions).mockResolvedValue({content:[],number:0,totalPages:0,totalElements:0,size:20});
 render(<SessionAdministration fixedDepartmentId="appointed" checking={false} />);
 await waitFor(() => expect(appointmentApi.listConsultationSessions).toHaveBeenCalledWith(expect.objectContaining({departmentId:"appointed"})));
 expect(screen.queryByLabelText("Khoa buổi khám")).not.toBeInTheDocument();
 expect(screen.queryByText("Quản trị quyền lập lịch")).not.toBeInTheDocument();
});

it("discards the old filter response when the department date changes", async () => {
 let resolve!: (value: Awaited<ReturnType<typeof appointmentApi.listConsultationSessions>>) => void;
 vi.mocked(appointmentApi.listConsultationSessions).mockReturnValueOnce(new Promise(done => { resolve = done; }));
 render(<SessionAdministration fixedDepartmentId="appointed" checking={false} />);
 await waitFor(() => expect(resolve).toBeDefined());
 fireEvent.change(screen.getByLabelText("Từ ngày"), {target:{value:"2026-11-02"}});
 await act(async () => { resolve({content:[{sessionId:"old",revision:1,doctorId:"d",departmentId:"appointed",roomId:"r",capacity:20,heldCount:0,paymentLockedCount:0,confirmedCount:0,consumedCount:0,availableCount:20,observedAt:"now",sessionDate:"1999-01-01",startTime:"08:00",endTime:"12:00",breaks:[],status:"OPEN"}],number:0,totalPages:1,totalElements:1,size:20}); });
 expect(screen.queryByText(/1999/)).not.toBeInTheDocument();
});
