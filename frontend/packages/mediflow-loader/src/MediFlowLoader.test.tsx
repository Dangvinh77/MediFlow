import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MediFlowLoader, MediFlowMark } from "./index";

describe("MediFlowLoader", () => {
  it("announces its label and shows it", () => {
    render(<MediFlowLoader label="Đang tải dữ liệu..." />);
    expect(screen.getByRole("status", { name: "Đang tải dữ liệu..." })).toBeInTheDocument();
    expect(screen.getByText("Đang tải dữ liệu...")).toBeInTheDocument();
  });

  it("stays announced without a visible label", () => {
    render(<MediFlowLoader />);
    expect(screen.getByRole("status", { name: "Loading" })).toBeInTheDocument();
  });

  it("softens the beat for reduced motion but keeps the trace running, so it never looks frozen", () => {
    const { container } = render(<MediFlowLoader />);
    const beat = container.querySelector(".animate-mediflow-beat");
    const trace = container.querySelector(".animate-mediflow-trace");
    expect(beat).toHaveClass("motion-reduce:animate-mediflow-beat-soft");
    expect(container.querySelector(".animate-mediflow-halo")).toHaveClass("motion-reduce:hidden");
    expect(trace?.getAttribute("class")).not.toContain("motion-reduce");
    expect(beat).toContainElement(trace as HTMLElement);
  });

  it("scales the beat duration with speed and falls back on invalid input", () => {
    const { container, rerender } = render(<MediFlowLoader speed={1.5} />);
    const svg = () => container.querySelector("svg") as SVGElement;
    expect(svg().style.getPropertyValue("--mediflow-beat-duration")).toBe("1s");
    rerender(<MediFlowLoader speed={0} size={-1} />);
    expect(svg().style.getPropertyValue("--mediflow-beat-duration")).toBe("1.5s");
    expect(svg()).toHaveAttribute("width", "64");
  });

  it("omits the halo when glow is off", () => {
    const { container } = render(<MediFlowLoader glow={false} />);
    expect(container.querySelector(".animate-mediflow-halo")).toBeNull();
  });
});

describe("MediFlowMark", () => {
  it("follows the theme tokens by default", () => {
    const { container } = render(<MediFlowMark />);
    expect(container.querySelector("path")).toHaveAttribute("fill", "var(--mark-cross, #0F766E)");
  });

  it("uses the white cross on brand surfaces, in both themes", () => {
    const { container } = render(<MediFlowMark tone="on-brand" />);
    expect(container.querySelector("path")).toHaveAttribute("fill", "#FFFFFF");
  });
});
