import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReferenceProvider, type ReferenceDirectory } from "@/components/references/ReferenceProvider";
import { storeSession } from "@/lib/session";
import { ApiRequestError } from "@/lib/api";
import { CreatePrescriptionForm } from "./CreatePrescriptionForm";

const mocks = vi.hoisted(() => ({ create: vi.fn(), context: vi.fn(), searchDrugs: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: mocks.replace }) }));
vi.mock("../../api", () => ({ pharmacyApi: { getPrescribingContext: mocks.context, searchDrugs: mocks.searchDrugs, createOutpatientPrescription: mocks.create } }));
const patient = "00000000-0000-4000-8000-000000000001";
const doctor = "00000000-0000-4000-8000-000000000002";
const department = "00000000-0000-4000-8000-000000000003";
const record = "00000000-0000-4000-8000-000000000004";
const episode = "00000000-0000-4000-8000-000000000005";
const options = {
  patient: [{ id: patient, label: "Nguyễn An" }, { id: record, label: "Trần Bình" }],
  doctor: [{ id: doctor, label: "Bác sĩ Lan" }],
  department: [{ id: department, label: "Khoa Nội" }, { id: episode, label: "Khoa Ngoại" }],
  record: [{ id: record, label: "Hồ sơ 29/02/2024 · Khám định kỳ" }],
};
function directory(): ReferenceDirectory {
  return {
    canSearch: () => true,
    search: vi.fn(async (kind: string) => ({ items: options[kind as keyof typeof options], hasMore: false })),
    resolve: vi.fn(async (kind, id) => options[kind as keyof typeof options]?.find(item => item.id === id)?.label ?? null),
  };
}
function field(label: string) { return screen.getByLabelText(label, { exact: false }); }
function fill(label: string, value: string) { fireEvent.change(field(label), { target: { value } }); }
async function choose(label: string, name: string) {
  const input = field(label);
  fireEvent.change(input, { target: { value: name } });
  fireEvent.click(within(input.parentElement!).getByRole("button", { name: "Tìm lựa chọn" }));
  fireEvent.click(await screen.findByRole("button", { name }));
}
async function context() {
  await choose("Bệnh nhân", "Nguyễn An");
  await choose("Hồ sơ khám", "Hồ sơ 29/02/2024 · Khám định kỳ"); await screen.findByText("Đã lấy thông tin kê đơn từ hồ sơ khám.");
}
describe("Pharmacy with the shared dashboard reference directory", () => {
  beforeEach(() => {
    vi.clearAllMocks(); localStorage.clear();
    storeSession({ accessToken: "doctor", refreshToken: "refresh", role: "DOCTOR" });
    mocks.searchDrugs.mockResolvedValue({ content: [{ drugId: patient, drugName: "Paracetamol", unit: "Viên", price: 2500, stockQuantity: 20 }] });
    mocks.create.mockResolvedValue({ prescriptionId: "created-rx" });
    mocks.context.mockResolvedValue({ recordId: record, patientId: patient, doctorId: doctor, departmentId: department, careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: episode, priceCodes: ["SOURCE-CATALOG"] });
  });
  it("selects names but dispatches exact V1 authority IDs and a command key", async () => {
    const source = directory();
    render(<ReferenceProvider directory={source}><CreatePrescriptionForm /></ReferenceProvider>);
    await context();
    expect(mocks.context).toHaveBeenCalledWith(record);
    expect(source.search).toHaveBeenCalledWith("record", "Hồ sơ 29/02/2024 · Khám định kỳ", 0, patient);
    fill("Ngày kê đơn", "2024-02-29");
    fireEvent.change(await screen.findByRole("combobox", { name: /^Thuốc/ }), { target: { value: patient } });
    fireEvent.submit(field("Hồ sơ khám").closest("form")!);
    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));
    expect(mocks.create.mock.calls[0][0]).toMatchObject({ commandId: expect.stringMatching(/^[\da-f-]{36}$/i), request: {
      patientId: patient, doctorId: doctor, departmentId: department, recordId: record,
      careEpisodeId: episode, careContractVersion: 1, priceCode: "SOURCE-CATALOG",
    } });
  });
  it("clears the selected record and authoritative context when the patient changes", async () => {
    render(<ReferenceProvider directory={directory()}><CreatePrescriptionForm /></ReferenceProvider>);
    await context();
    await choose("Bệnh nhân", "Trần Bình");
    expect(field("Hồ sơ khám")).toHaveValue(""); expect(screen.queryByText("Đã lấy thông tin kê đơn từ hồ sơ khám.")).not.toBeInTheDocument();
    fireEvent.submit(field("Hồ sơ khám").closest("form")!);
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it("does not create an identity from search text or a failed lookup", async () => {
    const source = directory(); source.search = vi.fn().mockRejectedValue(new ApiRequestError("Không được phép", 403));
    render(<ReferenceProvider directory={source}><CreatePrescriptionForm /></ReferenceProvider>);
    const input = field("Bệnh nhân");
    fireEvent.change(input, { target: { value: "Nguyễn An" } });
    fireEvent.click(within(input.parentElement!).getByRole("button", { name: "Tìm lựa chọn" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Không được phép");
    fireEvent.submit(field("Hồ sơ khám").closest("form")!);
    expect(input).toHaveAttribute("aria-invalid", "true"); expect(mocks.create).not.toHaveBeenCalled();
  });
});
