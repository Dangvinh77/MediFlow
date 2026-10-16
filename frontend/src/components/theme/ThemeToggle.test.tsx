import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ThemeToggle } from "./ThemeToggle";

const setTheme = vi.fn();
vi.mock("./ThemeProvider", () => ({
  useTheme: () => ({ theme: "system", setTheme }),
}));

describe("Theme selector", () => {
  it("retains all existing themes and lets the user select each explicitly", async () => {
    render(<ThemeToggle />);
    const selector = screen.getByRole("combobox", { name: "Giao diện" });
    expect(selector).toHaveValue("system");
    expect(screen.getAllByRole("option")).toHaveLength(3);
    for (const theme of ["light", "dark", "system"]) {
      await userEvent.selectOptions(selector, theme);
      expect(setTheme).toHaveBeenLastCalledWith(theme);
    }
  });
});
