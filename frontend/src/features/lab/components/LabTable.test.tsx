import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { LabTestDTO } from "../types";

const mocks = vi.hoisted(() => ({ search: vi.fn(), role: "LAB_TECH", router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
vi.mock("../api", () => ({ labApi: { search: mocks.search } }));
import { LabTable } from "./LabTable";

const page = { content: [], number: 0, size: 20, totalPages: 0, totalElements: 0, first: true, last: true };
const labTest: LabTestDTO = {
  testId: "lab-a", recordId: "record-a", patientId: "patient-a", requestingDepartmentId: "department-a",
  labType: "CBC", requestedDate: "2026-10-17", performedDate: null, status: "READY", conclusion: null,
  paid: true, results: null, createdAt: "2026-10-17T01:00:00Z", updatedAt: "2026-10-17T01:00:00Z",
  careContractVersion: 1, sourceOrderId: "order-a", careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: "episode-a",
  priceCode: "LAB-CBC", clearanceId: "clearance-a", emergencyOverrideId: null, resultVersion: 0,
};

describe("Lab queue request lifecycle", () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.search.mockReset(); mocks.role = "LAB_TECH"; });

  it("ignores an authentication failure after leaving the queue", async () => {
    let reject!: (cause: unknown) => void;
    mocks.search.mockReturnValue(new Promise((_, fail) => { reject = fail; }));
    const view = render(<LabTable />);
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(1));
    view.unmount();
    await act(async () => reject(new ApiRequestError("Old request", 401, "UNAUTHORIZED")));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  it("ignores an older filter failure when a newer submission has finished", async () => {
    let reject!: (cause: unknown) => void;
    mocks.search.mockResolvedValueOnce(page)
      .mockReturnValueOnce(new Promise<typeof page>((_, fail) => { reject = fail; }))
      .mockResolvedValueOnce(page);
    render(<LabTable />);
    await screen.findByText("Chưa có yêu cầu xét nghiệm phù hợp.");
    fireEvent.change(screen.getByLabelText("Trạng thái"), { target: { value: "READY" } });
    const form = screen.getByRole("button", { name: "Lọc" }).closest("form")!;
    fireEvent.submit(form);
    fireEvent.change(screen.getByLabelText("Trạng thái"), { target: { value: "COMPLETED" } });
    fireEvent.submit(form);
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(3));
    await screen.findByText("Chưa có yêu cầu xét nghiệm phù hợp.");
    await act(async () => reject(new ApiRequestError("Old filter failed", 503, "UNAVAILABLE")));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("Chưa có yêu cầu xét nghiệm phù hợp.")).toBeVisible();
  });

  it("validates the episode pair before sending another request", async () => {
    mocks.search.mockResolvedValue(page);
    render(<LabTable />);
    await screen.findByText("Chưa có yêu cầu xét nghiệm phù hợp.");
    fireEvent.change(screen.getByLabelText("Loại đợt"), { target: { value: "OUTPATIENT_VISIT" } });
    fireEvent.click(screen.getByRole("button", { name: "Lọc" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Loại và mã đợt chăm sóc phải được nhập cùng nhau.");
    expect(mocks.search).toHaveBeenCalledTimes(1);
  });

  it("retries the submitted filter without applying an unsent draft", async () => {
    mocks.search.mockResolvedValueOnce(page)
      .mockRejectedValueOnce(new ApiRequestError("Temporarily unavailable", 503, "UNAVAILABLE", [], "queue-correlation"))
      .mockResolvedValueOnce(page);
    render(<LabTable />);
    await screen.findByText("Chưa có yêu cầu xét nghiệm phù hợp.");
    fireEvent.change(screen.getByLabelText("Trạng thái"), { target: { value: "READY" } });
    fireEvent.click(screen.getByRole("button", { name: "Lọc" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Temporarily unavailable");
    expect(screen.getByText("queue-correlation")).toBeVisible();
    fireEvent.change(screen.getByLabelText("Trạng thái"), { target: { value: "COMPLETED" } });
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByText("Chưa có yêu cầu xét nghiệm phù hợp.");
    expect(mocks.search).toHaveBeenLastCalledWith(expect.objectContaining({ status: "READY", page: 0, size: 20 }));
  });

  it("keeps the submitted status and episode pair when changing pages", async () => {
    mocks.search.mockResolvedValue({ ...page, content: [labTest], totalPages: 2, totalElements: 21 });
    render(<LabTable />);
    await screen.findByText("CBC");
    fireEvent.change(screen.getByLabelText("Trạng thái"), { target: { value: "READY" } });
    fireEvent.change(screen.getByLabelText("Loại đợt"), { target: { value: "OUTPATIENT_VISIT" } });
    fireEvent.change(screen.getByLabelText("Mã đợt chăm sóc"), { target: { value: "123e4567-e89b-42d3-a456-426614174000" } });
    fireEvent.click(screen.getByRole("button", { name: "Lọc" }));
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(2));
    fireEvent.change(screen.getByLabelText("Trạng thái"), { target: { value: "COMPLETED" } });
    fireEvent.change(screen.getByLabelText("Mã đợt chăm sóc"), { target: { value: "123e4567-e89b-42d3-a456-426614174001" } });
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(3));
    expect(mocks.search).toHaveBeenLastCalledWith({
      status: "READY",
      episodeType: "OUTPATIENT_VISIT",
      episodeId: "123e4567-e89b-42d3-a456-426614174000",
      page: 1,
      size: 20,
    });
  });

  it.each([["LAB_TECH", true], ["MANAGER", false]])("applies list detail visibility for %s", async (role, canView) => {
    mocks.role = role;
    mocks.search.mockResolvedValue({ ...page, content: [labTest], totalPages: 1, totalElements: 1 });
    render(<LabTable />);
    await screen.findByText("CBC");
    expect(Boolean(screen.queryByRole("link", { name: "CBC" }))).toBe(canView);
  });
});
