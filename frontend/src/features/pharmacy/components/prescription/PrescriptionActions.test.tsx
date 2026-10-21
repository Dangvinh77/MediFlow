import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import type { PrescriptionDTO } from "../../types";

const mocks = vi.hoisted(() => ({ role: "ADMIN" as Role, get: vi.fn(), cancel: vi.fn(), dispense: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
vi.mock("../../api", () => ({ pharmacyApi: { getPrescription: mocks.get, cancelPrescription: mocks.cancel, dispensePrescription: mocks.dispense } }));
import { PrescriptionDetail } from "./PrescriptionDetail";

const id = "00000000-0000-4000-8000-000000000001";
const prescription: PrescriptionDTO = { prescriptionId: id, recordId: id, patientId: id, doctorId: id, departmentId: id,
  prescribedDate: "2024-02-29", totalAmount: 123456, lines: [], status: "ACTIVE", dispenseStatus: "PENDING", paymentConfirmed: true,
  cancelledAt: null, cancelledBy: null, cancellationReason: null, createdAt: "2024-02-29T00:00:00Z", updatedAt: "2024-02-29T00:00:00Z" };
const cancelled = { ...prescription, status: "CANCELLED", dispenseStatus: "CANCELLED" } as const;
const dispensed = { dispenseId: id, prescriptionId: id, status: "DISPENSED", dispensedAt: "2026-10-16T00:00:00Z", dispensedBy: id, failureReason: null } as const;
async function open(action: "cancel" | "dispense") {
  render(<PrescriptionDetail prescriptionId={id} mode="legacy" />);
  const trigger = await screen.findByRole("button", { name: action === "cancel" ? "Hủy đơn" : "Xuất thuốc" });
  trigger.focus();
  // Browsers can blur a trigger as soon as opening confirmation disables it.
  trigger.addEventListener("click", () => trigger.blur(), { once: true });
  fireEvent.click(trigger); return trigger;
}
function submit(action: "cancel" | "dispense") {
  fireEvent.submit(screen.getByRole("button", { name: action === "cancel" ? "Xác nhận hủy đơn" : "Xác nhận xuất thuốc" }).closest("form")!);
}

describe("Prescription terminal actions", () => {
  beforeEach(() => {
    mocks.role = "ADMIN"; mocks.get.mockReset().mockResolvedValue(prescription);
    mocks.cancel.mockReset(); mocks.dispense.mockReset(); mocks.router.replace.mockReset();
  });
  it("uses inline cancellation confirmation, validates reason and returns focus with Escape", async () => {
    const trigger = await open("cancel"); const reason = screen.getByLabelText("Lý do hủy", { exact: false });
    expect(reason).toHaveFocus(); expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    submit("cancel"); expect(reason).toHaveAttribute("aria-invalid", "true"); expect(mocks.cancel).not.toHaveBeenCalled();
    fireEvent.keyDown(reason, { key: "Escape" });
    await waitFor(() => expect(trigger).toHaveFocus()); expect(screen.queryByText("Xác nhận hủy đơn thuốc")).not.toBeInTheDocument();
  });
  it.each([0, 3])("sends one trimmed cancel intent, then displays the server's %s released reservations", async releasedReservations => {
    let resolve!: (value: unknown) => void;
    mocks.cancel.mockReturnValue(new Promise(done => { resolve = done; }));
    mocks.get.mockResolvedValueOnce(prescription).mockResolvedValue(cancelled);
    await open("cancel"); const reason = screen.getByLabelText("Lý do hủy", { exact: false });
    fireEvent.change(reason, { target: { value: " Theo yêu cầu bệnh nhân " } });
    const form = reason.closest("form")!;
    act(() => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.cancel).toHaveBeenCalledTimes(1); expect(mocks.cancel).toHaveBeenCalledWith(id, { reason: "Theo yêu cầu bệnh nhân" });
    expect(reason).toBeDisabled(); fireEvent.keyDown(reason, { key: "Escape" });
    expect(screen.getByText("Xác nhận hủy đơn thuốc")).toBeVisible();
    await act(async () => resolve({ prescriptionId: id, status: "CANCELLED", releasedReservations, cancelledAt: "2026-10-16T00:00:00Z" }));
    expect(await screen.findByText(`Đã hủy đơn thuốc. Đã giải phóng ${releasedReservations} lượt giữ tồn kho.`)).toBeVisible();
    await waitFor(() => expect(screen.queryByRole("button", { name: "Hủy đơn" })).not.toBeInTheDocument());
    expect(screen.queryByText(/hủy trước đó/)).not.toBeInTheDocument();
  });
  it("maps server reason errors, retains input and focuses after the pending state", async () => {
    mocks.cancel.mockRejectedValue(new ApiRequestError("Không hợp lệ", 400, "VALIDATION_ERROR", [{ field: "reason", message: "Kiểm tra lý do" }], "cancel-validation"));
    await open("cancel"); const reason = screen.getByLabelText("Lý do hủy", { exact: false });
    fireEvent.change(reason, { target: { value: "Lý do đang nhập" } }); submit("cancel");
    await waitFor(() => expect(reason).toHaveAttribute("aria-invalid", "true")); expect(reason).toHaveFocus();
    expect(reason).toHaveValue("Lý do đang nhập"); expect(screen.getByText(/cancel-validation/)).toBeVisible();
  });
  it("offers dispense only for the pharmacist/admin role plus active/pending and confirmed payment", async () => {
    mocks.role = "DOCTOR"; const view = render(<PrescriptionDetail prescriptionId={id} mode="legacy" />);
    await screen.findByRole("button", { name: "Hủy đơn" });
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
    view.unmount(); mocks.role = "PHARMACIST"; mocks.get.mockResolvedValue({ ...prescription, paymentConfirmed: false });
    render(<PrescriptionDetail prescriptionId={id} mode="legacy" />); await screen.findByText("Chờ thanh toán", { exact: true });
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Hủy đơn" })).not.toBeInTheDocument();
  });
  it("focuses a safe close control in dispense confirmation and returns to its trigger", async () => {
    const trigger = await open("dispense"); const close = screen.getByRole("button", { name: "Đóng" });
    expect(close).toHaveFocus(); fireEvent.keyDown(close, { key: "Escape" });
    await waitFor(() => expect(trigger).toHaveFocus()); expect(mocks.dispense).not.toHaveBeenCalled();
  });
  it("sends the bodyless dispense once, blocks old actions until read settles and uses the new terminal state", async () => {
    let resolve!: (value: unknown) => void; let resolveRead!: (value: PrescriptionDTO) => void;
    mocks.dispense.mockReturnValue(new Promise(done => { resolve = done; }));
    mocks.get.mockResolvedValueOnce(prescription).mockReturnValueOnce(new Promise(done => { resolveRead = done; }));
    await open("dispense"); const form = screen.getByRole("button", { name: "Xác nhận xuất thuốc" }).closest("form")!;
    act(() => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.dispense).toHaveBeenCalledExactlyOnceWith(id);
    await act(async () => resolve(dispensed));
    expect(screen.getByText(/Đã xuất thuốc thành công/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Xuất thuốc" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Hủy đơn" })).toBeDisabled();
    await act(async () => resolveRead({ ...prescription, status: "FULFILLED", dispenseStatus: "DISPENSED" }));
    expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument();
  });
  it.each(["cancel", "dispense"] as const)("preserves successful %s evidence when the follow-up read fails; retry only reads", async action => {
    mocks.get.mockResolvedValueOnce(prescription).mockRejectedValueOnce(new ApiRequestError("Read unavailable", 503, "READ_FAILED", [], "read-after-action"))
      .mockResolvedValueOnce(action === "cancel" ? cancelled : { ...prescription, status: "FULFILLED", dispenseStatus: "DISPENSED" });
    mocks.cancel.mockResolvedValue({ prescriptionId: id, status: "CANCELLED", releasedReservations: 2 });
    mocks.dispense.mockResolvedValue(dispensed); await open(action);
    if (action === "cancel") fireEvent.change(screen.getByLabelText("Lý do hủy", { exact: false }), { target: { value: "Hủy theo yêu cầu" } });
    submit(action); expect(await screen.findByText(/Chưa tải được chi tiết mới/)).toHaveTextContent("read-after-action");
    expect(screen.getByText(action === "cancel" ? /Đã hủy đơn thuốc. Đã giải phóng 2/ : /Đã xuất thuốc thành công/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" })); await waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(3));
    expect(action === "cancel" ? mocks.cancel : mocks.dispense).toHaveBeenCalledTimes(1);
  });
  it.each(["cancel", "dispense"] as const)("refreshes on %s conflict 409 and waits for a fresh state before another action", async action => {
    (action === "cancel" ? mocks.cancel : mocks.dispense).mockRejectedValue(new ApiRequestError("Concurrent state", 409, "CONCURRENT_MODIFICATION", [], "race-rx"));
    mocks.get.mockResolvedValueOnce(prescription).mockResolvedValue(cancelled); await open(action);
    if (action === "cancel") fireEvent.change(screen.getByLabelText("Lý do hủy", { exact: false }), { target: { value: "Hủy theo yêu cầu" } });
    submit(action); expect(await screen.findByText(/race-rx/)).toBeVisible();
    await waitFor(() => expect(screen.queryByRole("button", { name: "Hủy đơn" })).not.toBeInTheDocument());
    expect(action === "cancel" ? mocks.cancel : mocks.dispense).toHaveBeenCalledTimes(1);
  });
  it.each(["cancel", "dispense"] as const)("refreshes the unknown %s outcome without declaring success or failure", async action => {
    (action === "cancel" ? mocks.cancel : mocks.dispense).mockRejectedValue(new TypeError("Network timeout"));
    mocks.get.mockResolvedValueOnce(prescription).mockResolvedValue(cancelled); await open(action);
    if (action === "cancel") fireEvent.change(screen.getByLabelText("Lý do hủy", { exact: false }), { target: { value: "Hủy theo yêu cầu" } });
    submit(action); expect(await screen.findByText(/Chưa xác định được kết quả/)).toBeVisible();
    await waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(2));
    expect(screen.queryByText(/Đã xuất thuốc thành công|Đã hủy đơn thuốc\. Đã giải phóng/)).not.toBeInTheDocument();
    expect(action === "cancel" ? mocks.cancel : mocks.dispense).toHaveBeenCalledTimes(1);
  });
  it.each(["cancel", "dispense"] as const)("reconciles HTTP 408 for %s and retries only a failed follow-up read", async action => {
    const mutation = action === "cancel" ? mocks.cancel : mocks.dispense;
    mutation.mockRejectedValue(new ApiRequestError("Timeout", 408, "REQUEST_TIMEOUT", [], "terminal-timeout"));
    mocks.get.mockResolvedValueOnce(prescription)
      .mockRejectedValueOnce(new ApiRequestError("Unavailable", 503, "READ_FAILED", [], "terminal-read"))
      .mockResolvedValueOnce(action === "cancel" ? cancelled : { ...prescription, status: "FULFILLED", dispenseStatus: "DISPENSED" });
    await open(action);
    if (action === "cancel") fireEvent.change(screen.getByLabelText("Lý do hủy", { exact: false }), { target: { value: "Hủy theo yêu cầu" } });
    submit(action);
    expect(await screen.findByText(/Chưa tải được chi tiết mới/)).toHaveTextContent("terminal-read");
    expect(screen.getByText(/Chưa xác định được kết quả/)).toHaveTextContent("terminal-timeout");
    expect(screen.queryByRole("button", { name: /Xác nhận (hủy đơn|xuất thuốc)/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/Đã xuất thuốc thành công|Đã hủy đơn thuốc\. Đã giải phóng/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument());
    expect(mutation).toHaveBeenCalledTimes(1);
  });
  it.each(["PAYMENT_PROOF_REQUIRED", "PHARMACY_CLEARANCE_REQUIRED"])("refreshes a rejected %s and removes dispense when payment is no longer confirmed", async code => {
    mocks.dispense.mockRejectedValue(new ApiRequestError("No proof", 422, code, [], "proof-rx"));
    mocks.get.mockResolvedValueOnce(prescription).mockResolvedValue({ ...prescription, paymentConfirmed: false });
    await open("dispense"); submit("dispense"); expect(await screen.findByText(/proof-rx/)).toBeVisible();
    await waitFor(() => expect(screen.queryByRole("button", { name: "Xuất thuốc" })).not.toBeInTheDocument());
  });
  it("does not claim a failed dispense was committed from an error code alone", async () => {
    mocks.dispense.mockRejectedValue(new ApiRequestError("Drug expired", 422, "DRUG_EXPIRED", [], "expiry-rx"));
    await open("dispense"); submit("dispense"); const message = await screen.findByText(/expiry-rx/);
    expect(message).toHaveTextContent("kiểm tra kết quả"); expect(message).not.toHaveTextContent("đã ghi nhận");
    expect(mocks.get).toHaveBeenCalledTimes(2);
  });
  it.each(["cancel", "dispense"] as const)("keeps %s forbidden denial visible and blocks another submission", async action => {
    (action === "cancel" ? mocks.cancel : mocks.dispense).mockRejectedValue(new ApiRequestError("Denied", 403, "FORBIDDEN", [], "forbidden-rx"));
    await open(action); if (action === "cancel") fireEvent.change(screen.getByLabelText("Lý do hủy", { exact: false }), { target: { value: "Hủy theo yêu cầu" } });
    submit(action); expect(await screen.findByText(/forbidden-rx/)).toHaveTextContent("không có quyền");
    expect(screen.getByRole("button", { name: action === "cancel" ? "Xác nhận hủy đơn" : "Xác nhận xuất thuốc" })).toBeDisabled();
    submit(action); expect(action === "cancel" ? mocks.cancel : mocks.dispense).toHaveBeenCalledTimes(1);
  });
  it.each(["cancel", "dispense"] as const)("redirects %s on expired session without retry", async action => {
    (action === "cancel" ? mocks.cancel : mocks.dispense).mockRejectedValue(new ApiRequestError("Expired", 401));
    await open(action); if (action === "cancel") fireEvent.change(screen.getByLabelText("Lý do hủy", { exact: false }), { target: { value: "Hủy theo yêu cầu" } });
    submit(action); await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
    expect(action === "cancel" ? mocks.cancel : mocks.dispense).toHaveBeenCalledTimes(1);
  });
  it("ignores a late old mutation after navigating to another prescription", async () => {
    let resolve!: (value: unknown) => void; mocks.dispense.mockReturnValue(new Promise(done => { resolve = done; }));
    const view = render(<PrescriptionDetail prescriptionId={id} mode="legacy" />);
    fireEvent.click(await screen.findByRole("button", { name: "Xuất thuốc" })); submit("dispense");
    const next = "00000000-0000-4000-8000-000000000002";
    mocks.get.mockResolvedValue({ ...cancelled, prescriptionId: next }); view.rerender(<PrescriptionDetail prescriptionId={next} mode="legacy" />);
    await screen.findByText(next, { selector: "p" }); await act(async () => resolve(dispensed));
    expect(screen.queryByText(/Đã xuất thuốc thành công/)).not.toBeInTheDocument(); expect(mocks.get).toHaveBeenCalledTimes(2);
  });
  it("does not request a malformed prescription UUID", () => {
    render(<PrescriptionDetail prescriptionId="bad-id" mode="legacy" />); expect(screen.getByRole("alert")).toHaveTextContent("UUID hợp lệ"); expect(mocks.get).not.toHaveBeenCalled();
  });
});
