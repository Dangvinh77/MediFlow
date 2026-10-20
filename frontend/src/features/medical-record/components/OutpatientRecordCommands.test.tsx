import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { ClinicalLabOrderDTO, MedicalRecordDTO } from "../types";

const mocks = vi.hoisted(() => ({
  complete: vi.fn(),
  createLabOrder: vi.fn(),
  byPatient: vi.fn(),
  router: { replace: vi.fn() },
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({
  medicalRecordApi: {
    complete: mocks.complete,
    createLabOrder: mocks.createLabOrder,
    byPatient: mocks.byPatient,
  },
}));

import { ClinicalLabOrderForm } from "./ClinicalLabOrderForm";
import { CompleteRecordForm } from "./CompleteRecordForm";
import { ExaminationRecordLink } from "./ExaminationRecordLink";

const diagnosis = {
  diagnosisId: "diagnosis-a",
  diagnosisName: "Acute condition",
  description: null,
  icdCode: null,
};

const record: MedicalRecordDTO = {
  recordId: "record-a",
  patientId: "patient-a",
  doctorId: "doctor-a",
  departmentId: "department-a",
  examinationDate: "2026-10-20",
  symptoms: "Fever",
  appointmentId: "appointment-a",
  diagnoses: [diagnosis],
  createdAt: "2026-10-20T01:00:00Z",
  updatedAt: null,
  status: "OPEN",
  disposition: null,
  dispositionNote: null,
  completedAt: null,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function fillLabOrder() {
  fireEvent.change(screen.getByLabelText("Loại xét nghiệm", { exact: false }), { target: { value: "  CBC  " } });
  fireEvent.change(screen.getByLabelText("Mã giá", { exact: false }), { target: { value: "  CBC-APPROVED  " } });
}

describe("outpatient record commands", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.complete.mockReset();
    mocks.createLabOrder.mockReset();
    mocks.byPatient.mockReset();
    mocks.router.replace.mockReset();
  });

  it("requires a diagnosis before enabling record completion", () => {
    render(<CompleteRecordForm record={{ ...record, diagnoses: [] }} onCompleted={vi.fn()} />);

    fireEvent.click(screen.getByRole("checkbox"));
    expect(screen.getByRole("button", { name: "Hoàn tất hồ sơ khám" })).toBeDisabled();
    expect(screen.getByText("Cần thêm ít nhất một chẩn đoán trước khi hoàn tất.")).toBeVisible();
    fireEvent.submit(screen.getByRole("button", { name: "Hoàn tất hồ sơ khám" }).closest("form")!);
    expect(mocks.complete).not.toHaveBeenCalled();
  });

  it("offers only outpatient dispositions and submits the selected completion after confirmation", async () => {
    const completed = { ...record, status: "COMPLETED" as const, disposition: "PRESCRIPTION" as const };
    mocks.complete.mockResolvedValue(completed);
    const onCompleted = vi.fn();
    render(<CompleteRecordForm record={record} onCompleted={onCompleted} />);

    const disposition = screen.getByLabelText("Hướng xử trí", { exact: false }) as HTMLSelectElement;
    expect(Array.from(disposition.options).map((option) => option.value)).toEqual([
      "OUTPATIENT_FOLLOW_UP", "PRESCRIPTION", "OTHER",
    ]);
    fireEvent.change(disposition, { target: { value: "PRESCRIPTION" } });
    fireEvent.change(screen.getByLabelText("Ghi chú xử trí", { exact: false }), { target: { value: "  Theo dõi sau tái khám  " } });
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.submit(screen.getByRole("button", { name: "Hoàn tất hồ sơ khám" }).closest("form")!);

    await waitFor(() => expect(mocks.complete).toHaveBeenCalledWith("record-a", {
      disposition: "PRESCRIPTION",
      dispositionNote: "Theo dõi sau tái khám",
    }));
    expect(onCompleted).toHaveBeenCalledWith(completed);
  });

  it("locks a completion command against two synchronous submissions", async () => {
    const pending = deferred<MedicalRecordDTO>();
    mocks.complete.mockReturnValue(pending.promise);
    render(<CompleteRecordForm record={record} onCompleted={vi.fn()} />);
    fireEvent.click(screen.getByRole("checkbox"));

    const form = screen.getByRole("button", { name: "Hoàn tất hồ sơ khám" }).closest("form")!;
    await act(async () => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.complete).toHaveBeenCalledTimes(1);
    await act(async () => pending.resolve(record));
  });

  it("submits a Clinical lab order once with trimmed input and the exact record identity", async () => {
    const pending = deferred<ClinicalLabOrderDTO>();
    mocks.createLabOrder.mockReturnValue(pending.promise);
    const onCreated = vi.fn();
    render(<ClinicalLabOrderForm recordId="record-a" onCreated={onCreated} />);
    fillLabOrder();

    const form = screen.getByRole("button", { name: "Lập chỉ định" }).closest("form")!;
    await act(async () => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.createLabOrder).toHaveBeenCalledTimes(1);
    expect(mocks.createLabOrder).toHaveBeenCalledWith("record-a", {
      labType: "CBC",
      requestedDate: expect.any(String),
      priceCode: "CBC-APPROVED",
    });
    await act(async () => pending.resolve({
      sourceOrderId: "source-order-a",
      recordId: "record-a",
      patientId: "patient-a",
      requestingDoctorId: "doctor-a",
      requestingDepartmentId: "department-a",
      careEpisodeType: "OUTPATIENT_VISIT",
      careEpisodeId: "appointment-a",
      labType: "CBC",
      requestedDate: "2026-10-20",
      priceCode: "CBC-APPROVED",
      orderedAt: "2026-10-20T02:00:00Z",
    }));
    expect(onCreated).toHaveBeenCalledTimes(1);
  });

  it("blocks a retry after a Clinical lab create has an unknown outcome", async () => {
    mocks.createLabOrder.mockRejectedValue(new ApiRequestError("Kết quả chưa rõ", 503, "TEMPORARY_FAILURE"));
    render(<ClinicalLabOrderForm recordId="record-a" onCreated={vi.fn()} />);
    fillLabOrder();

    const form = screen.getByRole("button", { name: "Lập chỉ định" }).closest("form")!;
    await act(async () => fireEvent.submit(form));
    expect(await screen.findByText(/Không gửi lại chỉ định khi chưa đối soát kết quả/)).toBeVisible();
    expect(screen.getByText(/Hãy liên hệ bộ phận xét nghiệm để xác nhận tình trạng chỉ định/)).toBeVisible();
    expect(screen.queryByText(/Giữ mã theo dõi ở trên/)).not.toBeInTheDocument();
    await act(async () => fireEvent.submit(form));
    expect(mocks.createLabOrder).toHaveBeenCalledTimes(1);
  });

  it("does not redirect or invoke a late Clinical callback after the order form unmounts", async () => {
    const pending = deferred<ClinicalLabOrderDTO>();
    mocks.createLabOrder.mockReturnValue(pending.promise);
    const onCreated = vi.fn();
    const view = render(<ClinicalLabOrderForm recordId="record-a" onCreated={onCreated} />);
    fillLabOrder();
    fireEvent.click(screen.getByRole("button", { name: "Lập chỉ định" }));
    await waitFor(() => expect(mocks.createLabOrder).toHaveBeenCalledTimes(1));
    view.unmount();

    await act(async () => pending.reject(new ApiRequestError("Phiên cũ", 401, "UNAUTHORIZED")));
    expect(onCreated).not.toHaveBeenCalled();
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  it("links only the record returned for the exact appointment", async () => {
    mocks.byPatient.mockResolvedValue([
      { ...record, recordId: "record-other-appointment", appointmentId: "appointment-other" },
      record,
    ]);
    render(<ExaminationRecordLink patientId="patient-a" appointmentId="appointment-a" />);

    expect(await screen.findByRole("link", { name: "Mở hồ sơ khám" })).toHaveAttribute("href", "/records/record-a");
    expect(mocks.byPatient).toHaveBeenCalledWith("patient-a");
  });

  it("does not choose between multiple records for the same appointment", async () => {
    mocks.byPatient.mockResolvedValue([
      record,
      { ...record, recordId: "record-duplicate" },
    ]);
    render(<ExaminationRecordLink patientId="patient-a" appointmentId="appointment-a" />);

    expect(await screen.findByText(/Chưa xác định được một hồ sơ duy nhất/)).toBeVisible();
    expect(screen.queryByRole("link", { name: "Mở hồ sơ khám" })).not.toBeInTheDocument();
  });

  it("ignores a stale record lookup after the appointment identity changes", async () => {
    const oldLookup = deferred<MedicalRecordDTO[]>();
    mocks.byPatient.mockReturnValueOnce(oldLookup.promise).mockResolvedValueOnce([
      { ...record, recordId: "record-b", patientId: "patient-b", appointmentId: "appointment-b" },
    ]);
    const view = render(<ExaminationRecordLink patientId="patient-a" appointmentId="appointment-a" />);
    view.rerender(<ExaminationRecordLink patientId="patient-b" appointmentId="appointment-b" />);

    const currentLink = await screen.findByRole("link", { name: "Mở hồ sơ khám" });
    await act(async () => oldLookup.resolve([record]));
    expect(currentLink).toHaveAttribute("href", "/records/record-b");
    expect(screen.queryByRole("link", { name: "Mở hồ sơ khám" })).toHaveAttribute("href", "/records/record-b");
  });
});
