import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { AsyncState } from "./AsyncState";

it("centres the loader across its container, with one status announcement", () => {
  render(<AsyncState kind="loading" message="Đang tải dữ liệu…" />);
  const loader = screen.getByRole("status", { name: "Đang tải dữ liệu…" });
  expect(loader).toHaveClass("items-center");
  expect(loader.parentElement).toHaveClass("w-full", "justify-center");
});
