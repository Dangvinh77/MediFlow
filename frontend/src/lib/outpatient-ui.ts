import "server-only";

// UI exposure only. Operators must first accept the matching backend runtime modes.
export function outpatientUiEnabled(): boolean {
  return process.env.MEDIFLOW_OUTPATIENT_UI_ENABLED === "true";
}
