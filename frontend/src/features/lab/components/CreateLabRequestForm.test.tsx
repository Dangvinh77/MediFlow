import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { LabTestDTO } from "../types";

const mocks = vi.hoisted(() => ({ create: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ labApi: { create: mocks.create } }));
import { CreateLabRequestForm } from "./CreateLabRequestForm";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function fillLabRequest() {
  fireEvent.change(screen.getByLabelText("Mã hồ sơ khám", { exact: false }), { target: { value: "123e4567-e89b-42d3-a456-426614174000" } });
  fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value: "123e4567-e89b-42d3-a456-426614174001" } });
  fireEvent.change(screen.getByLabelText("Mã khoa yêu cầu", { exact: false }), { target: { value: "123e4567-e89b-42d3-a456-426614174002" } });
  fireEvent.change(screen.getByLabelText("Loại xét nghiệm", { exact: false }), { target: { value: "CBC" } });
  fireEvent.change(screen.getByLabelText("Ngày yêu cầu", { exact: false }), { target: { value: "2026-10-16" } });
}

describe("Create lab request form", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.create.mockReset(); });

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
