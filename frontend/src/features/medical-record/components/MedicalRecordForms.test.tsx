import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { webcrypto } from "node:crypto";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { MedicalRecordDTO } from "../types";

const mocks = vi.hoisted(() => ({ create: vi.fn(), getById: vi.fn(), update: vi.fn(), session: "doctor-session", sessionListeners: new Set<() => void>(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => mocks.session, subscribeToAuthChanges: (listener: () => void) => { mocks.sessionListeners.add(listener); return () => { mocks.sessionListeners.delete(listener); }; } }));
vi.mock("../api", () => ({ medicalRecordApi: { create: mocks.create, getById: mocks.getById, update: mocks.update } }));
import { CreateMedicalRecordForm } from "./CreateMedicalRecordForm";
import { EditMedicalRecordForm } from "./EditMedicalRecordForm";

const record: MedicalRecordDTO = {
  recordId: "record-a", patientId: "123e4567-e89b-42d3-a456-426614174000",
  doctorId: "123e4567-e89b-42d3-a456-426614174001", departmentId: "123e4567-e89b-42d3-a456-426614174002",
  examinationDate: "2026-10-16", symptoms: "First record", appointmentId: null,
  diagnoses: [], createdAt: "2026-10-17T01:00:00Z", updatedAt: null,
  status: "OPEN", disposition: null, dispositionNote: null, completedAt: null,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function fillCreateRecord() {
  fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: record.patientId } });
  fireEvent.change(screen.getByLabelText("Mã khoa", { exact: false }), { target: { value: record.departmentId } });
  fireEvent.change(screen.getByLabelText("Mã bác sĩ", { exact: false }), { target: { value: record.doctorId } });
  fireEvent.change(screen.getByLabelText("Ngày khám", { exact: false }), { target: { value: record.examinationDate } });
  fireEvent.change(screen.getByLabelText("Tên chẩn đoán", { exact: false }), { target: { value: "Acute condition" } });
}

describe("Medical record forms", () => {
  beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal("crypto", webcrypto); sessionStorage.clear(); mocks.create.mockReset(); mocks.getById.mockReset(); mocks.update.mockReset(); mocks.session = "doctor-session"; mocks.sessionListeners.clear(); });

  it("sends only one record creation when submit fires twice before the response", async () => {
    const pending = deferred<MedicalRecordDTO>();
    mocks.create.mockReturnValue(pending.promise);
    render(<CreateMedicalRecordForm />);
    fillCreateRecord();
    const form = screen.getByRole("button", { name: "Tạo hồ sơ khám" }).closest("form")!;
    await act(async () => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });

  it("ignores a late authentication failure after the record creation form unmounts", async () => {
    const pending = deferred<MedicalRecordDTO>();
    mocks.create.mockReturnValue(pending.promise);
    const view = render(<CreateMedicalRecordForm />);
    fillCreateRecord();
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ khám" }));
    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));
    view.unmount();
    await act(async () => pending.reject(new ApiRequestError("Phiên hết hạn", 401, "UNAUTHORIZED")));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  it("does not navigate from an old record edit after the route identity changes", async () => {
    const pending = deferred<MedicalRecordDTO>();
    mocks.getById.mockResolvedValueOnce(record).mockResolvedValueOnce({
      ...record, recordId: "record-b", patientId: "patient-b", symptoms: "Second record",
    });
    mocks.update.mockReturnValue(pending.promise);
    const view = render(<EditMedicalRecordForm recordId="record-a" />);
    expect(await screen.findByText("First record")).toBeVisible();
    fireEvent.change(screen.getByLabelText("Triệu chứng"), { target: { value: "Second symptoms" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    await waitFor(() => expect(mocks.update).toHaveBeenCalledTimes(1));
    view.rerender(<EditMedicalRecordForm recordId="record-b" />);
    expect(await screen.findByText("Second record")).toBeVisible();
    await act(async () => pending.resolve(record));
    expect(mocks.router.replace).not.toHaveBeenCalled();
    expect(screen.getByText("Second record")).toBeVisible();
  });

  it("drops loaded data when the signed-in session changes", async () => {
    const pending = deferred<MedicalRecordDTO>();
    mocks.getById.mockReturnValueOnce(pending.promise).mockResolvedValueOnce({ ...record, symptoms: "Second session record" });
    render(<EditMedicalRecordForm recordId="record-a" />);

    act(() => {
      mocks.session = "another-session";
      for (const listener of mocks.sessionListeners) listener();
    });
    expect(await screen.findByText("Second session record")).toBeVisible();
    await act(async () => pending.resolve(record));
    expect(screen.queryByText("First record")).not.toBeInTheDocument();
  });

  it("does not send an old edit under a replacement login session", async () => {
    mocks.getById.mockResolvedValue(record);
    const gate = deferred<void>();
    const originalDigest = crypto.subtle.digest.bind(crypto.subtle);
    const digest = vi.spyOn(crypto.subtle, "digest").mockImplementation(async (algorithm, data) => {
      await gate.promise;
      return originalDigest(algorithm, data);
    });
    render(<EditMedicalRecordForm recordId="record-a" />);
    expect(await screen.findByText("First record")).toBeVisible();
    fireEvent.change(document.getElementById("symptoms")!, { target: { value: "Old session symptoms" } });
    fireEvent.submit(document.getElementById("symptoms")!.closest("form")!);
    await waitFor(() => expect(digest).toHaveBeenCalledTimes(3));

    act(() => {
      mocks.session = "replacement-session";
      for (const listener of mocks.sessionListeners) listener();
    });
    gate.resolve();
    await waitFor(() => expect(mocks.getById).toHaveBeenCalledTimes(2));
    await screen.findByText("First record");
    expect(mocks.update).not.toHaveBeenCalled();
    digest.mockRestore();
  });

  it("preserves a backend field error and correlation ID", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError(
      "Không tìm thấy bệnh nhân", 404, "PATIENT_NOT_FOUND", [{ field: "patientId", message: "Bệnh nhân không tồn tại" }], "record-form-correlation",
    ));
    render(<CreateMedicalRecordForm />);
    fillCreateRecord();
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ khám" }));
    expect(await screen.findByText("Bệnh nhân không tồn tại")).toBeVisible();
    fireEvent.click(screen.getByText("Thông tin hỗ trợ"));
    expect(screen.getByText(/Mã theo dõi: record-form-correlation/)).toBeVisible();
  });

  it("rejects an invalid diagnosis code before creating a record", () => {
    render(<CreateMedicalRecordForm />);
    fillCreateRecord();
    fireEvent.change(screen.getByLabelText("Mã ICD", { exact: false }), { target: { value: "X123" } });
    fireEvent.click(screen.getByRole("button", { name: "Tạo hồ sơ khám" }));
    expect(screen.getByText("Mã ICD phải có dạng A00 hoặc A00.0.")).toBeVisible();
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it("locks a valid record edit while its first update is still pending", async () => {
    const pending = deferred<MedicalRecordDTO>();
    mocks.getById.mockResolvedValue(record);
    mocks.update.mockReturnValue(pending.promise);
    render(<EditMedicalRecordForm recordId="record-a" />);
    expect(await screen.findByText("First record")).toBeVisible();
    fireEvent.change(screen.getByLabelText("Triệu chứng"), { target: { value: "Updated symptoms" } });
    const form = screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!;
    await act(async () => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.update).toHaveBeenCalledTimes(1);
  });

  it("sends only changed structured sections when editing a record", async () => {
    mocks.getById.mockResolvedValue({ ...record, chiefComplaint: "Đau đầu", presentIllness: "Khởi phát sáng nay", pastHistory: null, allergyNote: null, physicalExam: null, advice: null });
    mocks.update.mockResolvedValue(record);
    render(<EditMedicalRecordForm recordId="record-a" />);

    await screen.findByText("First record");
    fireEvent.change(screen.getByLabelText("Lý do khám chính"), { target: { value: "Đau ngực" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    await waitFor(() => expect(mocks.update).toHaveBeenCalledWith("record-a", { chiefComplaint: "Đau ngực" }, {
      idempotencyKey: expect.any(String), correlationId: expect.any(String),
    }));
  });

  it("sends an empty value to clear a structured section", async () => {
    mocks.getById.mockResolvedValue({ ...record, pastHistory: "Hen phế quản" });
    mocks.update.mockResolvedValue(record);
    render(<EditMedicalRecordForm recordId="record-a" />);

    await screen.findByText("First record");
    fireEvent.change(screen.getByLabelText("Tiền sử"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    await waitFor(() => expect(mocks.update).toHaveBeenCalledWith("record-a", { pastHistory: "" }, {
      idempotencyKey: expect.any(String), correlationId: expect.any(String),
    }));
  });

  it("retries an uncertain update with the same idempotency key and payload", async () => {
    mocks.getById.mockResolvedValue({ ...record, chiefComplaint: "Đau đầu" });
    mocks.update.mockRejectedValueOnce(new ApiRequestError("Chưa nhận được kết quả", 503, "UNAVAILABLE"))
      .mockResolvedValueOnce(record);
    render(<EditMedicalRecordForm recordId="record-a" />);

    await screen.findByText("First record");
    fireEvent.change(screen.getByLabelText("Lý do khám chính"), { target: { value: "Đau ngực" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thay đổi" }));
    fireEvent.click(await screen.findByRole("button", { name: "Gửi lại cùng nội dung" }));

    await waitFor(() => expect(mocks.update).toHaveBeenCalledTimes(2));
    expect(mocks.update.mock.calls[0]).toEqual(mocks.update.mock.calls[1]);
  });
});
