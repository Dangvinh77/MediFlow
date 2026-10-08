export type GioiTinh = "M" | "F";

export interface PatientDTO {
  maBenhNhan: string;
  hoTen: string;
  ngaySinh: string;
  gioiTinh: GioiTinh;
  soCmnd: string;
  diaChi: string | null;
  soDienThoai: string | null;
  email: string | null;
  bhytSo: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePatientRequest {
  hoTen: string;
  ngaySinh: string;
  gioiTinh: GioiTinh;
  soCmnd: string;
  diaChi: string | null;
  soDienThoai: string | null;
  email: string | null;
  bhytSo: string | null;
}

export type UpdatePatientRequest = Omit<CreatePatientRequest, "soCmnd">;
