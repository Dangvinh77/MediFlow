import { describe, expect, it } from "vitest";
import { readProfileIdentity } from "./profile-identity";

const id = "00000000-0000-4000-8000-000000000001";
const token = (claims: object) => `header.${btoa(JSON.stringify(claims))}.signature`;
describe("Profile identity selection", () => {
  it.each([null, "bad-token", token({ sub: id, role: "DOCTOR", type: "refresh", staffId: id }), token({ sub: id, role: "DOCTOR", type: "access", staffId: "invalid" }), token({ sub: id, role: "DOCTOR", type: "access", patientId: id })])("ignores unusable identity %s", (value) => {
    expect(readProfileIdentity(value, "DOCTOR")).toBeNull();
  });
  it("selects a patient UUID instead of the account UUID", () => {
    const patientId = "00000000-0000-4000-8000-000000000002";
    expect(readProfileIdentity(token({ sub: id, role: "PATIENT", type: "access", patientId }), "PATIENT"))
      .toEqual({ accountId: id, profileId: patientId });
  });
});
