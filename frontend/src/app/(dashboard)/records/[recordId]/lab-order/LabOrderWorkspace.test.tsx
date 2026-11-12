import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearSession, storeSession } from "@/lib/session";
import { ApiRequestError } from "@/lib/api";
import type { ClinicalLabOrderDTO } from "@/features/medical-record/types";

const mocks = vi.hoisted(() => ({ getRecord: vi.fn(), createLabOrder: vi.fn(), createLabRequest: vi.fn(), catalog: vi.fn() }));

vi.mock("@/features/medical-record/api", () => ({
  medicalRecordApi: { getById: mocks.getRecord, createLabOrder: mocks.createLabOrder },
}));
vi.mock("@/features/lab/api", () => ({
  labApi: { create: mocks.createLabRequest, catalog: mocks.catalog },
}));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));

import { LabOrderWorkspace } from "./LabOrderWorkspace";

const sourceOrder: ClinicalLabOrderDTO = {
  sourceOrderId: "11111111-1111-4111-8111-111111111111",
  recordId: "22222222-2222-4222-8222-222222222222",
  patientId: "33333333-3333-4333-8333-333333333333",
  requestingDoctorId: "44444444-4444-4444-8444-444444444444",
  requestingDepartmentId: "55555555-5555-4555-8555-555555555555",
  careEpisodeType: "OUTPATIENT_VISIT",
  careEpisodeId: "66666666-6666-4666-8666-666666666666",
  labType: "CBC",
  requestedDate: "2026-10-20",
  priceCode: "CBC-APPROVED",
  orderedAt: "2026-10-20T02:00:00Z",
  serviceCode: "CBC",
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

async function startClinicalOrder() {
  await screen.findByRole("heading", { name: "Chỉ định xét nghiệm" });
  fireEvent.change(screen.getByLabelText("Loại xét nghiệm", { exact: false }), { target: { value: "CBC" } });
  fireEvent.change(screen.getByLabelText("Mã giá", { exact: false }), { target: { value: "CBC-APPROVED" } });
  await act(async () => {
    fireEvent.submit(screen.getByRole("button", { name: "Gửi chỉ định xét nghiệm" }).closest("form")!);
  });
}

describe("Clinical and Lab order workspace composition", () => {
  beforeEach(() => {
    clearSession();
    storeSession({ accessToken: `session-a-${crypto.randomUUID()}`, refreshToken: "refresh-a", role: "DOCTOR" });
    vi.clearAllMocks();
    mocks.createLabOrder.mockReset();
    mocks.createLabRequest.mockReset();
    mocks.catalog.mockReset();
    mocks.catalog.mockRejectedValue(new ApiRequestError("Danh mục tắt", 404, "LAB_CATALOG_DISABLED"));
    mocks.getRecord.mockReset();
    mocks.getRecord.mockImplementation(async (recordId: string) => ({
      recordId, patientId: sourceOrder.patientId, doctorId: sourceOrder.requestingDoctorId,
      departmentId: sourceOrder.requestingDepartmentId, examinationDate: "2026-10-20", status: "OPEN",
      symptoms: "Demo", diagnoses: [], appointmentId: sourceOrder.careEpisodeId,
      createdAt: sourceOrder.orderedAt, updatedAt: null, completedAt: null, disposition: null, dispositionNote: null,
    }));
  });

  it("loads the authoritative record context before allowing an order", async () => {
    render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);
    expect(await screen.findByRole("region", { name: "Ngữ cảnh chỉ định xét nghiệm" })).toHaveTextContent("20/10/2026");
    expect(mocks.getRecord).toHaveBeenCalledWith(sourceOrder.recordId);
  });

  it("does not offer an order when the record response belongs to another resource", async () => {
    mocks.getRecord.mockResolvedValue({ recordId: "another-record", status: "OPEN" });
    render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);
    expect(await screen.findByText(/Không xác minh được hồ sơ/)).toBeVisible();
    expect(screen.queryByRole("button", { name: "Gửi chỉ định xét nghiệm" })).not.toBeInTheDocument();
    expect(mocks.createLabOrder).not.toHaveBeenCalled();
  });

  it("passes the exact source-order identifiers and episode fields returned by Clinical to Lab", async () => {
    mocks.createLabOrder.mockResolvedValue(sourceOrder);
    mocks.createLabRequest.mockResolvedValue({ testId: "lab-test-a" });
    render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);

    await startClinicalOrder();
    await waitFor(() => expect(mocks.createLabRequest).toHaveBeenCalledWith({
      recordId: sourceOrder.recordId,
      patientId: sourceOrder.patientId,
      requestingDepartmentId: sourceOrder.requestingDepartmentId,
      labType: sourceOrder.labType,
      requestedDate: sourceOrder.requestedDate,
      sourceOrderId: sourceOrder.sourceOrderId,
      careEpisodeType: sourceOrder.careEpisodeType,
      careEpisodeId: sourceOrder.careEpisodeId,
      priceCode: sourceOrder.priceCode,
      serviceCode: sourceOrder.serviceCode,
    }));
  });

  it("creates a catalogue order with its name, price code and service code in Clinical and Lab", async () => {
    mocks.catalog.mockResolvedValue([{
      serviceCode: "CBC", name: "Công thức máu", displayGroup: "HEMATOLOGY", specimenType: "Máu",
      preparation: "Nhịn ăn 8 giờ", turnaroundHours: 4, priceCode: "LAB-CBC", templateCode: "CBC-V1", panel: true,
    }]);
    mocks.createLabOrder.mockResolvedValue({ ...sourceOrder, labType: "Công thức máu", priceCode: "LAB-CBC", serviceCode: "CBC" });
    mocks.createLabRequest.mockResolvedValue({ testId: "lab-test-a" });
    render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);

    const service = await screen.findByLabelText(/Dịch vụ xét nghiệm/);
    fireEvent.change(service, { target: { value: "CBC" } });
    expect(screen.getByText("Máu")).toBeVisible();
    expect(screen.getByText("Nhịn ăn 8 giờ")).toBeVisible();
    expect(screen.getByText("4 giờ")).toBeVisible();
    await act(async () => {
      fireEvent.submit(screen.getByRole("button", { name: "Gửi chỉ định xét nghiệm" }).closest("form")!);
    });
    await waitFor(() => expect(mocks.createLabOrder).toHaveBeenCalledWith(sourceOrder.recordId, {
      labType: "Công thức máu", requestedDate: new Date().toISOString().slice(0, 10), priceCode: "LAB-CBC", serviceCode: "CBC",
    }));
    await waitFor(() => expect(mocks.createLabRequest).toHaveBeenCalledWith(expect.objectContaining({
      labType: "Công thức máu", priceCode: "LAB-CBC", serviceCode: "CBC",
    })));
  });

  it("keeps the current free-text order form when the catalogue is disabled", async () => {
    render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);
    expect(await screen.findByLabelText("Loại xét nghiệm", { exact: false })).toBeVisible();
    expect(screen.getByLabelText("Mã giá", { exact: false })).toBeVisible();
    expect(screen.queryByLabelText("Dịch vụ xét nghiệm")).not.toBeInTheDocument();
  });

  it("does not transmit a source order for a different patient", async () => {
    mocks.createLabOrder.mockResolvedValue({ ...sourceOrder, patientId: "another-patient" });
    render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);
    await startClinicalOrder();
    expect(await screen.findByRole("alert")).toHaveTextContent("không khớp hồ sơ đang mở");
    expect(screen.queryByRole("button", { name: "Tiếp tục chuyển sang Lab" })).not.toBeInTheDocument();
    expect(mocks.createLabRequest).not.toHaveBeenCalled();
  });

  it("retries a failed context read without creating an order", async () => {
    mocks.getRecord.mockRejectedValueOnce(new Error("unavailable"));
    render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);
    expect(await screen.findByText(/Không xác minh được hồ sơ/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: /Thử lại/ }));
    expect(await screen.findByRole("heading", { name: "Chỉ định xét nghiệm" })).toBeVisible();
    expect(mocks.getRecord).toHaveBeenCalledTimes(2);
    expect(mocks.createLabOrder).not.toHaveBeenCalled();
  });

  it("drops a pending Clinical response when the record route changes", async () => {
    const pending = deferred<ClinicalLabOrderDTO>();
    mocks.createLabOrder.mockReturnValue(pending.promise);
    const view = render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);
    await startClinicalOrder();
    await waitFor(() => expect(mocks.createLabOrder).toHaveBeenCalledTimes(1));

    view.rerender(<LabOrderWorkspace recordId="77777777-7777-4777-8777-777777777777" />);
    expect(await screen.findByRole("heading", { name: "Chỉ định xét nghiệm" })).toBeVisible();
    await act(async () => pending.resolve(sourceOrder));

    expect(screen.queryByText(sourceOrder.sourceOrderId)).not.toBeInTheDocument();
    expect(mocks.createLabRequest).not.toHaveBeenCalled();
  });

  it("clears the mounted source order when the signed-in session changes", async () => {
    mocks.createLabOrder.mockResolvedValue(sourceOrder);
    const view = render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);
    await startClinicalOrder();
    expect(await screen.findByText(sourceOrder.sourceOrderId)).not.toBeVisible();

    const nextRecord = await mocks.getRecord(sourceOrder.recordId);
    mocks.getRecord.mockResolvedValue({ ...nextRecord, patientId: "new-session-patient" });
    await act(async () => storeSession({ accessToken: "session-b", refreshToken: "refresh-b", role: "DOCTOR" }));
    expect(await screen.findByRole("heading", { name: "Chỉ định xét nghiệm" })).toBeVisible();
    expect(screen.queryByText(sourceOrder.sourceOrderId)).not.toBeInTheDocument();
    expect(view.container).not.toHaveTextContent(sourceOrder.patientId);
    expect(view.container).toHaveTextContent("new-session-patient");
  });
});
