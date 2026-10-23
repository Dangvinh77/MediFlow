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
it.each(["dang van an", "bac si", "khoa noi"])("searches all staff on the server with the original keyword: %s", async keyword => {
  const onChange = vi.fn();
  mocks.staff.mockResolvedValue({ content: [{ ...staffContractFixture, fullName: "Đặng Văn An" }], totalPages: 1 });
  render(<StaffPicker id="owner" label="Nhân viên" value="" onChange={onChange} />);
  fireEvent.change(screen.getByRole("textbox", { name: "Tìm nhân viên" }), { target: { value: keyword } });
  await screen.findByRole("option", { name: "Đặng Văn An · Bác sĩ · Khoa Nội" });
  expect(mocks.staff).toHaveBeenCalledExactlyOnceWith({ departmentId: undefined, jobTitle: undefined, keyword, activeOnly: true, page: 0, size: 20 });
  fireEvent.change(screen.getByRole("combobox", { name: "Nhân viên" }), { target: { value: staffContractFixture.staffId } });
  expect(onChange).toHaveBeenCalledWith(staffContractFixture.staffId);
});
it("loads only the requested result page and retries that page on failure", async () => {
  let fail = true;
  mocks.staff.mockImplementation(async ({ page }) => {
    if (page === 1 && fail) throw new Error("Không tải được nhân viên");
    return { content: [{ ...staffContractFixture, staffId: `staff-${page}`, fullName: `An ${page}` }], totalPages: 2 };
  });
  render(<StaffPicker label="Nhân viên" value="" onChange={vi.fn()} />);
  fireEvent.change(screen.getByRole("textbox"), { target: { value: "an" } });
  await screen.findByRole("option", { name: /An 0/ });
  expect(mocks.staff).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được nhân viên");
  expect(screen.queryByRole("option", { name: /An 0/ })).not.toBeInTheDocument();
  fail = false; fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
  await waitFor(() => expect(screen.getByRole("option", { name: /An 1/ })).toBeInTheDocument());
  expect(mocks.staff).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, keyword: "an", activeOnly: true, size: 20 }));
});