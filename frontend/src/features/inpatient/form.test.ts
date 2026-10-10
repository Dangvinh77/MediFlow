import { describe, expect, it } from "vitest";
import {
  mapAdmissionFieldErrors,
  toCreateAdmissionRequest,
  validateAdmissionForm,
  type AdmissionFormField,
  type AdmissionFormValues,
} from "./form";

const REQUEST_ID = "00000000-0000-4000-8000-000000000001";
const RECORD_ID = "00000000-0000-4000-8000-000000000002";
const PATIENT_ID = "00000000-0000-4000-8000-000000000003";
const DEPARTMENT_ID = "00000000-0000-4000-8000-000000000004";
const ACTOR_ID = "00000000-0000-4000-8000-000000000005";

const validValues: AdmissionFormValues = {
  maYeuCauNoiTru: ` ${REQUEST_ID} `,
  maHoSoNguon: RECORD_ID,
  maBenhNhan: PATIENT_ID,
  maKhoa: DEPARTMENT_ID,
  nguoiYeuCau: ACTOR_ID,
  tomTatChanDoan: "  Viêm phổi  ",
  doUuTien: "URGENT",
  capCuu: false,
  thoiGianYeuCau: "2026-10-09T10:30",
};

describe("admission form contract", () => {
  it("accepts and maps a valid form to the exact wire contract", () => {
    expect(validateAdmissionForm(validValues)).toEqual({});

    expect(toCreateAdmissionRequest(validValues)).toEqual({
      maYeuCauNoiTru: REQUEST_ID,
      maHoSoNguon: RECORD_ID,
      maBenhNhan: PATIENT_ID,
      maKhoa: DEPARTMENT_ID,
      nguoiYeuCau: ACTOR_ID,
      tomTatChanDoan: "Viêm phổi",
      doUuTien: "URGENT",
      capCuu: false,
      thoiGianYeuCau: new Date("2026-10-09T10:30").toISOString(),
    });
  });

  it.each([
    ["maYeuCauNoiTru", "Mã yêu cầu nội trú"],
    ["maHoSoNguon", "Mã hồ sơ nguồn"],
    ["maBenhNhan", "Mã bệnh nhân"],
    ["maKhoa", "Mã khoa"],
    ["nguoiYeuCau", "Mã nhân viên yêu cầu"],
  ] satisfies Array<[AdmissionFormField, string]>) (
    "rejects malformed UUID field %s",
    (field, label) => {
      const errors = validateAdmissionForm({ ...validValues, [field]: "not-a-uuid" });

      expect(errors[field]).toBe(`${label} phải là UUID hợp lệ.`);
    },
  );

  it("requires every UUID field", () => {
    const errors = validateAdmissionForm({
      ...validValues,
      maYeuCauNoiTru: "",
      maHoSoNguon: "",
      maBenhNhan: "",
      maKhoa: "",
      nguoiYeuCau: "",
    });

    expect(errors).toMatchObject({
      maYeuCauNoiTru: "Mã yêu cầu nội trú là bắt buộc.",
      maHoSoNguon: "Mã hồ sơ nguồn là bắt buộc.",
      maBenhNhan: "Mã bệnh nhân là bắt buộc.",
      maKhoa: "Mã khoa là bắt buộc.",
      nguoiYeuCau: "Mã nhân viên yêu cầu là bắt buộc.",
    });
  });

  it("validates diagnosis summary and request time", () => {
    expect(validateAdmissionForm({ ...validValues, tomTatChanDoan: "   " }))
      .toMatchObject({ tomTatChanDoan: "Tóm tắt chẩn đoán là bắt buộc." });
    expect(validateAdmissionForm({ ...validValues, tomTatChanDoan: "x".repeat(4001) }))
      .toMatchObject({ tomTatChanDoan: "Tóm tắt chẩn đoán không được dài quá 4000 ký tự." });
    expect(validateAdmissionForm({ ...validValues, thoiGianYeuCau: "" }))
      .toMatchObject({ thoiGianYeuCau: "Thời gian yêu cầu là bắt buộc." });
    expect(validateAdmissionForm({ ...validValues, thoiGianYeuCau: "not-a-date" }))
      .toMatchObject({ thoiGianYeuCau: "Thời gian yêu cầu không hợp lệ." });
  });

  it("maps only known backend field errors", () => {
    expect(mapAdmissionFieldErrors([
      { field: "maBenhNhan", message: "Bệnh nhân không hợp lệ" },
      { field: "unknown", message: "Không dùng" },
    ])).toEqual({ maBenhNhan: "Bệnh nhân không hợp lệ" });
  });
});
