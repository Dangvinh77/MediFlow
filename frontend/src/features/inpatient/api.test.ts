import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AdmissionDTO, CreateAdmissionRequest } from "./types";

const { get, post } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("@/lib/api", () => ({ api: { get, post } }));

import { inpatientApi } from "./api";

const request: CreateAdmissionRequest = {
  maYeuCauNoiTru: "00000000-0000-4000-8000-000000000001",
  maHoSoNguon: "00000000-0000-4000-8000-000000000002",
  maBenhNhan: "00000000-0000-4000-8000-000000000003",
  maKhoa: "00000000-0000-4000-8000-000000000004",
  nguoiYeuCau: "00000000-0000-4000-8000-000000000005",
  tomTatChanDoan: "Viêm phổi",
  doUuTien: "URGENT",
  capCuu: false,
  thoiGianYeuCau: "2026-10-09T03:30:00.000Z",
};

describe("inpatientApi.createAdmission", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("posts the exact request contract to the admission collection", async () => {
    const created = { maDotNoiTru: "00000000-0000-4000-8000-000000000006" } as AdmissionDTO;
    post.mockResolvedValue(created);

    await expect(inpatientApi.createAdmission(request)).resolves.toBe(created);

    expect(post).toHaveBeenCalledWith("/v1/inpatient/admissions", request);
  });
});
