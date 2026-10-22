import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { departmentContractFixture, staffContractFixture } from "../contract.fixture";
import { StaffPicker } from "./StaffPicker";

const mocks = vi.hoisted(() => ({ staff: vi.fn(), departments: vi.fn() }));
vi.mock("../api", () => ({ organizationApi: mocks }));

beforeEach(() => {
  mocks.staff.mockReset();
  mocks.departments.mockReset().mockResolvedValue([{ ...departmentContractFixture, departmentName: "Khoa Nội" }]);
});

it("finds an active employee beyond the first API page using unaccented text and retains the staff UUID", async () => {
  const onChange = vi.fn();
  mocks.staff.mockImplementation(async ({ page, size }) => ({
    content: size === 100 && page === 1
      ? [{ ...staffContractFixture, fullName: "Đặng Văn An", jobTitle: "DOCTOR", specialization: null }]
      : [],
    totalPages: size === 100 ? 2 : 1,
  }));
  render(<StaffPicker id="owner" label="Nhân viên" value="" onChange={onChange} />);
  fireEvent.change(screen.getByRole("textbox", { name: "Tìm nhân viên" }), { target: { value: "dang van an" } });
  await screen.findByRole("option", { name: "Đặng Văn An · Bác sĩ · Khoa Nội" });
  expect(mocks.staff).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 100 }));
  fireEvent.change(screen.getByRole("combobox", { name: "Nhân viên" }), { target: { value: staffContractFixture.staffId } });
  expect(onChange).toHaveBeenCalledWith(staffContractFixture.staffId);
});

it.each(["bac si", "khoa noi"])("searches the displayed job title or department: %s", async keyword => {
  mocks.staff.mockResolvedValue({ content: [
    { ...staffContractFixture, fullName: "Nguyễn An", jobTitle: "DOCTOR", active: true },
    { ...staffContractFixture, staffId: "inactive", fullName: "Đã nghỉ", jobTitle: "DOCTOR", active: false },
  ], totalPages: 1 });
  render(<StaffPicker label="Nhân viên" value="" onChange={vi.fn()} />);
  fireEvent.change(screen.getByRole("textbox"), { target: { value: keyword } });
  await screen.findByRole("option", { name: "Nguyễn An · Bác sĩ · Khoa Nội" });
  expect(screen.queryByRole("option", { name: /Đã nghỉ/ })).not.toBeInTheDocument();
});

it("reports a later-page failure and retries without presenting incomplete search results", async () => {
  let fail = true;
  mocks.staff.mockImplementation(async ({ page }) => {
    if (page === 1 && fail) throw new Error("Không tải được nhân viên");
    return { content: [{ ...staffContractFixture, staffId: `staff-${page}`, fullName: `An ${page}` }], totalPages: 2 };
  });
  render(<StaffPicker label="Nhân viên" value="" onChange={vi.fn()} />);
  fireEvent.change(screen.getByRole("textbox"), { target: { value: "an" } });
  expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được nhân viên");
  expect(screen.queryByRole("option", { name: /An 0/ })).not.toBeInTheDocument();
  fail = false;
  fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
  await waitFor(() => expect(screen.getByRole("option", { name: /An 1/ })).toBeInTheDocument());
});
