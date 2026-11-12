import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { PrescriptionDTO } from "../../types";

const mocks = vi.hoisted(() => ({ role: "DOCTOR", current: true, getDrug: vi.fn(), searchDrugs: vi.fn(), update: vi.fn() }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role }));
vi.mock("../PharmacySessionBoundary", () => {
  const scope = () => mocks.current;
  return { usePharmacyRequestScope: () => scope };
});
vi.mock("../../api", () => ({ pharmacyApi: { getDrug: mocks.getDrug, searchDrugs: mocks.searchDrugs, updatePrescription: mocks.update } }));
import { EditPrescriptionForm } from "./EditPrescriptionForm";

const id = "00000000-0000-4000-8000-000000000001";
const drug = { drugId: id, drugName: "Paracetamol", unit: "Viên", stockQuantity: 100, expiryDate: "2099-01-01" };
const prescription = { prescriptionId: id, revision: 3, recordId: id, patientId: id, doctorId: id, departmentId: id,
  prescribedDate: "2026-11-12", paymentConfirmed: false, status: "ACTIVE", dispenseStatus: "PENDING",
  lines: [{ lineId: id, drugId: id, drugName: "Paracetamol", quantity: 2, dosage: "Ngày 2 lần", note: null }] } as PrescriptionDTO;

async function save() {
  fireEvent.click(await screen.findByRole("button", { name: "Lưu đơn thuốc" }));
}

describe("EditPrescriptionForm", () => {
  beforeEach(() => {
    mocks.role = "DOCTOR"; mocks.current = true;
    mocks.getDrug.mockReset().mockResolvedValue(drug);
    mocks.searchDrugs.mockReset().mockResolvedValue({ content: [drug], totalPages: 1, totalElements: 1 });
    mocks.update.mockReset();
  });

  it("sends only the version and edited clinical lines with one command key", async () => {
    const saved = vi.fn(); mocks.update.mockResolvedValue({ ...prescription, revision: 4 });
    render(<EditPrescriptionForm prescription={prescription} onSaved={saved} onClose={vi.fn()} onConflict={vi.fn()} />);
    fireEvent.change(await screen.findByLabelText("Số lượng dòng 1"), { target: { value: "5" } });
    await save();
    await waitFor(() => expect(saved).toHaveBeenCalled());
    expect(mocks.update).toHaveBeenCalledWith(id, { expectedRevision: 3, lines: [{ drugId: id, quantity: 5, dosage: "Ngày 2 lần" }] }, expect.any(String));
    expect(mocks.update.mock.calls[0][2]).toMatch(/^[0-9a-f-]{36}$/);
  });

  it("keeps an uncertain request frozen and explicitly retries identical bytes and key", async () => {
    mocks.update.mockRejectedValueOnce(new ApiRequestError("Timeout", 504)).mockResolvedValueOnce({ ...prescription, revision: 4 });
    render(<EditPrescriptionForm prescription={prescription} onSaved={vi.fn()} onClose={vi.fn()} onConflict={vi.fn()} />);
    await screen.findByLabelText("Số lượng dòng 1"); await save();
    const retry = await screen.findByRole("button", { name: "Gửi lại đúng lần sửa" });
    expect(screen.getByLabelText("Số lượng dòng 1")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Đóng" })).toBeDisabled();
    const original = mocks.update.mock.calls[0]; fireEvent.click(retry);
    await waitFor(() => expect(mocks.update).toHaveBeenCalledTimes(2));
    expect(mocks.update.mock.calls[1]).toEqual(original);
  });

  it("reloads after paid or stale conflict instead of resending", async () => {
    const conflict = vi.fn(); mocks.update.mockRejectedValue(new ApiRequestError("Đơn đã được thanh toán", 409));
    render(<EditPrescriptionForm prescription={prescription} onSaved={vi.fn()} onClose={vi.fn()} onConflict={conflict} />);
    await screen.findByLabelText("Số lượng dòng 1"); await save();
    await waitFor(() => expect(conflict).toHaveBeenCalledWith("Đơn đã được thanh toán"));
    expect(mocks.update).toHaveBeenCalledTimes(1);
  });

  it("blocks a save when the session or role changed", async () => {
    render(<EditPrescriptionForm prescription={prescription} onSaved={vi.fn()} onClose={vi.fn()} onConflict={vi.fn()} />);
    await screen.findByLabelText("Số lượng dòng 1");
    mocks.current = false; await save(); expect(mocks.update).not.toHaveBeenCalled();
  });

  it("does not send a second request while the first save is pending", async () => {
    let resolve!: (value: PrescriptionDTO) => void;
    mocks.update.mockReturnValue(new Promise<PrescriptionDTO>(done => { resolve = done; }));
    render(<EditPrescriptionForm prescription={prescription} onSaved={vi.fn()} onClose={vi.fn()} onConflict={vi.fn()} />);
    await screen.findByLabelText("Số lượng dòng 1");
    const button = screen.getByRole("button", { name: "Lưu đơn thuốc" });
    fireEvent.click(button); fireEvent.click(button); expect(mocks.update).toHaveBeenCalledTimes(1);
    await act(async () => resolve({ ...prescription, revision: 4 }));
  });
  it("refreshes after Billing reports a payment with the standard 422 business status", async () => {
    const conflict = vi.fn();
    mocks.update.mockRejectedValue(new ApiRequestError("Payment exists", 422, "BILLING_PRESCRIPTION_REVISION_PAYMENT_EXISTS"));
    render(<EditPrescriptionForm prescription={prescription} onSaved={vi.fn()} onClose={vi.fn()} onConflict={conflict} />);
    await screen.findByLabelText("Số lượng dòng 1"); await save();
    await waitFor(() => expect(conflict).toHaveBeenCalledWith("Đơn thuốc đã có giao dịch hoặc đang được thu tiền. Hãy làm mới để kiểm tra."));
    expect(mocks.update).toHaveBeenCalledTimes(1);
  });

});
