import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Role } from "@/lib/roles";

const mocks = vi.hoisted(() => ({ role: "ADMIN" as Role | null, push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: mocks.push }) }));
vi.mock("@/lib/auth", () => ({ getRole: () => mocks.role, subscribeToAuthChanges: () => () => {} }));
import { PrescriptionLookup } from "./PrescriptionLookup";

describe("PrescriptionLookup", () => {
  beforeEach(() => { mocks.role = "ADMIN"; mocks.push.mockReset(); });
  it("gives compact and standalone instances unique, correctly associated inputs", () => {
    render(<><PrescriptionLookup /><PrescriptionLookup variant="compact" /></>);
    const inputs = screen.getAllByLabelText("Mã đơn thuốc");
    expect(inputs).toHaveLength(2);
    expect(inputs[0].id).not.toBe(inputs[1].id);
  });
  it("rejects malformed UUID and returns focus to the input", async () => {
    const user = userEvent.setup(); render(<PrescriptionLookup variant="compact" />);
    await user.type(screen.getByLabelText("Mã đơn thuốc"), "invalid");
    await user.click(screen.getByRole("button", { name: "Tra cứu" }));
    expect(screen.getByRole("alert")).toHaveTextContent("Mã đơn thuốc không hợp lệ");
    expect(screen.getByLabelText("Mã đơn thuốc")).toHaveFocus();
    expect(mocks.push).not.toHaveBeenCalled();
  });
  it("navigates to the exact trimmed known ID for a doctor", async () => {
    mocks.role = "DOCTOR"; const user = userEvent.setup(); render(<PrescriptionLookup />);
    const id = "00000000-0000-4000-8000-000000000001";
    await user.type(screen.getByLabelText("Mã đơn thuốc"), " " + id + " ");
    await user.click(screen.getByRole("button", { name: "Tra cứu" }));
    expect(mocks.push).toHaveBeenCalledWith("/pharmacy/prescriptions/" + id);
  });
  it.each(["MANAGER", "NURSE", "CASHIER"] as Role[])("does not offer lookup to %s", (role) => {
    mocks.role = role; render(<PrescriptionLookup />);
    expect(within(screen.getByRole("alert")).queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Tra cứu" })).not.toBeInTheDocument();
  });
});
