import { act, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { storeSession } from "@/lib/session";
import { ReportSessionBoundary } from "./ReportSessionBoundary";
import { ReportDepartmentName } from "./ReportDepartmentName";

const mocks = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api", async original => ({ ...await original<typeof import("@/lib/api")>(), api: { get: mocks.get } }));
const first = "00000000-0000-4000-8000-000000000001";
const second = "00000000-0000-4000-8000-000000000002";
function view(id: string | null = first) {
  return <ReportSessionBoundary><ReportDepartmentName id={id} /><ReportDepartmentName id={id} showId={false} /></ReportSessionBoundary>;
}
describe("Report department display", () => {
  beforeEach(() => { mocks.get.mockReset(); localStorage.clear(); storeSession({ accessToken: "manager", refreshToken: "refresh", role: "MANAGER" }); });
  it("shares the authorized name request between filter and report scope", async () => {
    mocks.get.mockResolvedValue({ departmentId: first, departmentName: "Khoa Nội" });
    render(view());
    expect(await screen.findAllByText("Khoa Nội")).toHaveLength(2);
    expect(screen.queryByText(first)).not.toBeInTheDocument();
    expect(mocks.get).toHaveBeenCalledExactlyOnceWith(`/v1/org/departments/${first}`);
  });
  it("does not look up the whole-hospital scope or invalid drafts", () => {
    const rendered = render(view(null));
    expect(screen.getAllByText("Toàn bệnh viện")).toHaveLength(2);
    rendered.rerender(view("invalid"));
    expect(mocks.get).not.toHaveBeenCalled();
  });
  it.each([403, 404, 503])("does not expose the scope ID when lookup returns %s", async status => {
    mocks.get.mockRejectedValue(new ApiRequestError("failure", status));
    render(view());
    await waitFor(() => expect(screen.queryAllByText("Đang tra cứu tên khoa…")).toHaveLength(0));
    expect(screen.queryByText(first)).not.toBeInTheDocument();
    expect(screen.queryByText("Toàn bệnh viện")).not.toBeInTheDocument();
  });
  it("never assigns a mismatched response to the selected department", async () => {
    mocks.get.mockResolvedValue({ departmentId: second, departmentName: "Sai khoa" });
    render(view());
    expect(await screen.findAllByText("Chưa tải được tên khoa")).toHaveLength(2);
    expect(screen.queryByText("Sai khoa")).not.toBeInTheDocument();
  });
  it("does not overwrite a new filter with an old response", async () => {
    let resolve!: (value: unknown) => void;
    mocks.get.mockReturnValueOnce(new Promise(value => { resolve = value; })).mockResolvedValue({ departmentId: second, departmentName: "Khoa mới" });
    const rendered = render(view());
    await waitFor(() => expect(mocks.get).toHaveBeenCalledTimes(1));
    rendered.rerender(view(second));
    expect(await screen.findAllByText("Khoa mới")).toHaveLength(2);
    await act(async () => resolve({ departmentId: first, departmentName: "Khoa cũ" }));
    expect(screen.queryByText("Khoa cũ")).not.toBeInTheDocument();
  });
  it("clears names and reloads under a new same-role session", async () => {
    mocks.get.mockResolvedValueOnce({ departmentId: first, departmentName: "Tên phiên cũ" }).mockResolvedValue({ departmentId: first, departmentName: "Tên phiên mới" });
    render(view());
    expect(await screen.findAllByText("Tên phiên cũ")).toHaveLength(2);
    act(() => storeSession({ accessToken: "new", refreshToken: "refresh-new", role: "MANAGER" }));
    expect(screen.queryByText("Tên phiên cũ")).not.toBeInTheDocument();
    expect(await screen.findAllByText("Tên phiên mới")).toHaveLength(2);
  });
});
