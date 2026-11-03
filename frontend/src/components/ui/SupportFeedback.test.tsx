import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { AsyncState } from "./AsyncState";
import { CommandFeedback } from "./CommandFeedback";

const trace = "10000000-0000-4000-8000-000000000001";
it.each(["read", "command"])("keeps %s error identifiers inside deliberate support access", (kind) => {
  render(kind === "read"
    ? <AsyncState kind="error" message="Chưa thể tải dữ liệu" correlationId={trace} onRetry={vi.fn()} />
    : <CommandFeedback uncertain error={{ status: 503, message: "Chưa thể tải dữ liệu", code: "SOURCE_UNAVAILABLE", correlationId: trace, details: [] }} />);
  expect(screen.getByText("Chưa thể tải dữ liệu")).toBeVisible();
  expect(screen.getByText(new RegExp(trace))).not.toBeVisible();
  if (kind === "command") expect(screen.getByText(/Chưa xác định thao tác/)).toBeVisible();
  fireEvent.click(screen.getByText("Thông tin hỗ trợ"));
  expect(screen.getByText(new RegExp(trace))).toBeVisible();
});

it("keeps retry ahead of support details in keyboard order", async () => {
  const user = userEvent.setup();
  render(<div tabIndex={-1} role="group" aria-label="Lỗi đọc"><AsyncState kind="error" message="Chưa thể tải dữ liệu" correlationId={trace} onRetry={vi.fn()} /></div>);
  screen.getByRole("group", { name: "Lỗi đọc" }).focus();
  await user.tab();
  expect(screen.getByRole("button", { name: "Thử lại" })).toHaveFocus();
});
