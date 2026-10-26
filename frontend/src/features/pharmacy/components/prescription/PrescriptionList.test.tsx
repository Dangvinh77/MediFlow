import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { ApiRequestError } from "@/lib/api";
const mocks = vi.hoisted(() => ({ query: "", search: vi.fn(), patients: vi.fn(), patient: vi.fn(), current: () => true }));
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(mocks.query) }));
vi.mock("../../api", () => ({ pharmacyApi: { searchPrescriptions: mocks.search, searchPatients: mocks.patients, getPatientIdentity: mocks.patient } }));
vi.mock("../PharmacySessionBoundary", () => ({ PharmacySessionBoundary: ({ children }: { children: ReactNode }) => children, usePharmacyRequestScope: () => mocks.current }));
vi.mock("../PharmacyIdentity", () => ({ PharmacyIdentity: () => <span>Nguyễn Văn An Demo</span> }));
vi.mock("./PrescriptionLookup", () => ({ PrescriptionLookup: () => <span>Tra cứu bổ sung</span> }));
import { PrescriptionList } from "./PrescriptionList";
const rx = { prescriptionId: "demo-rx", patientId: "patient", prescribedDate: "2026-10-25", totalAmount: 6000,
  status: "ACTIVE", dispenseStatus: "PENDING", paymentConfirmed: true, lines: [{ lineId: "line", drugName: "Omeprazol 20mg", quantity: 2 }] };
const page = { content: [rx], totalElements: 1, totalPages: 1, number: 0, size: 20 };
const patientId = "00000000-0000-4000-8000-000000000001";
describe("prescription list", () => {
  beforeEach(() => {
    mocks.query = "";
    vi.spyOn(window.history, "pushState").mockImplementation(() => {});
    mocks.search.mockReset().mockResolvedValue(page);
    mocks.patients.mockReset().mockResolvedValue({ content: [
      { maBenhNhan: patientId, hoTen: "Nguyễn An", ngaySinh: "1990-01-01" },
      { maBenhNhan: "00000000-0000-4000-8000-000000000002", hoTen: "Nguyễn An", ngaySinh: "2000-02-02" },
    ], number: 0, totalPages: 1 });
    mocks.patient.mockReset().mockResolvedValue({ maBenhNhan: patientId, hoTen: "Nguyễn An", ngaySinh: "1990-01-01" });
  });
  it("loads all prescriptions immediately and opens detail without entering an ID", async () => {
    render(<PrescriptionList />);
    expect(await screen.findByText("Nguyễn Văn An Demo")).toBeVisible();
    expect(screen.getByRole("link", { name: "Xem đơn" })).toHaveAttribute("href", "/pharmacy/prescriptions/demo-rx");
    expect(screen.getByText("Omeprazol 20mg × 2")).toBeVisible();
    expect(mocks.search).toHaveBeenCalledWith({ patientId: "", status: "", page: 0, size: 20 });
  });
  it("filters on the server and requests another page", async () => {
    mocks.search.mockResolvedValue({ ...page, totalElements: 21, totalPages: 2 });
    render(<PrescriptionList />); await screen.findByText("Nguyễn Văn An Demo");
    fireEvent.change(screen.getByLabelText("Tìm theo tên bệnh nhân"), { target: { value: "Nguyễn An" } });
    fireEvent.click(screen.getByRole("button", { name: "Tìm lựa chọn" }));
    expect(await screen.findByRole("button", { name: "Nguyễn An · 02/02/2000" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Nguyễn An · 01/01/1990" }));
    await waitFor(() => expect(mocks.search).toHaveBeenLastCalledWith({ patientId, status: "", page: 0, size: 20 }));
    expect(mocks.patients).toHaveBeenCalledWith("Nguyễn An", 0);
    fireEvent.change(screen.getByLabelText("Trạng thái đơn"), { target: { value: "CANCELLED" } });
    await waitFor(() => expect(mocks.search).toHaveBeenLastCalledWith({ patientId, status: "CANCELLED", page: 0, size: 20 }));
    await screen.findByRole("link", { name: "Xem đơn" });
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    await waitFor(() => expect(mocks.search).toHaveBeenLastCalledWith({ patientId, status: "CANCELLED", page: 1, size: 20 }));
    fireEvent.click(screen.getByRole("button", { name: "Xem tất cả" }));
    await waitFor(() => expect(mocks.search).toHaveBeenLastCalledWith({ patientId: "", status: "", page: 0, size: 20 }));
  });
  it("does not display failed reads as an empty list and can retry", async () => {
    mocks.search.mockRejectedValueOnce(new Error("offline"));
    render(<PrescriptionList />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải");
    expect(screen.queryByText(/Chưa có đơn thuốc/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("link", { name: "Xem đơn" })).toBeVisible();
  });
  it("keeps a forbidden read closed", async () => {
    mocks.search.mockRejectedValue(new ApiRequestError("Denied",403));
    render(<PrescriptionList />); await screen.findByRole("alert");
    expect(screen.getByRole("button", { name: "Tìm lựa chọn" })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
  });
  it("does not turn typed patient names or failed searches into patient identifiers", async () => {
    mocks.patients.mockRejectedValueOnce(new Error("Không thể tìm bệnh nhân"));
    render(<PrescriptionList />); await screen.findByRole("link", { name: "Xem đơn" });
    fireEvent.change(screen.getByLabelText("Tìm theo tên bệnh nhân"), { target: { value: "Nguyễn An" } });
    fireEvent.click(screen.getByRole("button", { name: "Tìm lựa chọn" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tìm bệnh nhân");
    expect(mocks.search.mock.calls.every(([params]) => params.patientId === "")).toBe(true);
  });
  it("ignores a late old result after changing filters", async () => {
    let resolve!: (value: typeof page) => void;
    mocks.search.mockReturnValueOnce(new Promise(done => { resolve = done; }))
      .mockResolvedValueOnce({ ...page, content: [], totalElements: 0, totalPages: 0 });
    render(<PrescriptionList />);
    fireEvent.change(screen.getByLabelText("Trạng thái đơn"), { target: { value: "CANCELLED" } });
    await screen.findByText(/Không có đơn thuốc phù hợp/);
    await act(async () => resolve(page));
    expect(screen.queryByRole("link", { name: "Xem đơn" })).not.toBeInTheDocument();
  });
  it("restores submitted patient, status and page from a returning URL", async () => {
    mocks.query = `patientId=${patientId}&status=ACTIVE&page=1`;
    mocks.search.mockResolvedValue({ ...page, number: 1, totalPages: 2, totalElements: 21 });
    const view = render(<PrescriptionList />);
    await waitFor(() => expect(mocks.search).toHaveBeenLastCalledWith({ patientId, status: "ACTIVE", page: 1, size: 20 }));
    mocks.query = "status=CANCELLED"; view.rerender(<PrescriptionList />);
    await waitFor(() => expect(mocks.search).toHaveBeenLastCalledWith({ patientId: "", status: "CANCELLED", page: 0, size: 20 }));
  });
  it("clears typed but unselected names when returning to all results", async () => {
    render(<PrescriptionList />); await screen.findByRole("link", { name: "Xem đơn" });
    fireEvent.change(screen.getByLabelText("Tìm theo tên bệnh nhân"), { target: { value: "Unselected name" } });
    fireEvent.click(screen.getByRole("button", { name: "Xem tất cả" }));
    expect(screen.getByLabelText("Tìm theo tên bệnh nhân")).toHaveValue("");
    expect(window.history.pushState).toHaveBeenLastCalledWith(null, "", "/pharmacy/prescriptions");
  });
});
