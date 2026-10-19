import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ router: { push: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));

import { PrescriptionAdjustmentLookupForm } from "./PrescriptionAdjustmentLookupForm";

describe("Prescription adjustment lookup form", () => {
  beforeEach(() => {
    mocks.router.push.mockReset();
  });

  it("navigates to the refund-due detail route for a valid prescription UUID", () => {
    render(<PrescriptionAdjustmentLookupForm />);
    fireEvent.change(screen.getByLabelText("Mã đơn thuốc", { exact: false }), {
      target: { value: "10000000-0000-4000-8000-000000000001" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Xem khoản hoàn tiền" }));
    expect(mocks.router.push).toHaveBeenCalledWith("/billing/prescription-adjustments/10000000-0000-4000-8000-000000000001");
  });

  it("rejects a malformed prescription id without navigating", () => {
    render(<PrescriptionAdjustmentLookupForm />);
    fireEvent.change(screen.getByLabelText("Mã đơn thuốc", { exact: false }), {
      target: { value: "not-a-uuid" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Xem khoản hoàn tiền" }));
    expect(screen.getByText("Mã đơn thuốc phải là UUID hợp lệ.")).toBeVisible();
    expect(mocks.router.push).not.toHaveBeenCalled();
  });
});
