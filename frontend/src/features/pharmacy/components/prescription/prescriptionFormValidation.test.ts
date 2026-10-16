import { describe, expect, it } from "vitest";
import { createEmptyPrescriptionForm, hasCreatePrescriptionErrors, toCreatePrescriptionRequest, validateCreatePrescriptionForm } from "./prescriptionFormValidation";

const id = "00000000-0000-4000-8000-0000000000ab";
function valid() {
  const values = createEmptyPrescriptionForm();
  return { ...values, recordId: id, patientId: id, doctorId: id, departmentId: id, prescribedDate: "2024-02-29",
    lines: [{ ...values.lines[0], drugId: id, quantity: "2", dosage: "Sau ăn" }] };
}

describe("Legacy prescription validation and wire intent", () => {
  it.each(["2023-02-29", "2024-02-30", "2024-13-01", "2024-3-01", "2024-03-02"])("rejects invalid/future calendar date %s", prescribedDate => {
    expect(validateCreatePrescriptionForm({ ...valid(), prescribedDate }, "2024-03-01").fields.prescribedDate).toBeTruthy();
  });
  it.each(["0", "-1", "1.5", "1e2", "2147483648", ""])("rejects quantity %s before HTTP", quantity => {
    const values = valid(); values.lines[0].quantity = quantity;
    expect(validateCreatePrescriptionForm(values, "2024-03-01").lines[values.lines[0].rowKey].quantity).toBeTruthy();
  });
  it("rejects duplicate UUIDs regardless of case and identifies the second row", () => {
    const values = valid(); values.lines.push({ ...values.lines[0], rowKey: "second", drugId: id.toUpperCase() });
    expect(validateCreatePrescriptionForm(values, "2024-03-01").lines.second.drugId).toMatch(/trùng/);
  });
  it("rejects overlong dosage and missing lines without blaming record identity", () => {
    const values = valid(); values.lines[0].dosage = "a".repeat(256);
    expect(validateCreatePrescriptionForm(values, "2024-03-01").lines[values.lines[0].rowKey].dosage).toBeTruthy();
    const empty = validateCreatePrescriptionForm({ ...valid(), lines: [] }, "2024-03-01");
    expect(empty.lineList).toMatch(/ít nhất/); expect(empty.fields.recordId).toBeUndefined(); expect(hasCreatePrescriptionErrors(empty)).toBe(true);
  });
  it("sends only V0 identifiers, date and drug intent, without cached price or V1 context", () => {
    const values = valid(); values.recordId = ` ${id} `; values.lines[0].dosage = " Sau ăn ";
    expect(hasCreatePrescriptionErrors(validateCreatePrescriptionForm(values, "2024-03-01"))).toBe(false);
    expect(toCreatePrescriptionRequest(values)).toEqual({ recordId: id, patientId: id, doctorId: id, departmentId: id,
      prescribedDate: "2024-02-29", lines: [{ drugId: id, quantity: 2, dosage: "Sau ăn" }] });
  });
});
