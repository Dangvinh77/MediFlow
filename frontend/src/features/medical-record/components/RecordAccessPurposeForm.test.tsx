import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";

const mocks = vi.hoisted(() => ({ declareAccess: vi.fn() }));
vi.mock("../api", () => ({ medicalRecordApi: { declareAccess: mocks.declareAccess } }));
import { RecordAccessPurposeForm } from "./RecordAccessPurposeForm";

describe("Record access purpose form", () => {
  beforeEach(() => { mocks.declareAccess.mockReset(); });

  it("nurse purpose form offers emergency only", () => {
    render(<RecordAccessPurposeForm patientId="patient-a" role="NURSE" onDeclared={vi.fn()} />);
    expect(screen.getAllByRole("radio")).toHaveLength(1);
    expect(screen.getByRole("radio", { name: "Cấp cứu" })).toBeVisible();
  });

  it("offers doctors the four purposes and administrators the two management purposes", () => {
    const { unmount } = render(<RecordAccessPurposeForm patientId="patient-a" role="DOCTOR" onDeclared={vi.fn()} />);
    expect(screen.getAllByRole("radio").map(radio => radio.getAttribute("value"))).toEqual(["CONSULTATION", "CARE_CONTINUITY", "QUALITY_REVIEW", "EMERGENCY"]);
    unmount();
    render(<RecordAccessPurposeForm patientId="patient-a" role="ADMIN" onDeclared={vi.fn()} />);
    expect(screen.getAllByRole("radio").map(radio => radio.getAttribute("value"))).toEqual(["RECORD_MANAGEMENT", "QUALITY_REVIEW"]);
  });

  it("requires reason text only for quality review and record management", async () => {
    mocks.declareAccess.mockResolvedValue({ declarationId: "d", patientId: "patient-a", purpose: "QUALITY_REVIEW", expiresAt: "2026-11-10T01:00:00Z" });
    const onDeclared = vi.fn();
    render(<RecordAccessPurposeForm patientId="patient-a" role="DOCTOR" onDeclared={onDeclared} />);
    fireEvent.click(screen.getByRole("radio", { name: "Khám bệnh" }));
    expect(screen.queryByLabelText(/Lý do/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Xác nhận mục đích" })).toBeEnabled();

    fireEvent.click(screen.getByRole("radio", { name: "Đánh giá chất lượng" }));
    expect(screen.getByRole("button", { name: "Xác nhận mục đích" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/Lý do/), { target: { value: "ngắn" } });
    expect(screen.getByRole("button", { name: "Xác nhận mục đích" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/Lý do/), { target: { value: "Rà soát chất lượng hồ sơ quý 4" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận mục đích" }));

    await waitFor(() => expect(onDeclared).toHaveBeenCalledTimes(1));
    expect(mocks.declareAccess).toHaveBeenCalledWith({ patientId: "patient-a", purpose: "QUALITY_REVIEW", reason: "Rà soát chất lượng hồ sơ quý 4" });
  });

  it("sends no reason for purposes that do not need one", async () => {
    mocks.declareAccess.mockResolvedValue({ declarationId: "d", patientId: "patient-a", purpose: "EMERGENCY", expiresAt: "2026-11-10T01:00:00Z" });
    render(<RecordAccessPurposeForm patientId="patient-a" role="NURSE" onDeclared={vi.fn()} />);
    fireEvent.click(screen.getByRole("radio", { name: "Cấp cứu" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận mục đích" }));
    await waitFor(() => expect(mocks.declareAccess).toHaveBeenCalledWith({ patientId: "patient-a", purpose: "EMERGENCY", reason: null }));
  });

  it("keeps the form and shows the server message when declaring fails", async () => {
    mocks.declareAccess.mockRejectedValue(new ApiRequestError("Mục đích không hợp lệ", 400, "INVALID_REQUEST"));
    const onDeclared = vi.fn();
    render(<RecordAccessPurposeForm patientId="patient-a" role="NURSE" onDeclared={onDeclared} />);
    fireEvent.click(screen.getByRole("radio", { name: "Cấp cứu" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận mục đích" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Mục đích không hợp lệ");
    expect(onDeclared).not.toHaveBeenCalled();
  });
});
