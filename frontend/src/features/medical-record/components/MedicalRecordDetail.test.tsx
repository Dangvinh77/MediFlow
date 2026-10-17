import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { DiagnosisDTO, MedicalRecordDTO } from "../types";

const mocks = vi.hoisted(() => ({ getById: vi.fn(), addDiagnosis: vi.fn(), role: "DOCTOR", router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
vi.mock("../api", () => ({ medicalRecordApi: { getById: mocks.getById, addDiagnosis: mocks.addDiagnosis } }));
import { MedicalRecordDetail } from "./MedicalRecordDetail";

const record: MedicalRecordDTO = {
  recordId: "record-a", patientId: "patient-a", doctorId: "doctor-a", departmentId: "department-a",
  examinationDate: "2026-10-17", symptoms: "First record", appointmentId: null, diagnoses: [],
  createdAt: "2026-10-17T01:00:00Z", updatedAt: null, status: "OPEN", disposition: null,
  dispositionNote: null, completedAt: null,
};

describe("Medical record detail identity and permissions", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.getById.mockReset(); mocks.addDiagnosis.mockReset(); mocks.role = "DOCTOR"; });

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

  it("keeps completed records immutable even for a doctor", async () => {
    mocks.getById.mockResolvedValue({ ...record, status: "COMPLETED", disposition: "PRESCRIPTION" });
    render(<MedicalRecordDetail recordId="record-a" />);
    await screen.findByText("First record");
    expect(screen.queryByRole("button", { name: "Thêm chẩn đoán" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Sửa triệu chứng" })).not.toBeInTheDocument();
    expect(screen.getByText("Kê đơn thuốc")).toBeVisible();
  });
});
