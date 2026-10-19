import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { storeSession } from "@/lib/session";
beforeEach(() => storeSession({ accessToken: "catalog-test", refreshToken: "test", role: "PHARMACIST" }));
import type { DrugDTO } from "../../types";
import type { PageResult } from "@/lib/types";

const mocks = vi.hoisted(() => ({ query: "", replace: vi.fn(), search: vi.fn(), router: { replace: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router, usePathname: () => "/pharmacy/drugs", useSearchParams: () => new URLSearchParams(mocks.query) }));
vi.mock("../../api", () => ({ pharmacyApi: { searchDrugs: mocks.search } }));
import { DrugCatalog } from "./DrugCatalog";

const drug: DrugDTO = { drugId: "00000000-0000-4000-8000-000000000001", drugName: "Thuốc A", activeIngredient: null, unit: "Viên", price: 1000, stockQuantity: 15, lowStockThreshold: 10, expiryDate: "2099-01-01", manufacturer: null, createdAt: "2026-10-12T00:00:00Z", updatedAt: "2026-10-12T00:00:00Z" };
function page(name = "Thuốc A"): PageResult<DrugDTO> { return { content: [{ ...drug, drugName: name }], totalElements: 1, totalPages: 1, number: 0, size: 20 }; }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>((done) => { resolve = done; }); return { promise, resolve }; }

describe("DrugCatalog", () => {
  it("does not offer a read retry or resubmit a server-denied catalog search", async () => {
    mocks.search.mockRejectedValue(new ApiRequestError("Denied", 403, "FORBIDDEN", [], "catalog-denied"));
    render(<DrugCatalog />);
    expect(await screen.findByRole("alert")).toHaveTextContent("catalog-denied");
    expect(screen.queryByRole("button", { name: "Thử lại" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tìm kiếm" })).toBeDisabled();
    fireEvent.submit(screen.getByLabelText("Tìm theo tên thuốc").closest("form")!);
    expect(mocks.router.replace).not.toHaveBeenCalled(); expect(mocks.search).toHaveBeenCalledTimes(1);
  });
  beforeEach(() => { mocks.query = ""; mocks.search.mockReset(); mocks.router.replace.mockReset(); });
  it("uses URL filters and resets pagination when submitting a new name", async () => {
    mocks.query = "keyword=Thu%E1%BB%91c&page=2&size=50";
    mocks.search.mockResolvedValue({ ...page(), number: 2, totalPages: 3, totalElements: 101, size: 50 });
    render(<DrugCatalog />);
    await screen.findByText("101 thuốc");
    expect(mocks.search).toHaveBeenCalledWith({ keyword: "Thuốc", page: 2, size: 50 });
    fireEvent.change(screen.getByLabelText("Tìm theo tên thuốc"), { target: { value: " A & B " } });
    fireEvent.submit(screen.getByRole("button", { name: "Tìm kiếm" }).closest("form")!);
    expect(mocks.router.replace).toHaveBeenCalledWith("/pharmacy/drugs?page=0&size=50&keyword=A+%26+B", { scroll: false });
  });
  it("discards late results from an obsolete query", async () => {
    const older = deferred<PageResult<DrugDTO>>(), newer = deferred<PageResult<DrugDTO>>();
    mocks.search.mockReturnValueOnce(older.promise).mockReturnValueOnce(newer.promise);
    const view = render(<DrugCatalog />);
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(1));
    mocks.query = "keyword=New"; view.rerender(<DrugCatalog />);
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(2));
    await act(async () => newer.resolve(page("Thuốc mới")));
    expect(screen.getByRole("link", { name: "Thuốc mới" })).toBeVisible();
    await act(async () => older.resolve(page("Thuốc cũ")));
    expect(screen.queryByText("Thuốc cũ")).not.toBeInTheDocument();
  });
  it("does not expose an old page under a new query that fails, and allows retry", async () => {
    mocks.search.mockResolvedValueOnce(page()).mockRejectedValueOnce(new ApiRequestError("Không tải được", 503, "UNAVAILABLE", [], "corr-catalog")).mockResolvedValueOnce(page("Thuốc B"));
    const view = render(<DrugCatalog />); await screen.findByRole("link", { name: "Thuốc A" });
    mocks.query = "keyword=B"; view.rerender(<DrugCatalog />);
    expect(await screen.findByRole("alert")).toHaveTextContent("corr-catalog");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("link", { name: "Thuốc B" })).toBeVisible();
  });
  it("moves an out-of-range bookmark to the last server page", async () => {
    mocks.query = "page=9&size=20"; mocks.search.mockResolvedValue({ ...page(), number: 9, totalPages: 2 });
    render(<DrugCatalog />);
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/pharmacy/drugs?page=1&size=20", { scroll: false }));
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });
  it("shows explicit empty search results", async () => {
    mocks.search.mockResolvedValue({ ...page(), content: [], totalElements: 0, totalPages: 0 });
    render(<DrugCatalog />);
    expect(await screen.findByText(/Không tìm thấy thuốc phù hợp/)).toBeVisible();
  });
  it("redirects rejected authentication to login", async () => {
    mocks.search.mockRejectedValue(new ApiRequestError("Hết phiên", 401)); render(<DrugCatalog />);
    await waitFor(() => expect(mocks.router.replace).toHaveBeenCalledWith("/login"));
  });
});
