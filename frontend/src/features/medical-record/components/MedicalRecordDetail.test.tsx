import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { DiagnosisDTO, MedicalRecordDTO } from "../types";

const mocks = vi.hoisted(() => ({ getById: vi.fn(), addDiagnosis: vi.fn(), complete: vi.fn(), createLabOrder: vi.fn(), role: "DOCTOR", token: "session-a", listeners: new Set<() => void>(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: () => mocks.role,
  getSessionKey: () => mocks.token,
  subscribeToAuthChanges: (listener: () => void) => { mocks.listeners.add(listener); return () => { mocks.listeners.delete(listener); }; },
}));
vi.mock("../api", () => ({ medicalRecordApi: { getById: mocks.getById, addDiagnosis: mocks.addDiagnosis, complete: mocks.complete, createLabOrder: mocks.createLabOrder } }));
import { MedicalRecordDetail } from "./MedicalRecordDetail";

const record: MedicalRecordDTO = {
  recordId: "record-a", patientId: "patient-a", doctorId: "doctor-a", departmentId: "department-a",
  examinationDate: "2026-10-17", symptoms: "First record", appointmentId: null, diagnoses: [],
  createdAt: "2026-10-17T01:00:00Z", updatedAt: null, status: "OPEN", disposition: null,
  dispositionNote: null, completedAt: null,
};

describe("Medical record detail identity and permissions", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.getById.mockReset(); mocks.addDiagnosis.mockReset(); mocks.complete.mockReset(); mocks.createLabOrder.mockReset(); mocks.role = "DOCTOR"; mocks.token = "session-a"; mocks.listeners.clear(); });

  it("blocks refreshing while a diagnosis is being saved", async () => {
    let resolve!: (value: DiagnosisDTO) => void;
    mocks.getById.mockResolvedValue(record);
    mocks.addDiagnosis.mockReturnValue(new Promise<DiagnosisDTO>(done => { resolve = done; }));
    render(<MedicalRecordDetail recordId="record-a" />);
    await screen.findByText("First record");
    fireEvent.click(screen.getByText("Thêm chẩn đoán", { selector: "summary" }));
    fireEvent.change(screen.getByLabelText(/Tên chẩn đoán/), { target: { value: "Chẩn đoán mới" } });
    fireEvent.click(screen.getByRole("button", { name: "Thêm chẩn đoán" }));
    expect(screen.getByRole("button", { name: "Cập nhật hồ sơ" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Cập nhật hồ sơ" }));
    expect(mocks.getById).toHaveBeenCalledTimes(1);
    await act(async () => resolve({ diagnosisId: "new", diagnosisName: "Chẩn đoán mới", description: null, icdCode: null }));
    expect(screen.getByText("Chẩn đoán mới")).toBeVisible();
    expect(screen.getByRole("button", { name: "Cập nhật hồ sơ" })).toBeEnabled();
  });

  it("keeps identity visible and puts clinical content before technical metadata", async () => {
    mocks.getById.mockResolvedValue(record);
    render(<MedicalRecordDetail recordId="record-a" />);
    const context = await screen.findByLabelText("Ngữ cảnh hồ sơ khám");
    expect(context).toHaveTextContent("17/10/2026");
    expect(context).toHaveTextContent("patient-a");
    const clinical = screen.getByRole("heading", { name: "Nội dung khám" });
    const metadata = screen.getByText("Thông tin đối chiếu và thời điểm");
    expect(clinical.compareDocumentPosition(metadata) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("preserves a diagnosis draft after refreshing and asks before applying a closed record", async () => {
    mocks.getById.mockResolvedValueOnce(record).mockResolvedValueOnce({ ...record, status: "COMPLETED" });
    render(<MedicalRecordDetail recordId="record-a" />);
    await screen.findByText("First record");
    fireEvent.click(screen.getByText("Thêm chẩn đoán", { selector: "summary" }));
    fireEvent.change(screen.getByLabelText(/Tên chẩn đoán/), { target: { value: "Bản nháp" } });
    fireEvent.click(screen.getByRole("button", { name: "Cập nhật hồ sơ" }));
    expect(await screen.findByText(/Hồ sơ đã thay đổi trạng thái/)).toBeVisible();
    expect(screen.getByLabelText(/Tên chẩn đoán/)).toHaveValue("Bản nháp");
    expect(screen.getByRole("button", { name: "Thêm chẩn đoán" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Xem dữ liệu mới" }));
    expect(screen.getByText("Đã hoàn tất")).toBeVisible();
    expect(screen.queryByLabelText(/Tên chẩn đoán/)).not.toBeInTheDocument();
  });

  it("blocks refreshing during completion and keeps the authoritative completed state", async () => {
    const current = { ...record, appointmentId: "appointment-a", diagnoses: [{ diagnosisId: "dx", diagnosisName: "Theo dõi", description: null, icdCode: null }] };
    let resolve!: (value: MedicalRecordDTO) => void;
    mocks.getById.mockResolvedValue(current);
    mocks.complete.mockReturnValue(new Promise<MedicalRecordDTO>(done => { resolve = done; }));
    render(<MedicalRecordDetail recordId="record-a" outpatientEnabled />);
    await screen.findByText("First record");
    fireEvent.click(screen.getByText("Hoàn tất lần khám"));
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: "Hoàn tất hồ sơ khám" }));
    expect(screen.getByRole("button", { name: "Cập nhật hồ sơ" })).toBeDisabled();
    expect(mocks.getById).toHaveBeenCalledTimes(1);
    await act(async () => resolve({ ...current, status: "COMPLETED" }));
    expect(screen.getByText("Đã hoàn tất")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Hoàn tất hồ sơ khám" })).not.toBeInTheDocument();
  });

  it("never attaches an old diagnosis response to another record", async () => {
    let resolve!: (value: DiagnosisDTO) => void;
    mocks.addDiagnosis.mockReturnValue(new Promise<DiagnosisDTO>((done) => { resolve = done; }));
    mocks.getById.mockResolvedValueOnce(record).mockResolvedValueOnce({ ...record, recordId: "record-b", symptoms: "Second record" });
    const view = render(<MedicalRecordDetail recordId="record-a" />);
    fireEvent.change(await screen.findByLabelText("Tên chẩn đoán", { exact: false }), { target: { value: "Old diagnosis" } });
    fireEvent.click(screen.getByRole("button", { name: "Thêm chẩn đoán" }));
    expect(mocks.addDiagnosis).toHaveBeenCalledTimes(1);
    view.rerender(<MedicalRecordDetail recordId="record-b" />);
    await screen.findByText("Second record");
    await act(async () => resolve({ diagnosisId: "diagnosis-a", diagnosisName: "Old diagnosis", description: null, icdCode: null }));
    expect(screen.queryByText("Old diagnosis")).not.toBeInTheDocument();
    expect(screen.getByText("Chưa có chẩn đoán.")).toBeVisible();
  });

  it("clears unsaved diagnosis fields when opening another record", async () => {
    mocks.getById.mockResolvedValueOnce(record).mockResolvedValueOnce({ ...record, recordId: "record-b", symptoms: "Second record" });
    const view = render(<MedicalRecordDetail recordId="record-a" />);
    fireEvent.change(await screen.findByLabelText("Tên chẩn đoán", { exact: false }), { target: { value: "Unsaved diagnosis" } });
    view.rerender(<MedicalRecordDetail recordId="record-b" />);
    await screen.findByText("Second record");
    expect(screen.getByLabelText("Tên chẩn đoán", { exact: false })).toHaveValue("");
  });

  it("clears the previous patient's record while the replacement session reloads it", async () => {
    let resolve!: (value: MedicalRecordDTO) => void;
    mocks.getById.mockResolvedValueOnce(record).mockReturnValueOnce(new Promise<MedicalRecordDTO>((done) => { resolve = done; }));
    render(<MedicalRecordDetail recordId="record-a" />);
    expect(await screen.findByText("First record")).toBeVisible();

    act(() => {
      mocks.token = "session-b";
      for (const listener of mocks.listeners) listener();
    });

    expect(screen.queryByText("First record")).not.toBeInTheDocument();
    expect(screen.getByText("Đang tải chi tiết hồ sơ khám…")).toBeVisible();
    await act(async () => resolve({ ...record, patientId: "patient-b", symptoms: "Second session record" }));
    expect(screen.getByText("Second session record")).toBeVisible();
    expect(mocks.getById).toHaveBeenCalledTimes(2);
  });

  it("ignores an old diagnosis authentication error after navigation", async () => {
    let reject!: (cause: unknown) => void;
    mocks.addDiagnosis.mockReturnValue(new Promise<DiagnosisDTO>((_, fail) => { reject = fail; }));
    mocks.getById.mockResolvedValueOnce(record).mockResolvedValueOnce({ ...record, recordId: "record-b", symptoms: "Second record" });
    const view = render(<MedicalRecordDetail recordId="record-a" />);
    fireEvent.change(await screen.findByLabelText("Tên chẩn đoán", { exact: false }), { target: { value: "Old diagnosis" } });
    fireEvent.click(screen.getByRole("button", { name: "Thêm chẩn đoán" }));
    view.rerender(<MedicalRecordDetail recordId="record-b" />);
    await screen.findByText("Second record");
    await act(async () => reject(new ApiRequestError("Old session", 401, "UNAUTHORIZED")));
    expect(mocks.router.replace).not.toHaveBeenCalled();
    expect(screen.getByText("Second record")).toBeVisible();
  });

  it("sends only one diagnosis when the form receives two synchronous submits", async () => {
    let resolve!: (value: DiagnosisDTO) => void;
    mocks.getById.mockResolvedValue(record);
    mocks.addDiagnosis.mockReturnValue(new Promise<DiagnosisDTO>((done) => { resolve = done; }));
    render(<MedicalRecordDetail recordId="record-a" />);
    fireEvent.change(await screen.findByLabelText("Tên chẩn đoán", { exact: false }), { target: { value: "Acute condition" } });
    const form = screen.getByRole("button", { name: "Thêm chẩn đoán" }).closest("form")!;
    await act(async () => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.addDiagnosis).toHaveBeenCalledTimes(1);
    await act(async () => resolve({ diagnosisId: "diagnosis-a", diagnosisName: "Acute condition", description: null, icdCode: null }));
  });

  it.each(["ADMIN", "DOCTOR", "NURSE"])("shows only permitted actions for %s", async (role) => {
    mocks.role = role;
    mocks.getById.mockResolvedValue(record);
    render(<MedicalRecordDetail recordId="record-a" />);
    await screen.findByText("First record");
    expect(Boolean(screen.queryByRole("button", { name: "Thêm chẩn đoán" }))).toBe(role !== "NURSE");
  });

  it.each(["ADMIN", "DOCTOR"])("shows outpatient order and completion controls for %s only on an appointment-backed open record", async (role) => {
    mocks.role = role;
    mocks.getById.mockResolvedValue({ ...record, appointmentId: "appointment-a" });
    render(<MedicalRecordDetail recordId="record-a" outpatientEnabled />);
    await screen.findByText("First record");

    fireEvent.click(screen.getByText("Tác vụ khác"));
    expect(screen.queryByRole("link", { name: "Kê đơn cho lượt khám" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Lập chỉ định xét nghiệm" })).toHaveAttribute("href", "/records/record-a/lab-order");
    expect(screen.getByRole("link", { name: "Xem phiếu xét nghiệm" })).toHaveAttribute("href", "/lab?episodeId=appointment-a");
    expect(screen.getByLabelText("Ghi chú xử trí")).not.toBeVisible();
    fireEvent.click(screen.getByText("Hoàn tất lần khám"));
    expect(screen.getByLabelText("Ghi chú xử trí")).toBeVisible();
  });

  it("renders the embedded doctor prescription workspace before completion only for an open outpatient record", async () => {
    const appointmentRecord = { ...record, appointmentId: "appointment-a" };
    mocks.getById.mockResolvedValue(appointmentRecord);
    render(<MedicalRecordDetail recordId="record-a" outpatientEnabled prescriptionWorkspace={<div>Embedded prescription workspace</div>} />);
    const workspace = await screen.findByText("Embedded prescription workspace");
    expect(workspace).toBeVisible();
    expect(workspace.compareDocumentPosition(screen.getByText("Hoàn tất lần khám")) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it("gives the embedded doctor workspace no create permission for a COMPLETED record", async () => {
    mocks.getById.mockResolvedValue({ ...record, appointmentId: "appointment-a", status: "COMPLETED" });
    render(<MedicalRecordDetail recordId="record-a" outpatientEnabled prescriptionWorkspace={(_record, canCreate) => <div>Embedded prescription workspace · {canCreate ? "can-create" : "read-only"}</div>} />);
    expect(await screen.findByText(/read-only/)).toBeVisible();
  });

  it("does not render the prescription workspace for a NURSE", async () => {
    mocks.role = "NURSE";
    mocks.getById.mockResolvedValue({ ...record, appointmentId: "appointment-a" });
    render(<MedicalRecordDetail recordId="record-a" outpatientEnabled prescriptionWorkspace={<div>Embedded prescription workspace</div>} />);
    await screen.findByText("First record");
    expect(screen.queryByText("Embedded prescription workspace")).not.toBeInTheDocument();
  });

  it("does not expose outpatient commands to nurses or to a record without an appointment", async () => {
    mocks.role = "NURSE";
    mocks.getById.mockResolvedValue({ ...record, appointmentId: "appointment-a" });
    const nurseView = render(<MedicalRecordDetail recordId="record-a" outpatientEnabled />);
    await screen.findByText("First record");
    expect(screen.queryByRole("link", { name: "Chỉ định xét nghiệm" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Hoàn tất khám" })).not.toBeInTheDocument();
    nurseView.unmount();

    mocks.role = "DOCTOR";
    mocks.getById.mockResolvedValue({ ...record, appointmentId: null });
    render(<MedicalRecordDetail recordId="record-a" outpatientEnabled />);
    await screen.findByText("First record");
    expect(screen.queryByRole("link", { name: "Chỉ định xét nghiệm" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Hoàn tất khám" })).not.toBeInTheDocument();
  });

  it.each([
    ["a disabled outpatient feature", false, "OPEN"],
    ["a completed record", true, "COMPLETED"],
  ] as const)("does not expose outpatient commands for %s", async (_reason, outpatientEnabled, status) => {
    mocks.getById.mockResolvedValue({ ...record, appointmentId: "appointment-a", status });
    render(<MedicalRecordDetail recordId="record-a" outpatientEnabled={outpatientEnabled} />);
    await screen.findByText("First record");

    expect(screen.queryByRole("link", { name: "Chỉ định xét nghiệm" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Hoàn tất khám" })).not.toBeInTheDocument();
  });

  it("keeps completed records immutable even for a doctor", async () => {
    mocks.getById.mockResolvedValue({ ...record, status: "COMPLETED", disposition: "PRESCRIPTION" });
    render(<MedicalRecordDetail recordId="record-a" />);
    await screen.findByText("First record");
    expect(screen.queryByRole("button", { name: "Thêm chẩn đoán" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Sửa triệu chứng" })).not.toBeInTheDocument();
    expect(screen.getByText("Kê đơn thuốc")).toBeVisible();
  });
});
