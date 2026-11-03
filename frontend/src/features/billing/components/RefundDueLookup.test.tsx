import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { storeSession } from "@/lib/session";
const mocks = vi.hoisted(() => ({ refundsDue: vi.fn(), labRefundsDue: vi.fn(), recordRefund: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks }));
vi.mock("../api", () => ({ billingApi: mocks }));
import { RefundDueLookup } from "./RefundDueLookup";
const id = "00000000-0000-4000-8000-000000000001";
beforeEach(() => { vi.resetAllMocks(); act(() => storeSession({ accessToken: "a", refreshToken: "r", role: "CASHIER" })); });
function openLookup() { const summary = screen.getByText("Tra cứu khoản cần hoàn (hỗ trợ)"); if (!summary.closest("details")?.open) fireEvent.click(summary); }
async function lookup() { openLookup(); fireEvent.change(screen.getByLabelText("Mã đơn thuốc", { exact: false }), { target: { value: id } }); fireEvent.click(screen.getByRole("button", { name: "Tra cứu nghĩa vụ hoàn tiền" })); await screen.findByText("125,5 USD"); }
describe("RefundDueLookup", () => {
 it("keeps raw source lookup inside a collapsed support disclosure", () => {
   render(<RefundDueLookup />);
   const summary = screen.getByText("Tra cứu khoản cần hoàn (hỗ trợ)");
   const disclosure = summary.closest("details") as HTMLDetailsElement;
   expect(disclosure).not.toHaveAttribute("open");
   expect(disclosure.querySelector("input#refund-source-id")).not.toBeVisible();
 });

 it("reads exact obligations and records only an explicitly confirmed completed refund", async () => {
   mocks.refundsDue.mockResolvedValue({ prescriptionId: id, status: "APPLIED", eventType: "CANCELLED", rejectionCode: null, refundsDue: [{ originalTransactionId: id, amount: 125.5, currency: "USD" }] });
   mocks.recordRefund.mockResolvedValue({ refundTransactionId: "refund-1", originalTransactionId: id, accountId: "account", paymentRequestId: "request", classification: "SERVICE_PAYMENT", status: "COMPLETED", amount: 25.5, currency: "USD", paymentMethod: "CASH", completedAt: "2026-11-03T10:00:00Z" });
   render(<RefundDueLookup />); await lookup();
   fireEvent.change(screen.getByLabelText("Số tiền đã hoàn", { exact: false }), { target: { value: "25.50" } });
   fireEvent.change(screen.getByLabelText("Lý do", { exact: false }), { target: { value: "Hủy đơn thử nghiệm" } });
   expect(screen.getByRole("button", { name: "Ghi nhận hoàn tiền" })).toBeDisabled();
   fireEvent.click(screen.getByRole("checkbox"));
   fireEvent.click(screen.getByRole("button", { name: "Ghi nhận hoàn tiền" }));
   const receipt = await screen.findByText(/Đã ghi nhận hoàn tiền/);
   const receiptRegion = receipt.closest("[role=status]") as HTMLElement;
   const receiptDisclosure = within(receiptRegion).getByText("Thông tin biên nhận").closest("details") as HTMLDetailsElement;
   expect(receiptDisclosure).not.toHaveAttribute("open");
   expect(within(receiptDisclosure).getByText("refund-1")).not.toBeVisible();
   fireEvent.click(within(receiptRegion).getByText("Thông tin biên nhận"));
   expect(within(receiptRegion).getByText("refund-1")).toBeVisible();
   expect(mocks.recordRefund).toHaveBeenCalledWith(id, expect.objectContaining({ amount: 25.5, reason: "Hủy đơn thử nghiệm", paymentMethod: "CASH", idempotencyKey: expect.any(String) }));
 });
 it("looks up a Lab adjustment from its exact public source and records the same due refund", async () => {
   const labId = "00000000-0000-4000-8000-000000000002";
   mocks.labRefundsDue.mockResolvedValue({ sourceId: labId, sourceType: "LAB_TEST", eventType: "CANCELLED", status: "APPLIED", rejectionCode: null, refundsDue: [{ originalTransactionId: id, amount: 125.5, currency: "USD" }] });
   mocks.recordRefund.mockResolvedValue({ refundTransactionId: "lab-refund-1", originalTransactionId: id, accountId: "account", paymentRequestId: "request", classification: "SERVICE_PAYMENT", status: "COMPLETED", amount: 25.5, currency: "USD", paymentMethod: "CASH", completedAt: "2026-11-03T10:00:00Z" });
   render(<RefundDueLookup />);
   openLookup();
   fireEvent.change(screen.getByLabelText("Loại nguồn hoàn tiền", { exact: false }), { target: { value: "LAB_TEST" } });
   fireEvent.change(screen.getByLabelText("Mã xét nghiệm", { exact: false }), { target: { value: labId } });
   fireEvent.click(screen.getByRole("button", { name: "Tra cứu nghĩa vụ hoàn tiền" }));
   await screen.findByText("125,5 USD");

   expect(mocks.labRefundsDue).toHaveBeenCalledWith(labId);
   expect(mocks.refundsDue).not.toHaveBeenCalled();
   fireEvent.change(screen.getByLabelText("Số tiền đã hoàn", { exact: false }), { target: { value: "25.50" } });
   fireEvent.change(screen.getByLabelText("Lý do", { exact: false }), { target: { value: "Hủy chỉ định xét nghiệm" } });
   fireEvent.click(screen.getByRole("checkbox"));
   fireEvent.click(screen.getByRole("button", { name: "Ghi nhận hoàn tiền" }));

   expect(await screen.findByText(/Đã ghi nhận hoàn tiền/)).toBeVisible();
   expect(mocks.recordRefund).toHaveBeenCalledWith(id, expect.objectContaining({ amount: 25.5, reason: "Hủy chỉ định xét nghiệm", paymentMethod: "CASH", idempotencyKey: expect.any(String) }));
 });
 it("locks an ambiguous refund without issuing another write", async () => {
   mocks.refundsDue.mockResolvedValue({ prescriptionId: id, status: "APPLIED", eventType: "CANCELLED", rejectionCode: null, refundsDue: [{ originalTransactionId: id, amount: 125.5, currency: "USD" }] });
   mocks.recordRefund.mockRejectedValue(new ApiRequestError("Unknown refund", 408));
   render(<RefundDueLookup />); await lookup();
   fireEvent.change(screen.getByLabelText("Số tiền đã hoàn", { exact: false }), { target: { value: "25" } });
   fireEvent.change(screen.getByLabelText("Lý do", { exact: false }), { target: { value: "Test" } });
   fireEvent.click(screen.getByRole("checkbox"));
   const button = screen.getByRole("button", { name: "Ghi nhận hoàn tiền" });
   fireEvent.submit(button.closest("form")!);
   expect(await screen.findByRole("alert")).toHaveTextContent("Chưa xác định");
   fireEvent.submit(button.closest("form")!);
   expect(mocks.recordRefund).toHaveBeenCalledTimes(1);
   expect(screen.getByRole("button", { name: "Tra cứu nguồn khác" })).toBeDisabled();
   fireEvent.submit(screen.getByRole("button", { name: "Tra cứu nghĩa vụ hoàn tiền" }).closest("form")!);
   expect(mocks.refundsDue).toHaveBeenCalledTimes(1);
 });

 it("starts another lookup in place without keeping the previous obligation", async () => {
   mocks.refundsDue.mockResolvedValue({ prescriptionId: id, status: "APPLIED", refundsDue: [{ originalTransactionId: id, amount: 125.5, currency: "USD" }] });
   render(<RefundDueLookup />); await lookup();
   fireEvent.click(screen.getByRole("button", { name: "Tra cứu nguồn khác" }));
   expect(screen.getByLabelText("Mã đơn thuốc", { exact: false })).toHaveValue("");
   expect(screen.getByLabelText("Mã đơn thuốc", { exact: false })).toHaveFocus();
   expect(screen.queryByText("125,5 USD")).not.toBeInTheDocument();
   mocks.refundsDue.mockResolvedValue({ prescriptionId: id, status: "APPLIED", refundsDue: [] });
   fireEvent.change(screen.getByLabelText("Mã đơn thuốc", { exact: false }), { target: { value: id } });
   fireEvent.click(screen.getByRole("button", { name: "Tra cứu nghĩa vụ hoàn tiền" }));
   expect(await screen.findByText("Không có nghĩa vụ hoàn tiền trong kết quả này.")).toBeVisible();
 });

 it("refreshes obligations after a completed partial refund and preserves its receipt", async () => {
   mocks.refundsDue.mockResolvedValueOnce({ prescriptionId: id, status: "APPLIED", refundsDue: [{ originalTransactionId: id, amount: 125.5, currency: "USD" }] })
     .mockResolvedValue({ prescriptionId: id, status: "APPLIED", refundsDue: [{ originalTransactionId: id, amount: 100, currency: "USD" }] });
   mocks.recordRefund.mockResolvedValue({ refundTransactionId: "receipt-1", status: "COMPLETED", amount: 25.5, currency: "USD" });
   render(<RefundDueLookup />); await lookup();
   submitRefund();
   expect(await screen.findByText("100 USD")).toBeVisible();
   expect(screen.getByText(/Đã ghi nhận hoàn tiền/)).toBeVisible();
   screen.getAllByText("receipt-1").forEach((transactionId) => expect(transactionId).not.toBeVisible());
   expect(mocks.refundsDue).toHaveBeenCalledTimes(2);
   expect(screen.getByLabelText("Số tiền đã hoàn", { exact: false })).toHaveValue("");
   expect(screen.getByRole("checkbox")).not.toBeChecked();
   mocks.recordRefund.mockResolvedValue({ refundTransactionId: "receipt-2", status: "COMPLETED", amount: 25.5, currency: "USD" });
   submitRefund();
   expect(await screen.findByText(/receipt-2/)).not.toBeVisible();
   expect(screen.getAllByText(/Đã ghi nhận hoàn tiền/)).toHaveLength(2);
   expect(mocks.recordRefund.mock.calls[1][1].idempotencyKey).not.toBe(mocks.recordRefund.mock.calls[0][1].idempotencyKey);
 });

 it("keeps the completed receipt and retries only the read when refresh fails", async () => {
   mocks.refundsDue.mockResolvedValueOnce({ prescriptionId: id, status: "APPLIED", refundsDue: [{ originalTransactionId: id, amount: 125.5, currency: "USD" }] })
     .mockRejectedValueOnce(new ApiRequestError("Không tải được nghĩa vụ", 503));
   mocks.recordRefund.mockResolvedValue({ refundTransactionId: "receipt-1", status: "COMPLETED", amount: 25.5, currency: "USD" });
   render(<RefundDueLookup />); await lookup(); submitRefund();
   expect(await screen.findByRole("button", { name: "Tải lại nghĩa vụ hoàn tiền" })).toBeVisible();
   expect(screen.getByText(/Đã ghi nhận hoàn tiền/)).toBeVisible();
   screen.getAllByText("receipt-1").forEach((transactionId) => expect(transactionId).not.toBeVisible());
   expect(screen.getByRole("button", { name: "Ghi nhận hoàn tiền" })).toBeDisabled();
   mocks.refundsDue.mockResolvedValue({ prescriptionId: id, status: "APPLIED", refundsDue: [] });
   fireEvent.click(screen.getByRole("button", { name: "Tải lại nghĩa vụ hoàn tiền" }));
   expect(await screen.findByText("Không có nghĩa vụ hoàn tiền trong kết quả này.")).toBeVisible();
   expect(screen.getByText(/Đã ghi nhận hoàn tiền/)).toBeVisible();
   screen.getAllByText("receipt-1").forEach((transactionId) => expect(transactionId).not.toBeVisible());
   expect(mocks.recordRefund).toHaveBeenCalledTimes(1);
 });

 it("blocks switching prescriptions while a refund is pending or non-completed", async () => {
   mocks.refundsDue.mockResolvedValue({ prescriptionId: id, status: "APPLIED", refundsDue: [{ originalTransactionId: id, amount: 125.5, currency: "USD" }] });
   let finish!: (value: unknown) => void;
   mocks.recordRefund.mockReturnValue(new Promise((resolve) => { finish = resolve; }));
   render(<RefundDueLookup />); await lookup(); submitRefund();
   expect(screen.getByRole("button", { name: "Tra cứu nguồn khác" })).toBeDisabled();
   await act(async () => finish({ refundTransactionId: "pending-1", status: "PENDING" }));
   expect(screen.getByRole("button", { name: "Tra cứu nguồn khác" })).toBeDisabled();
   expect(mocks.refundsDue).toHaveBeenCalledTimes(1);
 });

 it("allows a new lookup after a definitive refund rejection", async () => {
   mocks.refundsDue.mockResolvedValue({ prescriptionId: id, status: "APPLIED", refundsDue: [{ originalTransactionId: id, amount: 125.5, currency: "USD" }] });
   mocks.recordRefund.mockRejectedValue(new ApiRequestError("Montant refusé", 409));
   render(<RefundDueLookup />); await lookup(); submitRefund();
   expect(await screen.findByRole("alert")).toHaveTextContent("Montant refusé");
   fireEvent.click(screen.getByRole("button", { name: "Tra cứu nguồn khác" }));
   expect(screen.getByLabelText("Mã đơn thuốc", { exact: false })).toBeEnabled();
   expect(screen.queryByRole("alert")).not.toBeInTheDocument();
 });
});

function submitRefund() {
 fireEvent.change(screen.getByLabelText("Số tiền đã hoàn", { exact: false }), { target: { value: "25.50" } });
 fireEvent.change(screen.getByLabelText("Lý do", { exact: false }), { target: { value: "Hủy đơn" } });
 fireEvent.click(screen.getByRole("checkbox"));
 fireEvent.click(screen.getByRole("button", { name: "Ghi nhận hoàn tiền" }));
}
