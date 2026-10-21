import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PageResult } from "@/lib/types";
import {
  createPatientContractFixture,
  patientContractFixture,
  updatePatientContractFixture,
} from "./contract.fixture";
import type { PatientDTO } from "./types";

const { get, post, put, delEmpty } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delEmpty: vi.fn(),
}));

vi.mock("@/lib/api", () => ({ api: { get, post, put, delEmpty } }));

import { patientApi } from "./api";

const patientPage: PageResult<PatientDTO> = {
  content: [patientContractFixture],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

describe("patientApi facade contract", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    put.mockReset();
    delEmpty.mockReset();
  });

  it("gets a patient using the live id path", async () => {
    get.mockResolvedValueOnce(patientContractFixture);
    await expect(patientApi.getById(patientContractFixture.maBenhNhan))
      .resolves.toBe(patientContractFixture);
    expect(get).toHaveBeenCalledWith(
      `/v1/patients/${patientContractFixture.maBenhNhan}`,
    );
  });

  it("uses zero-based search paging with default size 20 and keyword filter", async () => {
    get.mockResolvedValueOnce(patientPage);
    await patientApi.search();
    expect(get).toHaveBeenCalledWith("/v1/patients?page=0&size=20");

    get.mockResolvedValueOnce(patientPage);
    await patientApi.search({ keyword: "Nguyen A", page: 3, size: 10 });
    expect(get).toHaveBeenLastCalledWith(
      "/v1/patients?page=3&size=10&keyword=Nguyen+A",
    );
  });

  it("posts Vietnamese create DTO fields, including soCmnd", async () => {
    post.mockResolvedValueOnce(patientContractFixture);
    await patientApi.create(createPatientContractFixture);
    expect(post).toHaveBeenCalledWith("/v1/patients", createPatientContractFixture);
    expect(createPatientContractFixture).toHaveProperty("soCmnd", "001234567890");
  });

  it("puts the corrected soCmnd and preserves nullable fields", async () => {
    put.mockResolvedValueOnce(patientContractFixture);
    await patientApi.update(patientContractFixture.maBenhNhan, updatePatientContractFixture);

    expect(put).toHaveBeenCalledWith(
      `/v1/patients/${patientContractFixture.maBenhNhan}`,
      updatePatientContractFixture,
    );
    expect(updatePatientContractFixture).toHaveProperty("soCmnd", "001234567891");
    expect(updatePatientContractFixture.diaChi).toBeNull();
    expect(updatePatientContractFixture.bhytSo).toBeNull();
  });

  it("uses DELETE through the empty-response API for the 204 endpoint", async () => {
    delEmpty.mockResolvedValueOnce(undefined);
    await expect(patientApi.delete(patientContractFixture.maBenhNhan)).resolves.toBeUndefined();
    expect(delEmpty).toHaveBeenCalledWith(
      `/v1/patients/${patientContractFixture.maBenhNhan}`,
    );
  });

  it("supports legacy updates without an identity correction and explicit null", async () => {
    put.mockResolvedValue(patientContractFixture);
    const { soCmnd: originalIdentity, ...legacy } = updatePatientContractFixture;
    expect(originalIdentity).toBeTruthy();
    await patientApi.update(patientContractFixture.maBenhNhan, legacy);
    expect(put.mock.calls[0][1]).not.toHaveProperty("soCmnd");
    await patientApi.update(patientContractFixture.maBenhNhan, { ...legacy, soCmnd: null });
    expect(put.mock.calls[1][1]).toHaveProperty("soCmnd", null);
  });

  it("preserves the backend duplicate-CMND error code for the form", async () => {
    const error = {
      name: "ApiRequestError",
      message: "Số CMND/CCCD đã tồn tại",
      status: 409,
      code: "PATIENT_CMND_DUPLICATE",
    };
    post.mockRejectedValueOnce(error);

    await expect(patientApi.create(createPatientContractFixture)).rejects.toMatchObject({
      code: "PATIENT_CMND_DUPLICATE",
      status: 409,
    });
  });

  it("normalizes paging like PageQuery and trims blank keywords", async () => {
    get.mockResolvedValue(patientPage);
    await patientApi.search({ page: -1, size: 999, keyword: "  " });
    expect(get).toHaveBeenLastCalledWith("/v1/patients?page=0&size=100");
    await patientApi.search({ page: Number.NaN, size: 0, keyword: "  A & B  " });
    expect(get).toHaveBeenLastCalledWith("/v1/patients?page=0&size=20&keyword=A+%26+B");
  });

  it("keeps identity corrections but strips runtime server fields from updates", async () => {
    put.mockResolvedValue(patientContractFixture);
    await patientApi.update(patientContractFixture.maBenhNhan, { ...updatePatientContractFixture,
      ...patientContractFixture });
    const body = put.mock.calls[0][1];
    expect(body).toHaveProperty("soCmnd", patientContractFixture.soCmnd);
    expect(body).not.toHaveProperty("maBenhNhan");
    expect(body).not.toHaveProperty("createdAt");
    expect(body).toHaveProperty("hoTen", patientContractFixture.hoTen);
  });
});
