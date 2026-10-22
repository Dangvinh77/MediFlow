import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { clearSession, storeSession } from "@/lib/session";
import type { InvoiceDTO } from "../types";

const mocks = vi.hoisted(() => ({ create: vi.fn(), push: vi.fn(), back: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks }));
vi.mock("../api", () => ({ billingApi: { create: mocks.create } }));

import { InvoiceForm } from "./InvoiceForm";

const patientId = "00000000-0000-4000-8000-000000000001";

function invoice(): InvoiceDTO {
  return {
    invoiceId: "00000000-0000-4000-8000-000000000099",
    patientId,
    createdDate: "2026-10-20",
    totalAmount: 0,
    isPaid: false,
    paymentMethod: null,
    prescriptionId: null,
    sagaStatus: "COMPLETED",
    paidAt: null,
    fees: [],
  };
}

function fillPatientId(value: string) {
  fireEvent.change(screen.getByLabelText("Mã bệnh nhân", { exact: false }), { target: { value } });
}

function submit() {
  fireEvent.click(screen.getByRole("button", { name: "Lập hóa đơn" }));
}

beforeEach(() => {
  vi.clearAllMocks();
  act(() => storeSession({ accessToken: "session-a", refreshToken: "refresh-a", role: "CASHIER" }));
});

afterEach(() => act(() => clearSession()));

describe("InvoiceForm", () => {
  it("rejects a non-UUID patient id without calling the API", () => {
    render(<InvoiceForm />);
    fillPatientId("not-a-uuid");
    submit();

    expect(screen.getByRole("alert")).toHaveTextContent("UUID hợp lệ");
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it("creates the invoice from the server-owned fees and navigates to its detail", async () => {
    mocks.create.mockResolvedValue(invoice());
    render(<InvoiceForm />);
    fillPatientId(patientId);
    submit();

    expect(mocks.create).toHaveBeenCalledWith({ patientId, createdDate: expect.any(String) });
    await vi.waitFor(() => expect(mocks.push).toHaveBeenCalledWith(`/billing/${invoice().invoiceId}?notice=created`));
  });

  it("shows the server rejection with its correlation id and does not navigate", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("Bệnh nhân không tồn tại.", 404, "BILLING_PATIENT_NOT_FOUND", [], "trace-create"));
    render(<InvoiceForm />);
    fillPatientId(patientId);
    submit();

    expect(await screen.findByText("trace-create", { exact: false })).toBeVisible();
    expect(screen.getByText("Bệnh nhân không tồn tại.")).toBeVisible();
    expect(mocks.push).not.toHaveBeenCalled();
    // a definite 4xx rejection unlocks the form for another attempt
    expect(screen.getByRole("button", { name: "Lập hóa đơn" })).not.toBeDisabled();
  });

  it("locks the form after an uncertain outcome until the cashier looks up the invoice", async () => {
    mocks.create.mockRejectedValue(new ApiRequestError("timeout", 408, undefined, [], "trace-timeout"));
    render(<InvoiceForm />);
    fillPatientId(patientId);
    submit();

    expect(await screen.findByText(/Tra cứu hóa đơn của bệnh nhân/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Lập hóa đơn" })).toBeDisabled();
  });

  it("navigates back without creating an invoice", () => {
    render(<InvoiceForm />);
    fireEvent.click(screen.getByRole("button", { name: "Quay lại" }));
    expect(mocks.back).toHaveBeenCalledTimes(1);
    expect(mocks.create).not.toHaveBeenCalled();
  });
});
