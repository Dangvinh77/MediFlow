import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { RecordLabOrderDTO } from "../types";
import { RecordLabOrdersPanel } from "./RecordLabOrdersPanel";

const mocks = vi.hoisted(() => ({ labOrders: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ replace: vi.fn() }) }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => "doctor-session" }));
vi.mock("../api", () => ({ medicalRecordApi: { labOrders: mocks.labOrders } }));

const order = (over: Partial<RecordLabOrderDTO>): RecordLabOrderDTO => ({
  sourceOrderId: "order-1", testId: "test-1", labType: "Công thức máu", status: "IN_PROGRESS", orderedAt: "2026-11-10T01:00:00Z", ...over,
});

describe("Lab orders of a record", () => {
  beforeEach(() => vi.clearAllMocks());

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
