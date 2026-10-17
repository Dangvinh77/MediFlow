import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { MedicalRecordDTO } from "../types";

const mocks = vi.hoisted(() => ({ byPatient: vi.fn(), role: "DOCTOR", router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
vi.mock("../api", () => ({ medicalRecordApi: { byPatient: mocks.byPatient } }));
import { MedicalRecordTable } from "./MedicalRecordTable";

const record: MedicalRecordDTO = {
  recordId: "record-a", patientId: "patient-a", doctorId: "doctor-a", departmentId: "department-a",
  examinationDate: "2026-10-17", symptoms: "First patient record", appointmentId: null, diagnoses: [],
  createdAt: "2026-10-17T01:00:00Z", updatedAt: null, status: "OPEN", disposition: null,
  dispositionNote: null, completedAt: null,
};
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

describe("Medical record patient lookup", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.byPatient.mockReset(); mocks.role = "DOCTOR"; });

  it("rejects malformed patient IDs before calling the record API", async () => {
    render(<MedicalRecordTable />);
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân"), { target: { value: "not-a-uuid" } });
    fireEvent.click(screen.getByRole("button", { name: "Tìm hồ sơ" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Mã bệnh nhân phải là UUID hợp lệ.");
    expect(mocks.byPatient).not.toHaveBeenCalled();
  });

  it("retries the searched patient ID and leaves its correlation ID visible", async () => {
    mocks.byPatient.mockRejectedValueOnce(new ApiRequestError("Không thể tải", 503, "UNAVAILABLE", [], "record-lookup-correlation"))
      .mockResolvedValueOnce([record]);
    render(<MedicalRecordTable />);
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân"), { target: { value: "123e4567-e89b-42d3-a456-426614174000" } });
    fireEvent.click(screen.getByRole("button", { name: "Tìm hồ sơ" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải");
    expect(screen.getByText("record-lookup-correlation")).toBeVisible();
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân"), { target: { value: "123e4567-e89b-42d3-a456-426614174001" } });
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("First patient record")).toBeVisible();
    expect(mocks.byPatient).toHaveBeenLastCalledWith("123e4567-e89b-42d3-a456-426614174000");
  });

  it("keeps the latest patient's records when an older search fails later", async () => {
    const older = deferred<MedicalRecordDTO[]>();
    mocks.byPatient.mockReturnValueOnce(older.promise).mockResolvedValueOnce([{ ...record, symptoms: "Latest patient record" }]);
    render(<MedicalRecordTable />);
    const form = screen.getByRole("button", { name: "Tìm hồ sơ" }).closest("form")!;
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân"), { target: { value: "123e4567-e89b-42d3-a456-426614174000" } });
    fireEvent.submit(form);
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân"), { target: { value: "123e4567-e89b-42d3-a456-426614174001" } });
    fireEvent.submit(form);
    expect(await screen.findByText("Latest patient record")).toBeVisible();
    await act(async () => older.reject(new ApiRequestError("Older patient search failed", 503, "UNAVAILABLE")));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("Latest patient record")).toBeVisible();
  });

  it("does not redirect after a patient lookup returns 401 after unmount", async () => {
    let reject!: (cause: unknown) => void;
    mocks.byPatient.mockReturnValue(new Promise((_, fail) => { reject = fail; }));
    const view = render(<MedicalRecordTable />);
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân"), { target: { value: "123e4567-e89b-42d3-a456-426614174000" } });
    fireEvent.click(screen.getByRole("button", { name: "Tìm hồ sơ" }));
    await waitFor(() => expect(mocks.byPatient).toHaveBeenCalledTimes(1));
    view.unmount();
    await act(async () => reject(new ApiRequestError("Phiên hết hạn", 401, "UNAUTHORIZED")));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  it.each([["ADMIN", true], ["DOCTOR", true], ["NURSE", false]])("gates record creation for %s", (role, canCreate) => {
    mocks.role = role;
    render(<MedicalRecordTable />);
    expect(Boolean(screen.queryByRole("link", { name: "Tạo hồ sơ khám" }))).toBe(canCreate);
  });
});
