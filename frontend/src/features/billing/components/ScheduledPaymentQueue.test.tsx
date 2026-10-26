import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { it, expect, vi } from "vitest";
import { ScheduledPaymentQueue } from "./ScheduledPaymentQueue";
import { ReferenceProvider } from "@/components/references/ReferenceProvider";
const queue = vi.hoisted(() => vi.fn());
vi.mock("../api", () => ({ billingApi: { scheduledPaymentQueue: queue } }));
it("offers a named patient and selects the exact server registration without displaying its UUID", async () => {
  queue.mockResolvedValue({ content: [{ registrationId: "registration-id", patientId: "patient-id", departmentId: "department-id", createdAt: "2026-10-26T04:00:00Z", expiresAt: "2026-10-26T04:15:00Z" }], number: 0, totalPages: 1 });
  const select = vi.fn();
  render(<ReferenceProvider directory={{ canSearch: () => true, resolve: async kind => kind === "patient" ? "Nguyễn An · 01/01/1990" : "Khoa Nội", search: vi.fn() }}><ScheduledPaymentQueue disabled={false} onSelect={select} /></ReferenceProvider>);
  await userEvent.click(screen.getByRole("button", { name: "Tải khoản chờ thu" }));
  expect(await screen.findByText("Nguyễn An · 01/01/1990")).toBeVisible();
  expect(await screen.findByText("Khoa Nội")).toBeVisible();
  expect(screen.queryByText("registration-id")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Chọn khoản thu" }));
  expect(select).toHaveBeenCalledWith("registration-id");
});
it("locks selection while a payment is pending or uncertain", () => {
  render(<ScheduledPaymentQueue disabled onSelect={vi.fn()} />);
  expect(screen.getByRole("button", { name: "Tải khoản chờ thu" })).toBeDisabled();
});
