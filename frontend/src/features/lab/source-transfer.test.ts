import { beforeEach, expect, it, vi } from "vitest";
import type { CreateLabRequest } from "./types";
import { ApiRequestError } from "@/lib/api";

const mocks = vi.hoisted(() => ({ session: "", create: vi.fn() }));
vi.mock("@/lib/session", () => ({ getSessionKey: () => mocks.session }));
vi.mock("./api", () => ({ labApi: { create: mocks.create } }));
import { transferLabSource } from "./source-transfer";

const request: CreateLabRequest = { recordId: "record", patientId: "patient", requestingDepartmentId: "department", labType: "CBC", requestedDate: "2026-11-03", sourceOrderId: "order", careEpisodeType: "OUTPATIENT_VISIT", careEpisodeId: "visit", priceCode: "CBC" };
beforeEach(() => { mocks.session = crypto.randomUUID(); mocks.create.mockReset(); });

it("reuses the original result when a transfer screen remounts", async () => {
  mocks.create.mockResolvedValue({ testId: "lab" });
  const first = transferLabSource(request);
  expect(transferLabSource({ ...request })).toBe(first);
  await first;
  await transferLabSource(request);
  expect(mocks.create).toHaveBeenCalledTimes(1);
});

it("does not replay a lost response on remount and rejects changed source bytes", async () => {
  mocks.create.mockRejectedValue(new Error("lost response"));
  await expect(transferLabSource(request)).rejects.toThrow("lost response");
  await expect(transferLabSource(request)).rejects.toThrow("lost response");
  expect(() => transferLabSource({ ...request, priceCode: "other" })).toThrow("Thông tin chỉ định đã thay đổi");
  expect(mocks.create).toHaveBeenCalledTimes(1);
});

it("permits an explicit retry of a rejected transfer and isolates signed-in sessions", async () => {
  mocks.create.mockRejectedValueOnce(new ApiRequestError("invalid request", 422)).mockResolvedValue({ testId: "lab" });
  await expect(transferLabSource(request)).rejects.toThrow("invalid request");
  await transferLabSource(request, true);
  expect(mocks.create).toHaveBeenCalledTimes(2);
  mocks.session = "new-session";
  await transferLabSource(request);
  expect(mocks.create).toHaveBeenCalledTimes(3);
});
