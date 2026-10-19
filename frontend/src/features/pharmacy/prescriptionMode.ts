export type PrescriptionMode = "outpatient" | "legacy";

/** Server runtime setting; it does not switch backend profiles or infer mode from errors. */
export function resolvePrescriptionMode(value: string | undefined): PrescriptionMode | null {
  if (value === undefined || value === "outpatient") return "outpatient";
  if (value === "legacy") return "legacy";
  return null;
}
