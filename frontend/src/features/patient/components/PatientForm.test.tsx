import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { patientContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({ getById: vi.fn(), create: vi.fn(), update: vi.fn(),
  getRole: vi.fn(() => "ADMIN"), router: { replace: vi.fn(), push: vi.fn(), back: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: mocks.getRole }));
vi.mock("../api", () => ({ patientApi: { getById: mocks.getById, create: mocks.create, update: mocks.update } }));
import { PatientForm } from "./PatientForm";

function fill() {
  fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "  Nguyen Van A  " } });
  fireEvent.change(screen.getByLabelText(/Ngày sinh/), { target: { value: "1990-01-15" } });
  fireEvent.change(screen.getByLabelText(/Số CMND/), { target: { value: " 001234567890 " } });
}
function submit() { fireEvent.submit(screen.getByLabelText(/Họ tên/).closest("form")!); }
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

describe("PatientForm logic using existing markup", () => {
  beforeEach(() => {
    vi.clearAllMocks(); mocks.getRole.mockReturnValue("ADMIN");
    mocks.getById.mockReset(); mocks.create.mockReset(); mocks.update.mockReset();
  });
  it("validates programmatic submissions independently of native HTML", async () => {
    render(<PatientForm />); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("Họ tên");
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it("creates once with trimmed/null wire values and navigates by returned identity", async () => {
    const response = deferred<typeof patientContractFixture>(); mocks.create.mockReturnValue(response.promise);
    render(<PatientForm />); fill(); submit(); submit();
    expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ hoTen: "Nguyen Van A", soCmnd: "001234567890", email: null, bhytSo: null }));
    await act(async () => response.resolve(patientContractFixture));
    expect(mocks.router.push).toHaveBeenCalledWith(`/patients/${patientContractFixture.maBenhNhan}?notice=created`);
  });
  it("lets staff correct identity and sends it with the same patient UUID", async () => {
    mocks.getById.mockResolvedValue(patientContractFixture); mocks.update.mockResolvedValue(patientContractFixture);
    render(<PatientForm patientId={patientContractFixture.maBenhNhan} />);
    await screen.findByDisplayValue("Nguyen Van A");
    expect(screen.getByLabelText(/Số CMND/)).toBeEnabled();
    fireEvent.change(screen.getByLabelText(/Số CMND/), { target: { value: " 001234567891 " } });
    submit();
    await waitFor(() => expect(mocks.update).toHaveBeenCalledTimes(1));
    expect(mocks.update).toHaveBeenCalledWith(patientContractFixture.maBenhNhan,
      expect.objectContaining({ soCmnd: "001234567891" }));
  });
  it("stops a forbidden role before creating", async () => {
    mocks.getRole.mockReturnValue("DOCTOR"); render(<PatientForm />); fill(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("không có quyền");
    expect(mocks.create).not.toHaveBeenCalled();
  });
  it("keeps a rejected identity correction and allows correction after a duplicate conflict", async () => {
    mocks.getById.mockResolvedValue(patientContractFixture);
    mocks.update.mockRejectedValueOnce(new ApiRequestError("Trùng CMND", 409, "PATIENT_CMND_DUPLICATE", [], "edit-trace"))
      .mockResolvedValueOnce(patientContractFixture);
    render(<PatientForm patientId={patientContractFixture.maBenhNhan} />);
    await screen.findByDisplayValue("Nguyen Van A");
    const identity = screen.getByLabelText(/Số CMND/);
    fireEvent.change(identity, { target: { value: "002" } }); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("edit-trace");
    expect(identity).toHaveValue("002");
    expect(mocks.router.push).not.toHaveBeenCalled();
    fireEvent.change(identity, { target: { value: "003" } }); submit();
    await waitFor(() => expect(mocks.update).toHaveBeenCalledTimes(2));
    expect(mocks.update).toHaveBeenLastCalledWith(patientContractFixture.maBenhNhan,
      expect.objectContaining({ soCmnd: "003" }));
  });
  it("preserves input and correlation after duplicate identity", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Trùng CMND", 409, "PATIENT_CMND_DUPLICATE", [], "trace-409"));
    render(<PatientForm />); fill(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("trace-409");
    expect(screen.getByLabelText(/Số CMND/)).toHaveValue(" 001234567890 ");
    expect(mocks.router.push).not.toHaveBeenCalled();
  });
  it("blocks a second create after an unknown timeout", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("timeout", 408));
    render(<PatientForm />); fill(); submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("Chưa xác định");
    submit(); expect(mocks.create).toHaveBeenCalledTimes(1);
  });
  it("clears the previous patient's draft/response when the edit identity changes", async () => {
    const old = deferred<typeof patientContractFixture>();
    mocks.getById.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ ...patientContractFixture, hoTen: "Patient Two" });
    const view = render(<PatientForm patientId="one" />);
    view.rerender(<PatientForm patientId="two" />);
    await screen.findByDisplayValue("Patient Two");
    await act(async () => old.resolve(patientContractFixture));
    expect(screen.queryByDisplayValue("Nguyen Van A")).not.toBeInTheDocument();
  });
  it("ignores a late create success after unmount", async () => {
    const response = deferred<typeof patientContractFixture>(); mocks.create.mockReturnValue(response.promise);
    const view = render(<PatientForm />); fill(); submit(); view.unmount();
    await act(async () => response.resolve(patientContractFixture));
    expect(mocks.router.push).not.toHaveBeenCalled();
  });
  it("ignores a late authentication error after unmount", async () => {
    const response = deferred<typeof patientContractFixture>(); mocks.create.mockReturnValue(response.promise);
    const view = render(<PatientForm />); fill(); submit(); view.unmount();
    await act(async () => response.reject(new ApiRequestError("expired", 401)));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });
});
