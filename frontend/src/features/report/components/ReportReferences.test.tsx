import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReferenceProvider, type ReferenceDirectory } from "@/components/references/ReferenceProvider";
import { storeSession } from "@/lib/session";
import { ApiRequestError } from "@/lib/api";
import { DailyReportView } from "./DailyReportView";
import { MonthlyReportView } from "./MonthlyReportView";
import { TopMedicinesView } from "./TopMedicinesView";
import { OutpatientReportView } from "./OutpatientReportView";

const mocks = vi.hoisted(() => ({ daily: vi.fn(), get: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("../api", () => ({ reportApi: { daily: mocks.daily } }));
vi.mock("@/lib/api", async original => ({ ...await original<typeof import("@/lib/api")>(), api: { get: mocks.get } }));
const department = "00000000-0000-4000-8000-000000000001";
function directory(): ReferenceDirectory {
  return { canSearch: () => true, search: vi.fn().mockResolvedValue({ items: [{ id: department, label: "Khoa Nội" }], hasMore: false }), resolve: vi.fn().mockResolvedValue("Khoa Nội") };
}
async function select() {
  const input = screen.getByLabelText("Khoa (tùy chọn)");
  fireEvent.change(input, { target: { value: "Nội" } });
  fireEvent.click(within(input.parentElement!).getByRole("button", { name: "Tìm lựa chọn" }));
  fireEvent.click(await screen.findByRole("button", { name: "Khoa Nội" }));
}
function submit() { fireEvent.submit(screen.getByLabelText("Ngày báo cáo").closest("form")!); }
describe("Report shared department selection", () => {
  beforeEach(() => {
    vi.clearAllMocks(); localStorage.clear(); storeSession({ accessToken: "manager", refreshToken: "refresh", role: "MANAGER" });
    mocks.daily.mockResolvedValue({ reportDate: "2024-02-29", departmentId: department, visitCount: 1, labCount: 0, prescriptionCount: 0, revenue: 0 });
  });
  it("sends the selected UUID and uses the dashboard directory for result scope", async () => {
    const source = directory();
    render(<ReferenceProvider directory={source}><DailyReportView initialQuery={{ date: "2024-02-29" }} /></ReferenceProvider>);
    await select(); submit();
    await screen.findByRole("heading", { name: "Báo cáo ngày 29/02/2024" });
    expect(mocks.daily).toHaveBeenCalledWith("2024-02-29", department);
    await waitFor(() => expect(source.resolve).toHaveBeenCalledWith("department", department));
    expect(mocks.get).not.toHaveBeenCalled();
  });
  it("clears the selected scope when editing its label instead of sending a name as an ID", async () => {
    render(<ReferenceProvider directory={directory()}><DailyReportView initialQuery={{ date: "2024-02-29" }} /></ReferenceProvider>);
    await select();
    fireEvent.change(screen.getByLabelText("Khoa (tùy chọn)"), { target: { value: "Ngoại" } });
    submit(); await waitFor(() => expect(mocks.daily).toHaveBeenCalledWith("2024-02-29", undefined));
  });
  it("preserves missing-data errors instead of manufacturing a zero report", async () => {
    mocks.daily.mockRejectedValue(new ApiRequestError("Không có dữ liệu báo cáo", 404));
    render(<ReferenceProvider directory={directory()}><DailyReportView initialQuery={{ date: "2024-02-29" }} /></ReferenceProvider>);
    await select(); submit();
    expect(await screen.findByText("Không có dữ liệu báo cáo")).toBeVisible();
    expect(screen.queryByRole("definition")).not.toBeInTheDocument();
  });
  it.each(["monthly", "top-medicines", "outpatient"])("preserves the selected UUID in the %s URL query", async view => {
    const onQuerySubmit = vi.fn(() => true);
    const content = view === "monthly"
      ? <MonthlyReportView initialQuery={{ month: 2, year: 2024 }} onQuerySubmit={onQuerySubmit} />
      : view === "top-medicines"
        ? <TopMedicinesView initialQuery={{ fromDate: "2024-02-29", toDate: "2024-02-29", limit: 10 }} onQuerySubmit={onQuerySubmit} />
        : <OutpatientReportView initialQuery={{ mode: "daily", fromDate: "2024-02-29", toDate: "2024-02-29", year: 2024 }} autoLoad={false} onQuerySubmit={onQuerySubmit} />;
    render(<ReferenceProvider directory={directory()}>{content}</ReferenceProvider>);
    await select();
    fireEvent.submit(screen.getByLabelText("Khoa (tùy chọn)").closest("form")!);
    expect(onQuerySubmit).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({ departmentId: department }));
  });
});
