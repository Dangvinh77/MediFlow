import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { ReferenceProvider, type ReferenceDirectory, type ReferencePage } from "@/components/references/ReferenceProvider";
import { PrescriptionRecordPicker } from "./PrescriptionRecordPicker";

const record = { id: "record-a", label: "Hồ sơ 25/10/2026 · Đau đầu" };
const second = { id: "record-b", label: "Hồ sơ 26/10/2026 · Tái khám" };
function directory(search: ReferenceDirectory["search"]): ReferenceDirectory {
  return { canSearch: () => true, resolve: vi.fn(), search };
}
function Picker({ source, patientId = "patient-a", disabled = false }: { source: ReferenceDirectory; patientId?: string; disabled?: boolean }) {
  const [value, setValue] = useState("");
  return <ReferenceProvider directory={source}>
    <label htmlFor="record">Hồ sơ khám</label>
    <PrescriptionRecordPicker id="record" patientId={patientId} value={value} onValueChange={setValue} disabled={disabled} />
    <output data-testid="selected">{value}</output>
  </ReferenceProvider>;
}

describe("patient-scoped prescription records", () => {
  it("waits for a patient before reading records", () => {
    const search = vi.fn();
    render(<Picker source={directory(search)} patientId="" />);
    expect(search).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Hồ sơ khám")).toBeDisabled();
  });
  it("loads and selects the only record without a search or selection action", async () => {
    const search = vi.fn().mockResolvedValue({ items: [record], hasMore: false });
    render(<Picker source={directory(search)} />);
    await waitFor(() => expect(screen.getByTestId("selected")).toHaveTextContent(record.id));
    expect(search).toHaveBeenCalledWith("record", "", 0, "patient-a");
    expect(screen.getByLabelText("Hồ sơ khám")).toHaveValue(record.label);
    expect(screen.getByLabelText("Hồ sơ khám")).toHaveAttribute("readonly");
    expect(screen.queryByRole("button", { name: "Tìm lựa chọn" })).not.toBeInTheDocument();
  });
  it("shows the patient's records immediately and requires a choice when there are several", async () => {
    render(<Picker source={directory(vi.fn().mockResolvedValue({ items: [record, second], hasMore: false }))} />);
    await screen.findByRole("option", { name: second.label });
    expect(screen.getByTestId("selected")).toBeEmptyDOMElement();
    fireEvent.change(screen.getByLabelText("Hồ sơ khám"), { target: { value: second.id } });
    expect(screen.getByTestId("selected")).toHaveTextContent(second.id);
  });
  it("does not auto-select one item when additional pages exist and retains loaded choices", async () => {
    const search = vi.fn().mockResolvedValueOnce({ items: [record], hasMore: true })
      .mockResolvedValueOnce({ items: [second], hasMore: false });
    render(<Picker source={directory(search)} />);
    fireEvent.click(await screen.findByRole("button", { name: "Xem thêm hồ sơ" }));
    await screen.findByRole("option", { name: second.label });
    expect(screen.getByRole("option", { name: record.label })).toBeInTheDocument();
    expect(screen.getByTestId("selected")).toBeEmptyDOMElement();
    expect(search).toHaveBeenLastCalledWith("record", "", 1, "patient-a");
  });
  it("shows an empty state without inventing a record", async () => {
    render(<Picker source={directory(vi.fn().mockResolvedValue({ items: [], hasMore: false }))} />);
    await screen.findByText("Bệnh nhân chưa có hồ sơ khám.", { selector: "p" });
    expect(screen.getByTestId("selected")).toBeEmptyDOMElement();
    expect(screen.getByLabelText("Hồ sơ khám")).toBeDisabled();
  });
  it("retries failed reads explicitly", async () => {
    const search = vi.fn().mockRejectedValueOnce(new Error("Không thể tải hồ sơ"))
      .mockResolvedValueOnce({ items: [record], hasMore: false });
    render(<Picker source={directory(search)} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải hồ sơ");
    fireEvent.click(screen.getByRole("button", { name: "Thử tải lại danh sách hồ sơ" }));
    await waitFor(() => expect(screen.getByTestId("selected")).toHaveTextContent(record.id));
  });
  it("ignores a previous patient's response after switching patients", async () => {
    let resolveOld!: (page: ReferencePage) => void;
    const search = vi.fn().mockReturnValueOnce(new Promise<ReferencePage>(resolve => { resolveOld = resolve; }))
      .mockResolvedValueOnce({ items: [second], hasMore: false });
    const source = directory(search);
    const view = render(<Picker source={source} />);
    view.rerender(<Picker source={source} patientId="patient-b" />);
    await waitFor(() => expect(screen.getByTestId("selected")).toHaveTextContent(second.id));
    await act(async () => resolveOld({ items: [record], hasMore: false }));
    expect(screen.getByLabelText("Hồ sơ khám")).toHaveValue(second.label);
    expect(search).toHaveBeenLastCalledWith("record", "", 0, "patient-b");
  });
  it("locks selection together with a pending prescription", async () => {
    const source = directory(vi.fn().mockResolvedValue({ items: [record, second], hasMore: false }));
    const view = render(<Picker source={source} />);
    await screen.findByRole("option", { name: record.label });
    view.rerender(<Picker source={source} disabled />);
    expect(screen.getByLabelText("Hồ sơ khám")).toBeDisabled();
  });
});
