import { act, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CreateLabRequest, LabTestDTO } from "../types";

const mocks = vi.hoisted(() => ({
  create: vi.fn(),
  getSessionKey: vi.fn(() => "lab-order-test-session"),
  router: { replace: vi.fn() },
}));
vi.mock("client-only", () => ({}));
vi.mock("@/lib/session", () => ({ getSessionKey: mocks.getSessionKey }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ labApi: { create: mocks.create } }));

import { CreateSourceLabRequest } from "./CreateSourceLabRequest";

const catalogRequest: CreateLabRequest = {
  recordId: "10000000-0000-4000-8000-000000000001",
  patientId: "10000000-0000-4000-8000-000000000002",
  requestingDepartmentId: "10000000-0000-4000-8000-000000000003",
  labType: "Công thức máu",
  requestedDate: "2026-11-10",
  sourceOrderId: "10000000-0000-4000-8000-000000000004",
  careEpisodeType: "OUTPATIENT_VISIT",
  careEpisodeId: "10000000-0000-4000-8000-000000000005",
  priceCode: "LAB-CBC",
  serviceCode: "CBC",
};
const requestForOrder = (sourceOrderId: string): CreateLabRequest => ({
  ...catalogRequest,
  sourceOrderId,
});

const legacyRequest: CreateLabRequest = {
  recordId: catalogRequest.recordId,
  patientId: catalogRequest.patientId,
  requestingDepartmentId: catalogRequest.requestingDepartmentId,
  labType: catalogRequest.labType,
  requestedDate: catalogRequest.requestedDate,
  sourceOrderId: "10000000-0000-4000-8000-000000000006",
  careEpisodeType: "OUTPATIENT_VISIT",
  careEpisodeId: "10000000-0000-4000-8000-000000000005",
  priceCode: "LEGACY-CBC",
};

const createdTest = {
  testId: "10000000-0000-4000-8000-000000000007",
  recordId: catalogRequest.recordId,
  patientId: catalogRequest.patientId,
  requestingDepartmentId: catalogRequest.requestingDepartmentId,
  labType: catalogRequest.labType,
  requestedDate: catalogRequest.requestedDate,
  performedDate: null,
  status: "PENDING",
  conclusion: null,
  paid: false,
  results: null,
  createdAt: "2026-11-10T09:00:00Z",
  updatedAt: "2026-11-10T09:00:00Z",
  careContractVersion: 0,
  sourceOrderId: catalogRequest.sourceOrderId ?? null,
  careEpisodeType: catalogRequest.careEpisodeType ?? null,
  careEpisodeId: catalogRequest.careEpisodeId ?? null,
  priceCode: catalogRequest.priceCode ?? null,
  clearanceId: null,
  emergencyOverrideId: null,
  resultVersion: 0,
} satisfies LabTestDTO;

describe("CreateSourceLabRequest", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.router.replace.mockReset();
    mocks.create.mockResolvedValue(createdTest);
    mocks.getSessionKey.mockReturnValue("lab-order-test-session");
  });

  it("transfers the locked catalog name, price code, and service code to Lab", async () => {
    const request = requestForOrder("10000000-0000-4000-8000-000000000041");
    render(<CreateSourceLabRequest request={request} autoSubmit />);

    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));
    expect(mocks.create).toHaveBeenCalledWith(request);
    expect(await screen.findByText(/Đã tạo yêu cầu xét nghiệm/)).toBeVisible();
  });

  it("keeps legacy source orders compatible without a service code", async () => {
    render(<CreateSourceLabRequest request={legacyRequest} autoSubmit />);

    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));
    expect(mocks.create).toHaveBeenCalledWith(legacyRequest);
    expect(mocks.create.mock.calls[0][0]).not.toHaveProperty("serviceCode");
  });

  it("submits an auto transfer only once across rerenders", async () => {
    const request = requestForOrder("10000000-0000-4000-8000-000000000043");
    const view = render(<CreateSourceLabRequest request={request} autoSubmit />);
    await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(1));

    view.rerender(<CreateSourceLabRequest request={request} autoSubmit />);
    await act(async () => {});

    expect(mocks.create).toHaveBeenCalledTimes(1);
  });

  it("lets the user continue a legacy transfer manually", async () => {
    const request = { ...legacyRequest, sourceOrderId: "10000000-0000-4000-8000-000000000044" };
    render(<CreateSourceLabRequest request={request} />);

    await act(async () => screen.getByRole("button", { name: "Tiếp tục chuyển sang Lab" }).click());

    await waitFor(() => expect(mocks.create).toHaveBeenCalledWith(request));
    expect(await screen.findByText(/Đã tạo yêu cầu xét nghiệm/)).toBeVisible();
  });
});
