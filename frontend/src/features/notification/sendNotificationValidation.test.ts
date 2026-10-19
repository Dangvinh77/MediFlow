import { describe, expect, it } from "vitest";
import { toSendNotificationRequest, validateSendNotification, type SendNotificationValues } from "./sendNotificationValidation";

const valid: SendNotificationValues = { patientId: "00000000-0000-4000-8000-000000000001", title: "Tiêu đề", content: "Nội dung", channel: "IN_APP", recipientAddress: "" };
describe("Notification validation matching the public backend", () => {
  it("requires patient UUID, nonblank title/content, and enforces 255 characters", () => {
    expect(validateSendNotification({ ...valid, patientId: "account", title: " ", content: "\n" })).toHaveProperty("patientId");
    expect(validateSendNotification({ ...valid, title: " ", content: "\n" })).toMatchObject({ title: expect.any(String), content: expect.any(String) });
    expect(validateSendNotification({ ...valid, title: "x".repeat(256) })).toHaveProperty("title");
    expect(validateSendNotification({ ...valid, title: "x".repeat(255) })).toEqual({});
  });
  it.each(["", "not-email", "a@domain", "a@sub.domain.com", "a".repeat(145) + "@xx.com"])("rejects backend-incompatible email %s", address => {
    expect(validateSendNotification({ ...valid, channel: "EMAIL", recipientAddress: address })).toHaveProperty("recipientAddress");
  });
  it.each(["0912345678", "01234567890"])("accepts the backend domestic SMS shape %s", address => {
    expect(validateSendNotification({ ...valid, channel: "SMS", recipientAddress: address })).toEqual({});
  });
  it.each(["+84912345678", "09123", "091234567890", "09123x5678"])("rejects invalid SMS %s", address => {
    expect(validateSendNotification({ ...valid, channel: "SMS", recipientAddress: address })).toHaveProperty("recipientAddress");
  });
  it("serializes only public command fields and drops stale address for IN_APP", () => {
    expect(toSendNotificationRequest({ ...valid, patientId: ` ${valid.patientId} `, title: " Tiêu đề ", content: " Nội dung ", recipientAddress: "private@example.com" }))
      .toEqual({ ...valid, recipientAddress: null });
    expect(toSendNotificationRequest({ ...valid, channel: "EMAIL", recipientAddress: " user@example.com " }).recipientAddress).toBe("user@example.com");
  });
  it("refuses to serialize invalid drafts", () => {
    expect(() => toSendNotificationRequest({ ...valid, patientId: "" })).toThrow();
  });
});
