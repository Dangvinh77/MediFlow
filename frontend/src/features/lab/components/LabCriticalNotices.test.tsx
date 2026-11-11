import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { LabCriticalNoticeDTO } from "../types";

const mocks = vi.hoisted(() => ({
  role: "DOCTOR" as string | null,
  router: { replace: vi.fn() },
  criticalNotices: vi.fn(), acknowledgeCriticalNotice: vi.fn(),
}));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: () => mocks.role,
  getSessionKey: () => "session-1",
  subscribeToAuthChanges: () => () => undefined,
}));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "session-1" }));
vi.mock("@/components/references/ReferenceText", () => ({ ReferenceText: ({ fallback }: { fallback: string }) => <span>{fallback}</span> }));
vi.mock("../review-api", () => ({ labReviewApi: {
  criticalNotices: mocks.criticalNotices,
  acknowledgeCriticalNotice: mocks.acknowledgeCriticalNotice,
} }));

import { LabCriticalNotices } from "./LabCriticalNotices";

const notice: LabCriticalNoticeDTO = {
  noticeId: "notice-1", testId: "test-1", resultVersion: 2, fieldCode: "WBC", label: "Bạch cầu",
  value: "1.2", unit: "10^9/L", flag: "CRITICAL_LOW", patientId: "patient-1", departmentId: "dept-1",
  status: "OPEN", acknowledgedBy: null, acknowledgedAt: null, createdAt: "2026-11-11T08:00:00Z",
};

describe("lab critical notices", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.role = "DOCTOR";
    mocks.criticalNotices.mockResolvedValue([notice]);
    mocks.acknowledgeCriticalNotice.mockResolvedValue({ ...notice, status: "ACKNOWLEDGED" });
  });

  it("lets a doctor acknowledge an open notice", async () => {
    mocks.criticalNotices.mockResolvedValueOnce([notice]).mockResolvedValueOnce([]);
    render(<LabCriticalNotices />);
    expect(await screen.findByText("Bạch cầu")).toBeVisible();
    expect(screen.getByText("Thấp nguy cấp")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận đã nhận" }));
    await waitFor(() => expect(mocks.acknowledgeCriticalNotice).toHaveBeenCalledWith("test-1", "notice-1"));
    expect(await screen.findByText("Không có cảnh báo kết quả nguy cấp đang chờ xác nhận.")).toBeVisible();
  });

  it("keeps the notice read-only for lab technicians", async () => {
    mocks.role = "LAB_TECH";
    render(<LabCriticalNotices />);
    expect(await screen.findByText("Bạch cầu")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Xác nhận đã nhận" })).not.toBeInTheDocument();
  });
});
