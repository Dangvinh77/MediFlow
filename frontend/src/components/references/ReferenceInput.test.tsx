import { act, render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ReferenceProvider, type ReferenceDirectory } from "./ReferenceProvider";
import { ReferenceInput } from "./ReferenceInput";
import { ReferenceText } from "./ReferenceText";

const id = "11111111-1111-4111-8111-111111111111";
function directory(overrides: Partial<ReferenceDirectory> = {}): ReferenceDirectory {
  return { canSearch: () => true, search: vi.fn().mockResolvedValue({ items: [{ id, label: "Nguyễn An · 12/05/1995" }], hasMore: false }), resolve: vi.fn().mockResolvedValue("Nguyễn An"), ...overrides };
}

describe("reference selection", () => {
  it("dismisses the dropdown without changing the selection and restores input focus", async () => {
    const onValueChange = vi.fn();
    render(<ReferenceProvider directory={directory()}><ReferenceInput aria-label="Bệnh nhân" placeholder="Tìm bệnh nhân theo tên" kind="patient" value="" onValueChange={onValueChange} /></ReferenceProvider>);
    expect(screen.getByPlaceholderText("Tìm bệnh nhân theo tên")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Tìm lựa chọn" }));
    const option = await screen.findByRole("button", { name: "Nguyễn An · 12/05/1995" });
    option.focus(); fireEvent.keyDown(option, { key: "Escape" });
    expect(screen.queryByRole("button", { name: "Nguyễn An · 12/05/1995" })).not.toBeInTheDocument();
    expect(screen.getByRole("textbox")).toHaveFocus();
    expect(onValueChange).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Tìm lựa chọn" }));
    fireEvent.click(await screen.findByRole("button", { name: "Đóng danh sách" }));
    expect(screen.getByRole("textbox")).toHaveFocus();
    expect(onValueChange).not.toHaveBeenCalled();
  });

  it("does not reopen a dismissed dropdown when a pending search finishes", async () => {
    let finish!: (value: { items: { id: string; label: string }[]; hasMore: boolean }) => void;
    const search = vi.fn(() => new Promise<{ items: { id: string; label: string }[]; hasMore: boolean }>(resolve => { finish = resolve; }));
    render(<ReferenceProvider directory={directory({ search })}><ReferenceInput aria-label="Bệnh nhân" kind="patient" value="" onValueChange={vi.fn()} /></ReferenceProvider>);
    fireEvent.click(screen.getByRole("button", { name: "Tìm lựa chọn" }));
    fireEvent.keyDown(screen.getByRole("textbox"), { key: "Escape" });
    await act(async () => finish({ items: [{ id, label: "Nguyễn An" }], hasMore: false }));
    expect(screen.queryByRole("button", { name: "Nguyễn An" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tìm lựa chọn" })).toBeEnabled();
  });

  it("shows names and emits only the selected server identity; editing clears that identity", async () => {
    const user = userEvent.setup(); const onValueChange = vi.fn(); const source = directory();
    const { rerender } = render(<ReferenceProvider directory={source}><ReferenceInput id="patient" aria-label="Bệnh nhân" kind="patient" value="" onValueChange={onValueChange} /></ReferenceProvider>);
    await user.type(screen.getByRole("textbox", { name: "Bệnh nhân" }), "An");
    await user.click(screen.getByRole("button", { name: "Tìm lựa chọn" }));
    await user.click(await screen.findByRole("button", { name: "Nguyễn An · 12/05/1995" }));
    expect(onValueChange).toHaveBeenLastCalledWith(id);
    rerender(<ReferenceProvider directory={source}><ReferenceInput id="patient" aria-label="Bệnh nhân" kind="patient" value={id} onValueChange={onValueChange} /></ReferenceProvider>);
    expect(screen.getByRole("textbox")).toHaveValue("Nguyễn An · 12/05/1995");
    expect(screen.queryByDisplayValue(id)).not.toBeInTheDocument();
    await user.type(screen.getByRole("textbox"), " khác");
    expect(onValueChange).toHaveBeenLastCalledWith("");
  });

  it("ignores old searches after the query changes and exposes retry after a failed read", async () => {
    let finish!: (value: { items: { id: string; label: string }[]; hasMore: boolean }) => void;
    const search = vi.fn().mockImplementationOnce(() => new Promise(resolve => { finish = resolve; })).mockRejectedValueOnce(new Error("Mất kết nối"));
    render(<ReferenceProvider directory={directory({ search })}><ReferenceInput id="patient" aria-label="Bệnh nhân" kind="patient" value="" onValueChange={vi.fn()} /></ReferenceProvider>);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "cũ" } });
    fireEvent.click(screen.getByRole("button", { name: "Tìm lựa chọn" }));
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "mới" } });
    finish({ items: [{ id, label: "Kết quả cũ" }], hasMore: false });
    await waitFor(() => expect(screen.queryByText("Kết quả cũ")).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Tìm lựa chọn" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Mất kết nối");
  });

  it("does not call a forbidden directory and keeps technical entry explicit", () => {
    const source = directory({ canSearch: () => false });
    render(<ReferenceProvider directory={source}><ReferenceInput id="patient" aria-label="Bệnh nhân" kind="patient" value="" onValueChange={vi.fn()} /></ReferenceProvider>);
    expect(source.search).not.toHaveBeenCalled();
    expect(screen.getByText(/Chưa có quyền tìm kiếm/)).toBeInTheDocument();
    expect(screen.getByText("Nhập mã tham chiếu").closest("details")).not.toHaveAttribute("open");
  });

  it("clears previous-session labels immediately when directory changes", async () => {
    const { rerender } = render(<ReferenceProvider directory={directory()}><ReferenceText kind="patient" value={id} /></ReferenceProvider>);
    expect(await screen.findByText("Nguyễn An")).toBeInTheDocument();
    rerender(<ReferenceProvider directory={directory({ resolve: () => new Promise(() => {}) })}><ReferenceText kind="patient" value={id} /></ReferenceProvider>);
    expect(screen.queryByText("Nguyễn An")).not.toBeInTheDocument();
    expect(screen.getByText(id)).not.toBeVisible();
  });
  it("offers the next result page even when the current page has no match", async () => {
    const search = vi.fn().mockResolvedValueOnce({ items: [], hasMore: true }).mockResolvedValueOnce({ items: [{ id, label: "Nguyễn An" }], hasMore: false });
    render(<ReferenceProvider directory={directory({ search })}><ReferenceInput aria-label="Bệnh nhân" kind="patient" value="" onValueChange={vi.fn()} /></ReferenceProvider>);
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "An" } });
    fireEvent.click(screen.getByRole("button", { name: "Tìm lựa chọn" }));
    expect(await screen.findByText("Không có kết quả phù hợp.")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Trang sau" }));
    expect(await screen.findByRole("button", { name: "Nguyễn An" })).toBeVisible();
    expect(search).toHaveBeenLastCalledWith("patient", "An", 1, undefined);
  });

  it("collapses technical identity again when the displayed reference changes", () => {
    const { rerender } = render(<ReferenceText value={id} fallback="Hóa đơn" technical />);
    expect(screen.getByText(id)).not.toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Thông tin kỹ thuật" }));
    expect(screen.getByText(id)).toBeVisible();
    rerender(<ReferenceText value="another-id" fallback="Hóa đơn" technical />);
    expect(screen.getByText("another-id")).not.toBeVisible();
    expect(screen.getByRole("button")).toHaveAttribute("aria-expanded", "false");
  });

  it("reports a failed name read without leaving an endless loading label", async () => {
    render(<ReferenceProvider directory={directory({ resolve: vi.fn().mockRejectedValue(new Error("Unavailable")) })}><ReferenceText kind="patient" value={id} fallback="Bệnh nhân" /></ReferenceProvider>);
    expect(await screen.findByText("Bệnh nhân · Chưa tải được tên")).toBeVisible();
    expect(screen.getByText(id)).not.toBeVisible();
  });

  it("retries a failed name lookup without exposing the UUID", async () => {
    const resolve = vi.fn().mockRejectedValueOnce(new Error("Unavailable")).mockResolvedValueOnce("Nguyễn An");
    render(<ReferenceProvider directory={directory({ resolve })}><ReferenceText kind="patient" value={id} fallback="Bệnh nhân" /></ReferenceProvider>);
    expect(await screen.findByText("Bệnh nhân · Chưa tải được tên")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Tải lại tên" }));
    expect(await screen.findByText("Nguyễn An")).toBeVisible();
    expect(resolve).toHaveBeenCalledTimes(2);
    expect(screen.getByText(id)).not.toBeVisible();
  });

});
