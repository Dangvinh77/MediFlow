import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { departmentContractFixture, staffContractFixture } from "../contract.fixture";

const mocks = vi.hoisted(() => ({
  department: vi.fn(), staffMember: vi.fn(),
  getRole: vi.fn(() => "ADMIN"),
  getSessionKey: vi.fn<() => string | null>(() => "session-a"),
  router: { replace: vi.fn() },
}));
vi.mock("next/navigation", () => ({ useRouter: () => mocks.router }));
vi.mock("@/lib/auth", () => ({ getSessionKey: mocks.getSessionKey, getRole: mocks.getRole, subscribeToAuthChanges: () => () => {} }));
vi.mock("../api", () => ({ organizationApi: { department: mocks.department, staffMember: mocks.staffMember } }));

import { DepartmentDetail } from "./DepartmentDetail";
import { StaffDetail } from "./StaffDetail";

describe.each(["department", "staff"] as const)("Organization %s detail lifecycle", (kind) => {
  const fixture = kind === "department" ? departmentContractFixture : staffContractFixture;
  const name = kind === "department" ? "Clinical" : "Nguyen Van A";
  const id = kind === "department" ? departmentContractFixture.departmentId : staffContractFixture.staffId;
  const request = () => kind === "department" ? mocks.department : mocks.staffMember;
  const detail = (value = id) => kind === "department"
    ? <DepartmentDetail departmentId={value} /> : <StaffDetail staffId={value} />;

  beforeEach(() => {
    mocks.getRole.mockReturnValue("ADMIN");
    mocks.department.mockReset();
    mocks.staffMember.mockReset();
    mocks.getSessionKey.mockReturnValue("session-a");
    mocks.router.replace.mockReset();
  });

  it("clears the previous identity immediately and ignores its late response", async () => {
    let resolve!: (value: typeof fixture) => void;
    request().mockReturnValueOnce(new Promise((done) => { resolve = done; }))
      .mockResolvedValueOnce({ ...fixture, departmentName: "Current", fullName: "Current" });
    const view = render(detail());
    view.rerender(detail("00000000-0000-4000-8000-000000000099"));
    expect(await screen.findByRole("heading", { name: "Current" })).toBeVisible();
    await act(async () => { resolve(fixture); });
    expect(screen.queryByRole("heading", { name })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Current" })).toBeVisible();
  });

  it("hides an already loaded detail as soon as its identity changes", async () => {
    request().mockResolvedValueOnce(fixture).mockReturnValueOnce(new Promise(() => {}));
    const view = render(detail());
    await screen.findByRole("heading", { name });
    view.rerender(detail("00000000-0000-4000-8000-000000000099"));
    expect(screen.queryByRole("heading", { name })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Chỉnh sửa" })).not.toBeInTheDocument();
  });

  it("retries only the failed detail request", async () => {
    request().mockRejectedValueOnce(new Error("Unavailable")).mockResolvedValueOnce(fixture);
    render(detail());
    expect(await screen.findByRole("alert")).toHaveTextContent("Unavailable");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { name })).toBeVisible();
    expect(request()).toHaveBeenNthCalledWith(2, id);
  });

  it.each([null, "session-b"])("handles 401 for session %s", async (token) => {
    request().mockImplementation(async () => {
      mocks.getSessionKey.mockReturnValue(token);
      throw new ApiRequestError("Expired", 401);
    });
    render(detail());
    await waitFor(() => expect(request()).toHaveBeenCalledTimes(1));
    if (token === null) expect(mocks.router.replace).toHaveBeenCalledWith("/login");
    else expect(mocks.router.replace).not.toHaveBeenCalled();
  });
});

describe("Staff directory privacy", () => {
  it.each(["MANAGER", "DOCTOR", "NURSE"])("hides personal fields for %s even if the payload contains them", async (role) => {
    mocks.getRole.mockReturnValue(role);
    mocks.staffMember.mockResolvedValue({ ...staffContractFixture,
      identityNumber: "012345678901", licenseNumber: "PRIVATE-LICENSE",
      phoneNumber: "0901234567", email: "private@example.test" });
    render(<StaffDetail staffId={staffContractFixture.staffId} />);
    await screen.findByRole("heading", { name: staffContractFixture.fullName });
    for (const value of ["012345678901", "PRIVATE-LICENSE", "0901234567", "private@example.test"]) {
      expect(screen.queryByText(value)).not.toBeInTheDocument();
    }
    expect(screen.queryByRole("link", { name: "Chỉnh sửa" })).not.toBeInTheDocument();
  });
});
