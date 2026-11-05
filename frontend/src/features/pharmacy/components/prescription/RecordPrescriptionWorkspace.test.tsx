import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { ApiRequestError } from "@/lib/api";

const mocks = vi.hoisted(() => ({ search: vi.fn(), current: () => true, onCreated: vi.fn() }));
vi.mock("../../api", () => ({ pharmacyApi: { searchPrescriptions: mocks.search } }));
vi.mock("../PharmacySessionBoundary", () => ({ PharmacySessionBoundary: ({ children }: { children: ReactNode }) => children, usePharmacyRequestScope: () => mocks.current }));
vi.mock("./CreatePrescriptionForm", () => ({
  CreatePrescriptionForm: ({ mode, sourceRecordId, onCreated }: { mode: string; sourceRecordId: string; onCreated?: () => void }) => (
    <div>
      <span>Form kê đơn {mode} · {sourceRecordId}</span>
      <button type="button" onClick={() => onCreated?.()}>Giả lập tạo đơn</button>
    </div>
  ),
}));

import { RecordPrescriptionWorkspace } from "./RecordPrescriptionWorkspace";

const recordId = "00000000-0000-4000-8000-000000000001";
const patientId = "00000000-0000-4000-8000-000000000002";
function rx(id: string, overrides: Partial<{ recordId: string | null; status: string }> = {}) {
  return { prescriptionId: id, recordId, patientId, doctorId: "doctor", departmentId: "department", prescribedDate: "2026-10-25",
    totalAmount: 6000, status: "ACTIVE", dispenseStatus: "PENDING", paymentConfirmed: true, cancelledAt: null, cancelledBy: null,
    cancellationReason: null, createdAt: "2026-10-25T01:00:00Z", updatedAt: "2026-10-25T01:00:00Z",
    lines: [{ lineId: `${id}-line`, drugId: "drug", drugName: "Omeprazol 20mg", quantity: 2, unitPrice: 3000, dosage: null }],
    ...overrides };
}
function page(content: ReturnType<typeof rx>[], totalPages = 1) {
  return { content, totalElements: content.length, totalPages, number: 0, size: 100 };
}

describe("record prescription workspace", () => {
  beforeEach(() => {
    mocks.search.mockReset().mockResolvedValue(page([rx("rx-a")]));
    mocks.current = () => true;
    mocks.onCreated.mockReset();
  });

  it("lists only prescriptions belonging to this record and links to their detail", async () => {
    mocks.search.mockResolvedValue(page([rx("rx-a"), rx("rx-other-record", { recordId: "00000000-0000-4000-8000-000000000099" })]));
    render(<RecordPrescriptionWorkspace recordId={recordId} patientId={patientId} canCreate={false} mode="outpatient" />);
    const links = await screen.findAllByRole("link");
    expect(links.map(link => link.getAttribute("href"))).toEqual(["/pharmacy/prescriptions/rx-a"]);
    expect(screen.queryByText("Form kê đơn")).not.toBeInTheDocument();
  });

  it("embeds the source-locked form only when the record allows creating", async () => {
    render(<RecordPrescriptionWorkspace recordId={recordId} patientId={patientId} canCreate mode="outpatient" />);
    expect(await screen.findByText(`Form kê đơn outpatient · ${recordId}`)).toBeInTheDocument();
    expect(screen.getByText("Kê đơn cho lượt khám", { selector: "summary" })).toBeInTheDocument();
  });

  it("refreshes the list after a prescription is created from this record", async () => {
    render(<RecordPrescriptionWorkspace recordId={recordId} patientId={patientId} canCreate mode="outpatient" />);
    await screen.findByRole("link", { name: /Omeprazol 20mg/ });
    mocks.search.mockResolvedValue(page([rx("rx-a"), rx("rx-b")]));
    fireEvent.click(await screen.findByRole("button", { name: "Giả lập tạo đơn" }));
    await waitFor(() => expect(screen.getAllByRole("link", { name: /Omeprazol 20mg/ })).toHaveLength(2));
  });

  it("keeps a failed read closed instead of showing an empty record", async () => {
    mocks.search.mockRejectedValueOnce(new Error("offline"));
    render(<RecordPrescriptionWorkspace recordId={recordId} patientId={patientId} canCreate={false} mode={null} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải");
    expect(screen.queryByText("Hồ sơ này chưa có đơn thuốc.")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("link", { name: /Omeprazol 20mg/ })).toBeVisible();
  });

  it("does not retry a forbidden read and hides the create form", async () => {
    mocks.search.mockRejectedValue(new ApiRequestError("Denied", 403));
    render(<RecordPrescriptionWorkspace recordId={recordId} patientId={patientId} canCreate mode="outpatient" />);
    expect(await screen.findByRole("alert")).toHaveTextContent("không có quyền");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(screen.queryByText("Form kê đơn")).not.toBeInTheDocument();
  });
});
