import { describe, it, expect, vi } from "vitest";
import { patientApi } from "@/features/patient/api";
import { organizationApi } from "@/features/organization/api";
import { createReferenceDirectory } from "./reference-directory";
vi.mock("@/features/patient/api", () => ({ patientApi: { search: vi.fn(), getById: vi.fn() } }));
vi.mock("@/features/organization/api", () => ({ organizationApi: { staff: vi.fn(), staffMember: vi.fn(), departments: vi.fn(), department: vi.fn() } }));

describe("dashboard directory contracts", () => {
  it("never calls Patient reads for CASHIER, LAB_TECH or PATIENT", async () => {
    for (const role of ["CASHIER", "LAB_TECH", "PATIENT"] as const) {
      const directory = createReferenceDirectory(role);
      expect(directory.canSearch("patient")).toBe(false);
      expect(await directory.resolve("patient", "private-id")).toBeNull();
      await expect(directory.search("patient", "An", 0)).rejects.toThrow();
    }
    expect(patientApi.search).not.toHaveBeenCalled(); expect(patientApi.getById).not.toHaveBeenCalled();
  });
  it("uses Patient keyword/page and keeps the exact identity, without contact/CCCD in labels", async () => {
    vi.mocked(patientApi.search).mockResolvedValue({ content: [{ maBenhNhan: "patient-id", hoTen: "Nguyễn An", ngaySinh: "1995-05-12", soCmnd: "secret" }], number: 2, totalPages: 4 } as never);
    const result = await createReferenceDirectory("NURSE").search("patient", "An", 2);
    expect(patientApi.search).toHaveBeenCalledWith({ keyword: "An", page: 2, size: 20 });
    expect(result.items[0].id).toBe("patient-id"); expect(result.items[0].label).toContain("Nguyễn An"); expect(result.items[0].label).not.toContain("secret"); expect(result.hasMore).toBe(true);
  });
  it("requests doctors scoped by selected department and does not select inactive staff", async () => {
    vi.mocked(organizationApi.staff).mockResolvedValue({ content: [{ staffId: "doctor-id", fullName: "Bác sĩ An", active: true, jobTitle: "DOCTOR" }, { staffId: "old", fullName: "An cũ", active: false, jobTitle: "DOCTOR" }], number: 0, totalPages: 1 } as never);
    const result = await createReferenceDirectory("ADMIN").search("doctor", "An", 0, "department-id");
    expect(organizationApi.staff).toHaveBeenCalledWith({ departmentId: "department-id", jobTitle: "DOCTOR", page: 0, size: 20 });
    expect(result.items.map(item => item.id)).toEqual(["doctor-id"]);
  });
});
