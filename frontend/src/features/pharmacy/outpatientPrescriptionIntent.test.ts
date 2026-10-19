import { describe, expect, it, vi } from "vitest";
import type { CreateOutpatientPrescriptionRequest } from "./types";
import { prepareOutpatientPrescriptionIntent } from "./outpatientPrescriptionIntent";

const firstKey = "00000000-0000-4000-8000-0000000000aa";
const secondKey = "00000000-0000-4000-8000-0000000000bb";
function request(): CreateOutpatientPrescriptionRequest {
  return {
    recordId: "00000000-0000-4000-8000-000000000001",
    patientId: "00000000-0000-4000-8000-000000000002",
    doctorId: "00000000-0000-4000-8000-000000000003",
    departmentId: "00000000-0000-4000-8000-000000000004",
    prescribedDate: "2026-10-18",
    lines: [{ drugId: "00000000-0000-4000-8000-000000000005", quantity: 2, dosage: "Sau ăn" }],
    careContractVersion: 1, careContext: "OUTPATIENT", careEpisodeType: "OUTPATIENT_VISIT",
    careEpisodeId: "00000000-0000-4000-8000-000000000006", admissionId: null, priceCode: "DRUG",
  };
}

describe("Outpatient prescription submitted intent", () => {
  it("retains the same command and payload for a deliberate unchanged retry", () => {
    const createKey = vi.fn().mockReturnValue(firstKey);
    const intent = prepareOutpatientPrescriptionIntent(request(), null, createKey);
    expect(prepareOutpatientPrescriptionIntent(request(), intent, createKey)).toBe(intent);
    expect(createKey).toHaveBeenCalledTimes(1);
    expect(intent.commandId).toBe(firstKey);
    expect(intent.request).not.toHaveProperty("commandId");
  });
  it("keeps the submitted snapshot intact after the live draft changes", () => {
    const draft = request();
    const intent = prepareOutpatientPrescriptionIntent(draft, null, () => firstKey);
    draft.lines[0].quantity = 9;
    draft.lines.push({ ...draft.lines[0], drugId: firstKey });
    draft.priceCode = "OTHER";
    expect(intent.request.lines).toEqual(request().lines);
    expect(intent.request.priceCode).toBe("DRUG");
    expect(Object.isFrozen(intent.request.lines[0])).toBe(true);
    expect(Object.isFrozen(intent.request.lines)).toBe(true);
    expect(Object.isFrozen(intent.request)).toBe(true);
    expect(Object.isFrozen(intent)).toBe(true);
  });
  it.each(["patientId", "doctorId", "departmentId", "careEpisodeId", "priceCode", "prescribedDate"] as const)(
    "uses a new key when %s changes", (field) => {
      const intent = prepareOutpatientPrescriptionIntent(request(), null, () => firstKey);
      const changed = prepareOutpatientPrescriptionIntent({ ...request(), [field]: "changed" }, intent, () => secondKey);
      expect(changed.commandId).toBe(secondKey);
      expect(intent.request[field]).toBe(request()[field]);
    },
  );
  it("detects a changed drug line and does not reuse the old command", () => {
    const original = prepareOutpatientPrescriptionIntent(request(), null, () => firstKey);
    const changed = request(); changed.lines[0].quantity += 1;
    const next = prepareOutpatientPrescriptionIntent(changed, original, () => secondKey);
    expect(next.commandId).toBe(secondKey);
    expect(next.request.lines[0].quantity).toBe(3);
    expect(original.request.lines[0].quantity).toBe(2);
  });
  it("does not allocate a different key merely because object properties have another order", () => {
    const original = prepareOutpatientPrescriptionIntent(request(), null, () => firstKey);
    const reordered = { ...request(), lines: [{ dosage: "Sau ăn", quantity: 2, drugId: request().lines[0].drugId }] };
    const makeKey = vi.fn();
    expect(prepareOutpatientPrescriptionIntent(reordered, original, makeKey)).toBe(original);
    expect(makeKey).not.toHaveBeenCalled();
  });
  it("starts a fresh command for an explicit new intent with the same payload", () => {
    prepareOutpatientPrescriptionIntent(request(), null, () => firstKey);
    expect(prepareOutpatientPrescriptionIntent(request(), null, () => secondKey).commandId).toBe(secondKey);
  });
  it.each(["", "1-2-3-4-5", "not-a-uuid", ` ${firstKey} `])("rejects malformed command UUID %j", (key) => {
    expect(() => prepareOutpatientPrescriptionIntent(request(), null, () => key)).toThrow(/UUID/);
  });
  it("rejects a generator reusing the old key for a changed payload, regardless of case", () => {
    const original = prepareOutpatientPrescriptionIntent(request(), null, () => firstKey);
    expect(() => prepareOutpatientPrescriptionIntent({ ...request(), priceCode: "OTHER" }, original, () => firstKey.toUpperCase()))
      .toThrow(/mã yêu cầu mới/);
  });
  it("does not copy extra client values into the submitted wire snapshot", () => {
    const source = { ...request(), totalAmount: 1, recordMode: "V1", commandId: secondKey };
    const intent = prepareOutpatientPrescriptionIntent(source, null, () => firstKey);
    expect(intent.request).toEqual(request());
    expect(intent.request).not.toHaveProperty("commandId");
    expect(intent.request).not.toHaveProperty("totalAmount");
  });
});
