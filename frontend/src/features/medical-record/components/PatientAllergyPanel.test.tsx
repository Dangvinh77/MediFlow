import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { webcrypto } from "node:crypto";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { PatientAllergiesDTO, PatientAllergyDTO } from "../types";

const mocks = vi.hoisted(() => ({ allergies: vi.fn(), addAllergy: vi.fn(), changeAllergyStatus: vi.fn(), router: { replace: vi.fn() }, session: "doctor-session", listeners: new Set<() => void>() }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/session", () => ({
  getSessionKey: () => mocks.session,
  subscribeToAuthChanges: (listener: () => void) => { mocks.listeners.add(listener); return () => { mocks.listeners.delete(listener); }; },
}));
vi.mock("../api", () => ({ medicalRecordApi: { allergies: mocks.allergies, addAllergy: mocks.addAllergy, changeAllergyStatus: mocks.changeAllergyStatus } }));

import { PatientAllergyPanel } from "./PatientAllergyPanel";

const active: PatientAllergyDTO = {
  allergyId: "allergy-active", substance: "Penicillin", reaction: "Nổi mẩn", severity: "MODERATE", status: "ACTIVE",
  recordedByStaffId: "staff-a", recordedAt: "2026-11-11T01:00:00Z", sourceRecordId: "record-source",
  statusReason: null, statusChangedByStaffId: null, statusChangedAt: null,
};
const inactive: PatientAllergyDTO = { ...active, allergyId: "allergy-inactive", substance: "Latex", status: "ENTERED_IN_ERROR", statusReason: "Ghi nhầm" };
const list: PatientAllergiesDTO = { active: [active], inactive: [inactive] };

describe("patient allergy list", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("crypto", webcrypto);
    sessionStorage.clear();
    mocks.allergies.mockReset();
    mocks.addAllergy.mockReset();
    mocks.changeAllergyStatus.mockReset();
    mocks.session = "doctor-session";
    mocks.listeners.clear();
  });

  it("shows not recorded rather than claiming no allergy", async () => {
    mocks.allergies.mockResolvedValue({ active: [], inactive: [] });
    render(<PatientAllergyPanel patientId="patient-a" role="NURSE" />);

    expect(await screen.findByText("Chưa ghi nhận dị ứng")).toBeVisible();
    expect(screen.queryByText(/Không dị ứng/)).not.toBeInTheDocument();
  });

  it("shows a retryable outage instead of an empty allergy list", async () => {
    mocks.allergies.mockRejectedValueOnce(new ApiRequestError("Unavailable", 503, "UNAVAILABLE")).mockResolvedValueOnce(list);
    render(<PatientAllergyPanel patientId="patient-a" role="NURSE" />);

    expect(await screen.findByText(/Không thể tải sổ dị ứng/)).toBeVisible();
    expect(screen.queryByText("Chưa ghi nhận dị ứng")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText("Penicillin")).toBeVisible();
  });

  it("shows a forbidden state", async () => {
    mocks.allergies.mockRejectedValue(new ApiRequestError("Forbidden", 403, "RECORD_ACCESS_DENIED"));
    render(<PatientAllergyPanel patientId="patient-a" role="NURSE" />);

    expect(await screen.findByText("Bạn không có quyền xem sổ dị ứng của bệnh nhân này.")).toBeVisible();
  });

  it("allows a nurse to add a patient allergy with an idempotency key", async () => {
    mocks.allergies.mockResolvedValue({ active: [], inactive: [] });
    mocks.addAllergy.mockResolvedValue(active);
    render(<PatientAllergyPanel patientId="patient-a" role="NURSE" sourceRecordId="record-current" />);

    await waitFor(() => expect(document.getElementById("allergy-substance")).toBeInTheDocument());
    fireEvent.change(document.getElementById("allergy-substance")!, { target: { value: "Penicillin" } });
    fireEvent.change(document.getElementById("allergy-reaction")!, { target: { value: "Nổi mẩn" } });
    fireEvent.submit(document.getElementById("allergy-substance")!.closest("form")!);

    await waitFor(() => expect(mocks.addAllergy).toHaveBeenCalledWith("patient-a", {
      substance: "Penicillin", reaction: "Nổi mẩn", severity: "UNKNOWN", sourceRecordId: "record-current",
    }, { idempotencyKey: expect.any(String), correlationId: expect.any(String) }));
  });

  it("retries an uncertain allergy command with the same key", async () => {
    mocks.allergies.mockResolvedValue({ active: [], inactive: [] });
    mocks.addAllergy.mockRejectedValueOnce(new ApiRequestError("Không nhận được phản hồi", 503, "UNAVAILABLE"))
      .mockResolvedValueOnce(active);
    render(<PatientAllergyPanel patientId="patient-a" role="NURSE" />);

    await waitFor(() => expect(document.getElementById("allergy-substance")).toBeInTheDocument());
    fireEvent.change(document.getElementById("allergy-substance")!, { target: { value: "Penicillin" } });
    fireEvent.submit(document.getElementById("allergy-substance")!.closest("form")!);
    fireEvent.click(await screen.findByRole("button", { name: "Gửi lại cùng yêu cầu" }));

    await waitFor(() => expect(mocks.addAllergy).toHaveBeenCalledTimes(2));
    expect(mocks.addAllergy.mock.calls[0]).toEqual(mocks.addAllergy.mock.calls[1]);
  });

  it("prevents a second concurrent retry while the same command is in flight", async () => {
    let resolveRetry!: (value: PatientAllergyDTO) => void;
    mocks.allergies.mockResolvedValue({ active: [], inactive: [] });
    mocks.addAllergy.mockRejectedValueOnce(new ApiRequestError("Unavailable", 503, "UNAVAILABLE"))
      .mockReturnValueOnce(new Promise<PatientAllergyDTO>((resolve) => { resolveRetry = resolve; }));
    render(<PatientAllergyPanel patientId="patient-a" role="NURSE" />);

    await waitFor(() => expect(document.getElementById("allergy-substance")).toBeInTheDocument());
    fireEvent.change(document.getElementById("allergy-substance")!, { target: { value: "Penicillin" } });
    fireEvent.submit(document.getElementById("allergy-substance")!.closest("form")!);
    const retry = await screen.findByTestId("allergy-command-retry");
    fireEvent.click(retry);
    fireEvent.click(retry);

    await waitFor(() => expect(mocks.addAllergy).toHaveBeenCalledTimes(2));
    await act(async () => resolveRetry(active));
    expect(mocks.addAllergy).toHaveBeenCalledTimes(2);
  });

  it("lets a doctor mark an allergy entered in error only with a reason and collapses inactive items", async () => {
    mocks.allergies.mockResolvedValue(list);
    mocks.changeAllergyStatus.mockResolvedValue(inactive);
    render(<PatientAllergyPanel patientId="patient-a" role="DOCTOR" />);

    expect(await screen.findByText("Penicillin")).toBeVisible();
    expect(screen.queryByText("Latex")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Dị ứng đã ngừng hoặc ghi nhầm (1)" }));
    expect(screen.getByText("Latex")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Đánh dấu nhập nhầm" }));
    expect(screen.getByRole("button", { name: "Xác nhận" })).toBeDisabled();
    fireEvent.change(document.getElementById("allergy-status-reason")!, { target: { value: "Nhập nhầm dị nguyên" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));

    await waitFor(() => expect(mocks.changeAllergyStatus).toHaveBeenCalledWith("patient-a", "allergy-active", {
      status: "ENTERED_IN_ERROR", reason: "Nhập nhầm dị nguyên",
    }, { idempotencyKey: expect.any(String), correlationId: expect.any(String) }));
  });

  it("ignores an old patient response after switching patients", async () => {
    let resolveOld!: (value: PatientAllergiesDTO) => void;
    mocks.allergies.mockImplementation((patientId: string) => patientId === "patient-old"
      ? new Promise<PatientAllergiesDTO>((resolve) => { resolveOld = resolve; })
      : Promise.resolve({ active: [{ ...active, substance: "New patient allergy" }], inactive: [] }));
    const view = render(<PatientAllergyPanel patientId="patient-old" role="NURSE" />);
    view.rerender(<PatientAllergyPanel patientId="patient-new" role="NURSE" />);
    expect(await screen.findByText("New patient allergy")).toBeVisible();
    resolveOld({ active: [{ ...active, substance: "Old patient allergy" }], inactive: [] });
    await waitFor(() => expect(screen.queryByText("Old patient allergy")).not.toBeInTheDocument());
  });

  it("does not redirect or update a replacement patient after an old write fails", async () => {
    let rejectOld!: (cause: unknown) => void;
    mocks.allergies.mockImplementation((patientId: string) => Promise.resolve({
      active: patientId === "patient-old" ? [] : [{ ...active, substance: "New patient allergy" }], inactive: [],
    }));
    mocks.addAllergy.mockReturnValue(new Promise<PatientAllergyDTO>((_resolve, reject) => { rejectOld = reject; }));
    const view = render(<PatientAllergyPanel patientId="patient-old" role="NURSE" />);
    await waitFor(() => expect(document.getElementById("allergy-substance")).toBeInTheDocument());
    fireEvent.change(document.getElementById("allergy-substance")!, { target: { value: "Old patient allergy" } });
    fireEvent.submit(document.getElementById("allergy-substance")!.closest("form")!);
    await waitFor(() => expect(mocks.addAllergy).toHaveBeenCalledTimes(1));

    view.rerender(<PatientAllergyPanel patientId="patient-new" role="NURSE" />);
    expect(await screen.findByText("New patient allergy")).toBeVisible();
    await act(async () => rejectOld(new ApiRequestError("Expired", 401, "UNAUTHORIZED")));

    expect(mocks.router.replace).not.toHaveBeenCalled();
    expect(screen.getByText("New patient allergy")).toBeVisible();
  });

  it("does not redirect after a pending allergy write finishes following unmount", async () => {
    let rejectOld!: (cause: unknown) => void;
    mocks.allergies.mockResolvedValue({ active: [], inactive: [] });
    mocks.addAllergy.mockReturnValue(new Promise<PatientAllergyDTO>((_resolve, reject) => { rejectOld = reject; }));
    const view = render(<PatientAllergyPanel patientId="patient-a" role="NURSE" />);
    await waitFor(() => expect(document.getElementById("allergy-substance")).toBeInTheDocument());
    fireEvent.change(document.getElementById("allergy-substance")!, { target: { value: "Penicillin" } });
    fireEvent.submit(document.getElementById("allergy-substance")!.closest("form")!);
    await waitFor(() => expect(mocks.addAllergy).toHaveBeenCalledTimes(1));
    view.unmount();

    await act(async () => rejectOld(new ApiRequestError("Expired", 401, "UNAUTHORIZED")));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  it("remounts the panel for a new login session", async () => {
    mocks.allergies.mockResolvedValue({ active: [{ ...active, substance: "Prior session allergy" }], inactive: [] });
    const view = render(<PatientAllergyPanel patientId="patient-a" role="NURSE" />);
    expect(await screen.findByText("Prior session allergy")).toBeVisible();
    mocks.allergies.mockResolvedValue({ active: [{ ...active, substance: "New session allergy" }], inactive: [] });

    act(() => {
      mocks.session = "nurse-session";
      for (const listener of mocks.listeners) listener();
    });

    expect(await screen.findByText("New session allergy")).toBeVisible();
    expect(screen.queryByText("Prior session allergy")).not.toBeInTheDocument();
    view.unmount();
  });
});
