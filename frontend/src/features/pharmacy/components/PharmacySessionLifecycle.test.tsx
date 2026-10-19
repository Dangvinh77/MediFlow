import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { clearSession, storeSession } from "@/lib/session";
import type { PageResult } from "@/lib/types";
import type { Role } from "@/lib/roles";
import type { DrugDTO, PrescriptionDTO } from "../types";

const mocks = vi.hoisted(() => ({ search: vi.fn(), drug: vi.fn(), rx: vi.fn(), create: vi.fn(), stock: vi.fn(), replay: vi.fn(), cancel: vi.fn(), dispense: vi.fn(), router: { replace: vi.fn(), push: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router, usePathname: () => "/pharmacy/drugs", useSearchParams: () => new URLSearchParams() }));
vi.mock("../api", () => ({ pharmacyApi: { searchDrugs: mocks.search, getDrug: mocks.drug, getPrescription: mocks.rx, createDrug: mocks.create, adjustStock: mocks.stock, replayOutbox: mocks.replay, cancelPrescription: mocks.cancel, dispensePrescription: mocks.dispense } }));
import { DrugCatalog } from "./drug/DrugCatalog";
import { DrugDetail } from "./drug/DrugDetail";
import { CreateDrugForm } from "./drug/CreateDrugForm";
import { AdjustStockForm } from "./drug/AdjustStockForm";
import { PrescriptionDetail } from "./prescription/PrescriptionDetail";
import { PrescriptionLookup } from "./prescription/PrescriptionLookup";
import { OutboxReplayForm } from "./outbox/OutboxReplayForm";

const id = "00000000-0000-4000-8000-000000000001";
const drug: DrugDTO = { drugId: id, drugName: "Thuốc phiên A", activeIngredient: null, unit: "Viên", price: 1000, stockQuantity: 20, lowStockThreshold: 10, expiryDate: "2099-01-01", manufacturer: null, createdAt: "2026-10-18T00:00:00Z", updatedAt: "2026-10-18T00:00:00Z" };
const rx: PrescriptionDTO = { prescriptionId: id, recordId: id, patientId: id, doctorId: id, departmentId: id, prescribedDate: "2026-10-18", totalAmount: 12345, lines: [], status: "ACTIVE", dispenseStatus: "PENDING", paymentConfirmed: true, cancelledAt: null, cancelledBy: null, cancellationReason: null, createdAt: drug.createdAt, updatedAt: drug.updatedAt };
const page = (name: string): PageResult<DrugDTO> => ({ content: [{ ...drug, drugName: name }], totalElements: 1, totalPages: 1, number: 0, size: 20 });
function session(token = "session-a", role: Role = "ADMIN") { storeSession({ accessToken: token, refreshToken: "test", role }); }
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (cause: unknown) => void; const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; }); return { promise, resolve, reject }; }
function change(label: string, value: string) { fireEvent.change(screen.getByLabelText(label, { exact: false }), { target: { value } }); }

describe("Pharmacy session lifecycle", () => {
  beforeEach(() => { vi.clearAllMocks(); session(); });
  afterEach(() => clearSession());

  it("removes catalogue results and search drafts on same-role account change", async () => {
    const next = deferred<PageResult<DrugDTO>>(); mocks.search.mockResolvedValueOnce(page("Thuốc phiên A")).mockReturnValueOnce(next.promise);
    render(<DrugCatalog />); await screen.findByRole("link", { name: "Thuốc phiên A" });
    change("Tìm theo tên thuốc", "private draft");
    act(() => session("session-b"));
    expect(screen.queryByText("Thuốc phiên A")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Tìm theo tên thuốc")).toHaveValue("");
    await act(async () => next.resolve(page("Thuốc phiên B")));
    expect(await screen.findByRole("link", { name: "Thuốc phiên B" })).toBeVisible();
  });

  it("ignores an obsolete catalogue 401 rather than redirecting the new account", async () => {
    const old = deferred<PageResult<DrugDTO>>(); mocks.search.mockReturnValueOnce(old.promise).mockResolvedValueOnce(page("Thuốc mới"));
    render(<DrugCatalog />); act(() => session("session-b")); await screen.findByText("Thuốc mới");
    await act(async () => old.reject(new ApiRequestError("old session", 401)));
    expect(mocks.router.replace).not.toHaveBeenCalled();
    expect(screen.getByText("Thuốc mới")).toBeVisible();
  });

  it("drops a paid prescription and its open cancellation when the account changes", async () => {
    mocks.rx.mockResolvedValueOnce(rx).mockReturnValueOnce(new Promise(() => {}));
    render(<PrescriptionDetail prescriptionId={id} />); await screen.findByText("Đã xác nhận thanh toán");
    fireEvent.click(screen.getByRole("button", { name: "Hủy đơn" })); change("Lý do hủy", "private reason");
    act(() => session("session-b"));
    expect(screen.queryByText("Đã xác nhận thanh toán")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Lý do hủy", { exact: false })).not.toBeInTheDocument();
    await waitFor(() => expect(mocks.rx).toHaveBeenCalledTimes(2));
  });

  it("ignores prescription cancellation success belonging to the old session", async () => {
    const pending = deferred<unknown>(); mocks.rx.mockResolvedValueOnce(rx).mockReturnValueOnce(new Promise(() => {})); mocks.cancel.mockReturnValue(pending.promise);
    render(<PrescriptionDetail prescriptionId={id} />); fireEvent.click(await screen.findByRole("button", { name: "Hủy đơn" }));
    change("Lý do hủy", "Test"); fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy đơn" }));
    act(() => session("session-b")); await act(async () => pending.resolve({ prescriptionId: id, status: "CANCELLED", releasedReservations: 1 }));
    expect(screen.queryByText(/Đã hủy đơn thuốc/)).not.toBeInTheDocument();
    expect(mocks.rx).toHaveBeenCalledTimes(2);
  });

  it("drops drug detail and does not accept a late read after logout", async () => {
    const old = deferred<DrugDTO>(); mocks.drug.mockReturnValue(old.promise);
    render(<DrugDetail drugId={id} />); act(() => clearSession());
    await act(async () => old.resolve(drug));
    expect(screen.queryByRole("heading", { name: drug.drugName })).not.toBeInTheDocument();
    expect(mocks.drug).toHaveBeenCalledTimes(1);
  });

  it("does not navigate after create-drug completes for an obsolete account", async () => {
    const pending = deferred<DrugDTO>(); mocks.create.mockReturnValue(pending.promise);
    render(<CreateDrugForm />); change("Tên thuốc", "Private draft"); change("Đơn vị", "Viên"); change("Giá thuốc", "1000"); change("Tồn kho ban đầu", "20"); change("Hạn sử dụng", "2099-01-01");
    fireEvent.submit(screen.getByLabelText("Tên thuốc", { exact: false }).closest("form")!);
    act(() => session("session-b")); await act(async () => pending.resolve(drug));
    expect(mocks.router.replace).not.toHaveBeenCalled(); expect(screen.getByLabelText("Tên thuốc", { exact: false })).toHaveValue("");
  });

  it("does not apply old stock results to the new account", async () => {
    const pending = deferred<DrugDTO>(); const onSuccess = vi.fn(), onConflict = vi.fn(); mocks.stock.mockReturnValue(pending.promise);
    render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} />);
    change("Số lượng điều chỉnh", "5"); fireEvent.click(screen.getByRole("button", { name: "Xem xác nhận" })); fireEvent.click(screen.getByRole("button", { name: "Xác nhận điều chỉnh" }));
    act(() => session("session-b")); await act(async () => pending.resolve({ ...drug, stockQuantity: 25 }));
    expect(onSuccess).not.toHaveBeenCalled(); expect(onConflict).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Số lượng điều chỉnh", { exact: false })).toHaveValue(null);
  });

  it("drops pending outbox results when switching between ADMIN accounts", async () => {
    const pending = deferred<unknown>(); mocks.replay.mockReturnValue(pending.promise);
    render(<OutboxReplayForm />); change("Event ID", id); fireEvent.click(screen.getByRole("button", { name: "Xem xác nhận" })); fireEvent.click(screen.getByRole("button", { name: "Xác nhận replay" }));
    act(() => session("session-b")); await act(async () => pending.resolve({ eventId: id, replayed: true }));
    expect(screen.queryByText("Đã đưa event trở lại hàng đợi replay")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Event ID")).toHaveValue("");
  });

  it("clears the prescription lookup draft across same-role sessions", () => {
    render(<PrescriptionLookup />); change("Mã đơn thuốc", id); act(() => session("session-b"));
    expect(screen.getByLabelText("Mã đơn thuốc")).toHaveValue("");
    expect(mocks.router.push).not.toHaveBeenCalled();
  });
});
