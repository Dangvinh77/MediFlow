import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { DrugDTO } from "../../types";
import type { Role } from "@/lib/roles";
const mocks = vi.hoisted(() => ({ role: "PHARMACIST" as Role, adjust: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role }));
vi.mock("../../api", () => ({ pharmacyApi: { adjustStock: mocks.adjust } }));
import { AdjustStockForm } from "./AdjustStockForm";

const drug: DrugDTO = { drugId: "00000000-0000-4000-8000-000000000001", drugName: "Thuốc A", activeIngredient: null, unit: "Viên", price: 1000, stockQuantity: 20, lowStockThreshold: 10, expiryDate: "2099-01-01", manufacturer: null, createdAt: "2026-10-12T00:00:00Z", updatedAt: "2026-10-12T00:00:00Z" };
const onSuccess = vi.fn(), onConflict = vi.fn();
function prepare(quantity = "-5", reason = " Kiểm kê ") {
  fireEvent.change(screen.getByLabelText("Số lượng điều chỉnh", { exact: false }), { target: { value: quantity } });
  fireEvent.change(screen.getByLabelText("Lý do"), { target: { value: reason } });
  fireEvent.submit(screen.getByLabelText("Lý do").closest("form")!);
}
function confirm() { fireEvent.click(screen.getByRole("button", { name: "Xác nhận điều chỉnh" })); }

describe("AdjustStockForm", () => {
  beforeEach(() => { mocks.role = "PHARMACIST"; mocks.adjust.mockReset(); mocks.router.replace.mockReset(); onSuccess.mockReset(); onConflict.mockReset(); });
  it.each(["0", "1.5", "2147483648", "-2147483649"])("rejects invalid delta %s before confirmation", value => {
    render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} />); prepare(value);
    expect(screen.getByLabelText("Số lượng điều chỉnh", { exact: false })).toHaveFocus();
    expect(screen.queryByRole("region", { name: "Xác nhận điều chỉnh" })).not.toBeInTheDocument();
    expect(mocks.adjust).not.toHaveBeenCalled();
  });
  it("requires a reason for decreases and focuses it", () => {
    render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} />); prepare("-5", " ");
    expect(screen.getByLabelText("Lý do")).toHaveFocus();
    expect(screen.getByLabelText("Lý do")).toHaveAttribute("aria-invalid", "true");
  });
  it("focuses confirmation, supports Escape/cancel, and invalidates the preview on edits", async () => {
    render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} />); prepare();
    expect(screen.getByRole("button", { name: "Xác nhận điều chỉnh" })).toHaveFocus();
    await userEvent.keyboard("{Escape}");
    expect(screen.getByLabelText("Số lượng điều chỉnh", { exact: false })).toHaveFocus();
    prepare(); fireEvent.click(screen.getByRole("button", { name: "Hủy" }));
    expect(screen.getByLabelText("Số lượng điều chỉnh", { exact: false })).toHaveFocus();
    prepare(); screen.getByLabelText("Lý do").focus();
    fireEvent.change(screen.getByLabelText("Lý do"), { target: { value: "Sửa lý do" } });
    expect(screen.queryByRole("button", { name: "Xác nhận điều chỉnh" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Lý do")).toHaveFocus();
    expect(mocks.adjust).not.toHaveBeenCalled();
  });
  it("submits once and uses the returned stock rather than the local preview", async () => {
    let resolve!: (value: DrugDTO) => void; mocks.adjust.mockReturnValue(new Promise(done => { resolve = done; }));
    render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} />); prepare(); confirm();
    fireEvent.click(screen.getByRole("button", { name: "Đang cập nhật…" }));
    expect(mocks.adjust).toHaveBeenCalledTimes(1);
    expect(mocks.adjust).toHaveBeenCalledWith(drug.drugId, { quantity: -5, reason: "Kiểm kê" });
    expect(screen.getByLabelText("Lý do")).toBeDisabled();
    const serverDrug = { ...drug, stockQuantity: 12 };
    await act(async () => resolve(serverDrug));
    expect(onSuccess).toHaveBeenCalledWith(serverDrug);
    expect(screen.getByRole("status")).toHaveTextContent("12 Viên");
    expect(screen.getByLabelText("Số lượng điều chỉnh", { exact: false })).toHaveValue(null);
  });
  it.each(["DRUG_OUT_OF_STOCK", "STOCK_BELOW_RESERVED", "CONCURRENT_UPDATE"])("refreshes after %s without automatic mutation retry", async code => {
    mocks.adjust.mockRejectedValue(new ApiRequestError("Không đủ tồn", 409, code, [], "corr-stock"));
    render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} />); prepare("-25"); confirm();
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-stock");
    expect(onConflict).toHaveBeenCalledOnce(); expect(onSuccess).not.toHaveBeenCalled();
    expect(mocks.adjust).toHaveBeenCalledOnce();
  });
  it("refreshes unknown network outcomes and requires confirmation to send again", async () => {
    mocks.adjust.mockRejectedValue(new TypeError("Network error"));
    render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} />); prepare(); confirm();
    expect(await screen.findByRole("alert")).toHaveTextContent("kiểm tra tồn kho trước khi xác nhận lại");
    expect(onConflict).toHaveBeenCalledOnce(); expect(mocks.adjust).toHaveBeenCalledOnce();
    expect(screen.queryByRole("button", { name: "Xác nhận điều chỉnh" })).not.toBeInTheDocument();
  });
  it("keeps server denial and correlation visible", async () => {
    mocks.adjust.mockRejectedValue(new ApiRequestError("Forbidden", 403, "FORBIDDEN", [], "corr-denied"));
    render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} />); prepare(); confirm();
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-denied");
    expect(screen.getByRole("button", { name: "Xem xác nhận" })).toBeDisabled();
    fireEvent.submit(screen.getByLabelText("Lý do").closest("form")!);
    expect(screen.queryByRole("button", { name: "Xác nhận điều chỉnh" })).not.toBeInTheDocument();
    expect(mocks.adjust).toHaveBeenCalledTimes(1);
    expect(onSuccess).not.toHaveBeenCalled(); expect(onConflict).not.toHaveBeenCalled();
  });
  it.each([500, 503])("refreshes and clears the old stock delta after an uncertain HTTP %s outcome", async status => {
    mocks.adjust.mockRejectedValue(new ApiRequestError("Unavailable", status, "UNAVAILABLE", [], "uncertain-stock"));
    render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} />); prepare(); confirm();
    expect(await screen.findByRole("alert")).toHaveTextContent("uncertain-stock");
    expect(onConflict).toHaveBeenCalledOnce(); expect(onSuccess).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Số lượng điều chỉnh", { exact: false })).toHaveValue(null);
    fireEvent.click(screen.getByRole("button", { name: "Xem xác nhận" }));
    expect(mocks.adjust).toHaveBeenCalledOnce();
    expect(screen.queryByRole("button", { name: "Xác nhận điều chỉnh" })).not.toBeInTheDocument();
  });
  it("redirects HTTP 401 to login", async () => {
    mocks.adjust.mockRejectedValue(new ApiRequestError("Hết phiên", 401));
    render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} />); prepare(); confirm();
    await vi.waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
  });
  it("keeps a definite validation failure editable without requiring outcome reconciliation", async () => {
    mocks.adjust.mockRejectedValue(new ApiRequestError("Lý do không hợp lệ", 422, "VALIDATION_ERROR", [{ field: "reason", message: "Cần lý do cụ thể" }], "corr-validation"));
    const onUncertain = vi.fn();
    render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} onUncertain={onUncertain} />); prepare(); confirm();
    expect(await screen.findByText(/corr-validation/)).toBeVisible();
    await vi.waitFor(() => expect(screen.getByLabelText("Lý do")).toHaveFocus());
    expect(screen.getByLabelText("Số lượng điều chỉnh", { exact: false })).toHaveValue(-5);
    expect(screen.getByLabelText("Lý do")).toBeEnabled();
    expect(onUncertain).not.toHaveBeenCalled(); expect(onConflict).not.toHaveBeenCalled();
  });
  it("blocks stale-role submissions and mutations while a refresh is pending", () => {
    const view = render(<AdjustStockForm drug={drug} onSuccess={onSuccess} onConflict={onConflict} />); prepare();
    mocks.role = "DOCTOR"; confirm(); expect(mocks.adjust).not.toHaveBeenCalled();
    mocks.role = "PHARMACIST";
    view.rerender(<AdjustStockForm drug={drug} disabled onSuccess={onSuccess} onConflict={onConflict} />);
    expect(screen.getByRole("button", { name: "Xác nhận điều chỉnh" })).toBeDisabled();
    expect(mocks.adjust).not.toHaveBeenCalled();
  });
});
