import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import type { DrugDTO } from "../../types";

const mocks = vi.hoisted(() => ({ role: "DOCTOR" as Role | null, search: vi.fn(), create: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, getSessionKey: () => "actor-token", subscribeToAuthChanges: () => () => {} }));
vi.mock("../../api", () => ({ pharmacyApi: { searchDrugs: mocks.search, createPrescription: mocks.create } }));
import { CreatePrescriptionForm } from "./CreatePrescriptionForm";

const id = "00000000-0000-4000-8000-000000000001";
const drug: DrugDTO = { drugId: id, drugName: "Paracetamol", unit: "Viên", price: 2500, stockQuantity: 20,
  activeIngredient: null, manufacturer: null, expiryDate: "2099-01-01", lowStockThreshold: 10, createdAt: "", updatedAt: "" };
function fill(label: string, value: string) { fireEvent.change(screen.getByLabelText(label, { exact: false }), { target: { value } }); }
function submit() { fireEvent.submit(screen.getByLabelText("Mã hồ sơ", { exact: false }).closest("form")!); }
async function validForm() {
  await screen.findByRole("option", { name: /Paracetamol/ });
  for (const label of ["Mã bệnh nhân", "Mã khoa", "Mã bác sĩ", "Mã hồ sơ"]) fill(label, id);
  fill("Ngày kê đơn", "2024-02-29"); fill("Số lượng", "2"); fill("Cách dùng", " Sau ăn ");
  fireEvent.change(screen.getByRole("combobox", { name: /^Thuốc/ }), { target: { value: id } });
}

describe("CreatePrescriptionForm", () => {
  beforeEach(() => {
    mocks.role = "DOCTOR"; mocks.search.mockReset().mockResolvedValue({ content: [drug] });
    mocks.create.mockReset(); mocks.router.replace.mockReset();
  });
  it.each(["PHARMACIST", "MANAGER", null] as const)("does not read catalog or create with role %s", role => {
    mocks.role = role; render(<CreatePrescriptionForm mode="legacy" />);
    expect(screen.queryByRole("button", { name: "Tạo đơn thuốc" })).not.toBeInTheDocument(); expect(mocks.search).not.toHaveBeenCalled();
  });
  it("focuses the first missing identifier and later the invalid drug selector", async () => {
    render(<CreatePrescriptionForm mode="legacy" />); submit();
    await waitFor(() => expect(screen.getByLabelText("Mã hồ sơ", { exact: false })).toHaveFocus());
    for (const label of ["Mã bệnh nhân", "Mã khoa", "Mã bác sĩ", "Mã hồ sơ"]) fill(label, id);
    submit();
    await waitFor(() => expect(screen.getByRole("combobox", { name: /^Thuốc/ })).toHaveFocus());
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it("adds and removes rows, keeps one, and retains selected drug metadata across searches", async () => {
    render(<CreatePrescriptionForm mode="legacy" />); await validForm();
    expect(screen.getByRole("button", { name: "Xóa dòng thuốc 1" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Thêm dòng thuốc" }));
    const second = screen.getByRole("region", { name: "Thuốc 2" });
    expect(within(second).getByRole("option", { name: /Paracetamol/ })).toBeDisabled();
    fireEvent.click(within(second).getByRole("button", { name: "Xóa dòng thuốc 2" }));
    mocks.search.mockResolvedValue({ content: [] }); fill("Tìm thuốc trong danh mục", "Khác");
    fireEvent.click(screen.getByRole("button", { name: "Tìm thuốc" }));
    await waitFor(() => expect(mocks.search).toHaveBeenLastCalledWith({ keyword: "Khác", page: 0, size: 20 }));
    expect(await screen.findByText(/Giá tham khảo:.*2.500/)).toBeVisible();
    expect(screen.getByRole("combobox", { name: /^Thuốc/ })).toHaveValue(id);
  });
  it("sends one V0 command, disables editing while pending and navigates by the returned ID", async () => {
    let resolve!: (value: { prescriptionId: string }) => void;
    mocks.create.mockReturnValue(new Promise(done => { resolve = done; }));
    render(<CreatePrescriptionForm mode="legacy" />); await validForm();
    act(() => { submit(); submit(); });
    expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(mocks.create).toHaveBeenCalledWith({ recordId: id, patientId: id, doctorId: id, departmentId: id,
      prescribedDate: "2024-02-29", lines: [{ drugId: id, quantity: 2, dosage: "Sau ăn" }] });
    expect(screen.getByLabelText("Mã bác sĩ", { exact: false })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Thêm dòng thuốc" })).toBeDisabled();
    await act(async () => resolve({ prescriptionId: "server-prescription" }));
    expect(mocks.router.replace).toHaveBeenCalledWith("/pharmacy/prescriptions/server-prescription");
  });
  it.each(["lines[0].drugId", "lines.0.quantity"])("maps server %s errors to the current row and focuses after enabling fields", async field => {
    mocks.create.mockRejectedValue(new ApiRequestError("Không hợp lệ", 400, "VALIDATION_ERROR", [{ field, message: "Kiểm tra dòng thuốc" }], "rx-validation"));
    render(<CreatePrescriptionForm mode="legacy" />); await validForm(); submit();
    const target = field.endsWith("drugId") ? screen.getByRole("combobox", { name: /^Thuốc/ }) : screen.getByLabelText("Số lượng", { exact: false });
    await waitFor(() => expect(target).toHaveFocus()); expect(target).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText(/rx-validation/)).toBeVisible(); expect(screen.getByLabelText("Mã bác sĩ", { exact: false })).toHaveValue(id);
  });
  it("explains signed prescriber denial without guessing account/staff mapping", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Denied", 403, "PRESCRIPTION_CREATION_FORBIDDEN", [], "identity-rx"));
    render(<CreatePrescriptionForm mode="legacy" />); await validForm(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("bác sĩ kê đơn");
    expect(screen.getByRole("alert")).toHaveTextContent("identity-rx"); expect(screen.getByLabelText("Mã bác sĩ", { exact: false })).toHaveValue(id);
  });
  it.each([new TypeError("Failed to fetch"), new ApiRequestError("Unavailable", 502), new ApiRequestError("Empty payload", 200)])("keeps intent and warns of an unknown create outcome without replay", async cause => {
    mocks.create.mockRejectedValue(cause); render(<CreatePrescriptionForm mode="legacy" />); await validForm(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("kiểm tra đơn đã tạo trước khi gửi lại");
    expect(mocks.create).toHaveBeenCalledTimes(1); expect(screen.getByLabelText("Cách dùng")).toHaveValue(" Sau ăn ");
  });
  it("redirects an expired session and ignores success after unmount", async () => {
    mocks.create.mockRejectedValueOnce(new ApiRequestError("Expired", 401));
    const view = render(<CreatePrescriptionForm mode="legacy" />); await validForm(); submit();
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
    let resolve!: (value: { prescriptionId: string }) => void;
    mocks.create.mockReturnValue(new Promise(done => { resolve = done; })); submit(); view.unmount();
    await act(async () => resolve({ prescriptionId: "late" }));
    expect(mocks.router.replace).not.toHaveBeenCalledWith("/pharmacy/prescriptions/late");
  });
  it("rechecks role at submit even when an old authorized form is still rendered", async () => {
    render(<CreatePrescriptionForm mode="legacy" />); await validForm(); mocks.role = "PHARMACIST"; submit();
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it("ignores a late earlier catalog response after a newer search", async () => {
    let resolve!: (value: { content: DrugDTO[] }) => void;
    mocks.search.mockReturnValueOnce(new Promise(done => { resolve = done; })).mockResolvedValueOnce({ content: [] });
    render(<CreatePrescriptionForm mode="legacy" />); fill("Tìm thuốc trong danh mục", "Khác");
    fireEvent.keyDown(screen.getByLabelText("Tìm thuốc trong danh mục"), { key: "Enter" });
    await screen.findByText(/Chưa có thuốc trong lựa chọn/);
    await act(async () => resolve({ content: [drug] }));
    expect(screen.queryByRole("option", { name: /Paracetamol/ })).not.toBeInTheDocument();
  });
});
