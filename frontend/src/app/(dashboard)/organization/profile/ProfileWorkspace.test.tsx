import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import type { Role } from "@/lib/roles";
import { staffContractFixture } from "@/features/organization/contract.fixture";
import { patientContractFixture } from "@/features/patient/contract.fixture";

const mocks = vi.hoisted(() => ({
  role: "DOCTOR" as Role | null, token: null as string | null, session: "session-a" as string | null,
  listeners: new Set<() => void>(), staff: vi.fn(), ownDepartment: vi.fn(), patient: vi.fn(), replace: vi.fn(), directory: vi.fn(),
}));
vi.mock("@/lib/auth", () => ({
  getRole: () => mocks.role, getToken: () => mocks.token, getSessionKey: () => mocks.session,
  subscribeToAuthChanges: (listener: () => void) => { mocks.listeners.add(listener); return () => { mocks.listeners.delete(listener); }; },
}));
vi.mock("next/navigation", () => ({ useRouter: () => router }));
const router = { replace: mocks.replace };
vi.mock("@/features/organization/api", () => ({ organizationApi: { staffMember: mocks.staff, ownDepartment: mocks.ownDepartment } }));
vi.mock("@/features/patient/api", () => ({ patientApi: { getById: mocks.patient } }));
vi.mock("@/features/organization/components/OrganizationDirectory", () => ({ OrganizationDirectory: () => { mocks.directory(); return <div>Danh bạ quản trị</div>; } }));

import { ProfileWorkspace } from "./ProfileWorkspace";
import { OrganizationWorkspace } from "../OrganizationWorkspace";

const accountId = "00000000-0000-4000-8000-000000000003";
function signIn(role: Role, profileId: string | null = staffContractFixture.staffId) {
  mocks.role = role;
  mocks.token = `header.${btoa(JSON.stringify({ sub: accountId, role, type: "access", ...(profileId ? { [role === "PATIENT" ? "patientId" : "staffId"]: profileId } : {}) }))}.signature`;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.staff.mockReset().mockResolvedValue(staffContractFixture);
  mocks.ownDepartment.mockReset().mockResolvedValue({ departmentId: staffContractFixture.departmentId, departmentName: "Khoa Nội" });
  mocks.patient.mockReset().mockResolvedValue(patientContractFixture);
  mocks.session = "session-a";
  signIn("DOCTOR");
});

describe("Role-specific organization workspace", () => {
  it("gives ADMIN management tools without personal profile information in the management page", () => {
    signIn("ADMIN");
    render(<OrganizationWorkspace />);
    expect(screen.getByRole("heading", { name: "Quản trị tổ chức" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Quản lý tài khoản" })).toHaveAttribute("href", "/organization/accounts");
    expect(screen.getByText("Danh bạ quản trị")).toBeVisible();
    expect(screen.queryByRole("link", { name: /Xem hồ sơ cá nhân/ })).not.toBeInTheDocument();
    expect(mocks.staff).not.toHaveBeenCalled();
    expect(mocks.patient).not.toHaveBeenCalled();
  });

  it.each<Role>(["MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH"])("gives %s only their personal record on entry", async (role) => {
    signIn(role);
    render(<OrganizationWorkspace />);
    expect(await screen.findByRole("heading", { name: staffContractFixture.fullName })).toBeVisible();
    expect(screen.getByText(staffContractFixture.identityNumber)).toBeVisible();
    expect(screen.queryByText("Định danh tài khoản")).not.toBeInTheDocument();
    expect(screen.queryByText("Thông tin kỹ thuật")).not.toBeInTheDocument();
    expect(mocks.staff).toHaveBeenCalledExactlyOnceWith(staffContractFixture.staffId);
    await waitFor(() => expect(screen.getByText("Khoa Nội")).toBeVisible());
    expect(mocks.ownDepartment).toHaveBeenCalledExactlyOnceWith(staffContractFixture.staffId);
    expect(mocks.directory).not.toHaveBeenCalled();
    expect(mocks.patient).not.toHaveBeenCalled();
    expect(screen.queryByRole("link", { name: "Quản lý tài khoản" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Chỉnh sửa" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đổi mật khẩu" })).toHaveAttribute("href", "/organization/security");
  });

  it("loads a PATIENT record without calling organization APIs", async () => {
    signIn("PATIENT", patientContractFixture.maBenhNhan);
    render(<ProfileWorkspace />);
    expect(await screen.findByText("Hồ sơ bệnh nhân của bạn")).toBeVisible();
    expect(screen.getByText(patientContractFixture.soCmnd)).toBeVisible();
    expect(mocks.patient).toHaveBeenCalledExactlyOnceWith(patientContractFixture.maBenhNhan);
    expect(mocks.staff).not.toHaveBeenCalled();
    expect(mocks.directory).not.toHaveBeenCalled();
    expect(screen.queryByRole("link", { name: "Tra cứu khoa và nhân sự" })).not.toBeInTheDocument();
  });

  it("shows an unlinked account without fetching a directory", () => {
    signIn("ADMIN", null);
    render(<ProfileWorkspace />);
    expect(screen.getByText(/Tài khoản chưa được liên kết/)).toBeVisible();
    expect(mocks.staff).not.toHaveBeenCalled();
    expect(mocks.patient).not.toHaveBeenCalled();
  });

  it("does not fetch when the token role conflicts with the stored role", () => {
    mocks.role = "PATIENT";
    render(<ProfileWorkspace />);
    expect(screen.getByText(/Không đọc được định danh/)).toBeVisible();
    expect(mocks.staff).not.toHaveBeenCalled();
    expect(mocks.patient).not.toHaveBeenCalled();
  });

  it.each(["staff", "patient"] as const)("clears %s data and ignores late results after account replacement", async (kind) => {
    if (kind === "patient") signIn("PATIENT", patientContractFixture.maBenhNhan);
    const api = mocks[kind];
    let resolve!: (value: unknown) => void;
    api.mockReturnValueOnce(new Promise((done) => { resolve = done; })).mockReturnValueOnce(new Promise(() => {}));
    render(<ProfileWorkspace />);
    await waitFor(() => expect(api).toHaveBeenCalledOnce());
    act(() => {
      mocks.session = "session-b";
      signIn(kind === "staff" ? "DOCTOR" : "PATIENT", "00000000-0000-4000-8000-000000000099");
      mocks.listeners.forEach((listener) => listener());
    });
    await act(async () => { resolve(kind === "staff" ? staffContractFixture : patientContractFixture); });
    expect(screen.queryByRole("heading", { name: "Nguyen Van A" })).not.toBeInTheDocument();
    expect(api).toHaveBeenLastCalledWith("00000000-0000-4000-8000-000000000099");
  });

  it.each(["staff", "patient"] as const)("retries a failed %s read without any mutation", async (kind) => {
    if (kind === "patient") signIn("PATIENT", patientContractFixture.maBenhNhan);
    const api = mocks[kind];
    api.mockRejectedValueOnce(new Error("Tạm thời không khả dụng"));
    render(<ProfileWorkspace />);
    expect(await screen.findByRole("alert")).toHaveTextContent("Tạm thời không khả dụng");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByRole("heading", { name: "Nguyen Van A" })).toBeVisible();
    expect(api).toHaveBeenCalledTimes(2);
  });

  it("hides an already loaded private profile immediately when the session changes", async () => {
    render(<ProfileWorkspace />);
    await screen.findByText(staffContractFixture.identityNumber);
    mocks.patient.mockReturnValueOnce(new Promise(() => {}));
    act(() => {
      mocks.session = "patient-session";
      signIn("PATIENT", patientContractFixture.maBenhNhan);
      mocks.listeners.forEach((listener) => listener());
    });
    expect(screen.queryByText(staffContractFixture.identityNumber)).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Thông tin công tác" })).not.toBeInTheDocument();
  });

  it.each(["staff", "patient"] as const)("ignores a late %s 401 belonging to a replaced session", async (kind) => {
    if (kind === "patient") signIn("PATIENT", patientContractFixture.maBenhNhan);
    let reject!: (cause: unknown) => void;
    mocks[kind].mockReturnValueOnce(new Promise((_resolve, fail) => { reject = fail; }));
    render(<ProfileWorkspace />);
    mocks.session = "session-b";
    await act(async () => { reject(new ApiRequestError("Old session", 401)); });
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it.each(["staff", "patient"] as const)("redirects an expired %s session to login", async (kind) => {
    if (kind === "patient") signIn("PATIENT", patientContractFixture.maBenhNhan);
    mocks[kind].mockImplementation(async () => {
      mocks.session = null;
      throw new ApiRequestError("Hết phiên", 401);
    });
    render(<ProfileWorkspace />);
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/login"));
  });
});
