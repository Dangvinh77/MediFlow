import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { patientContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({
  getById: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  getToken: vi.fn(() => "session-a"),
  router: { back: vi.fn(), push: vi.fn(), replace: vi.fn() },
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({
  patientApi: { getById: mocks.getById, create: mocks.create, update: mocks.update },
}));
vi.mock("@/lib/auth", () => ({ getToken: mocks.getToken }));

import { PatientForm } from "./PatientForm";

describe("PatientForm", () => {
  beforeEach(() => {
    mocks.getById.mockReset();
    mocks.create.mockReset();
    mocks.update.mockReset();
    mocks.getToken.mockReturnValue("session-a");
    mocks.router.back.mockReset();
    mocks.router.push.mockReset();
    mocks.router.replace.mockReset();
  });

  it("creates a patient with the required Vietnamese wire fields", async () => {
    mocks.create.mockResolvedValue(patientContractFixture);
    render(<PatientForm />);
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "  Nguyen Van A  " } });
    fireEvent.change(screen.getByLabelText(/Ngày sinh/), { target: { value: "1990-01-15" } });
    fireEvent.change(screen.getByLabelText(/Số CMND\/CCCD/), { target: { value: "001234567890" } });
    fireEvent.submit(screen.getByRole("button", { name: "Tạo bệnh nhân" }).closest("form")!);

    await waitFor(() => expect(mocks.create).toHaveBeenCalledWith({
      hoTen: "Nguyen Van A",
      ngaySinh: "1990-01-15",
      gioiTinh: "M",
      soCmnd: "001234567890",
      diaChi: null,
      soDienThoai: null,
      email: null,
      bhytSo: null,
    }));
    expect(mocks.router.push).toHaveBeenCalledWith(
      `/patients/${patientContractFixture.maBenhNhan}?notice=created`,
    );
  });

  it("keeps identity immutable on update and retries a failed initial lookup", async () => {
    mocks.getById
      .mockRejectedValueOnce(new Error("Không tải được hồ sơ"))
      .mockResolvedValueOnce(patientContractFixture);
    render(<PatientForm patientId={patientContractFixture.maBenhNhan} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được hồ sơ");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByDisplayValue("Nguyen Van A")).toBeVisible();
    const identity = screen.getByLabelText(/Số CMND\/CCCD/);
    expect(identity).toBeDisabled();
    expect(identity).toHaveValue(patientContractFixture.soCmnd);
    mocks.update.mockResolvedValue(patientContractFixture);
    fireEvent.change(screen.getByLabelText(/Địa chỉ/), { target: { value: "  12 Main Street  " } });
    fireEvent.submit(screen.getByRole("button", { name: "Lưu thay đổi" }).closest("form")!);

    await waitFor(() => expect(mocks.update).toHaveBeenCalledWith(
      patientContractFixture.maBenhNhan,
      {
        hoTen: patientContractFixture.hoTen,
        ngaySinh: patientContractFixture.ngaySinh,
        gioiTinh: patientContractFixture.gioiTinh,
        diaChi: "12 Main Street",
        soDienThoai: null,
        email: null,
        bhytSo: null,
      },
    ));
    expect(mocks.router.push).toHaveBeenCalledWith(
      `/patients/${patientContractFixture.maBenhNhan}?notice=updated`,
    );
    expect(mocks.getById).toHaveBeenCalledTimes(2);
  });

  it("keeps the form and displays a rejected save", async () => {
    mocks.create.mockRejectedValue(new Error("Số định danh đã tồn tại"));
    render(<PatientForm />);
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "Nguyen Van A" } });
    fireEvent.change(screen.getByLabelText(/Ngày sinh/), { target: { value: "1990-01-15" } });
    fireEvent.change(screen.getByLabelText(/Số CMND\/CCCD/), { target: { value: "001234567890" } });
    fireEvent.submit(screen.getByRole("button", { name: "Tạo bệnh nhân" }).closest("form")!);

    expect(await screen.findByRole("alert")).toHaveTextContent("Số định danh đã tồn tại");
    expect(screen.getByLabelText(/Họ tên/)).toHaveValue("Nguyen Van A");
    expect(mocks.router.push).not.toHaveBeenCalled();
  });

  it("does not navigate using a patient's response after the signed-in account changes", async () => {
    let resolve!: (patient: typeof patientContractFixture) => void;
    const request = new Promise<typeof patientContractFixture>((done) => { resolve = done; });
    mocks.create.mockReturnValue(request);
    render(<PatientForm />);
    fireEvent.change(screen.getByLabelText(/Họ tên/), { target: { value: "Nguyen Van A" } });
    fireEvent.change(screen.getByLabelText(/Ngày sinh/), { target: { value: "1990-01-15" } });
    fireEvent.change(screen.getByLabelText(/Số CMND\/CCCD/), { target: { value: "001234567890" } });
    fireEvent.submit(screen.getByRole("button", { name: "Tạo bệnh nhân" }).closest("form")!);
    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));

    mocks.getToken.mockReturnValue("session-b");
    await act(async () => { resolve(patientContractFixture); await request; });
    expect(mocks.router.push).not.toHaveBeenCalled();
  });
});
