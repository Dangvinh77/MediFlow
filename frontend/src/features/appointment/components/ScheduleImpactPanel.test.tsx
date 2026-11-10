import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { ScheduleImpactPanel } from "./ScheduleImpactPanel";

const mocks = vi.hoisted(() => ({ sessionImpact: vi.fn(), shiftImpact: vi.fn() }));
vi.mock("../api", () => ({ appointmentApi: mocks }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: ({ fallback }: { fallback?: string }) => <span>{fallback}</span> }));

beforeEach(() => Object.values(mocks).forEach(mock => mock.mockReset()));

const empty = { sessionIds: ["s"], heldCount: 0, paymentLockedCount: 0, confirmedCount: 0, consumedCount: 0, registrations: [] };

it("says nobody is affected when the session has no registrations", async () => {
  mocks.sessionImpact.mockResolvedValue(empty);
  render(<ScheduleImpactPanel kind="session" id="s" />);
  expect(await screen.findByText("Không có phiếu nào bị ảnh hưởng.")).toBeVisible();
});

it("does not read a failed lookup as no impact and can retry", async () => {
  mocks.shiftImpact.mockRejectedValueOnce(new ApiRequestError("Không tải được", 503)).mockResolvedValueOnce(empty);
  render(<ScheduleImpactPanel kind="shift" id="w" />);
  expect(await screen.findByText("Không tải được")).toBeVisible();
  expect(screen.queryByText("Không có phiếu nào bị ảnh hưởng.")).not.toBeInTheDocument();
  await userEvent.setup().click(screen.getByRole("button", { name: /Thử lại/ }));
  await waitFor(() => expect(mocks.shiftImpact).toHaveBeenCalledTimes(2));
  expect(await screen.findByText("Không có phiếu nào bị ảnh hưởng.")).toBeVisible();
});

it("lists counts, arrival and both queue numbers of each affected registration", async () => {
  mocks.sessionImpact.mockResolvedValue({ ...empty, heldCount: 1, confirmedCount: 2, registrations: [
    { registrationId: "r1", patientId: "p1", sessionId: "s", status: "CONFIRMED", reservationStatus: "CONFIRMED", checkedInAt: "2026-11-10T01:00:00Z", queueNumber: 4, roomQueueNumber: 2 },
    { registrationId: "r2", patientId: "p2", sessionId: "s", status: "AWAITING_PAYMENT", reservationStatus: "HELD", checkedInAt: null, queueNumber: null, roomQueueNumber: null },
  ] });
  render(<ScheduleImpactPanel kind="session" id="s" />);
  expect(await screen.findByText(/Số quầy 4/)).toHaveTextContent("Số phòng 2");
  expect(screen.getByText(/Chưa có số/)).toBeVisible();
  expect(screen.getByText("Đã đến · Chờ khám")).toBeVisible();
  expect(screen.getByText("Chờ thanh toán")).toBeVisible();
});
