import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Role } from "@/lib/roles";
import { AdmissionCreateLink } from "./AdmissionCreateLink";

describe("AdmissionCreateLink", () => {
  it.each<Role>(["ADMIN", "DOCTOR"])("shows the create action to %s", (role) => {
    render(<AdmissionCreateLink role={role} />);

    expect(screen.getByRole("link", { name: "Tạo đợt nội trú" })).toHaveAttribute(
      "href",
      "/inpatient/new",
    );
  });

  it.each<Array<Role | null>>([["MANAGER"], ["NURSE"], ["CASHIER"], [null]])(
    "hides the create action from %s",
    (role) => {
      const { container } = render(<AdmissionCreateLink role={role} />);

      expect(container).toBeEmptyDOMElement();
    },
  );
});
