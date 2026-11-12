import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { LabWorklistItemDTO } from "../types";

const mocks = vi.hoisted(() => ({ router: { replace: vi.fn() }, worklist: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "session-1", subscribeToAuthChanges: () => () => undefined }));
vi.mock("../review-api", () => ({ labReviewApi: { worklist: mocks.worklist } }));

import { LabWorklist } from "./LabWorklist";

const items: LabWorklistItemDTO[] = [
  {
    testId: "test-1", patientId: "patient-1", labType: "CBC", testStatus: "IN_PROGRESS", serviceCode: "CBC",
    serviceName: "Công thức máu", displayGroup: "HEMATOLOGY", specimenStatus: "ACCEPTED", reportStatus: "DRAFT",
    reportVersion: 1, dueAt: "2026-11-10T08:00:00Z", overdue: true,
  },
  {
    testId: "test-2", patientId: "patient-2", labType: "Glucose", testStatus: "READY", serviceCode: "GLU",
    serviceName: "Đường huyết", displayGroup: "BIOCHEMISTRY", specimenStatus: "NOT_COLLECTED", reportStatus: null,
    reportVersion: null, dueAt: null, overdue: false,
  },
];

describe("lab worklist", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.worklist.mockResolvedValue(items); });

  it("filters by display group and highlights overdue rows", async () => {
    render(<LabWorklist />);
    expect(await screen.findByRole("link", { name: "Công thức máu" })).toHaveAttribute("href", "/lab/test-1");
    expect(screen.getByText("Chưa thu mẫu")).toBeInTheDocument();
    expect(screen.getByText("Quá hạn")).toBeVisible();
    expect(screen.getByText("Đang thực hiện")).toBeVisible();
    fireEvent.change(screen.getByLabelText("Nhóm xét nghiệm"), { target: { value: "BIOCHEMISTRY" } });
    expect(screen.queryByText("Công thức máu")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đường huyết" })).toBeVisible();
    expect(screen.queryByText("Quá hạn")).not.toBeInTheDocument();
  });

  it("shows the workflow-disabled state", async () => {
    const { ApiRequestError } = await import("@/lib/api");
    mocks.worklist.mockRejectedValue(new ApiRequestError("Disabled", 404, "LAB_REVIEW_DISABLED"));
    render(<LabWorklist />);
    expect(await screen.findByText("Quy trình duyệt xét nghiệm hiện chưa được bật.")).toBeVisible();
  });
});
