import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import type { PrescriptionDTO } from "../../types";
import { PrescriptionDetail } from "./PrescriptionDetail";

const mocks = vi.hoisted(() => ({ role: "PHARMACIST" as Role, get: vi.fn(), dispense: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
vi.mock("../../api", () => ({ pharmacyApi: { getPrescription: mocks.get, dispensePrescription: mocks.dispense } }));

const id = "00000000-0000-4000-8000-000000000001";
// The read DTO contains no care version or clearance metadata. Neither value of the legacy
// boolean is a V1 authorization proof; only the command can validate the actual grant.
const rx: PrescriptionDTO = { prescriptionId: id, recordId: id, patientId: id, doctorId: id, departmentId: id,
  prescribedDate: "2026-10-19", totalAmount: 30000, lines: [], status: "ACTIVE", dispenseStatus: "PENDING", paymentConfirmed: false,
  cancelledAt: null, cancelledBy: null, cancellationReason: null, createdAt: "2026-10-19T00:00:00Z", updatedAt: "2026-10-19T00:00:00Z" };
const terminal = { ...rx, status: "FULFILLED", dispenseStatus: "DISPENSED" } as const;
const slip = { dispenseId: id, prescriptionId: id, status: "DISPENSED", dispensedAt: rx.createdAt, dispensedBy: id, failureReason: null } as const;

async function open() {
  render(<PrescriptionDetail prescriptionId={id} mode="outpatient" />);
  const trigger = await screen.findByRole("button", { name: "Xuất thuốc" });
  trigger.focus(); fireEvent.click(trigger);
  return trigger;
}
function submit() {
  fireEvent.submit(screen.getByRole("button", { name: "Xác nhận xuất thuốc" }).closest("form")!);
}

describe("V1 dispense uses the locked runtime mode and server command authority", () => {
  beforeEach(() => {
    mocks.role = "PHARMACIST"; mocks.get.mockReset().mockResolvedValue(rx);
    mocks.dispense.mockReset(); mocks.router.replace.mockReset();
  });
  it.each(["ADMIN", "PHARMACIST"] as const)("allows %s to confirm V1 with the legacy boolean false without claiming payment", async role => {
    mocks.role = role; await open();
    expect(screen.getByRole("button", { name: "Đóng" })).toHaveFocus();
    expect(screen.queryByText("Chờ thanh toán", { exact: true })).not.toBeInTheDocument();
    expect(screen.queryByText("Đã xác nhận thanh toán")).not.toBeInTheDocument();
    expect(screen.getByText("Thanh toán được kiểm tra khi xuất thuốc")).toBeVisible();
    expect(mocks.dispense).not.toHaveBeenCalled();
  });
  it("does not turn the legacy boolean true into a V1 payment claim either", async () => {
    mocks.get.mockResolvedValue({ ...rx, paymentConfirmed: true }); await open();
    expect(screen.queryByText("Đã xác nhận thanh toán")).not.toBeInTheDocument();
  });
  it.each(["DOCTOR", "NURSE", "PATIENT", "CASHIER", "MANAGER", "LAB_TECH", "SYSTEM"] as const)("never grants %s a V1 dispense control", async role => {
    mocks.role = role; render(<PrescriptionDetail prescriptionId={id} mode="outpatient" />);
    if (role === "DOCTOR") await screen.findByRole("heading", { name: "Chi tiết đơn thuốc" });
    else await screen.findByRole("alert");
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
    expect(mocks.dispense).not.toHaveBeenCalled();
  });
  it.each(["FULFILLED", "CANCELLED", "EXPIRED", "DISPENSE_FAILED"] as const)("keeps V1 %s read-only even with legacy false", async status => {
    mocks.get.mockResolvedValue({ ...rx, status });
    render(<PrescriptionDetail prescriptionId={id} mode="outpatient" />);
    await screen.findByRole("heading", { name: "Chi tiết đơn thuốc" });
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
  });
  it("invalid runtime mode fails closed without inferring V1 from the DTO", async () => {
    render(<PrescriptionDetail prescriptionId={id} mode={null} />);
    await screen.findByRole("heading", { name: "Chi tiết đơn thuốc" });
    expect(screen.getByText("Chế độ xuất thuốc chưa sẵn sàng")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
  });
  it("mode changes close an old confirmation and discard its pending callback", async () => {
    let resolve!: (value: typeof slip) => void;
    mocks.dispense.mockReturnValue(new Promise(done => { resolve = done; }));
    const view = render(<PrescriptionDetail prescriptionId={id} mode="outpatient" />);
    fireEvent.click(await screen.findByRole("button", { name: "Xuất thuốc" })); submit();
    view.rerender(<PrescriptionDetail prescriptionId={id} mode="legacy" />);
    await screen.findByText("Chờ thanh toán", { exact: true });
    await act(async () => resolve(slip));
    expect(screen.queryByText(/Đã xuất thuốc thành công/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
  });
  it.each(["unpaid", "expired", "revoked"])("keeps %s clearance denial visible and never auto-retries or marks payment", async () => {
    mocks.dispense.mockRejectedValue(new ApiRequestError("No valid grant", 422, "PHARMACY_CLEARANCE_REQUIRED", [], "v1-clearance"));
    await open(); submit();
    expect(await screen.findByText(/v1-clearance/)).toHaveTextContent("Chưa có xác nhận thanh toán hợp lệ");
    await waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(2));
    expect(mocks.dispense).toHaveBeenCalledExactlyOnceWith(id);
    expect(screen.queryByText("Đã xác nhận thanh toán")).not.toBeInTheDocument();
    expect(screen.queryByText(/Đã xuất thuốc thành công/)).not.toBeInTheDocument();
    // The read cannot prove a grant; a further attempt requires a new explicit confirmation.
    fireEvent.click(screen.getByRole("button", { name: "Xuất thuốc" }));
    expect(mocks.dispense).toHaveBeenCalledTimes(1);
  });
  it.each([408, 503])("reconciles HTTP %s with a fresh read, preserves correlation, and never resubmits", async status => {
    mocks.dispense.mockRejectedValue(new ApiRequestError("Billing unavailable", status, "PHARMACY_UPSTREAM_UNAVAILABLE", [], "v1-upstream"));
    mocks.get.mockResolvedValueOnce(rx).mockRejectedValueOnce(new ApiRequestError("Read unavailable", 503)).mockResolvedValueOnce(rx);
    await open(); submit();
    expect(await screen.findByText(/Chưa tải được chi tiết mới/)).toBeVisible();
    expect(screen.getByText(/v1-upstream/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(3));
    expect(mocks.dispense).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(/Đã xuất thuốc thành công/)).not.toBeInTheDocument();
  });
  it("blocks double submit with legacy false, then refreshes the actual terminal read", async () => {
    let resolve!: (value: typeof slip) => void;
    mocks.dispense.mockReturnValue(new Promise(done => { resolve = done; }));
    mocks.get.mockResolvedValueOnce(rx).mockResolvedValue(terminal);
    await open();
    const confirm = screen.getByRole("button", { name: "Xác nhận xuất thuốc" });
    const form = confirm.closest("form")!;
    act(() => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.dispense).toHaveBeenCalledExactlyOnceWith(id);
    expect(confirm).toBeDisabled();
    await act(async () => resolve(slip));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument());
    expect(screen.getByText(/Đã xuất thuốc thành công/)).toHaveFocus();
    expect(mocks.get).toHaveBeenCalledTimes(2);
  });
});
