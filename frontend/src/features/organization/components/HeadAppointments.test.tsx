import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { organizationApi } from "../api";
import { HeadAppointments } from "./HeadAppointments";
vi.mock("../api", () => ({ organizationApi: { headAppointments: vi.fn(), replaceHeadAppointment: vi.fn() } }));
vi.mock("./StaffPicker", () => ({ StaffPicker: () => null }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: () => <span>Bác sĩ A</span> }));
it("previews who will be revoked and requires an audited reason", async () => {
  vi.mocked(organizationApi.headAppointments).mockResolvedValue({ departmentId: "d", revision: 2, staffId: "s", accountId: "a", appointmentId: "h", updatedAt: null, updatedByAccountId: null, history: [] });
  render(<HeadAppointments departmentId="d" />);
  await screen.findByText("Trưởng khoa đương nhiệm");
  fireEvent.click(screen.getByRole("button", { name: "Miễn nhiệm" }));
  expect(screen.getByText(/Nhiệm kỳ hiện tại sẽ kết thúc/)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Xác nhận miễn nhiệm" })).toBeDisabled();
  expect(organizationApi.replaceHeadAppointment).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Lý do"), { target: { value: "Kết thúc nhiệm kỳ" } });
  fireEvent.click(screen.getByRole("button", { name: "Miễn nhiệm" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "Xác nhận miễn nhiệm" })).toBeEnabled());
});
