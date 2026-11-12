import { webcrypto } from "node:crypto";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { MedicalRecordDTO } from "../types";
import { RecordAmendmentsPanel } from "./RecordAmendmentsPanel";

const mocks = vi.hoisted(() => ({
  list: vi.fn(), request: vi.fn(), decide: vi.fn(), assign: vi.fn(),
  sessionKey: "doctor-session" as string | null,
  listeners: [] as Array<() => void>,
  notifyAuthChange: () => { for (const listener of [...mocks.listeners]) listener(); },
  subscribe: (listener: () => void) => {
    mocks.listeners.push(listener);
    return () => { mocks.listeners = mocks.listeners.filter((item) => item !== listener); };
  },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => mocks.sessionKey, subscribeToAuthChanges: mocks.subscribe }));
vi.mock("@/lib/auth", () => ({ getRole: () => "DOCTOR", getToken: () => null, subscribeToAuthChanges: mocks.subscribe }));
vi.mock("../api", () => ({ medicalRecordApi: { amendments: mocks.list, requestAmendment: mocks.request, decideAmendment: mocks.decide, assignAmendment: mocks.assign } }));

const amendment = { amendmentId: "amend-a", section: "SYMPTOMS", originalText: "Đau đầu", amendedText: "Đau đầu nhẹ", reason: "Nhập thiếu", authorStaffId: "doctor-b", assignedByStaffId: null, status: "APPROVED", requestedAt: "2026-11-10T03:00:00Z", decidedByStaffId: "doctor-c", decisionNote: "Đã kiểm tra", decidedAt: "2026-11-10T04:00:00Z", contentHash: "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef" };
const pending = { ...amendment, status: "PENDING", contentHash: null, decidedByStaffId: null, decisionNote: null, decidedAt: null };
const applied = { ...amendment, status: "APPLIED", decidedByStaffId: "doctor-b", decisionNote: "Applied directly by the responsible doctor.", contentHash: amendment.contentHash };
const record: MedicalRecordDTO = { recordId: "record-a", patientId: "patient-a", doctorId: "doctor-a", departmentId: "department-a", examinationDate: "2026-11-10", symptoms: "Đau đầu", appointmentId: null, diagnoses: [], createdAt: "2026-11-10T03:00:00Z", updatedAt: null, status: "COMPLETED", disposition: null, dispositionNote: null, completedAt: "2026-11-10T03:00:00Z" };
const panelProps = (overrides: Partial<{ canAmend: boolean; canApplyAmendment: boolean; canDecideAmendments: boolean; currentStaffId: string | null }> = {}) => ({
  record, recordId: "record-a", departmentId: "department-a", canAmend: false, canApplyAmendment: false, canDecideAmendments: false, currentStaffId: "doctor-a", ...overrides,
});

describe("RecordAmendmentsPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("crypto", webcrypto);
    sessionStorage.clear();
    mocks.sessionKey = "doctor-session";
    mocks.listeners = [];
    mocks.list.mockResolvedValue([]);
    mocks.request.mockResolvedValue(amendment);
    mocks.decide.mockResolvedValue(amendment);
    mocks.assign.mockResolvedValue({ assignmentId: "assign-a", recordId: "record-a", assigneeStaffId: "doctor-c", assignedByStaffId: "doctor-a", reason: "Bác sĩ cũ nghỉ", createdAt: "2026-11-10T02:00:00Z" });
  });

  it("shows loading, empty, forbidden, hidden and a retryable outage", async () => {
    let resolve!: (value: unknown) => void;
    mocks.list.mockReturnValueOnce(new Promise((done) => { resolve = done; }));
    const loading = render(<RecordAmendmentsPanel {...panelProps()} />);
    expect(screen.getByText(/Đang tải đính chính/)).toBeVisible();
    resolve([]);
    expect(await screen.findByText(/Chưa có đính chính/)).toBeVisible();
    loading.unmount();

    mocks.list.mockRejectedValueOnce(new ApiRequestError("Cấm", 403));
    const denied = render(<RecordAmendmentsPanel {...panelProps()} />);
    expect(await screen.findByText(/Bạn không có quyền xem đính chính/)).toBeVisible();
    denied.unmount();

    mocks.list.mockRejectedValueOnce(new ApiRequestError("Tắt", 404, "RESOURCE_NOT_FOUND"));
    const hidden = render(<RecordAmendmentsPanel {...panelProps()} />);
    await waitFor(() => expect(screen.queryByRole("region", { name: "Đính chính hồ sơ" })).not.toBeInTheDocument());
    hidden.unmount();

    mocks.list.mockRejectedValueOnce(new Error("offline"));
    mocks.list.mockResolvedValueOnce([]);
    render(<RecordAmendmentsPanel {...panelProps()} />);
    fireEvent.click(await screen.findByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText(/Chưa có đính chính/)).toBeVisible();
  });

  it("shows the original beside an amendment and the internal confirmation hash", async () => {
    mocks.list.mockResolvedValueOnce([amendment]);
    render(<RecordAmendmentsPanel {...panelProps()} />);
    expect(await screen.findByText("Đau đầu")).toBeVisible();
    expect(screen.getByText("Đau đầu nhẹ")).toBeVisible();
    expect(screen.getByText(/Đã xác nhận nội bộ/)).toBeVisible();
    expect(screen.getByText("1234567890ab")).toBeVisible();
    expect(document.body.textContent).not.toMatch(/chữ ký|ký số|hợp pháp/i);
  });

  it("labels a responsible doctor's direct correction as applied, with the same doctor in the audit", async () => {
    mocks.list.mockResolvedValueOnce([applied]);
    render(<RecordAmendmentsPanel {...panelProps()} />);
    expect(await screen.findByText("Đã áp dụng bởi bác sĩ phụ trách")).toBeVisible();
    expect(screen.getByText("Nội dung đã áp dụng")).toBeVisible();
    expect(screen.getByText(/Đã áp dụng trực tiếp bởi bác sĩ phụ trách/)).toBeVisible();
    expect(document.body.textContent).toContain("doctor-b");
  });

  it("requires replacement text and a reason before requesting an amendment", async () => {
    render(<RecordAmendmentsPanel {...panelProps({ canAmend: true })} />);
    await screen.findByText(/Chưa có đính chính/);
    fireEvent.click(screen.getByRole("button", { name: /Tạo yêu cầu đính chính/ }));
    expect(mocks.request).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(/^Mục cần đính chính/), { target: { value: "SYMPTOMS" } });
    fireEvent.change(screen.getByLabelText(/^Nội dung thay thế/), { target: { value: "Triệu chứng rõ hơn" } });
    fireEvent.change(screen.getByLabelText(/^Lý do đính chính/), { target: { value: "Ghi thiếu từ đầu" } });
    fireEvent.click(screen.getByRole("button", { name: /Tạo yêu cầu đính chính/ }));
    await waitFor(() => expect(mocks.request).toHaveBeenCalledWith(
      "record-a", { section: "SYMPTOMS", originalText: "Đau đầu", amendedText: "Triệu chứng rõ hơn", reason: "Ghi thiếu từ đầu" },
      expect.objectContaining({ idempotencyKey: expect.any(String) }),
    ));
  });

  it("offers direct apply wording to the responsible doctor", async () => {
    mocks.request.mockResolvedValueOnce(applied);
    render(<RecordAmendmentsPanel {...panelProps({ canAmend: true, canApplyAmendment: true })} />);
    await screen.findByText(/Chưa có đính chính/);
    expect(screen.getByRole("heading", { name: "Áp dụng đính chính vào hồ sơ hoàn tất" })).toBeVisible();
    fireEvent.change(screen.getByLabelText(/^Mục cần đính chính/), { target: { value: "SYMPTOMS" } });
    fireEvent.change(screen.getByLabelText(/^Nội dung cần sửa/), { target: { value: "Triệu chứng rõ hơn" } });
    fireEvent.change(screen.getByLabelText(/^Lý do đính chính/), { target: { value: "Ghi thiếu từ đầu" } });
    fireEvent.click(screen.getByRole("button", { name: "Áp dụng đính chính" }));
    await waitFor(() => expect(mocks.request).toHaveBeenCalledWith(
      "record-a", { section: "SYMPTOMS", originalText: "Đau đầu", amendedText: "Triệu chứng rõ hơn", reason: "Ghi thiếu từ đầu" },
      expect.objectContaining({ idempotencyKey: expect.any(String) }),
    ));
  });

  it("hides an author's decision buttons and allows an eligible reviewer", async () => {
    mocks.list.mockResolvedValueOnce([{ ...pending, authorStaffId: "doctor-a" }]);
    const view = render(<RecordAmendmentsPanel {...panelProps({ canDecideAmendments: true, currentStaffId: "doctor-a" })} />);
    await screen.findByText("Đau đầu nhẹ");
    expect(screen.queryByRole("button", { name: /Duyệt đính chính/ })).not.toBeInTheDocument();
    view.rerender(<RecordAmendmentsPanel {...panelProps({ canDecideAmendments: true, currentStaffId: "doctor-c" })} />);
    fireEvent.click(await screen.findByRole("button", { name: /Duyệt đính chính/ }));
    fireEvent.change(screen.getByLabelText(/^Ghi chú quyết định/), { target: { value: "Đã soát lại" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận duyệt" }));
    await waitFor(() => expect(mocks.decide).toHaveBeenCalledWith(
      "record-a", "amend-a", { decision: "APPROVED", note: "Đã soát lại" }, expect.objectContaining({ idempotencyKey: expect.any(String) }),
    ));
  });

  it("closes an open decision form when reviewer permission is revoked", async () => {
    mocks.list.mockResolvedValueOnce([pending]);
    const view = render(<RecordAmendmentsPanel {...panelProps({ canDecideAmendments: true, currentStaffId: "doctor-c" })} />);
    await screen.findByText("Đau đầu nhẹ");
    fireEvent.click(screen.getByRole("button", { name: /Duyệt đính chính/ }));
    expect(screen.getByLabelText(/^Ghi chú quyết định/)).toBeVisible();
    view.rerender(<RecordAmendmentsPanel {...panelProps({ canDecideAmendments: false, currentStaffId: "doctor-c" })} />);
    expect(screen.queryByLabelText(/^Ghi chú quyết định/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xác nhận duyệt" })).not.toBeInTheDocument();
  });
});
