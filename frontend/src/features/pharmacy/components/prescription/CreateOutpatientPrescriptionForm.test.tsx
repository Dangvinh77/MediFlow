import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import type { OutpatientPrescriptionIntent } from "../../outpatientPrescriptionIntent";

const mocks = vi.hoisted(() => ({
  role: "DOCTOR" as Role | null, token: "actor-a", search: vi.fn(), create: vi.fn(), legacy: vi.fn(),
  router: { replace: vi.fn() }, listeners: new Set<() => void>(),
}));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: () => mocks.role, getSessionKey: () => mocks.token,
  subscribeToAuthChanges: (listener: () => void) => { mocks.listeners.add(listener); return () => mocks.listeners.delete(listener); },
}));
vi.mock("../../api", () => ({ pharmacyApi: {
  searchDrugs: mocks.search, createPrescription: mocks.legacy, createOutpatientPrescription: mocks.create,
} }));
import { CreatePrescriptionForm } from "./CreatePrescriptionForm";

const id = "00000000-0000-4000-8000-000000000001";
const episode = "00000000-0000-4000-8000-000000000002";
function fill(label: string, value: string) { fireEvent.change(screen.getByLabelText(label, { exact: false }), { target: { value } }); }
function submit() { fireEvent.submit(screen.getByLabelText("Mã hồ sơ", { exact: false }).closest("form")!); }
async function validForm() {
  await screen.findByRole("option", { name: /Paracetamol/ });
  for (const label of ["Mã hồ sơ", "Mã bệnh nhân", "Mã bác sĩ", "Mã khoa"]) fill(label, id);
  fill("Ngày kê đơn", "2024-02-29"); fill("Mã lượt khám", episode); fill("Mã giá thuốc", "CATALOG-DRUG");
  fireEvent.change(screen.getByRole("combobox", { name: /^Thuốc/ }), { target: { value: id } });
}
function changeActor() { act(() => { mocks.token = "actor-b"; mocks.listeners.forEach(listener => listener()); }); }

describe("outpatient prescription form", () => {
  beforeEach(() => {
    mocks.role = "DOCTOR"; mocks.token = "actor-a"; mocks.listeners.clear();
    mocks.create.mockReset(); mocks.legacy.mockReset(); mocks.router.replace.mockReset();
    mocks.search.mockReset().mockResolvedValue({ content: [{ drugId: id, drugName: "Paracetamol", unit: "Viên", price: 2500,
      stockQuantity: 20, activeIngredient: null, manufacturer: null, expiryDate: "2099-01-01", lowStockThreshold: 10 }] });
  });
  it("requires explicit episode and price code without inventing defaults", async () => {
    render(<CreatePrescriptionForm />); await validForm(); fill("Mã lượt khám", ""); fill("Mã giá thuốc", ""); submit();
    await waitFor(() => expect(screen.getByLabelText("Mã lượt khám", { exact: false })).toHaveFocus());
    expect(screen.getByLabelText("Mã giá thuốc", { exact: false })).toHaveAttribute("aria-invalid", "true");
    expect(mocks.create).not.toHaveBeenCalled(); expect(mocks.legacy).not.toHaveBeenCalled();
  });
  it("locks duplicate submit, uses V1 and navigates with the server ID", async () => {
    let resolve!: (value: { prescriptionId: string }) => void;
    mocks.create.mockReturnValue(new Promise(done => { resolve = done; }));
    render(<CreatePrescriptionForm />); await validForm(); act(() => { submit(); submit(); });
    expect(mocks.create).toHaveBeenCalledTimes(1);
    const intent: OutpatientPrescriptionIntent = mocks.create.mock.calls[0][0];
    expect(intent.commandId).toMatch(/^[\da-f-]{36}$/i);
    expect(intent.request).toMatchObject({ careContractVersion: 1, careContext: "OUTPATIENT",
      careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: episode, admissionId: null, priceCode: "CATALOG-DRUG", recordId: id });
    expect(screen.getByLabelText("Mã lượt khám", { exact: false })).toBeDisabled();
    await act(async () => resolve({ prescriptionId: "server-rx" }));
    expect(mocks.router.replace).toHaveBeenCalledWith("/pharmacy/prescriptions/server-rx");
    submit(); expect(mocks.create).toHaveBeenCalledTimes(1); expect(mocks.legacy).not.toHaveBeenCalled();
  });
  it.each([new TypeError("Network lost"), new ApiRequestError("Busy", 409), new ApiRequestError("Unavailable", 503), new ApiRequestError("Empty", 200)])("keeps a frozen snapshot for explicit retry after %s", async cause => {
    mocks.create.mockRejectedValueOnce(cause).mockResolvedValueOnce({ prescriptionId: "retried-rx" });
    render(<CreatePrescriptionForm />); await validForm(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("nguyên nội dung và mã yêu cầu");
    expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("Mã giá thuốc", { exact: false })).toBeDisabled();
    const original = mocks.create.mock.calls[0][0];
    fireEvent.click(screen.getByRole("button", { name: "Thử gửi lại đơn" }));
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/pharmacy/prescriptions/retried-rx"));
    expect(mocks.create.mock.calls[1][0]).toBe(original);
    expect(mocks.legacy).not.toHaveBeenCalled();
  });
  it("requires explicit new intent to edit an uncertain request and issues a new key", async () => {
    mocks.create.mockRejectedValueOnce(new TypeError("Unknown")).mockResolvedValueOnce({ prescriptionId: "new-rx" });
    render(<CreatePrescriptionForm />); await validForm(); submit(); await screen.findByRole("alert");
    const oldKey = mocks.create.mock.calls[0][0].commandId;
    fireEvent.click(screen.getByRole("button", { name: "Đã kiểm tra đơn cũ, bắt đầu đơn mới" }));
    expect(screen.getByLabelText("Mã hồ sơ", { exact: false })).toHaveValue("");
    await validForm(); fill("Mã giá thuốc", "ANOTHER-CATALOG"); submit();
    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(2));
    expect(mocks.create.mock.calls[1][0].commandId).not.toBe(oldKey);
  });
  it("moves keyboard focus to the new line and preserves its normal tab order", async () => {
    const user = userEvent.setup();
    render(<CreatePrescriptionForm />);
    await screen.findByRole("option", { name: /Paracetamol/ });
    screen.getByRole("button", { name: "Thêm dòng thuốc" }).focus();
    await user.keyboard("{Enter}");
    const row = within(screen.getByRole("region", { name: "Thuốc 2" }));
    expect(row.getByRole("combobox")).toHaveFocus();
    await user.tab(); expect(row.getByRole("spinbutton")).toHaveFocus();
    await user.tab(); expect(row.getByRole("textbox")).toHaveFocus();
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it.each([1, 2, 3])("keeps focus on a surviving line after removing line %s", async removed => {
    const user = userEvent.setup();
    render(<CreatePrescriptionForm />);
    await screen.findByRole("option", { name: /Paracetamol/ });
    fireEvent.click(screen.getByRole("button", { name: "Thêm dòng thuốc" }));
    fireEvent.click(screen.getByRole("button", { name: "Thêm dòng thuốc" }));
    const selectors = screen.getAllByRole("combobox");
    const expected = selectors[removed < 3 ? removed : removed - 2];
    await user.click(screen.getByRole("button", { name: `Xóa dòng thuốc ${removed}` }));
    expect(expected).toHaveFocus();
    expect(screen.getAllByRole("combobox")).toHaveLength(2);
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it("focuses the earliest visible source field regardless of server error order", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Invalid source", 400, "VALIDATION_ERROR", [
      { field: "priceCode", message: "Kiểm tra mã giá" },
      { field: "patientId", message: "Kiểm tra bệnh nhân" },
    ]));
    render(<CreatePrescriptionForm />); await validForm(); submit();
    await waitFor(() => expect(screen.getByLabelText("Mã bệnh nhân", { exact: false })).toHaveFocus());
  });
  it("focuses quantity before dosage when server line errors arrive in reverse order", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Invalid line", 400, "VALIDATION_ERROR", [
      { field: "lines[0].dosage", message: "Kiểm tra cách dùng" },
      { field: "lines[0].quantity", message: "Kiểm tra số lượng" },
    ]));
    render(<CreatePrescriptionForm />); await validForm(); submit();
    await waitFor(() => expect(screen.getByLabelText("Số lượng", { exact: false })).toHaveFocus());
  });
  it("returns to the first source field only after an explicit new intent", async () => {
    const user = userEvent.setup();
    mocks.create.mockRejectedValue(new TypeError("Unknown outcome"));
    render(<CreatePrescriptionForm />); await validForm(); submit(); await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: "Đã kiểm tra đơn cũ, bắt đầu đơn mới" }));
    expect(screen.getByLabelText("Mã hồ sơ", { exact: false })).toHaveFocus();
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });
  it("focuses unknown-outcome feedback when draft controls become locked", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Unavailable", 503, "UNAVAILABLE", [], "unknown-rx"));
    render(<CreatePrescriptionForm />); await validForm(); submit();
    const alert = await screen.findByRole("alert");
    await waitFor(() => expect(alert).toHaveFocus());
    expect(alert).toHaveTextContent("unknown-rx");
    expect(screen.getByLabelText("Mã hồ sơ", { exact: false })).toBeDisabled();
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });
  it.each([408, 504])("retains the V1 intent on HTTP %s and only retries its frozen payload and key", async status => {
    mocks.create.mockRejectedValueOnce(new ApiRequestError("Timeout", status, "REQUEST_TIMEOUT", [{ field: "patientId", message: "Không xác nhận được bệnh nhân" }], "timeout-rx"))
      .mockResolvedValueOnce({ prescriptionId: "rx-after-timeout" });
    render(<CreatePrescriptionForm />); await validForm(); submit();
    const alert = await screen.findByRole("alert");
    await waitFor(() => expect(alert).toHaveFocus());
    expect(alert).toHaveTextContent("timeout-rx");
    const first: OutpatientPrescriptionIntent = mocks.create.mock.calls[0][0];
    const patient = screen.getByLabelText("Mã bệnh nhân", { exact: false });
    expect(patient).toBeDisabled(); expect(patient).not.toHaveAttribute("aria-invalid", "true");
    fill("Mã bệnh nhân", episode); expect(patient).toHaveValue(id);
    expect(mocks.create).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Thử gửi lại đơn" }));
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/pharmacy/prescriptions/rx-after-timeout"));
    expect(mocks.create).toHaveBeenCalledTimes(2); expect(mocks.create.mock.calls[1][0]).toEqual(first);
    expect(mocks.legacy).not.toHaveBeenCalled();
  });
  it("focuses a rejected V1 source field and does not downgrade to legacy", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Source invalid", 400, "PHARMACY_CARE_CONTEXT_INVALID", [{ field: "careEpisodeId", message: "Lượt khám không khớp" }], "rx-source"));
    render(<CreatePrescriptionForm />); await validForm(); submit();
    await waitFor(() => expect(screen.getByLabelText("Mã lượt khám", { exact: false })).toHaveFocus());
    expect(screen.getByText(/Mã tra cứu: rx-source/)).toBeVisible(); expect(mocks.legacy).not.toHaveBeenCalled();
  });
  it("drops draft and ignores an old actor's pending completion", async () => {
    let resolve!: (value: { prescriptionId: string }) => void;
    mocks.create.mockReturnValue(new Promise(done => { resolve = done; }));
    render(<CreatePrescriptionForm />); await validForm(); submit(); changeActor();
    expect(screen.getByLabelText("Mã hồ sơ", { exact: false })).toHaveValue("");
    await act(async () => resolve({ prescriptionId: "old-actor-rx" }));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });
  it("redirects a current 401 and reports a signed prescriber denial without fallback", async () => {
    mocks.create.mockRejectedValueOnce(new ApiRequestError("Expired", 401))
      .mockRejectedValueOnce(new ApiRequestError("Denied", 403, "PRESCRIPTION_CREATION_FORBIDDEN", [], "actor-trace"));
    render(<CreatePrescriptionForm />); await validForm(); submit();
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
    submit(); expect(await screen.findByRole("alert")).toHaveTextContent("bác sĩ kê đơn");
    expect(screen.getByRole("alert")).toHaveTextContent("actor-trace"); expect(mocks.legacy).not.toHaveBeenCalled();
  });
  it("ignores late 401 after unmount and never resubmits on mount", async () => {
    let reject!: (cause: unknown) => void;
    mocks.create.mockReturnValue(new Promise((_, no) => { reject = no; }));
    const view = render(<CreatePrescriptionForm />); await validForm(); submit(); view.unmount();
    await act(async () => reject(new ApiRequestError("Late", 401)));
    expect(mocks.router.replace).not.toHaveBeenCalled();
    render(<CreatePrescriptionForm />); expect(mocks.create).toHaveBeenCalledTimes(1);
  });
  it.each(["PHARMACIST", "PATIENT", null] as const)("does not read or submit for %s", role => {
    mocks.role = role; render(<CreatePrescriptionForm />);
    expect(mocks.search).not.toHaveBeenCalled(); expect(mocks.create).not.toHaveBeenCalled();
    expect(screen.queryByLabelText("Mã lượt khám", { exact: false })).not.toBeInTheDocument();
  });
});
