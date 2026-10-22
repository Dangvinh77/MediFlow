import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { clearSession, storeSession } from "@/lib/session";
import { PharmacySessionBoundary } from "./PharmacySessionBoundary";
import { PharmacyIdentity } from "./PharmacyIdentity";
import { CreatePrescriptionForm } from "./prescription/CreatePrescriptionForm";

const mocks = vi.hoisted(() => ({ get: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ pharmacyApi: { searchDrugs: () => Promise.resolve({ content: [] }) } }));
vi.mock("@/lib/api", async original => ({ ...await original<typeof import("@/lib/api")>(), api: { get: mocks.get } }));
const first = "00000000-0000-4000-8000-000000000001";
const second = "00000000-0000-4000-8000-000000000002";
function view(id = first) {
  return <PharmacySessionBoundary><PharmacyIdentity kind="patient" id={id} /><PharmacyIdentity kind="patient" id={id} /></PharmacySessionBoundary>;
}
describe("Pharmacy public identity display", () => {
  beforeEach(() => { mocks.get.mockReset(); localStorage.clear(); storeSession({ accessToken: "test", refreshToken: "test-refresh", role: "DOCTOR" }); });
  it("deduplicates lookups and uses Patient hoTen with the exact ID still visible", async () => {
    mocks.get.mockResolvedValue({ maBenhNhan: first, hoTen: "Nguyễn An" });
    render(view());
    expect(await screen.findAllByText("Nguyễn An")).toHaveLength(2);
    expect(screen.getAllByText(first)).toHaveLength(2);
    expect(mocks.get).toHaveBeenCalledExactlyOnceWith(`/v1/patients/${first}`);
  });
  it("resolves staff and department names without account/service lookups", async () => {
    mocks.get.mockImplementation((path: string) => Promise.resolve(path.includes("staff") ? { staffId: first, fullName: "Bác sĩ Bình" } : { departmentId: second, departmentName: "Khoa Nội" }));
    render(<PharmacySessionBoundary><PharmacyIdentity kind="doctor" id={first} /><PharmacyIdentity kind="department" id={second} /></PharmacySessionBoundary>);
    expect(await screen.findByText("Bác sĩ Bình")).toBeVisible();
    expect(await screen.findByText("Khoa Nội")).toBeVisible();
    expect(mocks.get.mock.calls.map(([path]) => path)).toEqual([`/v1/org/staff/${first}`, `/v1/org/departments/${second}`]);
  });
  it("connects the actual creation form to the display resolver while preserving its UUID inputs", async () => {
    mocks.get.mockImplementation((path: string) => Promise.resolve(path.includes("patients")
      ? { maBenhNhan: first, hoTen: "Nguyễn An" }
      : path.includes("staff") ? { staffId: second, fullName: "Bác sĩ Bình" }
        : { departmentId: first, departmentName: "Khoa Nội" }));
    render(<CreatePrescriptionForm />);
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: first } });
    fireEvent.change(screen.getByLabelText("Mã khoa", { exact: false }), { target: { value: first } });
    fireEvent.change(screen.getByLabelText("Mã bác sĩ", { exact: false }), { target: { value: second } });
    expect(await screen.findByText("Nguyễn An")).toBeVisible();
    expect(await screen.findByText("Bác sĩ Bình")).toBeVisible();
    expect(await screen.findByText("Khoa Nội")).toBeVisible();
    expect(screen.getByLabelText("Mã bệnh nhân", { exact: false })).toHaveValue(first);
    expect(screen.getByLabelText("Mã bác sĩ", { exact: false })).toHaveValue(second);
    expect(screen.getByLabelText("Mã khoa", { exact: false })).toHaveValue(first);
    expect(mocks.get).toHaveBeenCalledTimes(3);
  });
  it("does not issue unauthorized PHARMACIST requests", async () => {
    storeSession({ accessToken: "pharmacist", refreshToken: "refresh", role: "PHARMACIST" });
    render(view());
    expect(await screen.findAllByText("Tài khoản chưa được cấp quyền tra cứu tên")).toHaveLength(2);
    expect(mocks.get).not.toHaveBeenCalled();
    expect(screen.getAllByText(first)).toHaveLength(2);
  });
  it.each([403, 404, 503])("keeps IDs and honest fallback for HTTP %s", async status => {
    mocks.get.mockRejectedValue(new ApiRequestError("failure", status));
    render(view());
    await waitFor(() => expect(screen.queryAllByText("Đang tra cứu tên…")).toHaveLength(0));
    expect(screen.getAllByText(first)).toHaveLength(2);
    expect(screen.queryByText("Nguyễn An")).not.toBeInTheDocument();
  });
  it("rejects a mismatched authority response and retries an unavailable lookup", async () => {
    mocks.get.mockResolvedValueOnce({ maBenhNhan: second, hoTen: "Sai bệnh nhân" }).mockResolvedValue({ maBenhNhan: first, hoTen: "Đúng bệnh nhân" });
    render(<PharmacySessionBoundary><PharmacyIdentity kind="patient" id={first} /></PharmacySessionBoundary>);
    fireEvent.click(await screen.findByRole("button", { name: "Tra cứu tên lại" }));
    expect(await screen.findByText("Đúng bệnh nhân")).toBeVisible();
    expect(screen.queryByText("Sai bệnh nhân")).not.toBeInTheDocument();
  });
  it("drops a late response after the patient changes", async () => {
    let resolve!: (value: unknown) => void;
    mocks.get.mockReturnValueOnce(new Promise(value => { resolve = value; })).mockResolvedValue({ maBenhNhan: second, hoTen: "Bệnh nhân mới" });
    const rendered = render(view());
    await waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1));
    rendered.rerender(view(second));
    expect(await screen.findAllByText("Bệnh nhân mới")).toHaveLength(2);
    await act(async () => resolve({ maBenhNhan: first, hoTen: "Bệnh nhân cũ" }));
    expect(screen.queryByText("Bệnh nhân cũ")).not.toBeInTheDocument();
  });
  it("discards the cache and late response on logout and a new same-role login", async () => {
    let resolve!: (value: unknown) => void;
    mocks.get.mockReturnValueOnce(new Promise(value => { resolve = value; })).mockResolvedValue({ maBenhNhan: first, hoTen: "Phiên mới" });
    render(view());
    await waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1));
    act(() => { clearSession(); storeSession({ accessToken: "new", refreshToken: "new-refresh", role: "DOCTOR" }); });
    expect(await screen.findAllByText("Phiên mới")).toHaveLength(2);
    await act(async () => resolve({ maBenhNhan: first, hoTen: "Phiên cũ" }));
    expect(screen.queryByText("Phiên cũ")).not.toBeInTheDocument();
    expect(mocks.get).toHaveBeenCalledTimes(2);
  });
});
