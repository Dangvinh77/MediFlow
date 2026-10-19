import { describe, expect, it } from "vitest";
import {
  createEmptyOutpatientPrescriptionForm,
  toOutpatientPrescriptionRequest,
  validateOutpatientPrescriptionForm,
} from "./outpatientPrescriptionForm";
import { hasCreatePrescriptionErrors, toCreatePrescriptionRequest } from "./prescriptionFormValidation";

const ids = {
  record: "00000000-0000-4000-8000-000000000001",
  patient: "00000000-0000-4000-8000-000000000002",
  doctor: "00000000-0000-4000-8000-000000000003",
  department: "00000000-0000-4000-8000-000000000004",
  episode: "00000000-0000-4000-8000-000000000005",
  drug: "00000000-0000-4000-8000-000000000006",
};
function valid() {
  const values = createEmptyOutpatientPrescriptionForm();
  return {
    ...values, recordId: ids.record, patientId: ids.patient, doctorId: ids.doctor,
    departmentId: ids.department, careEpisodeId: ids.episode, priceCode: "DRUG",
    prescribedDate: "2026-10-18",
    lines: [{ ...values.lines[0], drugId: ids.drug, quantity: "2", dosage: "Sau ăn" }],
  };
}

describe("Public outpatient prescription validation and wire shape", () => {
  it("starts without invented source identities or a Billing catalog default", () => {
    const values = createEmptyOutpatientPrescriptionForm();
    for (const field of ["recordId", "patientId", "doctorId", "departmentId", "careEpisodeId", "priceCode"] as const) {
      expect(values[field]).toBe("");
    }
    expect(values.lines).toHaveLength(1);
    expect(values.lines[0].drugId).toBe("");
  });
  it.each(["recordId", "patientId", "doctorId", "departmentId", "careEpisodeId"] as const)(
    "requires the exact public source UUID for %s without substituting another identifier", (field) => {
      const values = { ...valid(), [field]: "" };
      expect(validateOutpatientPrescriptionForm(values, "2026-10-18").fields[field]).toBeTruthy();
      expect(() => toOutpatientPrescriptionRequest(values, "2026-10-18")).toThrow(/chưa hợp lệ/);
      expect(values[field]).toBe("");
    },
  );
  it.each(["", "   ", "A".repeat(65), `${"A".repeat(64)} `])("rejects an empty/overlong catalog key %j", (priceCode) => {
    expect(validateOutpatientPrescriptionForm({ ...valid(), priceCode }, "2026-10-18").fields.priceCode).toBeTruthy();
  });
  it("preserves a case-sensitive catalog key instead of trimming or guessing it", () => {
    const request = toOutpatientPrescriptionRequest({ ...valid(), priceCode: " Drug-v1 " }, "2026-10-18");
    expect(request.priceCode).toBe(" Drug-v1 ");
    expect(validateOutpatientPrescriptionForm({ ...valid(), priceCode: "A".repeat(64) }, "2026-10-18").fields.priceCode).toBeUndefined();
  });
  it.each(["2026-02-29", "2026-10-19"])("rejects invalid/future dates %s", (prescribedDate) => {
    expect(validateOutpatientPrescriptionForm({ ...valid(), prescribedDate }, "2026-10-18").fields.prescribedDate).toBeTruthy();
  });
  it("reuses line rules for duplicate drugs, positive integer quantities and dosage bounds", () => {
    const values = valid();
    values.lines.push({ ...values.lines[0], rowKey: "duplicate", drugId: ids.drug.toUpperCase() });
    values.lines[0].quantity = "1.5";
    values.lines[0].dosage = "x".repeat(256);
    const errors = validateOutpatientPrescriptionForm(values, "2026-10-18");
    expect(errors.lines.duplicate.drugId).toMatch(/trùng/);
    expect(errors.lines[values.lines[0].rowKey].quantity).toBeTruthy();
    expect(errors.lines[values.lines[0].rowKey].dosage).toBeTruthy();
    expect(hasCreatePrescriptionErrors(errors)).toBe(true);
    expect(validateOutpatientPrescriptionForm({ ...valid(), lines: [] }, "2026-10-18").lineList).toBeTruthy();
  });
  it("serializes exact outpatient metadata and never cached prices, command keys or admission input", () => {
    const values = { ...valid(), recordId: ` ${ids.record} `, careEpisodeId: ` ${ids.episode} `,
      admissionId: ids.episode, careContext: "ADMISSION", commandId: ids.drug, totalAmount: 99 };
    expect(hasCreatePrescriptionErrors(validateOutpatientPrescriptionForm(values, "2026-10-18"))).toBe(false);
    expect(toOutpatientPrescriptionRequest(values, "2026-10-18")).toEqual({
      recordId: ids.record, patientId: ids.patient, doctorId: ids.doctor, departmentId: ids.department,
      prescribedDate: "2026-10-18", lines: [{ drugId: ids.drug, quantity: 2, dosage: "Sau ăn" }],
      careContractVersion: 1, careContext: "OUTPATIENT", careEpisodeType: "OUTPATIENT_VISIT",
      careEpisodeId: ids.episode, admissionId: null, priceCode: "DRUG",
    });
  });
  it("does not leak V1 fields into the legacy serializer", () => {
    expect(toCreatePrescriptionRequest(valid())).toEqual({
      recordId: ids.record, patientId: ids.patient, doctorId: ids.doctor, departmentId: ids.department,
      prescribedDate: "2026-10-18", lines: [{ drugId: ids.drug, quantity: 2, dosage: "Sau ăn" }],
    });
  });
});
