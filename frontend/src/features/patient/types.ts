export type GioiTinh = "M" | "F";

export interface PatientDTO {
  maBenhNhan: string;
  hoTen: string;
  ngaySinh: string;
  gioiTinh: GioiTinh;
  soCmnd: string;
  diaChi?: string;
  soDienThoai?: string;
  email?: string;
  bhytSo?: string;
  createdAt: string;
  updatedAt: string;
}
