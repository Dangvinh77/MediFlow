import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReferenceProvider, type ReferenceDirectory } from "@/components/references/ReferenceProvider";
import type { PageResult } from "@/lib/types";
import type { FollowUpRecommendationDTO, PatientExamResultDTO } from "../types";

const mocks = vi.hoisted(() => ({ mine: vi.fn(), getFollowUpRecommendation: vi.fn() }));
vi.mock("../api", () => ({ medicalRecordApi: { mine: mocks.mine, getFollowUpRecommendation: mocks.getFollowUpRecommendation } }));
import { PatientExamResults } from "./PatientExamResults";

const record: PatientExamResultDTO = {
  recordId: "record-a", doctorId: "doctor-a", departmentId: "department-a",
  examinationDate: "2026-10-26", symptoms: "Đau đầu", diagnoses: [
    { diagnosisId: "diagnosis-a", diagnosisName: "Đau đầu căng cơ", description: null, icdCode: null },
  ], disposition: "OUTPATIENT_FOLLOW_UP", dispositionNote: "Tái khám nếu triệu chứng kéo dài.",
  completedAt: "2026-10-26T09:30:00Z",
};

const recommendation: FollowUpRecommendationDTO = {
  recommendationId: "recommendation-a", sourceRecordId: "record-a", sourceRegistrationId: "registration-a",
  patientId: "patient-a", departmentId: "department-a", preferredDoctorId: "doctor-a",
  recommendedFrom: "2026-11-01", recommendedThrough: "2026-11-30", reason: "Đánh giá lại triệu chứng.",
  preparationInstructions: null, createdAt: "2026-10-26T09:30:00Z",
};

const resultPage = (content: PatientExamResultDTO[]): PageResult<PatientExamResultDTO> => ({
  content, number: 0, size: 20, totalPages: content.length ? 1 : 0, totalElements: content.length,
});

describe("patient completed exam results", () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it("shows only the completed-result empty state when there are no published results", async () => {
    mocks.mine.mockResolvedValue(resultPage([]));

    render(<PatientExamResults schedulingEnabled={false} />);

    expect(await screen.findByText("Chưa có kết quả khám đã hoàn tất.")).toBeVisible();
    expect(screen.queryByText("Đau đầu căng cơ")).not.toBeInTheDocument();
    expect(mocks.mine).toHaveBeenCalledWith(0);
  });

  it("keeps an unfinished request in a loading state instead of claiming there are no results", async () => {
    let finish!: (page: PageResult<PatientExamResultDTO>) => void;
    mocks.mine.mockReturnValue(new Promise(resolve => { finish = resolve; }));

    render(<PatientExamResults schedulingEnabled={false} />);

    expect(await screen.findByRole("status")).toHaveTextContent("Đang tải kết quả…");
    expect(screen.queryByText("Chưa có kết quả khám đã hoàn tất.")).not.toBeInTheDocument();
    await act(async () => finish(resultPage([record])));
    expect(await screen.findByText("Đau đầu căng cơ")).toBeVisible();
  });

  it("keeps a Gateway failure distinct from an empty result and retries the same page", async () => {
    mocks.mine.mockRejectedValueOnce(new Error("Dịch vụ kết quả đang tạm thời gián đoạn."))
      .mockResolvedValueOnce(resultPage([record]));

    render(<PatientExamResults schedulingEnabled={false} />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Dịch vụ kết quả đang tạm thời gián đoạn.");
    expect(screen.queryByText("Chưa có kết quả khám đã hoàn tất.")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByText("Đau đầu căng cơ")).toBeVisible();
    expect(screen.getByText(/Kết luận và hướng dẫn: Tái khám nếu triệu chứng kéo dài\./)).toBeVisible();
    expect(mocks.mine.mock.calls).toEqual([[0], [0]]);
  });

  it("shows the doctor's name and patient guidance while keeping source UUIDs out of the primary view", async () => {
    mocks.mine.mockResolvedValue(resultPage([record]));
    mocks.getFollowUpRecommendation.mockResolvedValue(recommendation);
    const directory: ReferenceDirectory = {
      canResolve: kind => kind === "doctor",
      canSearch: () => false,
      search: async () => ({ items: [], hasMore: false }),
      resolve: async (kind, id) => kind === "doctor" && id === recommendation.preferredDoctorId ? "Bác sĩ Nguyễn An" : null,
    };

    render(<ReferenceProvider directory={directory}>
      <PatientExamResults schedulingEnabled />
    </ReferenceProvider>);

    expect(await screen.findByText("Bác sĩ Nguyễn An")).toBeVisible();
    expect(screen.getByText("Liên hệ điều dưỡng để chọn buổi khám phù hợp.")).toBeVisible();
    expect(screen.queryByText(recommendation.preferredDoctorId)).not.toBeVisible();
    expect(screen.queryByText(recommendation.sourceRegistrationId)).not.toBeVisible();
    const sourceInfo = screen.getByText("Thông tin đối chiếu");
    expect(sourceInfo.closest("details")).not.toHaveAttribute("open");
    fireEvent.click(sourceInfo);
    expect(screen.getByText(recommendation.sourceRegistrationId)).toBeVisible();
    expect(screen.queryByRole("button", { name: "Tạo đề xuất tái khám" })).not.toBeInTheDocument();
  });
});
