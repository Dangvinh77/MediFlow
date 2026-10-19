import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import type { DrugDTO } from "../../types";
import { getLocalTodayIso } from "../../utils";
const mocks = vi.hoisted(() => ({ role: "PHARMACIST" as Role, get: vi.fn(), adjust: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
vi.mock("../../api", () => ({ pharmacyApi: { getDrug: mocks.get, adjustStock: mocks.adjust } }));
import { DrugDetail } from "./DrugDetail";

const drug: DrugDTO = { drugId: "00000000-0000-4000-8000-000000000001", drugName: "Thuốc A", activeIngredient: "Hoạt chất A", unit: "Viên", price: 1000, stockQuantity: 20, lowStockThreshold: 10, expiryDate: "2099-01-01", manufacturer: "Nhà sản xuất A", createdAt: "2026-10-12T00:00:00Z", updatedAt: "2026-10-12T00:00:00Z" };
function prepare() {
  fireEvent.change(screen.getByLabelText("Số lượng điều chỉnh", { exact: false }), { target: { value: "-5" } });
  fireEvent.change(screen.getByLabelText("Lý do"), { target: { value: "Kiểm kê" } });
  fireEvent.submit(screen.getByLabelText("Lý do").closest("form")!);
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận điều chỉnh" }));
}

describe("DrugDetail", () => {
  beforeEach(() => { mocks.role = "PHARMACIST"; mocks.get.mockReset().mockResolvedValue(drug); mocks.adjust.mockReset(); mocks.router.replace.mockReset(); });
  it("rejects invalid IDs without HTTP", () => {
    render(<DrugDetail drugId="invalid" />);
    expect(screen.getByRole("alert")).toHaveTextContent("Mã thuốc không hợp lệ");
    expect(mocks.get).not.toHaveBeenCalled();
  });
  it("shows all server fields with a read-only doctor view", async () => {
    mocks.role = "DOCTOR"; render(<DrugDetail drugId={drug.drugId} />);
    expect(await screen.findByRole("heading", { name: "Thuốc A" })).toBeVisible();
    expect(screen.getByText(drug.drugId)).toBeVisible();
    expect(screen.getByText("Nhà sản xuất A")).toBeVisible();
    expect(screen.getByRole("heading", { name: "Lịch sử bản ghi" })).toBeVisible();
    expect(screen.queryByRole("heading", { name: "Điều chỉnh tồn kho" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Tạo thuốc" })).not.toBeInTheDocument();
  });
  it.each([["2000-01-01", 0, "Hết hạn", "Hết hàng"], [getLocalTodayIso(), 4, "Sắp hết hạn", "Tồn thấp"]])("shows expiry and stock states for %s", async (expiryDate, stock, expiryLabel, stockLabel) => {
    mocks.get.mockResolvedValue({ ...drug, expiryDate, stockQuantity: Number(stock) });
    render(<DrugDetail drugId={drug.drugId} />);
    expect(await screen.findByText(expiryLabel)).toBeVisible();
    expect(screen.getByText(stockLabel)).toBeVisible();
  });
  it("shows a real 404 and correlation without a stock form", async () => {
    mocks.get.mockRejectedValue(new ApiRequestError("Missing", 404, "DRUG_NOT_FOUND", [], "corr-missing"));
    render(<DrugDetail drugId={drug.drugId} />);
    expect(await screen.findByRole("heading", { name: "Không tìm thấy thuốc" })).toBeVisible();
    expect(screen.getByText(/corr-missing/)).toBeVisible();
    expect(screen.queryByLabelText("Lý do")).not.toBeInTheDocument();
  });
  it("keeps 403 evidence without offering an unauthorized read retry", async () => {
    mocks.get.mockRejectedValueOnce(new ApiRequestError("Forbidden", 403, "FORBIDDEN", [], "corr-read"));
    render(<DrugDetail drugId={drug.drugId} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-read");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(mocks.get).toHaveBeenCalledTimes(1);
  });
  it("redirects authentication errors and ignores requests after unmount", async () => {
    let reject!: (error: unknown) => void;
    mocks.get.mockReturnValueOnce(new Promise((_, fail) => { reject = fail; })).mockRejectedValueOnce(new ApiRequestError("Hết phiên", 401));
    const view = render(<DrugDetail drugId={drug.drugId} />); view.unmount();
    await act(async () => reject(new ApiRequestError("Hết phiên", 401)));
    expect(mocks.router.replace).not.toHaveBeenCalled();
    render(<DrugDetail drugId={drug.drugId} />);
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
  });
  it("ignores a late response for the previously viewed drug", async () => {
    let resolve!: (drug: DrugDTO) => void;
    const secondId = "00000000-0000-4000-8000-000000000002";
    mocks.get.mockReturnValueOnce(new Promise(done => { resolve = done; })).mockResolvedValueOnce({ ...drug, drugId: secondId, drugName: "Thuốc B" });
    const view = render(<DrugDetail drugId={drug.drugId} />); view.rerender(<DrugDetail drugId={secondId} />);
    await screen.findByRole("heading", { name: "Thuốc B" });
    await act(async () => resolve(drug));
    expect(screen.queryByRole("heading", { name: "Thuốc A" })).not.toBeInTheDocument();
  });
  it("renders the exact stock response after adjustment", async () => {
    mocks.adjust.mockResolvedValue({ ...drug, stockQuantity: 8 });
    render(<DrugDetail drugId={drug.drugId} />); await screen.findByLabelText("Lý do"); prepare();
    expect(await screen.findByRole("status")).toHaveTextContent("8 Viên");
    expect(screen.getByText("Tồn thấp")).toBeVisible();
    expect(mocks.get).toHaveBeenCalledOnce();
  });
  it("blocks adjustment during conflict refresh, hides failed results and supports read retry", async () => {
    let reject!: (cause: unknown) => void;
    mocks.get.mockResolvedValueOnce(drug).mockReturnValueOnce(new Promise((_, fail) => { reject = fail; })).mockResolvedValueOnce({ ...drug, stockQuantity: 6 });
    mocks.adjust.mockRejectedValue(new ApiRequestError("Đã thay đổi", 409, "STOCK_BELOW_RESERVED"));
    render(<DrugDetail drugId={drug.drugId} />); await screen.findByLabelText("Lý do"); prepare();
    await waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText("Lý do")).toBeDisabled();
    await act(async () => reject(new ApiRequestError("Không tải được", 503, "UNAVAILABLE", [], "corr-refresh")));
    expect(screen.getByRole("alert")).toHaveTextContent("corr-refresh");
    expect(screen.queryByRole("heading", { name: "Thuốc A" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("6")).toBeVisible();
    expect(mocks.adjust).toHaveBeenCalledOnce();
  });
});
