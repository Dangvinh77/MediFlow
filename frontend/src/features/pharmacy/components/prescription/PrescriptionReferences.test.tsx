import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReferenceProvider, type ReferenceDirectory } from "@/components/references/ReferenceProvider";
import { storeSession } from "@/lib/session";
import { ApiRequestError } from "@/lib/api";
import { CreatePrescriptionForm } from "./CreatePrescriptionForm";

const mocks = vi.hoisted(() => ({ create: vi.fn(), searchDrugs: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: mocks.replace }) }));
vi.mock("../../api", () => ({ pharmacyApi: { searchDrugs: mocks.searchDrugs, createOutpatientPrescription: mocks.create } }));
const patient = "00000000-0000-4000-8000-000000000001";
const doctor = "00000000-0000-4000-8000-000000000002";
const department = "00000000-0000-4000-8000-000000000003";
const record = "00000000-0000-4000-8000-000000000004";
const episode = "00000000-0000-4000-8000-000000000005";
const options = {
  patient: [{ id: patient, label: "Nguyễn An" }, { id: record, label: "Trần Bình" }],
  doctor: [{ id: doctor, label: "Bác sĩ Lan" }],
  department: [{ id: department, label: "Khoa Nội" }, { id: episode, label: "Khoa Ngoại" }],
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
  await choose("Mã bệnh nhân", "Nguyễn An");
  await choose("Mã khoa", "Khoa Nội");
  await choose("Mã bác sĩ", "Bác sĩ Lan");
  fill("Mã hồ sơ", record); fill("Mã lượt khám", episode);
}
describe("Pharmacy with the shared dashboard reference directory", () => {
  beforeEach(() => {
    vi.clearAllMocks(); localStorage.clear();
    storeSession({ accessToken: "doctor", refreshToken: "refresh", role: "DOCTOR" });
    mocks.searchDrugs.mockResolvedValue({ content: [{ drugId: patient, drugName: "Paracetamol", unit: "Viên", price: 2500, stockQuantity: 20 }] });
    mocks.create.mockResolvedValue({ prescriptionId: "created-rx" });
  });
  it("selects names but dispatches exact V1 authority IDs and a command key", async () => {
    const source = directory();
    render(<ReferenceProvider directory={source}><CreatePrescriptionForm /></ReferenceProvider>);
    await context();
    expect(source.search).toHaveBeenCalledWith("doctor", "Bác sĩ Lan", 0, department);
    fill("Ngày kê đơn", "2024-02-29"); fill("Mã giá thuốc", "SOURCE-CATALOG");
    fireEvent.change(await screen.findByRole("combobox", { name: /^Thuốc/ }), { target: { value: patient } });
    fireEvent.submit(field("Mã hồ sơ").closest("form")!);
    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));
    expect(mocks.create.mock.calls[0][0]).toMatchObject({ commandId: expect.stringMatching(/^[\da-f-]{36}$/i), request: {
      patientId: patient, doctorId: doctor, departmentId: department, recordId: record,
      careEpisodeId: episode, careContractVersion: 1, priceCode: "SOURCE-CATALOG",
    } });
  });
  it("clears source record/episode when the patient changes and doctor when the department changes", async () => {
    render(<ReferenceProvider directory={directory()}><CreatePrescriptionForm /></ReferenceProvider>);
    await context();
    await choose("Mã khoa", "Khoa Ngoại");
    expect(field("Mã bác sĩ")).toHaveValue("");
    await choose("Mã bệnh nhân", "Trần Bình");
    expect(field("Mã hồ sơ")).toHaveValue(""); expect(field("Mã lượt khám")).toHaveValue("");
    fireEvent.submit(field("Mã hồ sơ").closest("form")!);
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it("does not create an identity from search text or a failed lookup", async () => {
    const source = directory(); source.search = vi.fn().mockRejectedValue(new ApiRequestError("Không được phép", 403));
    render(<ReferenceProvider directory={source}><CreatePrescriptionForm /></ReferenceProvider>);
    const input = field("Mã bệnh nhân");
    fireEvent.change(input, { target: { value: "Nguyễn An" } });
    fireEvent.click(within(input.parentElement!).getByRole("button", { name: "Tìm lựa chọn" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Không được phép");
    fireEvent.submit(field("Mã hồ sơ").closest("form")!);
    expect(input).toHaveAttribute("aria-invalid", "true"); expect(mocks.create).not.toHaveBeenCalled();
  });
});
