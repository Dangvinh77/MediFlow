import { webcrypto } from "node:crypto";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { MedicalRecordDTO } from "../types";
import { CloseRecordForm } from "./CloseRecordForm";
import { CompleteRecordForm } from "./CompleteRecordForm";

const mocks = vi.hoisted(() => ({ close: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "doctor-session" }));
vi.mock("../api", () => ({ medicalRecordApi: { closeWithoutConclusion: mocks.close } }));

const record: MedicalRecordDTO = {
  recordId: "record-a", patientId: "patient-a", doctorId: "doctor-a", departmentId: "department-a",
  examinationDate: "2026-11-10", symptoms: null, appointmentId: "appointment-a", diagnoses: [],
  createdAt: "2026-11-10T01:00:00Z", updatedAt: null, status: "OPEN", disposition: null,
  dispositionNote: null, completedAt: null,
};

function fill(note: string) {
  fireEvent.change(screen.getByLabelText(/Lý do kết thúc/), { target: { value: "PATIENT_REFUSED_EXAM" } });
  fireEvent.change(screen.getByLabelText(/Ghi chú và dặn dò/), { target: { value: note } });
  fireEvent.click(screen.getByRole("checkbox"));
}

describe("Ending a visit without a conclusion", () => {
  beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal("crypto", webcrypto); sessionStorage.clear(); });

  it("closes with reason and note", async () => {
    const closed = { ...record, status: "CLOSED_WITHOUT_CONCLUSION" as const };
    const onClosed = vi.fn();
    mocks.close.mockResolvedValue(closed);
    render(<CloseRecordForm record={record} onClosed={onClosed} />);
    fill("  Từ chối khám, đã dặn quay lại khi đau tăng  ");
    fireEvent.click(screen.getByRole("button", { name: "Kết thúc lượt khám" }));
    await waitFor(() => expect(onClosed).toHaveBeenCalledWith(closed));
    expect(mocks.close.mock.calls[0][0]).toBe("record-a");
    expect(mocks.close.mock.calls[0][1]).toEqual({ reason: "PATIENT_REFUSED_EXAM", note: "Từ chối khám, đã dặn quay lại khi đau tăng" });
    expect(mocks.close.mock.calls[0][2].idempotencyKey).toBeTruthy();
    expect(mocks.close.mock.calls[0][2].correlationId).toBeTruthy();
  });

  it("close needs a note", () => {
    render(<CloseRecordForm record={record} onClosed={vi.fn()} />);
    fill("   ");
    expect(screen.getByRole("button", { name: "Kết thúc lượt khám" })).toBeDisabled();
    expect(mocks.close).not.toHaveBeenCalled();
  });

  it("close offers no diagnosis field", () => {
    render(<CloseRecordForm record={record} onClosed={vi.fn()} />);
    expect(screen.queryByLabelText(/chẩn đoán/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/hướng xử trí/i)).not.toBeInTheDocument();
  });

  it("pending error points to the orders panel", async () => {
    mocks.close.mockRejectedValue(new ApiRequestError("Còn chỉ định chưa xong", 422, "CLINICAL_LAB_ORDER_PENDING"));
    render(<CloseRecordForm record={record} onClosed={vi.fn()} />);
    fill("Bệnh nhân tự ý về");
    fireEvent.click(screen.getByRole("button", { name: "Kết thúc lượt khám" }));
    expect(await screen.findByRole("link", { name: "Xem chỉ định xét nghiệm" })).toHaveAttribute("href", "#record-lab-orders");
  });

  it("offers no admission or transfer", () => {
    render(<CompleteRecordForm record={record} onCompleted={vi.fn()} />);
    const options = Array.from((screen.getByLabelText(/Hướng xử trí/) as HTMLSelectElement).options).map(option => option.value);
    expect(options).not.toContain("ADMISSION");
    expect(options).not.toContain("TRANSFER");
  });
});
