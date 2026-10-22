import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { LabTestDTO } from "../types";

const mocks = vi.hoisted(() => ({ create: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ labApi: { create: mocks.create } }));
import { ReferenceProvider, type ReferenceDirectory } from "@/components/references/ReferenceProvider";
import { CreateLabRequestForm } from "./CreateLabRequestForm";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function fillLabRequest() {
  fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: "123e4567-e89b-42d3-a456-426614174001" } });
  fireEvent.change(screen.getByLabelText("Mã hồ sơ khám", { exact: false }), { target: { value: "123e4567-e89b-42d3-a456-426614174000" } });
  fireEvent.change(screen.getByLabelText("Mã khoa yêu cầu", { exact: false }), { target: { value: "123e4567-e89b-42d3-a456-426614174002" } });
  fireEvent.change(screen.getByLabelText("Loại xét nghiệm", { exact: false }), { target: { value: "CBC" } });
  fireEvent.change(screen.getByLabelText("Ngày yêu cầu", { exact: false }), { target: { value: "2026-10-16" } });
}

describe("Create lab request form", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.create.mockReset(); });

  it("submits server identities selected by name and invalidates the record when patient changes", async () => {
    const ids = { patient: "123e4567-e89b-42d3-a456-426614174001", record: "123e4567-e89b-42d3-a456-426614174000", department: "123e4567-e89b-42d3-a456-426614174002" };
    const labels = { patient: "Nguyễn An", record: "Hồ sơ ngày 16/10", department: "Khoa Nội" };
    const source: ReferenceDirectory = {
      canSearch: () => true, resolve: vi.fn().mockResolvedValue(null),
      search: vi.fn(async (kind) => ({ items: [{ id: ids[kind as keyof typeof ids], label: labels[kind as keyof typeof labels] }], hasMore: false })),
    };
    mocks.create.mockReturnValue(new Promise(() => {}));
    render(<ReferenceProvider directory={source}><CreateLabRequestForm /></ReferenceProvider>);
    for (const [kind, field] of [["patient", "Mã bệnh nhân"], ["record", "Mã hồ sơ khám"], ["department", "Mã khoa yêu cầu"]] as const) {
      const input = screen.getByLabelText(field, { exact: false });
      fireEvent.click(within(input.parentElement!).getByRole("button", { name: "Tìm lựa chọn" }));
      fireEvent.click(await screen.findByRole("button", { name: labels[kind] }));
      expect(input).toHaveValue(labels[kind]);
    }
    expect(source.search).toHaveBeenCalledWith("record", "", 0, ids.patient);
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: "Bệnh nhân khác" } });
    expect(screen.getByLabelText("Mã hồ sơ khám", { exact: false })).toHaveValue("");
    fireEvent.click(screen.getByRole("button", { name: "Tạo yêu cầu xét nghiệm" }));
    expect(mocks.create).not.toHaveBeenCalled();
    // Re-select both references after changing the patient.
    for (const [kind, field] of [["patient", "Mã bệnh nhân"], ["record", "Mã hồ sơ khám"]] as const) {
      const input = screen.getByLabelText(field, { exact: false });
      fireEvent.click(within(input.parentElement!).getByRole("button", { name: "Tìm lựa chọn" }));
      fireEvent.click(await screen.findByRole("button", { name: labels[kind] }));
    }
    fireEvent.change(screen.getByLabelText("Loại xét nghiệm", { exact: false }), { target: { value: "CBC" } });
    fireEvent.change(screen.getByLabelText("Ngày yêu cầu", { exact: false }), { target: { value: "2026-10-16" } });
    fireEvent.click(screen.getByRole("button", { name: "Tạo yêu cầu xét nghiệm" }));
    await waitFor(() => expect(mocks.create).toHaveBeenCalledWith({ patientId: ids.patient, recordId: ids.record, requestingDepartmentId: ids.department, labType: "CBC", requestedDate: "2026-10-16" }));
  });

  it("sends only one create request when submit fires twice before the response", async () => {
    const pending = deferred<LabTestDTO>();
    mocks.create.mockReturnValue(pending.promise);
    render(<CreateLabRequestForm />);
    fillLabRequest();
    const form = screen.getByRole("button", { name: "Tạo yêu cầu xét nghiệm" }).closest("form")!;
    await act(async () => { fireEvent.submit(form); fireEvent.submit(form); });
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });

  it("ignores a late authentication failure after the lab form unmounts", async () => {
    const pending = deferred<LabTestDTO>();
    mocks.create.mockReturnValue(pending.promise);
    const view = render(<CreateLabRequestForm />);
    fillLabRequest();
    fireEvent.click(screen.getByRole("button", { name: "Tạo yêu cầu xét nghiệm" }));
    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));
    view.unmount();
    await act(async () => pending.reject(new ApiRequestError("Phiên hết hạn", 401, "UNAUTHORIZED")));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  it("maps a backend validation error without submitting another contract field", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError(
      "Loại xét nghiệm không hợp lệ", 400, "VALIDATION_ERROR", [{ field: "labType", message: "Loại xét nghiệm chưa được hỗ trợ" }], "lab-form-correlation",
    ));
    render(<CreateLabRequestForm />);
    fillLabRequest();
    fireEvent.click(screen.getByRole("button", { name: "Tạo yêu cầu xét nghiệm" }));
    expect(await screen.findByText("Loại xét nghiệm chưa được hỗ trợ")).toBeVisible();
    expect(screen.getByText("lab-form-correlation")).toBeVisible();
    expect(mocks.create).toHaveBeenCalledWith({
      recordId: "123e4567-e89b-42d3-a456-426614174000",
      patientId: "123e4567-e89b-42d3-a456-426614174001",
      requestingDepartmentId: "123e4567-e89b-42d3-a456-426614174002",
      labType: "CBC",
      requestedDate: "2026-10-16",
    });
  });

  it("rejects malformed IDs and missing required fields before creating a lab request", () => {
    render(<CreateLabRequestForm />);
    fillLabRequest();
    fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: "patient" } });
    fireEvent.click(screen.getByRole("button", { name: "Tạo yêu cầu xét nghiệm" }));
    expect(screen.getByText("Mã bệnh nhân phải là UUID hợp lệ.")).toBeVisible();
    expect(mocks.create).not.toHaveBeenCalled();
  });
});
