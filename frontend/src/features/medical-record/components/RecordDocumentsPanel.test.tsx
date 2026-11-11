import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { RecordDocumentDTO } from "../types";

const mocks = vi.hoisted(() => ({ documents: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ medicalRecordApi: { documents: mocks.documents } }));

import { RecordDocumentsPanel } from "./RecordDocumentsPanel";

const documents: RecordDocumentDTO[] = [
  { type: "LAB", referenceId: "source-lab-1", summary: "Công thức máu", attachedAt: "2026-11-11T01:15:00Z", afterConclusion: false },
  { type: "PRESCRIPTION", referenceId: "prescription-1", summary: "Đơn thuốc", attachedAt: "2026-11-11T04:30:00Z", afterConclusion: true },
];

describe("record documents", () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it("shows type, time, and source links", async () => {
    mocks.documents.mockResolvedValue(documents);
    render(<RecordDocumentsPanel recordId="record-a" sourceHref={(document) => `/source/${document.referenceId}`} />);

    expect(await screen.findByRole("link", { name: /Công thức máu/ })).toHaveAttribute("href", "/source/source-lab-1");
    expect(screen.getByRole("link", { name: /Đơn thuốc/ })).toHaveAttribute("href", "/source/prescription-1");
    expect(screen.getByText("Xét nghiệm")).toBeVisible();
    expect(screen.getByText("08:15 11/11/2026")).toBeVisible();
  });

  it("labels a document added after conclusion", async () => {
    mocks.documents.mockResolvedValue([documents[1]]);
    render(<RecordDocumentsPanel recordId="record-a" />);

    expect(await screen.findByText("Bổ sung sau khi kết thúc")).toBeVisible();
  });

  it("shows a retryable outage instead of an empty list", async () => {
    mocks.documents.mockRejectedValueOnce(new ApiRequestError("Không thể tải", 503, "UNAVAILABLE")).mockResolvedValueOnce([]);
    render(<RecordDocumentsPanel recordId="record-a" />);

    expect(await screen.findByText(/Không thể tải danh sách tài liệu/)).toBeVisible();
    expect(screen.queryByText("Chưa có tài liệu cho lượt khám này.")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Chưa có tài liệu cho lượt khám này.")).toBeVisible();
  });

  it("shows no permission on 403", async () => {
    mocks.documents.mockRejectedValue(new ApiRequestError("Forbidden", 403, "RECORD_ACCESS_DENIED"));
    render(<RecordDocumentsPanel recordId="record-a" />);

    expect(await screen.findByText("Bạn không có quyền xem tài liệu của hồ sơ này.")).toBeVisible();
    expect(screen.queryByText("Chưa có tài liệu cho lượt khám này.")).not.toBeInTheDocument();
  });
});
