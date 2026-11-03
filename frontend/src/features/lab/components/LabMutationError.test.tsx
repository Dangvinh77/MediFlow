import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { LabMutationError } from "./LabMutationError";

it("shows a useful error while keeping its technical reference collapsed", () => {
  render(<LabMutationError status={409} message="Yêu cầu đã thay đổi" correlationId="10000000-0000-4000-8000-000000000001" />);
  expect(screen.getByText("Yêu cầu đã thay đổi")).toBeVisible();
  const trace = screen.getByText(/10000000-/);
  expect(trace).not.toBeVisible();
  fireEvent.click(screen.getByText("Thông tin hỗ trợ"));
  expect(trace).toBeVisible();
});
