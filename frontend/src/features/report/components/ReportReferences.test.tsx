import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReferenceProvider, type ReferenceDirectory } from "@/components/references/ReferenceProvider";
import { storeSession } from "@/lib/session";
import { ApiRequestError } from "@/lib/api";
import { OutpatientReportView } from "./OutpatientReportView";

const mocks = vi.hoisted(() => ({ outpatientDaily: vi.fn(), availablePeriods: vi.fn(), get: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ reportApi: { outpatientDaily: mocks.outpatientDaily, availablePeriods: mocks.availablePeriods } }));
vi.mock("@/lib/api", async original => ({ ...await original<typeof import("@/lib/api")>(), api: { get: mocks.get } }));
const department = "00000000-0000-4000-8000-000000000001";
const query = { mode: "daily" as const, fromDate: "2024-02-29", toDate: "2024-02-29", year: 2024 };
function directory(): ReferenceDirectory {
  return { canSearch: () => true, search: vi.fn().mockResolvedValue({ items: [{ id: department, label: "Khoa Nội" }], hasMore: false }), resolve: vi.fn().mockResolvedValue("Khoa Nội") };
}
async function select() {
  const input = screen.getByLabelText("Khoa (tùy chọn)");
  fireEvent.change(input, { target: { value: "Nội" } });
  fireEvent.click(within(input.parentElement!).getByRole("button", { name: "Tìm lựa chọn" }));
  fireEvent.click(await screen.findByRole("button", { name: "Khoa Nội" }));
}
function submit() { fireEvent.submit(screen.getByLabelText("Khoa (tùy chọn)").closest("form")!); }

describe("Report shared department selection", () => {
  beforeEach(() => {
    vi.clearAllMocks(); localStorage.clear(); storeSession({ accessToken: "admin", refreshToken: "refresh", role: "ADMIN" });
  });
  it("clears the department selection and its visible label before submitting whole-hospital scope", async () => {
    const onQuerySubmit = vi.fn(() => true);
    render(<ReferenceProvider directory={directory()}><OutpatientReportView initialQuery={query} autoLoad={false} onQuerySubmit={onQuerySubmit} /></ReferenceProvider>);
    await select();
    expect(screen.getByLabelText("Khoa (tùy chọn)")).toHaveValue("Khoa Nội");
    fireEvent.click(screen.getByRole("button", { name: "Bỏ chọn khoa" }));
    expect(screen.getByLabelText("Khoa (tùy chọn)")).toHaveValue("");
    submit();
    expect(onQuerySubmit).toHaveBeenCalledWith(expect.objectContaining({ departmentId: undefined }));
  });
  it("preserves the selected UUID in the URL query", async () => {
    const onQuerySubmit = vi.fn(() => true);
    render(<ReferenceProvider directory={directory()}><OutpatientReportView initialQuery={query} autoLoad={false} onQuerySubmit={onQuerySubmit} /></ReferenceProvider>);
    await select(); submit();
    expect(onQuerySubmit).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ departmentId: department }));
  });
  it("clears the selected scope when editing its label instead of sending a name as an ID", async () => {
    const onQuerySubmit = vi.fn(() => true);
    render(<ReferenceProvider directory={directory()}><OutpatientReportView initialQuery={query} autoLoad={false} onQuerySubmit={onQuerySubmit} /></ReferenceProvider>);
    await select();
    fireEvent.change(screen.getByLabelText("Khoa (tùy chọn)"), { target: { value: "Ngoại" } });
    submit();
    expect(onQuerySubmit).toHaveBeenCalledWith(expect.objectContaining({ departmentId: undefined }));
  });
  it("sends the selected UUID and preserves missing-coverage errors instead of manufacturing zeros", async () => {
    mocks.outpatientDaily.mockRejectedValue(new ApiRequestError("Không có dữ liệu báo cáo", 404));
    render(<ReferenceProvider directory={directory()}><OutpatientReportView initialQuery={query} autoLoad={false} onQuerySubmit={() => false} /></ReferenceProvider>);
    await select(); submit();
    expect(await screen.findByText("Không có dữ liệu báo cáo")).toBeVisible();
    await waitFor(() => expect(mocks.outpatientDaily).toHaveBeenCalledWith("2024-02-29", "2024-02-29", department));
    expect(screen.queryByRole("region", { name: "Kết quả báo cáo ngoại trú" })).not.toBeInTheDocument();
  });
});
