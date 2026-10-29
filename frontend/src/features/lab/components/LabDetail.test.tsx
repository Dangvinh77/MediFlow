import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import type { LabTestDTO } from "../types";

const mocks = vi.hoisted(() => ({
  getById: vi.fn(),
  start: vi.fn(),
  getRole: vi.fn<() => Role | null>(() => "LAB_TECH"),
  router: { replace: vi.fn() },
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: mocks.getRole,
  subscribeToAuthChanges: () => () => {},
}));
vi.mock("../api", () => ({ labApi: { getById: mocks.getById, start: mocks.start } }));

import { LabDetail } from "./LabDetail";

// Exact LabTestDTO shape; local UI evidence only, never a payment/clearance authority.
const test: LabTestDTO = {
  testId: "10000000-0000-4000-8000-000000000001",
  recordId: "10000000-0000-4000-8000-000000000002",
  patientId: "10000000-0000-4000-8000-000000000003",
  requestingDepartmentId: "10000000-0000-4000-8000-000000000004",
  labType: "Công thức máu",
  requestedDate: "2026-10-16",
  performedDate: null,
  status: "AWAITING_PAYMENT",
  conclusion: null,
  paid: false,
  results: null,
  createdAt: "2026-10-16T01:00:00Z",
  updatedAt: "2026-10-16T01:00:00Z",
  careContractVersion: 1,
  sourceOrderId: "10000000-0000-4000-8000-000000000005",
  careEpisodeType: "OUTPATIENT_VISIT",
  careEpisodeId: "10000000-0000-4000-8000-000000000006",
  priceCode: "LAB-CBC",
  clearanceId: null,
  emergencyOverrideId: null,
  resultVersion: 0,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

describe("Lab detail read and permission states", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getById.mockReset();
    mocks.start.mockReset();
    mocks.getRole.mockReturnValue("LAB_TECH");
  });

  it("provides exact source identities for cashier handoff without making the nurse infer an episode", async () => {
    mocks.getById.mockResolvedValue(test);
    render(<LabDetail testId={test.testId} />);
    await screen.findByText(test.labType);
    fireEvent.click(screen.getByText("Thông tin bàn giao thu ngân"));
    expect(screen.getByLabelText("Mã xét nghiệm bàn giao")).toHaveValue(test.testId);
    expect(screen.getByLabelText("Mã bệnh nhân bàn giao")).toHaveValue(test.patientId);
    expect(screen.getByLabelText("Mã đợt chăm sóc bàn giao")).toHaveValue(test.careEpisodeId);
  });

  it("shows loading, then keeps payment separate from the care lifecycle", async () => {
    const pending = deferred<LabTestDTO>();
    mocks.getById.mockReturnValue(pending.promise);
    render(<LabDetail testId={test.testId} />);
    expect(screen.getByRole("status", { busy: true })).toBeVisible();
    await act(async () => pending.resolve({ ...test, paid: true }));
    expect(await screen.findByText("Chưa có xác nhận tài chính")).toBeVisible();
    expect(screen.getByText("Chờ quyền tài chính")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Bắt đầu xét nghiệm" })).not.toBeInTheDocument();
    expect(screen.getByText("Chưa có chỉ số kết quả.")).toBeVisible();
    expect(mocks.start).not.toHaveBeenCalled();
  });

  it("shows not found without treating absence as a retryable transport error", async () => {
    mocks.getById.mockRejectedValue(new ApiRequestError("Missing", 404, "LAB_NOT_FOUND"));
    render(<LabDetail testId={test.testId} />);
    expect(await screen.findByRole("heading", { name: "Không tìm thấy xét nghiệm" })).toBeVisible();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Quay lại danh sách" })).toHaveAttribute("href", "/lab");
  });

  it("keeps a forbidden read visible with its correlation ID and no mutation controls", async () => {
    mocks.getById.mockRejectedValue(new ApiRequestError("Không có quyền xem", 403, "AUTH_FORBIDDEN", [], "lab-denied"));
    render(<LabDetail testId={test.testId} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Không có quyền xem");
    expect(screen.getByText("lab-denied")).toBeVisible();
    expect(screen.queryByRole("heading", { name: "Thao tác xét nghiệm" })).not.toBeInTheDocument();
    expect(mocks.getById).toHaveBeenCalledTimes(1);
  });

  it("redirects an expired session to login", async () => {
    mocks.getById.mockRejectedValue(new ApiRequestError("Expired", 401, "AUTH_UNAUTHORIZED"));
    render(<LabDetail testId={test.testId} />);
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
    expect(mocks.getById).toHaveBeenCalledTimes(1);
  });

  it("retries only on user request and clears the previous error while loading", async () => {
    const retry = deferred<LabTestDTO>();
    mocks.getById.mockRejectedValueOnce(new ApiRequestError("Tạm thời gián đoạn", 503, "LAB_UNAVAILABLE", [], "lab-retry"))
      .mockReturnValueOnce(retry.promise);
    render(<LabDetail testId={test.testId} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("lab-retry");
    expect(mocks.getById).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByRole("status", { busy: true })).toBeVisible();
    await act(async () => retry.resolve(test));
    expect(await screen.findByText(test.labType)).toBeVisible();
    expect(mocks.getById.mock.calls).toEqual([[test.testId], [test.testId]]);
  });

  it("ignores an earlier response when navigating to another lab test", async () => {
    const old = deferred<LabTestDTO>();
    const current = deferred<LabTestDTO>();
    mocks.getById.mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
    const view = render(<LabDetail testId={test.testId} />);
    view.rerender(<LabDetail testId="current-test" />);
    await act(async () => current.resolve({ ...test, testId: "current-test", labType: "Hiện tại" }));
    expect(await screen.findByText("Hiện tại")).toBeVisible();
    await act(async () => old.resolve(test));
    expect(screen.queryByText(test.labType)).not.toBeInTheDocument();
    expect(screen.getByText("Hiện tại")).toBeVisible();
  });

  it("ignores a stale 401 instead of redirecting away from the current test", async () => {
    const old = deferred<LabTestDTO>();
    mocks.getById.mockReturnValueOnce(old.promise).mockResolvedValueOnce(test);
    const view = render(<LabDetail testId="old-test" />);
    view.rerender(<LabDetail testId={test.testId} />);
    expect(await screen.findByText(test.labType)).toBeVisible();
    await act(async () => old.reject(new ApiRequestError("Expired", 401, "AUTH_UNAUTHORIZED")));
    expect(mocks.router.replace).not.toHaveBeenCalled();
    expect(screen.getByText(test.labType)).toBeVisible();
  });

  it.each<Role | null>(["NURSE", null])("hides mutation controls for role %s", async (role) => {
    mocks.getRole.mockReturnValue(role);
    mocks.getById.mockResolvedValue({ ...test, status: "READY" });
    render(<LabDetail testId={test.testId} />);
    expect(await screen.findByText("Sẵn sàng")).toBeVisible();
    expect(screen.queryByRole("heading", { name: "Thao tác xét nghiệm" })).not.toBeInTheDocument();
  });

  it("lets a doctor cancel but never start or enter results", async () => {
    mocks.getRole.mockReturnValue("DOCTOR");
    mocks.getById.mockResolvedValue({ ...test, status: "READY" });
    render(<LabDetail testId={test.testId} />);
    expect(await screen.findByRole("button", { name: "Hủy xét nghiệm" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Bắt đầu xét nghiệm" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Nhập kết quả" })).not.toBeInTheDocument();
  });

  it.each(["COMPLETED", "CANCELLED"] as const)("keeps %s immutable even for ADMIN", async (status) => {
    mocks.getRole.mockReturnValue("ADMIN");
    mocks.getById.mockResolvedValue({ ...test, status });
    render(<LabDetail testId={test.testId} />);
    expect(await screen.findByText(test.labType)).toBeVisible();
    expect(screen.queryByRole("heading", { name: "Thao tác xét nghiệm" })).not.toBeInTheDocument();
  });

  it("renders textual results without interpreting or replacing their values", async () => {
    mocks.getById.mockResolvedValue({ ...test, status: "COMPLETED", resultVersion: 1,
      conclusion: "Cần bác sĩ đánh giá", results: [{ resultId: "result", indicator: "Hb",
        value: "< 8.0", unit: "g/dL", referenceRange: "12–16" }] });
    render(<LabDetail testId={test.testId} />);
    expect(await screen.findByRole("cell", { name: "< 8.0" })).toBeVisible();
    expect(screen.getByRole("cell", { name: "12–16" })).toBeVisible();
    expect(screen.getByText("Cần bác sĩ đánh giá")).toBeVisible();
    expect(mocks.start).not.toHaveBeenCalled();
  });

  it("clears a cancellation draft when navigating to a different test", async () => {
    mocks.getById.mockResolvedValueOnce({ ...test, status: "READY" })
      .mockResolvedValueOnce({ ...test, testId: "next-test", labType: "Xét nghiệm tiếp theo", status: "READY" });
    const view = render(<LabDetail testId={test.testId} />);
    fireEvent.click(await screen.findByRole("button", { name: "Hủy xét nghiệm" }));
    fireEvent.change(screen.getByLabelText(/Lý do hủy/), { target: { value: "Lý do của yêu cầu trước" } });
    view.rerender(<LabDetail testId="next-test" />);
    expect(await screen.findByText("Xét nghiệm tiếp theo")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Xác nhận hủy" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Hủy xét nghiệm" }));
    expect(screen.getByLabelText(/Lý do hủy/)).toHaveValue("");
  });

  it("does not let an old pending mutation replace the currently viewed test", async () => {
    const pending = deferred<LabTestDTO>();
    mocks.start.mockReturnValue(pending.promise);
    mocks.getById.mockResolvedValueOnce({ ...test, status: "READY", clearanceId: "10000000-0000-4000-8000-000000000007" })
      .mockResolvedValueOnce({ ...test, testId: "next-test", labType: "Xét nghiệm tiếp theo", status: "COMPLETED" });
    const view = render(<LabDetail testId={test.testId} />);
    const startButton = await screen.findByRole("button", { name: "Bắt đầu xét nghiệm" });
    await act(async () => { fireEvent.click(startButton); });
    await waitFor(() => expect(mocks.start).toHaveBeenCalledWith(test.testId));
    view.rerender(<LabDetail testId="next-test" />);
    expect(await screen.findByText("Xét nghiệm tiếp theo")).toBeVisible();
    await act(async () => pending.resolve({ ...test, status: "IN_PROGRESS" }));
    expect(screen.getByText("Xét nghiệm tiếp theo")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Nhập kết quả" })).not.toBeInTheDocument();
    expect(mocks.getById).toHaveBeenCalledTimes(2);
    expect(mocks.start).toHaveBeenCalledTimes(1);
  });

  it("shows V1 financial clearance independently of the legacy paid flag", async () => {
    mocks.getById.mockResolvedValue({ ...test, careContractVersion: 1, status: "READY", paid: false, clearanceId: "10000000-0000-4000-8000-000000000007" });
    render(<LabDetail testId={test.testId} />);
    expect(await screen.findByText("Có xác nhận tài chính")).toBeVisible();
    expect(screen.queryByText("Chưa thanh toán")).not.toBeInTheDocument();
  });

  it("does not start V1 READY without a server financial clearance", async () => {
    mocks.getById.mockResolvedValue({ ...test, status: "READY", paid: true, clearanceId: null });
    render(<LabDetail testId={test.testId} />);
    expect(await screen.findByText(test.labType)).toBeVisible();
    expect(screen.queryByRole("button", { name: "Bắt đầu xét nghiệm" })).not.toBeInTheDocument();
    expect(mocks.start).not.toHaveBeenCalled();
  });
});
