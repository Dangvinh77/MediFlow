import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import type { DrugDTO } from "../../types";

const mocks = vi.hoisted(() => ({ role: "DOCTOR" as Role | null, search: vi.fn(), doctor: vi.fn(), create: vi.fn(), context: vi.fn(), createOutpatient: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, getSessionKey: () => "actor-token", subscribeToAuthChanges: () => () => {} }));
vi.mock("../../api", () => ({ pharmacyApi: { searchDrugs: mocks.search, getPrescribingDoctor: mocks.doctor, getPrescribingContext: mocks.context, createPrescription: mocks.create, createOutpatientPrescription: mocks.createOutpatient } }));
import { CreatePrescriptionForm } from "./CreatePrescriptionForm";

const id = "00000000-0000-4000-8000-000000000001";
const drug: DrugDTO = { drugId: id, drugName: "Paracetamol", unit: "Viên", price: 2500, stockQuantity: 20,
  activeIngredient: null, manufacturer: null, expiryDate: "2099-01-01", lowStockThreshold: 10, createdAt: "", updatedAt: "" };
function fill(label: string, value: string) { fireEvent.change(screen.getByLabelText(label, { exact: false }), { target: { value } }); }
function submit() { fireEvent.submit(screen.getByLabelText("Hồ sơ khám", { exact: false }).closest("form")!); }
async function addDrug(name = "Paracetamol", preset = "Chỉ tối") {
  fireEvent.click(await screen.findByRole("button", { name: `Thêm ${name} vào toa` }));
  const dialog = screen.getByRole("dialog");
  fireEvent.click(within(dialog).getByRole("button", { name: preset }));
  fireEvent.click(within(dialog).getByRole("button", { name: "Áp dụng" }));
}
async function validForm() {
  await screen.findByRole("button", { name: "Thêm Paracetamol vào toa" });
  for (const label of ["Bệnh nhân", "Bác sĩ kê đơn", "Hồ sơ khám"]) fill(label, id);
  await waitFor(() => expect(screen.getByLabelText("Khoa khám", { exact: false })).toHaveValue("Khoa của bác sĩ đã chọn"));
  fill("Ngày kê đơn", "2024-02-29"); await addDrug(); fill("Số lượng", "2");
}

describe("CreatePrescriptionForm", () => {
  beforeEach(() => {
    mocks.role = "DOCTOR"; mocks.search.mockReset().mockResolvedValue({ content: [drug] });
    mocks.doctor.mockReset().mockResolvedValue({ staffId: id, departmentId: id, jobTitle: "DOCTOR", active: true });
    mocks.create.mockReset(); mocks.context.mockReset(); mocks.createOutpatient.mockReset(); mocks.router.replace.mockReset();
  });
  it.each(["PHARMACIST", null] as const)("does not read catalog or create with role %s", role => {
    mocks.role = role; render(<CreatePrescriptionForm mode="legacy" />);
    expect(screen.queryByRole("button", { name: "Tạo đơn thuốc" })).not.toBeInTheDocument(); expect(mocks.search).not.toHaveBeenCalled();
  });
  it("focuses the first missing identifier and later the drug search for an empty prescription", async () => {
    render(<CreatePrescriptionForm mode="legacy" />); submit();
    await waitFor(() => expect(screen.getByLabelText("Bệnh nhân", { exact: false })).toHaveFocus());
    for (const label of ["Bệnh nhân", "Bác sĩ kê đơn", "Hồ sơ khám"]) fill(label, id);
    await waitFor(() => expect(screen.getByLabelText("Khoa khám", { exact: false })).toHaveValue("Khoa của bác sĩ đã chọn"));
    submit();
    await waitFor(() => expect(screen.getByLabelText("Tìm thuốc trong danh mục")).toHaveFocus());
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it("blocks a duplicate drug, removes rows, and keeps the prescription across searches", async () => {
    render(<CreatePrescriptionForm mode="legacy" />); await validForm();
    expect(screen.getByText("Đã thêm")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Thêm Paracetamol vào toa" })).not.toBeInTheDocument();
    mocks.search.mockResolvedValue({ content: [] }); fill("Tìm thuốc trong danh mục", "Khác");
    fireEvent.click(screen.getByRole("button", { name: "Tìm thuốc" }));
    await waitFor(() => expect(mocks.search).toHaveBeenLastCalledWith({ keyword: "Khác", page: 0, size: 20 }));
    await screen.findByText(/Không có thuốc phù hợp/);
    expect(screen.getByRole("cell", { name: /Paracetamol/ })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Xóa dòng thuốc 1" }));
    expect(screen.getByText("Toa chưa có thuốc.")).toBeVisible();
  });
  it("never shows a price while prescribing", async () => {
    const { container } = render(<CreatePrescriptionForm mode="legacy" />); await validForm();
    expect(container.textContent).not.toMatch(/2[.,]500|Giá|Tổng tiền/);
  });
  it("generates a locked dosage, suggests quantity and sends the line note", async () => {
    mocks.create.mockResolvedValue({ prescriptionId: "rx" });
    render(<CreatePrescriptionForm mode="legacy" />); await validForm();
    const dosage = screen.getByRole("textbox", { name: /^Cách dùng dòng 1/ });
    expect(dosage).toHaveAttribute("readonly"); expect(dosage).toHaveValue("Tối 1 viên sau ăn");
    fireEvent.click(dosage);
    const dialog = within(screen.getByRole("dialog"));
    fireEvent.click(dialog.getByRole("checkbox", { name: "Sáng" }));
    fireEvent.change(dialog.getByRole("combobox", { name: "Thời điểm dùng buổi Sáng" }), { target: { value: "Trước ăn" } });
    fireEvent.click(dialog.getByRole("button", { name: "Tăng liều buổi Sáng" }));
    fireEvent.change(dialog.getByLabelText("Số ngày dùng"), { target: { value: "4" } });
    expect(dialog.getByText(/Gợi ý số lượng: 10 Viên/)).toBeVisible();
    fireEvent.click(dialog.getByRole("button", { name: "Áp dụng" }));
    expect(dosage).toHaveValue("Sáng 1,5 viên trước ăn; Tối 1 viên sau ăn (4 ngày)");
    expect(screen.getByLabelText("Số lượng dòng 1")).toHaveValue(10);
    fill("Ghi chú dòng 1", " Uống đủ liệu trình "); submit();
    expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ lines: [{ drugId: id, quantity: 10,
      dosage: "Sáng 1,5 viên trước ăn; Tối 1 viên sau ăn (4 ngày)", note: "Uống đủ liệu trình" }] }));
  });
  it("requires a dosage: closing the dialog keeps the row but blocks creation", async () => {
    render(<CreatePrescriptionForm mode="legacy" />);
    fireEvent.click(await screen.findByRole("button", { name: "Thêm Paracetamol vào toa" }));
    const dialog = within(screen.getByRole("dialog"));
    fireEvent.click(dialog.getByRole("button", { name: "Áp dụng" }));
    expect(dialog.getByRole("alert")).toHaveTextContent("ít nhất một buổi");
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /^Cách dùng dòng 1/ })).toHaveValue("");
    for (const label of ["Bệnh nhân", "Bác sĩ kê đơn", "Hồ sơ khám"]) fill(label, id);
    await waitFor(() => expect(screen.getByLabelText("Khoa khám", { exact: false })).toHaveValue("Khoa của bác sĩ đã chọn"));
    submit();
    expect(await screen.findByText("Chọn cách dùng cho thuốc này.")).toBeVisible();
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it("locks the department to the selected doctor and clears it immediately when changing doctors", async () => {
    const nextId = "00000000-0000-4000-8000-000000000002";
    let resolve!: (value: unknown) => void;
    mocks.doctor.mockResolvedValueOnce({ staffId: id, departmentId: nextId, jobTitle: "DOCTOR", active: true })
      .mockReturnValueOnce(new Promise(done => { resolve = done; }));
    render(<CreatePrescriptionForm mode="legacy" />); await validForm();
    const department = screen.getByLabelText("Khoa khám", { exact: false });
    expect(department).toHaveAttribute("readonly");
    fill("Bác sĩ kê đơn", nextId);
    expect(department).toHaveValue("");
    submit();
    expect(mocks.create).not.toHaveBeenCalled();
    await act(async () => resolve({ staffId: nextId, departmentId: id, jobTitle: "DOCTOR", active: true }));
    await waitFor(() => expect(department).toHaveValue("Khoa của bác sĩ đã chọn"));
    mocks.create.mockResolvedValue({ prescriptionId: "new-rx" }); submit();
    expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ doctorId: nextId, departmentId: id }));
  });
  it("ignores a late department response after the doctor is cleared", async () => {
    let resolve!: (value: unknown) => void;
    mocks.doctor.mockReturnValue(new Promise(done => { resolve = done; }));
    render(<CreatePrescriptionForm mode="legacy" />);
    fill("Bác sĩ kê đơn", id);
    fill("Bác sĩ kê đơn", "");
    await act(async () => resolve({ staffId: id, departmentId: id, jobTitle: "DOCTOR", active: true }));
    expect(screen.getByLabelText("Khoa khám", { exact: false })).toHaveValue("");
    expect(screen.getByPlaceholderText("Chọn bác sĩ trước")).toBeVisible();
  });
  it("blocks creation after a lookup failure and lets the user retry", async () => {
    mocks.doctor.mockRejectedValueOnce(new Error("offline"));
    render(<CreatePrescriptionForm mode="legacy" />);
    fill("Bác sĩ kê đơn", id);
    expect(await screen.findByRole("alert")).toHaveTextContent("Chưa lấy được khoa");
    submit(); expect(mocks.create).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Thử lấy khoa lại" }));
    await waitFor(() => expect(screen.getByLabelText("Khoa khám", { exact: false })).toHaveValue("Khoa của bác sĩ đã chọn"));
    expect(mocks.doctor).toHaveBeenCalledTimes(2);
  });
  it.each([
    { staffId: id, departmentId: id, jobTitle: "DOCTOR", active: false },
    { staffId: id, departmentId: id, jobTitle: "NURSE", active: true },
    { staffId: id, departmentId: null, jobTitle: "DOCTOR", active: true },
    { staffId: "other", departmentId: id, jobTitle: "DOCTOR", active: true },
  ])("rejects an invalid doctor relationship: %j", async data => {
    mocks.doctor.mockResolvedValue(data);
    render(<CreatePrescriptionForm mode="legacy" />); fill("Bác sĩ kê đơn", id);
    expect(await screen.findByRole("alert")).toHaveTextContent("Bác sĩ chưa có khoa hợp lệ");
    expect(screen.getByLabelText("Khoa khám", { exact: false })).toHaveValue("");
    submit(); expect(mocks.create).not.toHaveBeenCalled();
  });
  it("browses more than twenty drugs without losing selected lines and blocks unavailable choices", async () => {
    mocks.search.mockResolvedValueOnce({ content: [drug], number: 0, totalPages: 2, totalElements: 21 })
      .mockResolvedValueOnce({ content: [
        { ...drug, drugId: "00000000-0000-4000-8000-000000000002", drugName: "Thuốc A", stockQuantity: 0 },
        { ...drug, drugId: "00000000-0000-4000-8000-000000000003", drugName: "Thuốc B", expiryDate: "2000-01-01" },
      ], number: 1, totalPages: 2, totalElements: 21 });
    render(<CreatePrescriptionForm mode="legacy" />); await validForm();
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    await waitFor(() => expect(mocks.search).toHaveBeenLastCalledWith({ keyword: "", page: 1, size: 20 }));
    expect(await screen.findByText("Hết hàng")).toBeVisible();
    expect(screen.getByText("Hết hạn")).toBeVisible();
    expect(screen.queryByRole("button", { name: /Thêm Thuốc/ })).not.toBeInTheDocument();
    expect(screen.getByRole("cell", { name: /Paracetamol/ })).toBeVisible();
    expect(screen.getByLabelText("Số lượng", { exact: false })).toHaveValue(2);
  });
  it("sends one V0 command, disables editing while pending and navigates by the returned ID", async () => {
    let resolve!: (value: { prescriptionId: string }) => void;
    mocks.create.mockReturnValue(new Promise(done => { resolve = done; }));
    render(<CreatePrescriptionForm mode="legacy" />); await validForm();
    act(() => { submit(); submit(); });
    expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(mocks.create).toHaveBeenCalledWith({ recordId: id, patientId: id, doctorId: id, departmentId: id,
      prescribedDate: "2024-02-29", lines: [{ drugId: id, quantity: 2, dosage: "Tối 1 viên sau ăn" }] });
    expect(screen.getByLabelText("Bác sĩ kê đơn", { exact: false })).toBeDisabled();
    expect(screen.getByLabelText("Tìm thuốc trong danh mục")).toBeDisabled();
    await act(async () => resolve({ prescriptionId: "server-prescription" }));
    expect(mocks.router.replace).toHaveBeenCalledWith("/pharmacy/prescriptions/server-prescription");
  });
  it("shows a server drug error on the current row and focuses the drug name", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Không hợp lệ", 400, "VALIDATION_ERROR", [{ field: "lines[0].drugId", message: "Thuốc đã ngừng dùng" }], "rx-drug"));
    render(<CreatePrescriptionForm mode="legacy" />); await validForm(); submit();
    await waitFor(() => expect(screen.getByText("Paracetamol", { selector: "div" })).toHaveFocus());
    expect(screen.getByText("Thuốc đã ngừng dùng")).toBeVisible();
  });
  it("does not let delayed dosage-dialog focus override a server field error", async () => {
    const frames: FrameRequestCallback[] = [];
    const frame = vi.spyOn(globalThis, "requestAnimationFrame").mockImplementation(callback => { frames.push(callback); return frames.length; });
    try {
      mocks.create.mockRejectedValue(new ApiRequestError("Không hợp lệ", 400, "VALIDATION_ERROR", [{ field: "lines[0].drugId", message: "Thuốc đã ngừng dùng" }]));
      render(<CreatePrescriptionForm mode="legacy" />); await validForm(); submit();
      const target = screen.getByText("Paracetamol", { selector: "div" });
      await waitFor(() => expect(target).toHaveFocus());
      act(() => frames.forEach(callback => callback(0)));
      expect(target).toHaveFocus();
    } finally { frame.mockRestore(); }
  });
  it.each(["lines[0].quantity", "lines.0.quantity"])("maps server %s errors to the current row and focuses after enabling fields", async field => {
    mocks.create.mockRejectedValue(new ApiRequestError("Không hợp lệ", 400, "VALIDATION_ERROR", [{ field, message: "Kiểm tra dòng thuốc" }], "rx-validation"));
    render(<CreatePrescriptionForm mode="legacy" />); await validForm(); submit();
    const target = screen.getByLabelText("Số lượng", { exact: false });
    await waitFor(() => expect(target).toHaveFocus()); expect(target).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText(/rx-validation/)).toBeVisible(); expect(screen.getByLabelText("Bác sĩ kê đơn", { exact: false })).toHaveValue(id);
  });
  it("explains signed prescriber denial without guessing account/staff mapping", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Denied", 403, "PRESCRIPTION_CREATION_FORBIDDEN", [], "identity-rx"));
    render(<CreatePrescriptionForm mode="legacy" />); await validForm(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("bác sĩ kê đơn");
    expect(screen.getByRole("alert")).toHaveTextContent("identity-rx"); expect(screen.getByLabelText("Bác sĩ kê đơn", { exact: false })).toHaveValue(id);
  });
  it.each([new TypeError("Failed to fetch"), new ApiRequestError("Unavailable", 502), new ApiRequestError("Empty payload", 200)])("keeps intent and warns of an unknown create outcome without replay", async cause => {
    mocks.create.mockRejectedValue(cause); render(<CreatePrescriptionForm mode="legacy" />); await validForm(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("kiểm tra đơn đã tạo trước khi gửi lại");
    expect(mocks.create).toHaveBeenCalledTimes(1); expect(screen.getByRole("textbox", { name: /^Cách dùng dòng 1/ })).toHaveValue("Tối 1 viên sau ăn");
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
    await screen.findByText(/Không có thuốc phù hợp/);
    await act(async () => resolve({ content: [drug] }));
    expect(screen.queryByRole("button", { name: /Paracetamol/ })).not.toBeInTheDocument();
  });

  it("locks a source record, trusts the prescribing context for patient identity, and shows a receipt in place", async () => {
    const sourceRecordId = "00000000-0000-4000-8000-000000000010";
    const patientId = "00000000-0000-4000-8000-000000000011";
    const episodeId = "00000000-0000-4000-8000-000000000012";
    const context = { recordId: sourceRecordId, patientId, doctorId: id, departmentId: id,
      careEpisodeType: "OUTPATIENT_VISIT" as const, careEpisodeId: episodeId, priceCodes: ["OUTPATIENT"] };
    const created = { prescriptionId: "00000000-0000-4000-8000-000000000013", recordId: sourceRecordId, patientId,
      doctorId: id, departmentId: id, prescribedDate: "2024-02-29", totalAmount: 5000, lines: [], status: "ACTIVE",
      dispenseStatus: "PENDING", paymentConfirmed: false, cancelledAt: null, cancelledBy: null, cancellationReason: null,
      createdAt: "2026-11-04T00:00:00Z", updatedAt: "2026-11-04T00:00:00Z" };
    const onCreated = vi.fn();
    mocks.context.mockResolvedValue(context);
    mocks.createOutpatient.mockResolvedValue(created);
    render(<CreatePrescriptionForm mode="outpatient" sourceRecordId={sourceRecordId} onCreated={onCreated} />);

    expect(await screen.findByText(/Đã lấy thông tin kê đơn từ hồ sơ khám/)).toBeVisible();
    expect(mocks.context).toHaveBeenCalledExactlyOnceWith(sourceRecordId);
    expect(screen.queryByLabelText("Bệnh nhân", { exact: false })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Hồ sơ khám", { exact: false })).not.toBeInTheDocument();
    fill("Ngày kê đơn", "2024-02-29"); await addDrug(); fill("Số lượng", "2");
    fireEvent.click(screen.getByRole("button", { name: "Tạo đơn thuốc" }));

    await waitFor(() => expect(mocks.createOutpatient).toHaveBeenCalledWith(expect.objectContaining({
      request: expect.objectContaining({ recordId: sourceRecordId, patientId, doctorId: id, careEpisodeId: episodeId }),
    })));
    expect(onCreated).toHaveBeenCalledExactlyOnceWith(created);
    expect(await screen.findByRole("status", { name: "Đã tạo đơn thuốc" })).toHaveTextContent(created.prescriptionId);
    expect(screen.getByRole("link", { name: "Mở chi tiết đơn thuốc" })).toHaveAttribute("href", `/pharmacy/prescriptions/${created.prescriptionId}`);
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  it("blocks an embedded create when server prescribing context belongs to another record", async () => {
    const sourceRecordId = "00000000-0000-4000-8000-000000000010";
    mocks.context.mockResolvedValue({ recordId: "00000000-0000-4000-8000-000000000099", patientId: id,
      doctorId: id, departmentId: id, careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: id, priceCodes: ["OUTPATIENT"] });
    render(<CreatePrescriptionForm mode="outpatient" sourceRecordId={sourceRecordId} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("không khớp hồ sơ nguồn");
    expect(screen.queryByLabelText("Bệnh nhân", { exact: false })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tạo đơn thuốc" })).toBeDisabled();
    expect(mocks.createOutpatient).not.toHaveBeenCalled();
  });
});
