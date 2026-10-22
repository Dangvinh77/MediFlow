import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PageResult } from "@/lib/types";
import {
  accountContractFixture,
  changeStaffDepartmentRequestFixture,
  createAccountRequestFixture,
  createDepartmentRequestFixture,
  createStaffRequestFixture,
  departmentContractFixture,
  lockAccountRequestFixture,
  staffContractFixture,
  updateDepartmentRequestFixture,
  updateStaffRequestFixture,
} from "./contract.fixture";
import type { StaffDTO } from "./types";

const { get, post, put } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));

vi.mock("@/lib/api", () => ({ api: { get, post, put } }));

import { organizationApi } from "./api";

const staffPage: PageResult<StaffDTO> = {
  content: [staffContractFixture],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

describe("organizationApi facade contract", () => {
  it("loads account detail and forwards explicit replacement confirmation", async () => {
    get.mockResolvedValueOnce(accountContractFixture);
    await organizationApi.account(accountContractFixture.accountId);
    expect(get).toHaveBeenCalledWith(`/v1/org/accounts/${accountContractFixture.accountId}`);
    await organizationApi.updateAccountStatus(accountContractFixture.accountId, true, true);
    expect(put).toHaveBeenCalledWith(`/v1/org/accounts/${accountContractFixture.accountId}/status`, { isActive: true, replaceActiveAccount: true });
  });
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    put.mockReset();
  });

  it("uses the departments paths and preserves nullable head/location DTO fields", async () => {
    get.mockResolvedValueOnce([departmentContractFixture]);
    await expect(organizationApi.departments()).resolves.toEqual([departmentContractFixture]);
    expect(get).toHaveBeenLastCalledWith("/v1/org/departments?activeOnly=true");

    get.mockResolvedValueOnce([departmentContractFixture]);
    await organizationApi.departments(false);
    expect(get).toHaveBeenLastCalledWith("/v1/org/departments?activeOnly=false");

    get.mockResolvedValueOnce(departmentContractFixture);
    await organizationApi.department(departmentContractFixture.departmentId);
    expect(get).toHaveBeenLastCalledWith(
      `/v1/org/departments/${departmentContractFixture.departmentId}`,
    );
  });

  it("posts and puts department DTO bodies, including explicit null to remove a head", async () => {
    post.mockResolvedValueOnce(departmentContractFixture);
    await organizationApi.createDepartment(createDepartmentRequestFixture);
    expect(post).toHaveBeenCalledWith("/v1/org/departments", createDepartmentRequestFixture);

    put.mockResolvedValueOnce(departmentContractFixture);
    await organizationApi.updateDepartment(
      departmentContractFixture.departmentId,
      updateDepartmentRequestFixture,
    );
    expect(put).toHaveBeenCalledWith(
      `/v1/org/departments/${departmentContractFixture.departmentId}`,
      updateDepartmentRequestFixture,
    );
    expect(updateDepartmentRequestFixture).toHaveProperty("departmentHeadId", null);
  });

  it("uses zero-based staff paging with default size 20 and the live optional filters", async () => {
    get.mockResolvedValueOnce(staffPage);
    await organizationApi.staff();
    expect(get).toHaveBeenCalledWith("/v1/org/staff?page=0&size=20");

    get.mockResolvedValueOnce(staffPage);
    await organizationApi.staff({
      departmentId: departmentContractFixture.departmentId,
      jobTitle: "DOCTOR",
      page: 2,
      size: 10,
    });
    expect(get).toHaveBeenLastCalledWith(
      "/v1/org/staff?page=2&size=10&departmentId=00000000-0000-4000-8000-000000000001&jobTitle=DOCTOR",
    );
  });

  it("uses the staff paths and exact create, update and transfer request bodies", async () => {
    get.mockResolvedValueOnce(staffContractFixture);
    await organizationApi.staffMember(staffContractFixture.staffId);
    expect(get).toHaveBeenCalledWith(`/v1/org/staff/${staffContractFixture.staffId}`);

    post.mockResolvedValueOnce(staffContractFixture);
    await organizationApi.createStaff(createStaffRequestFixture, "staff-intent-1");
    expect(post).toHaveBeenCalledWith("/v1/org/staff", createStaffRequestFixture,
      { idempotencyKey: "staff-intent-1" });

    put.mockResolvedValueOnce(staffContractFixture);
    await organizationApi.updateStaff(staffContractFixture.staffId, updateStaffRequestFixture);
    expect(put).toHaveBeenCalledWith(
      `/v1/org/staff/${staffContractFixture.staffId}`,
      updateStaffRequestFixture,
    );

    put.mockResolvedValueOnce(staffContractFixture);
    await organizationApi.changeStaffDepartment(
      staffContractFixture.staffId,
      changeStaffDepartmentRequestFixture.newDepartmentId,
    );
    expect(put).toHaveBeenLastCalledWith(
      `/v1/org/staff/${staffContractFixture.staffId}/department`,
      changeStaffDepartmentRequestFixture,
    );
  });

  it("creates accounts and locks them with the isActive request property", async () => {
    post.mockResolvedValueOnce(accountContractFixture);
    await organizationApi.createAccount(createAccountRequestFixture, "account-command-1");
    expect(post).toHaveBeenCalledWith("/v1/org/accounts", createAccountRequestFixture, { idempotencyKey: "account-command-1" });

    put.mockResolvedValueOnce(accountContractFixture);
    await organizationApi.updateAccountStatus(accountContractFixture.accountId, false);
    expect(put).toHaveBeenCalledWith(
      `/v1/org/accounts/${accountContractFixture.accountId}/status`,
      lockAccountRequestFixture,
    );
  });

  it("preserves the backend department-head error code for the form", async () => {
    const error = {
      name: "ApiRequestError",
      message: "Head assignment must be removed first",
      status: 422,
      code: "DEPARTMENT_HEAD_INVALID",
    };
    put.mockRejectedValueOnce(error);

    await expect(organizationApi.changeStaffDepartment(
      staffContractFixture.staffId,
      changeStaffDepartmentRequestFixture.newDepartmentId,
    )).rejects.toMatchObject({ code: "DEPARTMENT_HEAD_INVALID", status: 422 });
  });
});
