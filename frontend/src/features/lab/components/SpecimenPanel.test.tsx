import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { LabSpecimenDTO } from "../types";

const mocks = vi.hoisted(() => ({
  role: "LAB_TECH" as string | null,
  router: { replace: vi.fn() },
  specimens: vi.fn(), collectSpecimen: vi.fn(), decideSpecimen: vi.fn(),
}));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: () => mocks.role,
  getSessionKey: () => "session-1",
  subscribeToAuthChanges: () => () => undefined,
}));
vi.mock("../review-api", () => ({ labReviewApi: {
  specimens: mocks.specimens,
  collectSpecimen: mocks.collectSpecimen,
  decideSpecimen: mocks.decideSpecimen,
} }));

import { SpecimenPanel } from "./SpecimenPanel";

const sample: LabSpecimenDTO = {
  specimenId: "specimen-1", accession: "ACC-1", patientId: "patient-1", specimenType: "Máu",
  testIds: ["test-1"], replacesSpecimenId: null, collectedBy: "nurse-1", collectedAt: "2026-11-11T08:00:00Z",
  receivedBy: null, receivedAt: null, decision: null, rejectionReason: null,
};

describe("lab specimen panel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.role = "LAB_TECH";
    mocks.specimens.mockResolvedValue([sample]);
    mocks.collectSpecimen.mockResolvedValue(sample);
    mocks.decideSpecimen.mockResolvedValue({ ...sample, decision: "REJECTED", rejectionReason: "Mẫu vỡ" });
  });

  it("blocks a rejection without a reason", async () => {
    render(<SpecimenPanel testId="test-1" />);
    await screen.findByText("ACC-1");
    fireEvent.click(screen.getByRole("button", { name: "Từ chối" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Cần nhập lý do khi từ chối bệnh phẩm.");
    expect(mocks.decideSpecimen).not.toHaveBeenCalled();
  });

  it("submits a rejection reason and reloads the specimen list", async () => {
    render(<SpecimenPanel testId="test-1" />);
    await screen.findByText("ACC-1");
    fireEvent.change(screen.getByLabelText("Lý do từ chối"), { target: { value: "Mẫu vỡ" } });
    fireEvent.click(screen.getByRole("button", { name: "Từ chối" }));
    await waitFor(() => expect(mocks.decideSpecimen).toHaveBeenCalledWith("specimen-1", { decision: "REJECTED", reason: "Mẫu vỡ" }));
    await waitFor(() => expect(mocks.specimens).toHaveBeenCalledTimes(2));
  });

  it("submits collection inputs and clears them after success", async () => {
    mocks.role = "NURSE";
    mocks.specimens.mockResolvedValue([]);
    render(<SpecimenPanel testId="test-1" />);
    await screen.findByText("Chưa có bệnh phẩm được thu nhận.");
    fireEvent.change(screen.getByLabelText(/Loại bệnh phẩm/), { target: { value: "Máu toàn phần" } });
    fireEvent.change(screen.getByLabelText(/Mã xét nghiệm bổ sung/), { target: { value: "test-2, test-3" } });
    fireEvent.click(screen.getByRole("button", { name: "Thu nhận" }));
    await waitFor(() => expect(mocks.collectSpecimen).toHaveBeenCalledWith("test-1", {
      specimenType: "Máu toàn phần", additionalTestIds: ["test-2", "test-3"],
    }));
    await waitFor(() => expect(mocks.specimens).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText(/Loại bệnh phẩm/)).toHaveValue("");
  });

  it("hides the panel when the review workflow is disabled", async () => {
    mocks.specimens.mockRejectedValue(new ApiRequestError("Disabled", 404, "LAB_REVIEW_DISABLED"));
    const { container } = render(<SpecimenPanel testId="test-1" />);
    await waitFor(() => expect(container).toBeEmptyDOMElement());
  });
});
