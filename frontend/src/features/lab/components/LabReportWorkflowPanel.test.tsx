import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { LabReportDTO, LabTestDTO } from "../types";

const mocks = vi.hoisted(() => ({
  getReport: vi.fn(),
  getTemplate: vi.fn(),
  saveReportDraft: vi.fn(),
  submitReport: vi.fn(),
  returnReport: vi.fn(),
  releaseReport: vi.fn(),
  amendReport: vi.fn(),
  getRole: vi.fn(() => "LAB_TECH"),
  router: { replace: vi.fn() },
  staffId: "staff-1",
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: mocks.getRole,
  subscribeToAuthChanges: () => () => undefined,
}));
vi.mock("@/lib/capabilities", () => ({
  useCapabilities: () => ({ data: { staffId: mocks.staffId }, loading: false, error: null }),
  refreshCapabilities: vi.fn(),
}));
vi.mock("../api", () => ({ labApi: {
  getReport: mocks.getReport,
  getTemplate: mocks.getTemplate,
  saveReportDraft: mocks.saveReportDraft,
  submitReport: mocks.submitReport,
  returnReport: mocks.returnReport,
  releaseReport: mocks.releaseReport,
  amendReport: mocks.amendReport,
} }));

import { LabReportWorkflowPanel } from "./LabReportWorkflowPanel";

const test = { testId: "lab-test", status: "IN_PROGRESS", requestedDate: "2026-11-01" } as Pick<LabTestDTO, "testId" | "status" | "requestedDate">;
const template = {
  testId: test.testId,
  serviceCode: "CBC",
  template: {
    templateCode: "CBC_V1", version: 1, name: "Công thức máu",
    fields: [{ fieldCode: "WBC", label: "Bạch cầu", position: 1, required: true,
      valueType: "QUANTITATIVE" as const, unit: "10^9/L", allowedOptions: null,
      allowsOperator: true, ranges: [] }],
  },
};

function report(overrides: Partial<LabReportDTO> = {}): LabReportDTO {
  return {
    reportId: "report-1", testId: test.testId, resultVersion: 1, status: "DRAFT",
    draftRevision: 2, templateCode: "CBC_V1", templateVersion: 1,
    values: [{ fieldCode: "WBC", operator: null, value: "5", noResultReason: null, flag: "NORMAL" }],
    conclusion: null, performedDate: "2026-11-11", amendmentReason: null,
    enteredBy: "staff-2", submittedBy: null, submittedAt: null, returnedBy: null,
    returnReason: null, releasedBy: null, releasedAt: null,
    ...overrides,
  };
}

describe("Lab report review workflow", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getRole.mockReturnValue("LAB_TECH");
    mocks.staffId = "staff-1";
    mocks.getTemplate.mockResolvedValue(template);
    mocks.getReport.mockResolvedValue(report());
    mocks.saveReportDraft.mockResolvedValue(report());
    mocks.submitReport.mockResolvedValue(report({ status: "PENDING_REVIEW" }));
    mocks.returnReport.mockResolvedValue(report());
    mocks.releaseReport.mockResolvedValue(report({ status: "RELEASED" }));
    mocks.amendReport.mockResolvedValue(report({ draftRevision: 0 }));
  });

  it("loads report fields and displays an undetermined flag separately from normal", async () => {
    mocks.getReport.mockResolvedValue(report({ values: [
      { fieldCode: "WBC", operator: null, value: "—", noResultReason: "Mẫu không đạt", flag: "UNDETERMINED" },
    ] }));
    render(<LabReportWorkflowPanel test={test} />);

    expect(await screen.findByRole("heading", { name: "Báo cáo xét nghiệm" })).toBeVisible();
    expect(screen.getByText("Chưa xác định")).toBeVisible();
    expect(screen.queryByText("Bình thường")).not.toBeInTheDocument();
  });

  it("shows the stale revision message and reloads the latest report", async () => {
    mocks.getReport.mockResolvedValueOnce(report()).mockResolvedValueOnce(report({ draftRevision: 3, values: [
      { fieldCode: "WBC", operator: null, value: "6", noResultReason: null, flag: "NORMAL" },
    ] }));
    mocks.saveReportDraft.mockRejectedValue(new ApiRequestError("Stale", 409, "LAB_REPORT_STALE"));
    render(<LabReportWorkflowPanel test={test} />);

    fireEvent.change(await screen.findByLabelText("Giá trị Bạch cầu"), { target: { value: "7" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu nháp" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Nhân viên khác đã lưu báo cáo này");
    fireEvent.click(screen.getByRole("button", { name: "Tải lại bản mới nhất" }));

    await waitFor(() => expect(screen.getByLabelText("Giá trị Bạch cầu")).toHaveValue(6));
    expect(mocks.getReport).toHaveBeenCalledTimes(2);
    expect(mocks.saveReportDraft).toHaveBeenCalledWith(test.testId, expect.objectContaining({ draftRevision: 2 }));
  });

  it.each([
    ["enteredBy", { enteredBy: "staff-1", submittedBy: "staff-2" }],
    ["submittedBy", { enteredBy: "staff-2", submittedBy: "staff-1" }],
  ] as const)("hides release when current staff is %s", async (_field, values) => {
    mocks.getReport.mockResolvedValue(report({ status: "PENDING_REVIEW", ...values }));
    render(<LabReportWorkflowPanel test={test} />);

    await screen.findByRole("heading", { name: "Báo cáo xét nghiệm" });
    expect(screen.queryByRole("button", { name: "Phát hành" })).not.toBeInTheDocument();
  });

  it.each([
    ["LAB_SELF_RELEASE", "Không thể tự phát hành báo cáo do chính bạn nhập hoặc gửi duyệt."],
    ["LAB_RELEASE_DENIED", "Tài khoản chưa được cấp quyền phát hành kết quả xét nghiệm."],
  ])("shows %s as an inline release message", async (code, message) => {
    mocks.getReport.mockResolvedValue(report({ status: "PENDING_REVIEW", enteredBy: "staff-2", submittedBy: "staff-3" }));
    mocks.releaseReport.mockRejectedValue(new ApiRequestError("Denied", 403, code));
    render(<LabReportWorkflowPanel test={test} />);

    fireEvent.click(await screen.findByRole("button", { name: "Phát hành" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
  });

  it("blocks return without a reason", async () => {
    mocks.getReport.mockResolvedValue(report({ status: "PENDING_REVIEW" }));
    render(<LabReportWorkflowPanel test={test} />);

    fireEvent.click(await screen.findByRole("button", { name: "Trả lại" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận trả lại" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Vui lòng nhập lý do trả lại.");
    expect(mocks.returnReport).not.toHaveBeenCalled();
  });

  it("blocks an amendment without a reason", async () => {
    mocks.getReport.mockResolvedValue(report({ status: "RELEASED" }));
    render(<LabReportWorkflowPanel test={test} />);

    fireEvent.click(await screen.findByRole("button", { name: "Đính chính" }));
    fireEvent.click(screen.getByRole("button", { name: "Tạo bản đính chính" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Vui lòng nhập lý do đính chính.");
    expect(mocks.amendReport).not.toHaveBeenCalled();
  });

  it("renders a server permission error without leaving the page", async () => {
    mocks.getReport.mockResolvedValue(report({ status: "PENDING_REVIEW", enteredBy: "staff-2", submittedBy: "staff-3" }));
    mocks.releaseReport.mockRejectedValue(new ApiRequestError("Denied", 403, "LAB_RELEASE_DENIED"));
    render(<LabReportWorkflowPanel test={test} />);

    fireEvent.click(await screen.findByRole("button", { name: "Phát hành" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Tài khoản chưa được cấp quyền");
    expect(screen.getByRole("heading", { name: "Báo cáo xét nghiệm" })).toBeVisible();
  });
});
