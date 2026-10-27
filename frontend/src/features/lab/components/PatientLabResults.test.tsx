import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PageResult } from "@/lib/types";
import type { PatientLabResultDTO } from "../types";

const mocks = vi.hoisted(() => ({ mine: vi.fn() }));
vi.mock("../api", () => ({ labApi: { mine: mocks.mine } }));
import { PatientLabResults } from "./PatientLabResults";

const result: PatientLabResultDTO = {
  testId: "test-a", recordId: "record-a", labType: "Công thức máu",
  requestedDate: "2026-10-26", performedDate: "2026-10-26", conclusion: "Trong giới hạn tham chiếu.",
  results: [{ resultId: "result-a", indicator: "WBC", value: "7.0", unit: "10^9/L", referenceRange: "4.0-10.0" }],
};

const resultPage = (content: PatientLabResultDTO[]): PageResult<PatientLabResultDTO> => ({
  content, number: 0, size: 20, totalPages: content.length ? 1 : 0, totalElements: content.length,
});

describe("patient completed Lab results", () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it("shows the completed-result empty state while no result is published", async () => {
    mocks.mine.mockResolvedValue(resultPage([]));

    render(<PatientLabResults />);

    expect(await screen.findByText("Chưa có kết quả xét nghiệm đã hoàn tất.")).toBeVisible();
    expect(screen.queryByText("WBC")).not.toBeInTheDocument();
    expect(mocks.mine).toHaveBeenCalledWith(0);
  });

  it("keeps an unfinished request in a loading state instead of claiming there are no results", async () => {
    let finish!: (page: PageResult<PatientLabResultDTO>) => void;
    mocks.mine.mockReturnValue(new Promise(resolve => { finish = resolve; }));

    render(<PatientLabResults />);

    expect(await screen.findByRole("status")).toHaveTextContent("Đang tải kết quả…");
    expect(screen.queryByText("Chưa có kết quả xét nghiệm đã hoàn tất.")).not.toBeInTheDocument();
    await act(async () => finish(resultPage([result])));
    expect(await screen.findByText("Công thức máu")).toBeVisible();
  });

  it("renders completed Lab values and recovers from an unavailable results source", async () => {
    mocks.mine.mockRejectedValueOnce(new Error("Nguồn kết quả xét nghiệm chưa phản hồi."))
      .mockResolvedValueOnce(resultPage([result]));

    render(<PatientLabResults />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Nguồn kết quả xét nghiệm chưa phản hồi.");
    expect(screen.queryByText("Chưa có kết quả xét nghiệm đã hoàn tất.")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByText("Công thức máu")).toBeVisible();
    expect(screen.getByText("7.0")).toBeVisible();
    expect(screen.getByText(/Kết luận: Trong giới hạn tham chiếu\./)).toBeVisible();
    expect(mocks.mine.mock.calls).toEqual([[0], [0]]);
  });
});
