import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { LabTestDTO, LabTestTemplateDTO } from "../types";

const mocks = vi.hoisted(() => ({ getTemplate: vi.fn(), addResults: vi.fn(), getRole: vi.fn(() => "LAB_TECH"), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: mocks.getRole, subscribeToAuthChanges: () => () => {} }));
vi.mock("../api", () => ({
  labApi: { getTemplate: mocks.getTemplate, addResults: mocks.addResults, start: vi.fn(), cancel: vi.fn() },
}));

import { LabActions } from "./LabActions";

const test: LabTestDTO = {
  testId: "10000000-0000-4000-8000-000000000001", recordId: "10000000-0000-4000-8000-000000000002",
  patientId: "10000000-0000-4000-8000-000000000003", requestingDepartmentId: "10000000-0000-4000-8000-000000000004",
  labType: "Công thức máu", requestedDate: "2026-10-20", performedDate: null, status: "IN_PROGRESS", conclusion: null,
  paid: false, results: null, createdAt: "2026-10-20T00:00:00Z", updatedAt: "2026-10-20T00:00:00Z",
  careContractVersion: 0, sourceOrderId: null, careEpisodeType: null, careEpisodeId: null, priceCode: null,
  clearanceId: null, emergencyOverrideId: null, resultVersion: 0,
};

const templateResponse = (fields: LabTestTemplateDTO["template"]) => ({ testId: test.testId, serviceCode: "CBC", template: fields });
const template = [
  {
    fieldCode: "WBC", label: "Bạch cầu", position: 1, required: true, valueType: "QUANTITATIVE" as const,
    unit: "10^9/L", allowedOptions: null, allowsOperator: true,
    ranges: [{ sex: null, ageMinDays: null, ageMaxDays: null, low: 4, high: 10, criticalLow: null, criticalHigh: null, reviewed: false }],
  },
  {
    fieldCode: "KETONE", label: "Thể ceton", position: 2, required: true, valueType: "QUALITATIVE" as const,
    unit: null, allowedOptions: ["Âm tính", "Dương tính"], allowsOperator: false, ranges: [],
  },
] satisfies NonNullable<LabTestTemplateDTO["template"]>["fields"];

function renderInProgress() {
  return render(<LabActions test={test} onUpdated={vi.fn()} />);
}

async function openStructuredResults() {
  fireEvent.click(screen.getByRole("button", { name: "Nhập kết quả" }));
  await screen.findByLabelText("Bạch cầu toán tử");
}

describe("LabActions structured result entry", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getRole.mockReturnValue("LAB_TECH");
    mocks.getTemplate.mockResolvedValue(templateResponse({ templateCode: "CBC-V1", version: 1, name: "Công thức máu", fields: template }));
    mocks.addResults.mockResolvedValue(test);
  });

  it("renders an operator only when the template field allows it and shows demo ranges", async () => {
    renderInProgress();
    await openStructuredResults();
    expect(screen.getByLabelText("Bạch cầu toán tử")).toBeVisible();
    expect(screen.queryByLabelText("Thể ceton toán tử")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Đơn vị Bạch cầu")).toHaveValue("10^9/L");
    expect(screen.getByLabelText("Khoảng tham chiếu Bạch cầu")).toHaveValue("4–10");
    expect(screen.getByText("Khoảng tham chiếu demo, chưa được duyệt lâm sàng")).toBeVisible();
  });

  it("uses the template options and submits a reason in place of a qualitative value", async () => {
    renderInProgress();
    await openStructuredResults();
    fireEvent.change(screen.getByLabelText("Bạch cầu toán tử"), { target: { value: "<=" } });
    fireEvent.change(screen.getByLabelText("Giá trị Bạch cầu"), { target: { value: "4" } });
    fireEvent.change(screen.getByLabelText("Lý do không có kết quả Thể ceton"), { target: { value: "Mẫu không đủ" } });
    await act(async () => fireEvent.click(screen.getByRole("button", { name: "Lưu kết quả" })));
    await waitFor(() => expect(mocks.addResults).toHaveBeenCalledWith(test.testId, expect.objectContaining({
      results: [
        { indicator: "Bạch cầu", value: "<=4", unit: "10^9/L", referenceRange: "4–10" },
        { indicator: "Thể ceton", value: "Không có kết quả: Mẫu không đủ", unit: null, referenceRange: null },
      ],
    })));
  });

  it("renders a qualitative select from allowed options", async () => {
    renderInProgress();
    await openStructuredResults();
    expect(screen.getByLabelText("Giá trị Thể ceton")).toHaveRole("combobox");
    expect(screen.getByRole("option", { name: "Âm tính" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Dương tính" })).toBeInTheDocument();
  });

  it("blocks submission when a required template field has neither a value nor a reason", async () => {
    renderInProgress();
    await openStructuredResults();
    fireEvent.change(screen.getByLabelText("Giá trị Thể ceton"), { target: { value: "Âm tính" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu kết quả" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Vui lòng nhập kết quả hoặc lý do không có kết quả cho Bạch cầu.");
    expect(mocks.addResults).not.toHaveBeenCalled();
  });

  it("blocks an empty set of optional template fields before submitting results", async () => {
    mocks.getTemplate.mockResolvedValue(templateResponse({
      templateCode: "OPTIONAL-V1", version: 1, name: "Mẫu tùy chọn",
      fields: [{ ...template[0], required: false }],
    }));
    renderInProgress();
    await openStructuredResults();
    fireEvent.click(screen.getByRole("button", { name: "Lưu kết quả" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Nhập ít nhất một kết quả hoặc lý do không có kết quả.");
    expect(mocks.addResults).not.toHaveBeenCalled();
  });

  it("keeps the free-row form when the test has no pinned template", async () => {
    mocks.getTemplate.mockResolvedValue(templateResponse(null));
    renderInProgress();
    fireEvent.click(screen.getByRole("button", { name: "Nhập kết quả" }));
    expect(await screen.findByLabelText(/Chỉ số/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Thêm chỉ số" })).toBeVisible();
  });

  it("keeps the free-row form when template lookup returns 404", async () => {
    mocks.getTemplate.mockRejectedValue(new ApiRequestError("Not found", 404, "LAB_TEMPLATE_NOT_FOUND"));
    renderInProgress();
    fireEvent.click(screen.getByRole("button", { name: "Nhập kết quả" }));
    expect(await screen.findByLabelText(/Chỉ số/)).toBeVisible();
  });

  it("lets a lab technician leave template loading failure and return to the available actions", async () => {
    mocks.getTemplate.mockRejectedValue(new ApiRequestError("Unavailable", 503, "LAB_UNAVAILABLE"));
    renderInProgress();
    fireEvent.click(screen.getByRole("button", { name: "Nhập kết quả" }));
    await screen.findByRole("alert");
    fireEvent.click(screen.getByRole("button", { name: "Quay lại" }));
    expect(screen.getByRole("button", { name: "Nhập kết quả" })).toBeVisible();
    expect(mocks.addResults).not.toHaveBeenCalled();
  });
});
