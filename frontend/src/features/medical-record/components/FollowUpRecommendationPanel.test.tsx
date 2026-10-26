import { webcrypto } from "node:crypto";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { medicalRecordApi } from "../api";
import type { FollowUpRecommendationDTO } from "../types";
import { FollowUpRecommendationPanel } from "./FollowUpRecommendationPanel";

const mocks = vi.hoisted(() => ({ get: vi.fn(), create: vi.fn() }));
vi.mock("../api", () => ({ medicalRecordApi: {
  getFollowUpRecommendation: mocks.get,
  createFollowUpRecommendation: mocks.create,
} }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "doctor-session" }));

const recommendation: FollowUpRecommendationDTO = {
  recommendationId: "recommendation-id", sourceRecordId: "record-id", sourceRegistrationId: "registration-id",
  patientId: "patient-id", departmentId: "department-id", preferredDoctorId: "doctor-id",
  recommendedFrom: "2026-11-01", recommendedThrough: "2026-11-30", reason: "Review symptoms after treatment.",
  preparationInstructions: null, createdAt: "2026-10-26T04:00:00Z",
};

describe("V2 follow-up recommendation panel", () => {
  beforeEach(() => {
    vi.clearAllMocks(); vi.stubGlobal("crypto", webcrypto); sessionStorage.clear();
  });

  it("creates a recommendation and retries an uncertain response with the same command key", async () => {
    const user = userEvent.setup();
    mocks.get.mockRejectedValueOnce(new ApiRequestError("No recommendation", 404, "RESOURCE_NOT_FOUND"));
    mocks.create.mockRejectedValueOnce(new TypeError("Connection lost")).mockResolvedValueOnce(recommendation);
    render(<FollowUpRecommendationPanel recordId="record-id" enabled canCreate />);
    expect(await screen.findByText("Chưa có đề xuất tái khám.")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Từ ngày khuyến nghị"), { target: { value: "2026-11-01" } });
    fireEvent.change(screen.getByLabelText("Đến ngày khuyến nghị"), { target: { value: "2026-11-30" } });
    await user.type(screen.getByLabelText("Lý do tái khám"), recommendation.reason);
    await user.click(screen.getByRole("button", { name: "Tạo đề xuất tái khám" }));
    expect(await screen.findByText(/Chưa xác định đã lưu đề xuất/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Gửi lại cùng đề xuất" }));
    expect(await screen.findByText("Review symptoms after treatment.")).toBeInTheDocument();
    expect(mocks.get).toHaveBeenCalledWith("record-id");
    expect(mocks.create).toHaveBeenCalledTimes(2);
    expect(mocks.create.mock.calls[0][0]).toBe("record-id");
    expect(mocks.create.mock.calls[0][1]).toEqual({
      recommendedFrom: "2026-11-01", recommendedThrough: "2026-11-30",
      reason: "Review symptoms after treatment.", preparationInstructions: null,
    });
    expect(mocks.create.mock.calls[0][2]).toEqual(mocks.create.mock.calls[1][2]);
    expect(mocks.create.mock.calls[0][2].idempotencyKey).toBeTruthy();
    expect(mocks.create.mock.calls[0][2].correlationId).toBeTruthy();
  });

  it("shows an existing recommendation as immutable and read-only", async () => {
    mocks.get.mockResolvedValue(recommendation);
    render(<FollowUpRecommendationPanel recordId="record-id" enabled canCreate />);
    expect(await screen.findByText("Review symptoms after treatment.")).toBeInTheDocument();
    expect(screen.getByText("01/11/2026 – 30/11/2026")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tạo đề xuất tái khám" })).not.toBeInTheDocument();
    expect(medicalRecordApi.getFollowUpRecommendation).toHaveBeenCalledWith("record-id");
  });
});
