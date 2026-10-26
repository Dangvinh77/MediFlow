import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PageResult } from "@/lib/types";
import type { PatientDTO } from "../types";

const mocks = vi.hoisted(() => ({
  search: vi.fn(),
  getRole: vi.fn(() => "ADMIN"),
  router: { replace: vi.fn() },
}));

vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({
  getRole: mocks.getRole,
  subscribeToAuthChanges: () => () => {},
}));
vi.mock("../api", () => ({ patientApi: { search: mocks.search } }));

import { PatientTable } from "./PatientTable";

const patient: PatientDTO = {
  maBenhNhan: "550e8400-e29b-41d4-a716-446655440000",
  hoTen: "Nguyen Van A",
  ngaySinh: "1990-01-15",
  gioiTinh: "M",
  soCmnd: "001234567890",
  diaChi: null,
  soDienThoai: null,
  email: null,
  bhytSo: null,
  createdAt: "2026-08-13T10:00:00Z",
  updatedAt: "2026-08-13T10:00:00Z",
};

function page(
  overrides: Partial<PageResult<PatientDTO>> = {},
): PageResult<PatientDTO> {
  return {
    content: [patient],
    totalElements: 41,
    totalPages: 3,
    number: 0,
    size: 20,
    ...overrides,
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (cause: unknown) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

describe("PatientTable read logic", () => {
  beforeEach(() => {
    mocks.search.mockReset();
    mocks.getRole.mockReturnValue("ADMIN");
    mocks.router.replace.mockReset();
  });

  it("does not redirect on a late 401 after unmount", async () => {
    const response = deferred<PageResult<PatientDTO>>(); mocks.search.mockReturnValue(response.promise);
    const view = render(<PatientTable />);
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(1));
    view.unmount();
    const { ApiRequestError } = await import("@/lib/api");
    await act(async () => response.reject(new ApiRequestError("expired", 401)));
    expect(mocks.router.replace).not.toHaveBeenCalled();
  });

  it("searches from page zero, keeps the active keyword while paging, and resets filters", async () => {
    mocks.search
      .mockResolvedValueOnce(page())
      .mockResolvedValueOnce(page())
      .mockResolvedValueOnce(page({ number: 1 }))
      .mockResolvedValueOnce(page());

    render(<PatientTable />);
    await screen.findByRole("link", { name: "Xem chi tiết Nguyen Van A" });
    expect(mocks.search).toHaveBeenNthCalledWith(1, {
      keyword: undefined,
      page: 0,
      size: 20,
    });

    fireEvent.change(screen.getByLabelText("Từ khóa bệnh nhân"), {
      target: { value: "  Nguyen  " },
    });
    fireEvent.submit(screen.getByLabelText("Từ khóa bệnh nhân").closest("form")!);
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(2));
    expect(mocks.search).toHaveBeenNthCalledWith(2, {
      keyword: "Nguyen",
      page: 0,
      size: 20,
    });

    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(3));
    expect(mocks.search).toHaveBeenNthCalledWith(3, {
      keyword: "Nguyen",
      page: 1,
      size: 20,
    });

    fireEvent.click(screen.getByRole("button", { name: "Xóa lọc" }));
    await waitFor(() => expect(mocks.search).toHaveBeenCalledTimes(4));
    expect(mocks.search).toHaveBeenNthCalledWith(4, {
      keyword: undefined,
      page: 0,
      size: 20,
    });
    expect(screen.getByLabelText("Từ khóa bệnh nhân")).toHaveValue("");
  });

  it("shows an empty result distinctly from a failed request", async () => {
    mocks.search.mockResolvedValue(page({ content: [], totalElements: 0, totalPages: 0 }));
    render(<PatientTable />);

    expect(await screen.findByText("Chưa có bệnh nhân phù hợp.")).toBeVisible();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows a network error without stale rows and retries the same query", async () => {
    mocks.search
      .mockResolvedValueOnce(page())
      .mockRejectedValueOnce(new Error("Mất kết nối"))
      .mockResolvedValueOnce(page());
    render(<PatientTable />);
    await screen.findByRole("link", { name: "Xem chi tiết Nguyen Van A" });

    fireEvent.change(screen.getByLabelText("Từ khóa bệnh nhân"), {
      target: { value: "retry me" },
    });
    fireEvent.submit(screen.getByLabelText("Từ khóa bệnh nhân").closest("form")!);
    expect(await screen.findByRole("alert")).toHaveTextContent("Mất kết nối");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await screen.findByRole("link", { name: "Xem chi tiết Nguyen Van A" });
    expect(mocks.search).toHaveBeenNthCalledWith(3, {
      keyword: "retry me",
      page: 0,
      size: 20,
    });
  });

  it("ignores late results from an older search request", async () => {
    const older = deferred<PageResult<PatientDTO>>();
    const newer = deferred<PageResult<PatientDTO>>();
    mocks.search
      .mockResolvedValueOnce(page())
      .mockReturnValueOnce(older.promise)
      .mockReturnValueOnce(newer.promise);
    render(<PatientTable />);
    await screen.findByRole("link", { name: "Xem chi tiết Nguyen Van A" });

    const keyword = screen.getByLabelText("Từ khóa bệnh nhân");
    fireEvent.change(keyword, { target: { value: "older" } });
    fireEvent.submit(keyword.closest("form")!);
    fireEvent.change(keyword, { target: { value: "newer" } });
    fireEvent.submit(keyword.closest("form")!);

    const newerPatient = { ...patient, maBenhNhan: "new-id", hoTen: "Patient Newer" };
    await act(async () => newer.resolve(page({ content: [newerPatient] })));
    expect(await screen.findByRole("link", { name: "Xem chi tiết Patient Newer" })).toBeVisible();

    const olderPatient = { ...patient, maBenhNhan: "old-id", hoTen: "Patient Older" };
    await act(async () => older.resolve(page({ content: [olderPatient] })));
    expect(screen.queryByText("Patient Older")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem chi tiết Patient Newer" })).toBeVisible();
  });

  it("keeps patient browsing available to doctors without showing create controls", async () => {
    mocks.getRole.mockReturnValue("DOCTOR");
    mocks.search.mockResolvedValue(page({ content: [patient], totalPages: 1 }));
    render(<PatientTable />);

    expect(await screen.findByRole("link", { name: "Xem chi tiết Nguyen Van A" })).toBeVisible();
    expect(screen.queryByRole("link", { name: "Thêm bệnh nhân" })).not.toBeInTheDocument();
  });
});
