import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { appointmentApi } from "../api";
import { DoctorWorkShiftsWorkspace } from "./DoctorWorkShiftsWorkspace";
vi.mock("../api", () => ({ appointmentApi: { workShifts: vi.fn(), consultationRooms: vi.fn(), createSessionFromShift: vi.fn() } }));
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
