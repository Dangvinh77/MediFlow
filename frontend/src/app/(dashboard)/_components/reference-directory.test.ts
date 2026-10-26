import { describe, it, expect, vi } from "vitest";
import { patientApi } from "@/features/patient/api";
import { organizationApi } from "@/features/organization/api";
import { createReferenceDirectory } from "./reference-directory";
vi.mock("@/features/patient/api", () => ({ patientApi: { directory: vi.fn(), directoryEntry: vi.fn(), search: vi.fn(), getById: vi.fn() } }));
vi.mock("@/features/organization/api", () => ({ organizationApi: { careLabel: vi.fn(), staff: vi.fn(), staffMember: vi.fn(), departments: vi.fn(), department: vi.fn(), accounts: vi.fn(), account: vi.fn() } }));

describe("dashboard directory contracts", () => {
  it("never calls Patient reads for PATIENT", async () => {
    for (const role of ["PATIENT"] as const) {
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
    expect(organizationApi.staff).toHaveBeenCalledWith({ departmentId: "department-id", jobTitle: "DOCTOR", keyword: "An", activeOnly: true, page: 0, size: 20 });
    expect(result.items.map(item => item.id)).toEqual(["doctor-id"]);
  });

  it("offers active Manager accounts only to ADMIN for scheduling scope assignment", async () => {
    vi.mocked(organizationApi.accounts).mockResolvedValue({ content: [{ accountId: "account-id", username: "clinic.manager", role: "MANAGER", isActive: true }], number: 0, totalPages: 1 } as never);
    const directory = createReferenceDirectory("ADMIN");
    expect(directory.canSearch("account")).toBe(true);
    const result = await directory.search("account", "manager", 0);
    expect(organizationApi.accounts).toHaveBeenCalledWith({ keyword: "manager", role: "MANAGER", active: true, page: 0 });
    expect(result.items).toEqual([{ id: "account-id", label: "clinic.manager · MANAGER" }]);
    expect(createReferenceDirectory("MANAGER").canSearch("account")).toBe(false);
  });
});

it("resolves only minimal care labels for patients without opening staff search or patient records", async () => {
  vi.mocked(organizationApi.careLabel).mockResolvedValue({ id: "doctor-id", label: "Bác sĩ Nguyễn An" });
  const directory = createReferenceDirectory("PATIENT");
  expect(directory.canResolve?.("doctor")).toBe(true);
  expect(directory.canSearch("doctor")).toBe(false);
  expect(await directory.resolve("doctor", "doctor-id")).toBe("Bác sĩ Nguyễn An");
  expect(organizationApi.careLabel).toHaveBeenCalledWith("doctor", "doctor-id");
  expect(organizationApi.staffMember).not.toHaveBeenCalled();
  expect(await directory.resolve("staff", "doctor-id")).toBeNull();
});

it("does not cache a missing care label permanently and rejects mismatched identities", async () => {
  vi.mocked(organizationApi.careLabel).mockResolvedValueOnce({ id: "wrong", label: "Wrong person" })
    .mockResolvedValueOnce({ id: "room-id", label: "Phòng khám Nội 1" });
  const directory = createReferenceDirectory("PATIENT");
  expect(await directory.resolve("room", "room-id")).toBeNull();
  expect(await directory.resolve("room", "room-id")).toBe("Phòng khám Nội 1");
});

it.each(["CASHIER", "LAB_TECH", "PHARMACIST"] as const)("uses only the three-field directory for %s", async role => {
  vi.mocked(patientApi.directoryEntry).mockResolvedValue({ maBenhNhan: "patient-id", hoTen: "Nguyễn An", ngaySinh: "1995-05-12" });
  vi.mocked(patientApi.directory).mockResolvedValue({ content: [{ maBenhNhan: "patient-id", hoTen: "Nguyễn An", ngaySinh: "1995-05-12" }], number: 0, totalPages: 1 } as never);
  const directory = createReferenceDirectory(role);
  expect(await directory.resolve("patient", "patient-id")).toContain("Nguyễn An");
  expect((await directory.search("patient", "An", 0)).items[0].label).toContain("Nguyễn An");
  expect(patientApi.directory).toHaveBeenCalledWith("An", 0);
});
