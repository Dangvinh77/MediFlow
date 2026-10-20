import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { storeSession } from "@/lib/session";
const mocks = vi.hoisted(() => ({ refundsDue: vi.fn(), recordRefund: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks }));
vi.mock("../api", () => ({ billingApi: mocks }));
import { RefundDueLookup } from "./RefundDueLookup";
const id = "00000000-0000-4000-8000-000000000001";
beforeEach(() => { vi.clearAllMocks(); act(() => storeSession({ accessToken: "a", refreshToken: "r", role: "CASHIER" })); });
async function lookup() { fireEvent.change(screen.getByLabelText("Mã đơn thuốc", { exact: false }), { target: { value: id } }); fireEvent.click(screen.getByRole("button", { name: "Tra cứu nghĩa vụ hoàn tiền" })); await screen.findByText("125,5 USD"); }
describe("RefundDueLookup", () => {
 it("reads exact obligations and records only an explicitly confirmed completed refund", async () => {
   mocks.refundsDue.mockResolvedValue({ prescriptionId: id, status: "APPLIED", eventType: "CANCELLED", rejectionCode: null, refundsDue: [{ originalTransactionId: id, amount: 125.5, currency: "USD" }] });
   mocks.recordRefund.mockResolvedValue({ refundTransactionId: "refund-1", status: "COMPLETED" });
   render(<RefundDueLookup />); await lookup();
   fireEvent.change(screen.getByLabelText("Số tiền đã hoàn", { exact: false }), { target: { value: "25.50" } });
   fireEvent.change(screen.getByLabelText("Lý do", { exact: false }), { target: { value: "Hủy đơn thử nghiệm" } });
   expect(screen.getByRole("button", { name: "Ghi nhận hoàn tiền" })).toBeDisabled();
   fireEvent.click(screen.getByRole("checkbox"));
   fireEvent.click(screen.getByRole("button", { name: "Ghi nhận hoàn tiền" }));
   expect(await screen.findByRole("status")).toHaveTextContent("refund-1");
   expect(mocks.recordRefund).toHaveBeenCalledWith(id, expect.objectContaining({ amount: 25.5, reason: "Hủy đơn thử nghiệm", paymentMethod: "CASH", idempotencyKey: expect.any(String) }));
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
 });
});
