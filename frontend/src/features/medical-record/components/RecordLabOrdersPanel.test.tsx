import { webcrypto } from "node:crypto";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { RecordLabOrderDTO } from "../types";
import { RecordLabOrdersPanel } from "./RecordLabOrdersPanel";

const mocks = vi.hoisted(() => ({ labOrders: vi.fn(), withdraw: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "doctor-session" }));
vi.mock("../api", () => ({ medicalRecordApi: { labOrders: mocks.labOrders, withdrawLabOrder: mocks.withdraw } }));

const order = (over: Partial<RecordLabOrderDTO>): RecordLabOrderDTO => ({
  sourceOrderId: "order-1", testId: "test-1", labType: "Công thức máu", status: "IN_PROGRESS", orderedAt: "2026-11-10T01:00:00Z", ...over,
});

describe("Lab orders of a record", () => {
  beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal("crypto", webcrypto); sessionStorage.clear(); });

  it("withdraws an order Lab never received, with a reason", async () => {
    mocks.withdraw.mockResolvedValue(order({ testId: null, status: "WITHDRAWN" }));
    mocks.labOrders.mockResolvedValueOnce([order({ sourceOrderId: "order-3", testId: null, status: "NOT_RECEIVED" })]).mockResolvedValue([]);
    render(<RecordLabOrdersPanel recordId="record-a" canWithdraw />);
    fireEvent.click(await screen.findByRole("button", { name: "Rút chỉ định" }));
    expect(screen.getByRole("button", { name: "Xác nhận" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/Lý do rút/), { target: { value: " Bệnh nhân không lấy mẫu " } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));
    await waitFor(() => expect(mocks.withdraw).toHaveBeenCalledWith("record-a", "order-3", { reason: "Bệnh nhân không lấy mẫu" },
      { idempotencyKey: expect.any(String), correlationId: expect.any(String) }));
    expect(await screen.findByText(/Chưa có chỉ định/)).toBeVisible();
  });

  it("offers no withdrawal without permission or once Lab has the order", async () => {
    mocks.labOrders.mockResolvedValue([order({ sourceOrderId: "order-3", testId: null, status: "NOT_RECEIVED" }), order({})]);
    const { unmount } = render(<RecordLabOrdersPanel recordId="record-a" cancelOrder={vi.fn()} />);
    await screen.findByText("Lab chưa tiếp nhận");
    expect(screen.queryByRole("button", { name: "Rút chỉ định" })).not.toBeInTheDocument();
    unmount();
    render(<RecordLabOrdersPanel recordId="record-a" canWithdraw />);
    await screen.findByText("Đang thực hiện");
    expect(screen.getAllByRole("button", { name: "Rút chỉ định" })).toHaveLength(1);
  });

  it("shows each order status from source", async () => {
    mocks.labOrders.mockResolvedValue([
      order({}), order({ sourceOrderId: "order-2", labType: "Sinh hóa", status: "COMPLETED" }),
      order({ sourceOrderId: "order-3", testId: null, labType: "Nước tiểu", status: "NOT_RECEIVED" }),
    ]);
    render(<RecordLabOrdersPanel recordId="record-a" />);
    expect(await screen.findByText("Đang thực hiện")).toBeVisible();
    expect(screen.getByText("Đã có kết quả")).toBeVisible();
    expect(screen.getByText("Lab chưa tiếp nhận")).toBeVisible();
    expect(mocks.labOrders).toHaveBeenCalledWith("record-a");
  });

  it("outage shows unavailable not empty", async () => {
    mocks.labOrders.mockRejectedValue(new ApiRequestError("Lab không phản hồi", 503, "LAB_LIFECYCLE_UNAVAILABLE"));
    render(<RecordLabOrdersPanel recordId="record-a" />);
    expect(await screen.findByText(/Không kiểm tra được trạng thái xét nghiệm/)).toBeVisible();
    expect(screen.queryByText(/Chưa có chỉ định/)).not.toBeInTheDocument();
  });

  it("offers actions only on orders still in progress", async () => {
    mocks.labOrders.mockResolvedValue([order({ status: "COMPLETED" })]);
    render(<RecordLabOrdersPanel recordId="record-a" cancelOrder={vi.fn()} />);
    await screen.findByText("Đã có kết quả");
    expect(screen.queryByRole("button", { name: "Hủy chỉ định" })).not.toBeInTheDocument();
  });

  it("cancel needs a reason", async () => {
    const cancelOrder = vi.fn().mockResolvedValue(undefined);
    mocks.labOrders.mockResolvedValue([order({})]);
    render(<RecordLabOrdersPanel recordId="record-a" cancelOrder={cancelOrder} />);
    fireEvent.click(await screen.findByRole("button", { name: "Hủy chỉ định" }));
    expect(screen.getByRole("button", { name: "Xác nhận" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/Lý do/), { target: { value: "Chỉ định nhầm" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledWith("test-1", "Chỉ định nhầm"));
    await waitFor(() => expect(mocks.labOrders).toHaveBeenCalledTimes(2));
  });

  it("postpone prefixes the reason", async () => {
    const cancelOrder = vi.fn().mockResolvedValue(undefined);
    mocks.labOrders.mockResolvedValue([order({})]);
    render(<RecordLabOrdersPanel recordId="record-a" cancelOrder={cancelOrder} />);
    fireEvent.click(await screen.findByRole("button", { name: "Hoãn sang lần sau" }));
    fireEvent.change(screen.getByLabelText(/Lý do/), { target: { value: "Chưa nhịn ăn" } });
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận" }));
    await waitFor(() => expect(cancelOrder).toHaveBeenCalledWith("test-1", "Hoãn sang lần khám sau: Chưa nhịn ăn"));
  });
});
