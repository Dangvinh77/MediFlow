import { fireEvent, render, screen, within } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import type { DoctorWorkShiftDTO } from "../types";
import { DoctorWorkShiftWeekGrid } from "./DoctorWorkShiftWeekGrid";
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: ({ value }: { value: string }) => <span>{value}</span> }));
const active: DoctorWorkShiftDTO = { shiftId: "s1", revision: 2, doctorId: "doctor-a", roomId: "room-a", departmentId: "k", shiftDate: "2026-11-02", startTime: "08:00", endTime: "12:30", status: "ACTIVE" };
it("shows Monday through Sunday and counts only active hours for each doctor", () => {
  render(<DoctorWorkShiftWeekGrid fromDate="2026-11-02" rows={[active, { ...active, shiftId: "cancelled", shiftDate: "2026-11-08", status: "CANCELLED" }]} disabled={false} onEdit={vi.fn()} onSession={vi.fn()} onCancel={vi.fn()} />);
  expect(screen.getByRole("columnheader", { name: "Thứ Hai 02/11/2026" })).toBeInTheDocument();
  expect(screen.getByRole("columnheader", { name: "Chủ nhật 08/11/2026" })).toBeInTheDocument();
  expect(screen.getByText("4,5 giờ")).toBeInTheDocument();
  expect(screen.getByText(/Đã hủy · Phiên bản/)).toBeInTheDocument();
  expect(screen.getAllByRole("button", { name: "Sửa giờ làm việc" })).toHaveLength(1);
});
it("keeps every action bound to the selected shift", () => {
  const onEdit = vi.fn(), onSession = vi.fn(), onCancel = vi.fn();
  const second = { ...active, shiftId: "s2", doctorId: "doctor-b", startTime: "13:00", endTime: "17:00" };
  render(<DoctorWorkShiftWeekGrid fromDate="2026-11-02" rows={[active, second]} disabled={false} onEdit={onEdit} onSession={onSession} onCancel={onCancel} />);
  const row = screen.getByRole("rowheader", { name: "doctor-b" }).closest("tr")!;
  fireEvent.click(within(row).getByRole("button", { name: "Sửa giờ làm việc" }));
  fireEvent.click(within(row).getByRole("button", { name: "Tạo buổi khám từ giờ làm việc" }));
  fireEvent.click(within(row).getByRole("button", { name: "Hủy giờ làm việc" }));
  expect(onEdit).toHaveBeenCalledWith(second); expect(onSession).toHaveBeenCalledWith(second); expect(onCancel).toHaveBeenCalledWith(second);
});
