import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { patientContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => {
  let token = "session-a";
  const listeners = new Set<() => void>();
  return {
    getById: vi.fn(), create: vi.fn(), update: vi.fn(), getRole: vi.fn(() => "ADMIN"),
    getSessionKey: vi.fn(() => token),
    subscribeToAuthChanges: vi.fn((listener: () => void) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    }),
    setToken: (value: string) => {
      token = value;
      listeners.forEach((listener) => listener());
    },
    router: { replace: vi.fn(), push: vi.fn(), back: vi.fn() },
  };
});

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: mocks.getRole }));
vi.mock("@/lib/session", () => ({ getSessionKey: mocks.getSessionKey, subscribeToAuthChanges: mocks.subscribeToAuthChanges }));
vi.mock("../api", () => ({ patientApi: { getById: mocks.getById, create: mocks.create, update: mocks.update } }));

import { PatientForm } from "./PatientForm";

function fill() {
  fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "  Nguyen Van A  " } });
  fireEvent.change(screen.getByLabelText(/Ngày sinh/), { target: { value: "1990-01-15" } });
  fireEvent.change(screen.getByLabelText(/Số CMND\/CCCD/), { target: { value: " 001234567890 " } });
}

function submit() { fireEvent.submit(screen.getByLabelText(/Họ tên/).closest("form")!); }

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

describe("PatientForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getRole.mockReturnValue("ADMIN");
    mocks.setToken("session-a");
  });

  it("validates programmatic submissions independently of native HTML", async () => {
    render(<PatientForm />);
    submit();
    expect(await screen.findByRole("alert", { name: "Không thể lưu bệnh nhân" })).toHaveTextContent("Họ tên");
    expect(mocks.create).not.toHaveBeenCalled();
    expect(screen.getByRole("alert", { name: "Không thể lưu bệnh nhân" })).toHaveFocus();
    expect(screen.getByLabelText(/Họ tên/)).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText(/Họ tên/)).toHaveAccessibleDescription("Họ tên phải có từ 1 đến 100 ký tự.");
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "Nguyen Van B" } });
    expect(screen.getByLabelText(/Họ tên/)).toHaveAttribute("aria-invalid", "false");
  });

  it("creates once with trimmed/null wire fields and navigates by returned patient identity", async () => {
    const response = deferred<typeof patientContractFixture>();
    mocks.create.mockReturnValue(response.promise);
    render(<PatientForm />);
    fill();
    submit();
    submit();

    expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(mocks.create).toHaveBeenCalledWith({
      hoTen: "Nguyen Van A", ngaySinh: "1990-01-15", gioiTinh: "M", soCmnd: "001234567890",
      diaChi: null, soDienThoai: null, email: null, bhytSo: null,
    });
    await act(async () => { response.resolve(patientContractFixture); });
    expect(mocks.router.push).toHaveBeenCalledWith(`/patients/${patientContractFixture.maBenhNhan}?notice=created`);
  });

  it("allows editing identity and sends its trimmed correction with the existing patient UUID", async () => {
    mocks.getById.mockResolvedValue(patientContractFixture);
    mocks.update.mockResolvedValue(patientContractFixture);
    render(<PatientForm patientId={patientContractFixture.maBenhNhan} />);
    await screen.findByDisplayValue("Nguyen Van A");

    const identity = screen.getByLabelText(/Số CMND\/CCCD/);
    expect(identity).toBeEnabled();
    fireEvent.change(identity, { target: { value: " 001234567891 " } });
    submit();

    await waitFor(() => expect(mocks.update).toHaveBeenCalledTimes(1));
    expect(mocks.update).toHaveBeenCalledWith(patientContractFixture.maBenhNhan, expect.objectContaining({
      hoTen: patientContractFixture.hoTen, soCmnd: "001234567891",
    }));
    expect(mocks.router.push).toHaveBeenCalledWith(`/patients/${patientContractFixture.maBenhNhan}?notice=updated`);
  });

  it("blocks roles without write access before calling the API", async () => {
    mocks.getRole.mockReturnValue("DOCTOR");
    render(<PatientForm />);
    fill();
    submit();
    expect(await screen.findByRole("alert", { name: "Không thể lưu bệnh nhân" })).toHaveTextContent("không có quyền");
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it("retries a failed initial lookup without exposing an editable form", async () => {
    mocks.getById.mockRejectedValueOnce(new Error("Không tải được hồ sơ")).mockResolvedValueOnce(patientContractFixture);
    render(<PatientForm patientId={patientContractFixture.maBenhNhan} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được hồ sơ");
    expect(screen.queryByLabelText(/Họ tên/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByDisplayValue("Nguyen Van A")).toBeVisible();
    expect(mocks.getById).toHaveBeenCalledTimes(2);
  });

  it("retains the draft and correlation evidence after a duplicate correction, then permits a corrected intent", async () => {
    mocks.getById.mockResolvedValue(patientContractFixture);
    mocks.update.mockRejectedValueOnce(new ApiRequestError("Trùng CMND", 409, "PATIENT_CMND_DUPLICATE", [], "edit-trace"))
      .mockResolvedValueOnce(patientContractFixture);
    render(<PatientForm patientId={patientContractFixture.maBenhNhan} />);
    await screen.findByDisplayValue("Nguyen Van A");

    const identity = screen.getByLabelText(/Số CMND\/CCCD/);
    fireEvent.change(identity, { target: { value: "002" } });
    submit();
    expect(await screen.findByRole("alert", { name: "Không thể lưu bệnh nhân" })).toHaveTextContent("edit-trace");
    expect(identity).toHaveValue("002");
    expect(mocks.router.push).not.toHaveBeenCalled();

    fireEvent.change(identity, { target: { value: "003" } });
    submit();
    await waitFor(() => expect(mocks.update).toHaveBeenCalledTimes(2));
    expect(mocks.update).toHaveBeenLastCalledWith(patientContractFixture.maBenhNhan, expect.objectContaining({ soCmnd: "003" }));
  });

  it("retains input and correlation after a duplicate identity on create", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Trùng CMND", 409, "PATIENT_CMND_DUPLICATE", [], "trace-409"));
    render(<PatientForm />);
    fill();
    submit();
    expect(await screen.findByRole("alert", { name: "Không thể lưu bệnh nhân" })).toHaveTextContent("trace-409");
    expect(screen.getByLabelText(/Số CMND\/CCCD/)).toHaveValue(" 001234567890 ");
    expect(mocks.router.push).not.toHaveBeenCalled();
  });

  it("blocks replay after a create timeout with unknown outcome", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("timeout", 408));
    render(<PatientForm />);
    fill();
    submit();
    expect(await screen.findByRole("alert", { name: "Không thể lưu bệnh nhân" })).toHaveTextContent("Chưa xác định");
    submit();
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });

  it("clears a previous patient's draft and ignores its late lookup response", async () => {
    const old = deferred<typeof patientContractFixture>();
    mocks.getById.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ ...patientContractFixture, hoTen: "Patient Two" });
    const view = render(<PatientForm patientId="one" />);
    view.rerender(<PatientForm patientId="two" />);
    await screen.findByDisplayValue("Patient Two");
    await act(async () => { old.resolve(patientContractFixture); });
    expect(screen.queryByDisplayValue("Nguyen Van A")).not.toBeInTheDocument();
  });

  it("does not navigate with a mutation response from a replaced session", async () => {
    const response = deferred<typeof patientContractFixture>();
    mocks.create.mockReturnValue(response.promise);
    render(<PatientForm />);
    fill();
    submit();
    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));

    act(() => mocks.setToken("session-b"));
    await act(async () => { response.resolve(patientContractFixture); });
    expect(mocks.router.push).not.toHaveBeenCalled();
  });

  it("ignores late create success after unmount", async () => {
    const response = deferred<typeof patientContractFixture>();
    mocks.create.mockReturnValue(response.promise);
    const view = render(<PatientForm />);
    fill();
    submit();
    view.unmount();
    await act(async () => { response.resolve(patientContractFixture); });
    expect(mocks.router.push).not.toHaveBeenCalled();
  });

  it("ignores late authentication errors after unmount", async () => {
    const response = deferred<typeof patientContractFixture>();
    mocks.create.mockReturnValue(response.promise);
    const view = render(<PatientForm />);
    fill();
    submit();
    view.unmount();
    await act(async () => { response.reject(new ApiRequestError("expired", 401)); });
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });
});
