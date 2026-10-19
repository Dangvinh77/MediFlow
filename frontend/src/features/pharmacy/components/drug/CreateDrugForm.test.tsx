import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
const mocks = vi.hoisted(() => ({ role: "PHARMACIST" as Role | null, create: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
vi.mock("../../api", () => ({ pharmacyApi: { createDrug: mocks.create } }));
import { CreateDrugForm } from "./CreateDrugForm";

function fill(label: string, value: string) { fireEvent.change(screen.getByLabelText(label, { exact: false }), { target: { value } }); }
function validForm() {
  fill("Tên thuốc", "  Thuốc mới  "); fill("Đơn vị", " Viên ");
  fill("Giá thuốc", "1234.50"); fill("Tồn kho ban đầu", "20"); fill("Hạn sử dụng", "2099-01-01");
}
function submit() { fireEvent.submit(screen.getByLabelText("Tên thuốc", { exact: false }).closest("form")!); }

describe("CreateDrugForm", () => {
  it("locks a server-denied form even when submitted directly again", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Denied", 403, "FORBIDDEN", [], "create-denied"));
    render(<CreateDrugForm />); validForm(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("create-denied");
    expect(screen.getByRole("button", { name: "Tạo thuốc" })).toBeDisabled();
    submit(); expect(mocks.create).toHaveBeenCalledTimes(1);
  });
  beforeEach(() => { mocks.role = "PHARMACIST"; mocks.create.mockReset(); mocks.router.replace.mockReset(); });
  it.each(["DOCTOR", "NURSE", null] as const)("does not render a mutation for %s", role => {
    mocks.role = role; render(<CreateDrugForm />);
    expect(screen.queryByRole("button", { name: "Tạo thuốc" })).not.toBeInTheDocument();
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it("focuses the first invalid field and makes no request", async () => {
    render(<CreateDrugForm />); submit();
    await waitFor(() => expect(screen.getByLabelText("Tên thuốc", { exact: false })).toHaveFocus());
    expect(screen.getByLabelText("Tên thuốc", { exact: false })).toHaveAttribute("aria-invalid", "true");
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it.each([["Giá thuốc", "1.001"], ["Giá thuốc", "10000000000000"], ["Tồn kho ban đầu", "1.5"], ["Tồn kho ban đầu", "2147483648"], ["Hạn sử dụng", "2000-01-01"], ["Ngưỡng tồn thấp", "-1"]])("rejects %s=%s before HTTP", (label, value) => {
    render(<CreateDrugForm />); validForm(); fill(label, value); submit();
    expect(screen.getByLabelText(label, { exact: false })).toHaveAttribute("aria-invalid", "true");
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it("sends the wire fields once, disables edits, and routes using the server ID", async () => {
    let resolve!: (value: { drugId: string }) => void;
    mocks.create.mockReturnValue(new Promise(done => { resolve = done; }));
    render(<CreateDrugForm />); validForm(); fill("Ngưỡng tồn thấp", "0"); submit(); submit();
    expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(mocks.create).toHaveBeenCalledWith({ drugName: "Thuốc mới", unit: "Viên", price: 1234.5, stockQuantity: 20, expiryDate: "2099-01-01", activeIngredient: undefined, manufacturer: undefined, lowStockThreshold: 0 });
    expect(screen.getByLabelText("Tên thuốc", { exact: false })).toBeDisabled();
    await act(async () => resolve({ drugId: "server-created-id" }));
    expect(mocks.router.replace).toHaveBeenCalledWith("/pharmacy/drugs/server-created-id");
  });
  it("keeps input and focuses server field errors with correlation evidence", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Không hợp lệ", 400, "VALIDATION_ERROR", [{ field: "unit", message: "Đơn vị không hỗ trợ" }], "corr-create"));
    render(<CreateDrugForm />); validForm(); submit();
    await waitFor(() => expect(screen.getByLabelText("Đơn vị", { exact: false })).toHaveFocus());
    expect(screen.getByText(/corr-create/)).toBeVisible();
    expect(screen.getByLabelText("Tên thuốc", { exact: false })).toHaveValue("  Thuốc mới  ");
    expect(screen.getByLabelText("Đơn vị", { exact: false })).toHaveAttribute("aria-describedby", "unit-error");
  });
  it.each([401, 403, 409])("handles HTTP %s without a second mutation", async status => {
    mocks.create.mockRejectedValue(new ApiRequestError("Không thể lưu", status, "CREATE_REJECTED", [], "corr-create"));
    render(<CreateDrugForm />); validForm(); submit();
    if (status === 401) await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
    else expect(await screen.findByRole("alert")).toHaveTextContent("corr-create");
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });
  it("describes unknown network outcomes and never retries automatically", async () => {
    mocks.create.mockRejectedValue(new TypeError("Failed to fetch"));
    render(<CreateDrugForm />); validForm(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("kiểm tra danh mục trước khi gửi lại");
    expect(mocks.create).toHaveBeenCalledTimes(1);
    submit(); expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("button", { name: "Tạo thuốc" })).toBeDisabled();
    expect(screen.getByRole("link", { name: "Kiểm tra danh mục thuốc" })).toHaveAttribute("href", "/pharmacy/drugs");
  });
  it("locks an uncertain server outcome while preserving correlation for reconciliation", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Unavailable", 503, "UNAVAILABLE", [], "uncertain-create"));
    render(<CreateDrugForm />); validForm(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("uncertain-create");
    expect(screen.getByLabelText("Tên thuốc", { exact: false })).toBeDisabled();
    submit(); expect(mocks.create).toHaveBeenCalledTimes(1);
  });
  it("keeps a successful command locked until navigation unmounts the form", async () => {
    mocks.create.mockResolvedValue({ drugId: "created" }); render(<CreateDrugForm />); validForm(); submit();
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/pharmacy/drugs/created"));
    submit(); expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("Tên thuốc", { exact: false })).toBeDisabled();
  });
});
