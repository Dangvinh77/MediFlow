import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PrescriptionDTO } from "../../types";
import type { Role } from "@/lib/roles";
import { ApiRequestError } from "@/lib/api";
const mocks = vi.hoisted(() => ({ role: "PHARMACIST" as Role, getPrescription: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
vi.mock("../../api", () => ({ pharmacyApi: { getPrescription: mocks.getPrescription } }));
import { PrescriptionDetail } from "./PrescriptionDetail";

const prescription: PrescriptionDTO = { prescriptionId: "00000000-0000-4000-8000-000000000001", recordId: null, patientId: "00000000-0000-4000-8000-000000000002", doctorId: "00000000-0000-4000-8000-000000000003", departmentId: "00000000-0000-4000-8000-000000000004", prescribedDate: "2026-10-12", totalAmount: 12345, lines: [], status: "ACTIVE", dispenseStatus: "PENDING", paymentConfirmed: true, cancelledAt: null, cancelledBy: null, cancellationReason: null, createdAt: "2026-10-12T00:00:00Z", updatedAt: "2026-10-12T00:00:00Z" };

describe("PrescriptionDetail status evidence", () => {
  beforeEach(() => { mocks.role = "PHARMACIST"; mocks.getPrescription.mockReset(); });
  it("shows payment proof independently of pending dispense and supports absent medical record", async () => {
    mocks.getPrescription.mockResolvedValue(prescription); render(<PrescriptionDetail prescriptionId={prescription.prescriptionId} mode="legacy" />);
    expect(await screen.findByText("Chờ xuất thuốc")).toBeVisible();
    expect(screen.getByText("Đã xác nhận thanh toán")).toBeVisible();
    expect(screen.queryByText("Chờ thanh toán")).not.toBeInTheDocument();
    expect(screen.getByText("Không có")).toBeVisible();
    expect(screen.getByRole("button", { name: "Xuất thuốc" })).toBeEnabled();
  });
  it("does not offer dispense when the same pending prescription lacks payment proof", async () => {
    mocks.getPrescription.mockResolvedValue({ ...prescription, paymentConfirmed: false });
    render(<PrescriptionDetail prescriptionId={prescription.prescriptionId} mode="legacy" />);
    expect(await screen.findByText("Chờ xuất thuốc")).toBeVisible();
    expect(screen.getByText("Chờ thanh toán", { exact: true })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
  });
  it("refreshes pending payment with a fresh GET and uses only server amounts before offering dispense", async () => {
    mocks.getPrescription.mockResolvedValueOnce({ ...prescription, paymentConfirmed: false })
      .mockResolvedValueOnce({ ...prescription, totalAmount: 98765 });
    render(<PrescriptionDetail prescriptionId={prescription.prescriptionId} mode="legacy" />);
    await screen.findByText("Chờ thanh toán", { exact: true });
    fireEvent.click(screen.getByRole("button", { name: "Tải lại trạng thái" }));
    expect(await screen.findByText("Đã xác nhận thanh toán")).toBeVisible();
    expect(screen.getByRole("button", { name: "Xuất thuốc" })).toBeEnabled();
    expect(screen.getByText(/98.765/)).toBeVisible();
    expect(mocks.getPrescription).toHaveBeenCalledTimes(2);
    expect(mocks.getPrescription).toHaveBeenLastCalledWith(prescription.prescriptionId);
  });
  it("does not keep stale paid actions after a failed refresh, and retries only the read", async () => {
    mocks.getPrescription.mockResolvedValueOnce(prescription)
      .mockRejectedValueOnce(new ApiRequestError("Refresh unavailable", 503, "UNAVAILABLE", [], "refresh-trace"))
      .mockResolvedValueOnce({ ...prescription, paymentConfirmed: false });
    render(<PrescriptionDetail prescriptionId={prescription.prescriptionId} mode="legacy" />);
    await screen.findByRole("button", { name: "Xuất thuốc" });
    fireEvent.click(screen.getByRole("button", { name: "Tải lại trạng thái" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("refresh-trace");
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("Chờ thanh toán", { exact: true });
    expect(mocks.getPrescription).toHaveBeenCalledTimes(3);
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
  });
  it.each(["FULFILLED", "CANCELLED", "EXPIRED", "DISPENSE_FAILED"] as const)("keeps %s prescriptions read-only even with payment confirmed", async status => {
    mocks.getPrescription.mockResolvedValue({ ...prescription, status }); render(<PrescriptionDetail prescriptionId={prescription.prescriptionId} mode="legacy" />);
    await screen.findByRole("heading", { name: "Chi tiết đơn thuốc" });
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Hủy đơn" })).not.toBeInTheDocument();
  });
  it("distinguishes missing dispense from a missing prescription", async () => {
    mocks.getPrescription.mockRejectedValue(new ApiRequestError("Missing dispense", 404, "DISPENSE_NOT_FOUND"));
    render(<PrescriptionDetail prescriptionId={prescription.prescriptionId} mode="legacy" />);
    expect(await screen.findByRole("heading", { name: "Chưa có phiếu xuất thuốc" })).toBeVisible();
    expect(screen.queryByRole("heading", { name: "Không tìm thấy đơn thuốc" })).not.toBeInTheDocument();
  });
  it("reports a missing prescription without guessing a sibling context ID", async () => {
    mocks.getPrescription.mockRejectedValue(new ApiRequestError("Missing", 404, "PRESCRIPTION_NOT_FOUND"));
    render(<PrescriptionDetail prescriptionId={prescription.prescriptionId} mode="legacy" />);
    expect(await screen.findByRole("heading", { name: "Không tìm thấy đơn thuốc" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
  });
  it("keeps correlation and denies paid actions on a forbidden read", async () => {
    mocks.getPrescription.mockRejectedValue(new ApiRequestError("Denied", 403, "FORBIDDEN", [], "read-trace"));
    render(<PrescriptionDetail prescriptionId={prescription.prescriptionId} mode="legacy" />);
    expect(await screen.findByRole("alert")).toHaveTextContent("không có quyền");
    expect(screen.getByRole("alert")).toHaveTextContent("read-trace");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
  });
  it("does not overwrite a new prescription with a late earlier detail response", async () => {
    let resolveOld!: (value: PrescriptionDTO) => void;
    const old = new Promise<PrescriptionDTO>(resolve => { resolveOld = resolve; });
    mocks.getPrescription.mockReturnValueOnce(old).mockResolvedValueOnce({ ...prescription, prescriptionId: "00000000-0000-4000-8000-000000000005", status: "CANCELLED" });
    const view = render(<PrescriptionDetail prescriptionId={prescription.prescriptionId} mode="legacy" />);
    await waitFor(() => expect(mocks.getPrescription).toHaveBeenCalledTimes(1));
    view.rerender(<PrescriptionDetail prescriptionId="00000000-0000-4000-8000-000000000005" mode="legacy" />);
    await screen.findByText("Đã hủy", { exact: true });
    await act(async () => resolveOld(prescription));
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
  });
});
