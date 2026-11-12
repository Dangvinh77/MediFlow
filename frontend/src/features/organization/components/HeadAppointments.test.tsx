import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { organizationApi } from "../api";
import { HeadAppointments } from "./HeadAppointments";
import { staffContractFixture } from "../contract.fixture";
vi.mock("../api", () => ({ organizationApi: { headAppointments: vi.fn(), replaceHeadAppointment: vi.fn(), staff: vi.fn() } }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: () => <span>Bác sĩ A</span> }));
it("previews who will be revoked and requires an audited reason", async () => {
  vi.mocked(organizationApi.headAppointments).mockResolvedValue({ departmentId: "d", revision: 2, staffId: "s", accountId: "a", appointmentId: "h", updatedAt: null, updatedByAccountId: null, history: [] });
  vi.mocked(organizationApi.staff).mockResolvedValue({ content: [
    { ...staffContractFixture, staffId: "s", fullName: "Bác sĩ Trưởng" },
    { ...staffContractFixture, staffId: "t", fullName: "Bác sĩ Mới" },
  ], totalPages: 1, totalElements: 2, number: 0, size: 100 });
  render(<HeadAppointments departmentId="d" />);
  await screen.findByRole("radio", { name: "Chọn Bác sĩ Mới" });
  expect(screen.getByText("Trưởng khoa đương nhiệm", { selector: "span" })).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Miễn nhiệm" }));
  expect(screen.getByText(/Nhiệm kỳ hiện tại sẽ kết thúc/)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Xác nhận miễn nhiệm" })).toBeDisabled();
  expect(organizationApi.replaceHeadAppointment).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Lý do"), { target: { value: "Kết thúc nhiệm kỳ" } });
  fireEvent.click(screen.getByRole("button", { name: "Miễn nhiệm" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "Xác nhận miễn nhiệm" })).toBeEnabled());
});

it("selects one doctor at a time from the radio table before appointing", async () => {
  vi.mocked(organizationApi.headAppointments).mockResolvedValue({ departmentId: "d", revision: 0, staffId: null, accountId: null, appointmentId: null, updatedAt: null, updatedByAccountId: null, history: [] });
  vi.mocked(organizationApi.staff).mockResolvedValue({ content: [
    { ...staffContractFixture, staffId: "a", fullName: "Bác sĩ A" },
    { ...staffContractFixture, staffId: "b", fullName: "Bác sĩ B" },
  ], totalPages: 1, totalElements: 2, number: 0, size: 100 });
  render(<HeadAppointments departmentId="d" />);
  const first = await screen.findByRole("radio", { name: "Chọn Bác sĩ A" });
  const second = screen.getByRole("radio", { name: "Chọn Bác sĩ B" });
  expect(screen.getByRole("button", { name: "Bổ nhiệm" })).toBeDisabled();
  expect(screen.queryByRole("button", { name: "Miễn nhiệm" })).not.toBeInTheDocument();
  fireEvent.click(first);
  fireEvent.click(second);
  expect(first).not.toBeChecked();
  expect(second).toBeChecked();
  fireEvent.change(screen.getByLabelText("Lý do"), { target: { value: "Quyết định bổ nhiệm" } });
  fireEvent.click(screen.getByRole("button", { name: "Bổ nhiệm" }));
  expect(screen.getByRole("button", { name: "Xác nhận bổ nhiệm" })).toBeEnabled();
  expect(organizationApi.staff).toHaveBeenCalledWith(expect.objectContaining({ departmentId: "d", jobTitle: "DOCTOR", activeOnly: true }));
});
