import { expect, it, vi } from "vitest";
import { api } from "@/lib/api";
import { organizationApi } from "./api";
vi.mock("@/lib/api", () => ({ api: { get: vi.fn(), put: vi.fn() } }));
it("sends an audited revision guarded revocation using one intent", async () => {
  const body = { staffId: null, expectedRevision: 3, reason: "Kết thúc nhiệm kỳ" };
  const options = { idempotencyKey: "same-intent", correlationId: "trace" };
  await organizationApi.replaceHeadAppointment("dept/1", body, options);
  expect(api.put).toHaveBeenCalledWith("/v2/org/departments/dept%2F1/head-appointments", body, options);
});
