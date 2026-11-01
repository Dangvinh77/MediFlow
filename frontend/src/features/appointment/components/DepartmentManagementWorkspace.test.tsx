import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { DepartmentManagementWorkspace } from "./DepartmentManagementWorkspace";
vi.mock("@/lib/capabilities", () => ({ useCapabilities: () => ({ data: { role: "DOCTOR", managementDepartments: [] }, loading: false, error: null }), refreshCapabilities: vi.fn() }));
it("denies a doctor without an effective appointment before loading department data", () => {
  render(<DepartmentManagementWorkspace enabled />);
  expect(screen.getByRole("alert")).toHaveTextContent("không có bổ nhiệm");
  expect(screen.queryByRole("button", { name: "Tải buổi khám" })).not.toBeInTheDocument();
});
