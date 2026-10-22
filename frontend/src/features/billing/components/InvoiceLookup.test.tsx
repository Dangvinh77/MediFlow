import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { formatDecimal } from "@/lib/format";
import { storeSession } from "@/lib/session";
const mocks = vi.hoisted(() => ({ byPatient: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks }));
vi.mock("../api", () => ({ billingApi: { byPatient: mocks.byPatient } }));
import { InvoiceLookup } from "./InvoiceLookup";
const patient = "00000000-0000-4000-8000-000000000001";
const row = { invoiceId: "invoice-1", patientId: patient, createdDate: "2026-10-20", totalAmount: 125.75, isPaid: true, paymentMethod: "CASH", prescriptionId: null, sagaStatus: "COMPLETED", paidAt: null, fees: [] };
function search() { fireEvent.change(screen.getByLabelText("Mã bệnh nhân"), { target: { value: patient } }); fireEvent.click(screen.getByRole("button", { name: "Tra cứu hóa đơn" })); }
beforeEach(() => { vi.clearAllMocks(); act(() => storeSession({ accessToken: "a", refreshToken: "r", role: "CASHIER" })); });
describe("InvoiceLookup", () => {
 it("uses the submitted patient for pagination and shows server amounts and terminal state", async () => {
   mocks.byPatient.mockResolvedValueOnce({ content: [row], number: 0, totalPages: 2, totalElements: 21 }).mockResolvedValueOnce({ content: [{ ...row, invoiceId: "invoice-2" }], number: 1, totalPages: 2, totalElements: 21 });
   render(<InvoiceLookup />); search();
   expect(await screen.findByText(formatDecimal(125.75))).toBeVisible();
   expect(screen.getByText("Hoàn tất")).toBeVisible();
   fireEvent.change(screen.getByLabelText("Mã bệnh nhân"), { target: { value: "unsubmitted-draft" } });
   fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
   expect(await screen.findByRole("link", { name: /Hóa đơn/ })).toHaveAttribute("href", "/billing/invoice-2");
   expect(mocks.byPatient).toHaveBeenLastCalledWith(patient, 1, 20);
   expect(screen.getByRole("button", { name: "Trang sau" })).toBeDisabled();
 });
 it("retries the failed query and renders an empty result", async () => {
   mocks.byPatient.mockRejectedValueOnce(new ApiRequestError("Unavailable", 503, undefined, [], "trace-read")).mockResolvedValueOnce({ content: [], number: 0, totalPages: 0, totalElements: 0 });
   render(<InvoiceLookup />); search();
   expect(await screen.findByText("trace-read")).toBeVisible();
   fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
   expect(await screen.findByText("Bệnh nhân chưa có hóa đơn.")).toBeVisible();
   expect(mocks.byPatient).toHaveBeenLastCalledWith(patient, 0, 20);
 });
 it("rejects invalid patient ids without issuing a read", async () => {
   render(<InvoiceLookup />);
   fireEvent.change(screen.getByLabelText("Mã bệnh nhân"), { target: { value: "invalid" } });
   fireEvent.click(screen.getByRole("button", { name: "Tra cứu hóa đơn" }));
   expect(await screen.findByRole("alert")).toHaveTextContent("UUID");
   expect(mocks.byPatient).not.toHaveBeenCalled();
 });
});
