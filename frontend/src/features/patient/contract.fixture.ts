import type { PatientDTO } from "./types";

/** Compile-time wire-contract fixture for the Vietnamese Patient DTO. */
export const patientContractFixture = {
  maBenhNhan: "550e8400-e29b-41d4-a716-446655440000",
  hoTen: "Nguyen Van A",
  ngaySinh: "1990-01-15",
  gioiTinh: "M",
  soCmnd: "001234567890",
  diaChi: "Ho Chi Minh City",
  soDienThoai: "0901234567",
  email: "patient@example.com",
  bhytSo: "01-12345678-9",
  createdAt: "2026-08-13T10:00:00Z",
  updatedAt: "2026-08-13T10:00:00Z",
} satisfies PatientDTO;
