import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearSession, storeSession } from "@/lib/session";
import type { ClinicalLabOrderDTO } from "@/features/medical-record/types";

const mocks = vi.hoisted(() => ({ createLabOrder: vi.fn(), createLabRequest: vi.fn() }));

vi.mock("@/features/medical-record/api", () => ({
  medicalRecordApi: { createLabOrder: mocks.createLabOrder },
}));
vi.mock("@/features/lab/api", () => ({
  labApi: { create: mocks.createLabRequest },
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
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function startClinicalOrder() {
  fireEvent.change(screen.getByLabelText("Loại xét nghiệm", { exact: false }), { target: { value: "CBC" } });
  fireEvent.change(screen.getByLabelText("Mã giá", { exact: false }), { target: { value: "CBC-APPROVED" } });
  fireEvent.submit(screen.getByRole("button", { name: "Lập chỉ định" }).closest("form")!);
}

describe("Clinical and Lab order workspace composition", () => {
  beforeEach(() => {
    clearSession();
    storeSession({ accessToken: "session-a", refreshToken: "refresh-a", role: "DOCTOR" });
    vi.clearAllMocks();
    mocks.createLabOrder.mockReset();
    mocks.createLabRequest.mockReset();
  });

  it("passes the exact source-order identifiers and episode fields returned by Clinical to Lab", async () => {
    mocks.createLabOrder.mockResolvedValue(sourceOrder);
    mocks.createLabRequest.mockResolvedValue({ testId: "lab-test-a" });
    render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);

    await act(async () => startClinicalOrder());
    fireEvent.click(await screen.findByRole("button", { name: "Tạo yêu cầu từ chỉ định" }));
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
    }));
  });

  it("drops a pending Clinical response when the record route changes", async () => {
    const pending = deferred<ClinicalLabOrderDTO>();
    mocks.createLabOrder.mockReturnValue(pending.promise);
    const view = render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);
    startClinicalOrder();
    await waitFor(() => expect(mocks.createLabOrder).toHaveBeenCalledTimes(1));

    view.rerender(<LabOrderWorkspace recordId="77777777-7777-4777-8777-777777777777" />);
    expect(await screen.findByRole("heading", { name: "1. Lập chỉ định xét nghiệm" })).toBeVisible();
    await act(async () => pending.resolve(sourceOrder));

    expect(screen.queryByText(sourceOrder.sourceOrderId)).not.toBeInTheDocument();
    expect(mocks.createLabRequest).not.toHaveBeenCalled();
  });

  it("clears the mounted source order when the signed-in session changes", async () => {
    mocks.createLabOrder.mockResolvedValue(sourceOrder);
    const view = render(<LabOrderWorkspace recordId={sourceOrder.recordId} />);
    await act(async () => startClinicalOrder());
    expect(await screen.findByText(sourceOrder.sourceOrderId)).not.toBeVisible();

    act(() => storeSession({ accessToken: "session-b", refreshToken: "refresh-b", role: "DOCTOR" }));
    expect(await screen.findByRole("heading", { name: "1. Lập chỉ định xét nghiệm" })).toBeVisible();
    expect(screen.queryByText(sourceOrder.sourceOrderId)).not.toBeInTheDocument();
    expect(view.container).not.toHaveTextContent(sourceOrder.patientId);
  });
});
