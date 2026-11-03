import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ReferenceProvider } from "./ReferenceProvider";
import { ReferenceText } from "./ReferenceText";

const id = "11111111-1111-4111-8111-111111111111";
describe("reference label privacy", () => {
  it.each([id, `Hồ sơ ${id}`])("keeps unsafe fallback %s out of normal text", (fallback) => {
    render(<ReferenceText value={id} fallback={fallback} technical />);
    expect(screen.getByText("Thông tin tham chiếu")).toBeVisible();
    expect(screen.getByText(id)).not.toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Thông tin kỹ thuật" }));
    expect(screen.getByText(id)).toBeVisible();
  });

  it.each([id, `Hồ sơ ${id}`])("does not treat resolver UUID %s as a patient name", async (label) => {
    const directory = { canSearch: () => true, search: vi.fn(), resolve: vi.fn().mockResolvedValue(label) };
    render(<ReferenceProvider directory={directory}><ReferenceText kind="patient" value={id} fallback="Bệnh nhân" /></ReferenceProvider>);
    expect(await screen.findByText("Bệnh nhân · Chưa tải được tên")).toBeVisible();
    expect(screen.getByText(id)).not.toBeVisible();
    expect(screen.getByRole("button", { name: "Tải lại tên" })).toBeVisible();
  });
});
