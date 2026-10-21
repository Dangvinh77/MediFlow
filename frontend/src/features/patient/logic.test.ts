import { describe, expect, it } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { patientContractFixture } from "./contract.fixture";
import { patientCreateBody, patientFailure, patientPermissions, patientToDraft, patientUpdateBody, validatePatientDraft } from "./logic";

describe("Patient validation and wire mapping", () => {
  const draft = patientToDraft(patientContractFixture);
  it("accepts the live fixture and permits the supplied current calendar date", () => {
    expect(validatePatientDraft(draft, "2026-10-20")).toEqual({});
    expect(validatePatientDraft({ ...draft, ngaySinh: "2026-10-20" }, "2026-10-20")).toEqual({});
  });
  it.each(["2025-02-29", "2026-04-31", "2026-13-01", "2026-10-21", "", "1/1/2000"])("rejects invalid/future date %s", (ngaySinh) => {
    expect(validatePatientDraft({ ...draft, ngaySinh }, "2026-10-20")).toHaveProperty("ngaySinh");
  });
  it("trims strings and the corrected identity, maps optional blanks to null", () => {
    const input = { ...draft, hoTen: "  A  ", soCmnd: " 001 ", email: " ", diaChi: " ", soDienThoai: " 0901234567 " };
    expect(patientCreateBody(input)).toMatchObject({ hoTen: "A", soCmnd: "001", email: null, diaChi: null, soDienThoai: "0901234567" });
    expect(patientUpdateBody(input)).toHaveProperty("soCmnd", "001");
  });
  it("validates every field independently of HTML including identity when editing", () => {
    const errors = validatePatientDraft({ ...draft, hoTen: " ", soCmnd: "", gioiTinh: "X", diaChi: "x".repeat(256), email: "invalid", soDienThoai: "abc", bhytSo: "123" });
    expect(Object.keys(errors)).toEqual(expect.arrayContaining(["hoTen", "soCmnd", "gioiTinh", "diaChi", "email", "soDienThoai", "bhytSo"]));
    expect(validatePatientDraft({ ...draft, soCmnd: "" })).toHaveProperty("soCmnd");
    expect(validatePatientDraft({ ...draft, soCmnd: "1".repeat(21) })).toHaveProperty("soCmnd");
  });
  it("preserves known field errors and correlation without inventing fields", () => {
    const error = patientFailure(new ApiRequestError("Trùng CMND", 409, "PATIENT_CMND_DUPLICATE", [{ field: "untrusted", message: "x" }], "trace"), true);
    expect(error).toMatchObject({ correlationId: "trace", fieldErrors: { soCmnd: "Trùng CMND" }, outcomeUnknown: false });
    expect(error.fieldErrors).not.toHaveProperty("untrusted");
  });
  it.each([408, 500, 503, 200])("marks HTTP %s mutations as unknown without treating reads as mutations", (status) => {
    const cause = new ApiRequestError("request failed", status);
    expect(patientFailure(cause, true).outcomeUnknown).toBe(true);
    expect(patientFailure(cause).outcomeUnknown).toBe(false);
  });
  it.each(["DOCTOR", "PATIENT", "CASHIER", "PHARMACIST", "MANAGER", "SYSTEM", null] as const)("blocks writes for %s", (role) => {
    expect(patientPermissions(role).write).toBe(false);
    expect(patientPermissions(role).delete).toBe(false);
  });
  it("matches the staff-only role matrix", () => {
    expect(patientPermissions("ADMIN")).toEqual({ read: true, write: true, delete: true });
    expect(patientPermissions("NURSE")).toEqual({ read: true, write: true, delete: false });
    expect(patientPermissions("DOCTOR")).toEqual({ read: true, write: false, delete: false });
    expect(patientPermissions("PATIENT").read).toBe(false);
  });
});
