import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { MyWorkShiftsWorkspace } from "./MyWorkShiftsWorkspace";
import { appointmentApi } from "../api";
vi.mock("../api", () => ({ appointmentApi: { myWorkShifts: vi.fn() } }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: () => <span>Phòng khám</span> }));
it("loads own shifts using the signed identity and exposes no management command", async () => {
 vi.mocked(appointmentApi.myWorkShifts).mockResolvedValue([{shiftId:"s",revision:1,doctorId:"d",departmentId:"k",roomId:"r",shiftDate:"2026-11-01",startTime:"08:00",endTime:"12:00",status:"ACTIVE"}]);
 render(<MyWorkShiftsWorkspace enabled />);
 expect(await screen.findByText("2026-11-01 · 08:00–12:00")).toBeInTheDocument();
 expect(appointmentApi.myWorkShifts).toHaveBeenCalledWith(expect.any(String),expect.any(String));
 expect(screen.queryByRole("button",{name:/Tạo|Hủy|Sửa/})).not.toBeInTheDocument();
});
