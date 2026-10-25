import { describe, expect, it } from "vitest";
import { decodeOwnPatientId } from "./own-patient-id";

function tokenWithPayload(payload: unknown): string {
  const json = JSON.stringify(payload);
  const base64url = btoa(json).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `header.${base64url}.signature`;
}

describe("decodeOwnPatientId", () => {
  it("reads the patientId claim from a well-formed access token", () => {
    const id = "00000000-0000-4000-8000-000000000001";
    expect(decodeOwnPatientId(tokenWithPayload({ sub: "acct", role: "PATIENT", patientId: id }))).toBe(id);
  });
  it("returns null for a staff token without a patientId claim", () => {
    expect(decodeOwnPatientId(tokenWithPayload({ sub: "acct", role: "NURSE" }))).toBeNull();
  });
  it("returns null for a claim that is not a valid UUID", () => {
    expect(decodeOwnPatientId(tokenWithPayload({ patientId: "not-a-uuid" }))).toBeNull();
  });
  it("returns null for no token, a malformed token, or invalid base64/JSON", () => {
    expect(decodeOwnPatientId(null)).toBeNull();
    expect(decodeOwnPatientId("only-one-segment")).toBeNull();
    expect(decodeOwnPatientId("header.not-valid-base64!!!.signature")).toBeNull();
  });
});
