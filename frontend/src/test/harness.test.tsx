import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

describe("frontend test harness", () => {
  it("renders React in jsdom with DOM matchers", () => {
    render(<button type="button">Tạo đợt nội trú</button>);

    expect(screen.getByRole("button", { name: "Tạo đợt nội trú" })).toBeEnabled();
  });
});
