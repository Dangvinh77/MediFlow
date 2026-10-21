import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { patientContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => {
  let token = "session-a";
  const listeners = new Set<() => void>();
  return {
    getById: vi.fn(), delete: vi.fn(), getRole: vi.fn(() => "DOCTOR"),
    getToken: vi.fn(() => token),
    subscribeToAuthChanges: vi.fn((listener: () => void) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    }),
    setToken: (value: string) => {
      token = value;
      listeners.forEach((listener) => listener());
    },
    router: { replace: vi.fn(), push: vi.fn() },
  };
});

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: mocks.getRole,
  subscribeToAuthChanges: mocks.subscribeToAuthChanges,
}));
vi.mock("@/lib/session", () => ({ getToken: mocks.getToken, subscribeToAuthChanges: mocks.subscribeToAuthChanges }));
vi.mock("../api", () => ({ patientApi: { getById: mocks.getById, delete: mocks.delete } }));

import { PatientDetail } from "./PatientDetail";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}

describe("PatientDetail read logic", () => {
  beforeEach(() => {
    mocks.getById.mockReset();
    mocks.delete.mockReset();
    mocks.getRole.mockReturnValue("DOCTOR");
    mocks.setToken("session-a");
    mocks.router.replace.mockReset();
    mocks.router.push.mockReset();
  });

  it("requires confirmation and prevents duplicate deletion", async () => {
    mocks.getRole.mockReturnValue("ADMIN");
    mocks.getById.mockResolvedValue(patientContractFixture);
    const response = deferred<void>(); mocks.delete.mockReturnValue(response.promise);
    render(<PatientDetail patientId={patientContractFixture.maBenhNhan} />);
    await screen.findByRole("heading", { name: "Nguyen Van A" });
    fireEvent.click(screen.getByRole("button", { name: "Xóa bệnh nhân" }));
    expect(mocks.delete).not.toHaveBeenCalled();
    const confirm = screen.getByRole("button", { name: "Xác nhận xóa" });
    fireEvent.click(confirm); fireEvent.click(confirm);
    expect(mocks.delete).toHaveBeenCalledTimes(1);
    await act(async () => response.resolve());
    expect(mocks.router.push).toHaveBeenCalledWith("/patients?notice=deleted");
  });

  it("drops delete confirmation when switching patients and ignores the old completion", async () => {
    mocks.getRole.mockReturnValue("ADMIN"); mocks.getById.mockResolvedValue(patientContractFixture);
    const response = deferred<void>(); mocks.delete.mockReturnValue(response.promise);
    const view = render(<PatientDetail patientId="one" />);
    await screen.findByRole("heading", { name: "Nguyen Van A" });
    fireEvent.click(screen.getByRole("button", { name: "Xóa bệnh nhân" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận xóa" }));
    view.rerender(<PatientDetail patientId="two" />);
    await screen.findByRole("heading", { name: "Nguyen Van A" });
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    await act(async () => response.resolve());
    expect(mocks.router.push).not.toHaveBeenCalled();
  });

  it("ignores a delete completion after the signed-in account changes", async () => {
    mocks.getRole.mockReturnValue("ADMIN");
    mocks.getById.mockResolvedValue(patientContractFixture);
    const response = deferred<void>();
    mocks.delete.mockReturnValue(response.promise);
    render(<PatientDetail patientId={patientContractFixture.maBenhNhan} />);
    await screen.findByRole("heading", { name: "Nguyen Van A" });
    fireEvent.click(screen.getByRole("button", { name: "Xóa bệnh nhân" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận xóa" }));

    act(() => mocks.setToken("session-b"));
    await act(async () => { response.resolve(); });
    expect(mocks.router.push).not.toHaveBeenCalled();
  });

  it("represents a missing record as not found", async () => {
    mocks.getById.mockRejectedValue(new ApiRequestError("Không tìm thấy", 404, "PATIENT_NOT_FOUND"));
    render(<PatientDetail patientId="missing-id" />);

    expect(await screen.findByText("Không tìm thấy bệnh nhân.")).toBeVisible();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("retries a failed detail request", async () => {
    mocks.getById
      .mockRejectedValueOnce(new Error("Mất kết nối"))
      .mockResolvedValueOnce(patientContractFixture);
    render(<PatientDetail patientId={patientContractFixture.maBenhNhan} />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Mất kết nối");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByRole("heading", { name: "Nguyen Van A" })).toBeVisible();
    expect(mocks.getById).toHaveBeenCalledTimes(2);
  });

  it("does not let a previous patient response replace the current detail", async () => {
    const previous = deferred<typeof patientContractFixture>();
    const current = deferred<typeof patientContractFixture>();
    mocks.getById.mockReturnValueOnce(previous.promise).mockReturnValueOnce(current.promise);
    const view = render(<PatientDetail patientId="patient-one" />);
    view.rerender(<PatientDetail patientId="patient-two" />);

    const currentPatient = { ...patientContractFixture, hoTen: "Patient Two" };
    await act(async () => current.resolve(currentPatient));
    expect(await screen.findByRole("heading", { name: "Patient Two" })).toBeVisible();

    const previousPatient = { ...patientContractFixture, hoTen: "Patient One" };
    await act(async () => previous.resolve(previousPatient));
    expect(screen.queryByRole("heading", { name: "Patient One" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Patient Two" })).toBeVisible();
  });

  it("shows delete controls only to admins and lets an admin cancel", async () => {
    mocks.getById.mockResolvedValue(patientContractFixture);
    mocks.getRole.mockReturnValue("NURSE");
    const view = render(<PatientDetail patientId={patientContractFixture.maBenhNhan} />);
    await screen.findByRole("heading", { name: "Nguyen Van A" });
    expect(screen.getByRole("link", { name: "Chỉnh sửa" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Xóa bệnh nhân" })).not.toBeInTheDocument();

    view.rerender(<PatientDetail patientId={patientContractFixture.maBenhNhan} />);
    mocks.getRole.mockReturnValue("ADMIN");
    // Auth subscriptions drive the role change; rerender after changing the mocked snapshot.
    view.rerender(<PatientDetail patientId={patientContractFixture.maBenhNhan} />);
    expect(screen.getByRole("button", { name: "Xóa bệnh nhân" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Xóa bệnh nhân" }));
    expect(screen.getByRole("alertdialog")).toHaveTextContent("không thể hoàn tác");
    fireEvent.click(screen.getByRole("button", { name: "Hủy" }));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(mocks.delete).not.toHaveBeenCalled();
  });

  it("confirms an admin delete and shows a rejected delete without losing retry context", async () => {
    mocks.getRole.mockReturnValue("ADMIN");
    mocks.getById.mockResolvedValue(patientContractFixture);
    mocks.delete.mockRejectedValue(new ApiRequestError("Hồ sơ đang được tham chiếu", 409, "PATIENT_DELETE_REFERENCED"));
    render(<PatientDetail patientId={patientContractFixture.maBenhNhan} />);
    await screen.findByRole("heading", { name: "Nguyen Van A" });
    fireEvent.click(screen.getByRole("button", { name: "Xóa bệnh nhân" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận xóa" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Hồ sơ đang được tham chiếu");
    expect(mocks.delete).toHaveBeenCalledWith(patientContractFixture.maBenhNhan);
    expect(mocks.router.push).not.toHaveBeenCalled();
  });
});
