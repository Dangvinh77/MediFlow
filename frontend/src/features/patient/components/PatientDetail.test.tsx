import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { patientContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({
  getById: vi.fn(),
  getRole: vi.fn(() => "DOCTOR"),
  router: { replace: vi.fn(), push: vi.fn() },
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: mocks.getRole,
  subscribeToAuthChanges: () => () => {},
}));
vi.mock("../api", () => ({ patientApi: { getById: mocks.getById } }));

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
    mocks.getRole.mockReturnValue("DOCTOR");
    mocks.router.replace.mockReset();
    mocks.router.push.mockReset();
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
});
