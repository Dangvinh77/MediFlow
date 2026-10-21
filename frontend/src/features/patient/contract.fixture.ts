import type { CreatePatientRequest, PatientDTO, UpdatePatientRequest } from "./types";

/** Compile-time wire-contract fixture for the Vietnamese Patient DTO. */
export const patientContractFixture = {
  maBenhNhan: "550e8400-e29b-41d4-a716-446655440000",
  hoTen: "Nguyen Van A",
  ngaySinh: "1990-01-15",
  gioiTinh: "M",
  soCmnd: "001234567890",
  diaChi: null,
  soDienThoai: null,
  email: null,
  bhytSo: null,
  createdAt: "2026-08-13T10:00:00Z",
  updatedAt: "2026-08-13T10:00:00Z",
} satisfies PatientDTO;

/** Update DTO fixture includes a corrected identity number. */
export const updatePatientContractFixture = {
  soCmnd: "001234567891",
  hoTen: "Nguyen Van A",
  ngaySinh: "1990-01-15",
  gioiTinh: "M",
  diaChi: null,
  soDienThoai: null,
  email: null,
  bhytSo: null,
} satisfies UpdatePatientRequest;

export const createPatientContractFixture = {
  hoTen: "Nguyen Van A",
  ngaySinh: "1990-01-15",
  gioiTinh: "M",
  soCmnd: "001234567890",
  diaChi: null,
  soDienThoai: null,
  email: null,
  bhytSo: null,
} satisfies CreatePatientRequest;
