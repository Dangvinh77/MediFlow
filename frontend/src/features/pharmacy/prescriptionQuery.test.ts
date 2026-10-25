import { describe, expect, it } from "vitest";
import { buildPrescriptionQuery, readPrescriptionQuery } from "./prescriptionQuery";

describe("prescription list URLs", () => {
  it("preserves exact patient, status and page across detail navigation", () => {
    const query = { patientId: "00000000-0000-4000-8000-000000000001", status: "CANCELLED", page: 3 };
    expect(readPrescriptionQuery(buildPrescriptionQuery(query))).toEqual(query);
  });
  it.each(["page=-1", "page=1.5", "page=99999999999", "status=unknown&patientId=typed-name"])("normalizes invalid bookmarks %s", raw => {
    expect(readPrescriptionQuery(raw)).toEqual({ patientId: "", status: "", page: 0 });
  });
});
